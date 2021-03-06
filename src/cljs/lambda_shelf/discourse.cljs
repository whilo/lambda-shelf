(ns lambda-shelf.discourse
  (:require goog.Uri
            [lambda-shelf.transactions :refer [trans-fns]]
            [lambda-shelf.environment :refer [uri ssl?]]
            [geschichte.sync :refer [client-peer]]
            [geschichte.stage :as s]
            [geschichte.meta :refer [update]]
            [geschichte.repo :as repo]
            [konserve.store :refer [new-mem-store]]
            [hasch.core :refer [sha-1 hash->str uuid]]
            [cljs.core.async :refer [put! take! chan <! >! alts! timeout
                                     close! sub sliding-buffer]]
            [clojure.string :refer [blank?]]
            [clojure.set :as set])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; Problems
;; - geschichte commit operation/type conflict
;; - consistency over repos

;; - singular profile view?
;; - hierarchy of om components?


#_(repo/new-repository "admin@polyc0l0r.net"
                       "A discourse organizer."
                       true
                       {:discourses #{}})

(go (def store
      (<! (new-mem-store
           (atom {"admin@polyc0l0r.net" {#uuid "7a68958b-620c-4f20-98a1-3f752151e14d"
                                         {:description "A discourse organizer.",
                                          :schema {:type "http://github.com/ghubber/geschichte", :version 1},
                                          :pull-requests {},
                                          :causal-order {#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48" [],
                                                         #uuid "2c454327-bd20-5532-ae9b-3cc85de5e59d"
                                                         [#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48"]},
                                          :public true,
                                          :branches {"master" {:heads #{#uuid "2c454327-bd20-5532-ae9b-3cc85de5e59d"}}},
                                          :head "master",
                                          :last-update #inst "2014-05-06T23:26:00.522-00:00",
                                          :id #uuid "7a68958b-620c-4f20-98a1-3f752151e14d"}}

                  #uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48"
                  {:transactions [[#uuid "2411fb8d-a15a-501a-b567-e8ddd5dd1c44"
                                   #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"]],
                   :parents [],
                   :ts #inst "2014-05-06T11:10:22.797-00:00",
                   :author "admin@polyc0l0r.net"},
                  #uuid "2411fb8d-a15a-501a-b567-e8ddd5dd1c44"
                  {:discourses #{}},
                  #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"
                  '(fn replace [old params] params),
                  #uuid "2c454327-bd20-5532-ae9b-3cc85de5e59d"
                  {:transactions [[#uuid "077c0348-2774-5c5e-9703-809359d11827"
                                   #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"]],
                   :ts #inst "2014-05-06T23:26:00.522-00:00",
                   :parents [#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48"],
                   :author "admin@polyc0l0r.net"},
                  #uuid "077c0348-2774-5c5e-9703-809359d11827"
                  {:discourses #{:beta :alpha}}}))))

    (def peer (client-peer "shelf-client" store)))


(defn update-stage [stage pub-ch update-fn]
  (go-loop [{:keys [meta] :as pm} (<! pub-ch)]
          (when pm
            (let [new-stage (swap! stage update-in [:meta] update meta)]
              (if (repo/merge-necessary? (:meta new-stage))
                (go
                  (<! (timeout (rand-int 10000)))
                  (when (repo/merge-necessary? (:meta @stage))
                    (.info js/console "MERGING" (pr-str (:meta @stage)))
                    (<! (s/sync! (swap! stage repo/merge)))))
                (let [nval (-> new-stage
                               (s/realize-value store trans-fns)
                               <!)]
                  (update-fn nval))))
            (recur (<! pub-ch)))))


(defn new-state [init-stage update-fn]
  (go (let [app-stage (-> init-stage
                          (s/wire-stage peer)
                          <!
                          s/sync!
                          #_<!
                          #_(s/connect! (str (if ssl? "wss://" "ws://")
                                             (.getdomain uri)
                                             ":"
                                             (.getport uri)
                                             "/geschichte/ws"))
                          <!
                          atom)
            pub-ch (chan)]
        (update-stage app-stage pub-ch update-fn)
        (let [[p out] (:chans @app-stage)]
          (sub p :meta-pub pub-ch)
          (>! out {:topic :meta-pub-req
                   :user (get-in init-stage [:author])
                   :repo (get-in init-stage [:meta :id])
                   :peer "STAGE"
                   :metas {"master" #{}}}))
        {:volatile {:store store
                    :peer peer
                    :stages {:discourses app-stage}}})))


#_(new-state {:meta
              {:description "A discourse organizer.",
               :schema {:type "http://github.com/ghubber/geschichte", :version 1},
               :pull-requests {},
               :causal-order {#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48" []},
               :public true,
               :branches
               {"master" {:heads #{#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48"}}},
               :head "master",
               :last-update #inst "2014-05-06T11:10:22.797-00:00",
               :id #uuid "7a68958b-620c-4f20-98a1-3f752151e14d"},
              :author "admin@polyc0l0r.net",
              :transactions [],
              :type :meta-sub,
              :new-values
              {#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48"
               {:transactions
                [[#uuid "2411fb8d-a15a-501a-b567-e8ddd5dd1c44"
                  #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"]],
                :parents [],
                :ts #inst "2014-05-06T11:10:22.797-00:00",
                :author "admin@polyc0l0r.net"},
               #uuid "2411fb8d-a15a-501a-b567-e8ddd5dd1c44" {:discourses #{}},
               #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"
               '(fn replace [old params] params)}}
             #(println "NEW-VALUE: " %)
             #_(om/transact!
                nil
                :discourses
                (fn [_] %)))

(defn add-stage [state user repo-id update-fn]
  (go (let [{:keys [peer stages]} state
            stage (-> {:meta ;; minimal metadata, filled in on meta-update
                       {:schema {:type "http://github.com/ghubber/geschichte", :version 1},
                        :pull-requests {},
                        :last-update #inst "2014-05-06T11:10:22.797-00:00",
                        :id repo-id},
                       :author user,
                       :transactions []}
                      (s/wire-stage peer)
                      <!
                      s/sync!
                      <!
                      atom)
            [p out] (get-in stages [:discourses :chans])
            pub-ch (chan)]

        (update-stage stage pub-ch update-fn)

        (sub p :meta-pub pub-ch)
        (>! out {:topic :meta-pub-req ;; init with stage
                 :user user
                 :repo repo-id
                 :peer "STAGE"
                 :metas {"master" #{}}}))
      (assoc-in state [:volatile :stages user repo-id] stage)))




(comment

  (-> (s/transact {:meta
                   {:description "A discourse organizer.",
                    :schema {:type "http://github.com/ghubber/geschichte", :version 1},
                    :pull-requests {},
                    :causal-order {#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48" []},
                    :public true,
                    :branches
                    {"master" {:heads #{#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48"}}},
                    :head "master",
                    :last-update #inst "2014-05-06T11:10:22.797-00:00",
                    :id #uuid "7a68958b-620c-4f20-98a1-3f752151e14d"},
                   :author "admin@polyc0l0r.net",
                   :transactions [],
                   :type :meta-sub,
                   :new-values
                   {#uuid "3f4c6eaa-0529-59bc-8723-dbfdc427de48"
                    {:transactions
                     [[#uuid "2411fb8d-a15a-501a-b567-e8ddd5dd1c44"
                       #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"]],
                     :parents [],
                     :ts #inst "2014-05-06T11:10:22.797-00:00",
                     :author "admin@polyc0l0r.net"},
                    #uuid "2411fb8d-a15a-501a-b567-e8ddd5dd1c44"
                    {:discourses
                     #{}}, #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"
                    '(fn replace [old params] params)}}
                  {:discourses #{:alpha :beta}}
                  '(fn replace [old params] params))
      repo/commit)




  (def app #{:holidays :business :help})

  (def discourses {:holidays #{1 6 8}
                   :business #{2 3 5}
                   :help #{4 7}})


  (def repos {"john@mail.com" {1 #{{:content "I like this proposal."
                                    :ts (java.util.Date.)
                                    :annotations #{{:type :vote
                                                    :voter "john@mail.com"}}}}}
              "marie@gmail.com" {6 #{{:content "Good idea."}}
                                 7 #{{:content "I'm out."}}}
              "pete@epost.de" {2 #{:content "[More infos](http://url.com)"}}}))
