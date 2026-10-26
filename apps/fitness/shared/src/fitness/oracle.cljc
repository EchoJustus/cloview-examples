(ns fitness.oracle
  "DOM-001 business oracles, portable to every cloview host.

  Runs each frozen operation of benchmarks/fitness-baseline/fixtures/business-inputs.edn
  through the shared fitness core (clogem.fitness.*), compares the outputs with
  business-cases.edn, and renders the FITNESS_BUSINESS_RESULTS_V1 observation envelope
  that `tools/check_fixtures.py --observed` validates (one file per host).

  The operation dispatch mirrors benchmarks/fitness-baseline/reference-core/runner.clj
  exactly (that runner is a JVM/Babashka tool; this namespace is the portable one).
  Everything here is pure: callers pass EDN text in and write the returned strings out
  (see fitness.verify for the host file I/O)."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [cloview.core.order :as order]
            [clogem.fitness.analytics :as a]
            [clogem.fitness.catalog :as c]
            [clogem.fitness.domain :as d]))

;; ---------------------------------------------------------------------------
;; Operation dispatch (same names and projections as reference-core/runner.clj)

(defn result
  "Actual output of one fixture case {:operation :input}."
  [{:keys [operation input]}]
  (let [{:keys [record profile samples document options from to]} input]
    (case operation
      "catalog-filter" (mapv :id (c/filter-exercises (:exercises input) (:filters input)))
      "normalize-equipment" (c/normalize-equipment (:values input))
      "toggle-equipment" (c/toggle-equipment (:current input) (:name input) (:selected? input))
      "normalize-exercises" (mapv c/normalize-exercise (:exercises input))
      "duplicate-item" (d/duplicate-item (:plan input) (:item-id input) (:new-id input))
      "duplicate-collision-error"
      (try (d/duplicate-item (:plan input) (:item-id input) (:new-id input))
           {:reason "did-not-throw"}
           (catch #?(:cljd cljd.core/ExceptionInfo
                     :clj clojure.lang.ExceptionInfo
                     :cljr clojure.lang.ExceptionInfo) e
             {:reason (:reason (ex-data e))}))
      "record-duration" {:duration-sec (d/record-duration-sec record) :segments (d/record-segments record)}
      "finish-record" (d/finish-record record (:now-ms input) (:updates input))
      "finish-idempotence" {:unchanged? (= record (d/finish-record record (:now-ms input) (:updates input)))
                            :record (d/finish-record record (:now-ms input) (:updates input))}
      "normalize-segments" (d/normalize-segments (:segments input))
      "muscle-weights" (d/exercise-muscle-weights (:exercise input))
      "match-hr" (d/match-heart-rate record samples)
      "validate-hr" (d/validate-hr-samples samples)
      "record-calories" (d/record-calories record profile samples)
      "target-hr-does-not-measure"
      (let [before (d/record-calories record profile samples)
            after (d/record-calories (assoc record :target-hr (:changed-target-hr input)) profile samples)]
        {:unchanged? (= before after) :before before :after after})
      "daily-calories" (d/daily-calories (:records input) profile samples from to)
      "analytics-summary" (a/summary document options)
      "analytics-payload" (a/payload document options)
      "adjacent-range" (a/adjacent-range from to)
      "calendar" (a/calendar document (:month input))
      (throw (ex-info "Unknown fixture operation" {:operation operation})))))

;; ---------------------------------------------------------------------------
;; Comparison (the fixture guide's rules: exact ints/strings/booleans/nil, order kept,
;; object keys compared as sets, finite floats within 1e-9 absolute or relative)

(def tolerance 1e-9)

(defn- fractional? [x] (and (number? x) (not (integer? x))))

(defn- close? [a b]
  (let [a (double a) b (double b) diff (abs (- a b))]
    (or (<= diff tolerance)
        (<= diff (* tolerance (max (abs a) (abs b)))))))

(defn approx=
  "Deep comparison with the fixture tolerance. Integers must match exactly (as the
  Python checker requires); an expected float accepts an integral or float actual."
  [expected actual]
  (cond
    (or (nil? expected) (boolean? expected)) (= expected actual)
    (integer? expected) (and (integer? actual) (== expected actual))
    (fractional? expected) (and (number? actual) (close? expected actual))
    (or (string? expected) (keyword? expected)) (= expected actual)
    (map? expected) (and (map? actual)
                         (= (set (keys expected)) (set (keys actual)))
                         (every? (fn [[k v]] (approx= v (get actual k))) expected))
    (sequential? expected) (and (sequential? actual)
                                (= (count expected) (count actual))
                                (every? true? (map approx= expected actual)))
    :else (= expected actual)))

(defn run-cases
  "Run every case of `inputs-edn` and compare with `expected-edn` (both EDN text).
  Returns {:cases [{:id :operation :actual :status}] :total :exact :within-tolerance
  :failed [ids] :errors [{:id :error}]}; :status is :exact, :tolerance, :mismatch or
  :error."
  [inputs-edn expected-edn]
  (let [inputs (edn/read-string inputs-edn)
        expected (into {} (map (juxt :id :expected)) (:cases (edn/read-string expected-edn)))
        rows (mapv (fn [{:keys [id operation] :as case}]
                     (try
                       (let [actual (result case)
                             wanted (get expected id ::missing)
                             status (cond (= wanted ::missing) :no-oracle
                                          (= wanted actual) :exact
                                          (approx= wanted actual) :tolerance
                                          :else :mismatch)]
                         {:id id :operation operation :actual actual :status status})
                       (catch #?(:cljd dynamic :clj Throwable :cljr System.Exception) e
                         {:id id :operation operation :status :error
                          :error #?(:cljd (str e) :default (str (or (ex-message e) e)))})))
                   (:cases inputs))]
    {:cases rows
     :total (count rows)
     :exact (count (filter #(= :exact (:status %)) rows))
     :within-tolerance (count (filter #(= :tolerance (:status %)) rows))
     :failed (mapv :id (filter #(contains? #{:mismatch :no-oracle} (:status %)) rows))
     :errors (mapv #(select-keys % [:id :error]) (filter #(= :error (:status %)) rows))}))

(defn passed? [run] (and (empty? (:failed run)) (empty? (:errors run)) (pos? (:total run))))

;; ---------------------------------------------------------------------------
;; JSON (same conventions as runner.clj: keywords -> strings without the colon,
;; string map keys kept, nil -> null, arrays keep order)

(defn- json-escape [s]
  (str/escape s {\" "\\\"" \\ "\\\\" \newline "\\n" \return "\\r" \tab "\\t"
                 \backspace "\\b" \formfeed "\\f"}))

(defn- json-double
  "Culture-invariant finite double. ClojureCLR's `str` of a double follows the current
  culture before 1.13 (a comma on de-DE), so the CLR branch formats explicitly."
  [x]
  #?(:cljd (let [d (double x)]
             (when (or (.-isNaN d) (.-isInfinite d))
               (throw (ex-info "Non-finite number in business output" {:value x})))
             (.toString d))
     :clj (let [d (double x)]
            (when (or (Double/isNaN d) (Double/isInfinite d))
              (throw (ex-info "Non-finite number in business output" {:value x})))
            (str d))
     :cljr (let [d (double x)]
             (when (or (System.Double/IsNaN d) (System.Double/IsInfinity d))
               (throw (ex-info "Non-finite number in business output" {:value x})))
             (let [s (.ToString d "R" System.Globalization.CultureInfo/InvariantCulture)]
               (if (re-find #"[.eE]" s) s (str s ".0"))))))

(defn- json-key [k]
  (cond (keyword? k) (subs (str k) 1)
        (string? k) k
        :else (str k)))

(defn ->json
  "Compact JSON for business values (map keys in ordinal order, so every host writes
  byte-identical files)."
  [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (str "\"" (json-escape v) "\"")
    (keyword? v) (str "\"" (json-escape (subs (str v) 1)) "\"")
    (integer? v) (str v)
    (number? v) (json-double v)
    (map? v) (str "{"
                  (str/join "," (for [[k x] (order/sort-by-str (comp json-key key) v)]
                                  (str "\"" (json-escape (json-key k)) "\":" (->json x))))
                  "}")
    (or (sequential? v) (set? v)) (str "[" (str/join "," (map ->json v)) "]")
    :else (throw (ex-info "Unsupported JSON value" {:value (pr-str v)}))))

(defn observation-json
  "The FITNESS_BUSINESS_RESULTS_V1 envelope for `host` (\"jvm\", \"dart\" or \"clr\")."
  [run host revision]
  (->json {:schema_version "FITNESS_BUSINESS_RESULTS_V1"
           :host host
           :candidate_revision revision
           :cases (mapv (fn [{:keys [id actual]}] {:id id :actual actual}) (:cases run))}))

(defn summary
  "Report map (no actual values) for logs and verify/out JSON."
  [run host]
  {:suite "fitness-business-oracles"
   :host host
   :ok (passed? run)
   :total (:total run)
   :exact (:exact run)
   :within_tolerance (:within-tolerance run)
   :failed (:failed run)
   :errors (:errors run)
   :statuses (mapv (fn [{:keys [id status]}] {:id id :status (name status)}) (:cases run))})
