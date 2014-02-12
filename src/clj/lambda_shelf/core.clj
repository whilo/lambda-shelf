(ns lambda-shelf.core
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :as enlive]
            [compojure.route :refer (resources)]
            [compojure.core :refer (GET defroutes)]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.java.io :as io]
            [lambda-shelf.database :refer [get-all-bookmarks]]))

                                        ; ring server, only for production
(enlive/deftemplate page
  (io/resource "public/index.html")
  []
  [:body] (enlive/append
            (enlive/html [:script (browser-connected-repl-js)])))

(defroutes site
  (resources "/")
  (GET "/init" [] {:status 200
                   :headers {"Content-Type" "application/edn"}
                   :body (str (get-all-bookmarks))})
  (GET "/*" req (page)))

#_(defonce server
    (run-jetty #'site {:port 8080 :join? false}))

(defn -main
  [& args]
  (run-jetty #'site {:port 8080 :join? false}))

#_(.stop server)
#_(.start server)
