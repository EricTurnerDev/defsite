/**
 * theme.js — light/dark mode toggle
 *
 * Reads system preference via matchMedia, respects a localStorage override,
 * and wires up the #theme-toggle button.
 *
 * The inline script in <head> already sets data-theme before first paint to
 * prevent flash; this file handles the interactive toggle and button label.
 */
(function () {
  "use strict";

  function getStoredTheme() {
    try { return localStorage.getItem("theme"); } catch (_) { return null; }
  }

  function storeTheme(theme) {
    try { localStorage.setItem("theme", theme); } catch (_) {}
  }

  function systemTheme() {
    return window.matchMedia("(prefers-color-scheme: dark)").matches
      ? "dark"
      : "light";
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    storeTheme(theme);
    updateButton(theme);
  }

  function updateButton(theme) {
    var btn = document.getElementById("theme-toggle");
    if (!btn) return;
    if (theme === "dark") {
      btn.setAttribute("aria-label", "Switch to light mode");
    } else {
      btn.setAttribute("aria-label", "Switch to dark mode");
    }
  }

  var current = getStoredTheme() || systemTheme();
  // Ensure data-theme is set (may already be set by inline head script).
  document.documentElement.setAttribute("data-theme", current);

  document.addEventListener("DOMContentLoaded", function () {
    updateButton(document.documentElement.getAttribute("data-theme") || current);

    var btn = document.getElementById("theme-toggle");
    if (!btn) return;
    btn.addEventListener("click", function () {
      var next = document.documentElement.getAttribute("data-theme") === "dark"
        ? "light"
        : "dark";
      applyTheme(next);
    });
  });

}());
