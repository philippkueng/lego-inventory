(ns machine
  (:require [components :as c]))

(comment
  (let [feeder-task (fn []
                      (while (not (Thread/interrupted))
                        (c/feeder-down!)
                        (c/feeder-up!)))
        feeder-process (Thread. feeder-task)

        ;conveyor-task (fn []
        ;                (try
        ;                  (while (not (Thread/interrupted))
        ;                    (c/start-stepper-motor!))
        ;                  (catch InterruptedException _
        ;                    (c/stop-stepper-motor!)
        ;                    nil)))
        ;conveyor-process (Thread. conveyor-task)
        ]
    (.start feeder-process)
    (c/start-stepper-motor!)
    (Thread/sleep 50000)                                     ;; do the foreground work
    (.interrupt feeder-process)
    (c/stop-stepper-motor!))

  )

