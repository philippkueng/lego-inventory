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
    (doseq [i (range 100)]
      (do
        (start-stepper-motor!)
        (Thread/sleep 400)
        (stop-stepper-motor!)
        (Thread/sleep 800))))

  )


(def slot-count 10)

(defn set-sorter-slot!
  "The angle the sorter can move without causing damage is 160 degrees, meaning from 10-170
   The slots are meant to be starting with 1"
  [slot-number]
  (let [new-angle (->> (/ 160 slot-count)
                    (* slot-number)
                    (+ 10))]
    (->> (client/get (format
                       "http://192.168.0.250/api/sorter_servo?angle=%d"
                       new-angle) {:as :json})
      :body)))

(comment

  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=90" {:as :json})
    :body)

  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=0" {:as :json})
    :body)

  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=180" {:as :json})
    :body)

  ;; iterate through all the slots
  (doseq [slot (range slot-count)]
    (set-sorter-slot! slot)
    (Thread/sleep 500))

  (set-sorter-slot! 1)
  (set-sorter-slot! 2)
  (set-sorter-slot! 3)
  (set-sorter-slot! 5)
  (set-sorter-slot! 8)
  (set-sorter-slot! 9)
  (set-sorter-slot! 10)


  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=10" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=20" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=30" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=40" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=50" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=60" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=70" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=80" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=90" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=100" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=110" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=120" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=130" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=140" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=150" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=160" {:as :json})
    :body)
  (->> (client/get "http://192.168.0.250/api/sorter_servo?angle=170" {:as :json})
    :body)

  )

(defn feeder-up! []
  (->> (client/get "http://192.168.0.250/api/feeder?angle=180" {:as :json})
    :body)
  (Thread/sleep 800))

(defn feeder-down! []
  (->> (client/get "http://192.168.0.250/api/feeder?angle=0" {:as :json})
    :body)
  (Thread/sleep 800))

(comment
  ;; feeder
  (->> (client/get "http://192.168.0.250/api/feeder?angle=90" {:as :json})
    :body)

  (->> (client/get "http://192.168.0.250/api/feeder?angle=0" {:as :json})
    :body)

  (->> (client/get "http://192.168.0.250/api/feeder?angle=180" {:as :json})
    :body)

  ;; feed for 10 runs
  (doseq [i (range 10)]
    (feeder-down!)
    (feeder-up!))


  )
