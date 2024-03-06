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
(def camera-ip "192.168.0.30")

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
(defn fetch-photo!
  []
  (some-> (:body (client/get
                   (str "http://" camera-ip "/capture?_cb=" (rand-int 1000000))
                   {:as :stream}))
    (io/copy (io/file "photo.jpg"))))


(comment
  ;; set LED intensity
  (:body (client/get (str "http://" camera-ip "/control?var=led_intensity&val=219")))

  (fetch-photo!)
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

(def controller-ip "192.168.0.250")
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

  (doall
    (doseq [i (range 10)]
      (do
        (start-stepper-motor!)
        (Thread/sleep 2000)
        (stop-stepper-motor!)
        (Thread/sleep 2000))))

  )

