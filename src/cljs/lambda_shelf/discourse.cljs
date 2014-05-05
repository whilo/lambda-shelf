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
;; - consistency over repos


(repo/new-repository "admin@polyc0l0r.net"
                     {:type "s" :version 1}
                     "A discourse organizer."
                     true
                     {:discourses #{}})


(defn new-state [update-fn]
  (go (let [store (<! (new-mem-store))
            peer (client-peer "shelf-client" store)
            app-stage (-> ;; will be loaded from kv-store
                       {:meta
                        {:description "A discourse organizer.",
                         :schema {:type "http://github.com/ghubber/geschichte", :version 1},
                         :pull-requests {},
                         :causal-order {#uuid "32026517-534c-597b-b58d-a0d098740e5e" []},
                         :public true,
                         :branches
                         {"master" {:heads #{#uuid "32026517-534c-597b-b58d-a0d098740e5e"}}},
                         :head "master",
                         :last-update #inst "2014-05-05T18:51:54.561-00:00",
                         :id #uuid "b03faa0b-a443-40db-850f-e9738f1f275f"},
                        :author "admin@polyc0l0r.net",
                        :schema {:type "s", :version 1},
                        :transactions [],
                        :type :meta-sub,
                        :new-values
                        {#uuid "32026517-534c-597b-b58d-a0d098740e5e"
                         {:transactions
                          [[#uuid "2411fb8d-a15a-501a-b567-e8ddd5dd1c44"
                            #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"]],
                          :parents [],
                          :ts #inst "2014-05-05T18:51:54.561-00:00",
                          :author "admin@polyc0l0r.net",
                          :schema {:type "s", :version 1}},
                         #uuid "2411fb8d-a15a-501a-b567-e8ddd5dd1c44" {:discourses #{}},
                         #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"
                         '(fn replace [old params] params)}}
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

        (go-loop [{:keys [meta] :as pm} (<! pub-ch)]
          (when pm
            (let [new-stage (swap! app-stage update-in [:meta] update meta)]
              (if (repo/merge-necessary? (:meta new-stage))
                (go
                  (<! (timeout (rand-int 10000)))
                  (when (repo/merge-necessary? (:meta @app-stage))
                    (.info js/console "MERGING" (pr-str (:meta @app-stage)))
                    (<! (s/sync! (swap! app-stage repo/merge)))))
                (let [nval (-> new-stage
                               (s/realize-value store trans-fns)
                               <!)]
                  (update-fn nval))))
            (recur (<! pub-ch))))

        (let [[p out] (:chans @app-stage)]
          (sub p :meta-pub pub-ch)
          (>! out {:topic :meta-pub-req ;; init with stage
                   :depth 0
                   :user "admin@polyc0l0r.net"
                   :repo #uuid "b03faa0b-a443-40db-850f-e9738f1f275f"
                   :peer "STAGE"
                   :metas {"master" #{}}}))
        {:volatile {:store store
                    :peer peer
                    :stages {:discourses app-stage}}})))


#_(new-state #(println "NEW-VALUE: " %)
             #_(om/transact!
               nil
               :discourses
               (fn [_] %)))




(comment
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
