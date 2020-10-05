(ns citron.routes
  (:require
   [citron.config :refer [cds success]]
   [citron.store :as store]
   [citron.mime :refer [more-mime-types]]
   
   [environ.core :refer [env]]
   [clojure.java.io :as io]
   [taoensso.timbre      :as t]
   [clojure.core.cache.wrapped :as c]
   ;; [sparrows.system :refer [get-mime]]
   [ring.util.mime-type :refer [ext-mime-type]]
   [sparrows.misc :refer [str->num]]
   [sparrows.time :as time]
   [clojure.string       :as s]
   [compojure.core       :refer [defroutes PUT GET DELETE POST]]
   [ring.util.response   :refer [redirect response header status not-found]])
  (:import
   [java.io File]
   [citron FileTranverse]))

(def ^:private default-root "/var/www/public")

(def page-size 100)

(defn- check-login [{:keys [username password]}]
  (and (= username password "hello") {:username username}))

(defn handle-post-login [{:keys [params session]}]
  (if-let [data (check-login params)]
    (-> (success data)
        (response)
        (assoc :session (assoc session :identity (:username params))))
    (response
     (cds :invalid-login))))

(defn handle-get-logout [{:keys [session]}]
  (-> (response (success))
      (assoc :session (dissoc session :identity))))

(defn to-os-file [& paths]
  {:pre [(not (some (fn [path] (s/includes? path "..")) paths))]}
  (let [root (env :citron-file-root default-root)]
    (apply io/file root (mapv (fn [path] (s/replace path "//" "/")) paths))))

(defn to-filename [path]
  (last (s/split path #"/")))

(defn previewable? [file mime]
  (and (.isFile file)
       mime
       (or (s/includes? mime "text")
           (s/includes? mime "json"))
       (< (.length file) (* (str->num (env :citron-max-preview-size 1024)) 1024))))

(def flist-cache (c/ttl-cache-factory {} :ttl (* 10 60 1000)))

(defn- all-files-in-path [path file]
  (let [last-edit (.lastModified file)
        cached (c/lookup flist-cache path)]
    (if (= last-edit (:last-edit cached))
      (:flist cached)
      (let [flist (FileTranverse/listDiretory path file)]
        (c/evict flist-cache path)
        (c/through-cache flist-cache path (constantly {:last-edit last-edit :flist flist}))
        flist))))

(defn- get-mime [uri]
  (ext-mime-type uri more-mime-types))

(defn- filter-files-by-name [term files]
  (filterv (fn [m]
             (s/includes? (s/lower-case (to-filename (get m "path"))) term))
           files))

(defn handle-get-file
  "Get file or path meta information"
  [{:keys [params]}]
  (response
   (let [{:keys [path offset term]} params
         term (when term (s/lower-case term))
         file                  (to-os-file path)
         offset                (or (str->num offset) 0)]
     (if (.exists file)
       (let [isdir     (.isDirectory file)
             mime      (when-not isdir (get-mime path))
             content   (when (previewable? file mime)
                         (slurp file))
             all-files (when isdir
                         (all-files-in-path path (to-os-file path)))
             all-files (if (s/blank? term)
                         all-files
                         (filter-files-by-name term all-files))
             total     (count all-files)
             files     (->> all-files (drop offset) (take page-size))
             more      (< (+ offset page-size) total)]
         (success {:exists  true
                   :offset  offset
                   :total   total
                   :more    more
                   :path    path
                   :isdir   isdir
                   :size    (when (not isdir) (.length file))
                   :mime    mime
                   :files   files
                   :content content}))
       (cds :not-exist)))))

(defn handle-delete-file [{:keys [params]}]
  (response
   (let [{:keys [path]} params
         file (to-os-file path)]
     (when (.isFile file)
       (io/copy file (File/createTempFile (str "del-" (time/long->date-string (time/now-in-millis))) (.getName file))))
     (io/delete-file file true)
     (success))))


(defn handle-put-file [{:keys [params]}]
  (response
   (let [{:keys [filename tempfile size]} (:file params)
         {:keys [parent]} params
         out (to-os-file parent filename)]
     (if (.exists out)
       (cds :file-exists)
       (do
         (io/copy tempfile out)
         (success))))))


(defn handle-put-msg [req]
  (response
   (let [msg (get-in req [:params :msg] "")]
     (store/add-msg msg)
     (success))))

(defn handle-get-msg [_]
  (response
   (success (store/get-msg))))

(defn handle-delete-msg [req]
  (response
   (success (store/delete-msg (get-in req [:params :id])))))

(defn handle-get-static-file [req]
  (let [path (get-in req [:params :path])
        f (to-os-file path)]
    (if (.exists f)
      (-> f
          (response)
          (header "filename" (.getName f))
          (header "content-type" (or (get-mime path) "application/binary")))
      (not-found "Not found!"))))

(defn handle-get-index [_]
  (redirect "/index.html"))

(defn handle-post-rename [{:keys [params]}]
  (let [{:keys [path filename]} params
        f (to-os-file path)
        nf (io/file (.getParentFile f) (s/trim filename))]
    (response
     (if (and (.exists f) (not (s/blank? filename)))
       (do
         (.renameTo f nf)
         (success))
       (cds :not-exist)))))

(defn handle-get-staticfile-by-path [req]
  (let [path (get-in req [:params :path])
        f (to-os-file path)]
    (if (.exists f)
      f
      (not-found "Not found!"))))

(defroutes api-routes
  (GET "/" _ handle-get-index)
  (POST "/pub/login" _ handle-post-login)
  (GET "/pub/logout" _ handle-get-logout)
  (GET "/file" _ handle-get-file)
  (DELETE "/file" _ handle-delete-file)
  (PUT "/file" _ handle-put-file)
  (POST "/rename" _ handle-post-rename)
  (PUT "/msg" _ handle-put-msg)
  (GET "/msg" _ handle-get-msg)
  (DELETE "/msg" _ handle-delete-msg)
  (GET "/static/file" _ handle-get-static-file)
  ;; (GET ["/pub/staticfile/:path" :path #".*"] _ handle-get-staticfile-by-path)
  (GET ["/staticfile/:path" :path #".*"] _ handle-get-staticfile-by-path))

