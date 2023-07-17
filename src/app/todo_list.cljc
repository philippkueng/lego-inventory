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
             :rebrickable-set-detail})

(def !client-state (atom {:page :rebrickable-sets}))

(defn goto-page! [page page-options]
  (swap! !client-state (fn [state]
                         (-> state
                             (assoc :page page)
                             (assoc :page-options page-options)))))

(e/defn LegoSetDetail [id]
  (e/server
   (let [e (xt/entity db id)]
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
       #_(dom/pre (dom/text (e/server (pr-str e)))))))))

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
                                        parts (bh/fetch-parts v (:xt/id set))]
                                    (xt/submit-tx !xtdb [[::xt/put set]])
                                    (xt/submit-tx !xtdb (->> parts
                                                             (map (fn [p] [::xt/put p]))
                                                             (into []))))
                                  (println "Set with ID" v "is already in the database."))))))
                          "Rebrickable Set ID..."))))

#?(:clj
   (defn lego-sets [db]
     (->> (xt/q db '{:find [(pull ?e [:xt/id :rebrickable/name :rebrickable/id :imported-at])]
                     :where [[?e :type :set]]})
          (map first)
          (sort-by :imported-at)
          (reverse)
          vec)))

(comment
  (lego-sets user/db)
  (lego-sets (xt/db user/!xtdb)))

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
            (ui/button (e/fn [] (goto-page! :rebrickable-sets nil)) (dom/text "Lego Sets")))))

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
                     (e/for-by :xt/id [{:keys [xt/id]} (e/offload #(lego-sets db))]
                               (LegoSet. id))))))

          :rebrickable-set-detail
          (LegoSetDetail. (let [state (e/watch !client-state)]
                            (:xt/id (:page-options state))))))))))
