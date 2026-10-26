;; Run from the package root. This reference runner is a JVM/Babashka tool,
;; not the framework under evaluation and not a CLR/Flutter build runner.
(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clogem.fitness.domain :as d]
         '[clogem.fitness.catalog :as c]
         '[clogem.fitness.analytics :as a])

(defn json-string [s]
  (str "\"" (str/escape s {\" "\\\"" \\ "\\\\" \newline "\\n" \return "\\r" \tab "\\t"}) "\""))
(declare json-value)
(defn json-value [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (json-string v)
    (keyword? v) (json-string (subs (str v) 1))
    (number? v) (str v)
    (map? v) (str "{" (str/join "," (for [[k x] (sort-by (comp str key) v)]
                                      (str (json-value (if (keyword? k) (subs (str k) 1) k)) ":" (json-value x)))) "}")
    (sequential? v) (str "[" (str/join "," (map json-value v)) "]")
    :else (throw (ex-info "Unsupported JSON value" {:value v}))))

(defn result [{:keys [operation input]}]
  (let [{:keys [record profile samples document options from to]} input]
    (case operation
      "catalog-filter" (mapv :id (c/filter-exercises (:exercises input) (:filters input)))
      "normalize-equipment" (c/normalize-equipment (:values input))
      "toggle-equipment" (c/toggle-equipment (:current input) (:name input) (:selected? input))
      "normalize-exercises" (mapv c/normalize-exercise (:exercises input))
      "duplicate-item" (d/duplicate-item (:plan input) (:item-id input) (:new-id input))
      "duplicate-collision-error" (try (d/duplicate-item (:plan input) (:item-id input) (:new-id input))
                                      {:reason "did-not-throw"}
                                      (catch clojure.lang.ExceptionInfo e {:reason (:reason (ex-data e))}))
      "record-duration" {:duration-sec (d/record-duration-sec record) :segments (d/record-segments record)}
      "finish-record" (d/finish-record record (:now-ms input) (:updates input))
      "finish-idempotence" {:unchanged? (= record (d/finish-record record (:now-ms input) (:updates input)))
                            :record (d/finish-record record (:now-ms input) (:updates input))}
      "normalize-segments" (d/normalize-segments (:segments input))
      "muscle-weights" (d/exercise-muscle-weights (:exercise input))
      "match-hr" (d/match-heart-rate record samples)
      "validate-hr" (d/validate-hr-samples samples)
      "record-calories" (d/record-calories record profile samples)
      "target-hr-does-not-measure" (let [before (d/record-calories record profile samples)
                                        after (d/record-calories (assoc record :target-hr (:changed-target-hr input)) profile samples)]
                                    {:unchanged? (= before after) :before before :after after})
      "daily-calories" (d/daily-calories (:records input) profile samples from to)
      "analytics-summary" (a/summary document options)
      "analytics-payload" (a/payload document options)
      "adjacent-range" (a/adjacent-range from to)
      "calendar" (a/calendar document (:month input))
      (throw (ex-info "Unknown fixture operation" {:operation operation})))))

(def inputs (edn/read-string (slurp "fixtures/business-inputs.edn")))
(def actual (assoc inputs :cases (mapv #(assoc % :expected (result %)) (:cases inputs))))
(def mode (or (first *command-line-args*) "--check"))
(case mode
  "--write" (do
               (spit "fixtures/business-cases.edn" (str (pr-str actual) "\n"))
               (spit "fixtures/business-cases.json" (str (json-value actual) "\n"))
               (println (str "Generated " (count (:cases actual)) " reference cases.")))
  "--check" (let [expected (edn/read-string (slurp "fixtures/business-cases.edn"))]
               (when-not (= (str/trim (slurp "fixtures/business-cases.json")) (json-value expected))
                 (throw (ex-info "Frozen JSON and EDN fixtures disagree." {})))
               (when-not (= expected actual)
                 (doseq [[wanted got] (map vector (:cases expected) (:cases actual))
                         :when (not= wanted got)] (println (str "MISMATCH " (:id wanted))))
                 (throw (ex-info "Reference core output differs from frozen fixtures." {})))
               (println (str "PASS: " (count (:cases actual)) " reference cases match frozen EDN outputs.")))
  (throw (ex-info "Use --check or --write" {:mode mode})))
