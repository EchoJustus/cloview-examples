(ns clogem.fitness.analytics
  "Pure local-calendar analytics; JSON-ready output, no clock or timezone I/O."
  (:require [clogem.fitness.domain :as domain]))

(defn- digits [s]
  (reduce (fn [n i] (+ (* n 10) (get {"0" 0 "1" 1 "2" 2 "3" 3 "4" 4
                                      "5" 5 "6" 6 "7" 7 "8" 8 "9" 9}
                                     (subs s i (inc i)) 0))) 0 (range (count s))))
(defn- leap? [y] (and (zero? (mod y 4)) (or (not (zero? (mod y 100))) (zero? (mod y 400)))))
(defn- month-days [y m] (get [0 31 (if (leap? y) 29 28) 31 30 31 30 31 31 30 31 30 31] m 0))
(defn- parts [date]
  (when (and (string? date) (re-matches #"\d{4}-\d{2}-\d{2}" date))
    (let [y (digits (subs date 0 4)) m (digits (subs date 5 7)) d (digits (subs date 8 10))]
      (when (and (<= 1 y 9999) (<= 1 m 12) (<= 1 d (month-days y m))) [y m d]))))
(defn- pad [n width] (let [s (str n)] (str (apply str (repeat (max 0 (- width (count s))) "0")) s)))
(defn- date-str [y m d] (str (pad y 4) "-" (pad m 2) "-" (pad d 2)))
(defn- ordinal [date]
  (when-let [[y m d] (parts date)]
    (let [p (dec y)] (+ (* 365 p) (quot p 4) (- (quot p 100)) (quot p 400)
                         (reduce + 0 (map #(month-days y %) (range 1 m))) (dec d)))))
(defn- ordinal-date [n]
  (when (or (neg? n) (> n (ordinal "9999-12-31")))
    (throw (ex-info "Date is outside the supported calendar." {:ordinal n})))
  (let [y (loop [y (inc (quot n 366))]
            (if (or (= y 9999) (> (ordinal (date-str (inc y) 1 1)) n)) y (recur (inc y))))
        remaining (- n (ordinal (date-str y 1 1)))]
    (loop [m 1 d remaining]
      (if (< d (month-days y m)) (date-str y m (inc d))
          (recur (inc m) (- d (month-days y m)))))))

(defn adjacent-range
  "Inclusive previous range containing the same number of local calendar days."
  [from to]
  (let [a (ordinal from) b (ordinal to)]
    (when-not (and a b (<= a b))
      (throw (ex-info "Invalid analytics date range." {:reason :invalid-date-range :from from :to to})))
    (let [previous-start (- a (inc (- b a)))]
      (when (neg? previous-start)
        (throw (ex-info "The equal-length previous period falls before year 0001."
                        {:reason :unsupported-previous-range :from from :to to})))
      {:from (ordinal-date previous-start) :to (ordinal-date (dec a))})))

(defn- within? [date from to]
  (and (parts date) (not (neg? (compare date from))) (not (pos? (compare date to)))))
(defn- valid-id? [x] (and (string? x) (pos? (count x))))
(defn- unique-by [f xs]
  (:out (reduce (fn [{:keys [seen] :as acc} x]
                  (let [k (f x)] (if (contains? seen k) acc
                                    (-> acc (update :seen conj k) (update :out conj x)))))
                {:seen #{} :out []} xs)))
(defn- complete-records [document]
  (unique-by :id (filter #(and (valid-id? (:id %)) (parts (:date %))
                               (pos? (domain/record-duration-sec %))) (:records document))))
(defn- group-key [record] (if (valid-id? (:group-id record)) (:group-id record) (:id record)))
(defn- actual-groups [records]
  ;; Attribute a split group to one earliest local day, even when the requested
  ;; period begins after that day. Minutes still follow each stored daily slice.
  (unique-by group-key (sort-by (juxt #(first (first (domain/record-segments %))) :date :id) records)))
(defn- planned-entries [document]
  (let [exercises (into {} (map (juxt :id identity) (:exercises document)))]
    (unique-by (juxt :plan-id :item-id)
      (for [plan (:plans document) item (:items plan)
            :when (and (valid-id? (:id plan)) (valid-id? (:id item)) (parts (:date plan)))
            :let [seconds (:duration-sec item) exercise (get exercises (:exercise-id item))]]
        {:plan-id (:id plan) :item-id (:id item) :date (:date plan) :exercise exercise
         :minutes (if (and (number? seconds) (< 0 seconds 1.0e12)) (/ seconds 60.0) 0.0)}))))
(defn- load-add [loads exercise quantity]
  (reduce (fn [out [id weight]] (update out id (fnil + 0.0) (* quantity weight)))
          loads (domain/exercise-muscle-weights exercise)))

(defn summary
  "Metric is weighted-minutes or sets. Primary/secondary muscle weights are 1/.5.
   Total sets is distinct physical groups, not the sum of weighted muscle counts."
  [document {:keys [from to metric mode] :or {metric "weighted-minutes" mode "actual"}}]
  (adjacent-range from to)
  (let [planned? (= mode "planned") records (complete-records document)
        rows (if planned? (filter #(and (:exercise %) (pos? (:minutes %))) (planned-entries document)) records)
        rows (filter #(within? (:date %) from to) rows)
        groups (if planned? rows (filter #(within? (:date %) from to) (actual-groups records)))
        minutes (fn [row] (if planned? (:minutes row) (/ (domain/record-duration-sec row) 60.0)))
        muscle-rows (if (= metric "sets") groups rows)]
    {:duration-min (reduce + 0.0 (map minutes rows))
     :sets (count groups)
     :training-days (count (set (map :date rows)))
     :muscles (reduce #(load-add %1 (:exercise %2) (if (= metric "sets") 1.0 (minutes %2))) {} muscle-rows)}))

(defn- change [current previous]
  {:current current :previous previous :delta (- current previous)
   :percent (when (pos? previous) (* 100.0 (/ (- current previous) previous)))})

(defn comparison [current previous]
  {:duration-min (change (:duration-min current) (:duration-min previous))
   :sets (change (:sets current) (:sets previous))
   :training-days (change (:training-days current) (:training-days previous))
   :muscles (mapv (fn [{:keys [id zh]}]
                    (assoc (change (get-in current [:muscles id] 0.0)
                                   (get-in previous [:muscles id] 0.0)) :id id :name zh))
                  domain/muscle-dictionary)})

(defn calendar
  "Monday-first 42-cell calendar. Completion matches BOTH plan-id and item-id.
   Heat follows actual minutes; group sets are attributed only to the first day."
  [document month]
  (let [first-day (str month "-01") start (ordinal first-day)]
    (when-not start (throw (ex-info "Invalid calendar month." {:month month})))
    (let [records (complete-records document) groups (actual-groups records)
          entries (planned-entries document)
          done (set (map (juxt :plan-id :item-id) records))
          minutes-by-day (reduce #(update %1 (:date %2) (fnil + 0.0) (/ (domain/record-duration-sec %2) 60.0)) {} records)
          groups-by-day (frequencies (map :date groups))
          plans-by-day (group-by :date entries)
          dates (map #(when (<= 0 % (ordinal "9999-12-31")) (ordinal-date %))
                     (range (- start (mod start 7)) (+ (- start (mod start 7)) 42)))]
      {:month month :weekdays ["Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"]
       :cells (mapv (fn [date]
                      (let [items (get plans-by-day date [])
                            completed (count (filter #(contains? done [(:plan-id %) (:item-id %)]) items))]
                        {:date date :day (when date (digits (subs date 8 10)))
                         :in-month? (boolean (and date (= month (subs date 0 7))))
                         :duration-min (get minutes-by-day date 0.0) :sets (get groups-by-day date 0)
                         :planned-items (count items) :completed-items completed
                         :plan-complete? (boolean (and (seq items) (= completed (count items))))})) dates)})))

(defn payload
  "Serialize this result to JSON for AnalyticsComparison and TrainingCalendar."
  [document {:keys [from to metric mode month] :or {metric "weighted-minutes" mode "actual"} :as options}]
  (let [previous-range (adjacent-range from to)
        current (summary document options)
        previous (summary document (merge options previous-range))]
    {:metric metric :mode mode :range {:from from :to to} :previous-range previous-range
     :current current :previous previous :muscles (:muscles current)
     :comparison (comparison current previous)
     :calendar (calendar document (or month (subs to 0 7)))}))
