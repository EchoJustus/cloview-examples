(ns clogem.fitness.catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [clogem.fitness.catalog :as catalog]))

(def library
  [{:id "press" :name {:en "Band Chest Press" :zh "Band Chest Press"}
    :primary ["pectoralis-major"] :secondary ["triceps-brachii"]
    :intensity "medium" :equipment ["Resistance band" "Weight bench"] :met 4.0}
   {:id "curl" :name {:en "Dumbbell Curl" :zh "Dumbbell Curl"}
    :primary ["biceps-brachii"] :secondary []
    :intensity "low" :equipment ["Dumbbell"] :met 2.5}
   {:id "pushup" :name {:en "Push-up" :zh "Push-up"}
    :primary ["pectoralis-major"] :secondary ["triceps-brachii"]
    :intensity "high" :equipment ["No equipment"] :met 6.0}
   {:id "mobility" :name "Hip mobility" :primary ["quadriceps"]
    :secondary ["gluteus-medius"] :met 2.0}])

(defn- ids [filters] (mapv :id (catalog/filter-exercises library filters)))

(deftest fixed-options-have-stable-values
  (is (= [{:id "low" :label "Low intensity"} {:id "medium" :label "Medium intensity"} {:id "high" :label "High intensity"}]
         catalog/intensity-options))
  (is (every? (set catalog/equipment-presets)
              ["No equipment" "Ab wheel" "Resistance band" "Jump rope" "Dumbbell" "Barbell" "Kettlebell" "Chair" "Weight bench" "Exercise mat"])))

(deftest equipment-normalization-is-stable-and-exclusive
  (is (= ["Resistance band" "Chair"]
         (catalog/normalize-equipment [" Resistance band " nil "" 123 "Chair" "Resistance band" "No equipment" "Unset"])))
  (is (= ["No equipment"] (catalog/normalize-equipment [" No equipment " "No equipment"])))
  (is (= ["Homemade sandbag"] (catalog/normalize-equipment " Homemade sandbag ")))
  (is (= [] (catalog/normalize-equipment nil)))
  (is (= [] (catalog/normalize-equipment {})))
  (let [normalized (catalog/normalize-equipment ["Dumbbell" "Barbell" "Dumbbell"])]
    (is (= normalized (catalog/normalize-equipment normalized)))))

(deftest selecting-no-equipment-and-real-equipment-are-mutually-exclusive
  (is (= ["No equipment"] (catalog/toggle-equipment ["Dumbbell" "Weight bench"] "No equipment" true)))
  (is (= ["Dumbbell"] (catalog/toggle-equipment ["No equipment"] " Dumbbell " true)))
  (is (= ["Dumbbell" "Weight bench"] (catalog/toggle-equipment ["Dumbbell"] "Weight bench" true)))
  (is (= ["Dumbbell"] (catalog/toggle-equipment ["Dumbbell" "Weight bench"] "Weight bench" false)))
  (is (= ["Dumbbell"] (catalog/toggle-equipment ["Dumbbell"] "Dumbbell" true)))
  (is (= [] (catalog/toggle-equipment ["No equipment"] "No equipment" false)))
  (is (= ["Dumbbell"] (catalog/toggle-equipment ["Dumbbell"] "Unset" true)))
  (is (= ["Dumbbell"] (catalog/toggle-equipment ["Dumbbell"] "  " true))))

(deftest intensity-boundaries-and-explicit-overrides
  (is (= ["low" "low" "medium" "medium" "high"]
         (mapv #(-> {:met %} catalog/normalize-exercise :intensity) [0 2.99 3 5.99 6])))
  (is (= "low" (:intensity (catalog/normalize-exercise {:met 9 :intensity "low"}))))
  (is (= "high" (:intensity (catalog/normalize-exercise {:met 1 :intensity "high"}))))
  (is (= "high" (:intensity (catalog/normalize-exercise {:met 8 :intensity "not-an-option"}))))
  (doseq [bad [nil "unknown" ##NaN ##Inf -1]]
    (is (= "medium" (:intensity (catalog/normalize-exercise {:met bad}))))))

(deftest only-original-demo-identity-and-media-get-equipment-inferred
  (is (= ["Resistance band"] (:equipment (catalog/normalize-exercise {:id "squat" :media-path "assets/demo/squat.gif"}))))
  (is (= ["Resistance band" "Chair"] (:equipment (catalog/normalize-exercise {:id "seated_chest_press" :media-path "assets/demo/seated_chest_press.gif"}))))
  (doseq [exercise [{:id "custom" :media-path "assets/demo/custom.gif"}
                    {:id "squat" :media-path "external/my-squat.gif"}
                    {:id "squat" :media-path "assets/demo/squat.mp4"}
                    {:id "squat" :media-path "assets/demo/lunge.gif"}
                    {:id "squat"}]]
    (is (= [] (:equipment (catalog/normalize-exercise exercise))))))

(deftest explicit-user-equipment-and-other-fields-survive-migration
  (let [demo {:id "squat" :media-path "assets/demo/squat.gif" :met 8 :intensity "low"
              :notes "my edited notes" :name {:en "My squat" :zh "My squat"} :tags ["favorite"]}]
    (doseq [equipment [[] nil ["No equipment"] ["Barbell" "Homemade sandbag"]]]
      (let [original (assoc demo :equipment equipment)
            normalized (catalog/normalize-exercise original)]
        (is (= (catalog/normalize-equipment equipment) (:equipment normalized)))
        (is (= "low" (:intensity normalized)))
        (is (= (dissoc original :equipment) (dissoc normalized :equipment)))))))

(deftest document-migration-is-idempotent-and-never-rewrites-history
  (let [snapshot {:id "squat" :media-path "assets/demo/squat.gif" :met 5}
        document {:version 1 :exercises [snapshot (assoc snapshot :id "custom" :equipment [])]
                  :plans [{:id "plan" :items [{:id "one" :exercise-id "squat"}]}]
                  :records [{:id "record" :exercise snapshot :segments [[0 1000]]}]
                  :active {:id "active" :exercise snapshot :segments [[1000 nil]]}
                  :other {:keep "exactly"}}
        migrated (catalog/migrate-document document)]
    (is (= ["Resistance band"] (get-in migrated [:exercises 0 :equipment])))
    (is (= [] (get-in migrated [:exercises 1 :equipment])))
    (is (= document (assoc migrated :exercises (:exercises document))))
    (is (= migrated (catalog/migrate-document migrated)))
    (is (not (contains? (get-in migrated [:records 0 :exercise]) :equipment)))
    (is (not (contains? (get-in migrated [:active :exercise]) :intensity))))
  (is (= {:records []} (catalog/migrate-document {:records []})))
  (is (= {:exercises nil} (catalog/migrate-document {:exercises nil}))))

(deftest available-equipment-does-not-invent-no-equipment
  (is (= [] (catalog/available-equipment [])))
  (is (= ["Unset"] (catalog/available-equipment [{:id "a"} {:id "b" :equipment []}])))
  (is (= ["No equipment"] (catalog/available-equipment [{:equipment ["No equipment"]}])))
  (is (= ["Dumbbell" "Resistance band"] (catalog/available-equipment [{:equipment [" Resistance band " "Dumbbell"]} {:equipment ["Dumbbell"]}])))
  (is (= ["Dumbbell" "Resistance band" "Unset"]
         (catalog/available-equipment [{:equipment ["Resistance band" "Dumbbell"]} {:equipment nil}]))))

(deftest no-filter-keeps-source-order-and-original-data
  (is (= library (catalog/filter-exercises library {})))
  (is (vector? (catalog/filter-exercises library nil)))
  (is (= ["press" "curl" "pushup" "mobility"] (ids {:query "  " :muscles [] :intensities [] :equipment []}))))

(deftest category-options-are-or-and-categories-are-and
  (is (= ["press" "curl" "pushup"] (ids {:muscles ["pectoralis-major" "biceps-brachii"]})))
  (is (= ["press" "curl" "mobility"] (ids {:intensities ["low" "medium"]})))
  (is (= ["press" "curl"] (ids {:equipment ["Resistance band" "Dumbbell"]})))
  (is (= ["press"] (ids {:muscles ["pectoralis-major" "biceps-brachii"]
                         :intensities ["medium" "high"] :equipment ["Resistance band" "Dumbbell"]})))
  (is (= [] (ids {:muscles ["pectoralis-major"] :equipment ["Dumbbell"]}))))

(deftest secondary-muscles-and-unset-equipment-are-filterable
  (is (= ["press" "pushup"] (ids {:muscles ["triceps-brachii"]})))
  (is (= ["mobility"] (ids {:muscles ["gluteus-medius"] :equipment ["Unset"]})))
  (is (= ["curl" "mobility"] (ids {:equipment ["Dumbbell" "Unset"]})))
  (is (= ["pushup"] (ids {:equipment ["No equipment"]}))))

(deftest search-covers-bilingual-names-muscles-and-equipment
  (is (= ["press"] (ids {:query " bAnD cHeSt "})))
  (is (= ["pushup"] (ids {:query "Push"})))
  (is (= ["press" "pushup"] (ids {:query "TRICEPS"})))
  (is (= ["press" "pushup"] (ids {:query "Triceps brachii"})))
  (is (= ["press"] (ids {:query "Weight bench"})))
  (is (= ["mobility"] (ids {:query "Gluteus medius"})))
  (is (= ["pushup"] (ids {:query "Pectoralis major" :intensities ["high"]})))
  (is (= [] (ids {:query "Nonexistent exercise"}))))

(deftest standalone-english-name-field-is-searchable-without-replacing-chinese
  (let [exercise {:id "overhead" :name "Overhead Press" :name-en "Overhead Press"
                  :primary ["anterior-deltoid"] :equipment ["Dumbbell"] :intensity "medium"}]
    (is (= [exercise] (catalog/filter-exercises [exercise] {:query " oVeRhEaD "})))
    (is (= [exercise] (catalog/filter-exercises [exercise] {:query "Overhead"})))
    (is (= [] (catalog/filter-exercises [exercise] {:query "overhead" :equipment ["Resistance band"]})))))
