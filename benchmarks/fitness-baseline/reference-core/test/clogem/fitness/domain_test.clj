(ns clogem.fitness.domain-test
  (:require [clojure.test :refer [deftest is testing]]
            [clogem.fitness.domain :as d]))

(def exercise
  {:id "press" :name {:en "Press" :zh "Press"}
   :primary ["pectoralis-major" "pectoralis-major"]
   :secondary ["pectoralis-major" "triceps-brachii" "triceps-brachii"]
   :met 4.0 :reference-rpm 10.0})

(def record
  {:id "r1" :plan-id "p1" :item-id "i1" :exercise exercise
   :started-at 0 :completed-at 120000 :segments [[0 60000] [90000 120000]]
   :reps 15 :date "2026-10-13" :duration-source :timer})

(defn approximately [expected actual]
  (and (number? actual) (< (Math/abs (- expected actual)) 0.000001)))

(deftest indexed-heart-rate-keeps-pause-boundaries-and-reuses-validation
  (let [samples (mapv (fn [t] {:timestamp t :bpm (+ 80 (mod t 7))}) (range -1000000 1000000 1000))
        index (d/prepare-heart-rate samples)
        direct (d/match-heart-rate record samples)
        prepared (d/match-heart-rate record [] {:heart-rate-index index})
        calls (atom 0) validate d/validate-hr-samples]
    (is (= direct prepared))
    (is (= 90000 (:covered-ms prepared)))
    (is (every? (fn [{:keys [start end]}] (or (<= end 60000) (>= start 90000))) (:intervals prepared)))
    (with-redefs [d/validate-hr-samples (fn [xs] (swap! calls inc) (validate xs))]
      (d/daily-calories [record (assoc record :id "second" :started-at 180000 :completed-at 240000 :segments [[180000 240000]])]
                       d/default-profile samples "2026-10-13" "2026-10-13")
      (is (= 1 @calls) "A daily report must index heart rate once, not once per set."))))

(deftest muscle-membership-is-not-counted-twice
  (is (= {"pectoralis-major" 1.0 "triceps-brachii" 0.5}
         (d/exercise-muscle-weights exercise)))
  (is (= (count d/muscle-dictionary) (count d/muscle-ids)))
  (is (every? string? d/muscle-ids)))

(deftest active-time-excludes-pauses-and-merges-overlap
  (is (= [[0 70000] [90000 120000]]
         (d/normalize-segments [[90000 120000] [0 60000] [30000 70000]
                                [10 nil] [6 5] ["bad" 12] [9 9]])))
  (is (= 90.0 (d/record-duration-sec record)))
  (is (= [[0 10000] [110000 120000]]
         (d/record-segments (assoc record :segments [[-5000 10000] [110000 200000]]))))
  (is (= 0.0 (d/record-duration-sec (assoc record :segments []))))
  (is (= 120.0 (d/record-duration-sec (dissoc record :segments))))
  (is (= 0.0 (d/record-duration-sec (dissoc record :completed-at)))))

(deftest local-date-attribution-is-caller-controlled
  (let [next-day (assoc record :id "r2" :date "2026-10-14")
        load (d/actual-load [record next-day] "2026-10-13" "2026-10-13")]
    (is (= {"pectoralis-major" 1.5 "triceps-brachii" 0.75} load))
    (is (= {} (d/actual-load [record] "2026-10-14" "2026-10-15")))
    (is (= 3.0 (get (d/actual-load [record next-day] "2026-10-13" "2026-10-14")
                    "pectoralis-major")))))

(deftest planned-load-uses-minutes-and-allows-repeated-exercise
  (let [plan {:id "p" :date "2026-10-13" :items [{:id "a" :exercise-id "press" :duration-sec 60 :reps 20}
                                                {:id "b" :exercise-id "press" :duration-sec 120 :reps 1}
                                                {:id "c" :exercise-id "unknown" :duration-sec 999}
                                                {:id "d" :exercise-id "press" :duration-sec -10}]}]
    (is (= {"pectoralis-major" 3.0 "triceps-brachii" 1.5}
           (d/planned-load [plan] [exercise] "2026-10-13" "2026-10-13")))
    (is (= {} (d/planned-load [plan] {"press" exercise} "2026-10-14" nil)))))

(deftest records-are-snapshots-and-completion-is-required
  (is (= {"pectoralis-major" 1.5 "triceps-brachii" 0.75}
         (d/actual-load [record record (dissoc record :completed-at)] nil nil)))
  (is (= {} (d/actual-load [(dissoc record :completed-at)] nil nil))))

(deftest hr-validation-keeps-last-valid-duplicate
  (let [answer (d/validate-hr-samples [{:timestamp 10000 :bpm 100}
                                      {:timestamp 0 :bpm 80}
                                      {:timestamp 10000 :bpm 110}
                                      {:timestamp 10000 :bpm 999}
                                      {:timestamp nil :bpm 80}
                                      {:timestamp 20000 :bpm 120 :end-at 19999}])]
    (is (= [{:timestamp 0 :bpm 80} {:timestamp 10000 :bpm 110}] (:samples answer)))
    (is (= 1 (:duplicates answer)))
    (is (= [:invalid-bpm :invalid-timestamp :invalid-end] (mapv :reason (:errors answer))))))

(deftest hr-matching-is-time-weighted-and-half-open
  (let [r (assoc record :completed-at 30000 :segments [[0 30000]])
        hr (d/match-heart-rate r [{:timestamp -10000 :end-at 0 :bpm 90}
                                  {:timestamp 0 :end-at 10000 :bpm 100}
                                  {:timestamp 10000 :end-at 30000 :bpm 130}
                                  {:timestamp 30000 :end-at 60000 :bpm 180}])]
    (is (= 30000 (:covered-ms hr)))
    (is (= 120.0 (:average-bpm hr)))
    (is (= 1.0 (:coverage hr)))
    (is (= 2 (count (:intervals hr))))))

(deftest hr-does-not-bridge-a-large-gap-or-pauses
  (let [hr (d/match-heart-rate record [{:timestamp 0 :bpm 100}
                                      {:timestamp 90000 :bpm 120}])]
    (is (= 10000 (:covered-ms hr)))
    (is (= 110.0 (:average-bpm hr)))
    (is (approximately (/ 1.0 9) (:coverage hr))))
  (let [hr (d/match-heart-rate record [{:timestamp 0 :bpm 100 :end-at 120000}])]
    (is (= 90000 (:covered-ms hr)))
    (is (= [{:start 0 :end 60000 :bpm 100} {:start 90000 :end 120000 :bpm 100}]
           (:intervals hr))))
  (is (nil? (:average-bpm (d/match-heart-rate record [])))))

(deftest overlapping-hr-samples-do-not-double-charge
  (let [samples [{:timestamp 0 :end-at 120000 :bpm 100}
                 {:timestamp 30000 :end-at 120000 :bpm 140}
                 {:timestamp 30000 :end-at 120000 :bpm 140}]
        hr (d/match-heart-rate record samples)]
    (is (= 90000 (:covered-ms hr)))
    (is (= 1.0 (:coverage hr)))
    (is (approximately (/ (+ (* 30000 100) (* 60000 140)) 90000.0) (:average-bpm hr)))))

(deftest baseline-met-calories-and-missing-weight
  (let [result (d/record-calories record {:weight-kg 70} [])]
    (is (= 7.35 (:baseline-kcal result)))
    (is (= 7.35 (:kcal result)))
    (is (= :met-frequency (:method result)))
    (is (false? (:example-profile? result))))
  (is (= {:kcal nil :reason :missing-weight}
         (select-keys (d/record-calories record {} []) [:kcal :reason])))
  (is (true? (:example-profile? (d/record-calories record d/default-profile []))))
  (is (= 0.0 (:kcal (d/record-calories (dissoc record :completed-at) {} [])))))

(deftest hr-reserve-adjustment-only-applies-to-covered-minutes
  (let [r (assoc record :segments [[0 60000]] :completed-at 60000 :reps 999)
        profile {:weight-kg 70 :resting-hr 60 :max-hr 160 :use-hr? true}
        result (d/record-calories r profile [{:timestamp 0 :end-at 30000 :bpm 160}])]
    (is (approximately 4.9 (:baseline-kcal result)))
    (is (= 0.5 (:hr-coverage result)))
    (is (approximately 1.2 (:hr-factor result)))
    (is (= 1.0 (:frequency-factor result)))
    (is (approximately (* 4.9 1.1) (:kcal result)))
    (is (= :hr-reserve-heuristic (:method result)))))

(deftest frequency-correction-is-bounded-and-disabled-with-any-hr
  (let [fast (assoc record :reps 9999)
        no-hr (d/record-calories fast {:weight-kg 70} [])
        with-hr (d/record-calories fast {:weight-kg 70 :use-hr? false}
                                  [{:timestamp 0 :bpm 100}])]
    (is (= 1.25 (:frequency-factor no-hr)))
    (is (= 1.0 (:frequency-factor with-hr)))
    (is (= :met (:method with-hr))))
  (let [result (d/record-calories (assoc record :reps 0) {:weight-kg 70} [])]
    (is (= 1.0 (:frequency-factor result)))))

(deftest daily-totals-ignore-duplicate-ids-and-overlapping-clock-time
  (let [other (assoc record :id "r2" :started-at 30000 :completed-at 150000
                            :segments [[30000 150000]] :reps 20)
        result (d/daily-calories [record record other (assoc record :id "open" :completed-at nil)]
                                 {:weight-kg 70} [] nil nil)
        day (get result "2026-10-13")]
    (is (= 2.5 (:duration-min day)))
    (is (= 2 (:record-count day)))
    (is (approximately 12.25 (:baseline-kcal day)))
    (is (approximately 12.25 (:kcal day))))
  (is (= 1 (:unknown-count (get (d/daily-calories [record] {} [] nil nil) "2026-10-13"))))
  (is (nil? (:kcal (get (d/daily-calories [record] {} [] nil nil) "2026-10-13")))))

(deftest plan-editing-preserves-repeated-exercises-with-distinct-item-ids
  (let [plan {:id "p" :items [{:id "a" :exercise-id "press"} {:id "b" :exercise-id "press"}]}
        copied (d/duplicate-item plan "a" "copy")]
    (is (= ["a" "copy" "b"] (mapv :id (:items copied))))
    (is (= ["copy" "b" "a"] (mapv :id (:items (d/reorder-item copied "a" 999)))))
    (is (= ["b" "a" "copy"] (mapv :id (:items (d/reorder-item copied "b" -1)))))
    (is (= ["a" "b"] (mapv :id (:items (d/remove-item copied "copy")))))
    (is (= plan (d/reorder-item plan "missing" 1)))
    (is (thrown? clojure.lang.ExceptionInfo (d/duplicate-item plan "a" "b")))
    (is (thrown? clojure.lang.ExceptionInfo (d/duplicate-item plan "a" " ")))))

(deftest finish-is-idempotent-closes-open-segments-and-protects-identity
  (let [open (assoc record :completed-at nil :segments [[0 30000] [90000 nil]])
        finished (d/finish-record open 120000 {:reps 20 :id "hijack" :date "tomorrow"})]
    (is (= [[0 30000] [90000 120000]] (:segments finished)))
    (is (= 60.0 (d/record-duration-sec finished)))
    (is (= "r1" (:id finished)))
    (is (= "2026-10-13" (:date finished)))
    (is (= 20 (:reps finished)))
    (is (= finished (d/finish-record finished 999999 {:reps 100})))
    (is (thrown? clojure.lang.ExceptionInfo (d/finish-record open -1)))))
