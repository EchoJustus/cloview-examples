(ns cloview.examples.tasks
  "Babashka task implementations for cloview-examples' bb.edn (run from the repo root).

  The CLR and Flutter helpers are cloview-core's (cloview.tasks), loaded from the sibling
  checkout ../cloview-core (bb.edn :paths); this namespace adds the apps' folders and the
  benchmark checkers. Nothing here writes into the cloview-core checkout: the WPF host is
  built from a copy of its template under target/.

  The PowerShell harness (verify/run-windows-checks.ps1) implements the same steps
  without requiring babashka; keep the two in sync (source dirs, paths)."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def core-root
  "The cloview-core checkout: a sibling of this repository (README, \"Getting started\")."
  "../cloview-core")

(when-not (fs/exists? (fs/path core-root "verify" "bb" "cloview" "tasks.clj"))
  (throw (ex-info (str "cloview-core not found at " (fs/absolutize core-root) ". Clone it next to "
                       "this repository: git clone https://github.com/echojustus/cloview-core ../cloview-core")
                  {:core-root core-root})))

(require '[cloview.tasks :as t])

;; ---------------------------------------------------------------------------
;; CLR: staging and the host

(def app-clr-dirs
  "The apps' CLR sources (shared code, its tests, WPF entry points)."
  ["apps/fitness/shared/src" "apps/fitness/shared/test" "apps/fitness/wpf/src"])

(def clr-source-dirs
  "Everything CloViewHost needs: cloview-core's module sources and verification helpers
  (its CLR source folders without tests and examples), then the apps'. Keep in sync with
  $sourceDirs in verify/run-windows-checks.ps1."
  (vec (concat (for [d t/clr-source-dirs
                     :when (not (or (str/starts-with? d "examples/") (str/ends-with? d "/test")))]
                 (str core-root "/" d))
               app-clr-dirs)))

(defn wpf-sync! [] (t/wpf-sync! clr-source-dirs))

(def host-project-dir "target/clr-host-project")

(defn wpf-build!
  "Copy cloview-core's CloViewHost template (CloViewHost.csproj, Program.cs) to
  target/clr-host-project and build it into target/clr-host, bundling target/clr-src."
  []
  (fs/create-dirs host-project-dir)
  (doseq [f ["CloViewHost.csproj" "Program.cs"]]
    (fs/copy (fs/path core-root "modules" "wpf" "host-template" f) (fs/path host-project-dir f)
             {:replace-existing true}))
  (t/wpf-build! (str (fs/path host-project-dir "CloViewHost.csproj"))
                (str "-p:CloViewSrcDir=" (fs/absolutize t/clr-src))))

(defn wpf-run! [& args] (apply t/wpf-run! args))

;; ---------------------------------------------------------------------------
;; Flutter

(def flutter-apps
  "Flutter projects of the apps (the shared tests are copied into test/: cljd test only
  discovers namespaces inside the project folder)."
  {"fitness" {:example "apps/fitness/flutter" :dir "target/flutter-fitness"
              :extra-tests ["apps/fitness/shared/test"]}})

(defn flutter-prepare! [app]
  (t/flutter-prepare! (or (get flutter-apps app)
                          (throw (ex-info (str "unknown Flutter app " app) {:apps (keys flutter-apps)})))))

(defn check-flutter! [] (t/check-flutter!))

;; ---------------------------------------------------------------------------
;; Fitness benchmark: Python oracle checks (benchmarks/fitness-baseline/tools)

(def fitness-baseline "benchmarks/fitness-baseline")

(defn fitness-check!
  "Run the baseline's own tools: package integrity, fixture consistency and, for every
  envelope given (paths relative to the repo root), the --observed comparison.
  Returns false when a check failed; prints why when Python is missing."
  [& envelopes]
  (if-some [py (t/python)]
    (let [run (fn [& args]
                ;; -X utf8: under -I Python ignores PYTHONUTF8 / PYTHONIOENCODING
                (zero? (:exit (apply p/shell {:dir fitness-baseline :continue true}
                                     (concat py ["-I" "-X" "utf8"] args)))))
          base-ok (and (run "tools/check_package.py") (run "tools/check_fixtures.py"))
          observed-ok (every? (fn [e] (run "tools/check_fixtures.py" "--observed"
                                           (str (fs/absolutize e))))
                              envelopes)]
      (and base-ok observed-ok))
    (do (println "Python 3.7+ not found: skipping benchmarks/fitness-baseline/tools checks"
                 "(the Clojure oracle comparison above still ran).")
        true)))
