(ns record
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(def camera-ip "esp32-317b50")

(defn fetch-photo!
  [camera-ip]
  (some-> (:body (client/get
                   (str "http://" camera-ip "/capture?_cb=" (rand-int 1000000))
                   {:as :stream}))
    (io/copy (io/file (format "photos/photo_%s.jpg" (System/currentTimeMillis))))))


(comment
  (doall
    (doseq [frame (range 10)]
      (fetch-photo! camera-ip)))

  )

(defn -main []
  (println "Starting to record pictures...")
  (while true
    (fetch-photo! camera-ip)))
