(ns humbleui-webcam
  (:require
    [io.github.humbleui.canvas :as canvas]
    [io.github.humbleui.window :as window]
    [io.github.humbleui.ui :as ui])
  (:import
    [io.github.humbleui.skija Canvas ColorAlphaType ColorType Image ImageInfo]
    [io.github.humbleui.types IPoint]
    [org.bytedeco.javacv FFmpegFrameGrabber Java2DFrameConverter OpenCVFrameConverter$ToMat]
    [org.bytedeco.opencv.opencv_core Mat]
    [org.bytedeco.opencv.global opencv_imgproc]
    [java.awt.image BufferedImage DataBufferByte]
    [java.nio ByteBuffer]))

;; Suppress FFmpeg logging
(org.bytedeco.ffmpeg.global.avutil/av_log_set_level
  org.bytedeco.ffmpeg.global.avutil/AV_LOG_ERROR)

;; State
(def *frame-image
  "Current frame as Skija Image"
  (atom nil))

(def *frame-image-canny
  "Current frame with Canny edge detection as Skija Image"
  (atom nil))

(def *running?
  "Whether the capture is running"
  (atom false))

(def *grabber
  "FFmpegFrameGrabber instance"
  (atom nil))

(def *capture-thread
  "Future running the capture loop"
  (atom nil))

(def *url
  "Current camera URL"
  (ui/signal {:text "http://esp32-31a6d4:81/"
              :placeholder "http://camera-url:port/"}))

(def *status
  "Status message"
  (ui/signal "Disconnected"))

(def *fps
  "Frames per second counter"
  (ui/signal 0))

(defn buffered-image->skija-image
  "Converts a BufferedImage to a Skija Image"
  [^BufferedImage bi]
  (when bi
    (let [width (.getWidth bi)
          height (.getHeight bi)
          ;; Convert to RGB if needed
          rgb-image (if (= (.getType bi) BufferedImage/TYPE_3BYTE_BGR)
                      bi
                      (let [converted (BufferedImage. width height BufferedImage/TYPE_3BYTE_BGR)
                            g (.createGraphics converted)]
                        (.drawImage g bi 0 0 nil)
                        (.dispose g)
                        converted))
          ;; Get raw byte data
          raster (.getRaster rgb-image)
          data-buffer (.getDataBuffer raster)
          bytes (if (instance? DataBufferByte data-buffer)
                  (.getData ^DataBufferByte data-buffer)
                  ;; Fallback: extract pixels manually
                  (let [pixel-data (byte-array (* width height 3))]
                    (.getDataElements raster 0 0 width height pixel-data)
                    pixel-data))
          ;; Convert BGR to RGBA
          rgba-bytes (byte-array (* width height 4))
          _ (dotimes [i (* width height)]
              (let [src-idx (* i 3)
                    dst-idx (* i 4)]
                ;; BGR -> RGBA
                (aset rgba-bytes dst-idx (aget bytes (+ src-idx 2)))       ; R
                (aset rgba-bytes (inc dst-idx) (aget bytes (inc src-idx))) ; G
                (aset rgba-bytes (+ dst-idx 2) (aget bytes src-idx))       ; B
                (aset rgba-bytes (+ dst-idx 3) (byte -1))))                ; A = 255
          ;; Create Skija Image
          image-info (ImageInfo/makeS32 width height ColorAlphaType/PREMUL)]
      (Image/makeRaster image-info rgba-bytes (* width 4)))))

(defn mat->skija-image
  "Converts an OpenCV Mat to a Skija Image"
  [^Mat mat]
  (when mat
    (try
      (let [width (.cols mat)
            height (.rows mat)
            channels (.channels mat)
            ;; Get raw byte data from Mat using proper stride
            row-stride (.step1 mat 0) ; bytes per row
            total-bytes (* height row-stride)
            bytes (byte-array total-bytes)
            _ (.get (.data mat) bytes 0 total-bytes)
            ;; Convert to RGBA
            rgba-bytes (byte-array (* width height 4))]
        (if (= channels 1)
          ;; Grayscale - replicate to RGB and set alpha
          (dotimes [y height]
            (dotimes [x width]
              (let [src-idx (+ (* y row-stride) x)
                    dst-idx (* (+ (* y width) x) 4)
                    gray (bit-and (aget bytes src-idx) 0xFF)]
                (aset rgba-bytes dst-idx (unchecked-byte gray))             ; R
                (aset rgba-bytes (inc dst-idx) (unchecked-byte gray))       ; G
                (aset rgba-bytes (+ dst-idx 2) (unchecked-byte gray))       ; B
                (aset rgba-bytes (+ dst-idx 3) (unchecked-byte 255)))))      ; A = 255
          ;; BGR - convert to RGBA
          (dotimes [y height]
            (dotimes [x width]
              (let [src-idx (+ (* y row-stride) (* x channels))
                    dst-idx (* (+ (* y width) x) 4)
                    b (bit-and (aget bytes src-idx) 0xFF)
                    g (bit-and (aget bytes (inc src-idx)) 0xFF)
                    r (bit-and (aget bytes (+ src-idx 2)) 0xFF)]
                (aset rgba-bytes dst-idx (unchecked-byte r))       ; R
                (aset rgba-bytes (inc dst-idx) (unchecked-byte g)) ; G
                (aset rgba-bytes (+ dst-idx 2) (unchecked-byte b)) ; B
                (aset rgba-bytes (+ dst-idx 3) (unchecked-byte 255))))))   ; A = 255
        ;; Create Skija Image
        (let [image-info (ImageInfo/makeS32 width height ColorAlphaType/PREMUL)]
          (Image/makeRaster image-info rgba-bytes (* width 4))))
      (catch Exception e
        (println "Error converting Mat to Skija Image:" (.getMessage e))
        nil))))

(defn apply-canny
  "Applies Canny edge detection to a Mat, returns new Mat"
  [^Mat src]
  (let [gray (Mat.)
        edges (Mat.)]
    ;; Convert to grayscale if needed
    (if (= (.channels src) 3)
      (opencv_imgproc/cvtColor src gray opencv_imgproc/COLOR_BGR2GRAY)
      (.copyTo src gray))
    ;; Apply Canny edge detection
    ;; Parameters: input, output, threshold1, threshold2
    (opencv_imgproc/Canny gray edges 50.0 150.0)
    (.release gray)
    edges))

(defn start-capture!
  "Starts capturing frames from the camera URL"
  [url]
  (when-not @*running?
    (reset! *running? true)
    (reset! *status "Connecting...")
    (reset! *capture-thread
      (future
        (try
          (let [grabber (FFmpegFrameGrabber. url)]
            (let [mat-converter (OpenCVFrameConverter$ToMat.)]
              (let [start-time (atom (System/currentTimeMillis))
                    frame-count (atom 0)]
                (try
                  (println "Starting webcam capture from" url)
                  (.start grabber)
                  (reset! *grabber grabber)
                  (reset! *status "Connected")

                  (loop []
                    (when @*running?
                      (try
                        (when-let [frame (.grab grabber)]
                          (when (.image frame)
                            (when-let [mat (.convert mat-converter frame)]
                              (try
                                ;; Create original frame image
                                (when-let [skija-image (mat->skija-image mat)]
                                  ;; Close previous original image to avoid memory leak
                                  (when-let [old-image @*frame-image]
                                    (.close ^Image old-image))
                                  (reset! *frame-image skija-image)

                                  ;; Apply Canny edge detection
                                  (let [canny-mat (apply-canny mat)]
                                    (try
                                      (when-let [canny-image (mat->skija-image canny-mat)]
                                        ;; Close previous Canny image
                                        (when-let [old-canny @*frame-image-canny]
                                          (.close ^Image old-canny))
                                        (reset! *frame-image-canny canny-image))
                                      (finally
                                        (.release canny-mat))))

                                  ;; Update FPS counter
                                  (swap! frame-count inc)
                                  (let [elapsed (- (System/currentTimeMillis) @start-time)]
                                    (when (>= elapsed 1000)
                                      (reset! *fps (int (/ @frame-count (/ elapsed 1000.0))))
                                      (reset! start-time (System/currentTimeMillis))
                                      (reset! frame-count 0))))
                                (finally
                                  ;; Always release the original mat
                                  (.release mat))))))
                        (catch Exception e
                          (println "Frame grab error:" (.getMessage e))
                          (.printStackTrace e)))
                      (recur)))

                  (catch Exception e
                    (println "Stream error:" (.getMessage e))
                    (reset! *status (str "Error: " (.getMessage e))))
                  (finally
                    (try
                      (.stop grabber)
                      (.release grabber)
                      (catch Exception e
                        (println "Error stopping grabber:" (.getMessage e))))
                    (reset! *grabber nil)
                    (when-not @*running?
                      (reset! *status "Stopped")))))))
          (catch Exception e
            (println "Error creating grabber/converter:" (.getMessage e))
            (.printStackTrace e)
            (reset! *status (str "Error: " (.getMessage e)))))))))

(defn stop-capture!
  "Stops the capture"
  []
  (when @*running?
    (reset! *running? false)
    (reset! *status "Stopping...")
    ;; Wait for thread to finish
    (when-let [t @*capture-thread]
      (future
        (deref t 5000 nil)
        (reset! *capture-thread nil)))))

(defn cleanup!
  "Cleanup all resources"
  []
  (stop-capture!)
  (when-let [img @*frame-image]
    (.close ^Image img)
    (reset! *frame-image nil))
  (when-let [img @*frame-image-canny]
    (.close ^Image img)
    (reset! *frame-image-canny nil)))

(defn paint-frame
  "Generic frame painter that takes an image atom"
  [ctx ^Canvas canvas ^IPoint size *image-atom label]
  (let [{:keys [width height]} size]
    (if-let [img @*image-atom]
      ;; Draw the frame, scaled to fit
      (let [img-width (.getWidth ^Image img)
            img-height (.getHeight ^Image img)
            scale-x (/ width img-width)
            scale-y (/ height img-height)
            scale (min scale-x scale-y)
            scaled-width (* img-width scale)
            scaled-height (* img-height scale)
            x (/ (- width scaled-width) 2)
            y (/ (- height scaled-height) 2)]
        (.drawImageRect canvas img
          (io.github.humbleui.types.Rect/makeXYWH 0 0 img-width img-height)
          (io.github.humbleui.types.Rect/makeXYWH x y scaled-width scaled-height)))
      ;; No frame yet - show message
      (let [font-ui (ui/get-font nil ctx)
            message (or label @*status)]
        (with-open [fill (ui/paint {:fill 0xFF808080} ctx)]
          (.drawString canvas message (/ width 2) (/ height 2) font-ui fill))))
    ;; Request continuous repaints while running
    (when @*running?
      (window/request-frame (:window ctx)))))

(defn on-paint-original [ctx ^Canvas canvas ^IPoint size]
  (paint-frame ctx canvas size *frame-image "Original"))

(defn on-paint-canny [ctx ^Canvas canvas ^IPoint size]
  (paint-frame ctx canvas size *frame-image-canny "Canny Edge Detection"))

(defn ui []
  [ui/column
   ;; Status bar
   [ui/padding {:padding 10}
    [ui/row {:gap 10}
     [ui/label "Status:"]
     [ui/label *status]
     [ui/gap {:width 20}]
     [ui/label "FPS:"]
     [ui/label *fps]]]

   ;; URL input and controls
   [ui/padding {:padding 10}
    [ui/row {:gap 10}
     [ui/label "URL:"]
     [ui/text-field {:*state *url}]
     [ui/button
      {:on-click (fn [_]
                   (when-not @*running?
                     (start-capture! (:text @*url))))}
      "Start"]
     [ui/button
      {:on-click (fn [_] (stop-capture!))}
      "Stop"]]]

   ;; Video canvases - Original and Canny side by side
   ^{:stretch 1}
   [ui/padding {:padding 10}
    [ui/row {:gap 10}
     ;; Original stream
     ^{:stretch 1}
     [ui/column
      [ui/padding {:padding 5}
       [ui/label {:font-weight :bold} "Original Stream"]]
      ^{:stretch 1}
      [ui/canvas {:on-paint on-paint-original}]]
     ;; Canny edge detection
     ^{:stretch 1}
     [ui/column
      [ui/padding {:padding 5}
       [ui/label {:font-weight :bold} "Canny Edge Detection"]]
      ^{:stretch 1}
      [ui/canvas {:on-paint on-paint-canny}]]]]])

(comment
  ;; Start the webcam viewer
  (ui/start-app!
    (ui/window
      {:title "HumbleUI Webcam - Original & Canny Edge Detection"
       :width 1400
       :height 700
       :on-close cleanup!}
      #'ui))

  ;; Test with specific URL
  (swap! *url assoc :text "http://esp32-31a6d4:81/")
  (start-capture! (:text @*url))

  ;; Stop capture
  (stop-capture!)

  ;; Cleanup
  (cleanup!)

  )
