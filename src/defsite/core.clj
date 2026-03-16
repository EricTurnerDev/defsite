(ns defsite.core
  (:require [defsite.config      :as config]
            [defsite.fs          :as fs]
            [defsite.markdown    :as markdown]
            [defsite.hiccup-post :as hiccup-post]
            [defsite.templates   :as tmpl]
            [defsite.search      :as search]
            [babashka.fs         :as bfs]
            [clojure.string      :as str]))

;; ---------------------------------------------------------------------------
;; Post loading

(defn- parse-post
  "Dispatch to the correct parser based on file extension."
  [^java.io.File file]
  (let [name (.getName file)]
    (cond
      (str/ends-with? name ".md")  (markdown/parse-post file)
      (str/ends-with? name ".edn") (hiccup-post/parse-post file)
      :else (throw (ex-info (str "Unknown post format: " name) {:file file})))))

(defn- load-posts [content-dir]
  (->> (fs/discover-posts content-dir)
       (mapv parse-post)
       (filterv :published)))

;; ---------------------------------------------------------------------------
;; Category aggregation

(defn- build-categories-map
  "Return {category-name [post ...]} for all posts."
  [posts]
  (reduce (fn [acc post]
            (reduce (fn [m cat]
                      (update m cat (fnil conj []) post))
                    acc
                    (:categories post)))
          {}
          posts))

;; ---------------------------------------------------------------------------
;; Build steps

(defn- prepare-output!
  "Delete and recreate the output directory for a clean build."
  [output-dir]
  (when (bfs/exists? output-dir)
    (bfs/delete-tree output-dir))
  (bfs/create-dirs output-dir))

(defn- write-post-pages! [cfg posts output-dir]
  (doseq [post posts]
    (fs/write-file output-dir
                   (str "posts/" (:slug post) "/index.html")
                   (tmpl/post-page cfg post))))

(defn- write-index-page! [cfg posts all-cats output-dir]
  (fs/write-file output-dir "index.html"
                 (tmpl/index-page cfg posts all-cats)))

(defn- write-category-pages! [cfg cats-map output-dir]
  (fs/write-file output-dir "categories/index.html"
                 (tmpl/categories-index-page cfg cats-map))
  (doseq [[cat cat-posts] cats-map]
    (fs/write-file output-dir
                   (str "categories/" (tmpl/category-slug cat) "/index.html")
                   (tmpl/category-page cfg cat cat-posts))))

(defn- write-search-index! [posts output-dir]
  (fs/write-file output-dir "search-index.json"
                 (-> posts search/build-index search/index->json)))

;; ---------------------------------------------------------------------------
;; Public entry point

(def ^:private defaults
  {:config-path   "config.edn"
   :content-dir   "content"
   :resources-dir "resources"
   :output-dir    "public"})

(defn build
  "Run the full site build pipeline.
   opts may override any of the default paths in `defaults`."
  ([]    (build {}))
  ([opts]
   (let [{:keys [config-path content-dir resources-dir output-dir]}
         (merge defaults opts)]

     (println "Loading config…")
     (let [cfg (config/load-config config-path)]
       (println (str "  Building '" (:site/title cfg) "'"))

       (println "Parsing posts…")
       (let [posts    (load-posts content-dir)
             cats-map (build-categories-map posts)
             all-cats (set (keys cats-map))]
         (println (str "  " (count posts)    " post(s) across "
                            (count cats-map) " category(s)"))

         (println "Preparing output directory…")
         (prepare-output! output-dir)

         (println "Copying static resources…")
         (fs/copy-resources resources-dir output-dir)

         (println "Rendering pages…")
         (write-index-page!     cfg posts all-cats output-dir)
         (write-post-pages!     cfg posts           output-dir)
         (write-category-pages! cfg cats-map        output-dir)

         (println "Writing search index…")
         (write-search-index! posts output-dir)

         (println (str "\nDone! "
                       (count posts) " post(s) written to "
                       output-dir "/")))))))

(defn -main [& _args]
  (try
    (build)
    (catch clojure.lang.ExceptionInfo e
      (println "\nError:" (ex-message e))
      (when-let [data (ex-data e)]
        (println "  Details:" data))
      (System/exit 1))
    (catch Exception e
      (println "\nUnexpected error:" (.getMessage e))
      (System/exit 1))))
