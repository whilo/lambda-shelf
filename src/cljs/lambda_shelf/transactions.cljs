(ns lambda-shelf.transactions
  (:require [clojure.set :as set]))

;; static function to eval map for repo transaction functions
;; this can be done differently (e.g. per symbols), but it is much
;; more elegant and safe to track the table. schema-specific
(def trans-fns {'(fn replace [old params] params)
                (fn replace [old params] params)
                '(fn [old new]
                   (update-in old [:links]
                              (fn [old new]
                                (merge-with (fn [old new]
                                              (-> old
                                                  (update-in [:comments] set/union (:comments new))
                                                  (update-in [:votes] set/union (:votes new))
                                                  (update-in [:date] max (:date new))))
                                            old new))
                              (:links new)))
                (fn [old new]
                  (update-in old [:links]
                             (fn [old new]
                               (merge-with (fn [old new]
                                             (-> old
                                                 (update-in [:comments] set/union (:comments new))
                                                 (update-in [:votes] set/union (:votes new))
                                                 (update-in [:date] max (:date new))))
                                           old new))
                             (:links new)))
                '(fn [old new]
                   (update-in old [:links]
                              (fn [old new]
                                (merge-with (fn [old new]
                                              (-> old
                                                  (update-in [:comments] set/union (:comments new))
                                                  (update-in [:date] max (:date new))))
                                            old new))
                              (:links new)))
                (fn [old new]
                  (update-in old [:links]
                             (fn [old new]
                               (merge-with (fn [old new]
                                             (-> old
                                                 (update-in [:comments] set/union (:comments new))
                                                 (update-in [:date] max (:date new))))
                                           old new))
                             (:links new)))
                '(fn [old new]
                   (update-in old [:links]
                              (fn [old new]
                                (merge-with (fn [old new]
                                              (-> old
                                                  (update-in [:votes] set/union (:votes new))
                                                  (update-in [:date] max (:date new))))
                                            old new))
                              (:links new)))
                (fn [old new]
                  (update-in old [:links]
                             (fn [old new]
                               (merge-with (fn [old new]
                                             (-> old
                                                 (update-in [:votes] set/union (:votes new))
                                                 (update-in [:date] max (:date new))))
                                           old new))
                             (:links new)))})
