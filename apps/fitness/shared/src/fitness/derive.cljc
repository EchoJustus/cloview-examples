(ns fitness.derive
  "Derived data and native-view props, computed in shared code (docs/NATIVE_CONTRACTS.md
  §1.5): hosts only map props to pixels.

  Expensive derivations are memoized on the db slices they read (`memo-last`), never on
  the whole db, so a selection, a tab switch or a style change recomputes nothing
  (benchmark ARCH-004). `calls` counts the real computations for tests and evidence.

  Text that a native draws arrives pre-formatted here with integer arithmetic only, the
  same on every host: on ClojureCLR 1.12.6 `str` of a double drops a zero fraction
  (\"20\" for 20.0) and .NET number formatting (`format`, `.ToString`) follows the current
  culture (\"70,5\" in de-DE)."
  (:require [clojure.string :as str]
            [clogem.fitness.analytics :as a]
            [clogem.fitness.catalog :as c]
            [clogem.fitness.domain :as d]
            [cloview.core.memo :as memo]
            [cloview.core.order :as order]
            [cloview.core.text :as text]
            [fitness.dates :as dates]))

;; ---------------------------------------------------------------------------
;; Culture-free number text

(defn- round-half-up [x] (long (+ x 0.5)))

(defn fmt-whole
  "Rounded integer text (\"12\", \"-3\")."
  [x]
  (let [x (double x)]
    (if (neg? x) (str "-" (round-half-up (- x))) (str (round-half-up x)))))

(defn fmt-quantity
  "Whole numbers as integers, anything else with one decimal: 2.0 -> \"2\", 4.9 -> \"4.9\"."
  [x]
  (let [x (double x)
        neg? (neg? x)
        tenths (round-half-up (* 10.0 (if neg? (- x) x)))
        whole (quot tenths 10)
        frac (rem tenths 10)]
    (str (when (and neg? (pos? tenths)) "-") whole (when (pos? frac) (str "." frac)))))

;; ---------------------------------------------------------------------------
;; Counters (ARCH-004 evidence)

(defonce calls (atom {}))

(defn- counted [k f]
  (memo/memo-last (fn [& args]
                    (swap! calls update k (fnil inc 0))
                    (apply f args))))

;; ---------------------------------------------------------------------------
;; Catalog

(def muscle-names (into {} (map (juxt :id :en)) d/muscle-dictionary))

(def muscle-options (mapv (fn [{:keys [id en]}] {:id id :label en}) d/muscle-dictionary))

(def intensity-options (mapv (fn [{:keys [id label]}] {:id id :label label}) c/intensity-options))

(def unset-tag "Unset")

(def ^:private equipment*
  (counted :equipment
           (fn [exercises]
             (let [tags (c/available-equipment exercises)
                   assigned (order/sort-strings (remove #{unset-tag} tags))]
               ;; ordinal order on every host (the core's `sort` is culture-sensitive on
               ;; ClojureCLR 1.12); the filter-only "Unset" option stays last
               (cond-> (mapv (fn [t] {:id t :label t}) assigned)
                 (some #{unset-tag} tags) (conj {:id unset-tag :label "Not set"}))))))

(defn equipment-options [doc] (equipment* (:exercises doc)))

(defn effective-filters
  "Catalog filters with obsolete equipment selections dropped (a tag that is no longer
  assigned to any exercise disappears from the filter)."
  [db]
  (let [f (get-in db [:catalog :filters])
        available (set (map :id (equipment-options (:doc db))))]
    {:query (get-in db [:catalog :query] "")
     :muscles (set (:muscles f))
     :intensities (set (:intensities f))
     :equipment (into #{} (filter available) (:equipment f))}))

(def ^:private visible* (counted :catalog (fn [exercises filters] (c/filter-exercises exercises filters))))

(defn visible-exercises [db] (visible* (get-in db [:doc :exercises]) (effective-filters db)))

(defn exercise-name [exercise]
  (let [n (:name exercise)]
    (str (cond (map? n) (or (:en n) (get n "en") (:id exercise))
               (string? n) n
               :else (:id exercise)))))

(defn exercise-by-id [doc id] (first (filter #(= id (:id %)) (:exercises doc))))

;; ---------------------------------------------------------------------------
;; Analytics

(defn analytics-opts [db]
  (select-keys (:analytics db) [:from :to :metric :mode]))

(def ^:private analysis*
  (counted :analysis
           (fn [records plans exercises opts]
             (let [doc {:records records :plans plans :exercises exercises}
                   previous-range (a/adjacent-range (:from opts) (:to opts))
                   current (a/summary doc opts)
                   previous (a/summary doc (merge opts previous-range))]
               {:current current :previous previous :previous-range previous-range
                :comparison (a/comparison current previous)}))))

(defn analysis
  "{:current :previous :previous-range :comparison} for the analytics range, or
  {:error msg} when the range is invalid."
  [db]
  (let [doc (:doc db)]
    (try
      (analysis* (:records doc) (:plans doc) (:exercises doc) (analytics-opts db))
      (catch #?(:cljd Object :clj Exception :cljr System.Exception) e
        {:error #?(:cljd (str e) :default (or (ex-message e) (str e)))}))))

(def ^:private calendar*
  (counted :calendar
           (fn [records plans exercises month]
             (a/calendar {:records records :plans plans :exercises exercises} month))))

(defn calendar [db]
  (let [doc (:doc db)]
    (calendar* (:records doc) (:plans doc) (:exercises doc) (get-in db [:calendar :month]))))

(def ^:private hr-index* (counted :hr-index (fn [samples] (d/prepare-heart-rate samples))))

(defn- daily-calories-fn [k]
  (counted k (fn [records profile samples from to]
               (d/daily-calories records profile samples from to
                                 {:heart-rate-index (hr-index* samples)}))))

(def ^:private range-calories* (daily-calories-fn :range-calories))
(def ^:private calendar-calories* (daily-calories-fn :calendar-calories))

(defn day-energy
  "{:kcal x-or-nil :status :none|:known|:unknown} for one day of daily-calories output."
  [day]
  (cond
    (or (nil? day) (zero? (:record-count day 0))) {:kcal 0.0 :status :none}
    (nil? (:kcal day)) {:kcal nil :status :unknown}
    :else {:kcal (double (:kcal day)) :status :known}))

(defn calendar-kcal
  "{date kcal-or-nil} for the dates of the visible calendar that have activity."
  [db cal]
  (let [dated (keep :date (:cells cal))
        doc (:doc db)]
    (if (empty? dated)
      {}
      (let [days (calendar-calories* (:records doc) (:profile doc) (:heart-rate doc)
                                     (first dated) (last dated))]
        (into {} (map (fn [[date day]] [date (:kcal (day-energy day))])) days)))))

;; ---------------------------------------------------------------------------
;; Native props: body map

(defn unit-of [metric] (if (= metric "sets") "sets" "min"))

(defn body-props
  "Props of :fitness/body-canvas (docs/NATIVE_CONTRACTS.md §3)."
  [db]
  (let [{:keys [current]} (analysis db)
        {:keys [metric mode body-style selected-muscle]} (:analytics db)
        loads (:muscles current)
        load-of (fn [id] (let [l (get loads id 0.0)] (if (and (number? l) (pos? l)) (double l) 0.0)))
        scale-max (double (reduce max 1.0 (map load-of (keys muscle-names))))
        regions (mapv (fn [{:keys [id en]}]
                        (let [l (load-of id)] {:id id :name en :load l :level (/ l scale-max)}))
                      d/muscle-dictionary)
        selected (when (contains? muscle-names selected-muscle) selected-muscle)
        sel (first (filter #(= selected (:id %)) regions))]
    {:size {:width 440 :height 340}
     :style (or body-style :two-d)
     :selected selected
     :metric metric :mode mode :unit (unit-of metric)
     :scale-max scale-max
     :regions regions
     :labels {:front "Front" :back "Back"}
     :a11y-label (str "Front and back muscle map. "
                      (if sel
                        (str "Selected: " (:name sel) ", " (fmt-quantity (:load sel)) " " (unit-of metric)
                             ", relative load " (fmt-whole (* 100.0 (:level sel))) "%.")
                        "Select a region."))
     :events {:body/select-muscle [:body/select-muscle]}}))

;; ---------------------------------------------------------------------------
;; Native props: radar (six regions)

(def radar-axes
  "Muscle -> axis grouping (PROPOSED: the package fixes only the axis order and that axis
  values are sums; trapezius, erector-spinae and iliopsoas are unconfirmed)."
  [{:id :chest :label "Chest" :muscles ["pectoralis-major"]}
   {:id :back :label "Back" :muscles ["latissimus-dorsi" "trapezius" "rhomboids" "erector-spinae"]}
   {:id :shoulders :label "Shoulders" :muscles ["anterior-deltoid" "lateral-deltoid" "posterior-deltoid"]}
   {:id :arms :label "Arms" :muscles ["biceps-brachii" "triceps-brachii"]}
   {:id :core :label "Core" :muscles ["rectus-abdominis" "obliques"]}
   {:id :lower-body :label "Lower body"
    :muscles ["gluteus-maximus" "gluteus-medius" "quadriceps" "rectus-femoris" "hamstrings"
              "iliopsoas" "gastrocnemius" "soleus"]}])

(defn radar-props
  "Props of :fitness/radar-canvas (docs/NATIVE_CONTRACTS.md §4)."
  [db]
  (let [{:keys [current previous]} (analysis db)
        metric (get-in db [:analytics :metric])
        sum (fn [summary muscles] (double (reduce + 0.0 (map #(get-in summary [:muscles %] 0.0) muscles))))
        raw (mapv (fn [{:keys [id label muscles]}]
                    {:id id :label label :current (sum current muscles) :previous (sum previous muscles)})
                  radar-axes)
        scale-max (double (reduce max 1.0 (mapcat (juxt :current :previous) raw)))
        axes (mapv #(assoc % :current-level (/ (:current %) scale-max)
                             :previous-level (/ (:previous %) scale-max))
                   raw)]
    {:size {:width 300 :height 260}
     :metric metric :unit (unit-of metric) :series [:current :previous]
     :scale-max scale-max
     :empty? (every? #(zero? (:current %)) axes)
     :axes axes
     :a11y-label (str "Training distribution, relative load in " (unit-of metric) ": "
                      (str/join ", " (map #(str (:label %) " " (fmt-quantity (:current %))
                                                " (previous " (fmt-quantity (:previous %)) ")")
                                          axes)))}))

;; ---------------------------------------------------------------------------
;; Native props: daily calories

(defn calorie-props
  "Props of :fitness/calorie-canvas (docs/NATIVE_CONTRACTS.md §6): one entry per date of
  the analytics range; energy is always completed actual activity."
  [db]
  (let [{:keys [from to]} (:analytics db)
        doc (:doc db)
        dates (dates/date-seq from to)
        days (if (seq dates)
               (range-calories* (:records doc) (:profile doc) (:heart-rate doc) from to)
               {})
        entries (mapv (fn [date]
                        (let [{:keys [kcal status]} (day-energy (get days date))]
                          {:date date :label (subs date 5) :kcal kcal :status status
                           :value-text (if (= status :unknown) "?" (fmt-whole (or kcal 0.0)))}))
                      dates)
        known (keep #(when (= :known (:status %)) (:kcal %)) entries)]
    {:size {:width (max 320 (+ 40 (* 46 (count entries)))) :height 200}
     :unit "kcal"
     :scale-max (double (reduce max 1.0 known))
     :highlight to
     :empty? (every? #(= :none (:status %)) entries)
     :example-profile? (true? (get-in doc [:profile :example?]))
     :days entries
     :a11y-label (str "Energy history, estimated kcal per day: "
                      (str/join ", " (map #(str (:date %) " "
                                                (if (= :unknown (:status %)) "unknown" (str (:value-text %) " kcal")))
                                          entries)))}))

;; ---------------------------------------------------------------------------
;; Media preview (docs/NATIVE_CONTRACTS.md §8)

(def image-extensions #{"gif" "png" "jpg" "jpeg" "webp" "bmp"})
(def video-extensions #{"mp4" "m4v" "mov" "webm"})

(defn media-kind
  "Lower-case extension class of a media path: :image (GIF animates), :video or nil."
  [path]
  (let [p (str path)
        i (str/last-index-of p ".")
        ext (when i (text/ascii-lower (subs p (inc i))))]
    (cond (contains? image-extensions ext) :image
          (contains? video-extensions ext) :video
          :else nil)))

(def media-viewport {:width 320 :height 180})

(defn media-props
  "Props of :fitness/media-player for a video file (no host renders it in Stage 2-2; the
  view then shows its \"not available on this host yet\" placeholder)."
  [exercise]
  (let [path (:media-path exercise)]
    {:source {:kind :mp4 :path path}
     :viewport media-viewport
     :fit :contain
     :autoplay? true :loop? true :muted? true :controls? true
     :labels {:empty "No media" :loading "Loading media" :missing "Media file is unavailable"
              :unsupported "This format is not supported on this device"
              :decode "The media file could not be decoded"}
     :a11y-label (str (exercise-name exercise) " demonstration video")
     :events {:media/error [:media/error (:id exercise)]}}))

;; ---------------------------------------------------------------------------
;; Records

(defn record-rows
  "Execution-log rows for completed records, newest first."
  [db]
  (let [doc (:doc db)
        idx (hr-index* (:heart-rate doc))]
    (->> (:records doc)
         (filter :completed-at)
         (order/stable-sort-by :started-at)
         reverse
         (mapv (fn [r]
                 (let [cal (d/record-calories r (:profile doc) (:heart-rate doc) {:heart-rate-index idx})]
                   {:id (:id r)
                    :date (:date r)
                    :name (exercise-name (:exercise r))
                    :duration-sec (d/record-duration-sec r)
                    :duration-source (:duration-source r)
                    :reps (:reps r)
                    :kcal (:kcal cal)
                    :kcal-reason (:reason cal)
                    :hr-coverage (:hr-coverage cal)}))))))
