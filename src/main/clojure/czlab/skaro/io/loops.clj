;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(ns ^{:doc "Basic functions for loopable services."
      :author "Kenneth Leung" }

  czlab.skaro.io.loops

  (:require
    [czlab.xlib.process :refer [async! safeWait]]
    [czlab.xlib.dates :refer [parseDate]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.logging :as log])

  (:use [czlab.skaro.sys.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.skaro.io.core])

  (:import
    [czlab.skaro.io IoService TimerEvent]
    [java.util Date Timer TimerTask]
    [clojure.lang APersistentMap]
    [czlab.xlib Muble Identifiable Startable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;; service hierarchy
(derive ::RepeatingTimer :czlab.skaro.io.core/Service)
(derive ::OnceTimer :czlab.skaro.io.core/Service)
(derive ::ThreadedTimer ::RepeatingTimer)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; These 2 fn(s) together dictates how a loopable shall run
(defmulti loopableSchedule "" (fn [a b] (:typeid (meta a))))
(defmulti loopableWakeup "" (fn [a b] (:typeid (meta a))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkEventObj

  ^TimerEvent
  [^IoService co repeat?]

  (let [eeid (str "event#" (seqint2))]
    (with-meta
      (reify
        TimerEvent

        (checkAuthenticity [_] false)
        (bindSession [_ s] nil)
        (session [_] nil)
        (id [_] eeid)
        (source [_] co)
        (isRepeating [_] repeat?))

      {:typeid ::TimerEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configRepeat

  "Configure a repeating timer"
  [^Timer tm delays ^long intv func]

  (log/info "Scheduling a repeating timer: %dms" intv)
  (let [tt (tmtask<> func)
        [dw ds] delays]
    (cond
      (inst? Date dw)
      (.schedule tm tt ^Date dw intv)
      (spos? ds)
      (.schedule tm tt ^long ds intv))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configOnce

  "Configure a *one-time* timer"
  [^Timer tm delays func]

  (log/info "Scheduling a *single-shot* timer")
  (let [ tt (tmtask<> func)
        [dw ds] delays]
    (cond
      (inst? Date dw)
      (.schedule tm tt ^Date dw)
      (spos? ds)
      (.schedule tm tt ^long ds))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimerTask

  [^IoService co repeat?]

  (let [tm (.getv (.getx co) :timer)
        {:keys [intervalSecs
                delayWhen
                delaySecs]}
        (.config co)
        d [delayWhen (s2ms delaySecs)]
        func #(loopableWakeup co nil)]
    (test-some "java-timer" tm)
    (if (and repeat?
             (spos? intervalSecs))
      (configRepeat tm d (s2ms intervalSecs) func)
      (configOnce tm d func))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startTimer

  ""
  [^IoService co repeat?]

  (logcomp "start-timer" co)
  (.setv (.getx co) :timer (Timer. true))
  (loopableSchedule co {:repeat? repeat?} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- killTimer

  ""
  [^IoService co]

  (when-some [t (.getv (.getx co) :timer)]
    (try! (.cancel ^Timer t))
    (.unsetv (.getx co) :timer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Repeating Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>
  ::RepeatingTimer [^IoService co _] (mkEventObj co true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::RepeatingTimer
  [^IoService co]

  (logcomp "io->start" co)
  (startTimer co true)
  (io<started> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::RepeatingTimer
  [^IoService co]

  (logcomp "io->stop" co)
  (killTimer co)
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::RepeatingTimer
  [^IoService co _]

  ;;(log/debug "loopableWakeup %s: %s" (gtid co) (.id co))
  (.dispatch co (ioevent<> co nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableSchedule

  :czlab.skaro.io.core/Service
  [^IoService co {:keys [repeat?]} ]

  (configTimerTask co repeat?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Once Timer

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ioevent<>
  ::OnceTimer [^IoService co _] (mkEventObj false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::OnceTimer
  [^IoService co]

  (logcomp "io->start" co)
  (startTimer co false)
  (io<started> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::OnceTimer
  [^IoService co]

  (logcomp "io->stop" co)
  (killTimer co)
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::OnceTimer
  [^IoService co _]

  (.dispatch co (ioevent<> co nil))
  (.stop ^Startable co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer
(defmethod loopableSchedule

  ::ThreadedTimer
  [^IoService co {:keys [intervalMillis]}]

  (log/info "%s timer @interval = %d" (gtid co) intervalMillis)
  (let [loopy (volatile! true)]
    (.setv (.getx co) :loopy loopy)
    (async!
      #(while @loopy
         (loopableWakeup co {:waitMillis intervalMillis}))
      {:cl (getCldr)})
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod loopableWakeup

  ::ThreadedTimer
  [^IoService co {:keys [waitMillis]}]

  ;;(log/debug "loopableWakeup %s: %s" (gtid co) (.id co))
  (.dispatch co (ioevent<> co nil))
  (safeWait waitMillis)
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->start

  ::ThreadedTimer
  [^IoService co]

  (logcomp "io->start" co)
  (let [{:keys [intervalSecs
                delaySecs delayWhen]}
        (.config co)
        func #(loopableSchedule co {:intervalMillis
                                    (s2ms intervalSecs)})]
    (if (or (spos? delaySecs)
            (inst? Date delayWhen))
      (configOnce (Timer.)
                   [delayWhen (s2ms delaySecs)] func)
      (func))
    (io<started> co)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod io->stop

  ::ThreadedTimer
  [^IoService co]

  (logcomp "io->stop" co)
  (if-some [loopy (.getv (.getx co) :loopy)]
    (vreset! loopy false))
  (io<stopped> co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


