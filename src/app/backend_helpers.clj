(ns app.backend-helpers
  (:require [xtdb.api :as xt]
            [clj-http.client :as client]))

(defonce api-url "https://rebrickable.com/api/v3/lego")
(defonce api-key (System/getenv "REBRICKABLE_API_KEY"))

(defn fetch-set [id]
  (-> (client/get
       (format "%s/sets/%s/" api-url id)
       {:accept :json
        :content-type :json
        :as :json
        :headers {:Authorization (format "key %s" api-key)}})
      :body
      ((fn [body]
         {:type :set
          :xt/id (random-uuid)
          :imported-at (System/currentTimeMillis)
          :rebrickable/id (:set_num body)
          :rebrickable/name (:name body)
          :rebrickable/url (:set_url body)
          :rebrickable/release-year (:year body)
          :rebrickable/theme-id (:theme_id body)
          :rebrickable/image-url (:set_img_url body)}))))

(defn fetch-parts [id internal-set-id]
  (let [results (atom [])
        done (atom false)
        url (atom (format "%s/sets/%s/parts" api-url id))
        options {:accept :json
                 :content-type :json
                 :as :json
                 :headers {:Authorization (format "key %s" api-key)}}]
    (while (not @done)
      (let [response (client/get @url options)]
        (reset! results (concat @results (-> response :body :results)))
        (if-let [next (-> response :body :next)]
          (reset! url next)
          (reset! done true))))
    (->> @results
         (map (fn [part]
                (repeatedly (:quantity part)
                            (fn [] {:type :part
                                    :xt/id (random-uuid)
                                    :belongs-to internal-set-id
                                    :rebrickable/id (:id part)
                                    :rebrickable/element-id (:element_id part) ;; why are some element-id values nil?
                                    :rebrickable/name (-> part :part :name)
                                    :rebrickable/url (-> part :part :part_url)
                                    :rebrickable/image-url (-> part :part :part_img_url)
                                    :part/number (-> part :part :part_num)
                                    :color/id (-> part :color :id)
                                    :color/name (-> part :color :name)}))))
         flatten)))

(comment
  (count (fetch-parts "8062-1" "my-id"))
;; => 410

  (xt/q (xt/db user/!xtdb)
        '{:find [?e (count ?part)]
          :in [?rebrickable-id]
          :where [[?e :rebrickable/id ?rebrickable-id]
                  [?part :belongs-to ?e]]}
        "8062-1")
;; => #{[#uuid "c0bd2e14-f53d-455a-9334-3390340eed0b" 78]}

  (xt/q (xt/db user/!xtdb)
        '{:find [?e (count ?part)]
          :in [?rebrickable-id]
          :where [[?e :rebrickable/id ?rebrickable-id]
                  [?part :belongs-to ?e]]}
        "8062-1")

  (->> (fetch-parts "8062-1" "my-id")
       (map :xt/id)
       frequencies
       vals
       distinct)

  (->> (fetch-parts "8062-1" "my-id")
       (map :rebrickable/id)
       distinct
       count))

(defn is-set-in-database? [db rebrickable-id]
  (>= (count (xt/q db '{:find [?e]
                        :in [?rebrickable-id]
                        :where [[?e :rebrickable/id ?rebrickable-id]]}
                   rebrickable-id))
      1))

(comment
  (is-set-in-database? (xt/db user/!xtdb) "8503-1")
  (is-set-in-database? (xt/db user/!xtdb) "no-existent-set"))

(comment
  ;; count all the entities within the database
  (xt/q (xt/db user/!xtdb)
        '{:find [(count ?e)]
          :where [[?e :xt/id]]});; => #{[398]}
  )

(comment
  ;; get the sets with their names and part count
  (xt/q (xt/db user/!xtdb)
        '{:find [?e ?id ?name (count ?part)]
          :where [[?e :type :set]
                  [?part :belongs-to ?e]
                  [?e :rebrickable/name ?name]
                  [?e :rebrickable/id ?id]]})
;; => #{[#uuid "133e752c-9a27-4c86-929d-d610bcb90155" "8446-1" "Crane Truck" 130] [#uuid "fc038d71-1e79-4f20-bfd8-ac9e96738072" "6815-1" "Hovertron" 17] [#uuid "3e78fe57-6832-4a81-8efc-996c5567da3e" "6512-1" "Landscape Loader" 24]}
  )
(defn distinct-number-of-parts-of-set [db set-id]
  (->> (xt/q db
             '{:find [?e (distinct ?id)]
               :in [?e]
               :where [[?part :belongs-to ?e]
                       [?part :rebrickable/id ?id]]}
             set-id)
       first
       second
       count))

(defn number-of-parts-of-set [db set-id]
  (->> (xt/q db
             '{:find [?e (count ?part)]
               :in [?e]
               :where [[?part :belongs-to ?e]]}
             set-id)
       first
       second))

(comment
  (number-of-parts-of-set (xt/db user/!xtdb)
                          #uuid "133e752c-9a27-4c86-929d-d610bcb90155")

  (number-of-parts-of-set (xt/db user/!xtdb)
                          #uuid "c3078264-911c-4c06-be4e-217169986935")

  (distinct-number-of-parts-of-set (xt/db user/!xtdb)
                                   #uuid "d4221122-cc6c-4413-b31c-07b738d3271f"))

(comment
  ;; Data maintenance and migration functions to be used during development
  ;; ------

  ;;evict all entities (purge database)
  (let [entities (xt/q (xt/db user/!xtdb)
                       '{:find [?e]
                         :where [[?e :xt/id]]})]
    (xt/submit-tx user/!xtdb (->> entities
                                  (map first)
                                  (map (fn [e] [::xt/evict e]))
                                  (into []))))

  ;; evict all the parts entities
  (let [entities (xt/q (xt/db user/!xtdb)
                       '{:find [?e]
                         :where [[?e :xt/id]
                                 [?e :type :part]]})]
    (xt/submit-tx user/!xtdb (->> entities
                                  (map first)
                                  (map (fn [e] [::xt/evict e]))
                                  (into []))))

  ; evict all the sets with the id 8062-1
  (let [entities (xt/q (xt/db user/!xtdb)
                       '{:find [?e]
                         :where [[?e :xt/id]
                                 [?e :type :set]
                                 [?e :rebrickable/id "8062-1"]]})]
    (xt/submit-tx user/!xtdb (->> entities
                                  (map first)
                                  (map (fn [e] [::xt/evict e]))
                                  (into []))))

  ;; fetch the parts for all the sets
  (let [sets (xt/q (xt/db user/!xtdb)
                   '{:find [?e (pull ?e [:rebrickable/id])]
                     :where [[?e :xt/id]
                             [?e :type :set]]})]
    (doall
     (doseq [s sets]
       (let [parts (fetch-parts (:rebrickable/id (second s)) (first s))]
         (xt/submit-tx user/!xtdb (->> parts
                                       (map (fn [p] [::xt/put p]))
                                       (into [])))
         (println "Imported parts for set" (:rebrickable/id (second s)))
         (Thread/sleep 10000))))))

(defn number-of-sets [db]
  (->> (xt/q db
             '{:find [(count ?e)]
               :where [[?e :type :set]]})
       first
       first))

(defn number-of-parts [db]
  (->> (xt/q db
             '{:find [(count ?e)]
               :where [[?e :type :part]]})
       first
       first))

(comment
  (number-of-sets (xt/db user/!xtdb))
;; => 21

  (number-of-parts (xt/db user/!xtdb));; => 1370
  )

(comment
  ;; what kind of part data do I have for eg. the Space Shuttle with id 8480-1?
  (->> (xt/q (xt/db user/!xtdb)
             '{:find [?p (pull ?p [*])]
               :in [?rebrickable-id]
               :where [[?e :rebrickable/id ?rebrickable-id]
                       [?p :belongs-to ?e]]
               :order-by [[?p :asc]]
               :limit 2}
             "8480-1"));; => [[#uuid "800d31b4-6629-42f4-953d-892b3ae66913" {:rebrickable/name "Plate 1 x 4", :belongs-to #uuid "8c251e52-84e8-4dd2-96b7-799ad9f9e9cf", :rebrickable/image-url "https://cdn.rebrickable.com/media/parts/elements/371026.jpg", :type :part, :rebrickable/id 710313, :rebrickable/element-id "371026", :color/name "Black", :color/id 0, :part/number "3710", :xt/id #uuid "800d31b4-6629-42f4-953d-892b3ae66913", :rebrickable/url "https://rebrickable.com/parts/3710/plate-1-x-4/"}] [#uuid "80306178-6c30-456f-913b-b40f7291e513" {:rebrickable/name "Plate Special 1 x 2 with 1 Stud without Groove (Jumper)", :belongs-to #uuid "8c251e52-84e8-4dd2-96b7-799ad9f9e9cf", :rebrickable/image-url "https://cdn.rebrickable.com/media/parts/ldraw/0/3794a.png", :type :part, :rebrickable/id 539213, :rebrickable/element-id nil, :color/name "Black", :color/id 0, :part/number "3794a", :xt/id #uuid "80306178-6c30-456f-913b-b40f7291e513", :rebrickable/url "https://rebrickable.com/parts/3794a/plate-special-1-x-2-with-1-stud-without-groove-jumper/"}]]

  ;; what is the part occurring the most in the given set?
  (->> (xt/q (xt/db user/!xtdb)
             '{:find [?rebrickable-part-id ?rebrickable-part-name (count ?p)]
               :in [?rebrickable-set-id]
               :where [[?e :rebrickable/id ?rebrickable-set-id]
                       [?p :belongs-to ?e]
                       [?p :rebrickable/id ?rebrickable-part-id]
                       [?p :rebrickable/name ?rebrickable-part-name]]
               :order-by [[(count ?p) :desc]]
               :limit 2}
             "8480-1"))
;; => [[155384 "Technic Bush 1/2 Toothed Type II [X Opening]" 90] [794745 "Technic Bush" 64]]

;; which part is occuring the most in all the sets we own?
  (->> (xt/q (xt/db user/!xtdb)
             '{:find [?rebrickable-part-id ?rebrickable-part-name (count ?p)]
               :where [[?p :type :part]
                       [?p :rebrickable/id ?rebrickable-part-id]
                       [?p :rebrickable/name ?rebrickable-part-name]]
               :order-by [[(count ?p) :desc]]
               :limit 6})))
