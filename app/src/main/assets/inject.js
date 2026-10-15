(function () {
  if (window.__ibInjected) return;
  window.__ibInjected = true;
  var N = window.iBrowserNative;
  if (!N) return;

  // ---------------- Cosmetic filtering ----------------
  function addCss() {
    try {
      if (!N.cosmeticEnabled()) return;
      var css = N.css(location.hostname || "");
      if (!css) return;
      var st = document.createElement("style");
      st.id = "__ib_cosmetic";
      st.textContent = css;
      (document.head || document.documentElement).appendChild(st);
    } catch (e) {}
  }
  if (document.documentElement) addCss();
  else document.addEventListener("readystatechange", addCss, { once: true });

  // Remove common full-screen overlays / scroll locks placed by ad walls
  function antiOverlay() {
    try {
      if (!N.cosmeticEnabled()) return;
      var els = document.querySelectorAll('div[class*="overlay"],div[id*="overlay"],div[class*="interstitial"],div[class*="popup"],div[id*="popup"]');
      for (var i = 0; i < els.length; i++) {
        var el = els[i], cs = getComputedStyle(el);
        var txt = (el.className + " " + el.id).toLowerCase();
        if ((cs.position === "fixed") && parseInt(cs.zIndex || "0") > 999 &&
            /(ad|promo|subscribe|adblock|interstitial)/.test(txt) && el.offsetHeight > innerHeight * 0.5) {
          el.remove();
          document.documentElement.style.overflow = "";
          if (document.body) document.body.style.overflow = "";
        }
      }
    } catch (e) {}
  }

  // ---------------- YouTube ad skipper ----------------
  function ytLoop() {
    try {
      var player = document.querySelector(".html5-video-player, #movie_player");
      var v = document.querySelector("video");
      if (player && v && (player.classList.contains("ad-showing") || player.classList.contains("ad-interrupting"))) {
        v.muted = true;
        if (isFinite(v.duration) && v.duration > 0) v.currentTime = v.duration - 0.1;
        v.playbackRate = 16;
      }
      var skip = document.querySelector(".ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytm-skip-ad-button, button[class*='skip-ad']");
      if (skip) skip.click();
      var overlayClose = document.querySelector(".ytp-ad-overlay-close-button");
      if (overlayClose) overlayClose.click();
    } catch (e) {}
  }
  if (/(^|\.)youtube\.com$/.test(location.hostname)) setInterval(ytLoop, 300);

  // ---------------- Media sniffer ----------------
  var seen = {};
  var MEDIA_RE = /\.(m3u8|mpd|mp4|m4v|webm|mkv|mov|flv|3gp|mp3|m4a|aac|ogg|oga|opus|wav|flac)(\?|#|$)/i;
  var MEDIA_CT = /(mpegurl|dash\+xml|^video\/|^audio\/)/i;
  function abs(u) { try { return new URL(u, location.href).href; } catch (e) { return u; } }
  function report(url, ct, from) {
    try {
      if (!url || !N.sniffEnabled()) return;
      url = abs(url);
      if (url.indexOf("blob:") === 0 || url.indexOf("data:") === 0) return;
      // ignore individual stream segments
      if (/\.(ts|m4s|m4f|cmfv|cmfa)(\?|#|$)/i.test(url)) return;
      if (/(segment|chunk|frag)[-_]?\d+/i.test(url) && !/\.(m3u8|mpd)/i.test(url)) return;
      var key = url + "|" + (ct || "");
      if (seen[key]) return;
      seen[key] = 1;
      N.media(url, ct || "", from || "", document.title || "", location.href);
    } catch (e) {}
  }

  // XHR hook
  try {
    var oOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (m, u) {
      this.__ibUrl = u;
      try {
        if (MEDIA_RE.test(String(u))) report(String(u), "", "xhr");
      } catch (e) {}
      this.addEventListener("readystatechange", function () {
        try {
          if (this.readyState === 2) {
            var ct = this.getResponseHeader("content-type") || "";
            if (MEDIA_CT.test(ct)) report(this.responseURL || this.__ibUrl, ct, "xhr");
          }
        } catch (e) {}
      });
      return oOpen.apply(this, arguments);
    };
  } catch (e) {}

  // fetch hook
  try {
    var oFetch = window.fetch;
    if (oFetch) {
      window.fetch = function (input, init) {
        var u = typeof input === "string" ? input : (input && input.url) || "";
        try { if (MEDIA_RE.test(u)) report(u, "", "fetch"); } catch (e) {}
        return oFetch.apply(this, arguments).then(function (res) {
          try {
            var ct = res.headers.get("content-type") || "";
            if (MEDIA_CT.test(ct)) report(res.url || u, ct, "fetch");
          } catch (e) {}
          return res;
        });
      };
    }
  } catch (e) {}

  // DOM media elements
  function scan() {
    try {
      var els = document.querySelectorAll("video, audio, source, video source, audio source");
      for (var i = 0; i < els.length; i++) {
        var el = els[i];
        var src = el.currentSrc || el.src || el.getAttribute("src") || el.getAttribute("data-src");
        var kind = el.tagName === "AUDIO" || (el.parentElement && el.parentElement.tagName === "AUDIO") ? "audio/*" : "video/*";
        if (src) report(src, el.type || kind, "dom");
      }
      var og = document.querySelector('meta[property="og:video"],meta[property="og:video:url"],meta[property="og:video:secure_url"]');
      if (og && og.content) report(og.content, "video/*", "meta");
    } catch (e) {}
  }
  document.addEventListener("play", function (e) {
    var t = e.target; if (t && (t.currentSrc || t.src)) report(t.currentSrc || t.src, t.tagName === "AUDIO" ? "audio/*" : "video/*", "play");
  }, true);
  document.addEventListener("loadedmetadata", function (e) {
    var t = e.target; if (t && (t.currentSrc || t.src)) report(t.currentSrc || t.src, t.tagName === "AUDIO" ? "audio/*" : "video/*", "meta");
  }, true);

  function onReady() {
    scan(); antiOverlay();
    setInterval(scan, 2500);
    setTimeout(antiOverlay, 2000);
    try {
      new MutationObserver(function () { clearTimeout(window.__ibScanT); window.__ibScanT = setTimeout(scan, 600); })
        .observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", onReady);
  else onReady();
})();
