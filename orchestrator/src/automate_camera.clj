(ns automate-camera
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(def camera-hostname "esp32-318a20")

(comment
  ;; play with the camera API
  ;; documentation can be found here: https://github.com/easytarget/esp32-cam-webserver/blob/master/API.md

  ;; get the status
  (-> (client/get (format "http://%s/status" camera-hostname))
    :body
    (json/parse-string true))
  ;=>
  ;{:awb 1,
  ; :wpc 1,
  ; :colorbar 0,
  ; :cam_name "ESP32 camera server",
  ; :gainceiling 0,
  ; :bpc 0,
  ; :saturation 0,
  ; :hmirror 0,
  ; :framesize 9,
  ; :ae_level 0,
  ; :contrast 0,
  ; :autolamp 0,
  ; :brightness 0,
  ; :lenc 1,
  ; :vflip 0,
  ; :wb_mode 0,
  ; :agc_gain 0,
  ; :raw_gma 1,
  ; :xclk 8,
  ; :sharpness 0,
  ; :stream_url "http://192.168.0.106:81/",
  ; :code_ver "Jul 17 2024 @ 17:45:38",
  ; :aec_value 204,
  ; :special_effect 0,
  ; :lamp 0,
  ; :quality 12,
  ; :rotate "0",
  ; :agc 1,
  ; :awb_gain 1,
  ; :aec 1,
  ; :dcw 1,
  ; :min_frame_time 0,
  ; :aec2 0}


  )
