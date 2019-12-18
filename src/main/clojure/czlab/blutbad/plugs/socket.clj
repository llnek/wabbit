;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.plugs.socket

  "Implementation for TCP socket service."

  (:require [czlab.basal.proc :as p]
            [czlab.basal.util :as u]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.blutbad.core :as b])

  (:import [java.net InetAddress ServerSocket Socket]
           [clojure.lang APersistentMap]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TcpConnectMsg []
  c/Idable
  (id [me] (:id me))
  c/Hierarchical
  (parent [me] (:source me)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- evt<>

  [plug ^Socket socket]

  (c/object<> TcpConnectMsg
              :socket socket
              :source plug
              :in (.getInputStream socket)
              :out (.getOutputStream socket)
              :id (str "TcpConnectMsg#" (u/seqint2))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- sock-it

  [plug soc]

  (c/try! (c/debug "opened soc: %s." soc)
          (b/dispatch (evt<> plug soc))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- ssoc<>

  ^ServerSocket
  [{:keys [timeoutMillis backlog host port]}]

  (let [ip (if (c/nichts? host)
             (InetAddress/getLocalHost)
             (InetAddress/getByName host))]
    (assert (c/spos? port)
            (str "Bad socket port: " port))
    (c/do-with
      [soc (ServerSocket. port
                          (int (c/num?? backlog 100)) ip)]
      (.setReuseAddress soc true)
      (c/info "Server socket %s (bound?) %s" soc (.isBound soc)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defrecord TCPPlugin [server _id info conf]
  c/Hierarchical
  (parent [_] server)
  c/Idable
  (id [_] _id)
  c/Startable
  (start [me]
    (c/start me nil))
  (start [me _]
    (let [ssoc (ssoc<> conf)]
      (p/async!
        #(while (and ssoc
                     (not (.isClosed ssoc)))
           (try (sock-it me (.accept ssoc))
                (catch Throwable t
                  (if-not (c/hasic-all?
                            (u/emsg t)
                            ["closed" "socket"])
                    (c/warn t "socket error"))))))
      (assoc me :soc ssoc)))
  (stop [me]
    (i/klose (:soc me))
    (assoc me :soc nil))
  c/Finzable
  (finz [me] (c/stop me))
  c/Initable
  (init [me arg]
    (update-in me
               [:conf]
               #(-> (c/merge+ % arg)
                    b/expand-vars* b/prevar-cfg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def TCPSpec
  {:conf {:$pluggable ::socket<>
          :host ""
          :port 7551
          :$error nil
          :$action nil}
   :info {:version "1.0.0"
          :name "TCP Socket Server"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn socket<>

  ([_ id]
   (socket<> _ id TCPSpec))

  ([ctr id {:keys [info conf]}]
   (TCPPlugin. ctr id info conf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


