(ns hello_python
  (:require [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.- get-attr call-attr] :as py]
            [tech.v3.datatype :as dtype]
            [detection.plot :as plot])
  (:import (java.io File)
           [javax.imageio ImageIO]
           [java.net URL]))

;;;; have to set the headless mode before requiring pyplot
(def mplt (py/import-module "matplotlib"))
(py. mplt "use" "Agg")


(require-python '[cv2])
(require-python '[numpy :as np])
(require-python '[matplotlib.pyplot :as pyplot])
;(require-python '[matplotlib.backends.backend_agg :as backend_agg])

;import os
;import cv2
;import numpy as np
;import matplotlib.pyplot as plt
;
;from zipfile import ZipFile
;from urllib.request import urlretrieve
;
;from IPython.display import Image

;; (require-python '[tensorflow :as tf])

;# Split the image into the B,G,R components
;img_bgr = cv2.imread("images/2024_08_20_room_light.jpg", cv2.IMREAD_COLOR)
;
;b, g, r = cv2.split(img_bgr)
;
;# Show the channels
;plt.figure(figsize=[20, 5])
;
;plt.subplot(141);plt.imshow(r, cmap="gray");plt.title("Red Channel")
;plt.subplot(142);plt.imshow(g, cmap="gray");plt.title("Green Channel")
;plt.subplot(143);plt.imshow(b, cmap="gray");plt.title("Blue Channel")
;
;# Merge the individual channels into a BGR image
;imgMerged = cv2.merge((b, g, r))
;# Show the merged output
;plt.subplot(144)
;plt.imshow(imgMerged[:, :, ::-1])
;plt.title("Merged Output")

#_(ImageIO/read (File. "images/2024_08_20_room_light.jpg"))

;(defmacro with-show
;  "Takes forms with mathplotlib.pyplot to then show locally"
;  [& body]
;  `(let [_# (matplotlib.pyplot/clf)
;         fig# (matplotlib.pyplot/figure)
;         agg-canvas# (matplotlib.backends.backend_agg/FigureCanvasAgg fig#)]
;     ~(cons 'do body)
;     (py. agg-canvas# "draw")
;     (matplotlib.pyplot/savefig "temp.png")
;     (sh/sh "open" "temp.png")))

;(defmacro with-show
;  "Takes forms with mathplotlib.pyplot to then show locally"
;  [& body]
;  `(let [_# (pyplot/clf)
;         fig# (pyplot/figure)
;         agg-canvas# (backend_agg/FigureCanvasAgg fig#)]
;     ~(cons 'do body)
;     (py. agg-canvas# "draw")
;     (pyplot/savefig "temp.png")
;     (sh/sh "open" "temp.png")))

#_(let [x (numpy/arange 0 (* 3 numpy/pi) 0.1)
      y (numpy/sin x)]
  (with-show
    (matplotlib.pyplot/plot x y)))

;(type (pyplot/imread "images/2024_08_20_room_light.jpg"))

#_(let [img (pyplot/imread "images/2024_08_20_room_light.jpg")
      img-tinted (numpy/multiply img [1 0.95 0.9])
      ]
  (with-show
    (matplotlib.pyplot/subplot 1 2 1)
    (matplotlib.pyplot/imshow img)
    (matplotlib.pyplot/subplot 1 2 2)
    (matplotlib.pyplot/imshow (numpy/uint8 img-tinted))))

;(plot/imread)

(clojure.pprint/pprint (pyplot/imread "images/2024_08_20_room_light.jpg"))

;; todo: how can I create a pyplot figure & save it to disk?

