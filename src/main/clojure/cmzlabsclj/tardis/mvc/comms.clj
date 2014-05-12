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

  cmzlabsclj.tardis.mvc.comms

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.string :as cstr])
  (:use [cmzlabsclj.util.core :only [MubleAPI Try! NiceFPath] ])
  (:use [cmzlabsclj.tardis.io.triggers])
  (:use [cmzlabsclj.tardis.io.http :only [HttpBasicConfig] ])
  (:use [cmzlabsclj.tardis.io.netty])
  (:use [cmzlabsclj.tardis.io.core])
  (:use [cmzlabsclj.tardis.core.sys])
  (:use [cmzlabsclj.tardis.core.constants])
  (:use [cmzlabsclj.tardis.mvc.templates
         :only [GetLocalFile ReplyFileAsset] ])
  (:use [cmzlabsclj.util.str :only [hgl? nsb strim] ])
  (:use [cmzlabsclj.util.meta :only [MakeObj] ])
  (:import (com.zotohlabs.gallifrey.mvc HTTPErrorHandler
                                        MVCUtils WebAsset WebContent))
  (:import (com.zotohlabs.frwk.core Hierarchial Identifiable))
  (:import (com.zotohlabs.gallifrey.io HTTPEvent Emitter))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import [com.zotohlabs.frwk.netty NettyFW])
  (:import (java.util Date))
  (:import (java.io File))
  (:import (com.zotohlabs.frwk.io XData))
  (:import (io.netty.handler.codec.http HttpRequest HttpResponseStatus HttpResponse
                                        CookieDecoder ServerCookieEncoder
                                        DefaultHttpResponse HttpVersion
                                        HttpServerCodec HttpMessage
                                        HttpHeaders LastHttpContent
                                        HttpHeaders Cookie QueryStringDecoder))
  (:import (io.netty.buffer Unpooled))
  (:import (io.netty.channel Channel ChannelHandler
                             ChannelPipeline ChannelHandlerContext))
  (:import (com.zotohlabs.frwk.netty NettyFW))
  (:import (com.google.gson JsonObject))
  (:import (jregex Matcher Pattern)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- isModified ""

  [^String eTag lastTm ^JsonObject info]

  (with-local-vars [ modd true ]
    (cond
      (HasHeader? info "if-none-match")
      (var-set modd (not= eTag (GetHeader info "if-none-match")))

      (HasHeader? info "if-unmodified-since")
      (if-let [ s (GetHeader info "if-unmodified-since") ]
          (Try! (when (>= (.getTime (.parse (MVCUtils/getSDF) s)) lastTm)
                      (var-set modd false))))
      :else nil)
    @modd
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AddETag ""

  [ ^cmzlabsclj.tardis.core.sys.Element src
    ^JsonObject info
    ^File file
    funcSetter ]

  (let [ maxAge (.getAttr src :cacheMaxAgeSecs)
         lastTm (.lastModified file)
         eTag  (str "\""  lastTm  "-" (.hashCode file)  "\"") ]
    (if (isModified eTag lastTm info)
        (funcSetter :header "last-modified"
                    (.format (MVCUtils/getSDF) (Date. lastTm)))
        (if (= (-> (.get info "method")(.getAsString)) "GET")
            (funcSetter :status HttpResponseStatus/NOT_MODIFIED)))
    (funcSetter :header "cache-control"
                (if (= maxAge 0) "no-cache" (str "max-age=" maxAge)))
    (when (.getAttr src :useETag) (funcSetter :header "etag" eTag))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- XXXaddETag ""

  [ ^cmzlabsclj.tardis.core.sys.Element src
    ^HTTPEvent evt
    ^JsonObject info
    ^HttpResponse rsp
    ^File file ]

  (let [ maxAge (.getAttr src :cacheMaxAgeSecs)
         lastTm (.lastModified file)
         eTag  (str "\""  lastTm  "-"
                    (.hashCode file)  "\"") ]
    (if (isModified eTag lastTm info)
        (HttpHeaders/setHeader rsp "last-modified"
                  (.format (MVCUtils/getSDF) (Date. lastTm)))
        (if (= (-> (.get info "method")(.getAsString)) "GET")
            (.setStatus rsp HttpResponseStatus/NOT_MODIFIED)))
    (HttpHeaders/setHeader rsp "cache-control"
                (if (= maxAge 0) "no-cache" (str "max-age=" maxAge)))
    (when (.getAttr src :useETag) (HttpHeaders/setHeader rsp "etag" eTag))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reply-error ""

  [^Emitter src code]

  (let [ ctr (.container src)
         appDir (.getAppDir ctr) ]
    (GetLocalFile appDir (str "pages/errors/" code ".html"))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeError ""

  [ ^cmzlabsclj.tardis.core.sys.Element src
    ^Channel ch
    code ]

  (with-local-vars [ rsp (NettyFW/makeHttpReply code) bits nil wf nil]
    (try
      (let [ h (.getAttr src :errorHandler)
             ^HTTPErrorHandler
             cb (if (hgl? h) (MakeObj h) nil)
             ^WebContent
             rc (if (nil? cb)
                    (reply-error src code)
                    (.getErrorResponse cb code)) ]
        (when-not (nil? rc)
          (HttpHeaders/setHeader ^HttpMessage @rsp "content-type" (.contentType rc))
          (var-set bits (.body rc)))
        (HttpHeaders/setContentLength @rsp
                                      (if (nil? @bits) 0 (alength ^bytes @bits)))
        (var-set wf (.writeAndFlush ch @rsp))
        (when-not (nil? @bits)
          (var-set wf (.writeAndFlush ch (Unpooled/wrappedBuffer ^bytes @bits))))
        (NettyFW/closeCF @wf false))
      (catch Throwable e#
        (NettyFW/closeChannel ch)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- handleStatic ""

  [src ^Channel ch info ^HTTPEvent evt ^File file]

  (let [ rsp (NettyFW/makeHttpReply ) ]
    (try
      (if (or (nil? file)
              (not (.exists file)))
        (ServeError src ch 404)
        (do
          (log/debug "serving static file: " (NiceFPath file))
          (XXXaddETag src evt info rsp file)
          ;; 304 not-modified
          (if (= (-> rsp (.getStatus)(.code)) 304)
            (do
              (HttpHeaders/setContentLength rsp 0)
              (NettyFW/closeCF (.writeAndFlush ch rsp) (.isKeepAlive evt) ))
            (ReplyFileAsset src ch info rsp file))))
      (catch Throwable e#
        (log/error "failed to get static resource " (.getUri evt) e#)
        (Try!  (ServeError src ch 500))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn __ServeStatic ""

  [ ^Emitter src
    ^cmzlabsclj.net.routes.RouteInfo ri
    ^Matcher mc ^Channel ch info ^HTTPEvent evt]

  (let [ ^File appDir (-> src (.container)(.getAppDir))
         mpt (nsb (.getf ^cmzlabsclj.util.core.MubleAPI ri :mountPoint))
         ps (NiceFPath (File. appDir ^String DN_PUBLIC))
         uri (.getUri evt)
         gc (.groupCount mc) ]
    (with-local-vars [ mp (StringUtils/replace mpt "${app.dir}" (NiceFPath appDir)) ]
      (if (> gc 1)
        (doseq [ i (range 1 gc) ]
          (var-set mp (StringUtils/replace ^String @mp "{}" (.group mc (int i)) 1))) )

      ;; ONLY serve static assets from *public folder*
      (var-set mp (NiceFPath (File. ^String @mp)))
      (log/debug "request to serve static file: " @mp)

      (if (.startsWith ^String @mp ps)
        (handleStatic src ch info evt (File. ^String @mp))
        (do
          (log/warn "attempt to access non public file-system: " @mp)
          (ServeError src ch 403))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeStatic ""

  [ ^Emitter src
    ri
    ^Matcher mc ^Channel ch info ^HTTPEvent evt]

  (let [ mpt (nsb (.getf ^cmzlabsclj.util.core.MubleAPI ri :mountPoint))
         ^File appDir (-> src (.container)(.getAppDir))
         ps (NiceFPath (File. appDir ^String DN_PUBLIC))
         gc (.groupCount mc) ]
    (with-local-vars [ mp (StringUtils/replace mpt "${app.dir}" (NiceFPath appDir)) ]
      (if (> gc 1)
        (doseq [ i (range 1 gc) ]
          (var-set mp (StringUtils/replace ^String @mp "{}" (.group mc (int i)) 1))) )
      (var-set mp (NiceFPath (File. ^String @mp)))
      (let [ ^cmzlabsclj.tardis.io.core.EmitterAPI co src
             ^cmzlabsclj.tardis.io.core.WaitEventHolder
             w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
        (.timeoutMillis w (.getAttr ^cmzlabsclj.tardis.core.sys.Element
                            src :waitMillis))
        (.hold co w)
        (.dispatch co evt { :info info
                            :path @mp } )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn ServeRoute ""

  [ ^cmzlabsclj.tardis.core.sys.Element src
    ^cmzlabsclj.net.routes.RouteInfo ri
    ^Matcher mc
    ^Channel ch
    ^cmzlabsclj.util.core.MubleAPI evt]

  (let [ pms (.collect ri mc)
         options { :router (.getHandler ri)
                   :params (merge {} pms)
                   :template (.getTemplate ri) } ]
    (let [ ^cmzlabsclj.tardis.io.core.EmitterAPI co src
           ^cmzlabsclj.tardis.io.core.WaitEventHolder
           w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
      (.timeoutMillis w (.getAttr src :waitMillis))
      (.hold co w)
      (.dispatch co evt options))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private comms-eof nil)
