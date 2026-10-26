(ns fitness.hr-import
  "Heart-rate import with a preview before commit (DOM-005), pure and portable.

  Flow (the app's events): :file/pick {:extensions [\"csv\" \"json\"]} -> :file/read ->
  (preview text opts) stored in the db and shown with its counts -> confirm:
  (commit document preview ...) | cancel: drop the preview (stored samples unchanged).

  Accepted input
    CSV   header row + one sample per row, columns (case-insensitive):
          timestamp|time|start|start_time|datetime|date_time, bpm|heart_rate|hr|value,
          optional end_time|end|end_at
    JSON  an array of flat objects with the same keys
  Timestamps
    - epoch numbers or digit strings: milliseconds, or seconds when |n| < 1e11
      (1e11 ms is 1973-03-03; no sample predates that)
    - ISO 8601 date-time with Z or an explicit offset (+08:00, +0800): that instant;
      the offset option is ignored for these rows
    - ISO date-time without a zone: interpreted with the explicit fixed offset
      :offset-min (minutes east of UTC, -840..840); without it the row is rejected
    - a bare time or a bare date is rejected (:incomplete-time)
  BPM validity (25..250), duplicate timestamps (last valid wins) and end-after-start are
  the domain's rules (clogem.fitness.domain/validate-hr-samples)."
  (:require [clojure.string :as str]
            [clogem.fitness.domain :as d]
            [cloview.core.text :as text]
            [cloview.fx.codec :as codec]
            [fitness.dates :as dates]))

(def timestamp-keys ["timestamp" "time" "start" "start_time" "datetime" "date_time" "start_at"])
(def bpm-keys ["bpm" "heart_rate" "hr" "value"])
(def end-keys ["end_time" "end" "end_at"])

(def min-offset -840)
(def max-offset 840)

(defn valid-offset? [m]
  (and (integer? m) (<= min-offset m max-offset)))

;; ---------------------------------------------------------------------------
;; Instants

(def ^:private iso-re
  #"(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d{1,9}))?)?([Zz]|[+-]\d{2}(?::?\d{2})?)?")

(def ^:private bare-time-re #"\d{1,2}:\d{2}(:\d{2}([.,]\d+)?)?")
(def ^:private bare-date-re #"\d{4}-\d{2}-\d{2}")
(def ^:private epoch-re #"-?\d{1,16}")

(defn- digits->long
  "Integer for an optionally signed string of ASCII digits (leading zeros allowed)."
  [s]
  (let [neg? (= "-" (subs s 0 1))
        body (if neg? (subs s 1) s)
        n (reduce (fn [acc i] (+ (* 10 acc) (- (int (nth body i)) 48))) 0 (range (count body)))]
    (if neg? (- n) n)))

(defn- n2 [s] (digits->long s))

(defn- epoch->ms [n]
  (let [n (long n)]
    (if (< (if (neg? n) (- n) n) 100000000000) (* n 1000) n)))

(defn- zone->minutes
  "Offset minutes of a zone suffix (\"Z\", \"+08:00\", \"-0530\", \"+08\")."
  [z]
  (if (contains? #{"Z" "z"} z)
    0
    (let [sign (if (= "-" (subs z 0 1)) -1 1)
          digits (str/replace (subs z 1) ":" "")
          hh (n2 (subs digits 0 2))
          mm (if (> (count digits) 2) (n2 (subs digits 2 4)) 0)]
      (when (and hh mm (<= hh 14) (<= mm 59))
        (* sign (+ (* 60 hh) mm))))))

(defn parse-instant
  "{:ms epoch-ms :zone :explicit|:epoch|:offset} or {:error reason} for one timestamp
  value (string or number). `offset-min` applies only to zone-less date-times."
  [v offset-min]
  (cond
    (integer? v) {:ms (epoch->ms v) :zone :epoch}
    (number? v) (if (== v (long v)) {:ms (epoch->ms (long v)) :zone :epoch} {:error :invalid-timestamp})
    (not (string? v)) {:error :invalid-timestamp}
    :else
    (let [s (str/trim v)]
      (cond
        (str/blank? s) {:error :invalid-timestamp}
        (re-matches epoch-re s) {:ms (epoch->ms (digits->long s)) :zone :epoch}
        (or (re-matches bare-time-re s) (re-matches bare-date-re s)) {:error :incomplete-time}
        :else
        (if-some [[_ y mo d hh mi ss frac zone] (re-matches iso-re s)]
          ;; optional groups: nil on the JVM and Dart, "" on ClojureCLR — normalize
          (let [ss (not-empty ss) frac (not-empty frac) zone (not-empty zone)
                date (str y "-" mo "-" d)
                days (dates/->days date)
                hh (n2 hh) mi (n2 mi) ss (if ss (n2 ss) 0)
                ms (if frac (n2 (subs (str frac "00") 0 3)) 0)
                zone-min (when zone (zone->minutes zone))]
            (cond
              (or (nil? days) (nil? hh) (> hh 23) (> mi 59) (> ss 59)) {:error :invalid-timestamp}
              (and zone (nil? zone-min)) {:error :invalid-timestamp}
              (and (nil? zone) (nil? offset-min)) {:error :needs-offset}
              (and (nil? zone) (not (valid-offset? offset-min))) {:error :invalid-offset}
              :else (let [local-ms (+ (* (+ (* (+ (* days 24) hh) 60) mi) 60000) (* ss 1000) ms)
                          minutes (if zone zone-min offset-min)]
                      {:ms (- local-ms (* minutes 60000))
                       :zone (if zone :explicit :offset)})))
          {:error :invalid-timestamp})))))

;; ---------------------------------------------------------------------------
;; Rows

(defn- pick [m ks] (some #(let [v (get m %)] (when-not (or (nil? v) (and (string? v) (str/blank? v))) v)) ks))

(defn- parse-bpm [v]
  (cond (number? v) v
        (string? v) (codec/parse-decimal v)
        :else nil))

(defn- row->sample
  "[sample nil] or [nil {:row r :reason kw}] for one row map with string keys."
  [row-number m offset-min]
  (let [ts (parse-instant (pick m timestamp-keys) offset-min)
        bpm (parse-bpm (pick m bpm-keys))
        end-raw (pick m end-keys)
        end (when (some? end-raw) (parse-instant end-raw offset-min))]
    (cond
      (:error ts) [nil {:row row-number :reason (:error ts)}]
      (nil? bpm) [nil {:row row-number :reason :invalid-bpm}]
      (:error end) [nil {:row row-number :reason :invalid-end}]
      :else [(cond-> {:timestamp (:ms ts) :bpm bpm :row row-number :zone (:zone ts)}
               end (assoc :end-at (:ms end)))
             nil])))

(defn detect-format
  "Format from the first non-blank character: [ or { -> :json, else :csv."
  [text]
  (let [t (str/triml (str text))
        t (if (and (pos? (count t)) (= "\uFEFF" (subs t 0 1))) (str/triml (subs t 1)) t)]
    (if (and (pos? (count t)) (contains? #{"[" "{"} (subs t 0 1))) :json :csv)))

(defn- rows-of
  "[[row-number {key value}] ...] with lower-cased keys."
  [text format]
  (case format
    :json (let [data (codec/read-json text)
                data (if (map? data) [data] data)]
            (when-not (and (sequential? data) (every? map? data))
              (throw (ex-info "JSON heart-rate input must be an array of objects" {:reason :json-shape})))
            (map-indexed (fn [i m] [(inc i) (into {} (map (fn [[k v]] [(text/ascii-lower (str k)) v])) m)]) data))
    (map (fn [{:keys [line values]}] [line values]) (:rows (codec/csv-maps text)))))

(defn preview
  "Parse `text` without committing anything:
    {:format :csv|:json  :rows n  :valid n  :invalid n  :duplicates n
     :samples [{:timestamp :bpm :end-at?} ...]   ; valid, deduplicated, sorted
     :errors [{:row r :reason kw} ...]            ; parse and validation errors
     :offset-min m  :offset-rows n  :explicit-rows n  :epoch-rows n}
  or {:error {:reason kw :message s}} when the file cannot be read at all.
  Options: :format (:csv/:json, detected when absent), :offset-min."
  [text {:keys [format offset-min]}]
  (try
    (let [format (or format (detect-format text))
          rows (vec (rows-of text format))
          parsed (map (fn [[n m]] (row->sample n m offset-min)) rows)
          samples (vec (keep first parsed))
          parse-errors (vec (keep second parsed))
          {:keys [errors duplicates] :as v} (d/validate-hr-samples samples)
          validation-errors (mapv (fn [{:keys [index reason]}]
                                    {:row (:row (nth samples index)) :reason reason})
                                  errors)
          zones (frequencies (map :zone samples))]
      {:format format
       :rows (count rows)
       :valid (count (:samples v))
       :invalid (+ (count parse-errors) (count validation-errors))
       :duplicates duplicates
       :samples (:samples v)
       :errors (vec (sort-by :row (concat parse-errors validation-errors)))
       :offset-min offset-min
       :offset-rows (get zones :offset 0)
       :explicit-rows (get zones :explicit 0)
       :epoch-rows (get zones :epoch 0)})
    (catch #?(:cljd Object :clj Exception :cljr System.Exception) e
      {:error {:reason (or (:reason (ex-data e)) :unreadable)
               :message #?(:cljd (str e) :default (or (ex-message e) (str e)))}})))

(defn commit
  "Document with the preview's samples merged into :heart-rate (an existing sample at the
  same timestamp is replaced: the last valid input wins) and a batch entry appended to
  :heart-rate-batches. `batch` = {:id :imported-at :source}."
  [document preview {:keys [id imported-at source]}]
  (if (or (:error preview) (empty? (:samples preview)))
    document
    (let [merged (:samples (d/validate-hr-samples (concat (:heart-rate document) (:samples preview))))]
      (-> document
          (assoc :heart-rate merged)
          (update :heart-rate-batches (fnil conj [])
                  {:id id :imported-at imported-at :source source
                   :count (count (:samples preview)) :offset-min (:offset-min preview)})))))
