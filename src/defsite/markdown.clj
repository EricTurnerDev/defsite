(ns defsite.markdown
  (:require [markdown.core :as md]
            [clojure.string :as str]))

(def ^:private frontmatter-re
  "Matches YAML frontmatter delimited by --- on its own line."
  #"(?s)\A---\r?\n(.*?)\r?\n---\r?\n?(.*)")

(def ^:private required-fields [:title :date :categories :summary])

;; ---------------------------------------------------------------------------
;; Minimal YAML frontmatter parser
;;
;; Supports the subset used in post files:
;;   key: "quoted string"
;;   key: unquoted string
;;   key: 2024-01-15
;;   key: [item1, item2, item3]
;;
;; Does NOT support multi-line values or nested maps — we don't need them.

(defn- parse-inline-seq
  "Parse '[val1, val2]' into [\"val1\" \"val2\"]."
  [s]
  (-> s
      (str/replace #"^\[|\]$" "")
      (str/split    #",")
      (->> (mapv (fn [item]
                   (-> item
                       str/trim
                       (str/replace #"^[\"']|[\"']$" "")))))))

(defn- parse-value [raw]
  (let [v (str/trim raw)]
    (cond
      (str/starts-with? v "[") (parse-inline-seq v)
      (re-matches #"[\"'].*[\"']" v) (subs v 1 (dec (count v)))
      :else v)))

(defn- parse-frontmatter
  "Parse a YAML frontmatter string into a keyword-keyed map."
  [text]
  (into {}
        (for [line  (str/split-lines text)
              :let  [colon (str/index-of line ":")]
              :when (and colon (pos? colon))]
          (let [k (-> line (subs 0 colon) str/trim keyword)
                v (parse-value (subs line (inc colon)))]
            [k v]))))

;; ---------------------------------------------------------------------------
;; Date parsing

(defn- parse-date
  "Coerce a frontmatter date value to java.time.LocalDate."
  [v]
  (when v (java.time.LocalDate/parse (str v))))

;; ---------------------------------------------------------------------------
;; File-level parsing

(defn- split-frontmatter
  "Split raw post text into {:frontmatter string :body string}.
   Throws with a clear message when no frontmatter delimiters are found."
  [text filename]
  (if-let [[_ fm body] (re-find frontmatter-re text)]
    {:frontmatter fm :body body}
    (throw (ex-info (str "No YAML frontmatter found in " filename)
                    {:file filename}))))

(defn- slug-from-filename
  "Derive a URL slug from the file name.
   '2024-01-01-my-great-post.md' → 'my-great-post'"
  [filename]
  (-> filename
      (str/replace #"^\d{4}-\d{2}-\d{2}-" "")
      (str/replace #"\.md$" "")))

(defn- validate-frontmatter!
  "Throw when a required metadata field is absent."
  [fm filename]
  (doseq [k required-fields]
    (when-not (contains? fm k)
      (throw (ex-info (str filename " is missing required field: " (name k))
                      {:file filename :missing k}))))
  fm)

(defn parse-post
  "Parse a Markdown post file into the unified post map.
   Frontmatter is a YAML subset; the body is converted to HTML by markdown-clj."
  [^java.io.File file]
  (let [filename   (.getName file)
        raw        (slurp file)
        {:keys [frontmatter body]} (split-frontmatter raw filename)
        fm         (parse-frontmatter frontmatter)
        _          (validate-frontmatter! fm filename)
        slug       (or (some-> (:slug fm) str not-empty)
                       (slug-from-filename filename))
        date       (parse-date (:date fm))
        categories (if (vector? (:categories fm))
                     (mapv str (:categories fm))
                     [(str (:categories fm))])]
    {:title      (str (:title fm))
     :date       date
     :categories categories
     :summary    (str (:summary fm))
     :slug       slug
     :url        (str "/posts/" slug "/")
     :body-html  (md/md-to-html-string body)
     :source     :markdown}))
