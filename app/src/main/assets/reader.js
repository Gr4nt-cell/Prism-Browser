(function () {
  // Lightweight readability: pick the element with the most paragraph text.
  function score(el) {
    var ps = el.querySelectorAll("p");
    var s = 0;
    for (var i = 0; i < ps.length; i++) {
      var t = ps[i].innerText || "";
      if (t.length > 40) s += t.length + (t.split(",").length * 10);
    }
    return s;
  }
  var candidates = document.querySelectorAll("article, main, [role=main], .post, .article, .entry-content, .post-content, .story, #content, .content, section, div");
  var best = null, bestScore = 0;
  for (var i = 0; i < candidates.length && i < 3000; i++) {
    var c = candidates[i];
    var sc = score(c);
    var tag = c.tagName;
    if (tag === "ARTICLE" || tag === "MAIN") sc *= 1.3;
    var cls = (c.className + " " + c.id).toLowerCase();
    if (/comment|footer|sidebar|nav|menu|related|promo|share|ad-/.test(cls)) sc *= 0.3;
    if (sc > bestScore) { bestScore = sc; best = c; }
  }
  if (!best || bestScore < 400) return JSON.stringify({ ok: false });
  var clone = best.cloneNode(true);
  var kill = clone.querySelectorAll("script,style,noscript,iframe,form,button,input,nav,aside,footer,header,svg,[class*=share],[class*=social],[class*=related],[class*=comment],[class*=newsletter],[class*=promo],[aria-hidden=true]");
  for (var k = 0; k < kill.length; k++) kill[k].remove();
  var imgs = clone.querySelectorAll("img");
  for (var j = 0; j < imgs.length; j++) {
    var im = imgs[j];
    var src = im.getAttribute("data-src") || im.getAttribute("data-lazy-src") || im.currentSrc || im.src;
    if (src) im.setAttribute("src", new URL(src, location.href).href);
    im.removeAttribute("srcset"); im.removeAttribute("width"); im.removeAttribute("height"); im.removeAttribute("style");
  }
  var links = clone.querySelectorAll("a[href]");
  for (var l = 0; l < links.length; l++) links[l].setAttribute("href", links[l].href);
  var all = clone.querySelectorAll("*");
  for (var a = 0; a < all.length; a++) { all[a].removeAttribute("style"); all[a].removeAttribute("class"); }
  var h1 = document.querySelector("h1");
  var byline = document.querySelector("[rel=author], .author, .byline, [class*=byline]");
  return JSON.stringify({
    ok: true,
    title: (h1 && h1.innerText) || document.title,
    byline: byline ? byline.innerText.trim().slice(0, 120) : "",
    site: location.hostname.replace(/^www\./, ""),
    html: clone.innerHTML
  });
})();
