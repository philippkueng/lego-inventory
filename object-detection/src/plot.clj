(ns plot
  (:require [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.- get-attr call-attr] :as py]
            [tech.v3.datatype :as dtype]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io])
  (:import (java.io File)
           [javax.imageio ImageIO]
           [java.net URL]))

;;;; have to set the headless mode before requiring pyplot
(def mplt (py/import-module "matplotlib"))
(py. mplt "use" "Agg")

(require-python '[numpy :as np])
(require-python '[matplotlib.pyplot :as pyplot])
(require-python '[matplotlib.backends.backend_agg :as backend_agg])

;(defn imread []
;  (type (pyplot/imread "images/2024_08_20_room_light.jpg")))

(comment
  (imread)


  (meta (ns-resolve 'matplotlib.pyplot 'imread))

  )

(defmacro with-show
  "Takes forms with matplotlib.pyplot to then show locally"
  [& body]
  `(let [_# (matplotlib.pyplot/clf)
         fig# (matplotlib.pyplot/figure)
         agg-canvas# (matplotlib.backends.backend_agg/FigureCanvasAgg fig#)]
     ~(cons 'do body)
     (py. agg-canvas# "draw")
     (matplotlib.pyplot/savefig "temp.png")
     (ImageIO/read (io/file "temp.png"))))

(comment

  ;# Show the channels
  ;plt.figure(figsize=[20, 5])
  ;
  ;plt.subplot(141);plt.imshow(r, cmap="gray");plt.title("Red Channel")
  ;plt.subplot(142);plt.imshow(g, cmap="gray");plt.title("Green Channel")
  ;plt.subplot(143);plt.imshow(b, cmap="gray");plt.title("Blue Channel")

  (let [img (pyplot/imread "images/2024_08_20_room_light.jpg")
        img-tinted (numpy/multiply img [1 0.95 0.9])
        ]
    (with-show
      (matplotlib.pyplot/subplot 1 3 1)
      (matplotlib.pyplot/imshow img)
      (matplotlib.pyplot/subplot 1 3 2)
      (matplotlib.pyplot/imshow (numpy/uint8 img-tinted))
      (matplotlib.pyplot/subplot 1 3 3)
      (matplotlib.pyplot/imshow img)))

  )

