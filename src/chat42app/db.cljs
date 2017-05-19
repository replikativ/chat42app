(ns chat42app.db
  (:require [clojure.spec.alpha :as s]))

;; spec of app-db
(s/def ::message string?)
(s/def ::author string?)
(s/def ::app-db
  (s/keys :req-un [::message ::author]))

;; initial state of app-db
(def app-db {:message ""
             :author ""})
