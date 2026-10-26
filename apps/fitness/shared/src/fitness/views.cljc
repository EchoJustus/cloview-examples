(ns fitness.views
  "The fitness app's root view shell, shared verbatim by JavaFX, Flutter and WPF:
  tabs for Catalog | Plan Builder | Execution Log | Anatomy & Analytics | Calendar.

  Everything here is canonical cloview vocabulary plus the portable composites
  (cloview.core.components); the four drawing surfaces go through fitness.native, whose
  renderers each host registers (docs/NATIVE_CONTRACTS.md)."
  (:require [clojure.string :as str]
            [clogem.fitness.catalog :as c]
            [cloview.core.components :as comps]
            [fitness.dates :as dates]
            [fitness.derive :as derive]
            [fitness.events :as events]
            [fitness.native :as native]))

(def palette {:ink "#243B36" :muted "#74817A" :teal "#258675" :orange "#E98D4F"
              :paper "#F6F5EF" :previous "#BCCDC5" :line "#D9E1D7"})

(defn- h2 [text] [:ui/text {:text text :style {:font-size 16 :font-weight :semibold :color (:ink palette)}}])
(defn- note [text] [:ui/text {:text text :wrap? true :style {:font-size 12 :color (:muted palette)}}])

(defn- panel
  "A titled section."
  [title & children]
  [:ui/box {:style {:padding 10 :border {:width 1 :color (:line palette)} :radius 8}}
   (into [:ui/column {:gap 8} (h2 title)] children)])

;; ---------------------------------------------------------------------------
;; Catalog (UI-001, UI-003, UI-004, UI-005, EXT-001)

(defn- filter-group [db dim label options]
  (let [selected (get (derive/effective-filters db) dim)]
    [:ui/column {:gap 4}
     [:ui/row {:gap 6 :align :center}
      [:ui/text {:text label :style {:font-weight :semibold}}]
      [:ui/spacer]
      [:ui/button {:label "Clear" :test-id (str "filter-" (name dim) "/clear")
                   :disabled? (empty? selected) :on-press [:catalog/clear-dimension dim]}]]
     [comps/chip-group {:options options
                        :selected selected
                        :on-toggle [:catalog/filter-toggle dim]
                        :empty-text "No options yet"
                        :test-id (str "filter-" (name dim))}]]))

(defn- exercise-row [selected-id exercise]
  (let [id (:id exercise)
        n (c/normalize-exercise exercise)]
    [:ui/button {:key id
                 :test-id (str "exercise-" id)
                 :label (str (when (= id selected-id) "> ") (derive/exercise-name exercise)
                             " — " (str/join ", " (map #(get derive/muscle-names % %) (:primary n)))
                             " — " (:intensity n))
                 :on-press [:catalog/select id]}]))

(defn- media-preview
  "Fixed 320x180 viewport (EXT-001/002: the box never resizes with the media). Images
  (an animated GIF included) use canonical :ui/image; video goes through the
  :fitness/media-player native contract."
  [exercise]
  (if-some [path (:media-path exercise)]
    (let [{:keys [width height]} derive/media-viewport]
      [:ui/column {:gap 4}
       [:ui/box {:test-id "media-preview" :style {:width width :height height :background (:paper palette)}}
        (case (derive/media-kind path)
          :image [:ui/image {:key :image :src {:file path} :fit :contain :style {:width width :height height}}]
          :video (native/view :fitness/media-player {:key :video :test-id "media-player"
                                                     :props (derive/media-props exercise)})
          [:ui/text {:key :unsupported :wrap? true :text "This media format is not supported."}])]
       (note path)])
    (note "No media for this exercise.")))

(defn- exercise-detail [db]
  (let [doc (:doc db)
        id (get-in db [:catalog :selected])
        exercise (derive/exercise-by-id doc id)]
    (if-not exercise
      [:ui/column {:gap 8 :style {:padding 12} :test-id "exercise-detail"}
       (h2 "Exercise")
       (note "Select an exercise to see its details.")]
      (let [n (c/normalize-exercise exercise)
            muscles (fn [ids] (if (seq ids) (str/join ", " (map #(get derive/muscle-names % %) ids)) "-"))]
        [:ui/scroll {:direction :vertical}
         [:ui/column {:gap 8 :style {:padding 12} :test-id "exercise-detail"}
          (h2 (derive/exercise-name exercise))
          [:ui/grid {:columns [:auto {:fr 1}] :gap 6}
           [:ui/text {:grid/row 0 :grid/col 0 :text "Primary"}]
           [:ui/text {:grid/row 0 :grid/col 1 :wrap? true :text (muscles (:primary n))}]
           [:ui/text {:grid/row 1 :grid/col 0 :text "Secondary"}]
           [:ui/text {:grid/row 1 :grid/col 1 :wrap? true :text (muscles (:secondary n))}]
           [:ui/text {:grid/row 2 :grid/col 0 :text "Equipment"}]
           [:ui/text {:grid/row 2 :grid/col 1 :wrap? true
                      :text (if (seq (:equipment n)) (str/join ", " (:equipment n)) "Not set")}]
           [:ui/text {:grid/row 3 :grid/col 0 :text "Intensity"}]
           [:ui/text {:grid/row 3 :grid/col 1 :text (str (:intensity n))}]
           [:ui/text {:grid/row 4 :grid/col 0 :text "MET"}]
           [:ui/text {:grid/row 4 :grid/col 1 :text (if (number? (:met n)) (derive/fmt-quantity (:met n)) "unknown (3.5 estimated)")}]]
          (media-preview exercise)
          [:ui/row {:gap 6}
           [:ui/button {:label "Add to plan" :test-id "add-to-plan" :on-press [:plan/add-exercise id]}]
           [:ui/button {:label "Import media" :test-id "import-media" :on-press [:media/pick id]}]]]]))))

(defn catalog-screen [db]
  (let [doc (:doc db)
        visible (derive/visible-exercises db)
        selected-id (get-in db [:catalog :selected])]
    [:ui/split-view {:ratio (get-in db [:ui :split] 0.55) :on-resize [:nav/split] :test-id "catalog-split"}
     [:ui/scroll {:direction :vertical}
      [:ui/column {:gap 10 :style {:padding 10}}
       [:ui/row {:gap 6 :align :center}
        [:ui/text-input {:test-id "catalog-search" :value (get-in db [:catalog :query] "")
                         :placeholder "Search exercises, muscles, equipment" :on-change [:catalog/query-change]}]
        [:ui/button {:label "Clear all" :test-id "catalog-clear" :on-press [:catalog/clear]}]]
       (filter-group db :muscles "Muscles" derive/muscle-options)
       (filter-group db :intensities "Intensity" derive/intensity-options)
       (filter-group db :equipment "Equipment" (derive/equipment-options doc))
       [:ui/text {:test-id "catalog-count"
                  :text (str (count visible) " of " (count (:exercises doc)) " exercises")}]
       (into [:ui/column {:gap 4 :test-id "exercise-list"}]
             (if (seq visible)
               (map #(exercise-row selected-id %) visible)
               [[:ui/text {:key ::empty :text "No exercise matches these filters."}]]))]]
     (exercise-detail db)]))

;; ---------------------------------------------------------------------------
;; Plan builder and active training (DOM-002..004)

(defn- item-text [doc item]
  (let [ex (derive/exercise-by-id doc (:exercise-id item))]
    (str (if ex (derive/exercise-name ex) (str "Missing exercise " (:exercise-id item)))
         " — " (derive/fmt-whole (/ (or (:duration-sec item) 0) 60.0)) " min"
         " · " (or (:reps item) 0) " reps"
         (when (:target-hr item) (str " · target HR " (:target-hr item)))
         (when (:scheduled-time item) (str " · at " (:scheduled-time item))))))

(defn- active-panel [doc]
  (when-some [active (:active doc)]
    (let [paused? (events/active-paused? active)
          interrupted? (and paused? (seq (:interrupted-at active)))]
      [:ui/box {:key ::active :test-id "active-training"
                :style {:padding 10 :background "#EDF4ED" :radius 8}}
       [:ui/column {:gap 6}
        [:ui/text {:test-id "active-status"
                   :text (str "Active: " (derive/exercise-name (:exercise active))
                              (cond interrupted? " (interrupted: the open interval was not counted; Resume to continue)"
                                    paused? " (paused)"
                                    :else " (running)")
                              " — " (count (:segments active)) " interval(s)")}]
        [:ui/row {:gap 6}
         (if paused?
           [:ui/button {:key :resume :label "Resume" :test-id "training-resume" :on-press [:training/resume]}]
           [:ui/button {:key :pause :label "Pause" :test-id "training-pause" :on-press [:training/pause]}])
         [:ui/button {:key :complete :label "Complete" :test-id "training-complete" :on-press [:training/complete]}]]]])))

(defn- plan-item-row [doc plan-id busy? item]
  (let [id (:id item)]
    [:ui/box {:key id :test-id (str "plan-item-" id)
              :style {:padding 8 :border {:width 1 :color (:line palette)} :radius 6}}
     [:ui/column {:gap 4}
      [:ui/text {:text (item-text doc item) :wrap? true}]
      [:ui/row {:gap 4}
       [:ui/button {:label "Up" :on-press [:plan/item-move plan-id id -1]}]
       [:ui/button {:label "Down" :on-press [:plan/item-move plan-id id 1]}]
       [:ui/button {:label "Copy" :test-id (str "copy-" id) :on-press [:plan/item-copy plan-id id]}]
       [:ui/button {:label "Remove" :on-press [:plan/item-remove plan-id id]}]
       [:ui/spacer]
       [:ui/button {:label "Start" :test-id (str "start-" id) :disabled? busy?
                    :on-press [:training/start plan-id id]}]
       [:ui/button {:label "Backfill" :test-id (str "backfill-" id) :on-press [:training/backfill plan-id id]}]]]]))

(defn plan-screen [db]
  (let [doc (:doc db)
        plans (:plans doc)
        plan-id (or (get-in db [:plan :selected]) (:id (first plans)))
        plan (first (filter #(= plan-id (:id %)) plans))
        busy? (some? (:active doc))]
    [:ui/scroll {:direction :vertical}
     [:ui/column {:gap 10 :style {:padding 10}}
      [:ui/row {:gap 6 :align :center}
       [:ui/text {:text "Plan"}]
       [:ui/select {:test-id "plan-select" :style {:width 320}
                    :options (mapv (fn [p] {:value (:id p) :label (str (:date p) " — " (:name p))}) plans)
                    :value plan-id :placeholder "No plans yet" :on-select [:plan/select]}]]
      (active-panel doc)
      (if plan
        (into [:ui/column {:gap 6 :test-id "plan-items"}]
              (if (seq (:items plan))
                (map #(plan-item-row doc plan-id busy? %) (:items plan))
                [[:ui/text {:key ::empty :text "This plan has no items. Add exercises from the catalog."}]]))
        (note "No plan selected. Add an exercise from the catalog to create one."))
      (note "Scheduled times are planning metadata; nothing starts automatically. Backfilled items are marked as estimated from the plan.")]]))

;; ---------------------------------------------------------------------------
;; Execution log and heart-rate import (DOM-005, DOM-006)

(defn- record-row [r]
  [:ui/box {:key (:id r) :test-id (str "record-" (:id r))
            :style {:padding 6 :border {:width 1 :color (:line palette)} :radius 6}}
   [:ui/text {:wrap? true
              :text (str (:date r) " · " (:name r)
                         " · " (derive/fmt-quantity (:duration-sec r)) " s (" (or (:duration-source r) "?") ")"
                         " · " (or (:reps r) 0) " reps"
                         " · " (if (number? (:kcal r))
                                 (str (derive/fmt-quantity (:kcal r)) " kcal")
                                 (str "kcal unknown (" (some-> (:kcal-reason r) name) ")"))
                         (when (pos? (or (:hr-coverage r) 0.0))
                           (str " · HR coverage " (derive/fmt-whole (* 100 (:hr-coverage r))) "%")))}]])

(defn- import-panel [db]
  (let [{:keys [status preview source offset-text error committed]} (:hr-import db)]
    (panel "Heart-rate import"
           [:ui/row {:gap 6 :align :center}
            [:ui/button {:label "Choose CSV/JSON file" :test-id "hr-pick" :on-press [:hr/pick]
                         :disabled? (contains? #{:picking :reading} status)}]
            [:ui/text {:text "UTC offset (minutes) for times without a zone"}]
            [:ui/text-input {:test-id "hr-offset" :style {:width 90} :value (or offset-text "")
                             :placeholder "e.g. 480" :on-change [:hr/offset-change]}]]
           (case status
             :reading (note "Reading file...")
             :failed (note (str "The file could not be read: " (or (:error error) error)))
             :committed (note (str "Imported " committed " samples."))
             :cancelled (note "Preview cancelled; stored samples are unchanged.")
             nil)
           (when (and (= status :preview) preview)
             (if-some [err (:error preview)]
               [:ui/column {:gap 4 :test-id "hr-preview"}
                (note (str "Cannot import " source ": " (:message err)))
                [:ui/button {:label "Close" :on-press [:hr/cancel]}]]
               [:ui/column {:gap 4 :test-id "hr-preview"}
                [:ui/text {:test-id "hr-counts"
                           :text (str (:valid preview) " valid · " (:invalid preview) " invalid · "
                                      (:duplicates preview) " duplicate(s) · " (:rows preview) " row(s) in " source)}]
                (note (str (:explicit-rows preview) " row(s) with an explicit zone, " (:epoch-rows preview)
                           " epoch, " (:offset-rows preview) " interpreted with the offset"
                           (when (some? (:offset-min preview)) (str " " (:offset-min preview) " min"))))
                (when (seq (:errors preview))
                  (note (str "Rejected rows: "
                             (str/join ", " (map #(str (:row %) " (" (name (:reason %)) ")") (take 8 (:errors preview))))
                             (when (> (count (:errors preview)) 8) ", ..."))))
                [:ui/row {:gap 6}
                 [:ui/button {:label "Import" :test-id "hr-confirm" :disabled? (zero? (:valid preview))
                              :on-press [:hr/confirm]}]
                 [:ui/button {:label "Cancel" :test-id "hr-cancel" :on-press [:hr/cancel]}]]])))))

(defn log-screen [db]
  (let [rows (derive/record-rows db)]
    [:ui/scroll {:direction :vertical}
     [:ui/column {:gap 10 :style {:padding 10}}
      (h2 "Completed training")
      (into [:ui/column {:gap 4 :test-id "log-records"}]
            (if (seq rows)
              (map record-row rows)
              [[:ui/text {:key ::empty :text "No completed training yet."}]]))
      (note (str (count (get-in db [:doc :heart-rate])) " heart-rate samples stored."))
      (import-panel db)]]))

;; ---------------------------------------------------------------------------
;; Anatomy & analytics (DOM-008, EXT-003..005)

(defn- volume-compare
  "Canonical per-muscle comparison (docs/NATIVE_CONTRACTS.md §5)."
  [db]
  (let [{:keys [comparison previous-range]} (derive/analysis db)
        {:keys [from to metric]} (:analytics db)
        rows (:muscles comparison)
        scale (double (reduce max 1.0 (mapcat (juxt :current :previous) rows)))
        bar (fn [v color] [:ui/box {:style {:width 12 :height (max 1 (long (* 90 (/ v scale)))) :background color :radius 3}}
                           [:ui/spacer]])
        change-text (fn [{:keys [percent delta]}]
                      (cond (some? percent) (str (if (neg? percent) "-" "+") (derive/fmt-whole (if (neg? percent) (- percent) percent)) "%")
                            (pos? delta) "New"
                            :else "-"))
        stat (fn [label k unit]
               (let [c (get comparison k)]
                 [:ui/box {:style {:padding 8 :border {:width 1 :color (:line palette)} :radius 6}}
                  [:ui/column {:gap 2}
                   (note label)
                   [:ui/text {:text (str (derive/fmt-quantity (:current c)) " " unit)
                              :style {:font-size 16 :font-weight :semibold}}]
                   (note (str (change-text c) " vs previous"))]]))]
    (panel (if (= "planned" (get-in db [:analytics :mode])) "Planned training comparison" "Completed training comparison")
           (note (str "Current " from " - " to " · previous " (:from previous-range) " - " (:to previous-range)))
           [:ui/row {:gap 8}
            (stat "Active minutes" :duration-min "min")
            (stat "Sets" :sets "sets")
            (stat "Training days" :training-days "days")]
           [:ui/scroll {:direction :horizontal :test-id "volume-compare"}
            (into [:ui/row {:gap 10 :align :end}]
                  (for [{:keys [id current previous]} rows]
                    [:ui/column {:key id :gap 2 :align :center :style {:width 64}}
                     [:ui/row {:gap 2 :align :end}
                      (bar previous (:previous palette))
                      (bar current (:teal palette))]
                     [:ui/text {:text (derive/fmt-quantity current) :style {:font-size 11}}]
                     [:ui/text {:text (get derive/muscle-names id id) :wrap? true :style {:font-size 10 :text-align :center}}]]))]
           (note (if (= metric "sets")
                   "Sets: primary muscles count 1, secondary 0.5 per set (comparison weights, not measurements)."
                   "Weighted minutes: primary 1.0 and secondary 0.5 per effective minute.")))))

(defn- body-panel [db]
  (let [props (derive/body-props db)
        selected (:selected props)
        sel (first (filter #(= selected (:id %)) (:regions props)))
        three-d? (= :three-d (:style props))]
    (panel "Muscle map"
           [:ui/row {:gap 8 :align :center}
            [:ui/select {:test-id "body-style" :style {:width 120}
                         :options [{:value :two-d :label "2D"} {:value :three-d :label "3D"}]
                         :value (:style props) :on-select [:body/style-change]}]
            [:ui/select {:test-id "muscle-select" :style {:width 220}
                         :options (into [{:value nil :label "No muscle selected"}]
                                        (map (fn [{:keys [id label]}] {:value id :label label}) derive/muscle-options))
                         :value selected :on-select [:body/select-muscle]}]
            [:ui/button {:label "Clear" :test-id "muscle-clear" :disabled? (nil? selected)
                         :on-press [:body/select-muscle nil]}]]
           (when three-d?
             (note "The 3D body view is not implemented on this host yet; the 2D map is shown."))
           (native/view :fitness/body-canvas {:key :body-canvas :test-id "body-map-canvas" :props props})
           [:ui/text {:test-id "muscle-selection"
                      :text (if sel
                              (str (:name sel) " — " (derive/fmt-quantity (:load sel)) " " (:unit props)
                                   " — relative load " (derive/fmt-whole (* 100 (:level sel))) "%")
                              "Tap a muscle or choose one above.")}])))

(defn- radar-panel [db]
  (let [props (derive/radar-props db)]
    (panel "Training distribution"
           (native/view :fitness/radar-canvas {:key :radar-canvas :test-id "radar-canvas" :props props})
           (into [:ui/grid {:columns [:auto {:fr 1} {:fr 1}] :gap 4 :test-id "radar-table"}
                  [:ui/text {:grid/row 0 :grid/col 0 :text "Region" :style {:font-weight :semibold}}]
                  [:ui/text {:grid/row 0 :grid/col 1 :text "Current" :style {:font-weight :semibold}}]
                  [:ui/text {:grid/row 0 :grid/col 2 :text "Previous" :style {:font-weight :semibold}}]]
                 (mapcat (fn [i {:keys [id label current previous]}]
                           [[:ui/text {:key [id 0] :grid/row (inc i) :grid/col 0 :text label}]
                            [:ui/text {:key [id 1] :grid/row (inc i) :grid/col 1 :text (derive/fmt-quantity current)}]
                            [:ui/text {:key [id 2] :grid/row (inc i) :grid/col 2 :text (derive/fmt-quantity previous)}]])
                         (range) (:axes props))))))

(defn- calorie-panel [db]
  (let [props (derive/calorie-props db)]
    (panel "Energy history (kcal)"
           (when (= "planned" (get-in db [:analytics :mode]))
             (note "Energy shows completed training only."))
           (if (:empty? props)
             (note "Complete training to view energy.")
             [:ui/scroll {:direction :horizontal}
              (native/view :fitness/calorie-canvas {:key :calorie-canvas :test-id "calorie-canvas" :props props})])
           (note (str (str/join " · " (map #(str (:label %) ": " (:value-text %)) (:days props)))
                      (when (:example-profile? props) " — estimated with the example profile"))))))

(defn analytics-screen [db]
  (let [{:keys [from to metric mode]} (:analytics db)
        err (:error (derive/analysis db))]
    [:ui/scroll {:direction :vertical}
     [:ui/column {:gap 10 :style {:padding 10}}
      [:ui/row {:gap 6 :align :center}
       [:ui/text {:test-id "analytics-range" :text (str from " - " to)}]
       [:ui/button {:label "Latest training week" :test-id "preset-latest" :on-press [:analytics/preset :latest-week]}]
       [:ui/button {:label "Last 7 days" :test-id "preset-7" :on-press [:analytics/preset :last-7]}]
       [:ui/button {:label "Last 30 days" :test-id "preset-30" :on-press [:analytics/preset :last-30]}]]
      [:ui/row {:gap 6 :align :center}
       [:ui/select {:test-id "analytics-mode" :style {:width 160}
                    :options [{:value "actual" :label "Completed"} {:value "planned" :label "Planned"}]
                    :value mode :on-select [:analytics/mode]}]
       [:ui/select {:test-id "analytics-metric" :style {:width 200}
                    :options [{:value "weighted-minutes" :label "Weighted minutes"} {:value "sets" :label "Sets"}]
                    :value metric :on-select [:analytics/metric]}]]
      (when err (note (str "Invalid range: " err)))
      [:ui/row {:gap 10 :align :start}
       (body-panel db)
       (radar-panel db)]
      (volume-compare db)
      (calorie-panel db)]]))

;; ---------------------------------------------------------------------------
;; Calendar (DOM-009)

(defn- day-details [db]
  (let [date (get-in db [:calendar :selected])
        doc (:doc db)
        plans (filter #(= date (:date %)) (:plans doc))
        done (set (map (juxt :plan-id :item-id) (filter :completed-at (:records doc))))]
    (if-not (dates/valid? date)
      (note "Select a date.")
      [:ui/column {:gap 6 :test-id "calendar-details"}
       (h2 (or (dates/long-title date) date))
       (if (seq plans)
         (into [:ui/column {:gap 4}]
               (for [p plans]
                 [:ui/column {:key (:id p) :gap 2}
                  [:ui/text {:text (:name p) :style {:font-weight :semibold}}]
                  (into [:ui/column {:gap 2}]
                        (for [it (:items p)]
                          [:ui/text {:key (:id it)
                                     :text (str (if (contains? done [(:id p) (:id it)]) "done · " "open · ")
                                                (item-text doc it))}]))]))
         (note "No plans on this date."))
       [:ui/button {:label "Day analysis" :test-id "day-analysis" :on-press [:analytics/open-date date]}]])))

(defn calendar-screen [db]
  (let [cal (derive/calendar db)
        month (:month cal)]
    [:ui/scroll {:direction :vertical}
     [:ui/column {:gap 10 :style {:padding 10}}
      [comps/month-grid {:calendar cal
                         :title (dates/month-title month)
                         :selected (get-in db [:calendar :selected])
                         :today (:today db)
                         :kcal (derive/calendar-kcal db cal)
                         :on-select [:calendar/select-date]
                         :on-prev [:calendar/change-month (dates/plus-months month -1)]
                         :on-next [:calendar/change-month (dates/plus-months month 1)]
                         :on-today [:calendar/go-today]
                         :test-id "calendar"}]
      (day-details db)]]))

;; ---------------------------------------------------------------------------
;; Root

(defn- status-line [db]
  (let [{:keys [save message]} (:status db)]
    (str (case save :saving "Saving..." :saved "Saved" :failed "Save failed — retry with Save" :unsaved "Unsaved changes" "")
         (when (and save message) " · ")
         message)))

(defn root
  "db -> hiccup for the whole app."
  [db]
  [:ui/column {:gap 4 :test-id "fitness-root"}
   [:ui/tabs {:selected (get-in db [:ui :tab] :catalog) :on-select [:nav/tab] :test-id "fitness-tabs"}
    [:ui/tab {:id :catalog :label "Catalog" :test-id "tab-catalog"} (catalog-screen db)]
    [:ui/tab {:id :plan :label "Plan Builder" :test-id "tab-plan"} (plan-screen db)]
    [:ui/tab {:id :log :label "Execution Log" :test-id "tab-log"} (log-screen db)]
    [:ui/tab {:id :analytics :label "Anatomy & Analytics" :test-id "tab-analytics"} (analytics-screen db)]
    [:ui/tab {:id :calendar :label "Calendar" :test-id "tab-calendar"} (calendar-screen db)]]
   [:ui/row {:gap 8 :align :center :style {:padding [2 8 4 8]}}
    [:ui/text {:test-id "status" :text (status-line db) :style {:font-size 12 :color (:muted palette)}}]
    [:ui/spacer]
    [:ui/button {:label "Save" :test-id "save" :on-press [:doc/save]}]]])
