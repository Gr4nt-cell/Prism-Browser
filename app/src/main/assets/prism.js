(function () {
  'use strict';
  if (window.__prismInjected) return;
  try { Object.defineProperty(window, '__prismInjected', { value: true }); } catch (e) { window.__prismInjected = true; }
  var B = window.PrismBridge;
  if (!B) return;
  var isTop = window === window.top;

  /* ------------------------------------------------------------ cosmetic filtering */
  var cssText = '';
  try { cssText = B.css(location.hostname || '') || ''; } catch (e) {}
  function applyCss() {
    if (!cssText) return;
    var root = document.head || document.documentElement;
    if (!root) return;
    var st = document.getElementById('__prism_css');
    if (!st) {
      st = document.createElement('style');
      st.id = '__prism_css';
      st.textContent = cssText;
    }
    if (st.parentNode !== root || root.lastElementChild !== st) root.appendChild(st);
  }
  if (cssText) {
    applyCss();
    document.addEventListener('DOMContentLoaded', applyCss, true);
    window.addEventListener('load', applyCss, true);
    try {
      var reapply = 0;
      new MutationObserver(function () {
        if (reapply) return;
        reapply = setTimeout(function () { reapply = 0; if (!document.getElementById('__prism_css')) applyCss(); }, 300);
      }).observe(document.documentElement, { childList: true, subtree: false });
    } catch (e) {}
  }

  /* ------------------------------------------------------------ generic .class / #id hiding (on demand) */
  if (cssText) {
    var seenTok = Object.create(null);
    var genStyle = null;
    var queue = [];
    var timer = 0;
    var visit = function (el, out) {
      if (el.id && typeof el.id === 'string') { var t = '#' + el.id; if (!seenTok[t]) { seenTok[t] = 1; out.push(t); } }
      var cl = el.classList;
      if (cl) for (var i = 0; i < cl.length; i++) { var c = '.' + cl[i]; if (!seenTok[c]) { seenTok[c] = 1; out.push(c); } }
    };
    var flush = function () {
      timer = 0;
      var nodes = queue; queue = [];
      var out = [];
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i];
        if (!n || n.nodeType !== 1) continue;
        visit(n, out);
        if (n.querySelectorAll) {
          var all = n.querySelectorAll('[id],[class]');
          for (var j = 0; j < all.length; j++) visit(all[j], out);
        }
      }
      if (!out.length) return;
      var css = '';
      try { css = B.genericCss(location.hostname || '', out.join(' ')); } catch (e) {}
      if (!css) return;
      if (!genStyle) { genStyle = document.createElement('style'); genStyle.id = '__prism_gen'; }
      genStyle.textContent += css;
      var root = document.head || document.documentElement;
      if (root && genStyle.parentNode !== root) root.appendChild(genStyle);
    };
    var enqueue = function (n) { queue.push(n); if (!timer) timer = setTimeout(flush, queue.length > 1 ? 120 : 0); };
    var startSurvey = function () {
      enqueue(document.documentElement);
      try {
        new MutationObserver(function (muts) {
          for (var i = 0; i < muts.length; i++) {
            var m = muts[i];
            if (m.type === 'attributes') { enqueue(m.target); continue; }
            for (var j = 0; j < m.addedNodes.length; j++) if (m.addedNodes[j].nodeType === 1) enqueue(m.addedNodes[j]);
          }
        }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'id'] });
      } catch (e) {}
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', startSurvey); else startSurvey();
  }

  /* ------------------------------------------------------------ pop-up / pop-under blocking */
  var enabled = true;
  try { enabled = B.popupBlocking(); } catch (e) {}
  var lastClick = { t: 0, legit: false, href: null };

  function closestTag(el, sel) {
    while (el && el.nodeType === 1) {
      if (el.matches && el.matches(sel)) return el;
      el = el.parentElement;
    }
    return null;
  }

  function isOverlay(el) {
    try {
      var r = el.getBoundingClientRect();
      var vw = window.innerWidth, vh = window.innerHeight;
      var area = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0)) * Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
      if (area < vw * vh * 0.45) return false;
      var cs = getComputedStyle(el);
      var txt = (el.innerText || '').trim();
      var hasMedia = el.querySelector && el.querySelector('img,video,picture,svg,canvas');
      var transparent = parseFloat(cs.opacity) < 0.1 || ((cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent') && !cs.backgroundImage.match(/url/));
      var positioned = cs.position === 'fixed' || cs.position === 'absolute';
      return positioned && transparent && !txt && !hasMedia;
    } catch (e) { return false; }
  }

  function originOf(u) { try { return new URL(u, location.href).origin; } catch (e) { return ''; } }

  function onUserClick(e) {
    if (!e.isTrusted) return;
    var t = e.target;
    var a = closestTag(t, 'a[href]');
    var btn = closestTag(t, 'button,input[type=submit],input[type=button],[role=button],[role=link],[role=menuitem],label,summary,select,[onclick]');
    lastClick.t = Date.now();
    lastClick.href = a ? a.href : null;
    lastClick.legit = !!(a || btn);
    if (enabled && a && isOverlay(a)) {
      // invisible full-page link layered over the content: classic click-jacking ad
      e.preventDefault(); e.stopImmediatePropagation();
      try { a.style.setProperty('pointer-events', 'none', 'important'); a.style.setProperty('display', 'none', 'important'); } catch (x) {}
      try { B.overlayBlocked(a.href || ''); } catch (x) {}
      lastClick.legit = false;
    }
  }
  document.addEventListener('click', onUserClick, true);
  document.addEventListener('pointerdown', function (e) {
    if (!enabled || !e.isTrusted) return;
    var a = closestTag(e.target, 'a[href]');
    if (a && isOverlay(a)) { try { a.style.setProperty('pointer-events', 'none', 'important'); } catch (x) {} }
  }, true);

  // Programmatic (untrusted) clicks on links that open new windows
  document.addEventListener('click', function (e) {
    if (!enabled || e.isTrusted) return;
    var a = closestTag(e.target, 'a[href]');
    if (!a) return;
    var newWin = (a.target && a.target !== '_self' && a.target !== '_top' && a.target !== '_parent');
    if (newWin && originOf(a.href) !== location.origin) {
      e.preventDefault(); e.stopImmediatePropagation();
      try { B.popupBlocked(a.href); } catch (x) {}
    }
  }, true);

  function fakeWindow() {
    var noop = function () {};
    var doc = { write: noop, writeln: noop, open: noop, close: noop, body: null, createElement: function () { return {}; } };
    return { closed: false, close: noop, focus: noop, blur: noop, postMessage: noop, document: doc, location: { href: 'about:blank', replace: noop, assign: noop }, opener: null };
  }

  if (enabled) {
    var realOpen = window.open;
    var guardedOpen = function (url, name, features) {
      var u = url ? String(url) : '';
      var now = Date.now();
      var recent = now - lastClick.t < 1200;
      var ok = recent && lastClick.legit;
      // pop-under trick: user clicked link A but the script opens a different page B
      if (ok && lastClick.href && u) {
        var target = originOf(u), clicked = originOf(lastClick.href);
        if (target && clicked && target !== clicked && lastClick.href.indexOf('javascript:') !== 0 && lastClick.href.slice(-1) !== '#') ok = false;
      }
      if (!ok) {
        try { B.popupBlocked(u); } catch (e) {}
        return fakeWindow();
      }
      lastClick.t = 0; // one window per click
      return realOpen.apply(window, arguments);
    };
    try {
      Object.defineProperty(window, 'open', { value: guardedOpen, writable: false, configurable: false });
    } catch (e) { window.open = guardedOpen; }

  }

  /* ------------------------------------------------------------ video detection */
  var seen = {};
  function report(src, kind) {
    if (!src || typeof src !== 'string') return;
    if (src.indexOf('blob:') === 0 || src.indexOf('data:') === 0 || src.indexOf('about:') === 0) return;
    try { src = new URL(src, location.href).href; } catch (e) { return; }
    if (seen[src]) return;
    seen[src] = 1;
    var title = '';
    try { title = (window.top.document.title || document.title || ''); } catch (e) { title = document.title || ''; }
    try { B.media(src, kind || 'video', title, location.href); } catch (e) {}
  }
  function scan(root) {
    try {
      var els = (root || document).querySelectorAll('video,audio,video source,audio source');
      for (var i = 0; i < els.length; i++) {
        var el = els[i];
        var kind = (el.tagName === 'AUDIO' || (el.parentElement && el.parentElement.tagName === 'AUDIO')) ? 'audio' : 'video';
        report(el.currentSrc || el.src, kind);
        if (el.getAttribute) report(el.getAttribute('src'), kind);
      }
    } catch (e) {}
  }
  document.addEventListener('play', function (e) {
    var t = e.target;
    if (t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO')) report(t.currentSrc || t.src, t.tagName === 'AUDIO' ? 'audio' : 'video');
  }, true);
  document.addEventListener('loadedmetadata', function (e) {
    var t = e.target;
    if (t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO')) report(t.currentSrc || t.src, t.tagName === 'AUDIO' ? 'audio' : 'video');
  }, true);
  var pending = 0;
  function scheduleScan() { if (pending) return; pending = setTimeout(function () { pending = 0; scan(); }, 800); }
  document.addEventListener('DOMContentLoaded', function () {
    scan();
    try { new MutationObserver(scheduleScan).observe(document.documentElement, { childList: true, subtree: true }); } catch (e) {}
  });
  window.addEventListener('load', scan);
})();
