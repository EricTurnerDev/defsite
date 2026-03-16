# defsite

A static site generator for a personal blog, written in Clojure.

- Posts in Markdown or Hiccup (EDN)
- Author photo and bio
- Category pages (static) + client-side category filtering
- Client-side full-text search (no backend)
- Pure static output: HTML, CSS, JS, images

## Requirements

- [Clojure CLI](https://clojure.org/guides/install_clojure) (`clj`)
- Java 11+
- Babashka

## Usage

### Build the site

```bash
bb build
```

Output is written to `public/`. Run again after any change to content, templates, or config.

### Serve locally

```bash
bb serve
```

Then open [http://localhost:3000](http://localhost:3000).

## Project structure

```
defsite/
├── config.edn              # Site title, base URL, author name/bio/photo
├── deps.edn                # Dependencies and :build alias
├── bb.edn                  # Tasks (e.g. build, serve, clean)
├── content/
│   └── posts/              # Blog posts (.md or .edn)
├── resources/
│   ├── css/style.css
│   ├── js/
│   │   ├── search.js       # Client-side search
│   │   └── filter.js       # Client-side category filter
│   └── images/
│       └── author.svg      # Replace with your photo
├── src/defsite/
│   ├── core.clj            # Build pipeline entry point
│   ├── config.clj          # Config loading and validation
│   ├── markdown.clj        # Markdown + frontmatter parsing
│   ├── hiccup_post.clj     # EDN/Hiccup post loading
│   ├── templates.clj       # HTML page templates
│   ├── search.clj          # Search index generation
│   └── fs.clj              # File system helpers
└── public/                 # Generated output (git-ignored)
```

## Writing posts

### Markdown (`.md`)

Place files in `content/posts/`. The filename should be prefixed with a date:

```
content/posts/2024-03-01-my-post.md
```

Required frontmatter fields:

```yaml
---
title: "My Post Title"
date: 2024-03-01
categories: [clojure, patterns]
summary: "One sentence shown on the index page and in search results."
---

Post body in Markdown...
```

The `slug` field is optional. If omitted, it is derived from the filename by stripping the date prefix.

### Hiccup / EDN (`.edn`)

Posts can also be written as EDN maps with a `:content` key containing a Hiccup vector. The file is loaded with `clojure.edn/read-string` — no code is evaluated.

```clojure
{:title      "My Post Title"
 :date       "2024-03-01"
 :categories ["clojure"]
 :summary    "One sentence description."

 :content
 [:article
  [:p "Write directly in " [:strong "Hiccup"] "."]
  [:pre [:code "(+ 1 2) ;; => 3"]]]}
```

Use Markdown for prose-heavy posts. Use Hiccup when you need precise HTML control or structured content.

## Configuration

Edit `config.edn` to set site-wide values:

```clojure
{:site/title       "My Blog"
 :site/base-url    "https://example.com"
 :site/description "A short description used in meta tags."
 :author/name      "Your Name"
 :author/bio       "A sentence or two about you."
 :author/photo     "/images/author.svg"}
```

Replace `resources/images/author.svg` with your own photo (any format supported by `<img>`). Update `:author/photo` to match.

## How it works

The build pipeline runs once and produces a fully static `public/` directory:

1. Load and validate `config.edn`
2. Parse all posts in `content/posts/` (`.md` and `.edn`)
3. Render `public/index.html` — all posts, sorted by date
4. Render `public/posts/{slug}/index.html` for each post
5. Render `public/categories/index.html` and one page per category
6. Write `public/search-index.json` — title, summary, and plain-text body of every post
7. Copy everything in `resources/` to `public/` verbatim

### Search

`search.js` fetches `search-index.json` on the first keypress and scores posts by how often the query appears in the title (weight 10), categories (3), summary (5), and body (1). No external library is required.

### Category filtering

The index page includes filter buttons generated at build time. `filter.js` reads the `data-categories` attribute on each post card and toggles a `.hidden` class. Each category also has its own static page at `/categories/{slug}/`, which works without JavaScript.

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| [hiccup](https://github.com/weavejester/hiccup) | 1.0.5 | HTML templating |
| [markdown-clj](https://github.com/yogthos/markdown-clj) | 1.11.4 | Markdown → HTML |
| [babashka/fs](https://github.com/babashka/fs) | 0.5.20 | File system utilities |
| [data.json](https://github.com/clojure/data.json) | 2.4.0 | Search index serialization |

No database, no server, no runtime dependencies in the output.
