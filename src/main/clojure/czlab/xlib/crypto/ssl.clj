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

(ns ^{:doc ""
      :author "kenl" }

  czlab.xlib.crypto.ssl

  (:require
    [czlab.xlib.crypto.stores :refer [CryptoStore*]]
    [czlab.xlib.util.core :refer [NewRandom]]
    [czlab.xlib.util.logging :as log]
    [czlab.xlib.crypto.core
    :refer [PkcsFile? GetJksStore GetPkcsStore ]])

  (:import
    [javax.net.ssl X509TrustManager TrustManager]
    [javax.net.ssl SSLEngine SSLContext]
    [com.zotohlab.frwk.net SSLTrustMgrFactory]
    [com.zotohlab.frwk.crypto PasswordAPI CryptoStoreAPI]
    [java.net URL]
    [javax.net.ssl KeyManagerFactory TrustManagerFactory]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SslContext*

  "Make a server-side SSLContext"

  ^SSLContext
  [^URL keyUrl
   ^PasswordAPI pwdObj & [flavor] ]

  (let
    [ks (with-open
          [inp (.openStream keyUrl) ]
          (if (PkcsFile? keyUrl)
            (GetPkcsStore inp pwdObj)
            (GetJksStore inp pwdObj)))
     cs (CryptoStore* ks pwdObj)
     tmf (.trustManagerFactory cs)
     kmf (.keyManagerFactory cs)
     ctx (->> (str (or flavor "TLS"))
              (SSLContext/getInstance )) ]
    (.init ctx
           (.getKeyManagers kmf)
           (.getTrustManagers tmf)
           (NewRandom))
    ctx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SslClientCtx*

  "Make a client-side SSLContext"

  ^SSLContext
  [ssl]

  (when ssl
    (doto (SSLContext/getInstance "TLS")
          (.init nil (SSLTrustMgrFactory/getTrustManagers) (NewRandom)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

