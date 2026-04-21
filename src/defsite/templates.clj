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

(defn- head-html [config title description canonical-url]
  ;; Inject theme-init before </head> so it runs synchronously, preventing
  ;; flash of wrong theme. hiccup 1.x escapes raw strings, so we use
  ;; str/replace on the rendered HTML to splice the script tag in.
  (-> (h/html
        [:head
         [:meta {:charset "utf-8"}]
         [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
         [:title (str title " — " (:site/title config))]
         (when description [:meta {:name "description" :content description}])
         [:link {:rel "canonical" :href canonical-url}]
         [:link {:rel "stylesheet" :href "/css/style.css"}]])
      (str/replace "</head>" (str theme-init-script "</head>"))))

(defn- nav-link [href label current-path]
  (let [active? (if (= href "/")
                  (= current-path "/")
                  (str/starts-with? current-path href))
        attrs   (cond-> {:href href}
                  active? (assoc :class "nav-current"))]
    [:li [:a attrs label]]))

(defn- site-header-html [config current-path]
  (h/html
    [:header.site-header
     [:nav.site-nav
      [:a.site-logo-link {:href "/about/"}
       [:img.site-logo {:src    (:author/photo config)
                        :alt    (:author/name config)
                        :width  "32"
                        :height "32"}]]
      [:a.site-title {:href "/"} (:site/title config)]
      [:ul.nav-links
       (nav-link "/about/"       "About"      current-path)
       (nav-link "/"             "Posts"      current-path)
       (nav-link "/categories/"  "Categories" current-path)]
      [:button#theme-toggle {:type "button" :aria-label "Switch to dark mode"}
       [:span.theme-icon.theme-icon-sun {:aria-hidden "true"}
        [:svg {:xmlns "http://www.w3.org/2000/svg" :width "20" :height "20" :viewBox "0 0 24 24"
               :fill "none" :stroke "currentColor" :stroke-width "2"
               :stroke-linecap "round" :stroke-linejoin "round"}
         [:circle {:cx "12" :cy "12" :r "4"}]
         [:path {:d "M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"}]]]
       [:span.theme-icon.theme-icon-moon {:aria-hidden "true"}
        [:svg {:xmlns "http://www.w3.org/2000/svg" :width "20" :height "20" :viewBox "0 0 24 24"
               :fill "none" :stroke "currentColor" :stroke-width "2"
               :stroke-linecap "round" :stroke-linejoin "round"}
         [:path {:d "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"}]]]]]]))

(def ^:private search-html
  (h/html
    [:div.site-search
     [:input#search-input {:type         "search"
                           :placeholder  "Search posts\u2026"
                           :autocomplete "off"
                           :aria-label   "Search posts"}]
     [:div#search-results {:role "region" :aria-live "polite"}]]))

(defn- footer-html [config]
  (h/html
    [:footer.site-footer
     [:p "\u00A9 " (.getYear (java.time.LocalDate/now)) " " (:author/name config) "." " " "All Rights Reserved. Published with " [:a {:href "https://github.com/EricTurnerDev/defsite" :target "_blank"} "defsite"] "."]]))

;; ---------------------------------------------------------------------------
;; Page assembly
;;
;; main-html is a raw HTML string injected verbatim into <main>.
;; This is the only safe way in hiccup 1.x to embed pre-rendered HTML —
;; rather than fighting the macro's escaping, we concatenate strings.

(def ^:private livereload-script
  (str "<script>"
       "(function(){"
       "var v=null;"
       "function poll(){"
       "fetch('/livereload.json?_='+Date.now())"
       ".then(function(r){return r.ok?r.json():Promise.reject();})"
       ".then(function(d){"
       "if(v===null){v=d.v;}"
       "else if(d.v!==v){location.reload();return;}"
       "setTimeout(poll,500);"
       "})"
       ".catch(function(){"
       "if(v!==null){setTimeout(poll,500);}"
       "});}"
       "poll();"
       "})();"
       "</script>"))

(defn- page-html [config title description canonical-url current-path main-html watch?]
  (str "<!DOCTYPE html>\n"
       "<html lang=\"en\">"
       (head-html config title description canonical-url)
       "<body>"
       (site-header-html config current-path)
       "<div class=\"site-body\">"
       search-html
       "<main class=\"main-content\">"
       main-html
       "</main>"
       "</div>"
       (footer-html config)
       (h/html [:script {:src "/js/search.js" :defer true}])
       (h/html [:script {:src "/js/filter.js" :defer true}])
       (h/html [:script {:src "/js/theme.js"  :defer true}])
       (when watch? livereload-script)
       "</body></html>"))

;; ---------------------------------------------------------------------------
;; Post card component

(defn- post-card [post]
  "Render a post summary card to an HTML string."
  (h/html
    [:article.post-card
     {:data-categories (str/join "," (map category-slug (:categories post)))}
     [:h2.post-card-title
      [:a {:href (:url post)} (:title post)]
      (when-not (:published post)
        [:span.draft-badge "Draft"])]
     [:div.post-card-meta
      [:time {:datetime (str (:date post))} (format-date (:date post))]
      [:span.post-card-categories
       (map category-tag (:categories post))]]
     [:p.post-card-summary (:summary post)]]))

;; ---------------------------------------------------------------------------
;; Page renderers

(defn index-page
  "Main listing page with category filter buttons and post cards."
  [config posts all-categories watch?]
  (let [sorted (sort-by (juxt :date :slug) #(compare %2 %1) posts)
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
    (page-html config "Posts" (:site/description config)
               (str (:site/base-url config) "/") "/"
               main-html watch?)))

(defn post-page
  "Individual post page.
   Uses a sentinel string to inject the markdown body without hiccup escaping it."
  [config post prev-post next-post watch?]
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
                              (when-not (:published post)
                                [:span.draft-badge "Draft"])
                              [:span.post-categories
                               (map category-tag (:categories post))]]]
                            [:div.post-body sentinel]
                            [:footer.post-footer
                             [:nav.post-nav
                              (if prev-post
                                [:a.post-nav-prev {:href (:url prev-post)}
                                 (str "\u2190 " (:title prev-post))]
                                [:span.post-nav-placeholder])
                              (if next-post
                                [:a.post-nav-next {:href (:url next-post)}
                                 (str (:title next-post) " \u2192")]
                                [:span.post-nav-placeholder])]
                             [:a.post-back {:href "/"} "\u2190 Back to all posts"]]])
                         (str/replace sentinel (:body-html post)))]
    (page-html config (:title post) (:summary post)
               (str (:site/base-url config) (:url post)) (:url post)
               article-html watch?)))

(defn category-page
  "Post listing for a single category."
  [config category posts watch?]
  (let [sorted    (sort-by (juxt :date :slug) #(compare %2 %1) posts)
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
               (str (:site/base-url config) "/categories/" (category-slug category) "/")
               (str "/categories/" (category-slug category) "/")
               main-html watch?)))

(defn sitemap-xml
  "Generate a sitemap.xml string covering all public pages."
  [config posts categories-map]
  (let [base     (:site/base-url config)
        url      (fn [path lastmod]
                   (str "<url>"
                        "<loc>" base path "</loc>"
                        (when lastmod (str "<lastmod>" lastmod "</lastmod>"))
                        "</url>"))
        entries  (concat
                   [(url "/" nil)
                    (url "/about/" nil)
                    (url "/categories/" nil)]
                   (for [[cat _] categories-map]
                     (url (str "/categories/" (category-slug cat) "/") nil))
                   (for [post (sort-by :date #(compare %2 %1) posts)]
                     (url (:url post) (str (:date post)))))]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
         "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n"
         (str/join "\n" entries)
         "\n</urlset>\n")))

(defn about-page
  "Author bio page at /about/."
  [config watch?]
  (let [main-html (h/html
                    [:div.about-page
                     [:img.about-photo {:src    (:author/photo config)
                                        :alt    (:author/name config)
                                        :width  "120"
                                        :height "120"}]
                     [:h1.about-name (:author/name config)]
                     [:p.about-bio   (:author/bio config)]])]
    (page-html config "About" (str "About " (:author/name config))
               (str (:site/base-url config) "/about/") "/about/"
               main-html watch?)))

(defn not-found-page
  "Styled 404 page served when a URL has no matching file."
  [config watch?]
  (let [main-html (h/html
                    [:div.not-found
                     [:h1 "404"]
                     [:p "Sorry, the page you\u2019re looking for doesn\u2019t exist."]
                     [:a {:href "/"} "\u2190 Back to all posts"]])]
    (page-html config "Page Not Found" "The requested page was not found."
               (str (:site/base-url config) "/404") "/404"
               main-html watch?)))

(defn categories-index-page
  "Page listing all categories with post counts."
  [config categories-map watch?]
  (let [main-html (h/html
                    [:div.categories-header [:h1 "Categories"]]
                    [:ul.categories-list
                     (for [[cat cat-posts]
                           (sort-by (comp str/lower-case first) categories-map)]
                       [:li.category-item
                        [:a {:href (str "/categories/" (category-slug cat) "/")} cat]
                        [:span.post-count " (" (count cat-posts) ")"]])])]
    (page-html config "Categories" "All post categories"
               (str (:site/base-url config) "/categories/") "/categories/"
               main-html watch?)))
