(ns defsite.templates
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Helpers

(defn category-slug
  "Normalise a category name to a URL-safe slug.
   'Functional Programming' → 'functional-programming'"
  [cat]
  (-> cat str/lower-case (str/replace #"[\s/]+" "-")))

(defn- format-date [^java.time.LocalDate date]
  (when date
    (.format date (java.time.format.DateTimeFormatter/ofPattern "MMMM d, yyyy"))))

;; Leaf component: returns a hiccup vector so it can be safely nested
;; inside any other h/html call without double-escaping.
(defn- category-tag [cat]
  [:a.category-tag {:href (str "/categories/" (category-slug cat) "/")} cat])

;; ---------------------------------------------------------------------------
;; Shell components — each returns an HTML string.
;;
;; We do NOT nest these inside a single top-level h/html call, because
;; hiccup 1.x escapes all string children. Instead, we concatenate strings
;; so that pre-rendered HTML (e.g. from markdown-clj) is injected verbatim.

(def ^:private theme-init-script
  ;; Runs synchronously before first paint to prevent flash of wrong theme.
  ;; Reads localStorage; falls back to system preference.
  (str "<script>"
       "(function(){"
       "var t=null;"
       "try{t=localStorage.getItem('theme');}catch(e){}"
       "if(!t){t=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';}"
       "document.documentElement.setAttribute('data-theme',t);"
       "})();"
       "</script>"))

(defn- head-html [config title description]
  ;; Inject theme-init before </head> so it runs synchronously, preventing
  ;; flash of wrong theme. hiccup 1.x escapes raw strings, so we use
  ;; str/replace on the rendered HTML to splice the script tag in.
  (-> (h/html
        [:head
         [:meta {:charset "utf-8"}]
         [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
         [:title (str title " — " (:site/title config))]
         (when description [:meta {:name "description" :content description}])
         [:link {:rel "stylesheet" :href "/css/style.css"}]])
      (str/replace "</head>" (str theme-init-script "</head>"))))

(defn- site-header-html [config]
  (h/html
    [:header.site-header
     [:nav.site-nav
      [:a.site-title {:href "/"} (:site/title config)]
      [:ul.nav-links
       [:li [:a {:href "/"} "Posts"]]
       [:li [:a {:href "/categories/"} "Categories"]]]
      [:button#theme-toggle {:type "button" :aria-label "Toggle theme"}
       "Dark mode"]]]))

(defn- sidebar-html [config]
  (h/html
    [:aside.sidebar
     [:div.author-card
      [:img.author-photo {:src    (:author/photo config)
                          :alt    (:author/name config)
                          :width  "80"
                          :height "80"}]
      [:h3.author-name (:author/name config)]
      [:p.author-bio   (:author/bio config)]]
     [:div.sidebar-search
      [:h4 "Search"]
      [:input#search-input {:type         "search"
                            :placeholder  "Search posts\u2026"
                            :autocomplete "off"
                            :aria-label   "Search posts"}]
      [:div#search-results {:role "region" :aria-live "polite"}]]]))

(defn- footer-html [config]
  (h/html
    [:footer.site-footer
     [:p "\u00A9 " (.getYear (java.time.LocalDate/now)) " " (:author/name config)]]))

;; ---------------------------------------------------------------------------
;; Page assembly
;;
;; main-html is a raw HTML string injected verbatim into <main>.
;; This is the only safe way in hiccup 1.x to embed pre-rendered HTML —
;; rather than fighting the macro's escaping, we concatenate strings.

(defn- page-html [config title description main-html]
  (str "<!DOCTYPE html>\n"
       "<html lang=\"en\">"
       (head-html config title description)
       "<body>"
       (site-header-html config)
       "<div class=\"site-body\">"
       "<main class=\"main-content\">"
       main-html
       "</main>"
       (sidebar-html config)
       "</div>"
       (footer-html config)
       (h/html [:script {:src "/js/search.js" :defer true}])
       (h/html [:script {:src "/js/filter.js" :defer true}])
       (h/html [:script {:src "/js/theme.js"  :defer true}])
       "</body></html>"))

;; ---------------------------------------------------------------------------
;; Post card component

(defn- post-card [post]
  "Render a post summary card to an HTML string."
  (h/html
    [:article.post-card
     {:data-categories (str/join "," (map category-slug (:categories post)))}
     [:h2.post-card-title
      [:a {:href (:url post)} (:title post)]]
     [:div.post-card-meta
      [:time {:datetime (str (:date post))} (format-date (:date post))]
      [:span.post-card-categories
       (map category-tag (:categories post))]]
     [:p.post-card-summary (:summary post)]]))

;; ---------------------------------------------------------------------------
;; Page renderers

(defn index-page
  "Main listing page with category filter buttons and post cards."
  [config posts all-categories]
  (let [sorted (sort-by :date #(compare %2 %1) posts)
        controls-html (h/html
                        [:div.index-controls
                         [:div.category-filters
                          [:button.filter-btn.active
                           {:data-filter "all" :type "button"} "All"]
                          (for [cat (sort all-categories)]
                            [:button.filter-btn
                             {:data-filter (category-slug cat) :type "button"}
                             cat])]])
        cards-html    (str/join "" (map post-card sorted))
        main-html     (str controls-html
                           "<div id=\"post-list\" class=\"post-list\">"
                           cards-html
                           "</div>")]
    (page-html config "Posts" (:site/description config) main-html)))

(defn post-page
  "Individual post page.
   Uses a sentinel string to inject the markdown body without hiccup escaping it."
  [config post]
  ;; Hiccup will not escape a plain alphanumeric sentinel, so we can
  ;; safely substitute the raw markdown HTML after rendering.
  (let [sentinel "__POST_BODY__"
        article-html (-> (h/html
                           [:article.post
                            [:header.post-header
                             [:h1.post-title (:title post)]
                             [:div.post-meta
                              [:time {:datetime (str (:date post))}
                               (format-date (:date post))]
                              [:span.post-categories
                               (map category-tag (:categories post))]]]
                            [:div.post-body sentinel]
                            [:footer.post-footer
                             [:a {:href "/"} "\u2190 Back to all posts"]]])
                         (str/replace sentinel (:body-html post)))]
    (page-html config (:title post) (:summary post) article-html)))

(defn category-page
  "Post listing for a single category."
  [config category posts]
  (let [sorted    (sort-by :date #(compare %2 %1) posts)
        cards-html (str/join "" (map post-card sorted))
        main-html  (str (h/html
                          [:div.category-header
                           [:h1 category]
                           [:p.post-count
                            (count posts) " post"
                            (when (not= 1 (count posts)) "s")]
                           [:a.back-link {:href "/categories/"} "\u2190 All categories"]])
                        "<div class=\"post-list\">"
                        cards-html
                        "</div>")]
    (page-html config
               (str "Category: " category)
               (str "Posts tagged \u201C" category "\u201D")
               main-html)))

(defn categories-index-page
  "Page listing all categories with post counts."
  [config categories-map]
  (let [main-html (h/html
                    [:div.categories-header [:h1 "Categories"]]
                    [:ul.categories-list
                     (for [[cat cat-posts]
                           (sort-by (comp str/lower-case first) categories-map)]
                       [:li.category-item
                        [:a {:href (str "/categories/" (category-slug cat) "/")} cat]
                        [:span.post-count " (" (count cat-posts) ")"]])])]
    (page-html config "Categories" "All post categories" main-html)))
