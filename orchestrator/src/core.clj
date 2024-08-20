(ns core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

;(defn- fetch-photo!
;  "makes an HTTP request and fetches the binary object"
;  [url]
;  (let [req (client/get url {:as :byte-array :throw-exceptions false})]
;    (if (= (:status req) 200)
;      (:body req))))

#_(defn- save-photo!
  "downloads and stores the photo on disk"
  [photo]
  (let [p (fetch-photo! (:url photo))]
    (if (not (nil? p))
      (with-open [w (io/output-stream (str "photos/" (:id photo) ".jpg"))]
        (.write w p)))))

;(defn- save-photo!
;  "downloads and stores the photo on disk"
;  [{:keys [url id] :as photo}]
;  (some-> (fetch-photo! url) (io/copy (io/file "photos" id ".jpg"))))

;; looking for hostname esp32-31a204
#_(def camera-ip "192.168.0.28")
(def camera-ip "esp32-3121b0")

;(defn take-picture!
;  "Instructs the controller to take a new picture"
;  []
;  (:body (client/get "http://192.168.0.27/capture")))
;
;(defn fetch-photo!
;  []
;  (some-> (:body (client/get
;                   "http://192.168.0.27/saved-photo"
;                   {:as :stream}))
;    (io/copy (io/file "photo.jpg"))))

;; flash the ESP-CAM code into an AI Thinker module
(defn enable-light! [camera-ip]
  (:body (client/get (str "http://" camera-ip "/control?var=lamp&val=100"))))

(defn disable-light! [camera-ip]
  (:body (client/get (str "http://" camera-ip "/control?var=lamp&val=0"))))



(defn fetch-photo!
  [camera-ip]
  (enable-light! camera-ip)
  (Thread/sleep 1000)
  (some-> (:body (client/get
                   (str "http://" camera-ip "/capture?_cb=" (rand-int 1000000))
                   {:as :stream}))
    (io/copy (io/file "photo.jpg")))
  (Thread/sleep 1000)
  (disable-light! camera-ip))


(comment
  ;; set LED intensity
  (:body (client/get (str "http://" camera-ip "/control?var=lamp&val=100")))
  (:body (client/get (str "http://" camera-ip "/control?var=lamp&val=0")))

  (fetch-photo! camera-ip)
  )

(comment
  ;; download the image from an ESP32-CAM
  ;; using my code from here https://gist.github.com/philippkueng/11377226

  (do
    (take-picture!)
    ;; it takes about 3s for the picture to the be ready so it can be read
    (Thread/sleep 3000)
    (fetch-photo!))

  ;; takes roughly 0.5s on a good power source
  (doall
    (doseq [i (range 100)]
      (time (some-> (:body (client/get
                             "http://192.168.0.27/saved-photo"
                             {:as :stream}))
              (io/copy (io/file "photo.jpg"))))

      ))

  (fetch-photo! {:url "http://192.168.0.27/saved-photo"
                 :id "photo"})



  )

#_(def controller-ip "192.168.0.251")
(def controller-ip "esp32-5d8488")
;; esp32-5d8488
(defn start-stepper-motor! []
  (->> (client/get (str "http://" controller-ip "/api/start") {:as :json})
    :body))

(defn stop-stepper-motor! []
  (->> (client/get (str "http://" controller-ip "/api/stop") {:as :json})
    :body))

(comment
  ;; talking to the controller of the stepper motor
  (->> (client/get "http://192.168.0.250/api/start" {:as :json})
    :body)

  (->> (client/get "http://192.168.0.250/api/stop" {:as :json})
    :body)

  (start-stepper-motor!)
  (stop-stepper-motor!)

  (do (start-stepper-motor!)
      (Thread/sleep 30000)
      (stop-stepper-motor!))

  (doall
    (doseq [i (range 50)]
      (do
        (start-stepper-motor!)
        (Thread/sleep 400)
        (stop-stepper-motor!)
        (Thread/sleep 800))))

  )

