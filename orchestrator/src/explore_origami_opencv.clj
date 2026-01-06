(ns explore-origami-opencv
  (:require [opencv4.core :as ocvc]
            [opencv4.utils :as u]
            [opencv4.video :as v]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [origami-dnn.net.yolo :as yolo]
            [origami-dnn.draw :as d]
            [opencv4.dnn.core :as origami-dnn])
  (:import (org.bytedeco.javacv FFmpegFrameGrabber OpenCVFrameConverter$ToMat CanvasFrame)
           [org.bytedeco.opencv.opencv_core Mat]
           [org.opencv.core Core]
           [org.opencv.videoio VideoCapture VideoWriter Videoio]
           [org.bytedeco.javacv Java2DFrameConverter]
           [org.bytedeco.opencv.global opencv_imgproc]
           [javax.swing JFrame]))

;; Following the instructions on https://hellonico.github.io/origami-docs/#/units/guide?id=_2-minutes-intro-using-clj
;; but there was a SSL issue so `clj` failed to download the packages. Hence I've manually downloaded them and
;; put them into my `.m2/` directory.

(comment
  ;; blurring a picture
  (-> "photo.jpg"
    (ocvc/imread)
    (ocvc/gaussian-blur! (ocvc/new-size 17 17) 9 9)
    (ocvc/imwrite "photo_blurred.jpg"))



  (->
    (ocvc/imread "photo.jpg")
    (ocvc/cvt-color! ocvc/COLOR_RGB2GRAY)
    (ocvc/canny! 300.0 100.0 3 true)
    (ocvc/bitwise-not!)
    (u/resize-by 0.5)
    (ocvc/imwrite "photo_canny.jpg"))

  )


#_(defn open-mjpeg-stream
  "Opens an MJPEG stream and returns a VideoCapture object"
  [url]
  (doto (VideoCapture.)
    (.open url)))

;(defn open-mjpeg-stream
;  "Opens an MJPEG stream and returns a VideoCapture object"
;  [url]
;  (v/new-videocapture url))
;
;(defn read-frame
;  "Reads a single frame from the stream. Returns a Mat or nil if failed."
;  [^VideoCapture capture]
;  (let [frame (Mat.)]
;    (when (.read capture frame)
;      frame)))
;
;(defn process-stream
;  "Continuously reads frames from an MJPEG stream and processes them"
;  [url process-fn]
;  (let [capture (open-mjpeg-stream url)]
;    (when (.isOpened capture)
;      (try
;        (loop []
;          (when-let [frame (read-frame capture)]
;            (process-fn frame)
;            (recur)))
;        (finally
;          (.release capture))))))

(defn process-stream
  "Continuously reads frames from an MJPEG stream"
  [url process-fn]
  (let [grabber (FFmpegFrameGrabber. url)
        converter (OpenCVFrameConverter$ToMat.)]
    (.start grabber)
    (try
      (loop []
        (when-let [frame (.grab grabber)]
          (when-let [mat (and (.image frame) (.convert converter frame))]
            (try
              (process-fn mat)
              (finally
                (.release mat))))
          (recur)))
      (catch Exception e
        (println "Stream error:" (.getMessage e)))
      (finally
        (.stop grabber)
        (.release grabber)))))


(defn display-stream
  "Opens an MJPEG stream and displays it in a window"
  [url window-title]
  (let [grabber (FFmpegFrameGrabber. url)
        canvas (CanvasFrame. window-title)]
    (.start grabber)
    ;; Make window closeable
    (.setDefaultCloseOperation canvas javax.swing.JFrame/EXIT_ON_CLOSE)
    (try
      (loop []
        (when-let [frame (.grab grabber)]
          (when (and (.isVisible canvas) (.image frame))
            (.showImage canvas frame)
            (recur))))
      (catch Exception e
        (println "Stream error:" (.getMessage e)))
      (finally
        (.stop grabber)
        (.release grabber)
        (.dispose canvas)))))


(defn start-stream-display
  "Starts displaying an MJPEG stream. Returns a map with :canvas, :grabber, and :future.
   Call (stop-stream-display stream) to close it."
  [url window-title]
  (let [grabber (FFmpegFrameGrabber. url)
        canvas (doto (CanvasFrame. window-title)
                 (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE))
        running? (atom true)]
    (.start grabber)
    (let [stream-future
          (future
            (try
              (loop []
                (when (and @running? (.isVisible canvas))
                  (when-let [frame (.grab grabber)]
                    (when (.image frame)
                      (.showImage canvas frame))
                    (recur))))
              (catch Exception e
                (println "Stream error:" (.getMessage e)))
              (finally
                (.stop grabber)
                (.release grabber)
                (.dispose canvas))))]
      {:canvas canvas
       :grabber grabber
       :future stream-future
       :running? running?})))

(defn start-stream-display-with-processing
  "Starts displaying and processing an MJPEG stream"
  [url window-title process-fn]
  (let [grabber (FFmpegFrameGrabber. url)
        canvas (doto (CanvasFrame. window-title)
                 (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE))
        converter (OpenCVFrameConverter$ToMat.)
        running? (atom true)]
    (.start grabber)
    (let [stream-future
          (future
            (try
              (loop []
                (when (and @running? (.isVisible canvas))
                  (when-let [frame (.grab grabber)]
                    (when (.image frame)
                      (.showImage canvas frame)
                      ;; Process the frame
                      (when-let [mat (.convert converter frame)]
                        (try
                          (process-fn mat)
                          (finally
                            (.release mat)))))
                    (recur))))
              (catch Exception e
                (println "Stream error:" (.getMessage e)))
              (finally
                (.stop grabber)
                (.release grabber)
                (.dispose canvas))))]
      {:canvas canvas
       :grabber grabber
       :converter converter
       :future stream-future
       :running? running?})))

(defn start-stream-display-with-opencv-processing
  "Displays stream with custom OpenCV processing function.
   process-fn takes a Mat and should return a processed Mat (or nil to show original)"
  [url window-title process-fn]
  (let [grabber (FFmpegFrameGrabber. url)
        canvas (doto (CanvasFrame. window-title)
                 (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE))
        mat-converter (OpenCVFrameConverter$ToMat.)
        running? (atom true)]
    (.start grabber)
    (let [stream-future
          (future
            (try
              (loop []
                (when (and @running? (.isVisible canvas))
                  (when-let [frame (.grab grabber)]
                    (when (.image frame)
                      (when-let [mat (.convert mat-converter frame)]
                        (try
                          ;; Process the mat
                          (if-let [processed-mat (process-fn mat)]
                            (do
                              ;; Show processed version
                              (let [processed-frame (.convert mat-converter processed-mat)]
                                (.showImage canvas processed-frame))
                              ;; Clean up processed mat if different from original
                              (when-not (identical? processed-mat mat)
                                (.release processed-mat)))
                            ;; Show original if process-fn returns nil
                            (.showImage canvas frame))
                          (finally
                            (.release mat)))))
                    (recur))))
              (catch Exception e
                (println "Stream error:" (.getMessage e)))
              (finally
                (.stop grabber)
                (.release grabber)
                (.dispose canvas))))]
      {:canvas canvas
       :grabber grabber
       :converter mat-converter
       :future stream-future
       :running? running?})))

(defn stop-stream-display
  "Stops and closes a stream display"
  [{:keys [canvas grabber running?]}]
  (reset! running? false)
  (when canvas
    (.dispose canvas))
  (when grabber
    (try
      (.stop grabber)
      (.release grabber)
      (catch Exception e
        (println "Error stopping grabber:" (.getMessage e))))))


(comment

  ;; Process a single camera
  (process-stream
    "http://esp32-31a6d4:81/"
    (fn [frame]
      (println "Got frame:" (.rows frame) "x" (.cols frame))))

  (process-stream
    "http://esp32-31a6d4:81/"
    (fn [^Mat mat]
      (println "Got frame:" (.rows mat) "x" (.cols mat))))


  (def cam1
    (start-stream-display "http://esp32-31a6d4:81/" "Camera 1"))

  (stop-stream-display cam1)

  (def cam1
    (start-stream-display-with-opencv-processing
      "http://esp32-31a6d4:81/"
      "Camera 1 - Grayscale"
      (fn [^Mat mat]
        (let [gray (Mat.)]
          (opencv_imgproc/cvtColor mat gray opencv_imgproc/CV_BGR2GRAY)
          gray))))

  (def cam1
    (start-stream-display-with-opencv-processing
      "http://esp32-31a6d4:81/"
      "Camera 1 - Edges"
      (fn [^Mat mat]
        (let [gray (Mat.)
              edges (Mat.)]
          (opencv_imgproc/cvtColor mat gray opencv_imgproc/CV_BGR2GRAY)
          (opencv_imgproc/Canny gray edges 50.0 150.0)
          (.release gray)
          edges))))

  (def cam1
    (let [[net _ labels]
          #_(origami-dnn/read-net-from-repo "networks.yolo:yolov3-tiny:1.0.0")
          (origami-dnn/read-net-from-folder "/Users/philippkueng/programming/Clojure/lego-inventory/orchestrator/models/yolo")]
      (start-stream-display-with-processing
        "http://esp32-31a6d4:81/"
        "Camera 1"
        (fn [^Mat mat]
          (println "Frame:" (.rows mat) "x" (.cols mat))))))

  ;; -----

  (u/simple-cam-window
    (fn [buffer]
      (u/resize-by buffer 0.4)
      (let [ output (ocvc/new-mat) bottom (-> buffer ocvc/clone (ocvc/flip! -1)) ]
        (-> buffer (ocvc/cvt-color! ocvc/COLOR_RGB2GRAY) (ocvc/cvt-color! ocvc/COLOR_GRAY2RGB))
        (ocvc/put-text buffer (str (java.util.Date.)) (ocvc/new-point 10 50) ocvc/FONT_HERSHEY_PLAIN 1 (ocvc/new-scalar 255 255 0) 1)
        (ocvc/vconcat [buffer bottom] output)
        output)))

  ;; -----



  (let [[net _ labels]
        #_(origami-dnn/read-net-from-repo "networks.yolo:yolov3-tiny:1.0.0")
        (origami-dnn/read-net-from-folder "/Users/philippkueng/programming/Clojure/lego-inventory/orchestrator/models/yolo")]
    (u/simple-cam-window
      #_{:frame {:fps true}}
      (fn [buffer]
        (-> buffer
          (yolo/find-objects net)
          (d/blue-boxes! labels)))))


  (let [[net _ labels]
        #_(origami-dnn/read-net-from-repo "networks.yolo:yolov3-tiny:1.0.0")
        (origami-dnn/read-net-from-folder "/Users/philippkueng/programming/Clojure/lego-inventory/orchestrator/models/yolo")]
    (u/simple-cam-window
      #_{:frame {:fps true}}
      {:frame {:width 500} :video {:device "video_of_raw_images.mp4"}}
      (fn [buffer]
        (-> buffer
          (yolo/find-objects net)
          (d/blue-boxes! labels)))))

  ;; stream & write
  (let [input "http://esp32-31a6d4:81/"
        output-file "stream_output.mp4"
        fourcc 1196444237
        ;[net opts labels] (origami-dnn/read-net-from-repo "networks.caffe:mobilenet:1.0.0")
        cap (ocvc/new-videocapture)
        _ (.open cap input)
        ;stream-size (rotated-video-size cap)
        buffer (ocvc/new-mat)
        w (v/new-videowriter)]
    ;(println "Rotated Stream size:\t" stream-size)
    (println (.open w output-file  fourcc 12 cap))
    (while (.read cap buffer)
      (let [annon
            (-> buffer
              ;(rotate! ROTATE_90_CLOCKWISE)
              ;(find-objects net opts)
              ;(d/blue-boxes! labels)
              )]
        (.write w annon)
        ))
    (.release w)
    (.release cap))


  ;; -----


  (System/setProperty "org.opencv.core.Core" "true")

  org.opencv.core.Core/NATIVE_LIBRARY_NAME
  ;=> "opencv_java4120"

  ;/Users/philippkueng/.m2/repository/opencv/opencv-native/4.12.0-1/opencv-native-4.12.0-1.jar!/natives/osx_arm64/libopencv_java4120.dylib

  ;; Make sure OpenCV native library is loaded
  (clojure.lang.RT/loadLibrary org.opencv.core.Core/NATIVE_LIBRARY_NAME)

  (System/loadLibrary org.opencv.core.Core/NATIVE_LIBRARY_NAME)


  (clojure.lang.RT/loadLibrary "opencv_java41201")
  (clojure.lang.RT/loadLibrary "libopencv_java4120")

  ;; See what classes are available in org.opencv
  (->> (all-ns)
    (map ns-name)
    (filter #(re-find #"opencv" (str %))))
  ;=> (opencv4.filter opencv4.colors.rgb opencv4.video explore-origami-opencv opencv4.core opencv4.utils)


  (defn find-opencv-jars []
    (println "=== OpenCV JARs on Classpath ===")
    (->> (str/split (System/getProperty "java.class.path")
           (re-pattern (System/getProperty "path.separator")))
      (filter #(re-find #"opencv" %))
      (run! println)))

  (defn list-jar-natives [jar-path]
    (try
      (with-open [jar (java.util.jar.JarFile. jar-path)]
        (->> (enumeration-seq (.entries jar))
          (map #(.getName %))
          (filter #(or (str/ends-with? % ".so")
                     (str/ends-with? % ".dylib")
                     (str/ends-with? % ".dll")))
          doall))
      (catch Exception e
        (println "Error reading JAR:" (.getMessage e))
        [])))

  (defn check-opencv-natives []
    (println "\n=== Native Libraries in OpenCV JARs ===")
    (->> (str/split (System/getProperty "java.class.path")
           (re-pattern (System/getProperty "path.separator")))
      (filter #(re-find #"opencv" %))
      (mapcat (fn [jar]
                (println "\nIn" jar ":")
                (let [natives (list-jar-natives jar)]
                  (run! #(println "  " %) natives)
                  natives)))))

  (defn check-opencv-classes []
    (println "\n=== Checking OpenCV Core ===")
    (try
      (import 'org.opencv.core.Core)
      (println "Core class loaded successfully")
      (println "NATIVE_LIBRARY_NAME:" org.opencv.core.Core/NATIVE_LIBRARY_NAME)
      true
      (catch Exception e
        (println "Failed to load Core:" (.getMessage e))
        false)))


  (find-opencv-jars)

  (check-opencv-natives)

  (check-opencv-classes)

  ;; Check the build information
  (println (org.opencv.core.Core/getBuildInformation))

  )
