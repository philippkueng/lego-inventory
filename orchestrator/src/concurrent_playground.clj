(ns concurrent-playground)

(comment
  (let [background-task (fn []
                          (while true
                            (Thread/sleep 200)
                            (println "working ....")))
        background-process (Thread. background-task)]
    (.start background-process)
    (Thread/sleep 1000)                                     ;; do the foreground work
    (.stop background-process))



  )

