(ns fitness.body
  "Shared 2D anatomy geometry and hit testing for the :fitness/body-map native view.

  Extracted from benchmarks/fitness-baseline/reference-ui/lib/muscle_view.dart
  (_FlatAnatomy / _FlatBodyPainter) — see docs/NATIVE_CONTRACTS.md §3. Every host draws
  the same polygons and resolves taps with the same `pick`, so a tap at a region's
  anchor selects the same muscle id everywhere (EXT-003).

  Body space: 100 x 280 units, y down. Regions are authored on the left half and
  mirrored with x -> 100 - x; the paint order is list order (left polygon, then its
  mirror) and later regions sit on top. Hit tests scan the reverse paint order.")

(def regions
  "{:front [[id polygon anchor] ...] :back [...]} in body units."
  {:front
   [["pectoralis-major" [[49 42] [36 39] [30 45] [34 58] [49 61]] [40 51]]
    ["anterior-deltoid" [[30 39] [23 41] [21 51] [26 60] [31 50]] [27 48]]
    ["lateral-deltoid" [[23 42] [19 46] [18 57] [21 63] [23 53]] [20 52]]
    ["biceps-brachii" [[24 60] [20 64] [17 81] [21 86] [26 68]] [22 72]]
    ["obliques" [[34 64] [41 70] [40 105] [35 114] [35 95]] [38 91]]
    ["rectus-abdominis" [[43 65] [49 65] [49 75] [42 75]] [46 70]]
    ["rectus-abdominis" [[43 78] [49 78] [49 88] [42 88]] [46 83]]
    ["rectus-abdominis" [[43 91] [49 91] [49 101] [42 101]] [46 96]]
    ["iliopsoas" [[37 112] [43 113] [49 132] [42 126]] [42 119]]
    ["quadriceps" [[32 141] [45 146] [43 177] [38 193] [31 181] [29 159]] [33 166]]
    ["rectus-femoris" [[37 145] [41 147] [40 178] [37 185] [35 177]] [38 163]]]
   :back
   [["trapezius" [[49 30] [43 34] [31 40] [40 49] [49 62]] [45 43]]
    ["posterior-deltoid" [[29 39] [22 41] [19 49] [23 59] [32 50]] [26 48]]
    ["triceps-brachii" [[23 60] [20 64] [16 82] [21 86] [27 66]] [22 72]]
    ["rhomboids" [[49 64] [41 52] [38 62] [44 77] [49 73]] [44 65]]
    ["latissimus-dorsi" [[32 60] [39 64] [43 82] [43 105] [35 94]] [37 82]]
    ["erector-spinae" [[46 78] [49 76] [49 115] [44 111]] [47 96]]
    ["gluteus-medius" [[34 111] [40 115] [37 128] [30 128]] [34 119]]
    ["gluteus-maximus" [[40 117] [49 119] [48 141] [35 144] [31 136]] [41 132]]
    ["hamstrings" [[32 147] [45 148] [43 180] [39 194] [32 187] [29 165]] [37 167]]
    ["gastrocnemius" [[32 204] [41 205] [43 219] [39 238] [33 232] [30 217]] [37 219]]
    ["soleus" [[33 234] [39 240] [40 252] [34 254]] [37 246]]]})

(def silhouette
  "Non-selectable outline: path commands in body units (:M :L :Q control+end, :C two
  controls+end, :Z, :oval [x y w h], :rrect [x y w h r]). shell = torso + arm +
  mirror(arm) + leg + mirror(leg)."
  {:torso [[:oval 40 3 20 26] [:rrect 44 24 12 14 3] [:M 44 32] [:Q 34 34 28 40] [:Q 27 53 32 69]
           [:L 35 103] [:Q 28 121 31 141] [:Q 50 151 69 141] [:Q 72 121 65 103] [:L 68 69]
           [:Q 73 53 72 40] [:Q 66 34 56 32] [:Z]]
   :arm [[:M 29 38] [:C 21 37 18 45 18 54] [:L 14 80] [:Q 12 85 14 90] [:L 8 121] [:L 5 135]
         [:Q 3 144 8 147] [:Q 12 146 13 137] [:L 15 130] [:Q 20 112 22 94] [:L 25 85]
         [:Q 29 68 32 56] [:Q 36 44 29 38] [:Z]]
   :leg [[:M 31 132] [:Q 25 157 29 184] [:L 30 202] [:Q 27 220 32 240] [:L 33 258] [:L 29 267]
         [:Q 27 273 33 273] [:L 42 273] [:Q 46 270 43 264] [:L 42 255] [:L 45 216] [:L 44 200]
         [:L 49 163] [:L 49 143] [:Z]]})

(def palette
  {:silhouette "#DDE2DE" :zero "#CAD2CC" :low "#B1D8C5" :high "#217B67"
   :outline "#F6F5EF" :selected "#E98D4F" :ink "#243B36" :muted "#74817A" :teal "#258675"})

(defn mirror [[x y]] [(- 100 x) y])

(defn mirror-commands
  "Silhouette path commands mirrored with x -> 100 - x."
  [commands]
  (mapv (fn [[op & args]]
          (case op
            (:M :L :Q :C) (into [op] (mapcat (fn [[x y]] [(- 100 x) y]) (partition 2 args)))
            :oval (let [[x y w h] args] [op (- 100 x w) y w h])
            :rrect (let [[x y w h r] args] [op (- 100 x w) y w h r])
            [op]))
        commands))

(def polygons
  "Paint list: [{:id :figure :points [[x y] ...] :anchor [x y]} ...] — for each region its
  left polygon, then the mirror."
  (vec (for [figure [:front :back]
             [id points anchor] (get regions figure)
             side [:left :right]]
         {:id id :figure figure
          :points (if (= side :left) points (mapv mirror points))
          :anchor (if (= side :left) anchor (mirror anchor))})))

(defn layout
  "Placement of both figures in a w x h canvas (logical px):
  {:scale s :front [ox oy] :back [ox oy] :captions {:front [x y] :back [x y]}}."
  [w h]
  (let [s (max 0.01 (min (/ (- h 28.0) 280.0) (/ w 245.0)))]
    {:scale s
     :front [(- (* 0.25 w) (* 50.0 s)) 7.0]
     :back [(- (* 0.75 w) (* 50.0 s)) 7.0]
     :captions {:front [(* 0.25 w) (- h 9.0)] :back [(* 0.75 w) (- h 9.0)]}}))

(defn to-canvas
  "Body point -> canvas point for `figure` under `lay`."
  [lay figure [x y]]
  (let [[ox oy] (get lay figure) s (:scale lay)]
    [(+ ox (* x s)) (+ oy (* y s))]))

(defn- winding
  "Non-zero winding number of point (px, py) for a closed polygon."
  [px py points]
  (let [n (count points)]
    (loop [i 0 wn 0]
      (if (= i n)
        wn
        (let [[x1 y1] (nth points i)
              [x2 y2] (nth points (mod (inc i) n))
              is-left (- (* (- x2 x1) (- py y1)) (* (- px x1) (- y2 y1)))]
          (recur (inc i)
                 (cond
                   (and (<= y1 py) (> y2 py) (pos? is-left)) (inc wn)
                   (and (> y1 py) (<= y2 py) (neg? is-left)) (dec wn)
                   :else wn)))))))

(defn inside? [[px py] points] (not (zero? (winding px py points))))

(defn pick
  "Muscle id under canvas point `p` of a w x h body map, or nil (silhouette and
  background select nothing)."
  [w h p]
  (let [lay (layout w h)
        s (:scale lay)
        [px py] p]
    (some (fn [figure]
            (let [[ox oy] (get lay figure)
                  q [(/ (- px ox) s) (/ (- py oy) s)]]
              (some (fn [{:keys [id points]}] (when (inside? q points) id))
                    (rseq (filterv #(= figure (:figure %)) polygons)))))
          [:front :back])))

(defn- lerp-channel [a b t] (+ a (* t (- b a))))

(def ^:private hex-digit
  {"0" 0 "1" 1 "2" 2 "3" 3 "4" 4 "5" 5 "6" 6 "7" 7 "8" 8 "9" 9
   "a" 10 "b" 11 "c" 12 "d" 13 "e" 14 "f" 15 "A" 10 "B" 11 "C" 12 "D" 13 "E" 14 "F" 15})

(defn- rgb [hex]
  (mapv (fn [i] (+ (* 16 (get hex-digit (subs hex i (inc i)))) (get hex-digit (subs hex (inc i) (+ i 2)))))
        [1 3 5]))

(defn- hex2 [n]
  (let [digits "0123456789ABCDEF"
        n (max 0 (min 255 n))]
    (str (subs digits (quot n 16) (inc (quot n 16))) (subs digits (mod n 16) (inc (mod n 16))))))

(defn level-color
  "Fill of a 2D region with relative load `level` in [0, 1] (\"#RRGGBB\")."
  [level]
  (if (or (nil? level) (<= level 0.0))
    (:zero palette)
    (let [t (min 1.0 (double level))
          a (rgb (:low palette)) b (rgb (:high palette))]
      (str "#" (apply str (map (fn [x y] (hex2 (long (+ 0.5 (lerp-channel x y t))))) a b))))))
