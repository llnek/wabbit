;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^:no-doc
    ^{:author "kenl"}

  demo.fork.core

  (:require [czlab.xlib.util.logging :as log])

  (:require
    [czlab.xlib.util.core :refer [try!]]
    [czlab.xlib.util.str :refer [hgl?]]
    [czlab.xlib.util.wfs :refer [SimPTask]])

  (:import
    [com.zotohlab.skaro.core Container]
    [com.zotohlab.wflow Job
    Activity WorkFlow FlowDot PTask Split]
    [java.lang StringBuilder]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fib ""

  [n]

  (if (< n 3)
    1
    (+ (fib (- n 2))
       (fib (- n 1)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;   parent(s1) --> split&nowait
;;                  |-------------> child(s1)----> split&wait --> grand-child
;;                  |                              |                    |
;;                  |                              |<-------------------+
;;                  |                              |---> child(s2) -------> end
;;                  |
;;                  |-------> parent(s2)----> end

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^Activity
  a1
  (SimPTask
    #(do
       (println "I am the *Parent*")
       (println "I am programmed to fork off a parallel child process, "
                "and continue my business."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^Activity
  a2
  (Split/fork
    (SimPTask
      (fn [^Job j]
        (println "*Child*: will create my own child (blocking)")
        (.setv j "rhs" 60)
        (.setv j "lhs" 5)
        (-> (Split/applyAnd
              (SimPTask
                (fn [^Job j]
                  (println "*Child*: the result for (5 * 60) according to "
                           "my own child is = "
                           (.getv j "result"))
                  (println "*Child*: done."))))
            (.include
              (SimPTask
                (fn [^Job j2]
                  (println "*Child->child*: taking some time to do "
                           "this task... ( ~ 6secs)")
                  (dotimes [n 7]
                    (Thread/sleep 1000)
                    (print "..."))
                  (println "")
                  (println "*Child->child*: returning result back to *Child*.")
                  (.setv j2 "result" (* (.getv j2 "rhs")
                                        (.getv j2 "lhs")))
                  (println "*Child->child*: done.")
                  nil))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^Activity
  a3
  (SimPTask
    (fn [_]
      (let [b (StringBuilder. "*Parent*: ")]
        (println "*Parent*: after fork, continue to calculate fib(6)...")
        (dotimes [n 7]
          (.append b (str (fib n) " ")))
        (println (.toString b) "\n" "*Parent*: done.")
        nil))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Demo

  "split but no wait, parent continues"

  ^WorkFlow
  []

  (reify WorkFlow
    (startWith [_]
      (-> (.chain a1 a2)
          (.chain a3)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

