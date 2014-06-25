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

  cmzlabsclj.tardis.io.webss

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:require [clojure.data.json :as json])
  (:use [cmzlabsclj.nucleus.util.core
         :only [MubleAPI ConvLong notnil? juid ternary MakeMMap Bytesify] ])
  (:use [cmzlabsclj.nucleus.crypto.core :only [GenMac] ])
  (:use [cmzlabsclj.nucleus.util.str :only [nsb hgl? AddDelim!] ])
  ;;(:use [cmzlabsclj.nucleus.util.guids :only [NewUUid] ])
  (:use [cmzlabsclj.nucleus.net.comms :only [GetFormFields] ])

  (:import (com.zotohlab.gallifrey.runtime ExpiredError AuthError))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (org.apache.commons.codec.net URLCodec))
  (:import (org.apache.commons.codec.binary Hex))
  (:import (com.zotohlab.frwk.util CoreUtils))
  (:import (java.net HttpCookie URLDecoder URLEncoder))
  (:import (com.zotohlab.gallifrey.io HTTPResult HTTPEvent IOSession Emitter))
  (:import (com.zotohlab.gallifrey.core Container))
  (:import (com.zotohlab.frwk.net ULFormItems ULFileItem)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private SESSION_COOKIE "__ss001" )
(def ^:private SSID_FLAG ":__f01ec")
(def ^:private CS_FLAG ":__f184f" ) ;; creation time
(def ^:private LS_FLAG ":__f384f" ) ;; last access time
(def ^:private ES_FLAG ":__f484f" ) ;; expiry time
(def ^:private NV_SEP "\u0000")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol WebSession

  ""

  (setMaxInactiveInterval [_ idleSecs] )
  (setAttribute [_ k v] )
  (getAttribute [_ k] )
  (removeAttribute [_ k] )
  (clear! [_] )
  (listAttributes [_] )
  (isNew? [_] )
  (isNull? [_])
  (isSSL? [_] )
  (invalidate! [_] )
  (setNew! [_ flag] )
  (getCreationTime [_]  )
  (getExpiryTime [_])
  (getId [_] )
  (getLastError [_])
  (getLastAccessedTime [_] )
  (getMaxInactiveInterval [_] ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resetFlags ""

  [ ^cmzlabsclj.tardis.io.webss.WebSession mvs maxAge]

  (let [ now (System/currentTimeMillis) ]
    (.setAttribute mvs SSID_FLAG
                   (Hex/encodeHexString (Bytesify (juid))))
    (.setAttribute mvs ES_FLAG (+ now (* maxAge 1000)))
    (.setAttribute mvs CS_FLAG now)
    (.setAttribute mvs LS_FLAG now)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeMacIt ""

  [^HTTPEvent evt
   ^Container ctr ^String data]

  (if (.checkAuthenticity evt)
    (str (GenMac (.getAppKeyBits ctr) data) "-" data)
    data
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- downstream ""

  [ ^HTTPEvent evt ^HTTPResult res ]

  (let [ ^cmzlabsclj.tardis.io.webss.WebSession
         mvs (.getSession evt) ]
    (if-not (.isNull? mvs)
      (let [ ^cmzlabsclj.tardis.core.sys.Element
             src (.emitter evt)
             ctr (.container ^Emitter src)
             du2 (.setMaxInactiveInterval mvs
                                          (.getAttr src :maxIdleSecs))
             du1 (if (.isNew? mvs)
                     (resetFlags mvs (.getAttr src :sessionAgeSecs)))
             data (-> (URLCodec. "utf-8")
                      (.encode (maybeMacIt evt ctr (nsb mvs))))
             ck (HttpCookie. SESSION_COOKIE data) ]
      (doto ck
            (.setDomain (nsb (.getAttr src :domain)))
            (.setSecure (.isSSL? mvs))
            (.setHttpOnly (.getAttr src :hidden))
            (.setMaxAge (/ (.getExpiryTime mvs) 1000))
            (.setPath (.getAttr src :domainPath)))
      (.addCookie res ck)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeValidateCookie  ""

  [^HTTPEvent evt ^Container ctr
   ^String part1 ^String part2 ]

  (if-let [ pkey (if (.checkAuthenticity evt)
                     (.getAppKeyBits ctr)
                     nil) ]
    (when (not= (GenMac pkey part2) part1)
      (log/error "Session cookie - broken.")
      (throw (AuthError. "Bad Session Cookie.")))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- upstream ""

  [ ^HTTPEvent evt ]

  (let [ ^cmzlabsclj.tardis.io.webss.WebSession
         mvs (.getSession evt)
         ^Emitter netty (.emitter evt)
         ck (.getCookie evt SESSION_COOKIE) ]
    (if (nil? ck)
      (.invalidate! mvs)
      (let [ ^cmzlabsclj.tardis.core.sys.Element
             src netty
             cookie (nsb (.getValue ck))
             ;;cookie (-> (URLCodec. "utf-8")
                        ;;(decode (nsb (.getValue ck))))
             pos (.indexOf cookie (int \-))
             [rc1 rc2] (if (< pos 0)
                           ["" cookie]
                           [(.substring cookie 0 pos)
                            (.substring cookie (+ pos 1) )] ) ]
        (maybeValidateCookie evt (.container netty) rc1 rc2)
        (log/debug "session attributes = " rc2)
        (try
          (doseq [ [k v] (seq (json/read-str rc2 :key-fn keyword)) ]
            (.setAttribute mvs k v))
          (catch Throwable e#
            (throw (ExpiredError. "Corrupted cookie."))))
        (.setNew! mvs false)
        (let [ ts (ConvLong (nsb (.getAttribute mvs LS_FLAG)) -1)
               es (ConvLong (nsb (.getAttribute mvs ES_FLAG)) -1)
               now (System/currentTimeMillis)
               mi (.getAttr src :maxIdleSecs) ]
          (if (< es now)
            (throw (ExpiredError. "Session has expired.")))
          (if (and (> mi 0)
                   (< (+ ts mi) now))
            (throw (ExpiredError. "Session has been inactive too long.")))
          (.setAttribute mvs LS_FLAG now))
        ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeWSSession ""

  ^IOSession
  [co ssl]

  (let [ attrs (MakeMMap)
         impl (MakeMMap) ]
    (.setf! impl :maxIdleSecs 0)
    (.setf! impl :newOne true)
    (with-meta
      (reify WebSession

        (setAttribute [_ k v] (.setf! attrs k v))
        (getAttribute [_ k] (.getf attrs k) )
        (removeAttribute [_ k] (.clrf! attrs k) )
        (clear! [_] (.clear! attrs))
        (listAttributes [_] (.seq* attrs))

        (setMaxInactiveInterval [_ idleSecs]
          (when (number? idleSecs)
            (.setf! impl :maxIdleSecs idleSecs)))

        (isNull? [_] (= (count (.seq* impl)) 0))
        (isNew? [_] (.getf impl :newOne))
        (isSSL? [_] ssl)

        (invalidate! [_]
          (.clear! attrs)
          (.clear! impl))

        (setNew! [_ flag]
          (if flag
            (do
              (.clear! attrs)
              (.clear! impl)
              (.setf! impl :maxIdleSecs 0)
              (.setf! impl :newOne true))
            (.setf! impl :newOne false)))

        (getMaxInactiveInterval [_] (.getf impl :maxIdleSecs))
        (getCreationTime [_]  (.getf attrs CS_FLAG))
        (getExpiryTime [_]  (.getf attrs ES_FLAG))
        (getId [_] (.getf attrs SSID_FLAG))

        (getLastAccessedTime [_] (.getf attrs LS_FLAG))
        (getLastError [_] (.getf impl :error))

        Object

        (toString [this]
          (nsb (reduce (fn [sum en]
                           (AddDelim! sum NV_SEP
                                      (str (name (first en))
                                           ":"
                                           (last en))))
                       (StringBuilder.)
                       (seq (.listAttributes this)))))

        IOSession

        (handleResult [this evt res] (downstream evt res))
        (handleEvent [this evt] (upstream evt))
        (getImpl [_] nil))

        { :typeid :czc.tardis.io/WebSession }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private webss-eof nil)

