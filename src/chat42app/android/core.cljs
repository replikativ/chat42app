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

(def logo-img (js/require "./images/cljs.png"))

(defn alert [title]
      (.alert (.-Alert ReactNative) title))

(defn render-item [item]
  (let [item (js->clj item)]
    (r/reactify-component
     (fn []
       [text {:style {:font-size 30}} (pr-str item)]))))

(defn app-root []
  (let [message (subscribe [:get-message])
        ormap (subscribe [:get-ormap])]
    (fn []
      [view {:style {:flex-direction "column" :margin 40 :align-items "center"}}
       #_[flat-list {:data (clj->js (map (fn [e] (assoc e :key (uuid))) @ormap))
                   :renderItem render-item}]
       (for [item @ormap]
         [text {:style {:font-size 30 :font-weight "100" :margin-bottom 20
                        :text-align "center"}
                :key (uuid)}
          (str (:name item) ": " (:text item))])
       [text-input {:style {:text-align "center"}
                    :onChange (fn [e] (dispatch [:set-message (.. e -nativeEvent -text)]))
                    :placeholder "Message"}]
       #_[image {:source logo-img
               :style  {:width 80 :height 80 :margin-bottom 30}}]
       [touchable-highlight {:style {:background-color "#999" :padding 10
                                     :border-radius 5}
                             :on-press #(send-message! (create-msg "native" @message))}
        [text {:style {:color "white" :text-align "center" :font-weight "bold"}}
         "send!"]]])))

(defn init []
      (dispatch-sync [:initialize-db])
      (.registerComponent app-registry "Chat42App" #(r/reactify-component app-root)))
