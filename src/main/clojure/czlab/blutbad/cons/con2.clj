;; Copyright © 2013-2020, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns czlab.blutbad.cons.con2

  (:require [clojure.java.io :as io]
            [clojure.string :as cs]
            [czlab.basal.io :as i]
            [czlab.basal.core :as c]
            [czlab.basal.util :as u]
            [czlab.blutbad.core :as b])

  (:import [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;doing this to get rid reflection warning from stencil
;seems to work
(comment
(binding
  [*warn-on-reflection* false]
  (require '[stencil.core :as sc])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;simulate what lein new template does
(defn create-pod

  "Create a new blutbad application."
  {:arglists '([name & args])}
  [name & args]

  (c/prn!! "Generating new 'blutbad' project...")
  (try (let [dir (c/_2 (drop-while
                         #(c/!eq? "--to-dir" %) args))
             options {:renderer-fn nil ;sc/render-string
                      :force? (some? (c/_1 (drop-while
                                             #(c/!eq? "--force" %) args)))
                      :dir (or dir (-> (u/get-user-dir) (io/file name) .getPath))}]
         ;;(c/prn!! "opts = %s" options)
         ;TODO:
         ;(apply ws/new<> name options args)
         )
       (catch Throwable t
         (c/prn!! "Failed to generate project.\n%s." (u/emsg t)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn publish-samples

  "Generate all samples."
  {:arglists '([outDir])}
  [outDir] )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

