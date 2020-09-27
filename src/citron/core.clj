(ns citron.core
  "doc for buddy access-rules: https://funcool.github.io/buddy-auth/latest/"
  (:gen-class)
  (:require
   [citron.routes                         :refer [api-routes]]
   [shared.utils                                 :as utils :refer [dissoc-empty-val-and-trim destroy-lazy-pools exception-handler]]
   [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
   [sparrows.system                             :refer [register-shutdownhook]]
   [environ.core                                :refer [env]]
   [compojure.core                              :refer [defroutes routes]]
   [ring.middleware.defaults                    :refer :all]
   [ring.middleware.json                        :refer [wrap-json-response wrap-json-params]]
   ;;[ring.middleware.session :refer [wrap-session]]
   [ring.util.response                          :refer [header]]
   [ring.middleware.reload                      :refer [wrap-reload]]
   [compojure.route                             :as route]
   [taoensso.timbre                             :as t :refer [default-timestamp-opts]]
   [org.httpkit.server                          :refer [run-server]]
   [buddy.auth.accessrules                      :refer [wrap-access-rules]]))

(def any-access (constantly true))

(defn check-identity [{:keys [session]}]
  (not (nil? (:identity session))))

(def access-rules
  [{:pattern #"^/pub/.*"
    :handler any-access}
   {:pattern #"^/.*"
    :handler check-identity}])

(defn make-timbre-config
  []
  {:timestamp-opts (merge default-timestamp-opts {:pattern "yy-MM-dd HH:mm:ss.SSS ZZ"
                                                  :timezone (java.util.TimeZone/getTimeZone "Asia/Shanghai")})
   :level          (keyword (env :log-level "info"))
   :appenders      {:rolling (rolling-appender
                              {:path    (env :log-file "citron.log")
                               :pattern :monthly})}})

(defonce tm-server (atom nil))

(def denied (constantly {:status 403
                         :body "Access Denied"}))

(def system-error {:status 500
                   :body "System error"})

(defn wrap-clean-params
  [handler]
  (fn [req]
    (let [pms (dissoc-empty-val-and-trim (:params req))
          req (assoc req :params pms)]
      (t/trace "cleaned params:" pms)
      (handler req))))

(defn wrap-logging [handler]
  (fn [{:keys [uri session params] :as req}]
    (let [start (System/currentTimeMillis)
          ua      (get-in req [:headers "user-agent"])
          address (or (get-in req [:headers "x-real-ip"]) (:remote-addr req))
          method (:request-method req)]
      (try
        (handler (assoc req :address address))
        (catch Throwable e
          (exception-handler req e)
          (t/error e)
          system-error)
        (finally
          (t/info
           "method:" method
           "uri:" uri
           "ua:" ua
           "from:" address
           "params:" params
           "time:" (- (System/currentTimeMillis) start)))))))

(defroutes not-found-routes
  (route/not-found "Not found"))

(defroutes resource-routes
  (route/resources "/public"))

(def x-routes [api-routes not-found-routes])

(defonce session-store (atom {}))

(defn- token->user [token]
  (get session-store token))

(defn wrap-aid
  [handler]
  (fn [req]
    (let [token (or (get-in req [:headers "az-token"]) (get-in req [:params :az-token]))
          user (when token (token->user token))]
      (handler (assoc req
                      :az-token token
                      :user user)))))


(def app (->
          (apply routes x-routes)
          wrap-json-response
          (wrap-access-rules {:rules access-rules :on-error denied})
          wrap-logging
          wrap-aid
          (wrap-defaults (-> site-defaults
                             ;;(assoc-in [:session :store] (redis-session-store))
                             (assoc-in [:security :anti-forgery] false)))
          wrap-json-params))

(defn wrap-no-cache
  "Disable cache for GET/HEAD requests.
  http://stackoverflow.com/questions/49547/how-to-control-web-page-caching-across-all-browsers"
  [handler]
  (fn [{:keys [request-method] :as req}]
    (if (#{:head :get} request-method)
      (header (handler req) "Cache-Control" "no-store, must-revalidate")
      (handler req))))

(defn start-server []
  (let [dev? (env :dev)
        options {:port (Integer/parseInt (env :port "9090"))
                 :ip (env :ip "127.0.0.1")}]
    (t/info "Dev mode? " (if dev? "true" "false"))
    (reset! tm-server (run-server
                       (if dev?
                         (wrap-no-cache (wrap-reload #'app {:dirs ["src" "src-dev"]}))
                         (routes #'app))
                       options))
    (t/info "Server start success with options" options)))

(defn stop-server []
  (when-let [s @tm-server]
    (s)
    (reset! tm-server nil)))

(defn init! []
  (t/info "Server starting ...")
  (t/merge-config! (make-timbre-config))
  (register-shutdownhook
   #(try
      (destroy-lazy-pools)
      (catch Throwable e
        (t/error e "Error caught when shutting down ..."))
      (finally
        (t/info "Cleaning success!")))))

(defonce initer (delay (init!)))

(defn -main []
  @initer
  (start-server))


(comment
  (-main)
  (stop-server)
  (do
    (stop-server)
    (-main))

  )
