(ns electric-fiddle.server-httpkit
  (:require
   [clojure.tools.logging :as log]
   [electric-fiddle.ring-middleware :as middleware]
   [hyperfiddle.electric-httpkit-adapter :as electric-httpkit]
   [hyperfiddle.electric-ring-adapter :as electric-ring]
   [org.httpkit.server :as httpkit]
   [ring.middleware.cookies :as cookies]
   [ring.middleware.params :refer [wrap-params]])
  (:import
   (java.io IOException)
   (java.net BindException)))

(defn electric-websocket-middleware
 "Open a websocket and boot an Electric server program defined by `entrypoint`.
  Takes:
  - a ring handler `next-handler` to call if the request is not a websocket upgrade (e.g. the next middleware in the chain),
  - a `config` map eventually containing {:hyperfiddle.electric/user-version <version>} to ensure client and server share the same version,
    - see `hyperfiddle.electric-ring-adapter/wrap-reject-stale-client`
  - an Electric `entrypoint`: a function (fn [ring-request] (e/boot-server {} my-ns/My-e-defn ring-request))
  "
  [next-handler config entrypoint]
  ;; Applied bottom-up
  (-> (electric-httpkit/wrap-electric-websocket next-handler entrypoint) ; 5. connect electric client
    (middleware/wrap-authenticated-request) ; 4. Optional - authenticate before opening a websocket
    (cookies/wrap-cookies) ; 3. makes cookies available to Electric app
    ;; 2. reject stale electric client
    (electric-ring/wrap-reject-stale-client config
      (fn on-mismatch [ring-request client-version server-version]
        (log/info 'wrap-reject-stale-client ": Electric client connection was rejected because client version doesn't match the server version. Client was instructed to perform a page reload so to get new javascript assets."
          {:client-version (pr-str client-version)
           :server-version (pr-str server-version)})
        (httpkit/as-channel ring-request
          (electric-httpkit/reject-websocket-handler 1008 "stale client"))))
    (wrap-params))) ; 1. parse query params

(defn middleware [config entrypoint]
  (-> (middleware/http-middleware config)  ; 3. serve regular http content
    (electric-websocket-middleware config entrypoint) ; 2. intercept electric websocket
    (middleware/wrap-allow-ws-connect (::middleware/accept-ws-connect-fn config)) ; 1. reject ws connections until server is ready.
    ;; To prevent stale electric clients to reconnect in dev mode.
    ))


(defn start-server! [entrypoint
                     {:keys [port host]
                      :or   {port 8080, host "0.0.0.0"} ; insecure default?
                      :as   config}]
  (log/info (pr-str config))
  (try
    (let [server (httpkit/run-server (middleware config entrypoint)
                   (merge {:port   port
                           :max-ws (* 1024 1024 100)} ; max ws message size = 100M, temporary
                     config))]
      (log/info "👉" (str "http://" host ":" port))
      server)

    (catch IOException err
      (if (instance? BindException (ex-cause err))  ; port is already taken, retry with another one
        (do (log/warn "Port" port "was not available, retrying with" (inc port))
            (start-server! entrypoint (update config :port inc)))
        (throw err)))))
