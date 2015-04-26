;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc "OS Process related utilities."
      :author "kenl" }

  czlabclj.xlib.util.process

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core :only [Try!]]
        [czlabclj.xlib.util.meta :only [GetCldr]]
        [czlabclj.xlib.util.str :only [nsb hgl?]])

  (:import  [java.lang.management ManagementFactory]
            [java.util.concurrent Callable]
            [java.util TimerTask Timer]
            [com.zotohlab.frwk.util CoreUtils]
            [java.lang Thread Runnable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SyncBlockExec ""

  [^Object lock func & args]

  (CoreUtils/syncExec lock
                      (reify Callable
                        (call [_]
                          (apply func args)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- asyncExecThread ""

  [^Runnable r options]

  (when-not (nil? r)
    (let [c (or (:classLoader options)
                     (GetCldr))
          d (true? (:daemon options))
          n (:name options)
          t (Thread. r) ]
      (.setContextClassLoader t ^ClassLoader c)
      (.setDaemon t d)
      (when (hgl? n)
        (.setName t (str "(" n ") " (.getName t))))
      (log/debug "asyncExecThread: about to start thread#" (.getName t) ", daemon = " d)
      (.start t))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AsyncExec "Run the code (runnable) in a separate daemon thread."

  ([^Runnable runable] (AsyncExec runable (GetCldr)))

  ([^Runnable runable arg]
   (asyncExecThread runable (if (instance? ClassLoader arg)
                              {:classLoader arg}
                              (if (map? arg) arg {})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Coroutine "Run this function asynchronously."

  ([func] (Coroutine func nil))

  ([func options]
   (let [r (reify Runnable
             (run [_]
               (Try! (when (fn? func) (func)))
               (log/debug "Coroutine thread#"
                          (-> (Thread/currentThread)
                              (.getName))
                          ": (run) is done."))) ]
     (AsyncExec r options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ThreadFunc ""

  (^Thread
    [func start arg]
    (let [t (Thread. (reify Runnable
                       (run [_] (apply func []))))]
      (with-local-vars [daemon false cl nil]
        (when (instance? ClassLoader arg)
          (var-set cl arg))
        (when (map? arg)
          (var-set cl (:classLoader arg))
          (when (true? (:daemon arg))
            (var-set daemon true)))
        (when-not (nil? @cl)
          (.setContextClassLoader t @cl))
        (.setDaemon t (true? @daemon)))
      (when start (.start t))
      (log/debug "ThreadFunc thread#" (.getName t) ", daemon = " (.isDaemon t))
      t))

  (^Thread [func start] (ThreadFunc func start nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SafeWait "Block current thread for some millisecs."

  [millisecs]

  (Try! (when (> millisecs 0)
          (Thread/sleep millisecs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ProcessPid "Get the current process pid."

  ^String
  []

  (let [ss (-> (nsb (.getName (ManagementFactory/getRuntimeMXBean)))
               (.split "@")) ]
    (if (or (nil? ss) (empty ss))
      ""
      (first ss))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn DelayExec ""

  [func delayMillis]

  (-> (Timer. true)
      (.schedule (proxy [TimerTask][]
                   (run []
                     (apply func [])))
                 (long delayMillis))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private process-eof nil)

