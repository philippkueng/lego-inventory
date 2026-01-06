(ns components
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

;; Servo Controller
(def servo-controller-hostname "lego-sorter-servo-controller")

;; Sorter Servo
(def ^:private slot-count 10)

(defn set-sorter-slot!
  "The angle the sorter can move without causing damage is 160 degrees, meaning from 10-170
   The slots are meant to be starting with 1"
  [slot-number]
  (let [new-angle (->> (/ 160 slot-count)
                    (* slot-number)
                    (+ 10))]
    (->> (client/get (format
                       "http://%s/api/sorter_servo?angle=%d"
                       servo-controller-hostname new-angle) {:as :json})
      :body)))

(comment
  (set-sorter-slot! 7)
  (set-sorter-slot! 1)
  (set-sorter-slot! 7)

  )


;; Feeder Servo
(defn feeder-up! []
  (->> (client/get (format "http://%s/api/feeder?angle=180" servo-controller-hostname) {:as :json})
    :body)
  (Thread/sleep 800))

(defn feeder-down! []
  (->> (client/get (format "http://%s/api/feeder?angle=0" servo-controller-hostname) {:as :json})
    :body)
  (Thread/sleep 800))


(comment
  ;; feed for 10 runs
  (doseq [i (range 10)]
    (feeder-down!)
    (feeder-up!))

  (feeder-down!)
  (feeder-up!)

  )

;; Stepper Motor Controller

(def stepper-controller-hostname "lego-sorter-stepper-controller")
(defn start-stepper-motor! []
  (->> (client/get (str "http://" stepper-controller-hostname "/api/start") {:as :json})
    :body))

(defn stop-stepper-motor! []
  (->> (client/get (str "http://" stepper-controller-hostname "/api/stop") {:as :json})
    :body))

(comment
  (doall
    (doseq [i (range 50)]
      (do
        (start-stepper-motor!)
        (Thread/sleep 400)
        (stop-stepper-motor!)
        (Thread/sleep 800))))
  )
