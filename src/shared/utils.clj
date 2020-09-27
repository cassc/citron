(ns shared.utils
  (:require
   [com.climate.claypoole        :as cp]
   [taoensso.timbre              :as t]
   [buddy.core.nonce             :as nonce]
   [buddy.core.hash              :as h]
   [buddy.core.crypto            :as crypto]
   [buddy.core.codecs            :refer [hex->bytes bytes->hex to-bytes bytes->str]]
   [clojure.java.io              :as io]
   [clojure.java.shell           :refer [sh]]
   [clojure.string               :as s]
   [sparrows.http                :refer [send-email POST default-request-map async-request]]
   [sparrows.cypher              :refer [sha512 md5 base64-encode form-encode base64-decode]]
   [sparrows.misc                :as sm :refer [str->num wrap-exception uuid]]
   [sparrows.system              :refer [get-mime]]
   [sparrows.time                :as time :refer [long->date-string date-string->long]]
   [clojure.walk                 :refer [postwalk]]
   [dk.ative.docjure.spreadsheet :as sp]
   [cheshire.core                :refer [parse-string generate-string]]
   [hiccup.core :refer [html]]
   [clojure.core.memoize         :as memo]
   [clojure.core.async :as a])
  (:import
   [org.apache.commons.codec.binary Hex]
   [java.awt Graphics2D Color Font]
   [java.awt.image BufferedImage]
   [javax.imageio ImageIO]
   [java.io File]
   [java.util Calendar]))

(defn dissoc-empty-val-and-trim
  "Like `dissoc-empty-val' but also trims string value"
  [m]
  (if (map? m)
    (loop [ks (keys m)
           m m]
      (if (seq ks)
        (let [k (first ks)
              v (get m k)
              more (rest ks)]
          (cond
            (nil? v)        (recur more (dissoc m k))
            (string? v)     (if (s/blank? v)
                              (recur more (dissoc m k))
                              (recur more (assoc m k (s/trim v))))
            (sequential? v) (recur more (assoc m k (seq v)))
            :else           (recur more m)))
        m))
    m))

(defn has-error? [url {:keys [status body error] :as resp}]
  (when (not= status 200)
    (t/error "Request to" url "failed:" resp)
    true))

(defn rand-digits [& [n]]
  (s/join (cons (inc (rand-int 9)) (repeatedly (dec n) #(rand-int 10)))))

(defn rand-string
  ([]
   (rand-string 6))
  ([n-bytes]
   (base64-encode (nonce/random-bytes n-bytes))))

(defn send-plain-email
  "Send plain text to the receiver. Should return nil when sent failed."
  [email-sender n address subject msg]
  (io!
   (try
     (send-email
      (assoc email-sender
             :to (if (sequential? address)
                   (vec address)
                   [address])
             :subject subject :text msg))
     (catch Exception e
       (t/error "Failed sending message to " address subject msg "Error:" (.getMessage e))
       (when (pos? n)
         (Thread/sleep 60000)
         (send-plain-email email-sender (dec n) address subject msg))))))

(def ^:private pools
  (atom {}))

(defn destroy-lazy-pools
  []
  (t/info "Shutting down all thread pools ... ")
  (doseq [p (seq (vals @pools))]
    (when (and p (realized? p))
      (.shutdown @p))))

(defn id->pool
  [id]
  (when-let [p (id @pools)]
    @p))

(defn- register-lazy-pool
  "Register a pool for later use.
  id should be a unique keyword
  pool should be a delay (cp/pool ...) instance"
  [id pool]
  {:pre [id pool (keyword? id) (delay? pool)]}
  (do
    (swap! pools assoc id pool)
    pool))

(register-lazy-pool :alipay-cb-success-handler (delay (cp/threadpool 2 :daemon true :name "alipay-cb-success-handler")))
(register-lazy-pool :luat-async-handler (delay (cp/threadpool 1 :daemon true :name "luat-async-handler")))

(defmacro wrap-async
  "Wraps an function with future. Exception will be captured and logged by email."
  [pool & body]
  `(cp/future
     ~pool
     (try
       ~@body
       (catch Throwable e#
         (t/warn "Error below ignored!")
         (t/error e#)))))

(defmacro wrap-nil [& body]
  `(do
     ~@body
     nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn today-as-string []
  (long->date-string (System/currentTimeMillis)))


(defn str-to-unixtime
  "Time string to unixtime in seconds"
  ([s]
   (or (str-to-unixtime time/datetime-string->long s)
       (str-to-unixtime time/date-string->long s)
       (str-to-unixtime #(time/date-string->long % {:pattern "yyyy-MM-dd HH:mm" :offset "+8"}) s)))
  ([converter s]
   (try
     (/ (converter s) 1000)
     (catch Exception e))))

(defn phone?
  [s]
  (re-seq #"^1[1-9][0-9]{9}$" (str s)))

(defn email?
  [s]
  (and s (string? s) (re-seq #"^[^@]+@[^@\\.]+[\\.].+" s)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; error handling
(def last-send-time (atom 0))
(defn- allow-send?
  [{:keys [address err]}]
  (when (pos? (- (System/currentTimeMillis) @last-send-time 60000))
    (reset! last-send-time (System/currentTimeMillis))))

(defonce error-chan (a/chan (a/sliding-buffer 10)))

(defn exception-handler
  [req e]
  (let [err-msg (str "Exception: " (.getMessage e) " on Req: " req )]
    (t/error e (str "Exception: " (.getMessage e) " on Req: " req))
    nil))

(defmacro wrap-internal-exception-handler
  "Wrap body in try-catch block. Logs any exception by email. Returns
  nil if an exception is caught."
  [& body]
  `(try
     ~@body
     (catch Throwable e#
       (internal-exception-hanlder e#)
       nil)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn hexify [bytes]
  (apply str (map #(format "%02x" %) bytes)))

(defn unhexify [hex]
  (into-array Byte/TYPE
              (map (fn [[x y]]
                     (unchecked-byte (Integer/parseInt (str x y) 16)))
                   (partition 2 hex))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; creates paths if not exists
(defn lowercase-keys [m]
  (reduce-kv (fn [r k v] (assoc r (s/lower-case (name k)) v)) {} m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- try-convert-str-to-long [^String time ^String offset ^String pattern]
  (try
    (time/datetime-string->long time {:pattern pattern :offset offset})
    (catch Throwable e
      nil)))

(defn boolean? [maybe-a-boolean]
  (or (= false maybe-a-boolean)
      (= true maybe-a-boolean)))


(defn assoc-when
  "Add a k-v pair to map if val is truethy"
  [m k v]
  (conj m (when v [k v])))


(defn now [] (System/currentTimeMillis))

(defn http-success?
  ([resp]
   (http-success? resp))
  ([url {:keys [error status]}]
   (if (or (not= 200 status) error)
     (do
       (t/error (or url "http") " error:" status error)
       false)
     true)))


(defn gen-xls [{:keys [title sheet x-key x-data output key-trans-map]}]
  (let [wb (sp/create-workbook title
                               (cons (if key-trans-map
                                       (map key-trans-map x-key)
                                       (map name x-key))
                                     (map (apply juxt x-key) x-data)))
        sheet (sp/select-sheet sheet wb)
        header-row (first (sp/row-seq sheet))]
    (sp/set-row-style! header-row (sp/create-cell-style! wb {:background :yellow,
                                                             :font {:bold true}}))
    (sp/save-workbook! output wb)))

(defn hex-to-int [hex]
  (Integer/parseInt hex 16))

(defn md5sum?
  [s]
  (re-seq #"^[0-9a-f]{32}$" s))

(defn- hash-with-salt
  [salt password]
  (str salt ":" (sha512 (str (if (md5sum? password) password (md5 password))
                             (md5 salt)))))

(defn encrypt
  "Input password can be either md5-hased or not"
  [password]
  {:pre [(not (s/blank? password))]}
  (hash-with-salt (uuid) password))

(defn check-password
  "Input password can be either md5-hased or not. "
  [password correct-hash]
  (let [[salt _] (s/split correct-hash #":")]
    (= (hash-with-salt salt password) correct-hash)))
