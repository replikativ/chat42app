(ns chat42app.db
  (:require [clojure.spec.alpha :as s]))

;; spec of app-db
(s/def ::message string?)
(s/def ::app-db
  (s/keys :req-un [::message]))

;; initial state of app-db
(def app-db {:message "Hello Clojure in iOS and Android!"})
