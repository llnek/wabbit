;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  comzotohlabscljc.tardis.io.loops

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.util.core :only [MubleAPI TryC] ])
  (:use [comzotohlabscljc.util.process :only [Coroutine SafeWait] ])
  (:use [comzotohlabscljc.util.dates :only [ParseDate] ])
  (:use [comzotohlabscljc.util.meta :only [GetCldr] ])
  (:use [comzotohlabscljc.util.seqnum :only [NextLong] ])
  (:use [comzotohlabscljc.util.str :only [nsb hgl? strim] ])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.io.core])
  (:import (java.util Date Timer TimerTask))
  (:import (com.zotohlabs.gallifrey.io TimerEvent))
  (:import (com.zotohlabs.frwk.core Identifiable Startable)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti LoopableWakeup "" (fn [a & args] (:typeid (meta a)) ))
(defmulti LoopableSchedule "" (fn [a] (:typeid (meta a)) ))
(defmulti LoopableOneLoop "" (fn [a] (:typeid (meta a)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- config-repeat-timer "" 
  
  [^Timer tm delays intv func]

  (let [ tt (proxy [TimerTask][]
              (run []
                (TryC
                    (when (fn? func) (func)))))
         [^Date dw ^long ds] delays ]
    (when (instance? Date dw)
      (.schedule tm tt dw ^long intv) )
    (when (number? ds)
      (.schedule tm tt ds ^long intv))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- config-timer "" 
  
  [^Timer tm delays func]

  (let [ tt (proxy [TimerTask][]
              (run []
                (when (fn? func) (func))))
         [^Date dw ^long ds] delays]
    (when (instance? Date dw)
      (.schedule tm tt dw) )
    (when (number? ds)
      (.schedule tm tt ds))) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- config-timertask "" 
  
  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ intv (.getAttr co :intervalMillis)
         t (.getAttr co :timer)
         ds (.getAttr co :delayMillis)
         dw (.getAttr co :delayWhen)
         func (fn [] (LoopableWakeup co)) ]
    (if (number? intv)
      (config-repeat-timer t [dw ds] intv func)
      (config-timer t [dw ds] func))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CfgLoopable "" 
  
  [^comzotohlabscljc.tardis.core.sys.Element co cfg]

  (let [ intv (:interval-secs cfg)
         ds (:delay-secs cfg)
         dw (nsb (:delay-when cfg)) ]
    (if (hgl? dw)
      (.setAttr! co :delayWhen (ParseDate (strim dw) "yyyy-MM-ddTHH:mm:ss"))
      (do
        (.setAttr! co :delayMillis
                   (* 1000 (Math/min (int 3)
                                     (int (if (number? ds) ds 3)))))))
    (when (number? intv)
      (.setAttr! co :intervalMillis (* 1000 intv)))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- start-timer "" 
  
  [^comzotohlabscljc.tardis.core.sys.Element co]

  (do
    (.setAttr! co :timer (Timer. true))
    (LoopableSchedule co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- kill-timer "" 
  
  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^Timer t (.getAttr co :timer) ]
    (TryC
        (when-not (nil? t) (.cancel t)) )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Repeating Timer
(defn MakeRepeatingTimer ""
  
  [container]

  (MakeEmitter container :czc.tardis.io/RepeatingTimer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReify-event :czc.tardis.io/RepeatingTimer

  [co & args]

  (let [ eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        TimerEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (emitter [_] co)
        (isRepeating [_] true))

      { :typeid :czc.tardis.io/TimerEvent } 
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/RepeatingTimer

  [co cfg]

  (CfgLoopable co cfg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/RepeatingTimer

  [co]

  (start-timer co)
  (IOESStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/RepeatingTimer

  [co]

  (kill-timer co)
  (IOESStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/RepeatingTimer

  [^comzotohlabscljc.tardis.io.core.EmitterAPI co & args]

  (.dispatch co (IOESReifyEvent co) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :default [co]
  (config-timertask co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Once Timer
(defn MakeOnceTimer ""
  
  [container]

  (MakeEmitter container :czc.tardis.io/OnceTimer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/OnceTimer

  [co & args]

  (let [ eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        TimerEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (emitter [_] co)
        (isRepeating [_] false))

      { :typeid :czc.tardis.io/TimerEvent } 
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/OnceTimer

  [co cfg]

  ;; get rid of interval millis field, if any
  (CfgLoopable co (dissoc cfg :interval-secs)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/OnceTimer

  [co]

  (start-timer co)
  (IOESStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/OnceTimer

  [co]

  (kill-timer co)
  (IOESStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/OnceTimer

  [^comzotohlabscljc.tardis.io.core.EmitterAPI co & args]

  (do
    (.dispatch co (IOESReifyEvent co) {} )
    (.stop ^Startable co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer

;;(defmethod loopable-oneloop :default [co] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :czc.tardis.io/ThreadedTimer

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ intv (.getAttr co :intervalMillis)
         cl (GetCldr)
         loopy (atom true)
         func (fn []
                (Coroutine (fn []
                             (while @loopy (LoopableWakeup co intv)))
                           cl)) ]
    (.setAttr! co :loopy loopy)
    (log/info "threaded one timer - interval = " intv)
    (func)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/ThreadedTimer

  [co & args]

  (do
    (TryC
        (LoopableOneLoop co) )
    (SafeWait (first args) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/ThreadedTimer

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ds (.getAttr co :delayMillis)
         dw (.getAttr co :delayWhen)
         intv (.getAttr co :intervalMillis)
         loopy (atom true)
         cl (GetCldr)
         func (fn [] (LoopableSchedule co)) ]
    (.setAttr! co :loopy loopy)
    (if (or (number? ds) (instance? Date dw))
      (config-timer (Timer.) [dw ds] func)
      (func))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/ThreadedTimer

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ loopy (.getAttr co :loopy) ]
    (reset! loopy false)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private loops-eof nil)

