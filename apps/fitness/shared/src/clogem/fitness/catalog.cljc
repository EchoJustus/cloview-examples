(ns clogem.fitness.catalog
  "Pure exercise metadata normalization, conservative migration, and library filters."
  (:require [clojure.string :as str]
            [clogem.fitness.domain :as domain]))

(def intensity-options
  [{:id "low" :label "Low intensity"}
   {:id "medium" :label "Medium intensity"}
   {:id "high" :label "High intensity"}])

(def equipment-presets
  ["No equipment" "Ab wheel" "Resistance band" "Jump rope" "Dumbbell" "Barbell" "Kettlebell" "Chair"
   "Weight bench" "Exercise mat" "Pull-up bar" "Cable machine" "Exercise ball" "Foam roller"])

(def ^:private intensity-ids (set (map :id intensity-options)))
(def ^:private demo-ids
  #{"chest_fly" "unilateral_fly" "incline_press" "decline_press" "chest_training"
    "floor_press" "seated_chest_press" "resisted_pushup" "seated_row" "bent_over_row"
    "straight_arm_pulldown" "high_row" "reverse_fly" "biceps_curl"
    "triceps_pushdown_mislabel" "lateral_raise" "front_raise" "upright_row"
    "kneeling_crunch" "woodchopper" "hip_hinge" "squat" "lunge" "hip_flexion"
    "hip_extension"})
(def ^:private muscles-by-id (into {} (map (juxt :id identity) domain/muscle-dictionary)))

(defn- values-of [values]
  (cond (string? values) [values]
        (or (sequential? values) (set? values)) values
        :else []))

(defn- clean-strings [values]
  (vec (distinct (remove str/blank? (map str/trim (filter string? (values-of values)))))))

(defn normalize-equipment
  "Actual equipment wins if a legacy value also says no equipment. Never assigns the filter sentinel."
  [values]
  (let [tags (vec (remove #{"Unset"} (clean-strings values)))
        actual (vec (remove #{"No equipment"} tags))]
    (if (seq actual) actual tags)))

(defn toggle-equipment [current name selected?]
  (let [tags (normalize-equipment current)
        tag (when (string? name) (str/trim name))]
    (cond
      (or (str/blank? tag) (= "Unset" tag)) tags
      (not selected?) (vec (remove #{tag} tags))
      (= "No equipment" tag) ["No equipment"]
      :else (normalize-equipment (conj (vec (remove #{"No equipment"} tags)) tag)))))

(defn- intensity-from-met [met]
  (let [value (if (and (number? met) (<= 0 met) (< met 1000000)) met 3.5)]
    (cond (< value 3) "low" (< value 6) "medium" :else "high")))

(defn- original-demo? [{:keys [id media-path]}]
  (and (contains? demo-ids id) (= media-path (str "assets/demo/" id ".gif"))))

(defn normalize-exercise [exercise]
  (if-not (map? exercise) exercise
    (assoc exercise
           :intensity (if (contains? intensity-ids (:intensity exercise))
                        (:intensity exercise) (intensity-from-met (:met exercise)))
           :equipment (if (contains? exercise :equipment)
                        (normalize-equipment (:equipment exercise))
                        (if (original-demo? exercise)
                          (cond-> ["Resistance band"] (= "seated_chest_press" (:id exercise)) (conj "Chair"))
                          [])))))

(defn migrate-document [document]
  (if (and (map? document) (sequential? (:exercises document)))
    (update document :exercises #(mapv normalize-exercise %))
    document))

(defn available-equipment
  "Assigned tags, sorted; append the filtering-only unset option iff any exercise has no tags."
  [exercises]
  (let [tag-groups (map #(normalize-equipment (:equipment %)) exercises)
        assigned (vec (sort (distinct (mapcat identity tag-groups))))]
    (cond-> assigned (some empty? tag-groups) (conj "Unset"))))

(defn- muscle-membership [exercise]
  (set (concat (values-of (:primary exercise)) (values-of (:secondary exercise)))))

(defn- searchable-text [exercise]
  (let [name (:name exercise)
        names (concat (if (map? name) [(:en name) (:zh name) (get name "en") (get name "zh")] [name])
                      [(:name-en exercise)])
        muscles (mapcat (fn [id]
                          (let [muscle (get muscles-by-id id)] [(:en muscle) (:zh muscle)]))
                        (muscle-membership exercise))]
    (str/lower-case
     (str/join " " (filter string? (concat names muscles (normalize-equipment (:equipment exercise))))))))

(defn- intersects? [selected values]
  (or (empty? selected) (boolean (some (set values) selected))))

(defn filter-exercises [exercises {:keys [query muscles intensities equipment]}]
  (let [query (if (string? query) (str/lower-case (str/trim query)) "")
        muscles (clean-strings muscles)
        intensities (clean-strings intensities)
        equipment (clean-strings equipment)]
    (vec
     (filter (fn [original]
               (let [exercise (normalize-exercise original)
                     tags (:equipment exercise)
                     equipment-values (if (seq tags) tags ["Unset"])]
                 (and (map? exercise)
                      (or (str/blank? query) (str/includes? (searchable-text exercise) query))
                      (intersects? muscles (muscle-membership exercise))
                      (intersects? intensities [(:intensity exercise)])
                      (intersects? equipment equipment-values))))
             exercises))))
