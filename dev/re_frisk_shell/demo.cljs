(ns re-frisk-shell.demo
  (:require
    [reagent.core :as reagent]
    [taoensso.sente  :as sente]
    [taoensso.sente.packers.transit :as sente-transit]
    [re-frisk-shell.re-com.views :as ui-re-com])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce re-frame-data (reagent/atom {:app-db (reagent/atom "not connected")
                                      :id-handler (reagent/atom "not connected")}))

(defonce re-frame-events (reagent/atom []))
(defonce deb-data (reagent/atom {}))

(defn update-app-db [val]
  (reset! (:app-db @re-frame-data) val))

(defn update-events [val]
  (let [indx (count @re-frame-events)
        app-db-diff (:app-db-diff val)
        duration (if (map? val) (:time val) val)
        event (if (map? val) (:event val) val)]
    (if (:trace (last @re-frame-events))
      (swap! re-frame-events update-in [(dec indx)]
             #(assoc % :app-db-diff app-db-diff
                       :trace {:duration duration :status :completed}))
      (swap! re-frame-events conj {:event event
                                   :app-db-diff app-db-diff
                                   :indx indx}))))

(defn update-pre-events [val]
  (let [indx (count @re-frame-events)]
    (swap! re-frame-events conj {:event val
                                 :trace {:status :handled}
                                 :indx indx})))

(defn update-id-handler [val]
  (reset! (:id-handler @re-frame-data) val))

;SENTE HANDLERS
(defmulti -event-msg-handler "Multimethod to handle Sente `event-msg`s" :id)

(defn event-msg-handler
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default [_])

(defmethod -event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (case (first ?data)
    :refrisk/app-db (update-app-db (second ?data))
    :refrisk/events (update-events (second ?data))
    :refrisk/pre-events (update-pre-events (second ?data))
    :refrisk/id-handler (update-id-handler (second ?data))))

;SENTE ROUTER
(defn start-router! []
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
          "/chsk" ; Must match server Ring routing URL
          {:type   :auto
           :host   (str "localhost:4567") ;js/location.port)
           :packer (sente-transit/get-transit-packer)})]
    (sente/start-client-chsk-router! ch-recv event-msg-handler)))

;REAGENT RENDER
(defn mount []
  (reagent/render [ui-re-com/main re-frame-data re-frame-events deb-data js/document]
                  (.getElementById js/document "app")))

;ENTRY POINT
(defn ^:export run [port]
  (start-router!)
  (mount))

(def test-state {:map {:kw "keyword"}
                 :map-string {"str" "string"}
                 :set #{1 2 3 {:inset 'inset}}
                 :array [0 1 2 [0 1 2 {:inarray 'inarray}]]
                 :map-complicated {[0 1 2 #{:foo}] 42}
                 :kinda-number {"0x0abc" -1}})

;ENTRY POINT TEST
(defn ^:export runtest [port]
  (mount)
  (update-app-db {:test test-state})
  (update-events [:test-event {:test "TEST"}])
  (update-events [:test-event {:test "TEST2"}]))

(defn on-js-reload []
  (mount))

(comment (on-js-reload) (run)) ; removing warning in IDEA
