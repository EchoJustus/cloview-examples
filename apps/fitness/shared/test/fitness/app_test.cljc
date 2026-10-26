(ns fitness.app-test
  "The shared fitness app on the headless reconciler (MockHostOps): every screen renders
  without tree problems, events change state as the benchmark specifies, derivations are
  not recomputed by unrelated changes. Runs on Clojure/JVM and ClojureCLR."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [cloview.core.reconcile :as r]
            [cloview.core.store :as st]
            [cloview.core.tree :as t]
            [clogem.fitness.domain]
            [fitness.app :as app]
            [fitness.demo :as demo]
            [fitness.derive :as derive]
            [fitness.hr-import :as hr]
            [fitness.io :as fio]
            [fitness.native :as native]
            [fitness.views :as views]))

(def today "2026-10-25")
(def t0 1792000000000)

(defn- fresh-store []
  (app/register!)
  (st/reg-cofx :fitness/today (fn [cofx] (assoc cofx :today today)))
  (let [clock (atom t0)]
    (st/reg-cofx :now (fn [cofx] (assoc cofx :now (swap! clock + 1000))))
    (st/create-store {:db (app/initial-db demo/document today)})))

(defn- render-tree [db] (t/normalize (views/root db)))

#?(:cljd nil
   :default
   (deftest demo-document-is-the-frozen-fixture
     (let [root (fio/find-repo-root)
           text (fio/read-text (str root "/benchmarks/fitness-baseline/fixtures/demo-document.edn"))]
       (is (= (edn/read-string text) demo/document)))))

(deftest every-screen-renders-without-tree-problems
  (let [store (fresh-store)]
    (doseq [tab [:catalog :plan :log :analytics :calendar]]
      (st/dispatch! store [:nav/tab tab])
      (let [tree (render-tree (st/db store))]
        (is (= [] (t/problems tree)) (str tab))
        (is (= tab (get-in (t/find-by-test-id tree "fitness-tabs") [:props :selected])))))
    (testing "the whole app mounts and re-renders through the reconciler"
      (let [mock (r/mock-host)
            rctx (r/create-reconciler (:ops mock) {:dispatch! #(st/dispatch! store %)})]
        (r/render! rctx (views/root (st/db store)))
        (is (pos? (count (r/op-log mock))))
        (r/clear-log! mock)
        (r/render! rctx (views/root (st/db store)))
        (is (= [] (r/op-log mock)) "an identical re-render costs no host operation")))))

(deftest natives-without-renderers-are-explicitly-unavailable
  (let [tree (render-tree (assoc-in (st/db (fresh-store)) [:ui :tab] :analytics))
        box (t/find-by-test-id tree "body-map-canvas")]
    (if (native/renderer :fitness/body-canvas)
      (is (= :ui/native (:tag box)))
      (do (is (= :ui/box (:tag box)))
          (is (str/includes? (get-in (first (:children box)) [:props :text]) "not available"))))))

(deftest media-preview-by-kind
  (let [db (-> (st/db (fresh-store))
               (assoc-in [:catalog :selected] "press"))
        with-media (fn [path] (update-in db [:doc :exercises]
                                         (fn [xs] (mapv #(if (= "press" (:id %)) (assoc % :media-path path) %) xs))))
        preview (fn [db] (first (:children (t/find-by-test-id (render-tree db) "media-preview"))))]
    (is (nil? (t/find-by-test-id (render-tree db) "media-preview")) "no media: no viewport")
    (testing "a GIF (any case) is a canonical :ui/image in the fixed 320x180 viewport"
      (let [img (preview (with-media "C:/app/media/press-1.GIF"))]
        (is (= :ui/image (:tag img)))
        (is (= {:file "C:/app/media/press-1.GIF"} (get-in img [:props :src])))
        (is (= :contain (get-in img [:props :fit])))))
    (testing "an MP4 goes through the :fitness/media-player contract"
      (let [v (preview (with-media "/data/media/press-2.mp4"))
            props (derive/media-props (derive/exercise-by-id (:doc (with-media "/data/media/press-2.mp4")) "press"))]
        (if (native/renderer :fitness/media-player)
          (is (= :ui/native (:tag v)))
          (is (str/includes? (get-in (first (:children v)) [:props :text]) "not available")
              "no Stage 2-2 host renders video: an honest placeholder, never an empty box"))
        (is (= {:kind :mp4 :path "/data/media/press-2.mp4"} (:source props)))
        (is (= {:width 320 :height 180} (:viewport props)))
        (is (= [:media/error "press"] (get-in props [:events :media/error])))))
    (testing "the media/error event only reports"
      (let [store (fresh-store)
            doc-before (:doc (st/db store))]
        (st/dispatch! store [:media/error "press" {:path "/x.mp4" :reason :missing}])
        (is (= doc-before (:doc (st/db store))))
        (is (str/includes? (get-in (st/db store) [:status :message]) "missing"))))))

(deftest catalog-filters
  (let [store (fresh-store)
        visible #(mapv :id (derive/visible-exercises (st/db store)))]
    (is (= ["press" "curl" "pushup" "mobility"] (visible)))
    (st/dispatch! store [:catalog/filter-toggle :muscles "triceps-brachii" true])
    (is (= ["press" "pushup"] (visible)) "secondary muscles satisfy the muscle filter")
    (st/dispatch! store [:catalog/filter-toggle :muscles "biceps-brachii" true])
    (is (= ["press" "curl" "pushup"] (visible)) "OR within a dimension")
    (st/dispatch! store [:catalog/filter-toggle :intensities "low" true])
    (is (= ["curl"] (visible)) "AND across dimensions")
    (st/dispatch! store [:catalog/clear-dimension :intensities])
    (is (= ["press" "curl" "pushup"] (visible)) "clearing one dimension keeps the others")
    (st/dispatch! store [:catalog/query-change "  DUMBBELL "])
    (is (= ["curl"] (visible)) "search is trimmed and case-insensitive")
    (st/dispatch! store [:catalog/clear])
    (is (= "" (get-in (st/db store) [:catalog :query])))
    (is (= ["press" "curl" "pushup" "mobility"] (visible)) "clear all clears search and every dimension")
    (testing "equipment options: assigned tags in ordinal order, then the filter-only Not set"
      (is (= ["Dumbbell" "No equipment" "Resistance band" "Weight bench" "Unset"]
             (mapv :id (derive/equipment-options (:doc (st/db store)))))))
    (testing "the chip groups show the filter state"
      (st/dispatch! store [:catalog/filter-toggle :equipment "Dumbbell" true])
      (let [chip (t/find-by-test-id (render-tree (st/db store)) "filter-equipment/Dumbbell")]
        (is (= :ui/toggle (:tag chip)))
        (is (true? (get-in chip [:props :checked])))
        (is (= [:catalog/filter-toggle :equipment "Dumbbell"] (get-in chip [:props :on-toggle])))))))

(deftest plan-items-and-training
  (let [store (fresh-store)
        items #(mapv :id (:items (first (get-in (st/db store) [:doc :plans]))))]
    (is (= ["press-a" "press-b" "curl-a"] (items)))
    (st/dispatch! store [:plan/item-move "plan-13" "curl-a" -1])
    (is (= ["press-a" "curl-a" "press-b"] (items)))
    (st/dispatch! store [:plan/item-copy "plan-13" "press-a"])
    (is (= 4 (count (items))))
    (is (= "press-a" (first (items))) "the copy is placed after its source")
    (is (str/starts-with? (second (items)) "press-a-"))
    (st/dispatch! store [:plan/item-remove "plan-13" (second (items))])
    (is (= ["press-a" "curl-a" "press-b"] (items)))
    (testing "start / pause / resume / complete, idempotent completion"
      (let [n (count (get-in (st/db store) [:doc :records]))]
        (st/dispatch! store [:training/start "plan-13" "curl-a"])
        (is (some? (get-in (st/db store) [:doc :active])))
        (st/dispatch! store [:training/start "plan-13" "press-a"])
        (is (= "curl-a" (get-in (st/db store) [:doc :active :item-id])) "only one active item")
        (st/dispatch! store [:training/pause])
        (st/dispatch! store [:training/resume])
        (st/dispatch! store [:training/complete])
        (st/dispatch! store [:training/complete])
        (let [records (get-in (st/db store) [:doc :records])
              rec (last records)]
          (is (= (inc n) (count records)) "a second completion adds nothing")
          (is (nil? (get-in (st/db store) [:doc :active])))
          (is (= today (:date rec)))
          (is (= "timer" (:duration-source rec)))
          (is (= 2 (count (:segments rec))) "the pause split the session")
          (is (= "curl" (get-in rec [:exercise :id])) "the exercise snapshot is captured"))))
    (testing "interrupted session (DOM-004): the open interval of unknown length is never counted"
      (st/dispatch! store [:training/start "plan-13" "press-a"])      ; open [t nil]
      (st/dispatch! store [:training/pause])                          ; closed, 1 s
      (st/dispatch! store [:training/resume])                         ; open again
      (let [saved (:doc (st/db store))]                               ; process stops here
        (st/dispatch! store [:doc/loaded saved]))                     ; ... and reloads
      (let [active (get-in (st/db store) [:doc :active])]
        (is (= 1 (count (:segments active))) "the open interval was dropped, the closed one kept")
        (is (= 1 (count (:interrupted-at active))))
        (is (str/includes? (get-in (t/find-by-test-id (render-tree (assoc-in (st/db store) [:ui :tab] :plan))
                                                      "active-status")
                                   [:props :text])
                           "interrupted")))
      (st/dispatch! store [:training/resume])                         ; a fresh interval
      (st/dispatch! store [:training/complete])
      (let [rec (last (get-in (st/db store) [:doc :records]))]
        (is (= "press-a" (:item-id rec)))
        (is (= 2.0 (clogem.fitness.domain/record-duration-sec rec))
            "1 s before the interruption + 1 s after it; the downtime is not active time")))
    (testing "backfill is marked as an estimate from the plan"
      (st/dispatch! store [:training/backfill "plan-13" "press-b"])
      (let [rec (last (get-in (st/db store) [:doc :records]))]
        (is (= "plan-estimate" (:duration-source rec)))
        (is (= 60000 (- (:completed-at rec) (:started-at rec))))))))

(deftest analytics-selection-does-not-recompute
  (let [store (fresh-store)
        db (st/db store)]
    (is (= {:from "2026-10-07" :to "2026-10-13"} (select-keys (:analytics db) [:from :to]))
        "the demo opens on the latest training week")
    (derive/body-props db)
    (derive/radar-props db)
    (derive/calorie-props db)
    (let [before @derive/calls]
      (st/dispatch! store [:body/select-muscle "pectoralis-major"])
      (st/dispatch! store [:body/style-change :three-d])
      (st/dispatch! store [:nav/tab :analytics])
      (let [db (st/db store)
            props (derive/body-props db)]
        (derive/radar-props db)
        (derive/calorie-props db)
        (is (= before @derive/calls) "selection, style and navigation reuse every derivation (ARCH-004)")
        (is (= "pectoralis-major" (:selected props)))
        (is (= 1.0 (:level (first (:regions props)))))
        (is (= [:body/select-muscle] (get-in props [:events :body/select-muscle])))))
    (st/dispatch! store [:body/select-muscle "not-a-muscle"])
    (is (nil? (get-in (st/db store) [:analytics :selected-muscle])) "unknown ids clear the selection")
    (st/dispatch! store [:analytics/metric "sets"])
    (is (= "sets" (:metric (derive/body-props (st/db store)))))
    (testing "zero previous totals never produce a percentage"
      (st/dispatch! store [:analytics/range "2026-10-13" "2026-10-13"])
      (st/dispatch! store [:analytics/range "2026-11-01" "2026-11-07"])
      (let [{:keys [comparison]} (derive/analysis (st/db store))]
        (is (nil? (get-in comparison [:sets :percent])))))
    (testing "daily energy: known, none and unknown are distinct"
      (st/dispatch! store [:analytics/range "2026-10-12" "2026-10-13"])
      (is (= [:known :known] (mapv :status (:days (derive/calorie-props (st/db store))))))
      (let [no-weight (update-in (st/db store) [:doc :profile] dissoc :weight-kg)]
        (is (= [:unknown :unknown] (mapv :status (:days (derive/calorie-props no-weight)))))
        (is (= ["?" "?"] (mapv :value-text (:days (derive/calorie-props no-weight)))))))))

(deftest calendar-navigation
  (let [store (fresh-store)]
    (is (= "2026-10" (get-in (st/db store) [:calendar :month])))
    (let [tree (render-tree (assoc-in (st/db store) [:ui :tab] :calendar))
          cell (t/find-by-test-id tree "calendar/2026-10-13")]
      (is (= [:calendar/select-date "2026-10-13"] (get-in cell [:props :on-press])))
      (is (str/includes? (get-in cell [:props :label]) "kcal"))
      (is (= [:calendar/change-month "2026-09"] (get-in (t/find-by-test-id tree "calendar/prev") [:props :on-press]))))
    (st/dispatch! store [:calendar/select-date "2026-09-28"])
    (is (= "2026-09-28" (get-in (st/db store) [:calendar :selected])) "adjacent-month cells select their date")
    (st/dispatch! store [:calendar/change-month "1899-12"])
    (is (= "2026-10" (get-in (st/db store) [:calendar :month])) "outside 1900..2100 nothing changes")
    (st/dispatch! store [:calendar/go-today])
    (is (= today (get-in (st/db store) [:calendar :selected])))
    (st/dispatch! store [:analytics/open-date "2026-10-13"])
    (is (= [:analytics "2026-10-13" "2026-10-13"]
           [(get-in (st/db store) [:ui :tab]) (get-in (st/db store) [:analytics :from]) (get-in (st/db store) [:analytics :to])]))))

(def hr-csv
  ;; the fixture's two explicit UTC samples, a duplicate, an invalid bpm, a zone-less time
  (str "timestamp,bpm,end_time\n"
       "2026-10-13T12:00:00Z,100,2026-10-13T12:01:00Z\n"
       "2026-10-13T12:01:30Z,120,2026-10-13T12:02:00Z\n"
       "2026-10-13T12:01:30Z,121,\n"
       "2026-10-13T12:03:00Z,300,\n"
       "2026-10-13 20:04:00,90,\n"
       "12:05,95,\n"))

(deftest heart-rate-import-preview-and-commit
  (let [p (hr/preview hr-csv {})]
    (is (= :csv (:format p)))
    (is (= [6 2 3 1] [(:rows p) (:valid p) (:invalid p) (:duplicates p)]))
    (is (= #{:invalid-bpm :needs-offset :incomplete-time}
           (set (map :reason (:errors p)))))
    (is (= 121 (:bpm (second (:samples p)))) "the last valid duplicate wins"))
  (let [p (hr/preview hr-csv {:offset-min 480})]
    (is (= 3 (:valid p)) "with an explicit offset the zone-less row is accepted")
    (is (= 1791893040000 (:timestamp (last (:samples p)))) "20:04 at +08:00 is 12:04Z")
    (is (= 1 (:offset-rows p))))
  (testing "explicit offsets and epoch values ignore the offset field"
    (is (= 1791892800000 (:ms (hr/parse-instant "2026-10-13T20:00:00+08:00" -300))))
    (is (= 1791892800000 (:ms (hr/parse-instant "1791892800" nil))) "epoch seconds")
    (is (= 1791892800000 (:ms (hr/parse-instant 1791892800000 nil))) "epoch milliseconds"))
  (testing "JSON input"
    (let [p (hr/preview "[{\"timestamp\":\"2026-10-13T12:00:00Z\",\"bpm\":100}]" {})]
      (is (= [:json 1] [(:format p) (:valid p)]))))
  (testing "a cancelled or unsupported file chooser re-enables the import button"
    (let [store (fresh-store)]
      (st/dispatch! store [:hr/pick-cancelled])
      (is (= :idle (get-in (st/db store) [:hr-import :status])))
      (st/dispatch! store [:pick/failed :hr {:error :unsupported :effect :file/pick}])
      (is (= :idle (get-in (st/db store) [:hr-import :status])))
      (is (= "Choosing files is not available on this host yet" (get-in (st/db store) [:status :message]))
          "ARCH-003: unsupported is said, not shown as a user cancel")))
  (testing "EFFECT-003/004: no commit during active training; a stale read is dropped"
    (let [store (fresh-store)]
      (st/dispatch! store [:hr/read "synthetic.csv" hr-csv])
      (st/dispatch! store [:training/start "plan-13" "press-a"])
      (st/dispatch! store [:hr/confirm])
      (is (= :preview (get-in (st/db store) [:hr-import :status])) "the preview is kept")
      (is (zero? (count (get-in (st/db store) [:doc :heart-rate-batches]))) "nothing was merged")
      (st/dispatch! store [:training/complete])
      (st/dispatch! store [:hr/confirm])
      (is (= :committed (get-in (st/db store) [:hr-import :status])) "confirmed once the item is done")
      (st/reg-fx :file/read (fn [_ _] nil))          ; the read result is delivered by hand below
      (st/dispatch! store [:hr/picked ["b.csv"]])
      (st/dispatch! store [:hr/read "a.csv" hr-csv])
      (is (= :reading (get-in (st/db store) [:hr-import :status])) "the late result of a.csv is ignored")
      (st/dispatch! store [:hr/read "b.csv" hr-csv])
      (is (= ["b.csv" :preview] [(get-in (st/db store) [:hr-import :source]) (get-in (st/db store) [:hr-import :status])]))))
  (testing "cancel leaves the stored samples unchanged; confirm merges them"
    (let [store (fresh-store)
          before (get-in (st/db store) [:doc :heart-rate])]
      (st/dispatch! store [:hr/read "synthetic.csv" hr-csv])
      (st/dispatch! store [:hr/cancel])
      (is (= before (get-in (st/db store) [:doc :heart-rate])))
      (st/dispatch! store [:hr/read "synthetic.csv" hr-csv])
      (st/dispatch! store [:hr/offset-change "480"])
      (is (= 3 (get-in (st/db store) [:hr-import :preview :valid])))
      (st/dispatch! store [:hr/confirm])
      (is (= :committed (get-in (st/db store) [:hr-import :status])))
      (is (= 3 (count (get-in (st/db store) [:doc :heart-rate]))) "2 replaced at equal timestamps + 1 new")
      (is (= 1 (count (get-in (st/db store) [:doc :heart-rate-batches])))))))
