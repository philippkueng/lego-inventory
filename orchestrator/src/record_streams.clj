(ns record-streams
  (:import [org.bytedeco.javacv FFmpegFrameGrabber FFmpegFrameRecorder CanvasFrame]
           [javax.swing JFrame]))

(org.bytedeco.ffmpeg.global.avutil/av_log_set_level
  org.bytedeco.ffmpeg.global.avutil/AV_LOG_ERROR)  ; Only show errors

(defn record-stream
  "Records an MJPEG stream to a file. Returns a map with controls.

   Options:
   - :duration - Duration in seconds (optional, records indefinitely if not specified)
   - :framerate - Output framerate (default: 25)
   - :video-codec - Codec to use (default: 'h264', options: 'mpeg4', 'mjpeg', etc.)
   - :file-format - Output format (default: auto-detected from filename extension)

   Example:
   (def recording (record-stream
                    \"http://esp32-31a6d4:81/\"
                    \"recordings/camera1.mp4\"
                    {:duration 30 :framerate 10}))"
  ([url output-file] (record-stream url output-file {}))
  ([url output-file {:keys [duration framerate video-codec file-format]
                     :or {framerate 25
                          video-codec "h264"}}]
   (let [grabber (FFmpegFrameGrabber. url)
         running? (atom true)
         start-time (System/currentTimeMillis)]
     (.start grabber)

     ;; Get video properties from the first frame
     (let [first-frame (.grab grabber)
           width (.imageWidth first-frame)
           height (.imageHeight first-frame)

           ;; Create recorder with the detected properties
           recorder (FFmpegFrameRecorder. output-file width height)]

       ;; Set format if specified
       (when file-format
         (.setFormat recorder file-format))

       ;; Set framerate
       (.setFrameRate recorder framerate)

       ;; Set video codec
       (when video-codec
         (case video-codec
           "h264" (.setVideoCodec recorder org.bytedeco.ffmpeg.global.avcodec/AV_CODEC_ID_H264)
           "mpeg4" (.setVideoCodec recorder org.bytedeco.ffmpeg.global.avcodec/AV_CODEC_ID_MPEG4)
           "mjpeg" (.setVideoCodec recorder org.bytedeco.ffmpeg.global.avcodec/AV_CODEC_ID_MJPEG)
           nil))

       (.start recorder)

       ;; Record the first frame
       (.record recorder first-frame)

       (let [recording-future
             (future
               (try
                 (println (format "Recording %s to %s..." url output-file))
                 (loop [frame-count 1]
                   (when @running?
                     (when-let [frame (.grab grabber)]
                       (when (.image frame)
                         (.record recorder frame)

                         ;; Check duration if specified
                         (when duration
                           (let [elapsed (/ (- (System/currentTimeMillis) start-time) 1000.0)]
                             (when (>= elapsed duration)
                               (reset! running? false))))

                         ;; Print progress every 100 frames
                         (when (zero? (mod frame-count 100))
                           (let [elapsed (/ (- (System/currentTimeMillis) start-time) 1000.0)]
                             (println (format "Recorded %d frames (%.1f seconds)"
                                            frame-count elapsed))))

                         (recur (inc frame-count))))))

                 (let [elapsed (/ (- (System/currentTimeMillis) start-time) 1000.0)]
                   (println (format "Recording finished: %s (%.1f seconds)" output-file elapsed)))

                 (catch Exception e
                   (println "Recording error:" (.getMessage e)))
                 (finally
                   (.stop recorder)
                   (.release recorder)
                   (.stop grabber)
                   (.release grabber))))]

         {:grabber grabber
          :recorder recorder
          :future recording-future
          :running? running?
          :output-file output-file})))))

(defn stop-recording
  "Stops an ongoing recording"
  [{:keys [running? output-file]}]
  (println "Stopping recording...")
  (reset! running? false)
  (println (format "Recording saved to: %s" output-file)))

(defn playback-file
  "Plays back a recorded video file in a window"
  [file-path window-title]
  (let [grabber (FFmpegFrameGrabber. file-path)
        canvas (doto (CanvasFrame. window-title)
                 (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE))
        running? (atom true)]
    (.start grabber)

    (let [playback-future
          (future
            (try
              (println (format "Playing back: %s" file-path))
              (loop []
                (when (and @running? (.isVisible canvas))
                  (if-let [frame (.grab grabber)]
                    (do
                      (.showImage canvas frame)
                      ;; Sleep to maintain approximate framerate
                      (Thread/sleep (long (/ 1000.0 (.getFrameRate grabber))))
                      (recur))
                    ;; End of file - loop back to beginning
                    (do
                      (.setFrameNumber grabber 0)
                      (recur)))))

              (println "Playback stopped")

              (catch Exception e
                (println "Playback error:" (.getMessage e)))
              (finally
                (.stop grabber)
                (.release grabber)
                (.dispose canvas))))]

      {:grabber grabber
       :canvas canvas
       :future playback-future
       :running? running?
       :file-path file-path})))

(defn stop-playback
  "Stops playback"
  [{:keys [running? canvas]}]
  (reset! running? false)
  (when canvas
    (.dispose canvas)))

(defn record-stream-with-display
  "Records a stream while also displaying it"
  ([url output-file] (record-stream-with-display url output-file {}))
  ([url output-file options]
   (let [{:keys [duration framerate video-codec file-format]
          :or {framerate 25
               video-codec "h264"}} options
         grabber (FFmpegFrameGrabber. url)
         canvas (doto (CanvasFrame. (str "Recording: " output-file))
                  (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE))
         running? (atom true)
         start-time (System/currentTimeMillis)]
     (.start grabber)

     (let [first-frame (.grab grabber)
           width (.imageWidth first-frame)
           height (.imageHeight first-frame)

           recorder (FFmpegFrameRecorder. output-file width height)]

       ;; Set format if specified
       (when file-format
         (.setFormat recorder file-format))

       ;; Set framerate
       (.setFrameRate recorder framerate)

       ;; Set video codec
       (when video-codec
         (case video-codec
           "h264" (.setVideoCodec recorder org.bytedeco.ffmpeg.global.avcodec/AV_CODEC_ID_H264)
           "mpeg4" (.setVideoCodec recorder org.bytedeco.ffmpeg.global.avcodec/AV_CODEC_ID_MPEG4)
           "mjpeg" (.setVideoCodec recorder org.bytedeco.ffmpeg.global.avcodec/AV_CODEC_ID_MJPEG)
           nil))

       (.start recorder)
       (.record recorder first-frame)
       (.showImage canvas first-frame)

       (let [recording-future
             (future
               (try
                 (println (format "Recording %s to %s..." url output-file))
                 (loop [frame-count 1]
                   (when (and @running? (.isVisible canvas))
                     (when-let [frame (.grab grabber)]
                       (when (.image frame)
                         ;; Record frame
                         (.record recorder frame)
                         ;; Display frame
                         (.showImage canvas frame)

                         ;; Check duration
                         (when duration
                           (let [elapsed (/ (- (System/currentTimeMillis) start-time) 1000.0)]
                             (when (>= elapsed duration)
                               (reset! running? false))))

                         ;; Progress
                         (when (zero? (mod frame-count 100))
                           (let [elapsed (/ (- (System/currentTimeMillis) start-time) 1000.0)]
                             (println (format "Recorded %d frames (%.1f seconds)"
                                            frame-count elapsed))))

                         (recur (inc frame-count))))))

                 (let [elapsed (/ (- (System/currentTimeMillis) start-time) 1000.0)]
                   (println (format "Recording finished: %s (%.1f seconds)" output-file elapsed)))

                 (catch Exception e
                   (println "Recording error:" (.getMessage e)))
                 (finally
                   (.stop recorder)
                   (.release recorder)
                   (.stop grabber)
                   (.release grabber)
                   (.dispose canvas))))]

         {:grabber grabber
          :recorder recorder
          :canvas canvas
          :future recording-future
          :running? running?
          :output-file output-file})))))

(defn simple-record [url output-file duration]
  (let [grabber (org.bytedeco.javacv.FFmpegFrameGrabber. url)
        running? (atom true)]
    (.start grabber)
    (let [first-frame (.grab grabber)
          recorder (org.bytedeco.javacv.FFmpegFrameRecorder.
                     output-file
                     (.imageWidth first-frame)
                     (.imageHeight first-frame))]
      (.setFrameRate recorder 10.0)
      (.start recorder)
      (future
        (try
          (println "Recording...")
          (.record recorder first-frame)
          (loop [frames 1]
            (when (and @running? (< frames (* duration 10)))
              (when-let [frame (.grab grabber)]
                (.record recorder frame)
                (when (zero? (mod frames 50))
                  (println "Frames:" frames))
                (recur (inc frames)))))
          (println "Done!")
          (finally
            (.stop recorder)
            (.release recorder)
            (.stop grabber)
            (.release grabber))))
      {:running? running?})))

(comment
  ;; Record a stream for 30 seconds
  (def rec1
    (record-stream
      "http://esp32-31a6d4:81/"
      "recordings/camera1.mp4"
      {:duration 30 :framerate 10 :file-format "mp4"}))

  ;; Stop recording early if needed
  (stop-recording rec1)

  (def rec1
    (record-stream
      "http://esp32-31a6d4:81/"
      "recordings/camera1.avi"
      {:duration 30 :framerate 10 :video-codec "mjpeg" :file-format "avi"}))

  (def rec1 (simple-record "http://esp32-31a6d4:81/" "recordings/test.mp4" 10))

  ;; Record indefinitely (stop manually)
  (def rec2
    (record-stream
      "http://esp32-31a6d4:81/"
      "recordings/camera1-long.mp4"
      {:framerate 15}))

  (stop-recording rec2)

  ;; Record with display
  (def rec3
    (record-stream-with-display
      "http://esp32-31a6d4:81/"
      "recordings/camera1-preview.mp4"
      {:duration 60 :framerate 10}))

  (stop-recording rec3)

  ;; Playback a recorded file
  (def playback
    (playback-file
      "recordings/camera1.mp4"
      "Camera 1 Playback"))

  (stop-playback playback)

  ;; Record all 4 cameras simultaneously
  (def recordings
    [(record-stream "http://esp32-318a20:81/" "recordings/cam1.mp4" {:duration 60 :framerate 10})
     (record-stream "http://esp32-31a6d4:81/" "recordings/cam2.mp4" {:duration 60 :framerate 10})
     (record-stream "http://esp32-3121b0:81/" "recordings/cam3.mp4" {:duration 60 :framerate 10})
     (record-stream "http://esp32-317b50:81/" "recordings/cam4.mp4" {:duration 60 :framerate 10})])

  ;; Stop all recordings
  (doseq [rec recordings]
    (stop-recording rec))

  ;; Different formats/codecs
  (def rec-avi
    (record-stream
      "http://esp32-31a6d4:81/"
      "recordings/camera1.avi"
      {:duration 30 :framerate 10 :video-codec "mpeg4"}))

  (def rec-mjpeg
    (record-stream
      "http://esp32-31a6d4:81/"
      "recordings/camera1-mjpeg.avi"
      {:duration 30 :framerate 10 :video-codec "mjpeg"})))

(comment

  ;; record the lego train
  (def lego (record-stream "http://esp32-a19c2c:81/" "recordings/lego_train.mp4" {:framerate 10}))

  (stop-recording lego)

  )

