(ns chat42app.events
  (:require
   [re-frame.core :refer [reg-event-db after]]
   [clojure.spec.alpha :as s]
   [chat42app.db :as db :refer [app-db]]))

;; -- Interceptors ------------------------------------------------------------
;;
;; See https://github.com/Day8/re-frame/blob/master/docs/Interceptors.md
;;
(defn check-and-throw
  "Throw an exception if db doesn't have a valid spec."
  [spec db [event]]
  (when-not (s/valid? spec db)
    (let [explain-data (s/explain-data spec db)]
      (throw (ex-info (str "Spec check after " event " failed: " explain-data) explain-data)))))

(def validate-spec
  (if goog.DEBUG
    (after (partial check-and-throw ::db/app-db))
    []))

;; -- Handlers --------------------------------------------------------------

(reg-event-db
 :initialize-db
 validate-spec
 (fn [_ _]
   app-db))

(reg-event-db
 :set-message
 validate-spec
 (fn [db [_ value]]
   (assoc db :message value)))

(reg-event-db
 :set-author
 validate-spec
 (fn [db [_ value]]
   (assoc db :author value)))

(reg-event-db
 :add-ormap
 validate-spec
 (fn [db [_ value]]
   (update db :ormap #(conj (or % #{}) value))))

(reg-event-db
 :rem-ormap
 validate-spec
 (fn [db [_ value]]
   (update db :ormap #(disj (or % #{}) value))))
