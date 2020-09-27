(ns citron.store
  (:require
   [environ.core :refer [env]]
   [sparrows.misc :refer [uuid]]
   [clojure.core.cache.wrapped :as c]))

(defonce ttl-store (delay (c/ttl-cache-factory {} :ttl (env :session-ttl (* 3600 24 1000)))))

(defn gen-session [session]
  (let [id (uuid)]
    (c/through-cache @ttl-store id (constantly session))))
