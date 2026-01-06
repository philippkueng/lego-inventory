;; A namespace to help with finding the esp cams on the network. Identifying them by their hostname.

(ns find-cameras
  (:import [java.net InetAddress]))

(defn ip-range
  "Generate a sequence of IP addresses in the range 192.168.0.1 to 192.168.0.255"
  []
  (map #(str "192.168.0." %) (range 1 256)))

(defn check-host
  "Check if a host is reachable and return its hostname if alive via reverse DNS lookup.
   Returns a map with :ip, :alive?, and :hostname keys."
  [ip timeout-ms]
  (try
    (let [inet-address (InetAddress/getByName ip)
          alive? (.isReachable inet-address timeout-ms)
          hostname (when alive?
                     (let [resolved-hostname (.getHostName inet-address)]
                       ;; Only return hostname if it's not just the IP address
                       (when-not (= resolved-hostname ip)
                         resolved-hostname)))]
      {:ip ip
       :alive? alive?
       :hostname hostname})
    (catch Exception _e
      {:ip ip
       :alive? false
       :hostname nil})))

(defn scan-network
  "Scan all IPs in the 192.168.0.x range and return alive hosts with their hostnames.
   Uses parallel processing for faster scanning.
   Hostnames are resolved via reverse DNS (PTR) lookups."
  [& {:keys [timeout-ms]
      :or {timeout-ms 3000}}]                               ;; the esp32 cams can be slow to respond
  (->> (ip-range)
       (pmap #(check-host % timeout-ms))
       (filter :alive?)
       doall))

(defn filter-esp32-hosts
  "Filter hosts to only include those with hostnames starting with 'esp32-'"
  [hosts]
  (->> hosts
       (filter #(and (:hostname %)
                     (.startsWith (:hostname %) "esp32-")))
       #_(map :hostname)))

(defn scan
  "Scan the network for ESP32 cameras and return their hostnames.
   This is the main entry point that performs the scan and filtering."
  []
  (->> (scan-network)
       filter-esp32-hosts))


(comment
  ;; Test checking a specific host
  (check-host "192.168.0.193" 3000)

  ;; Full scan (uses reverse DNS lookups)
  (time (scan))
  ;=> ("esp32-318a20" "esp32-31a6d4" "esp32-3121b0" "esp32-317b50")
  ;=>
  ;({:ip "192.168.0.106", :alive? true, :hostname "esp32-318a20"}
  ; {:ip "192.168.0.185", :alive? true, :hostname "esp32-31a6d4"}
  ; {:ip "192.168.0.193", :alive? true, :hostname "esp32-3121b0"}
  ; {:ip "192.168.0.217", :alive? true, :hostname "esp32-317b50"})

  ;; Full scan with detailed info
  (time (scan-network))

  )
