(ns fitness.javafx.native
  "JavaFX renderers for the fitness native views (docs/NATIVE_CONTRACTS.md): one Canvas
  per view, repainted only when its :props change (cljfx calls :update only then).

  Input: a body-map click that did not drag (MouseEvent/isStillSincePress) resolves the
  muscle with the shared fitness.body/pick and dispatches the event vector found in the
  latest props under [:events :body/select-muscle] — exactly once, never from paint or
  update. The latest props live in the node's properties map."
  (:require [fitness.body :as body])
  (:import [javafx.event EventHandler]
           [javafx.geometry VPos]
           [javafx.scene.canvas Canvas GraphicsContext]
           [javafx.scene.input MouseEvent]
           [javafx.scene.paint Color]
           [javafx.scene.text Font FontWeight TextAlignment]))

(set! *warn-on-reflection* true)

(defn- color ^Color [hex] (Color/web ^String hex))

(defn- props-of [^Canvas c] (.get (.getProperties c) ::props))

(defn- resize! [^Canvas c {:keys [width height]}]
  (.setWidth c (double width))
  (.setHeight c (double height)))

(defn- set-a11y! [^Canvas c text]
  (.setAccessibleText c (str text)))

;; ---------------------------------------------------------------------------
;; Body map (2D)

(defn- trace! [^GraphicsContext g lay figure commands]
  (let [pt (fn [x y] (body/to-canvas lay figure [x y]))
        s (:scale lay)]
    (doseq [[op & args] commands]
      (case op
        :M (let [[x y] (apply pt args)] (.beginPath g) (.moveTo g x y))
        :L (let [[x y] (apply pt args)] (.lineTo g x y))
        :Q (let [[cx cy] (pt (nth args 0) (nth args 1)) [x y] (pt (nth args 2) (nth args 3))]
             (.quadraticCurveTo g cx cy x y))
        :C (let [[ax ay] (pt (nth args 0) (nth args 1)) [bx by] (pt (nth args 2) (nth args 3))
                 [x y] (pt (nth args 4) (nth args 5))]
             (.bezierCurveTo g ax ay bx by x y))
        :Z (do (.closePath g) (.fill g))
        :oval (let [[x y w h] args [px py] (pt x y)] (.fillOval g px py (* w s) (* h s)))
        :rrect (let [[x y w h r] args [px py] (pt x y)]
                 (.fillRoundRect g px py (* w s) (* h s) (* 2 r s) (* 2 r s)))
        nil))))

(defn- paint-body! [^Canvas c props]
  (let [g (.getGraphicsContext2D c)
        w (.getWidth c) h (.getHeight c)
        lay (body/layout w h)
        s (:scale lay)
        levels (into {} (map (juxt :id :level)) (:regions props))
        selected (:selected props)
        {:keys [torso arm leg]} body/silhouette]
    (.clearRect g 0 0 w h)
    (.setFill g (color (:silhouette body/palette)))
    (doseq [figure [:front :back]
            part [torso arm (body/mirror-commands arm) leg (body/mirror-commands leg)]]
      (trace! g lay figure part))
    (doseq [{:keys [id figure points]} body/polygons]
      (let [pts (map #(body/to-canvas lay figure %) points)
            xs (double-array (map first pts))
            ys (double-array (map second pts))
            sel? (= id selected)]
        (.setFill g (color (body/level-color (get levels id))))
        (.fillPolygon g xs ys (count pts))
        (.setStroke g (color (if sel? (:selected body/palette) (:outline body/palette))))
        (.setLineWidth g (* s (if sel? 1.2 0.5)))
        (.strokePolygon g xs ys (count pts))))
    (.setFill g (color (:muted body/palette)))
    (.setFont g (Font/font 12.0))
    (.setTextAlign g TextAlignment/CENTER)
    (.setTextBaseline g VPos/CENTER)
    (doseq [[figure [x y]] (:captions lay)]
      (.fillText g (str (get-in props [:labels figure] (name figure))) x y))))

(defn body-canvas
  "Canvas for :fitness/body-canvas; `dispatch!` receives the selection event."
  [dispatch!]
  (let [c (Canvas. 440.0 340.0)]
    (.setOnMouseClicked c (reify EventHandler
                            (handle [_ e]
                              (let [^MouseEvent e e]
                                (when (.isStillSincePress e)
                                  (let [props (props-of c)]
                                    (when-some [id (body/pick (.getWidth c) (.getHeight c) [(.getX e) (.getY e)])]
                                      (when-some [ev (get-in props [:events :body/select-muscle])]
                                        (dispatch! (conj ev id))))))))))
    c))

(defn- update-body! [^Canvas c props]
  (.put (.getProperties c) ::props props)
  (resize! c (:size props))
  (set-a11y! c (:a11y-label props))
  (paint-body! c props))

;; ---------------------------------------------------------------------------
;; Radar

(defn- update-radar! [^Canvas c props]
  (resize! c (:size props))
  (set-a11y! c (:a11y-label props))
  (let [g (.getGraphicsContext2D c)
        w (.getWidth c) h (.getHeight c)
        axes (:axes props)
        n (max 3 (count axes))
        r (max 4.0 (min (* 0.31 w) (* 0.34 h)))
        cx (/ w 2.0) cy (/ h 2.0)
        angle (fn [i] (+ (- (/ Math/PI 2)) (/ (* 2 Math/PI i) n)))
        at (fn [i k] [(+ cx (* k r (Math/cos (angle i)))) (+ cy (* k r (Math/sin (angle i))))])
        poly (fn [k-of] (let [pts (map-indexed (fn [i a] (at i (k-of a))) axes)]
                          [(double-array (map first pts)) (double-array (map second pts)) (count pts)]))]
    (.clearRect g 0 0 w h)
    (.setStroke g (color "#D9E1D7"))
    (.setLineWidth g 1.0)
    (doseq [k [0.25 0.5 0.75 1.0]]
      (let [[xs ys m] (poly (constantly k))] (.strokePolygon g xs ys m)))
    (doseq [i (range (count axes))]
      (let [[x y] (at i 1.0)] (.strokeLine g cx cy x y)))
    ;; previous period: dashed outline
    (let [[xs ys m] (poly :previous-level)]
      (.setStroke g (color "#BCCDC5"))
      (.setLineWidth g 1.5)
      (.setLineDashes g (double-array [5.0 4.0]))
      (.strokePolygon g xs ys m)
      (.setLineDashes g nil))
    ;; current period: filled polygon + dots
    (let [[^doubles xs ^doubles ys m] (poly :current-level)]
      (.setFill g (Color/web "#258675" 0.15))
      (.fillPolygon g xs ys m)
      (.setStroke g (color "#258675"))
      (.setLineWidth g 2.0)
      (.strokePolygon g xs ys m)
      (.setFill g (color "#258675"))
      (dotimes [i m] (.fillOval g (- (aget xs i) 3) (- (aget ys i) 3) 6.0 6.0)))
    (.setFill g (color "#243B36"))
    (.setFont g (Font/font 11.0))
    (.setTextAlign g TextAlignment/CENTER)
    (.setTextBaseline g VPos/CENTER)
    (doseq [[i a] (map-indexed vector axes)]
      (let [[x y] (at i 1.34)]
        (.fillText g (str (:label a)) (max 35.0 (min (- w 35.0) x)) (max 9.0 (min (- h 9.0) y)))))))

;; ---------------------------------------------------------------------------
;; Daily calories

(defn- update-calories! [^Canvas c props]
  (resize! c (:size props))
  (set-a11y! c (:a11y-label props))
  (let [g (.getGraphicsContext2D c)
        w (.getWidth c) h (.getHeight c)
        days (:days props)
        n (max 1 (count days))
        bottom (- h 30.0) top 21.0
        chart-h (- bottom top)
        cell (/ (- w 28.0) n)
        bar-w (min 30.0 (* 0.52 cell))
        scale (double (:scale-max props 1.0))]
    (.clearRect g 0 0 w h)
    (.setStroke g (color "#D9E1D7"))
    (.setLineWidth g 1.0)
    (doseq [k (range 5)]
      (let [y (- bottom (* k (/ chart-h 4)))] (.strokeLine g 14.0 y (- w 14.0) y)))
    (.setFont g (Font/font 10.0))
    (.setTextAlign g TextAlignment/CENTER)
    (.setTextBaseline g VPos/CENTER)
    (doseq [[i {:keys [label kcal status value-text date]}] (map-indexed vector days)]
      (let [x (+ 14.0 (* cell (+ i 0.5)))]
        (if (= status :unknown)
          (do (.setStroke g (color "#74817A"))
              (.setLineDashes g (double-array [3.0 3.0]))
              (.strokeRect g (- x (/ bar-w 2)) (- bottom 8.0) bar-w 8.0)
              (.setLineDashes g nil)
              (.setFill g (color "#243B36"))
              (.fillText g "?" x (- bottom 19.0)))
          (let [bh (* chart-h (/ (double (or kcal 0.0)) scale))]
            (.setFill g (color (if (= date (:highlight props)) "#E98D4F" "#258675")))
            (.fillRoundRect g (- x (/ bar-w 2)) (- bottom bh) bar-w bh 6.0 6.0)
            (.setFill g (color "#243B36"))
            (.fillText g (str value-text) x (- bottom bh 11.0))))
        (.setFill g (color "#74817A"))
        (.fillText g (str label) x (+ bottom 17.0))))))

;; ---------------------------------------------------------------------------

(defn renderers
  "{kind host-map} for fitness.native/register-renderers!."
  [{:keys [dispatch!]}]
  {:fitness/body-canvas {:create (fn [] (body-canvas dispatch!))
                         :update update-body!
                         :dispose (fn [_] nil)}
   :fitness/radar-canvas {:create (fn [] (Canvas. 300.0 260.0))
                          :update update-radar!
                          :dispose (fn [_] nil)}
   :fitness/calorie-canvas {:create (fn [] (Canvas. 320.0 200.0))
                            :update update-calories!
                            :dispose (fn [_] nil)}})
