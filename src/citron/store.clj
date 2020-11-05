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
  (->> @msg-store vals (sort-by :ts) reverse))

(defn persist-db! [reload?]
  (let [msg (if reload?
              (merge @msg-store (edn/read-string (slurp citron-msg-file)))
              @msg-store)]
    (spit citron-msg-file msg)
    (reset! msg-store msg))
  (reset! dirty? false))

(defn start-msg-store! []
  (if (.exists citron-msg-file)
    (reset! msg-store (edn/read-string (slurp citron-msg-file)))
    (spit citron-msg-file @msg-store))
  (reset! dirty? false)
  (let [last-edit (atom (.lastModified citron-msg-file))]
    (a/go-loop []
      (try
        (let [modified? (not= @last-edit (.lastModified citron-msg-file))]
          (when (or modified? @dirty?)
            (persist-db! modified?)
            (reset! last-edit (.lastModified citron-msg-file))))
        (catch Throwable e
          (t/error "msg-store handle error" e))
        (finally
          (<! (timeout 1000))))
      (recur))))
