(ns citron.routes
  (:require
   [citron.config :refer [cds success]]
   [citron.store :as store]
   
   [environ.core :refer [env]]
   [clojure.java.io :as io]
   [taoensso.timbre      :as t]
   [sparrows.system :refer [get-mime]]
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

(defn previewable? [file mime]
  (and (.isFile file)
       (or (not mime)
           (s/includes? mime "text")
           (s/includes? mime "json"))
       (< (.length file) (* (str->num (env :citron-max-preview-size 1024)) 1024))))

(defn- all-files-in-path [path file]
  (FileTranverse/listDiretory path file))

(defn handle-get-file
  "Get file or path meta information"
  [{:keys [params]}]
  (response
   (let [{:keys [path offset]} params
         file                  (to-os-file path)
         offset                (or (str->num offset) 0)]
     (if (.exists file)
       (let [isdir     (.isDirectory file)
             mime      (when-not isdir (get-mime file))
             content   (when (previewable? file mime)
                         (slurp file))
             all-files (when isdir
                         (all-files-in-path path (to-os-file path)))
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
     (io/copy file (File/createTempFile (str "del-" (time/long->date-string (time/now-in-millis))) (.getName file)))
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
     (store/set-msg msg)
     (success))))

(defn handle-get-msg [_]
  (response
   (success (store/get-msg))))

(defn handle-get-static-file [req]
  (let [path (get-in req [:params :path])
        f (to-os-file path)]
    (if (.exists f)
      (-> f
          (response)
          (header "filename" (.getName f))
          (header "content-type" (or (get-mime f) "application/binary")))
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
  (GET "/static/file" _ handle-get-static-file))

