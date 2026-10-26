(ns clogem.fitness.analytics-test
  (:require [clojure.test :refer [deftest is testing]]
            [clogem.fitness.analytics :as a]))

(def exercise {:id "e" :primary ["pectoralis-major" "pectoralis-major"]
               :secondary ["pectoralis-major" "triceps-brachii" "triceps-brachii"]})
(defn record [id group date start end]
  {:id id :group-id group :date date :started-at start :completed-at end
   :segments [[start end]] :exercise exercise :plan-id "p" :item-id "i"})
(def split-doc
  {:exercises [exercise]
   :records [(record "r-a" "group" "2026-10-12" 0 60000)
             (record "r-b" "group" "2026-10-13" 60000 180000)]
   :plans [{:id "p" :date "2026-10-12" :items [{:id "i" :exercise-id "e" :duration-sec 180}]}]})

(deftest natural-calendar-adjacent-ranges
  (is (= {:from "2024-02-28" :to "2024-02-29"} (a/adjacent-range "2024-03-01" "2024-03-02")))
  (is (= {:from "2025-12-25" :to "2025-12-31"} (a/adjacent-range "2026-01-01" "2026-01-07")))
  (is (= {:from "2026-03-01" :to "2026-03-07"} (a/adjacent-range "2026-03-08" "2026-03-14")))
  (is (thrown? #?(:cljd cljd.core/ExceptionInfo :default clojure.lang.ExceptionInfo) (a/adjacent-range "2026-02-29" "2026-03-01")))
  (is (thrown? #?(:cljd cljd.core/ExceptionInfo :default clojure.lang.ExceptionInfo) (a/adjacent-range "0000-01-01" "0000-01-02")))
  (is (= :unsupported-previous-range
         (try (a/adjacent-range "0001-01-01" "0001-01-02")
              (catch #?(:cljd cljd.core/ExceptionInfo :default clojure.lang.ExceptionInfo) e (:reason (ex-data e))))))
  (is (thrown? #?(:cljd cljd.core/ExceptionInfo :default clojure.lang.ExceptionInfo) (a/adjacent-range "2026-03-02" "2026-03-01"))))

(deftest split-groups-count-once-but-minutes-follow-each-local-date
  (let [both (a/summary split-doc {:from "2026-10-12" :to "2026-10-13" :metric "sets"})
        second (a/summary split-doc {:from "2026-10-13" :to "2026-10-13" :metric "sets"})
        minutes (a/summary split-doc {:from "2026-10-13" :to "2026-10-13"})]
    (is (= 1 (:sets both))) (is (= 3.0 (:duration-min both)))
    (is (= {"pectoralis-major" 1.0 "triceps-brachii" 0.5} (:muscles both)))
    (is (= 0 (:sets second))) (is (= {} (:muscles second)))
    (is (= 2.0 (:duration-min second)))
    (is (= {"pectoralis-major" 2.0 "triceps-brachii" 1.0} (:muscles minutes)))))

(deftest duplicate-incomplete-paused-and-zero-records
  (let [r (assoc (record "one" nil "2026-10-13" 0 180000) :segments [[0 30000] [150000 180000]])
        doc {:records [r r (assoc r :id "open" :completed-at nil) (assoc r :id "zero" :segments [])]}
        result (a/summary doc {:from "2026-10-13" :to "2026-10-13"})]
    (is (= 1 (:sets result))) (is (= 1.0 (:duration-min result))) (is (= 1 (:training-days result)))))

(deftest group-is-owned-by-first-valid-active-slice-not-row-start-or-input-order
  (let [later (assoc (record "late" "shared" "2026-10-14" 0 180000)
                    :segments [[120000 180000]])
        earlier (assoc (record "early" "shared" "2026-10-13" 50000 120000)
                      :segments [[60000 120000]])
        zero (assoc (record "empty" "shared" "2026-10-12" 0 50000) :segments [])
        doc {:records [later zero earlier]}
        first-day (a/summary doc {:from "2026-10-13" :to "2026-10-13" :metric "sets"})
        next-day (a/summary doc {:from "2026-10-14" :to "2026-10-14" :metric "sets"})]
    (is (= 1 (:sets first-day))) (is (= 1.0 (:duration-min first-day)))
    (is (= 0 (:sets next-day))) (is (= 1.0 (:duration-min next-day)))))

(deftest planned-and-actual-are-independent-and-repeated-exercise-is-two-groups
  (let [doc (assoc split-doc :plans [{:id "p" :date "2026-10-13"
                                    :items [{:id "a" :exercise-id "e" :duration-sec 60}
                                            {:id "b" :exercise-id "e" :duration-sec 120}]}])
        planned (a/summary doc {:from "2026-10-13" :to "2026-10-13" :mode "planned" :metric "sets"})]
    (is (= 2 (:sets planned))) (is (= 3.0 (:duration-min planned)))
    (is (= {"pectoralis-major" 2.0 "triceps-brachii" 1.0} (:muscles planned)))
    (is (= 0 (:sets (a/summary doc {:from "2026-10-13" :to "2026-10-13"}))))))

(deftest month-grid-and-plan-completion
  (let [doc (update split-doc :plans conj {:id "other" :date "2026-10-13"
                                          :items [{:id "i" :exercise-id "e" :duration-sec 60}]})
        cal (a/calendar doc "2026-10")
        days (into {} (map (juxt :date identity) (:cells cal)))]
    (is (= 42 (count (:cells cal))))
    (is (= "2026-09-28" (:date (first (:cells cal)))))
    (is (= "2026-11-08" (:date (last (:cells cal)))))
    (is (false? (:in-month? (first (:cells cal)))))
    (is (true? (get-in days ["2026-10-12" :plan-complete?])))
    (is (false? (get-in days ["2026-10-13" :plan-complete?])))
    (is (= 0 (get-in days ["2026-10-13" :completed-items])))
    (is (= 2.0 (get-in days ["2026-10-13" :duration-min])))
    (is (= 1 (get-in days ["2026-10-12" :sets])))
    (is (= 0 (get-in days ["2026-10-13" :sets])))
    (is (false? (get-in days ["2026-10-14" :plan-complete?])))))

(deftest comparison-zero-baseline-is-not-an-infinite-percentage
  (let [result (a/payload split-doc {:from "2026-10-12" :to "2026-10-13" :metric "sets" :month "2026-10"})]
    (is (= {:from "2026-10-10" :to "2026-10-11"} (:previous-range result)))
    (is (nil? (get-in result [:comparison :sets :percent])))
    (is (= 1 (get-in result [:comparison :sets :delta])))
    (is (= 20 (count (get-in result [:comparison :muscles]))))
    (is (= "2026-10" (get-in result [:calendar :month])))))

(deftest calendar-month-lengths-and-empty-data
  (doseq [[month days] [["2024-02" 29] ["2025-02" 28] ["2026-04" 30] ["2026-12" 31]
                       ["0001-01" 31] ["9999-12" 31]]]
    (let [cells (:cells (a/calendar {} month))]
      (is (= 42 (count cells)))
      (is (= days (count (filter :in-month? cells))))
      (is (every? #(and (zero? (:sets %)) (zero? (:duration-min %))
                        (false? (:plan-complete? %))) cells))))
  (is (thrown? #?(:cljd cljd.core/ExceptionInfo :default clojure.lang.ExceptionInfo) (a/calendar {} "2026-13"))))
