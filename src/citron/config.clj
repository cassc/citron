(ns citron.config
  (:require
   [clj-time.core :as tt]
   [clojure.core.async :refer [put! chan sliding-buffer <! <!!]]
   [taoensso.timbre :as t]
   [clojure.java.io :as io]))

(def cds-map
  {:success       {:code 0}
   :exception     {:code 1 :msg "An occur has occured"}
   :invalid-login {:code 2 :msg "Invalid username or password"}
   :unauthorized  {:code 3 :msg "Unauthorized"}
   :not-exist     {:code 4 :msg "File not exists"}
   :file-exists   {:code 5 :msg "File with same name exists"}
   })

(def ^:prviate err (RuntimeException. "Code name not exists!"))

(defn cds
  ([code-name]
   (cds code-name nil))
  ([code-name merge-map]
   (if-let [c (get cds-map code-name)]
     (if merge-map
       (merge merge-map c)
       c)
     (throw err))))


(defn success
  "Return a success response code with an optional data"
  ([]
   (success nil))
  ([data]
   (if data
     (assoc (cds :success) :data data)
     (cds :success))))
