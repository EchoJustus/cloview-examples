(ns fitness.native
  "Registry of host renderers for the fitness native views (docs/NATIVE_CONTRACTS.md).

  Shared views emit `(view kind {...})`; each host entry point registers its renderers
  once at start-up:

    JVM / WPF  {kind {:create (fn [] node) :update (fn [node props]) :dispose (fn [node])}}
    Flutter    {kind {:build (fn [build-context props] widget)}}

  A renderer receives only data (:props) and reports user input by dispatching the event
  vectors found in (:events props) with the payload appended. A kind without a renderer
  renders an explicit \"not available on this host\" text, never an empty box (ARCH-003).")

(def kinds
  "Native view kinds used by the fitness app."
  #{:fitness/body-canvas :fitness/radar-canvas :fitness/calorie-canvas :fitness/media-player})

(defonce ^:private renderers (atom {}))

(defn register-renderers!
  "Merge {kind host-map} into the registry (idempotent; later registrations win)."
  [m]
  (swap! renderers merge m)
  :registered)

(defn renderer
  "Host map registered for `kind`, or nil."
  [kind]
  (get @renderers kind))

(defn available-kinds [] (set (keys @renderers)))

(defn view
  "Hiccup for native `kind`: a :ui/native node with the registered host map, or an
  honest placeholder when this host has no renderer for it."
  [kind {:keys [key test-id props unavailable]}]
  (if-some [host (renderer kind)]
    [:ui/native {:key key :test-id test-id :props props :host host}]
    [:ui/box {:key key :test-id test-id
              :style {:padding 12 :border {:width 1 :color "#BCCDC5"} :radius 6}}
     [:ui/text {:wrap? true
                :text (or unavailable
                          (str "This view is not available on this host yet (" (name kind) ")."))}]]))
