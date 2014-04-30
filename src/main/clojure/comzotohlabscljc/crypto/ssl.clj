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

  comzotohlabscljc.crypto.ssl

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [comzotohlabscljc.crypto.stores :only [MakeCryptoStore] ])
  (:use [comzotohlabscljc.util.core :only [NewRandom] ])
  (:use [comzotohlabscljc.crypto.core
         :only [PkcsFile? GetJksStore
                GetPkcsStore MakeSimpleTrustMgr] ])
  (:import (javax.net.ssl X509TrustManager TrustManager))
  (:import (javax.net.ssl SSLEngine SSLContext))
  (:import (com.zotohlabs.frwk.net SSLTrustMgrFactory))
  (:import (java.net URL))
  (:import (javax.net.ssl KeyManagerFactory TrustManagerFactory)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSslContext "Make a server-side SSLContext."

  ( ^SSLContext
    [^URL keyUrl ^comzotohlabscljc.crypto.codec.Password pwdObj]
    (MakeSslContext keyUrl pwdObj "TLS"))

  ( ^SSLContext
    [^URL keyUrl ^comzotohlabscljc.crypto.codec.Password pwdObj ^String flavor]
    (let [ ks (with-open [ inp (.openStream keyUrl) ]
                (if (PkcsFile? keyUrl)
                    (GetPkcsStore inp pwdObj)
                    (GetJksStore inp pwdObj)))
           cs (MakeCryptoStore ks pwdObj)
           tmf (.trustManagerFactory cs)
           kmf (.keyManagerFactory cs)
           ctx (SSLContext/getInstance flavor) ]
      (.init ctx
             (.getKeyManagers ^KeyManagerFactory kmf)
             (.getTrustManagers ^TrustManagerFactory tmf)
             (NewRandom))
      ctx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSslClientCtx "Make a client-side SSLContext."

  ^SSLContext
  [ssl]

  (if (not ssl)
      nil
      (doto (SSLContext/getInstance "TLS")
            (.init nil (SSLTrustMgrFactory/getTrustManagers) (NewRandom)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ssl-eof nil)

