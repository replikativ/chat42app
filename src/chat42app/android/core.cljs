(ns chat42app.android.core
  (:require [reagent.core :as r :refer [atom]]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [chat42app.events]
            [chat42app.subs]

            [konserve.memory :refer [new-mem-store]]
            [replikativ.crdt.lwwr.stage :refer [create-lwwr! set-register!]]
            [replikativ.crdt.lwwr.realize :refer [stream-into-atom!]]
            [replikativ.crdt.ormap.stage :as s]
            [replikativ.crdt.ormap.realize :refer [stream-into-identity!]]
            [replikativ.peer :refer [client-peer]]
            [replikativ.stage :refer [connect! create-stage!]]
            [superv.async :refer [<? S go-try put?] :include-macros]
            [cljs.core.async :refer [chan]]
            [hasch.core :refer [uuid]]
            [goog.net.WebSocket]
            [goog.Uri]
            [goog.crypt :as crypt]
            [goog.events :as events]))


;; 1. app constants
(def user "mail:alice@replikativ.io")
(def ormap-id #uuid "7d274663-9396-4247-910b-409ae35fe98d")
(def uri "ws://10.0.2.2:31744")


(enable-console-print!)

;; Have a look at the replikativ "Get started" tutorial to understand how the
;; replikativ parts work: http://replikativ.io/tut/get-started.html

(def stream-eval-fns
  {'assoc (fn [_ new] (dispatch [:add-ormap new]))
   'dissoc (fn [_ new] (dispatch [:rem-ormap new]))})

(defn setup-replikativ []
  (go-try S
    (let [local-store (<? S (new-mem-store))
          local-peer (<? S (client-peer S local-store))
          stage (<? S (create-stage! user local-peer))
          stream (stream-into-identity! stage
                                        [user ormap-id]
                                        stream-eval-fns
                                        nil)]
      (<? S (s/create-ormap! stage
                             :description "messages"
                             :id ormap-id))
      (connect! stage uri)
      {:store local-store
       :stage stage
       :stream stream
       :peer local-peer})))

(declare state)
;; this is the only state changing function
(defn send-message! [msg]
  (s/assoc! (:stage state)
            [user ormap-id]
            (uuid msg)
            [['assoc msg]]))


(defn create-msg [name text]
  {:text text
   :name name
   :date (.getTime (js/Date.))})


(go-try S (def state (<? S (setup-replikativ))))


(def ReactNative (js/require "react-native"))

(def app-registry (.-AppRegistry ReactNative))
(def text (r/adapt-react-class (.-Text ReactNative)))
(def view (r/adapt-react-class (.-View ReactNative)))
(def flat-list (r/adapt-react-class (.-FlatList ReactNative)))
(def text-input (r/adapt-react-class (.-TextInput ReactNative)))
(def image (r/adapt-react-class (.-Image ReactNative)))
(def touchable-highlight (r/adapt-react-class (.-TouchableHighlight ReactNative)))

(defn alert [title]
      (.alert (.-Alert ReactNative) title))

(defn format-time [d]
  (let [secs (-> (.getTime (js/Date.))
                 (- d)
                 (/ 1000)
                 js/Math.floor)]
    (cond
      (>= secs 3600) (str (js/Math.floor (/ secs 3600)) " hours ago")
      (>= secs 60) (str (js/Math.floor (/ secs 60)) " minutes ago")
      (>= secs 0) (str  " seconds ago"))))

(defn render-item [item]
  (let [{{msg "text"
          author "name"
          datetime "date"} "item"} (js->clj item)]
    (r/create-element
     (r/reactify-component
      (fn []
        [view {:style {:flex-direction "row"
                       :align-items "center"
                       :padding 10
                       :border-width 1
                       :border-radius 5
                       :border-style "solid"
                       :border-color "#FFF"
                       :background-color "#EEE"}}
         [text {:style {:font-size 16
                        :font-weight "300"
                        :position "absolute"
                        :right 10
                        :bottom 5
                        :color "#666"
                        :text-align "left"}} author]
         [text {:style {:font-size 12
                        :font-weight "100"
                        :color "#999"
                        :position "absolute"
                        :left 10
                        :bottom 5
                        :text-align "left"}} (format-time datetime)]
         [text {:style {:font-size 20
                        :font-weight "100" :margin-bottom 20
                        :color "#333"
                        :text-align "left"}} msg]])))))

(defn app-root []
  (let [message (subscribe [:get-message])
        author (subscribe [:get-author])
        ormap (subscribe [:get-ormap])]
    (fn []
      [view {:style {:flex-direction "column" :margin 40 :align-items "center"}}
       [flat-list {:style {:width "100%"
                           :max-height "70%"}
                   :data (clj->js
                          (->> @ormap
                               (map (fn [e] (assoc e :key (uuid e))))
                               (sort-by :date)
                               reverse))
                   :renderItem render-item}]
       [text-input {:style {:text-align "left"
                            :margin-top 10
                            :width 200}
                    :onChange (fn [e] (dispatch [:set-author (.. e -nativeEvent -text)]))
                    :placeholder "Author"}]
       [text-input {:style {:text-align "left"
                            :width 200}
                    :onChange (fn [e] (dispatch [:set-message (.. e -nativeEvent -text)]))
                    :placeholder "Message"}]
       [touchable-highlight {:style {:background-color "#999" :padding 10
                                     :border-radius 5}
                             :on-press
                             (fn [_]
                               (if (or (empty? @author)
                                       (empty? @message))
                                 (alert "Please enter an author and a message before sending.")
                                 (send-message! (create-msg @author @message))))}
        [text {:style {:color "white" :text-align "center" :font-weight "bold"}}
         "send!"]]])))

(defn init []
      (dispatch-sync [:initialize-db])
      (.registerComponent app-registry "Chat42App" #(r/reactify-component app-root)))
