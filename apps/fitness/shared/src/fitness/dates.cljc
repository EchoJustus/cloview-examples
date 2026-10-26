(ns fitness.dates
  "Local calendar dates as \"YYYY-MM-DD\" strings, months as \"YYYY-MM\" — pure integer
  arithmetic, identical on every host (no host date APIs, no time zones). The domain core
  never guesses a time zone; the host supplies today's local date (see `local-today`).")

(def ^:private digit
  {"0" 0 "1" 1 "2" 2 "3" 3 "4" 4 "5" 5 "6" 6 "7" 7 "8" 8 "9" 9})

(defn- num-of
  "Non-negative integer from a string of ASCII digits, or nil."
  [s]
  (when (and (string? s) (pos? (count s)))
    (reduce (fn [n i]
              (if-some [d (get digit (subs s i (inc i)))]
                (+ (* 10 n) d)
                (reduced nil)))
            0
            (range (count s)))))

(defn- pad [n width]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- width (count s))) "0")) s)))

(defn leap? [y] (and (zero? (mod y 4)) (or (not (zero? (mod y 100))) (zero? (mod y 400)))))

(defn month-days [y m]
  (get [0 31 (if (leap? y) 29 28) 31 30 31 30 31 31 30 31 30 31] m 0))

(defn parse
  "\"YYYY-MM-DD\" -> [y m d] for a valid date between 0000-01-01 and 9999-12-31, else nil."
  [s]
  (when (and (string? s) (= 10 (count s)) (= "-" (subs s 4 5)) (= "-" (subs s 7 8)))
    (let [y (num-of (subs s 0 4)) m (num-of (subs s 5 7)) d (num-of (subs s 8 10))]
      (when (and y m d (<= 1 m 12) (<= 1 d (month-days y m)))
        [y m d]))))

(defn valid? [s] (some? (parse s)))

(defn format-date [y m d] (str (pad y 4) "-" (pad m 2) "-" (pad d 2)))

;; days since 1970-01-01 (Howard Hinnant's civil-from-days / days-from-civil)

(defn ->days
  "Days since 1970-01-01 for a valid date string, else nil."
  [s]
  (when-some [[y m d] (parse s)]
    (let [y (if (<= m 2) (dec y) y)
          era (quot (if (>= y 0) y (- y 399)) 400)
          yoe (- y (* era 400))
          mp (mod (+ m 9) 12)
          doy (+ (quot (+ (* 153 mp) 2) 5) (dec d))
          doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)]
      (+ (* era 146097) doe -719468))))

(defn days->date
  "Date string for days since 1970-01-01."
  [z]
  (let [z (+ z 719468)
        era (quot (if (>= z 0) z (- z 146096)) 146097)
        doe (- z (* era 146097))
        yoe (quot (- doe (quot doe 1460) (- (quot doe 36524)) (quot doe 146096)) 365)
        y (+ yoe (* era 400))
        doy (- doe (- (+ (* 365 yoe) (quot yoe 4)) (quot yoe 100)))
        mp (quot (+ (* 5 doy) 2) 153)
        d (inc (- doy (quot (+ (* 153 mp) 2) 5)))
        m (if (< mp 10) (+ mp 3) (- mp 9))]
    (format-date (if (<= m 2) (inc y) y) m d)))

(defn plus-days
  "`s` moved by `n` days (nil for an invalid date)."
  [s n]
  (some-> (->days s) (+ n) days->date))

(defn days-between
  "Whole days from `a` to `b` (b - a), nil when either is invalid."
  [a b]
  (let [x (->days a) y (->days b)]
    (when (and x y) (- y x))))

(defn date-seq
  "Every date from `from` to `to` inclusive (empty when invalid or reversed)."
  [from to]
  (let [a (->days from) b (->days to)]
    (if (and a b (<= a b))
      (mapv days->date (range a (inc b)))
      [])))

(defn month-of [date] (when (valid? date) (subs date 0 7)))

(defn parse-month
  "\"YYYY-MM\" -> [y m] or nil."
  [s]
  (when (and (string? s) (= 7 (count s)) (= "-" (subs s 4 5)))
    (let [y (num-of (subs s 0 4)) m (num-of (subs s 5 7))]
      (when (and y m (<= 1 m 12)) [y m]))))

(defn plus-months
  "Month string moved by `n` months (nil when invalid)."
  [month n]
  (when-some [[y m] (parse-month month)]
    (let [idx (+ (* 12 y) (dec m) n)]
      (when (>= idx 0)
        (str (pad (quot idx 12) 4) "-" (pad (inc (mod idx 12)) 2))))))

(defn month-in-ui-range?
  "The benchmark UI supports 1900-01 .. 2100-12."
  [month]
  (boolean (when-some [[y _] (parse-month month)] (<= 1900 y 2100))))

(defn weekday-index
  "0 = Monday .. 6 = Sunday."
  [date]
  (when-some [z (->days date)]
    ;; 1970-01-01 was a Thursday (index 3)
    (mod (+ z 3) 7)))

(def month-names
  ["January" "February" "March" "April" "May" "June" "July" "August" "September"
   "October" "November" "December"])

(def weekday-names ["Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday" "Sunday"])

(defn long-title
  "\"Tuesday, October 13\" for a valid date."
  [date]
  (when-some [[_ m d] (parse date)]
    (str (get weekday-names (weekday-index date)) ", " (get month-names (dec m)) " " d)))

(defn month-title [month]
  (when-some [[y m] (parse-month month)] (str (get month-names (dec m)) " " y)))

(defn local-today
  "Today's local date on this host (the only host-dependent function here)."
  []
  #?(:cljd (let [n (DateTime/now)] (format-date (.-year n) (.-month n) (.-day n)))
     :clj (str (java.time.LocalDate/now))
     :cljr (.ToString System.DateTime/Now "yyyy-MM-dd" System.Globalization.CultureInfo/InvariantCulture)))
