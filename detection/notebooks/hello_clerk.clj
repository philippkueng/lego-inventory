;; # testing clerk

(ns hello-clerk
  (:import [javax.imageio ImageIO]
           [java.net URL]))

(ImageIO/read (URL. "https://upload.wikimedia.org/wikipedia/commons/thumb/3/31/The_Sower.jpg/1510px-The_Sower.jpg"))
