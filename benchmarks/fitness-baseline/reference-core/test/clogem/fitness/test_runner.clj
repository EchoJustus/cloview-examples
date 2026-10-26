(ns clogem.fitness.test-runner
  (:require [clojure.test :as test]
            [clogem.fitness.domain-test]
            [clogem.fitness.catalog-test]
            [clogem.fitness.analytics-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'clogem.fitness.domain-test 'clogem.fitness.catalog-test 'clogem.fitness.analytics-test)]
    (when (pos? (+ fail error)) (System/exit 1))))
