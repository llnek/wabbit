;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for TCP socket service."
      :author "Kenneth Leung"}

  czlab.wabbit.io.socket

  (:require [czlab.xlib.process :refer [async!]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.io :refer [closeQ]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.io.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.base.core])

  (:import [java.net InetAddress ServerSocket Socket]
           [czlab.xlib LifeCycle]
           [czlab.wabbit.io IoService SocketEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evt<>
  ""
  [co {:keys [^Socket socket]}]

  (let [eeid (str "event#" (seqint2))]
    (with-meta
      (reify SocketEvent
        (checkAuthenticity [_] false)
        (id [_] eeid)
        (sockOut [_] (.getOutputStream socket))
        (sockIn [_] (.getInputStream socket))
        (source [_] co)
        (dispose [_] (closeQ socket)))
      {:typeid ::SocketEvent})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown
  ""
  [co soc]
  (try!
    (log/debug "opened socket: %s" soc)
    (dispatch! (evt<> co {:socket soc}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ssoc<>
  ""
  ^ServerSocket
  [{:keys [timeoutMillis backlog host port]}]
  (let
    [ip (if (hgl? host)
          (InetAddress/getByName host)
          (InetAddress/getLocalHost))
     _ (test-pos "socket port" port)
     soc (ServerSocket. port
                        (int (or backlog 100)) ip)]
    (log/info "Server socket %s (bound?) %s" soc (.isBound soc))
    (.setReuseAddress soc true)
    soc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn SocketIO
  ""
  ^LifeCycle
  [co {:keys [conf] :as spec}]
  (let
    [see (keyword (juid))
     impl (muble<>)]
    (reify
      LifeCycle
      (init [_ arg]
        (.copyEx impl (merge conf arg)))
      (config [_] (.intern impl))
      (parent [_] co)
      (start [_ _]
        (when-some
          [ss (ssoc<> (.intern impl))]
          (.setv impl see ss)
          (async!
            #(while (not (.isClosed ss))
               (try
                 (sockItDown co (.accept ss))
                 (catch Throwable _
                   (.unsetv impl see) (closeQ ss))))
            {:cl (getCldr)})))
      (stop [_]
        (when-some [s (.getv impl see)]
          (.unsetv impl see)
          (closeQ s))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF


