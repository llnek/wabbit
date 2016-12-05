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

(ns ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.sys.exec

  (:require [czlab.convoy.net.mime :refer [setupCache]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.logging :as log]
            [clojure.java.io :as io])

  (:use [czlab.wabbit.etc.svcs]
        [czlab.wabbit.sys.core]
        [czlab.wabbit.sys.extn]
        [czlab.wabbit.sys.jmx]
        [czlab.xlib.core]
        [czlab.xlib.io]
        [czlab.xlib.str])

  (:import [czlab.wabbit.io IoService IoGist]
           [java.security SecureRandom]
           [clojure.lang Atom]
           [java.util Date]
           [java.io File]
           [java.net URL]
           [czlab.xlib
            Disposable
            Startable
            Muble
            Versioned
            Hierarchial
            Identifiable]
           [czlab.wabbit.server
            Container
            Execvisor
            AppGist
            JmxServer
            Component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private START-TIME (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inspectPod
  "Make sure the pod setup is ok"
  ^AppGist
  [^Execvisor execv desDir]
  (log/info "app dir : %s => inspecting..." desDir)
  ;;create the pod meta and register it
  ;;as a application
  (let
    [conf (io/file desDir CFG_APP_CF)
     _ (precondFile conf)
     app (basename desDir)
     s (str "{\n"
            (slurpUtf8 conf) "\n}\n")
     cf (readEdn s)
     ctx (.getx execv)
     m (podMeta app
                cf
                (io/as-url desDir))]
    (comp->init m nil)
    (doto->>
      m
      (.setv ctx :app ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJmx
  "Basic JMX support"
  ^JmxServer
  [^Execvisor co cfg]
  (try!
    (let [jmx (jmxServer<> cfg)]
      (.start jmx)
      (.reg jmx
            co
            "czlab"
            "execvisor"
            ["root=wabbit"])
      (doto->>
        jmx
        (.setv (.getx co) :jmxServer )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopJmx
  "Kill the internal JMX server"
  ^Component
  [^Execvisor co]
  (try!
    (let
      [ctx (.getx co)
       jmx (.getv ctx :jmxServer)]
      (when (some? jmx)
        (.stop ^JmxServer jmx))
      (.unsetv ctx :jmxServer)))
  (log/info "jmx terminated")
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod
  ""
  ^Execvisor
  [^Execvisor co ^AppGist gist]
  (try!
    (let
      [ctr (container<> co gist)
       app (.id gist)
       cid (.id ctr)]
      (log/debug "start pod = %s\ninstance = %s" app cid)
      (doto->>
        ctr
        (.setv (.getx co) :container )
        (.start ))))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods
  ""
  ^Execvisor
  [^Execvisor co]
  (log/info "preparing to stop pods...")
  (let [cx (.getx co)
        c (.getv cx :container)]
    (doto->>
      ^Container
      c
      (.stop )
      (.dispose ))
    (.unsetv cx :container)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<>
  "Create a Execvisor"
  ^Execvisor
  []
  (let
    [impl (muble<> {:container nil
                    :app nil
                    :emitters {}})
     pid (juid)]
    (with-meta
      (reify

        Execvisor

        (uptimeInMillis [_]
          (- (System/currentTimeMillis) START-TIME))
        (id [_] (format "%s{%s}" "execvisor" pid))
        (homeDir [_] (.getv impl :basedir))
        (locale [_] (.getv impl :locale))
        (version [_] "1.0")
        (getx [_] impl)
        (startTime [_] START-TIME)
        (kill9 [_] (apply (.getv impl :stop!) []))

        (start [this]
          (->> (.getv impl :app)
               (ignitePod this )))

        (stop [this]
          (stopJmx this)
          (stopPods this)))

       {:typeid ::Execvisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- emitMeta
  ""
  ^IoGist
  [emsType gist]
  (let [{:keys [info conf]}
        gist
        pid (format "%s[%s]"
                    (juid)
                    (:name info))
        impl (muble<> conf)]
    (with-meta
      (reify

        IoGist

        (version [_] (:version info))
        (getx [_] impl)
        (type [_] emsType)

        (setParent [_ p] (.setv impl :execv p))
        (parent [_] (.getv impl :execv))

        (isEnabled [_]
          (not (false? (:enabled info))))

        (id [_] pid))

      {:typeid  ::IoGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod comp->init
  ::IoGist
  [^IoGist co execv]

  (log/info "comp->init: '%s': '%s'" (gtid co) (.id co))
  (.setParent co execv)
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoEmitters
  ""
  ^Execvisor
  [^Execvisor co]
  (let [ctx (.getx co)]
    (->>
      (preduce<map>
        #(let [b (emitMeta (first %2)
                           (last %2))]
           (comp->init b co)
           (assoc! %1 (.type b) b))
        *emitter-defs*)
      (.setv ctx :emitters ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoApps
  ""
  ^Execvisor
  [^Execvisor co]
  (->> (.getv (.getx co) :appDir)
       (inspectPod co))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::Execvisor
  [^Execvisor co rootGist]
  {:pre [(inst? Atom rootGist)]}

  (let [{:keys [basedir appDir jmx]}
        @rootGist]
    (log/info "com->init: '%s': '%s'" (gtid co) (.id co))
    (test-some "conf file: jmx" jmx)
    (sysProp! "file.encoding" "utf-8")
    (.copy (.getx co) (muble<> @rootGist))
    (-> (io/file appDir
                 DN_ETC
                 "mime.properties")
        (io/as-url)
        (setupCache ))
    (regoEmitters co)
    (regoApps co)
    (startJmx co jmx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


