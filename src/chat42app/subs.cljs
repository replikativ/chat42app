(ns chat42app.subs
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub
  :get-message
  (fn [db _]
    (:message db)))

(reg-sub
 :get-ormap
 (fn [db _]
   (:ormap db)))
