(ns citron.routes
  (:require
   [citron.config :refer [cds success]]

   [environ.core :refer [env]]
   [clojure.java.io :as io]
   [taoensso.timbre      :as t]
   [clojure.string       :as s]
   [compojure.core       :refer [defroutes PUT GET DELETE POST]]
   [ring.util.response   :refer [redirect response header status]]))


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
  (let [root (env :root ".")]
    (apply io/file root (mapv (fn [path] (s/replace path "//" "/")) paths))))

(defn handle-get-file
  "Get file or path meta information"
  [{:keys [params]}]
  (response
   (let [{:keys [path]} params
         file (to-os-file path)]
     (if (.exists file)
       (let [isdir (.isDirectory file)
             files (when isdir
                     (some->> (mapv
                           (fn [f]
                             {:isdir (.isDirectory f)
                              :path (str path "/" (.getName f))
                              :exists true})
                           (.listFiles file))))]
         (success {:exists true
                   :path path
                   :isdir isdir
                   :files files}))
       (cds :not-exist)))))

(defn handle-delete-file [{:keys [params]}]
  (response
   (let [{:keys [path]} params
         file (to-os-file path)]
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

(defroutes api-routes
  (POST "/pub/login" _ handle-post-login)
  (GET "/pub/logout" _ handle-get-logout)
  (GET "/file" _ handle-get-file)
  (DELETE "/file" _ handle-delete-file)
  (PUT "/file" _ handle-put-file)
  (GET "/static/" _ handle-put-file))

