(ns citron.service
  (:require
   [citron.config :refer [bj-timezone]]
   [taoensso.timbre    :as t])
  (:import [java.util.concurrent Executors TimeUnit]
           [org.joda.time MutableDateTime]))

(defonce common-tasks
  [])

(defn calculate-delay
  [{:keys [sec minute hour period]}]
  {:pre [hour period]}
  (let [now (System/currentTimeMillis)
        mdt (.getMillis (doto (MutableDateTime. now bj-timezone)
                          (.setHourOfDay hour)
                          (.setMinuteOfHour (or minute 0))
                          (.setSecondOfMinute (or sec 0))))
        diff (- mdt now)]
    (if (pos? diff) diff (+ period diff))))

(defn start-common-jobs
  []
  (let [exec (Executors/newSingleThreadScheduledExecutor)]
    (doseq [task common-tasks
            :let [{:keys [id job delay period]} task]
            :when id]
      (let [delay (or delay (calculate-delay task))]
        (t/info "starting job" id "in" delay "[ms]")
        (.scheduleAtFixedRate exec job delay period TimeUnit/MILLISECONDS)))))









