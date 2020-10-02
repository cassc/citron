(ns citron.store
  (:require
   [sparrows.misc :refer [uuid]]
   [clojure.edn :as edn]
   [taoensso.timbre :as t]
   [clojure.java.io :as io]
   [clojure.core.async :as a :refer [<! timeout]]))

(def citron-msg-file (io/file ".citron.msg.edn"))

(def msg-store (atom {}))

(def dirty? (atom true))

(defn mark-dirty []
  (reset! dirty? true))

(defn add-msg [msg]
  (let [id (uuid)]
    (swap! msg-store assoc id {:msg msg
                               :id id
                               :ts (System/currentTimeMillis)})
    (mark-dirty)))

(defn delete-msg [id]
  (swap! msg-store dissoc id)
  (mark-dirty))

(defn get-msg []
  (->> @msg-store vals (sort-by :ts) rest reverse))

(defn persist-db! []
  (spit citron-msg-file @msg-store)
  (reset! dirty? false))

(defn start-msg-store! []
  (if (.exists citron-msg-file)
    (reset! msg-store (edn/read-string (slurp citron-msg-file)))
    (spit citron-msg-file @msg-store))
  (reset! dirty? false)
  (a/go-loop []
    (try
      (when @dirty?
        (persist-db!)) 
      (catch Throwable e
        (t/error "msg-store handle error" e))
      (finally
        (<! (timeout 1000))))
    (recur)))


