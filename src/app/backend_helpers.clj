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
                                    :rebrickable.part/part-num (-> part :part :part_num)
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

(defn lego-sets [db]
  (->> (xt/q db '{:find [(pull ?e [:xt/id :rebrickable/name :rebrickable/id :imported-at])]
                  :where [[?e :type :set]]})
       (map first)
       (sort-by :imported-at)
       (reverse)
       vec))

(defn lego-parts-for-set [db id]
  (->> (xt/q db '{:find [(pull ?p [*])]
                  :in [?set-id]
                  :where [[?p :belongs-to ?set-id]]}
             id)
       (map first)
       (group-by :rebrickable/id)
       (sort-by #(count (val %)))
       (reverse)
       #_(map (fn [e] [(key e) (count (val e))]))))

(comment
  (lego-parts-for-set (xt/db user/!xtdb) #uuid "2813b8e4-71f1-4bea-84af-66201e5ca55a"))

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

  ;; evict all the owned-set entities
  (let [entities (xt/q (xt/db user/!xtdb)
                       '{:find [?e]
                         :where [[?e :xt/id]
                                 [?e :type :owned-set]]})]
    (xt/submit-tx user/!xtdb (->> entities
                                  (map first)
                                  (map (fn [e] [::xt/evict e]))
                                  (into []))))

  ;; evict all the owned-part entities
  (let [entities (xt/q (xt/db user/!xtdb)
                       '{:find [?e]
                         :where [[?e :xt/id]
                                 [?e :type :owned-part]]})]
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
         (Thread/sleep 12000)))))

  ;; fetch the parts for the set with set-internal-id c17cf01b-eb48-4334-97dc-9efee4a621b1
  (let [sets (xt/q (xt/db user/!xtdb)
                   '{:find [?e (pull ?e [:rebrickable/id])]
                     :in [?e]
                     :where [[?e :xt/id]
                             [?e :type :set]]}
                   #uuid "c17cf01b-eb48-4334-97dc-9efee4a621b1")]
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

(comment
  ;; return the parts for a given set by its internal-set-id
  (let [set-id #uuid "c17cf01b-eb48-4334-97dc-9efee4a621b1"]
    (xt/q (xt/db user/!xtdb)
          '{:find [?p]
            :in [?rebrickable-set-id]
            :where [[?p :belongs-to ?rebrickable-set-id]]}
          set-id));; => #{}

  ;; are there other sets without any parts associated to them?
  (->> (xt/q (xt/db user/!xtdb)
             '{:find [(pull ?e [:xt/id :rebrickable/name :rebrickable/id])]
               :where [[?e :type :set]
                       (not-join [?e] [?p :type :part]
                                 [?p :belongs-to ?e])]}))
  ;; => #{[{:rebrickable/name "Street Sweeper", :rebrickable/id "6649-1", :xt/id #uuid "c17cf01b-eb48-4334-97dc-9efee4a621b1"}]}
  ;; it seems that a single set doesn't have any parts


  )

(comment
  ;; add an owned entity for a given set and create owned-parts for all the parts of the set too to keep track of the still missing and added parts.

  ;; given an internal-set-id, create a new owned set and owned parts.
  (let [set-internal-id #uuid "2813b8e4-71f1-4bea-84af-66201e5ca55a" ;; Renegade Runner
        db (xt/db user/!xtdb)]
    (let [parts (xt/q db '{:find [?p]
                           :in [?set-internal-id]
                           :where [[?p :belongs-to ?set-internal-id]]}
                      set-internal-id)
          owned-set-id (random-uuid)]
      (xt/submit-tx user/!xtdb [[::xt/put {:xt/id owned-set-id
                                           :type :owned-set
                                           :is-of-type set-internal-id}]])
      (xt/submit-tx user/!xtdb (->> parts
                                    (map (fn [p] [::xt/put {:xt/id (random-uuid)
                                                            :type :owned-part
                                                            :belongs-to owned-set-id
                                                            :is-of-type p
                                                            :status :part/missing}]))
                                    (into []))))))

(comment
  ;; how many owned sets and owned parts do we have in the database?
  (xt/q (xt/db user/!xtdb)
        '{:find [(count ?os)]
          :where [[?os :type :owned-set]]})

  )

(defn owned-sets [db]
  (->> (xt/q db '{:find [os-internal-id s-name s-image-url s-id]
                  :keys [os-internal-id rebrickable/name rebrickable/image-url rebrickable/id]
                  :where [[os :type :owned-set]
                          [os :xt/id os-internal-id]
                          [os :is-of-type s]
                          [s :rebrickable/name s-name]
                          [s :rebrickable/image-url s-image-url]
                          [s :rebrickable/id s-id]
                          ]})))

(comment
  (owned-sets (xt/db user/!xtdb))
  )

(defn owned-sets-for-set [db set-internal-id]
  (->> (xt/q db '{:find [?os]
                  :in [?set-internal-id]
                  :where [[?os :is-of-type ?set-internal-id]]}
         set-internal-id)
    (map first)))

(comment
  (owned-sets-for-set (xt/db user/!xtdb) #uuid "2813b8e4-71f1-4bea-84af-66201e5ca55a"))

(defn part-metadata [db rebrickable-element-id]
  (->> (xt/q db '{:find [(pull ?p [*])]
                      :in [?element-id]
                      :where [[?p :rebrickable/element-id ?element-id]]
                      :limit 1}
                 rebrickable-element-id)
           first
           first))

(comment
  (part-metadata (xt/db user/!xtdb) "4589")
;; => {:rebrickable/name "Cone 1 x 1 [No Top Groove]", :belongs-to #uuid "e3767dc6-2a31-4373-8c3a-a41e8423e481", :rebrickable/image-url "https://cdn.rebrickable.com/media/parts/elements/458926.jpg", :type :part, :rebrickable/id 7773230, :rebrickable.part/part-num "4589", :rebrickable/element-id "458926", :color/name "Black", :color/id 0, :part/number "4589", :xt/id #uuid "01e21469-4e63-488d-81ca-1a3e633c1395", :rebrickable/url "https://rebrickable.com/parts/4589/cone-1-x-1-no-top-groove/"}
  )

(defn part-occurrence-across-sets [db rebrickable-element-id]
  (->> (xt/q db '{:find [(count ?p)]
                  :in [?element-id]
                  :where [[?p :rebrickable/element-id ?element-id]]}
             rebrickable-element-id)
       first
       first))

(comment
  (part-occurrence-across-sets (xt/db user/!xtdb) "4589")
  )


(defn part-occurrence-in-sets [db rebrickable-element-id]
  (->> (xt/q db '{:find [(pull ?set [:xt/id :rebrickable/name]) (count ?p)]
                  :in [?rebrickable-element-id]
                  :where [[?p :rebrickable/element-id ?rebrickable-element-id]
                          [?p :belongs-to ?set]]
                  :order-by [[(count ?p) :desc]]}
             rebrickable-element-id)))

(comment
  (part-occurrence-in-sets (xt/db user/!xtdb) "4589")

  )

(comment
;; figuring out how the :rebrickable.part/part-num and the :rebrickable/id are to be understood with part entities
  (xt/q (xt/db user/!xtdb)
        '{:find [(pull ?p [:rebrickable.part/part-num :rebrickable/id :color/name :rebrickable/element-id])]
          :where [[?p :type :part]
                  [?p :rebrickable.part/part-num "4589"]
                  [?p :color/name "Trans-Dark Blue"]]})
;; => #{[{:rebrickable/id 639152, :rebrickable.part/part-num "4589", :rebrickable/element-id "618843", :color/name "Trans-Dark Blue"}] [{:rebrickable/id 43420, :rebrickable.part/part-num "4589", :rebrickable/element-id "618843", :color/name "Trans-Dark Blue"}] [{:rebrickable/id 800469, :rebrickable.part/part-num "4589", :rebrickable/element-id "618843", :color/name "Trans-Dark Blue"}]}

  ;; this must mean that the :rebrickable/id is even more unique than just the mold and the color, HOWEVER the :rebrickable/element-id seems to otherwise match our criteria

  (xt/q (xt/db user/!xtdb)
        '{:find [(pull ?p [:rebrickable.part/part-num :rebrickable/id :color/name :rebrickable/element-id])]
          :where [[?p :type :part]
                  [?p :rebrickable.part/part-num "4589"]
                  (or
                   [?p :color/name "Trans-Dark Blue"]
                   [?p :color/name "Blue"])]})
  ;; => #{[{:rebrickable/id 639152, :rebrickable.part/part-num "4589", :rebrickable/element-id "618843", :color/name "Trans-Dark Blue"}] [{:rebrickable/id 43420, :rebrickable.part/part-num "4589", :rebrickable/element-id "618843", :color/name "Trans-Dark Blue"}] [{:rebrickable/id 530086, :rebrickable.part/part-num "4589", :rebrickable/element-id "458923", :color/name "Blue"}] [{:rebrickable/id 645163, :rebrickable.part/part-num "4589", :rebrickable/element-id "458923", :color/name "Blue"}] [{:rebrickable/id 1114337, :rebrickable.part/part-num "4589", :rebrickable/element-id "458923", :color/name "Blue"}] [{:rebrickable/id 800469, :rebrickable.part/part-num "4589", :rebrickable/element-id "618843", :color/name "Trans-Dark Blue"}]}

;; yes my suspicion seems to be confirmed that the element-id is the unique id for the mold and color. Furthermore when looking at the available colors on https://rebrickable.com/parts/4589/cone-1-x-1-no-top-groove/ it also seems to add up.
  )

(comment
  ;; why does the part with :rebrickable.part/part-number "2348b" not have an element id?
  ;; looking at https://rebrickable.com/parts/2348b/glass-for-hinge-car-roof-4-x-4-sunroof-with-ridges/ I can see 3 colours available
  ;; but none of them has an element-id - hence I think the easiest might be that if a part is not defined the link button
  ;; will redirect to a detail view of the part number instead of the part element id.
  ;; then the part detail view by part number will display a disclaimer that multiple colours might be meant and that the
  ;; instructions should be consulted.

  )

(comment
  ;; why are there 2 parts of part number 3024 but listed separately and both with the element id 6252045?
  (xt/q (xt/db user/!xtdb)
    '{:find [(pull ?p [*])]
      :where [[?set :xt/id #uuid "c17cf01b-eb48-4334-97dc-9efee4a621b1"]
              [?p :belongs-to ?set]
              [?p :type :part]
              [?p :rebrickable.part/part-num "3024"]
              [?p :rebrickable/element-id "6252045"]]})
  ;; as suspected those parts have differing :rebrickable/id values. No idea why - I'll leave it like this for now

  )

