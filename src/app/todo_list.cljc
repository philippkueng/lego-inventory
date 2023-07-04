(ns app.todo-list
  (:require #?(:clj [app.xtdb-contrib :as db])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [xtdb.api #?(:clj :as :cljs :as-alias) xt]
            #?(:clj [clj-http.client :as client])))

(e/def !xtdb)
(e/def db) ; injected database ref; Electric defs are always dynamic

#?(:clj (defonce api-url "https://rebrickable.com/api/v3/lego"))
#?(:clj (defonce api-key (System/getenv "REBRICKABLE_API_KEY")))

(e/defn LegoSet [id]
  (e/server
   (let [e (xt/entity db id)]
     (e/client
      (dom/ul
       (dom/li (dom/text (e/server (format "id: %s - name: %s" (:rebrickable/id e) (:rebrickable/name e))))))))))

(e/defn InputSubmit [F placeholder-message]
  ; Custom input control using lower dom interface for Enter handling
  (dom/input (dom/props {:placeholder placeholder-message})
             (dom/on "keydown" (e/fn [e]
                                 (when (= "Enter" (.-key e))
                                   (when-some [v (contrib.str/empty->nil (-> e .-target .-value))]
                                     (new F v)
                                     (set! (.-value dom/node) "")))))))

(e/defn TodoCreate []
  (e/client
   (InputSubmit. (e/fn [v]
                   (e/server
                    (e/discard
                     (e/offload
                      #(xt/submit-tx !xtdb [[:xtdb.api/put
                                             {:xt/id (random-uuid)
                                              :task/description v
                                              :task/status :active}]])))))
                 "Buy milk")))

#?(:clj (defn fetch-set [id]
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
                  :rebrickable/id (:set_num body)
                  :rebrickable/name (:name body)
                  :rebrickable/url (:set_url body)
                  :rebrickable/release-year (:year body)
                  :rebrickable/theme-id (:theme_id body)
                  :rebrickable/image-url (:set_url body)})))))

(comment

  (fetch-set "6815-1")
;; => {:type :set, :xt/id #uuid "7b6a561e-5a76-4c39-8c04-c28af869b151", :rebrickable/id "6815-1", :rebrickable/name "Hovertron", :rebrickable/url "https://rebrickable.com/sets/6815-1/hovertron/", :rebrickable/release-year 1996, :rebrickable/theme-id 131, :rebrickable/image-url "https://rebrickable.com/sets/6815-1/hovertron/"}
  )

#?(:clj
   (defn fetch-parts [id]
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
                   (repeat (:quantity part)
                           {:type :part
                            :xt/id (random-uuid)
                            ;;:rebrickable/id (:id part)
                            :rebrickable/element-id (:element_id part) ;; why are some element-id values nil?
                            :rebrickable/name (-> part :part :name)
                            :rebrickable/url (-> part :part :part_url)
                            :rebrickable/image-url (-> part :part :part_img_url)
                            :part/number (-> part :part :part_num)
                            :color/id (-> part :color :id)
                            :color/name (-> part :color :name)})))
            flatten))))

(comment

  (repeat 2 "hello"))

(comment
  (count (fetch-parts "8446-1"))

  (count (fetch-parts "6512-1")))

(e/defn ImportSet []
  (e/client
   (InputSubmit. (e/fn [v]
                   (e/server
                    (e/discard
                     (e/offload
                      #(let [set (fetch-set v)
                             parts (fetch-parts v)]
                         (xt/submit-tx !xtdb [[::xt/put set]])
                         (xt/submit-tx !xtdb (->> parts
                                                  (map (fn [p] [::xt/put p]))
                                                  (into []))))))))
                 "Rebrickable Set ID...")))

#?(:clj
   (defn lego-sets [db]
     (->> (xt/q db '{:find [(pull ?e [:xt/id :rebrickable/name :rebrickable/id])]
                     :where [[?e :type :set]]})
          (map first)
          (sort-by :rebrickable/id)
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

(e/defn Todo-list []
  (e/server
   (binding [!xtdb user/!xtdb
             db (new (db/latest-db> user/!xtdb))]
     (e/client
      (dom/link (dom/props {:rel :stylesheet :href "/todo-list.css"}))
      (dom/h1 (dom/text "Lego inventory"))
      (dom/div
       (ImportSet.)
       (dom/div
        (dom/h1 (dom/text "Sets:"))
        (e/server
         (e/for-by :xt/id [{:keys [xt/id]} (e/offload #(lego-sets db))]
                   (LegoSet. id)))))))))
