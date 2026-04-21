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

(defn- load-posts [content-dir show-unpublished?]
  (->> (fs/discover-posts content-dir)
       (mapv parse-post)
       (filterv #(or show-unpublished? (:published %)))))

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

(defn- write-post-pages! [cfg posts output-dir watch?]
  (let [sorted (sort-by (juxt :date :slug) posts)
        n      (count sorted)]
    (doseq [[i post] (map-indexed vector sorted)]
      (let [prev-post (when (pos? i)          (nth sorted (dec i)))
            next-post (when (< i (dec n))     (nth sorted (inc i)))]
        (fs/write-file output-dir
                       (str "posts/" (:slug post) "/index.html")
                       (tmpl/post-page cfg post prev-post next-post watch?))))))

(defn- write-index-page! [cfg posts all-cats output-dir watch?]
  (fs/write-file output-dir "index.html"
                 (tmpl/index-page cfg posts all-cats watch?)))

(defn- write-category-pages! [cfg cats-map output-dir watch?]
  (fs/write-file output-dir "categories/index.html"
                 (tmpl/categories-index-page cfg cats-map watch?))
  (doseq [[cat cat-posts] cats-map]
    (fs/write-file output-dir
                   (str "categories/" (tmpl/category-slug cat) "/index.html")
                   (tmpl/category-page cfg cat cat-posts watch?))))

(defn- write-search-index! [posts output-dir]
  (fs/write-file output-dir "search-index.json"
                 (-> posts search/build-index search/index->json)))

(defn- write-about-page! [cfg output-dir watch?]
  (fs/write-file output-dir "about/index.html"
                 (tmpl/about-page cfg watch?)))

(defn- write-404-page! [cfg output-dir watch?]
  (fs/write-file output-dir "404.html"
                 (tmpl/not-found-page cfg watch?)))

(defn- write-sitemap! [cfg posts cats-map output-dir]
  (fs/write-file output-dir "sitemap.xml"
                 (tmpl/sitemap-xml cfg posts cats-map)))

;; ---------------------------------------------------------------------------
;; Public entry point

(def ^:private defaults
  {:ssg-config-path   "config.edn"
   :config-path       nil
   :ssg-resources-dir "resources"
   :content-dir       "content"
   :resources-dir     "resources"
   :output-dir        "public"
   :show-unpublished  false
   :watch             false})

(defn build
  "Run the full site build pipeline.
   opts may override any of the default paths in `defaults`.
   ssg-config-path / ssg-resources-dir are the SSG's own files, used as
   fallbacks when the site directory does not supply its own."
  ([]    (build {}))
  ([opts]
   (let [{:keys [ssg-config-path config-path ssg-resources-dir
                 content-dir resources-dir output-dir show-unpublished watch]}
         (merge defaults opts)]

     (println "Loading config…")
     (let [cfg (config/load-config ssg-config-path config-path)]
       (println (str "  Building '" (:site/title cfg) "'"))

       (println "Parsing posts…")
       (let [posts    (load-posts content-dir show-unpublished)
             cats-map (build-categories-map posts)
             all-cats (set (keys cats-map))]
         (println (str "  " (count posts)    " post(s) across "
                            (count cats-map) " category(s)"))

         (println "Preparing output directory…")
         (prepare-output! output-dir)

         (println "Copying static resources…")
         ;; Copy SSG defaults first, then overlay site-specific files so that
         ;; any resource present in the site directory takes precedence.
         (fs/copy-resources ssg-resources-dir output-dir)
         (fs/copy-resources resources-dir output-dir)

         (println "Rendering pages…")
         (write-index-page!     cfg posts all-cats output-dir watch)
         (write-post-pages!     cfg posts           output-dir watch)
         (write-category-pages! cfg cats-map        output-dir watch)
         (write-about-page!     cfg                 output-dir watch)
         (write-404-page!       cfg                 output-dir watch)

         (println "Writing search index…")
         (write-search-index! posts output-dir)

         (println "Writing sitemap…")
         (write-sitemap! cfg posts cats-map output-dir)

         (println (str "\nDone! "
                       (count posts) " post(s) written to "
                       output-dir "/")))))))

(defn -main [& args]
  ;; ssg-config-path and ssg-resources-dir are not passed here; they come from
  ;; `defaults` ("config.edn" / "resources" relative to CWD, i.e. the SSG dir).
  (try
    (let [show-unpublished? (boolean (some #{"--show-unpublished"} args))
          watch?            (boolean (some #{"--watch"} args))
          pos-args          (remove  #{"--show-unpublished" "--watch"} args)
          d                 (or (first pos-args) ".")]
      (build {:config-path      (str d "/config.edn")
              :content-dir      (str d "/content")
              :resources-dir    (str d "/resources")
              :output-dir       (str d "/public")
              :show-unpublished show-unpublished?
              :watch            watch?}))
    (catch clojure.lang.ExceptionInfo e
      (println "\nError:" (ex-message e))
      (when-let [data (ex-data e)]
        (println "  Details:" data))
      (System/exit 1))
    (catch Exception e
      (println "\nUnexpected error:" (.getMessage e))
      (System/exit 1))))
