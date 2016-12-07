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

  czlab.wabbit.sys.extn

  (:require [czlab.horde.dbio.connect :refer [dbopen<+>]]
            [czlab.xlib.resources :refer [loadResource]]
            [czlab.xlib.scheduler :refer [scheduler<>]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.format :refer [readEdn]]
            [czlab.twisty.codec :refer [passwd<>]]
            [czlab.xlib.logging :as log]
            [clojure.string :as cs]
            [clojure.java.io :as io]
            [czlab.horde.dbio.core
             :refer [dbspec<>
                     dbpool<>
                     dbschema<>]])

  (:use [czlab.wabbit.sys.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.xlib.io]
        [czlab.wabbit.io.core]
        [czlab.wabbit.io.loops]
        [czlab.wabbit.io.mails]
        [czlab.wabbit.io.files]
        [czlab.wabbit.io.jms]
        [czlab.wabbit.io.http]
        [czlab.wabbit.io.socket]
        [czlab.wabbit.mvc.ftl])

  (:import [czlab.wabbit.etc Gist ServiceError ConfigError]
           [czlab.wabbit.pugs PluginFactory Plugin]
           [czlab.horde Schema JDBCPool DBAPI]
           [java.io File StringWriter]
           [czlab.wabbit.server
            Execvisor
            Cljshim
            Service
            Container]
           [freemarker.template
            Configuration
            Template
            DefaultObjectWrapper]
           [java.util Locale]
           [java.net URL]
           [czlab.xlib
            Schedulable
            Versioned
            Hierarchial
            XData
            CU
            Muble
            I18N
            Morphable
            Activable
            Startable
            Disposable
            Identifiable]
           [czlab.wabbit.io IoGist IoEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn getPodKeyFromEvent
  "Get the secret application key"
  ^String [^IoEvent evt] (.. evt source server podKey))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBPool
  ""
  ^JDBCPool
  [^Container co ^String gid]
  (let
    [dk (stror gid DEF_DBID)]
    (get
      (.getv (.getx co) :dbps)
      (keyword dk))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeGetDBAPI
  ""
  ^DBAPI
  [^Container co ^String gid]
  (when-some
    [p (maybeGetDBPool co gid)]
    (log/debug "acquiring from dbpool: %s" p)
    (dbopen<+> p
               (.getv (.getx co) :schema))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- releaseSysResources
  ""
  [^Container co]
  (log/info "container releasing system resources")
  (if-some
    [sc (.getv (.getx co) :core)]
    (.dispose ^Disposable sc))
  (doseq [[k v]
          (.getv (.getx co) :dbps)]
    (log/debug "shutting down dbpool %s" (name k))
    (.shutdown ^JDBCPool v)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkctr
  ""
  ^Container
  [^Execvisor parObj ^Gist gist]
  (log/info "creating a container: %s" (.id gist))
  (let
    [pid (str (.id gist) "#" (seqint2))
     rts (Cljshim/newrt (getCldr) pid)
     ctx (.getx gist)
     podPath (io/file (.getv ctx :path))
     pub (io/file podPath
                  DN_PUB DN_PAGES)
     ftlCfg (genFtlConfig :root pub)
     impl (muble<> {:emitters {}})]
    (with-meta
      (reify

        Container

        (podKeyBits [this] (bytesify (.podKey this)))
        (podKey [_] (.getv impl :digest ))
        (podDir [this] podPath)
        (cljrt [_] rts)
        (getx [_] impl)
        (version [_] (.version gist))
        (id [_] pid)
        (name [_] (.getv impl :name))

        (acquireDbPool [this gid] (maybeGetDBPool this gid))
        (acquireDbAPI [this gid] (maybeGetDBAPI this gid))

        (acquireDbPool [this] (maybeGetDBPool this ""))
        (acquireDbAPI [this] (maybeGetDBAPI this ""))

        (setParent [_ x])
        (parent [_] parObj)

        (loadTemplate [_ tpath ctx]
          (let
            [tpl (str tpath)
             ts (str (if (.startsWith tpl "/") "" "/") tpl)
             out (renderFtl ftlCfg ts ctx)]
            {:data (xdata<> out)
             :ctype
             (cond
               (.endsWith tpl ".json") "application/json"
               (.endsWith tpl ".xml") "application/xml"
               (.endsWith tpl ".html") "text/html"
               :else "text/plain")}))

        (isEnabled [_] true)

        (service [_ sid]
          (get (.getv impl :emitters)
               (keyword sid)))

        (hasService [_ sid]
          (contains? (.getv impl :emitters)
                     (keyword sid)))

        (core [_]
          (.getv impl :core))

        (podConfig [_]
          (.getv impl :podConf))

        (start [this]
          (let [svcs (.getv impl :emitters)]
            (log/info "container starting emitters...")
            (doseq [[k v] svcs]
              (log/info "emitter: %s to start" k)
              (.start ^Service v))))

        (stop [this]
          (let [svcs (.getv impl :emitters)
                pugs (.getv impl :plugins)]
            (log/info "container stopping emitters...")
            (doseq [[k v] svcs]
              (.stop ^Service v))
            (log/info "container stopping plugins...")
            (doseq [[k v] pugs]
              (.stop ^Plugin v))
            (log/info "container stopping...")))

        (dispose [this]
          (let [svcs (.getv impl :emitters)
                pugs (.getv impl :plugins)]
            (log/info "container dispose(): emitters")
            (doseq [[k v] svcs]
              (.dispose ^Service v))
            (log/info "container dispose(): plugins")
            (doseq [[k v] pugs]
              (.dispose ^Plugin v))
            (releaseSysResources this))))

    {:typeid ::Container})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- service<+>
  ""
  ^Service
  [^Container co svcType nm cfg0]
  (let
    [^Execvisor exe (.parent co)
     bks (->> :emitters
              (.getv (.getx exe)))]
    (if-some
      [^IoGist
       bk (bks svcType)]
      (let
        [cfg (merge (.impl (.getx bk)) cfg0)
         obj (service<> co svcType nm)
         pkey (.podKey co)
         hid (:handler cfg)]
        (log/info "making service %s..." svcType)
        (log/info "svc meta: %s" (meta obj))
        (log/info "config params =\n%s" cfg)
        (.init obj cfg)
        (log/info "service - ok. handler => %s" hid)
        obj)
      (trap! ServiceError
             (str "No such service: " svcType)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ioServices<>
  ""
  ^Container
  [^Container co svcs]
  (->>
    (preduce<map>
      #(let
         [[k cfg] %2
          {:keys [service
                  enabled]} cfg]
         (if-not (or (false? enabled)
                     (nil? service))
           (let [v (service<+>
                     co service k cfg)]
             (assoc! %1 (.id v) v))
           %1))
      (seq svcs))
    (.setv (.getx co) :emitters ))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The runtime container for your application
(defn container<>
  "Create an application container"
  ^Container
  [^Execvisor exe ^Gist gist]
  (doto
    (mkctr exe gist)
    (comp->init  nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseConf
  ""
  [^Container co]
  (let
    [podDir (.podDir co)
     f #(slurpXXXConf podDir % true)]
    (doto (.getx co)
      (.setv :podConf (f CFG_POD_CF)))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fmtPluginFname
  ""
  ^File
  [^Container co ^String fc]
  (->> (cs/replace fc #"[\./]+" "")
       (io/file (.podDir co) "modules" )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- pluginInited?
  ""
  [^Container co ^String fc]
  (let [b (.exists (fmtPluginFname co fc))]
    (if b
      (log/info "plugin %s %s"
                "already initialized" fc))
    b))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- postInitPlugin
  ""
  [^Container co ^String fc]
  (let [pfile (->> (.podDir co)
                   (fmtPluginFname fc))]
    (writeFile pfile "ok")
    (log/info "initialized plugin: %s" fc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- doOnePlugin
  ""
  ^Plugin
  [^Container co ^Cljshim rts pc podConf]
  (log/info "plugin->factory: %s" pc)
  (let
    [[pn opts]
     (if (string? pc)
       [pc {}] [(:name pc) pc])
     pf (cast? PluginFactory
               (.call rts pn))
     u (some-> pf
               (.createPlugin co))]
    (when (some? u)
      (.init u {:pod podConf
                :pug opts})
      (postInitPlugin co pn)
      (log/info "plugin %s starting..." pn)
      (.start u)
      (log/info "plugin %s started" pn)
      u)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeInitDBs
  ""
  [^Container co pod]
  (preduce<map>
    #(let
       [[k v] %2]
       (if-not (false? (:status v))
         (let
           [pwd (passwd<> (:passwd v)
                          (.podKey co))
            cfg (merge v
                       {:passwd (.text pwd)
                        :id k})]
           (->> (dbpool<> (dbspec<> cfg) cfg)
                (assoc! %1 k)))
         %1))
    (seq (get-in pod [:databases :jdbc]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->init
  ::Container
  [^Container co arg]

  (logcomp "comp->init" co)
  (parseConf co)
  (let
    [cpu (scheduler<> (.id co))
     rts (.cljrt co)
     pid (.id co)
     {:keys [envConf podConf]}
     (.impl (.getx co))
     mcz (get-in podConf
                 [:info :main])
     loc (-> ^Execvisor
             (.parent co)
             (.locale))
     res (->>
           (str "Resources_"
                loc
                ".properties")
           (io/file (.podDir co) DN_ETC))]
    (.setv (.getx co) :core cpu)
    (if (fileRead? res)
      (->> (loadResource res)
           (I18N/setBundle pid)))
    ;;db stuff
    (doto->>
      (maybeInitDBs co podConf)
      (.setv (.getx co) :dbps)
      (log/debug "db [dbpools]\n%s" ))
    ;;handle the plugins
    (->>
      (preduce<map>
        #(let
           [[k v] %2]
           (->>
             (doOnePlugin
               co rts v podConf)
             (assoc! %1 k)))
        (seq (:plugins podConf)))
      (.setv (.getx co) :plugins))
    ;; build the user data-models?
    (when-some+
      [dmCZ (:data-model podConf)]
      (log/info "schema-func: %s" dmCZ)
      (if-some
        [sc (cast? Schema
                   (try! (.call rts dmCZ)))]
        (.setv (.getx co) :schema sc)
        (trap! ConfigError
               "Invalid data-model schema ")))
    (.activate ^Activable cpu {})
    (->> (or (:emitters podConf) {})
         (ioServices<> co ))
    (if (hgl? mcz)
      (.call rts mcz))
    (log/info "app: %s initialized - ok" pid)
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


