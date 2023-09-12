(ns user)

(comment
  ;; playing with the LED

  ;; turning it on
  (def blue-led js/D5)

  (defn light-on []
    (.write blue-led false) nil)

  (light-on)

  ;; toggle the led
  (def led-status (atom true))
  (defn toggle []
    (do
      (.write blue-led @led-status)
      (reset! led-status (not @led-status)))
    nil)

  (toggle)
)
