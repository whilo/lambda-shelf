(ns lambda-shelf.environment
  (:require goog.Uri))

(def uri (goog.Uri. js/document.URL))

(def ssl? (= (.getScheme uri) "https"))
