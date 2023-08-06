(ns app.todo-list
  (:require #?(:clj [app.xtdb-contrib :as db])
            #?(:clj [app.backend-helpers :as bh])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [xtdb.api #?(:clj :as :cljs :as-alias) xt]
            #?(:clj [clj-http.client :as client])))

(e/def !xtdb)
(e/def db) ; injected database ref; Electric defs are always dynamic

(def pages #{:rebrickable-sets ;; a view to aid with importing sets from rebrickable
             ;:rebrickable-parts-by
             :rebrickable-set-detail
             :rebrickable-part-detail ;; given a specific part, show where its being used
             :owned-sets                                    ;; an overview of the progress
             :owned-set-detail ;; one can see the progress of how many parts are still missing vs have been found
             })

(def !client-state (atom #_{:page :rebrickable-sets}
                    #_{:page :rebrickable-set-detail, :page-options {:xt/id #uuid "2813b8e4-71f1-4bea-84af-66201e5ca55a"}}
                    #_{:page :rebrickable-part-detail, :page-options {:xt/id #uuid "2813b8e4-71f1-4bea-84af-66201e5ca55a"}}
                    #_{:page :rebrickable-set-detail, :page-options {:xt/id #uuid "2813b8e4-71f1-4bea-84af-66201e5ca55a"}}
                    #_{:page :rebrickable-part-detail, :page-options {:rebrickable.part/part-num "4589"}}
                         #_{:page :rebrickable-set-detail, :page-options {:xt/id #uuid "44246b35-99e1-4f6c-92db-022e5db9df0f"}}
                     #_{:page :rebrickable-part-detail, :page-options {:rebrickable/element-id "6252045"}}
                     {:page :owned-set-detail, :page-options {:xt/id #uuid "dc9b7524-bc31-4526-ac1e-b3ee0f2ea7a8"}}
                     #_{:page :rebrickable-parts-by-part-num, :page-options nil}
                         #_{:page :rebrickable-part-detail, :page-options {:rebrickable.part/part-num 3294662}}))

(defn goto-page! [page page-options]
  (swap! !client-state (fn [state]
                         (-> state
                             (assoc :page page)
                             (assoc :page-options page-options)))))

(e/defn LegoPartDetail [rebrickable-element-id]
  (e/client
   (let [part (e/server (e/offload #(bh/part-metadata db rebrickable-element-id)))]
     (e/client
      (dom/div (dom/props {:class "lego-part-detail"})
               (dom/h1 (dom/text "Lego part: " (:rebrickable/name part)))
               (dom/img (dom/props {:src (:rebrickable/image-url part)}))
               (dom/div (dom/props {:class "attributes-table"})
                        (dom/div
                         (dom/span (dom/text "Rebrickable Part Number"))
                         (dom/span (dom/text (:rebrickable.part/part-num part))))
                       (dom/div
                         (dom/span (dom/text "Rebrickable Element Id"))
                         (dom/span (dom/text (:rebrickable/element-id part))))
                        (dom/div
                         (dom/span (dom/text "Rebrickable URL"))
                         (dom/a (dom/props {:href (:rebrickable/url part)})
                                (dom/text "link")))
                        (dom/div
                         (dom/span (dom/text "Color"))
                         ;; todo I think the part/part-num defined the mold while the id is the mold with the color
                         (dom/span (dom/text (:color/name part))))

                        (dom/div
                         (dom/span (dom/text "Overall occurrence of part within sets"))
                         (dom/span (dom/text (e/server (e/offload #(bh/part-occurrence-across-sets db rebrickable-element-id))))))

                        #_(dom/pre (dom/text (pr-str part))))
               (dom/h2 (dom/text "Occurrence of part in sets"))

               (dom/div (dom/props {:class "part-occurrence-in-sets"})
                        (e/for [set-with-occurrence (e/server (e/offload #(bh/part-occurrence-in-sets db rebrickable-element-id)))]
                          (dom/div
                           (dom/div

                            (dom/span (dom/text "Set"))
                            (ui/button (e/fn []
                                         (e/client (goto-page! :rebrickable-set-detail {:xt/id (-> set-with-occurrence first :xt/id)})))
                                       (dom/text (-> set-with-occurrence first :rebrickable/name))))
                           (dom/div
                            (dom/span (dom/text "Occurrence"))
                            (dom/span (dom/text (-> set-with-occurrence second))))))))))))


(e/defn LegoSetDetail [id]
  (let [e (e/server (xt/entity db id))]
    (e/client
     (dom/div (dom/props {:class "lego-set-detail"})
              (dom/h1 (dom/text (:rebrickable/name e)))
              (dom/img (dom/props {:src (:rebrickable/image-url e)}))
              (dom/div (dom/props {:class "attributes-table"})
                       (dom/div
                        (dom/span (dom/text "Internal Id"))
                        (dom/span (dom/text (:xt/id e))))
                       (dom/div
                        (dom/span (dom/text "Id"))
                        (dom/span (dom/text (:rebrickable/id e))))
                       (dom/div
                        (dom/span (dom/text "Rebrickable entry"))
                        (dom/a (dom/props {:href (:rebrickable/url e)})
                               (dom/text "link"))))
              (dom/h2 (dom/text "Owned Sets"))
              (dom/div
               (e/for [owned-set-id (e/server (bh/owned-sets-for-set db id))]
                 (dom/div
                  (ui/button (e/fn [] (e/client (goto-page! :owned-set-detail {:xt/id owned-set-id})))
                             (dom/text owned-set-id)))))
              (dom/h2 (dom/text "Parts of this set"))
              (dom/div (dom/props {:class "part-list"})
                       (e/for [part (e/server (bh/lego-parts-for-set db id))]
                         (dom/div
                          (dom/img (dom/props {:src (:rebrickable/image-url (-> part second first))}))
                          (dom/div
                           (dom/div
                            (dom/span (dom/text "Part Number"))
                            (dom/span (dom/text (-> part second first :rebrickable.part/part-num))))
                           (dom/div
                            (dom/span (dom/text "Part Element Id"))
                            (let [element-id (-> part second first :rebrickable/element-id)]
                              (ui/button (e/fn []
                                           (e/client (goto-page! :rebrickable-part-detail {:rebrickable/element-id element-id})))
                                         (dom/text element-id)))))
                          (dom/div
                           (dom/div
                            (dom/span (dom/text "Name"))
                            (dom/span (dom/text (-> part second first :rebrickable/name))))
                           (dom/div
                            (dom/span (dom/text "Color"))
                            (dom/span (dom/text (-> part second first :color/name)))))
                          (dom/div
                           (dom/span (dom/text "Number of pieces"))
                           (dom/span (dom/text (-> part second count))))))
                       #_(e/server
                          (e/for-by :xt/id [{:keys [xt/id]} (e/offload #(bh/lego-sets db))]
                                    (LegoSet. id))))
              #_(dom/pre (dom/text (e/server (pr-str e))))))))

(e/defn LegoSet [id]
  (e/server
   (let [e (xt/entity db id)]
     (e/client
      (dom/li
       (dom/div
        (dom/img (dom/props {:src (:rebrickable/image-url e)}))
        (dom/div
         (dom/span (dom/text "Id"))
         (ui/button (e/fn [] (e/client (goto-page! :rebrickable-set-detail {:xt/id id})))
                    (dom/text (:rebrickable/id e))))
        (dom/div
         (dom/span (dom/text "Name"))
         (dom/span (dom/text (:rebrickable/name e))))
        (dom/div
         (dom/span (dom/text "Part count"))
         (dom/span (dom/text (str "Distinct: " (e/server (e/offload #(bh/distinct-number-of-parts-of-set db id))))))
         (dom/span (dom/text (str "Absolut: " (e/server (e/offload #(bh/number-of-parts-of-set db id)))))))))))))

(e/defn InputSubmit [F placeholder-message]
  ; Custom input control using lower dom interface for Enter handling
  (dom/input (dom/props {:placeholder placeholder-message})
             (dom/on "keydown" (e/fn [e]
                                 (when (= "Enter" (.-key e))
                                   (when-some [v (contrib.str/empty->nil (-> e .-target .-value))]
                                     (new F v)
                                     (set! (.-value dom/node) "")))))))

(comment

  (bh/fetch-set "6815-1")
;; => {:type :set, :xt/id #uuid "7b6a561e-5a76-4c39-8c04-c28af869b151", :rebrickable/id "6815-1", :rebrickable/name "Hovertron", :rebrickable/url "https://rebrickable.com/sets/6815-1/hovertron/", :rebrickable/release-year 1996, :rebrickable/theme-id 131, :rebrickable/image-url "https://rebrickable.com/sets/6815-1/hovertron/"}
  )

(comment

  (repeat 2 "hello"))

(comment
  (count (bh/fetch-parts "8446-1" 12))

  (first (bh/fetch-parts "8446-1" 12))
;; => {:rebrickable/name "Axle Hose, Soft 14L", :belongs-to 12, :rebrickable/image-url "https://cdn.rebrickable.com/media/parts/ldraw/0/32201.png", :type :part, :rebrickable/element-id "4121648", :color/name "Black", :color/id 0, :part/number "32201", :xt/id #uuid "7cca0439-b3cb-4a61-911b-0dd931d44579", :rebrickable/url "https://rebrickable.com/parts/32201/axle-hose-soft-14l/"}
  )
(e/defn ImportSet []
  (e/client
   (dom/div (dom/props {:class "import-set"})
            (dom/span (dom/text "Import set:"))
            (InputSubmit. (e/fn [v]
                            (e/server
                             (e/discard
                              (e/offload
                               #(if (not (bh/is-set-in-database? db v))
                                  (let [set (bh/fetch-set v)
                                        parts (bh/fetch-parts v (:xt/id set))
                                        minifigures-and-parts (bh/fetch-minifigure-parts-for-set v (:xt/id set))]
                                    (xt/submit-tx !xtdb [[::xt/put set]])
                                    (xt/submit-tx !xtdb (->> parts
                                                             (map (fn [p] [::xt/put p]))
                                                             (into [])))
                                    (xt/submit-tx !xtdb (->> minifigures-and-parts
                                                          (map (fn [p] [::xt/put p]))
                                                          (into []))))
                                  (println "Set with ID" v "is already in the database."))))))
                          "Rebrickable Set ID..."))))

(comment
  (bh/lego-sets user/db)
  (bh/lego-sets (xt/db user/!xtdb)))

(e/defn OwnedLegoSets []
  (dom/div
    (dom/h1 (dom/text "Owned Sets"))
    (dom/div (dom/props {:class "owned-sets"})
      (e/for [owned-set (e/server (e/offload #(bh/owned-sets db)))]
        (dom/div
          (dom/img (dom/props {:src (:rebrickable/image-url owned-set)}))
          (dom/div
            (dom/span (dom/text "Id"))
            (ui/button (e/fn [] (goto-page! :owned-set-detail {:xt/id (:os-internal-id owned-set)}))
              (dom/text (:rebrickable/id owned-set))))
          (dom/div
            (dom/span (dom/text "Name"))
            (dom/span (dom/text (:rebrickable/name owned-set))))
          (dom/div
            (dom/span (dom/text "Tracking"))
            (e/server
              (let [e (xt/entity db (:os-internal-id owned-set))
                    eid-str (bh/uuid->str (:xt/id e))
                    instructions-bagged (:instructions-bagged e)
                    instructions-bagged-id (str eid-str "_instructions_bagged")
                    sticker-on-bag (:sticker-on-bag e)
                    sticker-on-bag-id (str eid-str "_sticker_on_bag")]
                (e/client
                  (dom/div
                    (ui/checkbox instructions-bagged
                      (e/fn [v]
                        (e/server (xt/submit-tx !xtdb [[::xt/put (assoc e :instructions-bagged v)]]))
                        nil)
                      (dom/props {:id instructions-bagged-id}))
                    (dom/label (dom/props {:for instructions-bagged-id})
                      (dom/text "instructions put in bag")))
                  (dom/div
                    (ui/checkbox sticker-on-bag
                      (e/fn [v]
                        (e/server (xt/submit-tx !xtdb [[::xt/put (assoc e :sticker-on-bag v)]]))
                        nil)
                      (dom/props {:id sticker-on-bag-id}))
                    (dom/label (dom/props {:for sticker-on-bag-id})
                      (dom/text "sticker on bag"))))))))))))

(e/defn OwnedLegoSetDetail [page-options]
  (let [owned-set-id (:xt/id page-options)
        owned-set (e/server (e/offload #(bh/owned-set db owned-set-id)))]
    (dom/div (dom/props {:class "lego-set-detail"})
      (dom/h1 (dom/text "Owned: " (:rebrickable/name owned-set)))
      (dom/img (dom/props {:src (:rebrickable/image-url owned-set)}))
      (dom/div (dom/props {:class "attributes-table"})
        (dom/div
          (dom/span (dom/text "Internal Id"))
          (dom/span (dom/text (:owned-set-id owned-set))))
        (dom/div
          (dom/span (dom/text "Id"))
          (ui/button (e/fn []
                       (e/client (goto-page! :rebrickable-set-detail {:xt/id (:internal-lego-set-id owned-set)})))
            (dom/text (:rebrickable/id owned-set))))
        (dom/div
          (dom/span (dom/text "Rebrickable entry"))
          (dom/a (dom/props {:href (:rebrickable/url owned-set)})
            (dom/text "link"))))
      (dom/h2 (dom/text "Parts of this owned set"))
      (dom/div (dom/props {:class "part-list"})
        (e/server
          (e/for [owned-part (e/server (e/offload #(bh/owned-parts-for-owned-set db owned-set-id)))]
            (e/client
              (dom/div
                (dom/img (dom/props {:src (-> owned-part :rebrickable/image-url)}))
                (dom/div
                  (dom/div
                    (dom/span (dom/text "Part Number"))
                    (dom/span (dom/text (-> owned-part :rebrickable.part/part-num))))
                  (dom/div
                    (dom/span (dom/text "Part Element Id"))
                    (let [element-id (-> owned-part :rebrickable/element-id)]
                      (ui/button (e/fn []
                                   (e/client (goto-page! :rebrickable-part-detail {:rebrickable/element-id element-id})))
                        (dom/text element-id)))))
                (dom/div
                  (dom/div
                    (dom/span (dom/text "Name"))
                    (dom/span (dom/text (-> owned-part :rebrickable/name))))
                  (dom/div
                    (dom/span (dom/text "Color"))
                    (dom/span (dom/text (-> owned-part :color/name)))))
                (dom/div
                  (dom/div
                    (dom/span (dom/text "Status"))
                    (dom/span (dom/text (condp = (-> owned-part :owned-part/status)
                                          :part/missing "Missing"
                                          :part/added "Added"))))
                  (dom/div
                    (dom/span (dom/text "Change Status"))
                    (condp = (-> owned-part :owned-part/status)
                      :part/missing (ui/button (e/fn [] (e/server (e/offload #(bh/change-status-of-owned-part
                                                                                (-> owned-part :owned-part/id)
                                                                                :part/added))))
                                      (dom/text "Add to set"))
                      :part/added (ui/button (e/fn [] (e/server (e/offload #(bh/change-status-of-owned-part
                                                                              (-> owned-part :owned-part/id)
                                                                              :part/missing))))
                                    (dom/text "Mark as missing again")))))
                #_(dom/div
                    (dom/span (dom/text "Number of pieces"))
                    (dom/span (dom/text (-> part second count))))))

            ))
        #_(e/server
            (e/for-by :xt/id [{:keys [xt/id]} (e/offload #(bh/lego-sets db))]
              (LegoSet. id))))
      #_(dom/pre (dom/text (e/server (pr-str e)))))))

(comment
  ;; what are the most occuring parts in the database?
  (->> (xt/q (xt/db user/!xtdb)
             '{:find [?element-id (count ?e)]
               :where [[?e :type :part]
                       [?e :rebrickable/element-id ?element-id]]})
       (sort-by second)
       (reverse)
       (take 10)))

#?(:clj
   (defn todo-count [db]
     (count (xt/q db '{:find [?e] :in [$ ?status]
                       :where [[?e :task/status ?status]]}
                  :active))))

(comment (todo-count user/db))

(e/defn Navigation []
  (e/client
   (dom/div (dom/props {:class "navigation"})
            (ui/button (e/fn [] (goto-page! :rebrickable-sets nil)) (dom/text "Lego Sets"))
            (ui/button (e/fn [] (goto-page! :owned-sets nil)) (dom/text "Owned Sets"))
            (ui/button (e/fn [] (goto-page! :rebrickable-parts-by-part-num nil)) (dom/text "Lego Parts (by part number)"))
            (ui/button (e/fn [] (goto-page! :rebrickable-parts-by-element-id nil)) (dom/text "Lego Parts (by element id)"))
     )))

(e/defn Todo-list []
  (e/server
   (binding [!xtdb user/!xtdb
             db (new (db/latest-db> user/!xtdb))]
     (e/client
      (dom/link (dom/props {:rel :stylesheet :href "/todo-list.css"}))
      (let [state (e/watch !client-state)]
        (dom/pre (dom/text (pr-str state))))
      (Navigation.)
      (let [state (e/watch !client-state)
            page (:page state)]
        (condp = page
          :rebrickable-sets
          (dom/div
           (ImportSet.)
           (dom/div
            (dom/h1 (dom/text (str (e/server (bh/number-of-sets db))) " Sets with " (e/server (bh/number-of-parts db)) " parts"))
            (dom/ul (dom/props {:class "lego-sets"})
                    (e/server
                     (e/for-by :xt/id [{:keys [xt/id]} (e/offload #(bh/lego-sets db))]
                               (LegoSet. id))))))

          :rebrickable-set-detail
          (LegoSetDetail. (:xt/id (:page-options state)))

          :rebrickable-part-detail
          (LegoPartDetail. (:rebrickable/element-id (:page-options state)))

          :owned-sets (OwnedLegoSets.)

          :owned-set-detail
          (OwnedLegoSetDetail. (:page-options state))

          :rebrickable-parts-by-part-num
          (dom/div
            (dom/h1 (dom/text "Lego parts by part number"))
            (let [!search (atom "")
                  search (e/watch !search)]
              (ui/input search (e/fn [v] (reset! !search v))
                (dom/props {:type "search" :placeholder "part number..."}))

              (e/server
                (e/for [metadata (e/offload #(bh/part-metadata-by-part-number db search))]
                  (e/client
                    (dom/h1 (dom/text (-> metadata :rebrickable/name)))
                    (dom/img (dom/props {:width 100 :src (-> metadata :rebrickable/image-url)})) )))

              (dom/table
                (dom/tbody
                  (dom/tr
                    (dom/th (dom/text "part number"))
                    (dom/th (dom/text "element id"))
                    (dom/th (dom/text "colour"))
                    (dom/th (dom/text "set id"))
                    (dom/th (dom/text "set name"))
                    (dom/th (dom/text "status"))
                    (dom/th (dom/text "add/remove!"))
                    )
                  (e/server
                    (e/for [part (e/offload #(bh/owned-parts-by-part-number db search))]
                      (e/client
                        (dom/tr
                          (dom/td (dom/text (-> part :rebrickable.part/part-num)))
                          (dom/td (dom/text (-> part :rebrickable/element-id)))
                          (dom/td (dom/text (-> part :color/name)))
                          (dom/td (ui/button (e/fn []
                                               (e/client (goto-page! :rebrickable-set-detail {:xt/id (-> part :set-internal-id)})))
                                    (dom/text (-> part :set-rebrickable-id))))
                          (dom/td (dom/text (-> part :set-rebrickable-name)))
                          (dom/td (dom/text (-> part :owned-part/status)))
                          (dom/td
                            (condp = (-> part :owned-part/status)
                              :part/missing (ui/button (e/fn [] (e/server (e/offload #(bh/change-status-of-owned-part
                                                                                        (-> part :owned-part/id)
                                                                                        :part/added))))
                                              (dom/text "Add to set"))
                              :part/added (ui/button (e/fn [] (e/server (e/offload #(bh/change-status-of-owned-part
                                                                                      (-> part :owned-part/id)
                                                                                      :part/missing))))
                                            (dom/text "Mark as missing again"))))
                          ))))))

              ))))))))
