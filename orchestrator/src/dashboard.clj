(ns dashboard
  (:require [clojure.string :as str])
  (:import [javax.swing JFrame JPanel JButton JLabel SwingConstants BoxLayout Box BorderFactory]
           [java.awt BorderLayout GridLayout Dimension Color]
           [org.bytedeco.javacv FFmpegFrameGrabber CanvasFrame]))

(org.bytedeco.ffmpeg.global.avutil/av_log_set_level
  org.bytedeco.ffmpeg.global.avutil/AV_LOG_ERROR)  ; Only show errors

(defn create-control-panel
  "Creates the left control panel with buttons and status labels"
  []
  (let [panel (JPanel.)]
    (.setLayout panel (BoxLayout. panel BoxLayout/Y_AXIS))
    (.setBorder panel (BorderFactory/createEmptyBorder 10 10 10 10))

    ;; Title
    (let [title (doto (JLabel. "Machine Control")
                  (.setFont (.deriveFont (.getFont (JLabel.)) 18.0))
                  (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT))]
      (.add panel title))

    (.add panel (Box/createRigidArea (Dimension. 0 20)))

    ;; Status section
    (let [status-title (doto (JLabel. "Status")
                         (.setFont (.deriveFont (.getFont (JLabel.)) 14.0))
                         (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT))]
      (.add panel status-title))

    (.add panel (Box/createRigidArea (Dimension. 0 10)))

    ;; Status labels (will be updated dynamically)
    (let [machine-status (doto (JLabel. "Machine: Idle")
                           (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT))
          camera-status (doto (JLabel. "Cameras: Disconnected")
                          (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT))]
      (.add panel machine-status)
      (.add panel camera-status))

    (.add panel (Box/createRigidArea (Dimension. 0 30)))

    ;; Control buttons
    (let [controls-title (doto (JLabel. "Controls")
                           (.setFont (.deriveFont (.getFont (JLabel.)) 14.0))
                           (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT))]
      (.add panel controls-title))

    (.add panel (Box/createRigidArea (Dimension. 0 10)))

    (let [start-btn (doto (JButton. "Start Machine")
                      (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT)
                      (.setMaximumSize (Dimension. 200 30)))
          stop-btn (doto (JButton. "Stop Machine")
                     (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT)
                     (.setMaximumSize (Dimension. 200 30)))
          capture-btn (doto (JButton. "Capture Image")
                        (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT)
                        (.setMaximumSize (Dimension. 200 30)))
          record-btn (doto (JButton. "Start Recording")
                       (.setAlignmentX java.awt.Component/CENTER_ALIGNMENT)
                       (.setMaximumSize (Dimension. 200 30)))]

      ;; Add button actions (to be implemented)
      (.addActionListener start-btn
        (reify java.awt.event.ActionListener
          (actionPerformed [_ e]
            (println "Start Machine clicked"))))

      (.addActionListener stop-btn
        (reify java.awt.event.ActionListener
          (actionPerformed [_ e]
            (println "Stop Machine clicked"))))

      (.addActionListener capture-btn
        (reify java.awt.event.ActionListener
          (actionPerformed [_ e]
            (println "Capture Image clicked"))))

      (.addActionListener record-btn
        (reify java.awt.event.ActionListener
          (actionPerformed [_ e]
            (println "Start Recording clicked"))))

      (.add panel start-btn)
      (.add panel (Box/createRigidArea (Dimension. 0 5)))
      (.add panel stop-btn)
      (.add panel (Box/createRigidArea (Dimension. 0 5)))
      (.add panel capture-btn)
      (.add panel (Box/createRigidArea (Dimension. 0 5)))
      (.add panel record-btn))

    ;; Add glue to push everything to the top
    (.add panel (Box/createVerticalGlue))

    panel))

(defn start-camera-stream
  "Starts a camera stream in a panel. Returns a map with stream controls."
  [url panel-width panel-height]
  (let [grabber (FFmpegFrameGrabber. url)
        panel (JPanel.)
        canvas-label (JLabel.)
        running? (atom true)]

    (.setLayout panel (BorderLayout.))
    (.setPreferredSize panel (Dimension. panel-width panel-height))
    (.setMinimumSize panel (Dimension. panel-width panel-height))
    (.setBorder panel (BorderFactory/createLineBorder Color/GRAY 2))

    ;; Set canvas label to show "Loading..." initially
    (.setText canvas-label "Loading camera...")
    (.setHorizontalAlignment canvas-label SwingConstants/CENTER)
    (.add panel canvas-label BorderLayout/CENTER)

    (.start grabber)

    (let [stream-future
          (future
            (try
              (let [frame-converter (org.bytedeco.javacv.Java2DFrameConverter.)]
                (loop []
                  (when @running?
                    (when-let [frame (.grab grabber)]
                      (when (.image frame)
                        ;; Convert frame to BufferedImage and display
                        (let [buffered-image (.convert frame-converter frame)]
                          (when buffered-image
                            ;; Scale image to fit panel
                            (let [scaled (java.awt.image.BufferedImage.
                                          panel-width panel-height
                                          java.awt.image.BufferedImage/TYPE_3BYTE_BGR)
                                  g (.createGraphics scaled)]
                              (.drawImage g buffered-image 0 0 panel-width panel-height nil)
                              (.dispose g)
                              ;; Update label with image
                              (.setIcon canvas-label (javax.swing.ImageIcon. scaled)))))
                        (recur))))))
              (catch Exception e
                (println "Stream error:" (.getMessage e)))
              (finally
                (.stop grabber)
                (.release grabber))))]

      {:panel panel
       :grabber grabber
       :future stream-future
       :running? running?
       :url url})))

(defn stop-camera-stream
  "Stops a camera stream"
  [{:keys [grabber running?]}]
  (reset! running? false)
  (when grabber
    (try
      (.stop grabber)
      (.release grabber)
      (catch Exception e
        (println "Error stopping stream:" (.getMessage e))))))

(defn create-camera-column
  "Creates a column with 2 camera streams stacked vertically"
  [camera-urls panel-width panel-height]
  (let [column (JPanel.)
        stream-height (/ panel-height 2)]

    (.setLayout column (BoxLayout. column BoxLayout/Y_AXIS))
    (.setBorder column (BorderFactory/createEmptyBorder 5 5 5 5))

    ;; Start streams and add panels
    (let [streams (mapv #(start-camera-stream % panel-width stream-height) camera-urls)]
      (doseq [{:keys [panel]} streams]
        (.add column panel)
        (.add column (Box/createRigidArea (Dimension. 0 5))))

      {:column column
       :streams streams})))

(defn create-dashboard
  "Creates the main dashboard window with control panel and 4 camera streams.

   camera-urls: Vector of 4 camera URLs
   Example: [\"http://esp32-31a6d4:81/\"
             \"http://esp32-318a20:81/\"
             \"http://esp32-camera3:81/\"
             \"http://esp32-camera4:81/\"]"
  [camera-urls]
  (when (not= (count camera-urls) 4)
    (throw (IllegalArgumentException. "Expected 4 camera URLs")))

  (let [frame (JFrame. "LEGO Inventory - Machine Dashboard")
        main-panel (JPanel. (BorderLayout.))

        ;; Create the three columns
        control-panel (create-control-panel)

        ;; Camera columns (each with 2 streams)
        camera-column-width 400
        camera-column-height 600

        left-cameras (create-camera-column
                       [(nth camera-urls 0) (nth camera-urls 1)]
                       camera-column-width
                       camera-column-height)

        right-cameras (create-camera-column
                        [(nth camera-urls 2) (nth camera-urls 3)]
                        camera-column-width
                        camera-column-height)]

    ;; Set preferred size for control panel
    (.setPreferredSize control-panel (Dimension. 250 camera-column-height))

    ;; Add columns to main panel
    (.add main-panel control-panel BorderLayout/WEST)
    (.add main-panel (:column left-cameras) BorderLayout/CENTER)
    (.add main-panel (:column right-cameras) BorderLayout/EAST)

    ;; Configure frame
    (.setContentPane frame main-panel)
    (.setDefaultCloseOperation frame JFrame/DISPOSE_ON_CLOSE)
    (.pack frame)
    (.setLocationRelativeTo frame nil) ; Center on screen
    (.setVisible frame true)

    ;; Return dashboard state
    {:frame frame
     :control-panel control-panel
     :left-cameras (:streams left-cameras)
     :right-cameras (:streams right-cameras)
     :all-streams (concat (:streams left-cameras) (:streams right-cameras))}))

(defn close-dashboard
  "Closes the dashboard and stops all camera streams"
  [{:keys [frame all-streams]}]
  (println "Closing dashboard...")

  ;; Stop all camera streams
  (doseq [stream all-streams]
    (stop-camera-stream stream))

  ;; Close the window
  (when frame
    (.dispose frame))

  (println "Dashboard closed"))

(comment
  ;; Create dashboard with 4 cameras
  (def dashboard
    (create-dashboard
      ["http://esp32-318a20:81/"
       "http://esp32-31a6d4:81/"
       "http://esp32-3121b0:81/"
       "http://esp32-317b50:81/"]))

  ;; Close the dashboard
  (close-dashboard dashboard)

  ;; For testing with a single camera URL repeated
  (def dashboard-test
    (create-dashboard
      ["http://esp32-31a6d4:81/"
       "http://esp32-31a6d4:81/"
       "http://esp32-31a6d4:81/"
       "http://esp32-31a6d4:81/"]))

  (close-dashboard dashboard-test))
