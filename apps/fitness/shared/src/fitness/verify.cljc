(ns fitness.verify
  "Fitness acceptance checks that run on every host from the same shared sources:

  - the reference core's unit tests (clogem.fitness.*-test, ported to .cljc), and
  - DOM-001: every frozen business oracle (fitness.oracle), written as the
    FITNESS_BUSINESS_RESULTS_V1 envelope for
    benchmarks/fitness-baseline/tools/check_fixtures.py --observed.

    JVM:  clj -M:fitness [--out-dir verify/out] [--prefix fitness-] [--revision <rev>] [--baseline <dir>]
          (bb fitness:test runs this, then the Python checker)
    CLR:  CloViewHost.exe --src target/clr-src --ns fitness.verify --run-tests --out verify/out/fitness-clr-summary.json
    Dart: (fitness.verify/verify! {:host \"dart\" ...}) from a flutter test — oracles only; the
          unit tests run through `clj -M:test:cljd test` there.

  Outputs, in out-dir: <prefix><host>-business-results.json (the envelope) and
  <prefix><host>-summary.json (test totals + per-case oracle status). The prefix defaults
  to $CLOVIEW_FITNESS_PREFIX or \"fitness-\" (the Windows harness uses \"02-fitness-\" on
  the JVM and \"04-fitness-\" on Dart; on the CLR it passes the summary path
  03-fitness-clr-summary.json, and the envelope is named after it).
  Exit code 0 only when every unit test and every oracle passes."
  (:require [clojure.string :as str]
            [fitness.io :as fio]
            [fitness.oracle :as oracle]
            #?@(:cljd []
                :default [[clojure.test :as test]
                          [clogem.fitness.domain-test]
                          [clogem.fitness.catalog-test]
                          [clogem.fitness.analytics-test]
                          [fitness.app-test]])))

(defn- git-revision [root]
  (try
    (let [head (str/trim (fio/read-text (fio/join root ".git/HEAD")))]
      (if-some [[_ ref] (re-matches #"ref: (.+)" head)]
        (let [ref-file (fio/join root (str ".git/" ref))]
          (when (fio/file-exists? ref-file) (subs (str/trim (fio/read-text ref-file)) 0 12)))
        (subs head 0 (min 12 (count head)))))
    (catch #?(:cljd dynamic :clj Throwable :cljr System.Exception) _ nil)))

;; ---------------------------------------------------------------------------
;; Unit tests (JVM / CLR; Dart runs them with `clj -M:test:cljd test`)

(def test-namespaces
  '[clogem.fitness.domain-test clogem.fitness.catalog-test clogem.fitness.analytics-test
    fitness.app-test])

(defn run-unit-tests
  "Returns {:test :pass :fail :error} for the ported reference tests."
  []
  #?(:cljd {:test 0 :pass 0 :fail 0 :error 0 :skipped "run with clj -M:test:cljd test"}
     :default (let [s (apply test/run-tests test-namespaces)]
                (select-keys s [:test :pass :fail :error]))))

;; ---------------------------------------------------------------------------
;; Entry points

(defn verify!
  "Run the unit tests and the business oracles for `host`; write the envelope and the
  summary into `out-dir`. Returns {:exit 0|1 :summary-path :results-path :summary}."
  [{:keys [host out-dir prefix revision baseline summary-path]}]
  (let [prefix (or prefix (fio/getenv "CLOVIEW_FITNESS_PREFIX") "fitness-")
        root (fio/find-repo-root)
        baseline (or baseline (fio/getenv "CLOVIEW_FITNESS_BASELINE")
                     (when root (fio/join root "benchmarks/fitness-baseline")))
        _ (when-not (and baseline (fio/file-exists? (fio/join baseline "fixtures/business-inputs.edn")))
            (throw (ex-info (str "Fitness baseline not found (looked for " fio/baseline-marker
                                 " above the working directory); pass --baseline <dir>")
                            {:baseline baseline})))
        out-dir (or out-dir (if root (fio/join root "verify/out") "."))
        revision (or revision (fio/getenv "CLOVIEW_REVISION") (when root (git-revision root)) "working-tree")
        tests (run-unit-tests)
        run (oracle/run-cases (fio/read-text (fio/join baseline "fixtures/business-inputs.edn"))
                              (fio/read-text (fio/join baseline "fixtures/business-cases.edn")))
        results-path (if summary-path
                       (str/replace summary-path #"summary\.json$|\.json$" "business-results.json")
                       (fio/join out-dir (str prefix host "-business-results.json")))
        summary-path (or summary-path (fio/join out-dir (str prefix host "-summary.json")))
        tests-ok? (and (zero? (:fail tests 0)) (zero? (:error tests 0)))
        summary (assoc (oracle/summary run host)
                       :ok (and tests-ok? (oracle/passed? run))
                       :candidate_revision revision
                       :unit_tests tests
                       :business_results (str/replace results-path #"^.*[\\/]" ""))]
    (fio/write-text! results-path (str (oracle/observation-json run host revision) "\n"))
    (fio/write-text! summary-path (str (oracle/->json summary) "\n"))
    (println (str "fitness " host ": unit tests " (:test tests 0) " (fail " (:fail tests 0)
                  ", error " (:error tests 0) "); oracles " (:exact run) " exact + "
                  (:within-tolerance run) " within 1e-9 of " (:total run)
                  (when (seq (:failed run)) (str "; FAILED " (str/join "," (:failed run))))
                  (when (seq (:errors run)) (str "; ERRORS " (pr-str (:errors run))))
                  " -> " results-path))
    {:exit (if (:ok summary) 0 1) :summary-path summary-path :results-path results-path
     :summary summary}))

(defn run-tests!
  "CloViewHost --run-tests entry (ClojureCLR): `out-path` is the summary JSON path; the
  envelope goes next to it (…-summary.json -> …-business-results.json)."
  [out-path]
  (:exit (verify! {:host "clr" :summary-path (or out-path "fitness-clr-summary.json")})))

#?(:cljd nil
   :default
   (defn -main
     "JVM: clj -M:fitness [--out-dir d] [--prefix p] [--revision r] [--baseline dir]"
     [& args]
     (let [opts (loop [args args opts {:host "jvm"}]
                  (if-some [[k v & more] (seq args)]
                    (case k
                      "--out-dir" (recur more (assoc opts :out-dir v))
                      "--prefix" (recur more (assoc opts :prefix v))
                      "--revision" (recur more (assoc opts :revision v))
                      "--baseline" (recur more (assoc opts :baseline v))
                      (recur (next args) opts))
                    opts))
           {:keys [exit]} (verify! opts)]
       (shutdown-agents)
       #?(:clj (System/exit exit) :cljr (System.Environment/Exit exit)))))
