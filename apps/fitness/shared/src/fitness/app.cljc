(ns fitness.app
  "The fitness example's application layer: initial state, store and registration.

  Every host entry point does the same three things (see apps/fitness/*/):
    (app/register!)                                   ; handlers, coeffects, composites
    (native/register-renderers! host-renderers)       ; the host's drawing surfaces
    (<host>/start! {:store app/store :view views/root ...})"
  (:require [clogem.fitness.catalog :as c]
            [cloview.core.components :as comps]
            [cloview.core.store :as st]
            [fitness.dates :as dates]
            [fitness.demo :as demo]
            [fitness.events :as events]))

(defn initial-db
  "State for `document` with `today` as the local date (\"YYYY-MM-DD\")."
  ([] (initial-db demo/document (dates/local-today)))
  ([document today]
   (let [doc (c/migrate-document document)
         [from to] (events/latest-training-week doc today)
         latest-month (dates/month-of to)]
     {:doc doc
      :today today
      :ui {:tab :catalog :split 0.55}
      :catalog {:query "" :filters {:muscles #{} :intensities #{} :equipment #{}} :selected nil}
      :plan {:selected (:id (first (:plans doc)))}
      :analytics {:from from :to to :preset :latest-week :metric "weighted-minutes" :mode "actual"
                  :body-style :two-d :selected-muscle nil}
      :calendar {:month (or latest-month (dates/month-of today)) :selected to}
      :hr-import {:status :idle :offset-text ""}
      :status {:save nil :message nil}
      :settings {:persist? false}})))

(defonce store (st/create-store {:db (initial-db)}))

(defn register!
  "Register the app's handlers and coeffects (idempotent; every entry point calls it)."
  []
  (comps/register!)
  (st/register-handlers! events/handlers))
