(ns citron.store)

(defonce msg-store (atom ""))

(defn set-msg [msg]
  (reset! msg-store msg))

(defn get-msg []
  @msg-store)

