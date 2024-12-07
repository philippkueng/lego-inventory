(ns explore-clay-with-libpython
  (:require [scicloj.clay.v2.api :as clay]
            [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.- get-attr call-attr] :as py]
            [tech.v3.datatype :as dtype]
            [plot]
            [clojure.java.io :as io])
  (:import [javax.imageio ImageIO]))

^{:kindly/hide-code true}
(comment
  (clay/make! {:source-path "src/explore_clay_with_libpython.clj"
               :live-reload true})
  )


(require-python '[cv2 :bind-ns true])
(require-python '[matplotlib.pyplot :as pyplot :bind-ns true])

;; # Plotting images

;; ## Read an image, tint & grayscale it and display it as a plot
^{:kindly/hide-code true}
(let [img (pyplot/imread "images/2024_08_20_room_light.jpg")
      img-tinted (numpy/multiply img [1 0.95 0.9])]
  (plot/with-show
    (pyplot/figure :figsize [5 2])
    (pyplot/subplot 1 3 1)
    (pyplot/imshow img)
    (pyplot/title "Original")
    (pyplot/subplot 1 3 2)
    (pyplot/imshow (numpy/uint8 img-tinted))
    (pyplot/title "Tinted")
    (pyplot/subplot 1 3 3)
    (pyplot/imshow img :cmap "gray")
    (pyplot/title "Gray")))

;; ## Display a single image using pyplot
(let [img (pyplot/imread "images/2024_08_20_room_light.jpg")
      img-tinted (numpy/multiply img [1 0.95 0.9])]
  (plot/with-show
    (pyplot/figure :figsize [5 5])
    (pyplot/imshow (numpy/uint8 img-tinted))))

;; ## Diplay the modified image without using pyplot

(let [img (pyplot/imread "images/2024_08_20_room_light.jpg")
      img-tinted (numpy/multiply img [1 0.95 0.9])]
  (cv2/imwrite "new_image.jpg" (numpy/uint8 img-tinted))
  (ImageIO/read (io/file "new_image.jpg")))




