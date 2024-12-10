(ns explore-clay-with-libpython
  (:require [scicloj.clay.v2.api :as clay]
            [libpython-clj2.require :refer [require-python]]
            [libpython-clj2.python :refer [py. py.. py.- get-attr call-attr
                                           ->jvm] :as py]
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

;; ## Display the modified image without using pyplot
(let [img (pyplot/imread "images/2024_08_20_room_light.jpg")
      img-tinted (numpy/multiply img [1 0.95 0.9])]
  (cv2/imwrite "new_image.jpg" (numpy/uint8 img-tinted))
  (ImageIO/read (io/file "new_image.jpg")))

;; # Feature detection

;; ## Using the `goodFeaturesToTrack` function.
(def max-corners 23)
(def max-trackbar 100)
(def quality-level 0.01)
(def min-distance 10)
(def block-size 3)
(def gradient-size 3)
(def use-harris-detector? false)
(def k 0.04)
(def radius 4)

;; ## Just get the dimensions of the corners
(->> (cv2/goodFeaturesToTrack
         (cv2/cvtColor (cv2/imread "images/2024_08_20_room_light.jpg")
           cv2/COLOR_BGR2GRAY)
         max-corners
         quality-level
         min-distance
         nil
         :blockSize block-size
         :gradientSize gradient-size
         :useHarrisDetector use-harris-detector?
         :k k)
  (->jvm)
  (map first))

;(defn draw-circle [image center radius color thickness]
;  (py/call-attr cv2 "circle" image center radius color thickness))

;; ## Overlay the corners onto the image & display the image
(let [image (cv2/imread "images/2024_08_20_room_light.jpg")
      corners (cv2/goodFeaturesToTrack
                (cv2/cvtColor image
                  cv2/COLOR_BGR2GRAY)
                max-corners
                quality-level
                min-distance
                nil
                :blockSize block-size
                :gradientSize gradient-size
                :useHarrisDetector use-harris-detector?
                :k k)
      image-copy (numpy/copy image)]
  (doall
    (for [[x y] (map first (->jvm corners))]
      (cv2/circle image-copy [(int x) (int y)] 10 [0 255 0] 2)))
  (cv2/imwrite "new_image.jpg" image-copy))


(let [image (cv2/imread "images/2024_08_20_room_light.jpg")
      corners (cv2/goodFeaturesToTrack
                (cv2/cvtColor image
                  cv2/COLOR_BGR2GRAY)
                max-corners
                quality-level
                min-distance
                nil
                :blockSize block-size
                :gradientSize gradient-size
                :useHarrisDetector use-harris-detector?
                :k k)
      image-copy (numpy/copy image)]
  (doall
    (for [[x y] (map first (->jvm corners))]
      (cv2/circle image-copy [(int x) (int y)] 10 [0 255 0] 2)))
  (cv2/imwrite "new_image.jpg" image-copy)
  (ImageIO/read (io/file "new_image.jpg")))


(comment
  ;; let's get the corners
  (cv2/goodFeaturesToTrack
    (cv2/cvtColor (cv2/imread "images/2024_08_20_room_light.jpg")
      cv2/COLOR_BGR2GRAY)
    max-corners
    quality-level
    min-distance
    nil
    :blockSize block-size
    :gradientSize gradient-size
    :useHarrisDetector use-harris-detector?
    :k k)

  (meta cv2/goodFeaturesToTrack)

  )



