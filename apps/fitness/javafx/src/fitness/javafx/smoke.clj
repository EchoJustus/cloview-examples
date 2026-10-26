(ns fitness.javafx.smoke
  "Off-screen JavaFX smoke of the fitness shell (bb smoke:fitness:javafx, harness step 02).

  Mounts the shared fitness view with the JavaFX native renderers in a hidden stage,
  drives the real controls (filter chips, Clear all, a body-map click at a muscle's
  anchor, a calendar cell) and checks the resulting state, the visible text and that
  selections do not recompute derivations. Writes JSON (default
  verify/out/fitness-jvm-smoke.json); exit code 0 when every critical check passed."
  (:require [cljfx.api :as fx]
            [clojure.string :as str]
            [cloview.core.store :as st]
            [cloview.javafx.app :as jvm]
            [cloview.verify.report :as report]
            [fitness.app :as app]
            [fitness.body :as body]
            [fitness.demo :as demo]
            [fitness.derive :as derive]
            [fitness.javafx.native :as natives]
            [fitness.native :as native]
            [fitness.views :as views])
  (:import [javafx.application Platform]
           [javafx.event Event]
           [javafx.geometry Point2D]
           [javafx.scene Node Parent]
           [javafx.scene.canvas Canvas]
           [javafx.scene.control Button ButtonBase Label ScrollPane SplitPane Tab TabPane ToggleButton]
           [javafx.scene.input MouseButton MouseEvent]
           [javafx.stage Stage]))

(set! *warn-on-reflection* true)

(def ^:private timeout-ms 20000)

(defn- on-fx [f]
  (if (Platform/isFxApplicationThread)
    (f)
    (let [p (promise)]
      (Platform/runLater (fn [] (deliver p (try [nil (f)] (catch Throwable e [e nil])))))
      (let [r (deref p timeout-ms ::timeout)]
        (when (= r ::timeout) (throw (ex-info "FX thread timeout" {})))
        (let [[e v] r] (if e (throw e) v))))))

(defn- settle! [] (dotimes [_ 6] (on-fx (fn [] nil))))

(defn- children-of
  "Child nodes, including control content that may not be in the scene graph while
  the stage is hidden (scroll pane content, every tab's content, split pane items)."
  [^Node n]
  (cond-> []
    (instance? ScrollPane n) (conj (.getContent ^ScrollPane n))
    (instance? TabPane n) (into (keep #(.getContent ^Tab %)) (.getTabs ^TabPane n))
    (instance? SplitPane n) (into (.getItems ^SplitPane n))
    (instance? Parent n) (into (.getChildrenUnmodifiable ^Parent n))))

(defn- all-nodes [^Stage stage]
  (let [seen (java.util.IdentityHashMap.)]
    (letfn [(walk [^Node n]
              (when (and n (not (.containsKey seen n)))
                (.put seen n true)
                (cons n (mapcat walk (children-of n)))))]
      (walk (.getRoot (.getScene stage))))))

(defn- lookup
  "Node whose id (cloview :test-id) equals `id`. A tree walk rather than Node/lookup,
  whose CSS selector syntax does not accept ids such as \"calendar/2026-10-12\"."
  ^Node [^Stage stage id]
  (some (fn [^Node n] (when (= id (.getId n)) n)) (all-nodes stage)))

(defn- text-of [^Stage stage id]
  (on-fx #(let [n (lookup stage id)] (when (instance? Label n) (.getText ^Label n)))))

(defn- press! [^Stage stage id]
  (on-fx #(let [n (lookup stage id)]
            (when-not (instance? ButtonBase n) (throw (ex-info (str "no button " id) {})))
            (.fire ^ButtonBase n)))
  (settle!))

(defn- click-canvas!
  "Fire a click that did not drag at canvas-local (x, y). Without a source the event's
  coordinates are scene coordinates; dispatch converts them back to canvas-local."
  [^Stage stage id x y]
  (on-fx #(let [^Canvas c (lookup stage id)
                ^Point2D p (.localToScene c (double x) (double y))]
            (Event/fireEvent c (MouseEvent. MouseEvent/MOUSE_CLICKED (.getX p) (.getY p) 0.0 0.0
                                            MouseButton/PRIMARY 1
                                            false false false false
                                            true false false
                                            false false true nil))))
  (settle!))

(defn- mount! [store errors]
  (let [stage-p (promise)
        {:keys [mounted]} (jvm/start! {:store store
                                       :view views/root
                                       :title "fitness smoke"
                                       :width 1280 :height 900
                                       :showing false
                                       :install-effects? false
                                       :error-handler (fn [^Throwable e]
                                                        (swap! errors conj (str e))
                                                        (.printStackTrace e))
                                       :wrap-desc (fn [desc]
                                                    {:fx/type fx/ext-on-instance-lifecycle
                                                     :on-created #(deliver stage-p %)
                                                     :desc desc})})]
    (deref mounted timeout-ms nil)
    (settle!)
    (or (deref stage-p timeout-ms nil)
        (throw (ex-info "fitness stage was not created" {})))))

(defn- checks! [rep]
  (let [events (atom [])
        errors (atom [])
        store (st/create-store {:db (app/initial-db demo/document "2026-10-25")
                                :on-event (fn [e _] (swap! events conj e))})
        _ (st/reg-cofx :fitness/today (fn [cofx] (assoc cofx :today "2026-10-25")))
        _ (native/register-renderers! (natives/renderers {:dispatch! #(st/dispatch! store %)}))
        ^Stage stage (mount! store errors)]
    ;; shell
    (report/check! rep "shell/five-tabs" 5 (on-fx #(.size (.getTabs ^TabPane (lookup stage "fitness-tabs")))))
    (report/check! rep "catalog/count-initial" "4 of 4 exercises" (text-of stage "catalog-count"))
    ;; multi-select chips (UI-003)
    (report/check! rep "catalog/chip-is-toggle-button" true
                   (on-fx #(instance? ToggleButton (lookup stage "filter-muscles/triceps-brachii"))))
    (press! stage "filter-muscles/triceps-brachii")
    (report/check! rep "catalog/chip-toggles-filter" #{"triceps-brachii"}
                   (get-in (st/db store) [:catalog :filters :muscles]))
    (report/check! rep "catalog/count-filtered" "2 of 4 exercises" (text-of stage "catalog-count"))
    (report/check! rep "catalog/chip-shows-state" true
                   (on-fx #(.isSelected ^ToggleButton (lookup stage "filter-muscles/triceps-brachii"))))
    (press! stage "catalog-clear")
    (report/check! rep "catalog/clear-all" "4 of 4 exercises" (text-of stage "catalog-count"))
    (report/check! rep "catalog/chip-unchecked-after-clear" false
                   (on-fx #(.isSelected ^ToggleButton (lookup stage "filter-muscles/triceps-brachii"))))
    ;; analytics natives (EXT-003, EXT-005)
    (st/dispatch! store [:nav/tab :analytics])
    (settle!)
    (doseq [[id check] [["body-map-canvas" "analytics/body-canvas-mounted"]
                        ["radar-canvas" "analytics/radar-canvas-mounted"]
                        ["calorie-canvas" "analytics/calorie-canvas-mounted"]]]
      (report/check! rep check true (on-fx #(instance? Canvas (lookup stage id)))))
    (let [before @derive/calls
          ^Canvas c (on-fx #(lookup stage "body-map-canvas"))
          lay (body/layout (on-fx #(.getWidth c)) (on-fx #(.getHeight c)))
          [x y] (body/to-canvas lay :front [40 51])]
      (click-canvas! stage "body-map-canvas" x y)
      (report/check! rep "body/click-selects-muscle" "pectoralis-major"
                     (get-in (st/db store) [:analytics :selected-muscle]))
      (report/check! rep "body/selection-text" true
                     (boolean (some-> (text-of stage "muscle-selection") (str/starts-with? "Pectoralis major"))))
      (click-canvas! stage "body-map-canvas" 2 2)
      (report/check! rep "body/background-click-emits-nothing" "pectoralis-major"
                     (get-in (st/db store) [:analytics :selected-muscle]))
      (report/check! rep "analytics/selection-recomputes-nothing" before @derive/calls
                     {:note "ARCH-004: derivations are memoized on db slices, not on the db"}))
    ;; calendar composite (DOM-009)
    (st/dispatch! store [:nav/tab :calendar])
    (settle!)
    (report/check! rep "calendar/cell-is-button" true (on-fx #(instance? Button (lookup stage "calendar/2026-10-12"))))
    (press! stage "calendar/2026-10-12")
    (report/check! rep "calendar/select-date" "2026-10-12" (get-in (st/db store) [:calendar :selected]))
    (report/check! rep "calendar/details" true
                   (boolean (some #(= "Monday, October 12" %)
                                  (on-fx #(doall (keep (fn [^Node n] (when (instance? Label n) (.getText ^Label n)))
                                                       (all-nodes stage)))))))
    (press! stage "calendar/prev")
    (report/check! rep "calendar/previous-month" "2026-09" (get-in (st/db store) [:calendar :month]))
    ;; events and errors
    (report/check! rep "events/logical-sequence"
                   [[:catalog/filter-toggle :muscles "triceps-brachii" true] [:catalog/clear]
                    [:nav/tab :analytics] [:body/select-muscle "pectoralis-major"]
                    [:nav/tab :calendar] [:calendar/select-date "2026-10-12"]
                    [:calendar/change-month "2026-09"]]
                   @events
                   {:note "one event per user action; programmatic writes never echo"})
    (report/check! rep "render-errors" [] @errors)
    (on-fx #(.close stage))))

(defn run-smoke! [out]
  (app/register!)
  (let [started (report/now-ms)
        rep (report/new-report "fitness-jvm-smoke" "jvm/javafx")]
    (swap! rep assoc :env (report/host-info))
    (try
      (checks! rep)
      (catch Throwable e (report/error! rep "fitness" e) (.printStackTrace e)))
    (let [final (report/finish rep started (report/now-ms))]
      (report/write-json! out final)
      (println (str "fitness jvm smoke: " (if (:ok final) "OK" "FAILED")
                    " (" (:checks_passed final) "/" (:checks_total final) " checks) -> " out))
      (if (:ok final) 0 1))))

(defn -main [& [out]]
  (let [code (run-smoke! (or out "verify/out/fitness-jvm-smoke.json"))]
    (shutdown-agents)
    (Platform/exit)
    (System/exit code)))
