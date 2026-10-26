(ns fitness.events
  "Event handlers of the fitness example, registered from data (`handlers`).

  Event ids follow the benchmark's logical vocabulary (benchmarks/fitness-baseline/docs/
  UI_SEMANTICS.md); the mapping to acceptance traces is listed in apps/fitness/README.md.
  Handlers are pure: time comes from the :now coeffect (cloview.fx.timer, epoch ms) and
  the local date from :fitness/today, so every host computes the same state."
  (:require [clojure.string :as str]
            [clogem.fitness.catalog :as c]
            [clogem.fitness.domain :as d]
            [cloview.core.store :as st]
            [cloview.core.text :as text]
            [cloview.fx.timer :as timer]
            [fitness.dates :as dates]
            [fitness.derive :as derive]
            [fitness.hr-import :as hr]))

(def tabs #{:catalog :plan :log :analytics :calendar})
(def filter-dimensions #{:muscles :intensities :equipment})
(def metrics #{"sets" "weighted-minutes"})
(def modes #{"actual" "planned"})
(def body-styles #{:two-d :three-d})

;; ---------------------------------------------------------------------------
;; Helpers

(defn- plan-by-id [doc id] (first (filter #(= id (:id %)) (:plans doc))))

(defn- update-plan [db plan-id f]
  (update-in db [:doc :plans] (fn [plans] (mapv #(if (= plan-id (:id %)) (f %) %) plans))))

(defn- item-of [plan item-id] (first (filter #(= item-id (:id %)) (:items plan))))

(defn- index-of [plan item-id]
  (first (keep-indexed (fn [i it] (when (= item-id (:id it)) i)) (:items plan))))

(defn- snapshot
  "The exercise as recorded at start/backfill time (later catalog edits never rewrite it)."
  [doc exercise-id]
  (derive/exercise-by-id doc exercise-id))

(defn- persist
  "Effects map with a storage write of the document when persistence is enabled."
  [db effects]
  (if (get-in db [:settings :persist?])
    (assoc effects :storage/put {:key "fitness-document" :value (:doc db)
                                 :on-success [:doc/saved] :on-failure [:doc/save-failed]})
    effects))

(defn- with-doc-change
  "{:db db'} plus the save effect, with the save status shown as unsaved/saving."
  [db]
  (let [db (assoc-in db [:status :save] (if (get-in db [:settings :persist?]) :saving :unsaved))]
    (persist db {:db db})))

(defn- message [db text] (assoc-in db [:status :message] text))

(defn recover-active
  "DOM-004: after a reload, the active item's open interval [start nil] has an unknown
  length (the process stopped somewhere inside it). Drop it — it must never count as
  completed work — and remember where it started, so the session shows as interrupted
  and Resume opens a fresh interval. Closed intervals are kept as they are."
  [doc]
  (let [active (:active doc)
        [a b] (peek (:segments active))]
    (if (and active a (nil? b))
      (assoc doc :active (-> active
                             (update :segments pop)
                             (update :interrupted-at (fnil conj []) a)))
      doc)))

(defn active-paused?
  "True when the active item has no open interval (paused, interrupted, or not started)."
  [active]
  (let [segments (:segments active)
        [_ b] (peek segments)]
    (or (empty? segments) (some? b))))

(defn latest-training-week
  "[from to] of the 7 days ending at the latest record date (or `today`)."
  [doc today]
  (let [latest (reduce (fn [m r] (if (and (dates/valid? (:date r)) (pos? (compare (:date r) m))) (:date r) m))
                       "0000-01-01"
                       (:records doc))
        to (if (= latest "0000-01-01") today latest)]
    [(dates/plus-days to -6) to]))

;; ---------------------------------------------------------------------------
;; Handlers

(def events-db
  {;; navigation
   :nav/tab (fn [db [_ tab]] (if (contains? tabs tab) (assoc-in db [:ui :tab] tab) db))
   :nav/split (fn [db [_ ratio]] (assoc-in db [:ui :split] (max 0.2 (min 0.8 (double ratio)))))

   ;; catalog (UI-003 / UI-004)
   :catalog/query-change (fn [db [_ s]] (assoc-in db [:catalog :query] (str s)))
   :catalog/filter-toggle
   (fn [db [_ dim id on?]]
     ;; logical event catalog/filter-change: dimension + resulting id set
     (if (contains? filter-dimensions dim)
       (update-in db [:catalog :filters dim] (fn [ids] ((if on? conj disj) (set ids) id)))
       db))
   :catalog/clear-dimension (fn [db [_ dim]] (assoc-in db [:catalog :filters dim] #{}))
   :catalog/clear (fn [db _] (assoc db :catalog (assoc (:catalog db) :query ""
                                                       :filters {:muscles #{} :intensities #{} :equipment #{}})))
   :catalog/select (fn [db [_ id]] (assoc-in db [:catalog :selected] id))

   ;; plan builder
   :plan/select (fn [db [_ id]] (assoc-in db [:plan :selected] id))
   :plan/item-move
   (fn [db [_ plan-id item-id delta]]
     (update-plan db plan-id (fn [plan]
                               (if-some [i (index-of plan item-id)]
                                 (d/reorder-item plan item-id (+ i delta))
                                 plan))))
   :plan/item-remove (fn [db [_ plan-id item-id]] (update-plan db plan-id #(d/remove-item % item-id)))

   ;; analytics (DOM-008, EXT-003..005)
   :analytics/metric (fn [db [_ m]] (if (contains? metrics m) (assoc-in db [:analytics :metric] m) db))
   :analytics/mode (fn [db [_ m]] (if (contains? modes m) (assoc-in db [:analytics :mode] m) db))
   :analytics/range
   (fn [db [_ from to]]
     (if (and (dates/valid? from) (dates/valid? to) (<= (dates/->days from) (dates/->days to)))
       (update db :analytics assoc :from from :to to)
       (message db "Invalid date range")))
   :analytics/open-date
   (fn [db [_ date]]
     (if (dates/valid? date)
       (-> db (update :analytics assoc :from date :to date) (assoc-in [:ui :tab] :analytics))
       db))
   :body/select-muscle
   (fn [db [_ id]]
     ;; never touches records or aggregates; unknown ids clear the selection
     (assoc-in db [:analytics :selected-muscle] (when (contains? derive/muscle-names id) id)))
   :body/style-change (fn [db [_ style]] (if (contains? body-styles style) (assoc-in db [:analytics :body-style] style) db))

   ;; calendar (DOM-009)
   :calendar/select-date (fn [db [_ date]] (if (dates/valid? date) (assoc-in db [:calendar :selected] date) db))
   :calendar/change-month
   (fn [db [_ month]] (if (dates/month-in-ui-range? month) (assoc-in db [:calendar :month] month) db))

   ;; heart-rate import (DOM-005)
   :hr/offset-change
   (fn [db [_ s]]
     (let [n (when-not (str/blank? s) (some-> (re-matches #"\s*(-?\d{1,4})\s*" (str s)) second parse-long))
           db (assoc-in db [:hr-import :offset-text] (str s))
           db (assoc-in db [:hr-import :offset-min] n)]
       (if-some [text (get-in db [:hr-import :text])]
         (assoc-in db [:hr-import :preview] (hr/preview text {:offset-min n}))
         db)))
   :hr/cancel (fn [db _] (assoc db :hr-import {:status :cancelled :offset-text (get-in db [:hr-import :offset-text] "")
                                               :offset-min (get-in db [:hr-import :offset-min])}))
   :hr/read
   (fn [db [_ path text]]
     ;; EFFECT-004: while a read is awaited, only that read may open a preview (a late
     ;; result of an earlier pick is dropped)
     (if (let [awaited (get-in db [:hr-import :reading])]
           (and (some? awaited) (not= path awaited)))
       db
       (assoc db :hr-import {:status :preview :source path :text text
                             :offset-text (get-in db [:hr-import :offset-text] "")
                             :offset-min (get-in db [:hr-import :offset-min])
                             :preview (hr/preview text {:offset-min (get-in db [:hr-import :offset-min])})})))
   :hr/read-failed (fn [db [_ err]]
                     (if (= :reading (get-in db [:hr-import :status]))
                       (-> db (assoc-in [:hr-import :status] :failed) (assoc-in [:hr-import :error] err))
                       db))
   :hr/pick-cancelled (fn [db _] (-> db (assoc-in [:hr-import :status] :idle)
                                     (message "Import cancelled; stored samples unchanged")))
   ;; ARCH-003: a host without a file chooser (Flutter until the file_picker plugin is
   ;; wired) reports :unsupported; say so instead of pretending the user cancelled
   :pick/failed (fn [db [_ purpose err]]
                  (-> (if (= purpose :hr) (assoc-in db [:hr-import :status] :idle) db)
                      (message (if (= :unsupported (:error err))
                                 "Choosing files is not available on this host yet"
                                 (str "The file chooser failed: " (:error err))))))
   :media/pick-cancelled (fn [db _] (message db "No file chosen; the media is unchanged"))
   :media/failed (fn [db [_ _id err]] (message db (str "Media import failed: " (or (:error err) err))))
   ;; emitted by a media-player renderer (once per source value): {:path :reason}
   :media/error (fn [db [_ id {:keys [reason]}]]
                  (message db (str "Media for " id " unavailable (" (some-> reason name) ")")))

   ;; persistence (EFFECT-001)
   :settings/persist (fn [db [_ on?]] (assoc-in db [:settings :persist?] (true? on?)))
   :doc/saved (fn [db _] (assoc-in db [:status :save] :saved))
   :doc/save-failed (fn [db [_ err]] (-> db (assoc-in [:status :save] :failed) (assoc-in [:status :error] err)))
   ;; DOM-010: the catalog migration is idempotent, so it runs on every load
   :doc/loaded (fn [db [_ doc]] (if (and (map? doc) (sequential? (:exercises doc)))
                                  (assoc db :doc (recover-active (c/migrate-document doc)))
                                  db))
   ;; EFFECT-002: unreadable stored data is reported, never replaced by an empty store; the
   ;; storage effect refuses writes until the files are repaired
   :doc/load-failed (fn [db [_ err]]
                      (-> db
                          (assoc-in [:status :save] :failed)
                          (assoc-in [:status :error] err)
                          (message (str "Stored data could not be read (" (some-> (:reason err) name)
                                        "); showing the demo document, nothing will be saved"))))})

(def events-fx
  {:plan/item-copy
   {:interceptors [(st/inject-cofx :now)]
    :handler (fn [{:keys [db now]} [_ plan-id item-id]]
               (let [plan (plan-by-id (:doc db) plan-id)]
                 (if (item-of plan item-id)
                   (with-doc-change (update-plan db plan-id #(d/duplicate-item % item-id (str item-id "-" now))))
                   {:db db})))}

   :plan/add-exercise
   {:interceptors [(st/inject-cofx :now) (st/inject-cofx :fitness/today)]
    :handler (fn [{:keys [db now today]} [_ exercise-id]]
               (let [doc (:doc db)
                     plan-id (or (get-in db [:plan :selected]) (:id (first (:plans doc))))
                     item {:id (str "item-" now) :exercise-id exercise-id :duration-sec 60 :reps 10 :target-hr 120}]
                 (if (plan-by-id doc plan-id)
                   (with-doc-change (-> (update-plan db plan-id #(update % :items (fnil conj []) item))
                                        (message (str "Added to plan " plan-id))))
                   (let [plan {:id (str "plan-" now) :date today :name "New plan" :items [item]}]
                     (with-doc-change (-> db
                                          (update-in [:doc :plans] (fnil conj []) plan)
                                          (assoc-in [:plan :selected] (:id plan))))))))}

   ;; active training (DOM-002 / DOM-003 / DOM-004)
   :training/start
   {:interceptors [(st/inject-cofx :now) (st/inject-cofx :fitness/today)]
    :handler (fn [{:keys [db now today]} [_ plan-id item-id]]
               (let [doc (:doc db)
                     item (item-of (plan-by-id doc plan-id) item-id)]
                 (cond
                   (some? (:active doc)) {:db (message db "A training item is already active")}
                   (nil? item) {:db db}
                   :else
                   (with-doc-change
                     (assoc-in db [:doc :active]
                               {:id (str "record-" now) :plan-id plan-id :item-id item-id
                                :exercise (snapshot doc (:exercise-id item))
                                :started-at now :segments [[now nil]]
                                :reps (:reps item) :target-hr (:target-hr item)
                                :date today :duration-source "timer"})))))}

   :training/pause
   {:interceptors [(st/inject-cofx :now)]
    :handler (fn [{:keys [db now]} _]
               (let [active (get-in db [:doc :active])
                     [a b] (peek (:segments active))]
                 (if (and active a (nil? b))
                   (with-doc-change (assoc-in db [:doc :active :segments]
                                              (conj (pop (:segments active)) [a (max a now)])))
                   {:db db})))}

   :training/resume
   {:interceptors [(st/inject-cofx :now)]
    :handler (fn [{:keys [db now]} _]
               (let [active (get-in db [:doc :active])]
                 (if (and active (active-paused? active))
                   (with-doc-change (update-in db [:doc :active :segments] (fnil conj []) [now nil]))
                   {:db db})))}

   :training/complete
   {:interceptors [(st/inject-cofx :now)]
    :handler (fn [{:keys [db now]} _]
               ;; idempotent: a second completion finds no active item and does nothing
               (if-some [active (get-in db [:doc :active])]
                 (let [record (d/finish-record active (max now (:started-at active)) {:duration-source "timer"})]
                   (with-doc-change (-> db
                                        (update-in [:doc :records] (fnil conj []) record)
                                        (assoc-in [:doc :active] nil)
                                        (message "Training item completed"))))
                 {:db db}))}

   :training/backfill
   {:interceptors [(st/inject-cofx :now) (st/inject-cofx :fitness/today)]
    :handler (fn [{:keys [db now today]} [_ plan-id item-id]]
               ;; completion = click time; start = completion - planned duration; the source
               ;; says it is an estimate from the plan, not measured time
               (let [doc (:doc db)
                     item (item-of (plan-by-id doc plan-id) item-id)
                     ms (* 1000 (long (or (:duration-sec item) 0)))]
                 (if (and item (pos? ms))
                   (with-doc-change
                     (update-in db [:doc :records] (fnil conj [])
                                {:id (str "record-" now) :plan-id plan-id :item-id item-id
                                 :exercise (snapshot doc (:exercise-id item))
                                 :started-at (- now ms) :completed-at now :segments [[(- now ms) now]]
                                 :reps (:reps item) :target-hr (:target-hr item)
                                 :date today :duration-source "plan-estimate"}))
                   {:db db})))}

   :calendar/go-today
   {:interceptors [(st/inject-cofx :fitness/today)]
    :handler (fn [{:keys [db today]} _]
               {:db (-> db (assoc-in [:calendar :month] (dates/month-of today))
                        (assoc-in [:calendar :selected] today))})}

   :analytics/preset
   {:interceptors [(st/inject-cofx :fitness/today)]
    :handler (fn [{:keys [db today]} [_ preset]]
               (let [[from to] (case preset
                                 :last-7 [(dates/plus-days today -6) today]
                                 :last-30 [(dates/plus-days today -29) today]
                                 :latest-week (latest-training-week (:doc db) today)
                                 [nil nil])]
                 {:db (if from (update db :analytics assoc :from from :to to :preset preset) db)}))}

   ;; heart-rate import: pick -> read -> preview -> confirm/cancel
   :hr/pick
   (fn [{:keys [db]} _]
     {:db (assoc-in db [:hr-import :status] :picking)
      :file/pick {:title "Import heart-rate data" :extensions ["csv" "json"]
                  :on-picked [:hr/picked] :on-cancel [:hr/pick-cancelled] :on-failure [:pick/failed :hr]}})
   :hr/picked
   (fn [{:keys [db]} [_ paths]]
     (let [path (first paths)]
       {:db (update db :hr-import assoc :status :reading :reading path)
        :file/read {:path path :on-success [:hr/read path] :on-failure [:hr/read-failed]}}))
   :hr/confirm
   {:interceptors [(st/inject-cofx :now)]
    :handler (fn [{:keys [db now]} _]
               (let [{:keys [preview source]} (:hr-import db)]
                 (cond
                   ;; EFFECT-003: no merged commit while a training item is active; the
                   ;; preview stays, so the import can be confirmed afterwards
                   (some? (get-in db [:doc :active]))
                   {:db (message db "Finish the active training item before importing heart-rate data")}

                   ;; the merge always applies to the current document (never a snapshot
                   ;; taken when the file was read), so later edits cannot be overwritten
                   (and preview (not (:error preview)) (pos? (:valid preview)))
                   (with-doc-change
                     (-> db
                         (update :doc hr/commit preview {:id (str "hr-" now) :imported-at now :source source})
                         (assoc :hr-import {:status :committed :committed (:valid preview)
                                            :offset-text (get-in db [:hr-import :offset-text] "")
                                            :offset-min (get-in db [:hr-import :offset-min])})))

                   :else {:db db})))}

   ;; exercise media (EXT-001): pick -> per-app directory -> managed copy -> reference
   ;; the copy; a cancelled pick never erases the previous media
   :media/pick
   (fn [{:keys [db]} [_ exercise-id]]
     {:db db
      :file/pick {:title "Import exercise media" :extensions ["gif" "mp4"]
                  :on-picked [:media/picked exercise-id] :on-cancel [:media/pick-cancelled]
                  :on-failure [:pick/failed :media]}})
   :media/picked
   (fn [{:keys [db]} [_ exercise-id paths]]
     {:db (message db "Copying media...")
      :file/app-dir {:on-success [:media/app-dir exercise-id (first paths)] :on-failure [:media/failed exercise-id]}})
   :media/app-dir
   {:interceptors [(st/inject-cofx :now)]
    :handler (fn [{:keys [db now]} [_ exercise-id src dir]]
               (let [ext (let [p (str src) i (str/last-index-of p ".")] (if i (text/ascii-lower (subs p (inc i))) "bin"))]
                 {:db db
                  :file/copy {:from src :to (str dir "/media/" exercise-id "-" now "." ext)
                              :on-success [:media/copied exercise-id] :on-failure [:media/failed exercise-id]}}))}
   :media/copied
   (fn [{:keys [db]} [_ exercise-id dest]]
     (with-doc-change
       (-> db
           (update-in [:doc :exercises] (fn [xs] (mapv #(if (= exercise-id (:id %)) (assoc % :media-path dest) %) xs)))
           (message "Media imported (managed copy)"))))

   :doc/save (fn [{:keys [db]} _] (with-doc-change db))
   :doc/load
   (fn [{:keys [db]} _]
     (if (get-in db [:settings :persist?])
       {:db db :storage/get {:key "fitness-document" :on-loaded [:doc/loaded] :on-failure [:doc/load-failed]}}
       {:db db}))})

(def handlers
  {:events-db events-db
   :events-fx events-fx
   ;; :now is also installed by cloview.fx.timer/install! (same value); registering it
   ;; here lets the app run with host effects disabled (smokes, tests)
   :cofx {:now (fn [cofx] (assoc cofx :now (timer/now-ms)))
          :fitness/today (fn [cofx] (assoc cofx :today (dates/local-today)))}})
