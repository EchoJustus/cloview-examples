(ns fitness.javafx.main
  "Fitness on JavaFX.  Run: bb run:fitness:javafx  (or clj -M:run in apps/fitness/javafx)."
  (:require [cloview.core.store :as st]
            [cloview.javafx.app :as jvm]
            [fitness.app :as app]
            [fitness.javafx.native :as natives]
            [fitness.native :as native]
            [fitness.views :as views]))

(defn start!
  "Register, install the JavaFX renderers and mount the app. Options are passed to
  cloview.javafx.app/start! (e.g. :showing false, :install-effects? false for smokes)."
  [opts]
  (app/register!)
  (native/register-renderers! (natives/renderers {:dispatch! #(st/dispatch! app/store %)}))
  (let [effects? (:install-effects? opts true)
        result (jvm/start! (merge {:store app/store
                                   :view views/root
                                   :title "cloview fitness — JavaFX"
                                   :width 1280 :height 860
                                   :app-id "fitness"}
                                  opts))]
    (when effects?
      ;; persistence needs the :storage/* effects; load the saved document if any
      (st/dispatch! app/store [:settings/persist true])
      (st/dispatch! app/store [:doc/load]))
    result))

(defn -main [& _]
  (start! {:exit-on-close? true}))
