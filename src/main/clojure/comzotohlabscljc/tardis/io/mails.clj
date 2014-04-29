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

  comzotohlabscljc.tardis.io.mails

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.crypto.codec :only [Pwdify] ])
  (:use [comzotohlabscljc.util.seqnum :only [NextLong] ])
  (:use [comzotohlabscljc.util.core :only [TryC notnil?] ])
  (:use [comzotohlabscljc.util.str :only [hgl? nsb] ])
  (:use [comzotohlabscljc.tardis.core.sys])
  (:use [comzotohlabscljc.tardis.io.loops ])
  (:use [comzotohlabscljc.tardis.io.core ])

  (:import (javax.mail Flags Flags$Flag Store Folder Session Provider Provider$Type))
  (:import (java.util Properties))
  (:import (javax.mail.internet MimeMessage))
  (:import (java.io IOException))
  (:import (com.zotohlabs.gallifrey.io EmailEvent))
  (:import (com.zotohlabs.frwk.core Identifiable)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeFolder ""
  
  [^Folder fd]

  (TryC
    (when-not (nil? fd)
      (when (.isOpen fd) (.close fd true))) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeStore ""
  
  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^Store conn (.getAttr co :store)
         ^Folder fd (.getAttr co :folder) ]
    (closeFolder fd)
    (TryC
      (when-not (nil? conn) (.close conn)) )
    (.setAttr! co :store nil)
    (.setAttr! co :folder nil)
  ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolve-provider ""

  [^comzotohlabscljc.tardis.core.sys.Element co 
   protos 
   ^String demo ^String mock]

  (let [ [^String pkey ^String sn]  protos
         props (doto (Properties.)
                     (.put  "mail.store.protocol" sn) )
         session (Session/getInstance props nil)
         ps (.getProviders session) ]
    (with-local-vars [proto sn sun nil]
      (var-set sun (some (fn [^Provider x] (if (= pkey (.getClassName x)) x nil)) (seq ps)))
      (when (nil? @sun)
        (throw (IOException. (str "Failed to find store: " pkey) )))
      (when (hgl? demo)
        (var-set sun  (Provider. Provider$Type/STORE mock demo "test" "1.0.0"))
        (log/debug "using demo store " mock " !!!")
        (var-set proto mock) )

      (.setProvider session @sun)
      (.setAttr! co :proto @proto)
      (.setAttr! co :session session))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ctor-email-event ""
  
  [co msg]

  (let [ eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        EmailEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (emitter [_] co)
        (getMsg [_] msg))

      { :typeid :czc.tardis.io/EmailEvent } 
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POP3
(def ST_POP3S  "com.sun.mail.pop3.POP3SSLStore" )
(def ST_POP3  "com.sun.mail.pop3.POP3Store")
(def POP3_MOCK "demopop3s")
(def POP3S "pop3s")
(def POP3C "pop3")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakePOP3Client "" 
  
  [container]

  (MakeEmitter container :czc.tardis.io/POP3))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/POP3

  [co & args]

  (ctor-email-event co (first args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connect-pop3 ""
  
  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ pwd (nsb (.getAttr co :pwd))
         ^Session session (.getAttr co :session)
         user (.getAttr co :user)
         ^String host (.getAttr co :host)
         ^long port (.getAttr co :port)
         ^String proto (.getAttr co :proto)
         s (.getStore session proto) ]
    (when-not (nil? s)
      (.connect s host port user (if (hgl? pwd) pwd nil))
      (.setAttr! co :store s)
      (.setAttr! co :folder (.getDefaultFolder s)))
    (if-let [ ^Folder fd (.getAttr co :folder) ]
      (.setAttr! co :folder (.getFolder fd "INBOX")))
    (let [ ^Folder fd (.getAttr co :folder) ]
      (when (or (nil? fd) (not (.exists fd)))
        (throw (IOException. "cannot find inbox.")) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- read-pop3 ""
  
  [^comzotohlabscljc.tardis.io.core.EmitterAPI co msgs]

  (let [^comzotohlabscljc.tardis.core.sys.Element src co]
    (doseq [ ^MimeMessage mm (seq msgs) ]
      (try
          (doto mm (.getAllHeaders)(.getContent))
          (.dispatch co (IOESReifyEvent co mm) {} )
        (finally
          (when (.getAttr src :deleteMsg)
            (.setFlag mm Flags$Flag/DELETED true)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scan-pop3 ""
  
  [^comzotohlabscljc.tardis.core.sys.Element co]

  (let [ ^Store s (.getAttr co :store)
         ^Folder fd (.getAttr co :folder) ]
    (when (and (notnil? fd) (not (.isOpen fd)))
      (.open fd Folder/READ_WRITE) )
    (when (.isOpen fd)
      (let [ cnt (.getMessageCount fd) ]
        (log/debug "Count of new mail-messages: " cnt)
        (when (> cnt 0)
          (read-pop3 co (.getMessages fd)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableOneLoop :czc.tardis.io/POP3

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (try
      (connect-pop3 co)
      (scan-pop3 co)
    (catch Throwable e#
      (warn e# ""))
    (finally
      (closeStore co))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- std-config ""
  
  [^comzotohlabscljc.tardis.core.sys.Element co cfg]

  (let [ intv (:interval-secs cfg)
         port (:port cfg)
         pkey (:hhh.pkey cfg)
         pwd (:passwd cfg) ]
    (.setAttr! co :intervalMillis (* 1000 (if (number? intv) intv 300)))
    (.setAttr! co :ssl (if (false? (:ssl cfg)) false true))
    (.setAttr! co :deleteMsg (true? (:deletemsg cfg)))
    (.setAttr! co :host (:host cfg))
    (.setAttr! co :port (if (number? port) port 995))
    (.setAttr! co :user (:username cfg))
    (.setAttr! co :pwd (Pwdify (if (hgl? pwd) pwd "") pkey) )
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/POP3

  [^comzotohlabscljc.tardis.core.sys.Element co cfg]

  (let [ demo (System/getProperty "skaro.demo.pop3" "") ]
    (std-config co cfg)
    (resolve-provider co
                      (if (.getAttr co :ssl)
                          [ST_POP3S POP3S]
                          [ST_POP3 POP3C])
                      demo POP3_MOCK)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IMAP
(def ST_IMAPS "com.sun.mail.imap.IMAPSSLStore" )
(def ST_IMAP "com.sun.mail.imap.IMAPStore" )
(def IMAP_MOCK "demoimaps")
(def IMAPS "imaps" )
(def IMAP "imap" )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeIMAPClient "" ""
  
  [container]

  (MakeEmitter container :czc.tardis.io/IMAP))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/IMAP

  [co & args]

  (ctor-email-event co (first args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connect-imap ""
  
  [co] 
  
  (connect-pop3 co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- read-imap ""
  
  [co msgs] 
  
  (read-pop3 co msgs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scan-imap ""
  
  [co] 
  
  (scan-pop3 co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableOneLoop :czc.tardis.io/IMAP

  [^comzotohlabscljc.tardis.core.sys.Element co]

  (try
      (connect-imap co)
      (scan-imap co)
    (catch Throwable e#
      (log/warn e# ""))
    (finally
      (closeStore co))
  ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/IMAP

  [^comzotohlabscljc.tardis.core.sys.Element co cfg]

  (let [ demo (System/getProperty "skaro.demo.imap" "") ]
    (std-config co cfg)
    (resolve-provider co
                      (if (.getAttr co :ssl)
                          [ST_IMAPS IMAPS]
                          [ST_IMAP IMAP])
                      demo IMAP_MOCK)
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private mails-eof nil)

