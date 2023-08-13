(ns app.backend-helpers
  (:require [xtdb.api :as xt]
            [clj-http.client :as client]))

(defonce api-url "https://rebrickable.com/api/v3/lego")
(defonce api-key (System/getenv "REBRICKABLE_API_KEY"))

(defn uuid->str
  [uuid]
  (.toString uuid))

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

(comment
  (fetch-set "8062-1")

  )
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
                       :color/name (-> part :color :name)
                       :is-spare (:is_spare part)}))))
      flatten)))

(comment
  (count (fetch-parts "8062-1" "my-id"))
  (filter #(= true (:is-spare %)) (fetch-parts "6649-1" "my-id"))
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

(defn fetch-minifigures [id internal-set-id]
  (let [results (atom [])
        done (atom false)
        url (atom (format "%s/sets/%s/minifigs" api-url id))
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
      (map (fn [minifigure]
             (repeatedly (:quantity minifigure)
               (fn [] {:type :minifigure
                       :xt/id (random-uuid)
                       :belongs-to internal-set-id
                       :rebrickable/id (:id minifigure)
                       :rebrickable/name (-> minifigure :set_name)
                       :rebrickable.minifigure/set-num (-> minifigure :set_num)
                       :rebrickable/image-url (-> minifigure :set_img_url)
                       }))))
      flatten)))
(defn fetch-minifigure-parts [minifigure-set-num internal-set-id internal-minifigure-id]
  (let [results (atom [])
        done (atom false)
        url (atom (format "%s/minifigs/%s/parts" api-url minifigure-set-num))
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
                       :part-of-minifigure internal-minifigure-id
                       :rebrickable/id (:id part)
                       :rebrickable/element-id (:element_id part) ;; why are some element-id values nil?
                       :rebrickable/name (-> part :part :name)
                       :rebrickable/url (-> part :part :part_url)
                       :rebrickable/image-url (-> part :part :part_img_url)
                       :rebrickable.part/part-num (-> part :part :part_num)
                       :color/id (-> part :color :id)
                       :color/name (-> part :color :name)
                       :is-spare (:is_spare part)
                       :rebrickable.minifigure/set-num (-> part :set_num)
                       }))))
      flatten)))

(defn fetch-minifigure-parts-for-set [id internal-set-id]
  (let [minifigures (fetch-minifigures id internal-set-id)
        parts (->> minifigures
                (map #(fetch-minifigure-parts
                        (:rebrickable.minifigure/set-num %)
                        internal-set-id
                        (:xt/id %)))
                (cons minifigures)
                (apply concat))]
    parts))
(comment
  (fetch-minifigures "6268-1" "some-id")
  (fetch-minifigure-parts "fig-005149" "some-internal-set-id" "some-internal-minifigure-id")

  (fetch-minifigure-parts-for-set "6268-1" "some-id")

  )

(defn fetch-themes []
  (let [results (atom [])
        done (atom false)
        url (atom (format "%s/themes" api-url))
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
      (map (fn [theme]
             {:type :theme
              :xt/id (random-uuid)
              :rebrickable/id (:id theme)
              :rebrickable/parent-id (:parent_id theme)
              :rebrickable/name (-> theme :name)}))
      flatten)))
(comment
  ;; https://rebrickable.com/api/v3/lego/themes/?page_size=1000
  ;{
  ; "id": 3,
  ; "parent_id": 1,
  ; "name": "Competition"
  ; }

  (take 10 (fetch-themes))
  (count (fetch-themes))

  ;; fetch and insert themes into our database
  (xt/submit-tx user/!xtdb (->> (fetch-themes)
                             (map (fn [p] [::xt/put p]))
                             (into [])))

  ;; ensure we inserted all the themes
  (xt/q (xt/db user/!xtdb)
    '{:find [(count e)]
      :where [[e :type :theme]]})
  ;; => #{[466]}
  )

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
      :where [[?e :xt/id]]})                                ;; => #{[398]}
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
                  :where [[?p :belongs-to ?set-id]
                          [?p :type :part]]}
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
        (let [parts (fetch-parts (:rebrickable/id (second s)) (first s))
              minifigures-and-parts (fetch-minifigure-parts-for-set (:rebrickable/id (second s)) (first s))]
          (xt/submit-tx user/!xtdb (->> parts
                                     (map (fn [p] [::xt/put p]))
                                     (into [])))
          (xt/submit-tx user/!xtdb (->> minifigures-and-parts
                                     (map (fn [p] [::xt/put p]))
                                     (into [])))
          (println "Imported parts for set" (:rebrickable/id (second s)))
          (Thread/sleep 20000)))
      (println "All parts and minifigures have been imported")))

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
          (Thread/sleep 10000)))))

  ;; fetch the minifigures and parts for the set with the set-internal-id c17cf01b-eb48-4334-97dc-9efee4a621b1
  (let [sets (xt/q (xt/db user/!xtdb)
               '{:find [?e (pull ?e [:rebrickable/id])]
                 :in [?e]
                 :where [[?e :xt/id]
                         [?e :type :set]]}
               #uuid "c17cf01b-eb48-4334-97dc-9efee4a621b1")]
    (doall
      (doseq [s sets]
        (let [minifigures-and-parts (fetch-minifigure-parts-for-set (:rebrickable/id (second s)) (first s))]
          (xt/submit-tx user/!xtdb (->> minifigures-and-parts
                                     (map (fn [p] [::xt/put p]))
                                     (into [])))
          (println "Imported minifigure parts for set" (:rebrickable/id (second s)))
          (Thread/sleep 10000)))
      (println "done importing")))
  )

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

  (number-of-parts (xt/db user/!xtdb))                      ;; => 1370
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
         "8480-1"))                                         ;; => [[#uuid "800d31b4-6629-42f4-953d-892b3ae66913" {:rebrickable/name "Plate 1 x 4", :belongs-to #uuid "8c251e52-84e8-4dd2-96b7-799ad9f9e9cf", :rebrickable/image-url "https://cdn.rebrickable.com/media/parts/elements/371026.jpg", :type :part, :rebrickable/id 710313, :rebrickable/element-id "371026", :color/name "Black", :color/id 0, :part/number "3710", :xt/id #uuid "800d31b4-6629-42f4-953d-892b3ae66913", :rebrickable/url "https://rebrickable.com/parts/3710/plate-1-x-4/"}] [#uuid "80306178-6c30-456f-913b-b40f7291e513" {:rebrickable/name "Plate Special 1 x 2 with 1 Stud without Groove (Jumper)", :belongs-to #uuid "8c251e52-84e8-4dd2-96b7-799ad9f9e9cf", :rebrickable/image-url "https://cdn.rebrickable.com/media/parts/ldraw/0/3794a.png", :type :part, :rebrickable/id 539213, :rebrickable/element-id nil, :color/name "Black", :color/id 0, :part/number "3794a", :xt/id #uuid "80306178-6c30-456f-913b-b40f7291e513", :rebrickable/url "https://rebrickable.com/parts/3794a/plate-special-1-x-2-with-1-stud-without-groove-jumper/"}]]

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
      set-id))                                              ;; => #{}

  ;; are there other sets without any parts associated to them?
  (->> (xt/q (xt/db user/!xtdb)
         '{:find [(pull ?e [:xt/id :rebrickable/name :rebrickable/id])]
           :where [[?e :type :set]
                   (not-join [?e] [?p :type :part]
                     [?p :belongs-to ?e])]}))
  ;; => #{[{:rebrickable/name "Street Sweeper", :rebrickable/id "6649-1", :xt/id #uuid "c17cf01b-eb48-4334-97dc-9efee4a621b1"}]}
  ;; it seems that a single set doesn't have any parts

  )

(defn char-range
  "from https://stackoverflow.com/questions/11670941/generate-character-sequence-from-a-to-z-in-clojure"
  [start end]
  (map char (range (int start) (inc (int end)))))

(defn generate-owned-set-name [rebrickable-name existing-names]
  (->> (char-range \A \Z)
    (into [])
    (#(nth % (count existing-names)))
    (str rebrickable-name "-")))
(defn create-an-owned-set-for-a-set [set-internal-id]
  (let [db (xt/db user/!xtdb)]
    (let [_ (xt/sync user/!xtdb)                            ;; wait for the index to catch up
          other-owned-set-names (->> (xt/q db '{:find [name]
                                                :in [set-internal-id]
                                                :where [[os :is-of-type set-internal-id]
                                                        [os :type :owned-set]
                                                        [os :name name]]}
                                       set-internal-id)
                                  (map first))
          parts (xt/q db '{:find [?p]
                           :in [?set-internal-id]
                           :where [[?p :belongs-to ?set-internal-id]
                                   [?p :type :part]]}
                  set-internal-id)
          owned-set-id (random-uuid)]
      (xt/submit-tx user/!xtdb [[::xt/put {:xt/id owned-set-id
                                           :type :owned-set
                                           :is-of-type set-internal-id
                                           :name (generate-owned-set-name
                                                   (:rebrickable/id (xt/entity db set-internal-id))
                                                   other-owned-set-names)}]])
      (xt/submit-tx user/!xtdb (->> parts
                                 (map (fn [p] [::xt/put {:xt/id (random-uuid)
                                                         :type :owned-part
                                                         :belongs-to owned-set-id
                                                         :is-of-type p
                                                         :status :part/missing}]))
                                 (into []))))))

(comment
  ;; create an owned set for each set and create all the owned parts for all the parts associated to a given set.
  (doall (doseq [set-internal-id (->> (xt/q (xt/db user/!xtdb)
                                        '{:find [s]
                                          :where [[s :type :set]]})
                                   (map first))]
           (create-an-owned-set-for-a-set set-internal-id)))

  )


(comment
  ;; every owned set should get it's short name eg. rebrickable/name + "-A"

  (char-range \A \Z)
  ;; => (\A \B \C \D \E \F \G \H \I \J \K \L \M \N \O \P \Q \R \S \T \U \V \W \X \Y \Z)

  (into [] (char-range \A \Z))

  (xt/sync user/!xtdb)

  (let [e (xt/entity (xt/db user/!xtdb) #uuid "dc9b7524-bc31-4526-ac1e-b3ee0f2ea7a8")]
    (xt/submit-tx user/!xtdb [[::xt/put (assoc e :name "6649-1-A")]]))

  (->> (xt/q (xt/db user/!xtdb)
         '{:find [name]
           :in [set-internal-id]
           :where [[os :is-of-type set-internal-id]
                   [os :type :owned-set]
                   [os :name name]]}
         #uuid "c17cf01b-eb48-4334-97dc-9efee4a621b1")
    (map first))
  ;; => ("6649-1-A")

  (generate-owned-set-name "6649-1" '("6649-1-A"))          ;; => "6649-1-B"
  (generate-owned-set-name "6649-1" '())

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
  (->> (xt/q db '{:find [os-internal-id os-name s-internal-id s-name s-image-url s-id (count op)]
                  :keys [os-internal-id os-name s-internal-id rebrickable/name rebrickable/image-url rebrickable/id op-count]
                  :where [[os :type :owned-set]
                          [os :xt/id os-internal-id]
                          [os :is-of-type s]
                          [s :rebrickable/name s-name]
                          [s :rebrickable/image-url s-image-url]
                          [s :rebrickable/id s-id]
                          [s :xt/id s-internal-id]
                          [os :name os-name]
                          [op :belongs-to os]
                          [op :type :owned-part]
                          ]
                  :order-by [[os-name :asc]]})))

(comment
  (->> (owned-sets (xt/db user/!xtdb))
    first)
  )

(defn completion-ratio-for-owned-set [db os-internal-id]
  (let [number-of-parts (->> (xt/q db
                               '{:find [os-internal-id
                                        (count op)]
                                 :keys [os-internal-id
                                        number-of-parts]
                                 :in [os-internal-id]
                                 :where [[os :type :owned-set]
                                         [os :xt/id os-internal-id]
                                         [op :belongs-to os]]}
                               os-internal-id)
                          first
                          :number-of-parts)
        number-of-parts-added (if-let [n (->> (xt/q db
                                                '{:find [os-internal-id
                                                         (count op)]
                                                  :keys [os-internal-id
                                                         number-of-parts]
                                                  :in [os-internal-id]
                                                  :where [[os :type :owned-set]
                                                          [os :xt/id os-internal-id]
                                                          [op :belongs-to os]
                                                          [op :status :part/added]]}
                                                os-internal-id)
                                           first
                                           :number-of-parts)]
                                n
                                0)
        number-of-parts-missing (- number-of-parts number-of-parts-added)
        ratio (->> (/ number-of-parts-added (/ number-of-parts 100))
                double)]
    (format "%.2f %% - %s parts missing" ratio number-of-parts-missing)))
(comment
  (owned-sets (xt/db user/!xtdb))

  ;; listing of owned sets with their respective completion ratio
  (xt/q (xt/db user/!xtdb)
    '{:find [os-internal-id
             os-name
             s-internal-id
             s-name
             s-image-url
             s-id
             (count op)]
      :keys [os-internal-id
             os-name
             s-internal-id
             rebrickable/name
             rebrickable/image-url
             rebrickable/id
             number-of-parts]
      :in [os-internal-id]
      :where [[os :type :owned-set]
              [os :xt/id os-internal-id]
              [os :is-of-type s]
              [s :rebrickable/name s-name]
              [s :rebrickable/image-url s-image-url]
              [s :rebrickable/id s-id]
              [s :xt/id s-internal-id]
              [os :name os-name]
              [op :belongs-to os]
              ]
      :order-by [[s-id :asc]]}
    #uuid "703daad1-f2ef-4812-912d-d8f0d8f019f5")

  (let [os-internal-id #uuid "703daad1-f2ef-4812-912d-d8f0d8f019f5"
        number-of-parts (->> (xt/q (xt/db user/!xtdb)
                               '{:find [os-internal-id
                                        (count op)]
                                 :keys [os-internal-id
                                        number-of-parts]
                                 :in [os-internal-id]
                                 :where [[os :type :owned-set]
                                         [os :xt/id os-internal-id]
                                         [op :belongs-to os]]}
                               #uuid "703daad1-f2ef-4812-912d-d8f0d8f019f5")
                          first
                          :number-of-parts)
        number-of-parts-added (->> (xt/q (xt/db user/!xtdb)
                                     '{:find [os-internal-id
                                              (count op)]
                                       :keys [os-internal-id
                                              number-of-parts]
                                       :in [os-internal-id]
                                       :where [[os :type :owned-set]
                                               [os :xt/id os-internal-id]
                                               [op :belongs-to os]
                                               [op :status :part/added]]}
                                     #uuid "703daad1-f2ef-4812-912d-d8f0d8f019f5")
                                first
                                :number-of-parts)]
    (->> (/ number-of-parts-added (/ number-of-parts 100))
      double
      (format "%.2f %%")))

  (completion-ratio-for-owned-set
    (xt/db user/!xtdb)
    #uuid "703daad1-f2ef-4812-912d-d8f0d8f019f5")


  (->> (xt/q (xt/db user/!xtdb)
         '{:find [os-internal-id
                  (count op)]
           :keys [os-internal-id
                  number-of-parts]
           :in [os-internal-id]
           :where [[os :type :owned-set]
                   [os :xt/id os-internal-id]
                   [op :belongs-to os]
                   [op :status :part/added]]}
         #uuid "703daad1-f2ef-4812-912d-d8f0d8f019f5")
    first
    :number-of-parts)
  )

(defn owned-set [db owned-set-id]
  (->> (xt/q db '{:find [owned-set-id
                         owned-set-name
                         s-name
                         s-image-url
                         s-id
                         s-url
                         s-internal-id]
                  :keys [owned-set-id
                         owned-set-name
                         rebrickable/name
                         rebrickable/image-url
                         rebrickable/id
                         rebrickable/url
                         internal-lego-set-id]
                  :in [owned-set-id]
                  :where [[os :xt/id owned-set-id]
                          [os :type :owned-set]
                          [os :is-of-type s]
                          [s :rebrickable/name s-name]
                          [s :rebrickable/image-url s-image-url]
                          [s :rebrickable/id s-id]
                          [s :rebrickable/url s-url]
                          [s :xt/id s-internal-id]
                          [os :name owned-set-name]
                          ]}
         owned-set-id)
    first))

(comment
  (owned-set (xt/db user/!xtdb) #uuid "cf4aa957-bc20-4cc7-8a74-92006c41c705")

  )

(defn owned-parts-for-owned-set [db owned-set-id]
  (->> (xt/q db '{:find [op
                         p-name
                         p-image-url
                         p-id
                         p-element-id
                         p-part-number
                         p-color
                         p-url
                         op-status]
                  :keys [owned-part/id
                         rebrickable/name
                         rebrickable/image-url
                         rebrickable/id
                         rebrickable/element-id
                         rebrickable.part/part-num
                         color/name
                         rebrickable/url
                         owned-part/status]
                  :in [owned-set-id]
                  :where [[os :xt/id owned-set-id]
                          [os :type :owned-set]
                          [op :belongs-to os]
                          [op :is-of-type p]
                          [p :rebrickable/name p-name]
                          [p :rebrickable/image-url p-image-url]
                          [p :rebrickable/id p-id]
                          [p :rebrickable/element-id p-element-id]
                          [p :rebrickable.part/part-num p-part-number]
                          [p :color/name p-color]
                          [p :rebrickable/url p-url]
                          [op :status op-status]
                          ]
                  :order-by [[op-status :desc]
                             [p-color :asc]
                             [p-part-number :asc]]}
         owned-set-id)
    ))

(comment
  (owned-parts-for-owned-set (xt/db user/!xtdb) #uuid "cf4aa957-bc20-4cc7-8a74-92006c41c705")

  )

(defn change-status-of-owned-part [owned-part-id new-status]
  (let [op (xt/entity (xt/db user/!xtdb) owned-part-id)]
    (xt/submit-tx user/!xtdb [[::xt/put (assoc op :status new-status)]])))
(comment
  (change-status-of-owned-part #uuid "9c70cd97-96f4-49d9-b00f-9f161773653f" :part/added)
  )

(defn owned-sets-for-set [db set-internal-id]
  (->> (xt/q db '{:find [os os-name]
                  :in [set-internal-id]
                  :where [[os :is-of-type set-internal-id]
                          [os :name os-name]]
                  :order-by [[os-name :asc]]}
         set-internal-id)))

(comment
  (owned-sets-for-set (xt/db user/!xtdb) #uuid "c17cf01b-eb48-4334-97dc-9efee4a621b1"))

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

(defn owned-parts-by-part-number [db part-number]
  (->> (xt/q db '{:find [op
                         p-name
                         p-id
                         p-element-id
                         p-part-number
                         p-color
                         op-status
                         s-name
                         s-id
                         s-internal-id
                         os-name]
                  :keys [owned-part/id
                         rebrickable/name
                         rebrickable/id
                         rebrickable/element-id
                         rebrickable.part/part-num
                         color/name
                         owned-part/status
                         set-rebrickable-name
                         set-rebrickable-id
                         set-internal-id
                         owned-set/name]
                  :in [part-number]
                  :where [[p :rebrickable.part/part-num part-number]
                          [op :is-of-type p]
                          [op :belongs-to os]
                          [p :belongs-to s]
                          [p :rebrickable/name p-name]
                          [p :rebrickable/id p-id]
                          [p :rebrickable/element-id p-element-id]
                          [p :rebrickable.part/part-num p-part-number]
                          [p :color/name p-color]
                          [op :status op-status]
                          [s :rebrickable/name s-name]
                          [s :rebrickable/id s-id]
                          [s :xt/id s-internal-id]
                          [os :name os-name]]
                  :order-by [[p-color :asc]
                             [s-id :asc]]}
         part-number)
    ))

(comment
  (owned-parts-by-part-number (xt/db user/!xtdb) "2452")
  (count (owned-parts-by-part-number (xt/db user/!xtdb) "2452"))
  (count (owned-parts-by-part-number (xt/db user/!xtdb) "3001"))

  )

(defn part-metadata-by-part-number [db part-number]
  (->> (xt/q db '{:find [p-name
                         p-image-url
                         p-part-number]
                  :keys [rebrickable/name
                         rebrickable/image-url
                         rebrickable.part/part-num]
                  :in [part-number]
                  :where [[p :rebrickable.part/part-num part-number]
                          [op :belongs-to os]
                          [p :rebrickable/name p-name]
                          [p :rebrickable/image-url p-image-url]
                          [p :rebrickable.part/part-num p-part-number]]
                  :limit 1}
         part-number)
    ))

(comment
  (part-metadata-by-part-number (xt/db user/!xtdb) "3001")
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

(comment
  ;; what kind of parts are occuring most often
  (xt/q (xt/db user/!xtdb)
    '{:find [part-number (count p)]
      :where [[p :type :part]
              [p :rebrickable.part/part-num part-number]]
      :limit 20
      :order-by [[(count p) :desc]]})
  ;=>
  ;[["2780" 1247]
  ; ["3023" 1215]
  ; ["6141" 904]
  ; ["3004" 861]
  ; ["3710" 724]
  ; ["3666" 547]
  ; ["3713" 525]
  ; ["4265b" 513]
  ; ["3749" 511]
  ; ["3024" 508]
  ; ["3005" 489]
  ; ["3062b" 455]
  ; ["3623" 444]
  ; ["6536" 426]
  ; ["2420" 421]
  ; ["3022" 415]
  ; ["3700" 402]
  ; ["32062" 389]
  ; ["3705" 387]
  ; ["6558" 368]]
  )

(comment
  ;; there any entities in the database which aren't of :type :set?
  (xt/q (xt/db user/!xtdb)
    '{:find [e]
      :where [[e :xt/id]
              [(not [e :type :set])]]})
  ;; => #{}
  ;; after clearing all the parts, owned-sets and owned-parts I got nothing but sets in the database

  )

(comment
  ;; testing the tracking of steps on owned sets eg. whether the instructions have been put into the bag
  (xt/q (xt/db user/!xtdb)
    '{:find [(pull e [*])]
      :where [[e :xt/id #uuid "f40736ac-d04d-4452-83c3-3513b44405be"]]})

  ;; marking this set as having had its instructions put into the bag
  (xt/submit-tx user/!xtdb [[::xt/put (assoc (xt/entity (xt/db user/!xtdb) #uuid "f40736ac-d04d-4452-83c3-3513b44405be")
                                        :instructions-bagged true)]])

  )


(comment
  (def !client-state (atom {:page-options {:search "test"}}))
  (swap! !client-state assoc-in [:page-options :search] "test2")

  )

(comment
  ;; I've now bagged and labelled all the bags for which I got instructions
  ;; an owned set which got labelled and bagged
  (xt/entity (xt/db user/!xtdb) #uuid "79e0421b-ee6b-466e-ae4d-729d6d4874b5")


  ;; Which owned sets are there are not bagged and labelled?
  (xt/q (xt/db user/!xtdb)
    '{:find [e name]
      :where [[e :type :owned-set]
              (not-join [e]
                   [e :instructions-bagged true])
              [e :is-of-type set]
              [set :rebrickable/name name]]})
  ;=>
  ;#{[#uuid"150b4121-4ffd-4fd1-95b8-868f9f28ad58" "Airtech Claw Rig"]
  ;  [#uuid"d1eea757-87d8-46da-a2c2-92d7406ccfa9" "Supersonic Car"]
  ;  [#uuid"be6560fa-c356-4354-8dde-7b4e5b224475" "Space Shuttle"]
  ;  [#uuid"329d6cf8-c551-43b9-814c-33f2acb5e540" "CyberMaster"]
  ;  [#uuid"e9b8740a-1ee9-4160-be83-a27d131647d8" "Barcode Multi-Set"]}

  ;; remove an owned-set and its owned-parts
  (let [owned-set-id #uuid "be6560fa-c356-4354-8dde-7b4e5b224475"]
    (let [owned-parts (xt/q (xt/db user/!xtdb)
                        '{:find [op]
                          :in [os]
                          :where [[op :xt/id]
                                  [op :type :owned-part]
                                  [op :belongs-to os]]}
                        owned-set-id)]
      (xt/submit-tx user/!xtdb (->> owned-parts
                                 (map first)
                                 (map (fn [e] [::xt/evict e]))
                                 (into [])))
      (xt/submit-tx user/!xtdb [[::xt/evict owned-set-id]])))
  )

(comment
  ;; how many parts have been added so far?

  (xt/q (xt/db user/!xtdb)
    '{:find [(count op)]
      :where [[op :type :owned-part]
              [op :status :part/added]]})
  )

(comment

  ;; get the themes of a particular owned-set
  (xt/q (xt/db user/!xtdb)
    '{:find [(pull s [*])
             (pull t [*])]
      :where [[os :xt/id #uuid "703daad1-f2ef-4812-912d-d8f0d8f019f5"]
              [os :is-of-type s]
              [s :rebrickable/theme-id theme-id]
              [t :rebrickable/id theme-id]]})


  ;; how many themes do we have all in all?
  (xt/q (xt/db user/!xtdb)
    '{:find [(count t)]
      :where [[t :type :theme]]})
  ;; => #{[466]}

  ;; how many of those are the top themes?
  (xt/q (xt/db user/!xtdb)
    '{:find [(count t)]
      :where [[t :type :theme]
              [t :rebrickable/parent-id nil]]})
  ;; => #{[144]}

  ;; what is the maximum depth of the graph?

  )

(comment
  ;; figure out how many parts have been added per day
  (let [internal-set-id #uuid "703daad1-f2ef-4812-912d-d8f0d8f019f5"]
    (xt/q (xt/db user/!xtdb)
      '{:find [op start-time]
        :in [internal-set-id]
        :where [[os :xt/id internal-set-id]
                [op :belongs-to os]
                [op :status :part/added]
                [(get-start-valid-time op) start-time]]}
      internal-set-id))

  (xt/entity (xt/db user/!xtdb)
    #uuid"0882fadc-4da3-41e0-9acb-b85452880289")
  ;=>
  ;{:type :owned-part,
  ; :belongs-to #uuid"703daad1-f2ef-4812-912d-d8f0d8f019f5",
  ; :is-of-type [#uuid"02d3ae1d-9842-40de-b958-74bc03d51e4c"],
  ; :status :part/added,
  ; :xt/id #uuid"0882fadc-4da3-41e0-9acb-b85452880289"}

  (xt/entity-tx (xt/db user/!xtdb)
    #uuid"0882fadc-4da3-41e0-9acb-b85452880289")
  ;=>
  ;{:xt/id #xtdb/id"032f9b7219df4460f9a0b29328dde3c1b5b43a4d",
  ; :xtdb.api/content-hash #xtdb/id"e4a24277c237981b71c19589ba25ea395182e697",
  ; :xtdb.api/valid-time #inst"2023-08-11T20:04:45.588-00:00",
  ; :xtdb.api/tx-time #inst"2023-08-11T20:04:45.588-00:00",
  ; :xtdb.api/tx-id 2918}

  )

(comment
  ;; how many different parts (part-num) do we have in the database?
  (->> (xt/q (xt/db user/!xtdb)
         '{:find [part-num (count op)]
           :where [[p :type :part]
                   [p :rebrickable.part/part-num part-num]
                   [op :is-of-type p]]
           :order-by [[(count op) :desc]]})
    (count))
  ;; => 2047

  (->> (xt/q (xt/db user/!xtdb)
         '{:find [part-num (count op)]
           :where [[p :type :part]
                   [p :rebrickable.part/part-num part-num]
                   [op :is-of-type p]]
           :order-by [[(count op) :desc]]})
    (take 10))
  ;=>
  ;(["2780" 1173]
  ; ["3023" 1150]
  ; ["6141" 972]
  ; ["3004" 876]
  ; ["3710" 699]
  ; ["3666" 535]
  ; ["3024" 531]
  ; ["3749" 517]
  ; ["3005" 501]
  ; ["3062b" 474])

  ;; how many parts don't have a part-num? - it seems that all the parts in the database have a number in string form.
  (->> (xt/q (xt/db user/!xtdb)
         '{:find [(pull p [*])]
           :where [[p :type :part]]})
    (map first)
    (map :rebrickable.part/part-num)
    (sort)
    (take 10))

  (->> (xt/q (xt/db user/!xtdb)
         '{:find [(count op)]
           :where [[p :type :part]
                   [p :rebrickable.part/part-num nil]
                   [op :is-of-type p]]}))

  (->> (xt/q (xt/db user/!xtdb)
         '{:find [(count op)]
           :where [[p :type :part]
                   (not-join [p] [p :rebrickable.part/part-num])
                   [op :is-of-type p]]}))
  )
