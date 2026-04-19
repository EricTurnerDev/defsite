(ns defsite.fs
  (:require [babashka.fs :as fs]))

(defn write-file
  "Write content to output-dir/rel-path, creating intermediate directories."
  [output-dir rel-path content]
  (let [dest (fs/path output-dir rel-path)]
    (fs/create-dirs (fs/parent dest))
    (spit (str dest) content)))

(defn copy-resources
  "Recursively copy every file from resources-dir into output-dir,
   preserving the relative path structure."
  [resources-dir output-dir]
  (when (fs/exists? resources-dir)
    (doseq [src  (fs/glob resources-dir "**")
            :when (fs/regular-file? src)]
      (let [rel  (fs/relativize resources-dir src)
            dest (fs/path output-dir rel)]
        (fs/create-dirs (fs/parent dest))
        (fs/copy src dest {:replace-existing true})))))

(defn discover-posts
  "Return a sorted seq of java.io.File for all .md and .edn files
   found anywhere under content-dir/posts/ (recursive)."
  [content-dir]
  (let [posts-path (fs/path content-dir "posts")]
    (if (fs/exists? posts-path)
      (->> (concat (fs/glob posts-path "**.md")
                   (fs/glob posts-path "**.edn"))
           (sort-by str)
           (map fs/file))
      [])))
