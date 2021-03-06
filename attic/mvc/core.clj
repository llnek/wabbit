;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.demo.mvc.core

  (:require [czlab.niou.core :as cc]
            [czlab.basal.core :as c]
            [czlab.blutbad.core :as b]
            [czlab.blutbad.plugs.mvc :as mvc])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- ftl-context

  []

  {:landing {:title_line "Sample Web App"
             :title_2 "Demo Skaro"
             :tagline "Say something" }
   :about {:title "About Skaro demo" }
   :services {}
   :contact {:email "a@b.com"}
   :description "Default Skaro web app."
   :encoding "utf-8"
   :title "Skaro|Sample"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn handler

  [evt res]

  (c/do-with
    [ch (:socket evt)]
    (let [ri (:route evt)
          tpl (:template ri)
          plug (c/parent evt)
          co (c/parent plug)
          {:keys [data ctype]}
          (mvc/load-template co tpl (ftl-context))]
      (->> (-> (cc/res-header-set res "content-type" ctype)
               (assoc :body data))
           cc/reply-result ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn main
  [_] (println "My AppMain called!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


