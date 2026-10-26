(ns clogem.fitness.domain
  "Portable fitness rules. IDs, local dates and epoch milliseconds come from callers."
  (:require [clojure.string :as str]))

(def muscle-dictionary
  [{:id "pectoralis-major" :en "Pectoralis major" :zh "Pectoralis major"}
   {:id "latissimus-dorsi" :en "Latissimus dorsi" :zh "Latissimus dorsi"}
   {:id "trapezius" :en "Trapezius" :zh "Trapezius"}
   {:id "rhomboids" :en "Rhomboids" :zh "Rhomboids"}
   {:id "anterior-deltoid" :en "Anterior deltoid" :zh "Anterior deltoid"}
   {:id "lateral-deltoid" :en "Lateral deltoid" :zh "Lateral deltoid"}
   {:id "posterior-deltoid" :en "Posterior deltoid" :zh "Posterior deltoid"}
   {:id "biceps-brachii" :en "Biceps brachii" :zh "Biceps brachii"}
   {:id "triceps-brachii" :en "Triceps brachii" :zh "Triceps brachii"}
   {:id "rectus-abdominis" :en "Rectus abdominis" :zh "Rectus abdominis"}
   {:id "obliques" :en "Obliques" :zh "Obliques"}
   {:id "erector-spinae" :en "Erector spinae" :zh "Erector spinae"}
   {:id "gluteus-maximus" :en "Gluteus maximus" :zh "Gluteus maximus"}
   {:id "gluteus-medius" :en "Gluteus medius" :zh "Gluteus medius"}
   {:id "quadriceps" :en "Quadriceps" :zh "Quadriceps"}
   {:id "rectus-femoris" :en "Rectus femoris" :zh "Rectus femoris"}
   {:id "hamstrings" :en "Hamstrings" :zh "Hamstrings"}
   {:id "iliopsoas" :en "Iliopsoas" :zh "Iliopsoas"}
   {:id "gastrocnemius" :en "Gastrocnemius" :zh "Gastrocnemius"}
   {:id "soleus" :en "Soleus" :zh "Soleus"}])

(def muscle-ids (set (map :id muscle-dictionary)))
(def default-profile
  {:weight-kg 70 :resting-hr 60 :max-hr 190 :use-hr? false :example? true})

(defn- finite-number? [n]
  (and (number? n) (< -1.0e15 n 1.0e15)))
(defn- positive-number? [n] (and (finite-number? n) (pos? n)))
(defn- clamp [low high n] (max low (min high n)))
(defn- valid-id? [id] (and (string? id) (not (str/blank? id))))
(defn- completed? [record]
  (and (finite-number? (:started-at record))
       (finite-number? (:completed-at record))
       (<= (:started-at record) (:completed-at record))))
(defn- in-date-range? [date from-date to-date]
  (and (string? date)
       (or (nil? from-date) (not (neg? (compare date from-date))))
       (or (nil? to-date) (not (pos? (compare date to-date))))))

(defn exercise-muscle-weights [exercise]
  (merge (into {} (map (fn [id] [id 0.5]) (filter valid-id? (:secondary exercise))))
         (into {} (map (fn [id] [id 1.0]) (filter valid-id? (:primary exercise))))))

(defn normalize-segments
  "Merge closed, positive [start,end) intervals. Invalid/open intervals are omitted."
  [segments]
  (reduce (fn [out [start end]]
            (if-let [[a b] (peek out)]
              (if (<= start b)
                (conj (pop out) [a (max b end)])
                (conj out [start end]))
              [[start end]]))
          [] (sort-by first
                      (filter (fn [segment]
                                (and (sequential? segment) (= 2 (count segment))
                                     (finite-number? (first segment))
                                     (finite-number? (second segment))
                                     (< (first segment) (second segment))))
                              segments))))

(defn record-segments [record]
  (if-not (completed? record)
    []
    (let [start (:started-at record) end (:completed-at record)
          segments (if (contains? record :segments) (:segments record) [[start end]])]
      (normalize-segments
       (map (fn [[a b]] [(max start a) (min end b)])
            (normalize-segments segments))))))

(defn- duration-ms [segments] (reduce + 0 (map (fn [[a b]] (- b a)) segments)))
(defn record-duration-sec [record] (/ (duration-ms (record-segments record)) 1000.0))

(defn validate-hr-samples [samples]
  (let [result
        (reduce (fn [{:keys [by-time] :as result} [index sample]]
                  (let [{:keys [timestamp bpm end-at]} sample
                        reason (cond
                                 (not (finite-number? timestamp)) :invalid-timestamp
                                 (not (and (finite-number? bpm) (<= 25 bpm 250))) :invalid-bpm
                                 (and (some? end-at)
                                      (not (and (finite-number? end-at) (< timestamp end-at)))) :invalid-end
                                 :else nil)]
                    (if reason
                      (update result :errors conj {:index index :reason reason})
                      (cond-> (assoc-in result [:by-time timestamp]
                                        (cond-> {:timestamp timestamp :bpm bpm}
                                          (some? end-at) (assoc :end-at end-at)))
                        (contains? by-time timestamp) (update :duplicates inc)))))
                {:by-time {} :errors [] :duplicates 0}
                (map-indexed vector samples))]
    {:samples (vec (sort-by :timestamp (vals (:by-time result))))
     :errors (:errors result) :duplicates (:duplicates result)}))

(defn- hr-intervals [samples opts]
  (let [valid (:samples (validate-hr-samples samples))
        max-gap (if (positive-number? (:max-gap-ms opts)) (:max-gap-ms opts) 30000)
        hold (min max-gap (if (positive-number? (:hold-ms opts)) (:hold-ms opts) 5000))]
    (mapv (fn [index {:keys [timestamp bpm end-at]}]
            (let [next-time (:timestamp (get valid (inc index)))
                  implicit-end (if (and next-time (<= (- next-time timestamp) max-gap))
                                 next-time (+ timestamp hold))
                  end (or end-at implicit-end)]
              {:start timestamp :end (if next-time (min next-time end) end) :bpm bpm}))
          (range (count valid)) valid)))

(defn prepare-heart-rate
  "Validate/sort once for a whole view. The immutable index can be reused until samples change."
  ([samples] (prepare-heart-rate samples {}))
  ([samples opts] {:intervals (hr-intervals samples opts)}))

(defn- intersect-index [intervals a b]
  ;; Intervals are disjoint and ordered; find the first end after this segment.
  (let [n (count intervals)
        first-index (loop [lo 0 hi n]
                      (if (>= lo hi) lo
                        (let [mid (quot (+ lo hi) 2)]
                          (if (<= (:end (nth intervals mid)) a)
                            (recur (inc mid) hi) (recur lo mid)))))]
    (loop [i first-index out []]
      (if (>= i n) out
        (let [{:keys [start end bpm]} (nth intervals i)]
          (if (>= start b) out
            (recur (inc i) (conj out {:start (max a start) :end (min b end) :bpm bpm}))))))))

(defn match-heart-rate
  ([record samples] (match-heart-rate record samples {}))
  ([record samples opts]
   (let [segments (record-segments record)
         active (duration-ms segments)
         index (or (:heart-rate-index opts) (prepare-heart-rate samples opts))
         intervals (vec (mapcat (fn [[a b]] (intersect-index (:intervals index) a b)) segments))
         covered (reduce + 0 (map (fn [{:keys [start end]}] (- end start)) intervals))
         weighted (reduce + 0 (map (fn [{:keys [start end bpm]}] (* (- end start) bpm)) intervals))]
     {:average-bpm (when (pos? covered) (/ weighted (double covered)))
      :covered-ms covered :active-ms active
      :coverage (if (pos? active) (/ covered (double active)) 0.0)
      :intervals intervals})))

(defn record-calories
  ([record profile samples] (record-calories record profile samples {}))
  ([record profile samples opts]
   (let [minutes (/ (record-duration-sec record) 60.0)
         weight (:weight-kg profile)
         met (:met (:exercise record))
         estimated-met? (not (positive-number? met))
         met (if estimated-met? 3.5 met)
         hr (match-heart-rate record samples opts)
         covered? (pos? (:covered-ms hr))
         resting (:resting-hr profile) maximum (:max-hr profile)
         adjust-hr? (and covered? (true? (:use-hr? profile))
                         (positive-number? resting) (positive-number? maximum)
                         (< resting maximum))
         ;; A bounded intensity heuristic around 60% HR reserve; not a measured calorie value.
         hr-factor (if adjust-hr?
                     (clamp 0.75 1.25
                            (+ 1.0 (* 0.5 (- (/ (- (:average-bpm hr) resting)
                                                (- maximum resting)) 0.6))))
                     1.0)
         rpm (:reference-rpm (:exercise record))
         frequency? (and (not covered?) (pos? minutes)
                         (positive-number? (:reps record)) (positive-number? rpm))
         frequency-factor (if frequency?
                            (clamp 0.75 1.25 (/ (/ (:reps record) minutes) rpm)) 1.0)
         valid-weight? (positive-number? weight)
         baseline (cond
                    (not (pos? minutes)) 0.0
                    valid-weight? (* met 3.5 (/ weight 200.0) minutes)
                    :else nil)
         factor (if adjust-hr? (+ 1.0 (* (:coverage hr) (- hr-factor 1.0))) frequency-factor)]
     {:kcal (when (some? baseline) (* baseline factor))
      :baseline-kcal baseline :duration-min minutes
      :weight-kg (when valid-weight? weight)
      :example-profile? (true? (:example? profile)) :estimated-met? estimated-met?
      :method (cond adjust-hr? :hr-reserve-heuristic frequency? :met-frequency :else :met)
      :hr-coverage (:coverage hr) :average-bpm (:average-bpm hr)
      :hr-factor hr-factor :frequency-factor frequency-factor
      :reason (cond (not (completed? record)) :incomplete
                    (not (pos? minutes)) :zero-duration
                    (not valid-weight?) :missing-weight
                    :else nil)})))

(defn- add-load [load exercise minutes]
  (reduce (fn [result [muscle weight]]
            (update result muscle (fnil + 0.0) (* minutes weight)))
          load (exercise-muscle-weights exercise)))

(defn planned-load [plans exercises from-date to-date]
  (let [by-id (if (map? exercises) exercises (into {} (map (juxt :id identity) exercises)))]
    (reduce (fn [load plan]
              (reduce (fn [result {:keys [exercise-id duration-sec]}]
                        (if (and (get by-id exercise-id) (positive-number? duration-sec))
                          (add-load result (get by-id exercise-id) (/ duration-sec 60.0))
                          result))
                      load (:items plan)))
            {} (filter #(in-date-range? (:date %) from-date to-date) plans))))

(defn- unique-completed [records from-date to-date]
  (:out
   (reduce (fn [{:keys [seen] :as result} record]
             (if (and (valid-id? (:id record)) (contains? seen (:id record)))
               result
               (-> result (update :seen conj (:id record)) (update :out conj record))))
           {:seen #{} :out []}
           (filter #(and (completed? %) (in-date-range? (:date %) from-date to-date)) records))))

(defn actual-load [records from-date to-date]
  (reduce (fn [load record]
            (let [minutes (/ (record-duration-sec record) 60.0)]
              (if (pos? minutes) (add-load load (:exercise record) minutes) load)))
          {} (unique-completed records from-date to-date)))

(defn- subtract-interval [segments [cut-start cut-end]]
  (vec (mapcat (fn [[a b]]
                 (if (or (<= cut-end a) (>= cut-start b))
                   [[a b]]
                   (cond-> [] (< a cut-start) (conj [a cut-start])
                              (< cut-end b) (conj [cut-end b])))) segments)))

(defn daily-calories
  ([records profile samples from-date to-date]
   (daily-calories records profile samples from-date to-date {}))
  ([records profile samples from-date to-date opts]
   (let [opts (if (:heart-rate-index opts) opts
                (assoc opts :heart-rate-index (prepare-heart-rate samples opts)))]
    (:days
    (reduce
     (fn [{:keys [occupied] :as state} record]
       ;; Two overlapping completed logs must not charge the same wall-clock interval twice.
       (let [segments (record-segments record)
             fresh (reduce subtract-interval segments occupied)
             original-ms (duration-ms segments)
             fresh-ms (duration-ms fresh)
             ratio (if (pos? original-ms) (/ fresh-ms (double original-ms)) 0.0)
             adjusted (cond-> (assoc record :segments fresh)
                        (finite-number? (:reps record)) (update :reps * ratio))
             calories (record-calories adjusted profile samples opts)
             date (:date record)
             base {:kcal 0.0 :baseline-kcal 0.0 :duration-min 0.0 :record-count 0
                   :example-profile? (true? (:example? profile)) :unknown-count 0}]
         (-> state
             (assoc :occupied (normalize-segments (concat occupied segments)))
             (update-in [:days date]
                        (fn [day]
                          (let [day (or day base)]
                            (-> day
                                (update :kcal (fn [n] (when (and (some? n) (some? (:kcal calories)))
                                                       (+ n (:kcal calories)))))
                                (update :baseline-kcal (fn [n] (when (and (some? n) (some? (:baseline-kcal calories)))
                                                                (+ n (:baseline-kcal calories)))))
                                (update :duration-min + (:duration-min calories))
                                (update :record-count + (if (pos? fresh-ms) 1 0))
                                (update :unknown-count + (if (nil? (:kcal calories)) 1 0)))))))))
     {:days (sorted-map) :occupied []}
     (sort-by :started-at (unique-completed records from-date to-date)))))))

(defn reorder-item [plan item-id target-index]
  (let [items (vec (:items plan))
        item (first (filter #(= item-id (:id %)) items))]
    (if-not item plan
      (let [others (vec (remove #(= item-id (:id %)) items))
            index (int (clamp 0 (count others) (if (finite-number? target-index) target-index 0)))]
        (assoc plan :items (into (conj (subvec others 0 index) item) (subvec others index)))))))

(defn duplicate-item [plan item-id new-id]
  (when-not (valid-id? new-id) (throw (ex-info "A new item ID is required." {:reason :invalid-id})))
  (when (some #(= new-id (:id %)) (:items plan))
    (throw (ex-info "Plan item IDs must be unique." {:reason :duplicate-id})))
  (assoc plan :items
         (vec (mapcat (fn [item] (if (= item-id (:id item)) [item (assoc item :id new-id)] [item]))
                      (:items plan)))))

(defn remove-item [plan item-id]
  (assoc plan :items (vec (remove #(= item-id (:id %)) (:items plan)))))

(defn finish-record
  ([record now-ms] (finish-record record now-ms {}))
  ([record now-ms updates]
   (if (completed? record) record
     (do
       (when-not (and (finite-number? now-ms) (finite-number? (:started-at record))
                      (<= (:started-at record) now-ms))
         (throw (ex-info "Completion must follow the supplied start time." {:reason :invalid-completion})))
       (let [segments (if (contains? record :segments)
                        (mapv (fn [[start end]] [start (or end now-ms)]) (:segments record))
                        [[(:started-at record) now-ms]])
             finished (merge record (select-keys updates [:reps :target-hr :notes :duration-source])
                             {:completed-at now-ms :segments segments})]
         (assoc finished :segments (record-segments finished)))))))
