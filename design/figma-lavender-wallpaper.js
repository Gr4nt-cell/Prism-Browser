// iBrowser — Lavender wallpaper update for the "iBrowser — Soft Liquid Glass" Figma file.
// Run with the free "Scripter" plugin (Plugins → Scripter), paste everything, press ▶ Run.
// Safe to run once. It:
//   1. adds wall/* colour tokens to "Glass · Light" and "Glass · Dark", makes bg/card frosted glass
//   2. creates paint styles Wallpaper/Light and Wallpaper/Dark
//   3. puts the wallpaper behind the Tab deck screens
//   4. adds Start page + Settings screens (Light & Dark) on the wallpaper

const hex = (h, a = 1) => ({ r: parseInt(h.slice(1, 3), 16) / 255, g: parseInt(h.slice(3, 5), 16) / 255, b: parseInt(h.slice(5, 7), 16) / 255, a });
const rgb = (h) => { const c = hex(h); return { r: c.r, g: c.g, b: c.b }; };
const WALL = {
  Light: { top: "#DCC4E6", mid: "#E8DEF5", bottom: "#D3DEF0", glowA: "#E9C6E2", glowB: "#CFD6F2" },
  Dark: { top: "#2B1F42", mid: "#1C1833", bottom: "#121B2E", glowA: "#4A2550", glowB: "#233160" },
};
const THEMES = ["Light", "Dark"];

// ---------------------------------------------------------------- lookups by name
const collections = await figma.variables.getLocalVariableCollectionsAsync();
const colFor = {};
for (const th of THEMES) {
  const c = collections.find(x => x.name === "Glass · " + th);
  if (!c) throw new Error("Collection 'Glass · " + th + "' not found — is this the right file?");
  colFor[th] = c;
}
const allVars = await figma.variables.getLocalVariablesAsync("COLOR");
const V = {};
for (const th of THEMES) {
  V[th] = {};
  for (const v of allVars) if (v.variableCollectionId === colFor[th].id) V[th][v.name] = v;
}

// ---------------------------------------------------------------- 1. tokens
for (const th of THEMES) {
  const col = colFor[th];
  const m = col.modes[0].modeId;
  for (const [k, h] of Object.entries(WALL[th])) {
    const name = "wall/" + k;
    let v = V[th][name];
    if (!v) { v = figma.variables.createVariable(name, col, "COLOR"); v.scopes = ["FRAME_FILL", "SHAPE_FILL"]; V[th][name] = v; }
    v.setValueForMode(m, hex(h));
  }
  V[th]["bg/card"].setValueForMode(m, th === "Light" ? { r: 1, g: 1, b: 1, a: 0.62 } : { r: 0.18, g: 0.153, b: 0.259, a: 0.36 });
  V[th]["bg/grouped"].setValueForMode(m, hex(th === "Light" ? "#E8DEF5" : "#1A1630"));
}

// ---------------------------------------------------------------- 2. wallpaper paint styles
function radial(cx, cy, rx, ry, c) {
  const sx = 0.5 / rx, sy = 0.5 / ry;
  return { type: "GRADIENT_RADIAL", gradientTransform: [[sx, 0, 0.5 - sx * cx], [0, sy, 0.5 - sy * cy]],
    gradientStops: [{ position: 0, color: c }, { position: 1, color: { r: c.r, g: c.g, b: c.b, a: 0 } }] };
}
function wallFills(th) {
  const w = WALL[th];
  return [
    { type: "GRADIENT_LINEAR", gradientTransform: [[0, 1, 0], [-1, 0, 1]],
      gradientStops: [{ position: 0, color: hex(w.top) }, { position: 0.52, color: hex(w.mid) }, { position: 1, color: hex(w.bottom) }] },
    radial(0.05, 0.28, 1.2, 0.55, hex(w.glowA, 0.85)),
    radial(1.0, 0.72, 1.1, 0.5, hex(w.glowB, 0.75)),
  ];
}
const paintStyles = await figma.getLocalPaintStylesAsync();
const WALLSTYLE = {};
for (const th of THEMES) {
  let s = paintStyles.find(x => x.name === "Wallpaper/" + th);
  if (!s) { s = figma.createPaintStyle(); s.name = "Wallpaper/" + th; }
  s.paints = wallFills(th);
  s.description = th === "Light"
    ? "App background: pink-lilac #DCC4E6 → #E8DEF5 → misty blue #D3DEF0, pink haze top-left, periwinkle haze right."
    : "App background (dark): plum #2B1F42 → #1C1833 → #121B2E with plum and indigo hazes.";
  WALLSTYLE[th] = s;
}

// ---------------------------------------------------------------- helpers
await Promise.all(["Regular", "Medium", "SemiBold", "Bold"].map(s => figma.loadFontAsync({ family: "Roboto", style: s })));
const effectStyles = await figma.getLocalEffectStylesAsync();
const EFF = {};
for (const th of THEMES) {
  EFF[th] = { soft: effectStyles.find(e => e.name === th + "/Glass Soft"), strong: effectStyles.find(e => e.name === th + "/Glass Strong") };
}
const textStyles = await figma.getLocalTextStylesAsync();
const TS = (n) => textStyles.find(t => t.name === "Type/" + n);

const bind = (v) => figma.variables.setBoundVariableForPaint({ type: "SOLID", color: { r: 0, g: 0, b: 0 } }, "color", v);
const col = (th, n) => bind(V[th][n]);
const sheen = (a) => ({ type: "GRADIENT_LINEAR", gradientTransform: [[0, 1, 0], [-1, 0, 1]], gradientStops: [{ position: 0, color: { r: 1, g: 1, b: 1, a } }, { position: 0.55, color: { r: 1, g: 1, b: 1, a: 0 } }] });
const rim = (a) => ({ type: "GRADIENT_LINEAR", gradientTransform: [[0, 1, 0], [-1, 0, 1]], gradientStops: [{ position: 0.7, color: { r: 1, g: 1, b: 1, a: 0 } }, { position: 1, color: { r: 1, g: 1, b: 1, a } }] });

async function glass(n, th, strong) {
  n.fills = [col(th, strong ? "glass/fill-strong" : "glass/fill"), sheen(th === "Light" ? 0.38 : 0.10), rim(th === "Light" ? 0.22 : 0.08)];
  n.strokes = [col(th, "glass/edge")]; n.strokeWeight = 1; n.strokeAlign = "INSIDE";
  const e = strong ? EFF[th].strong : EFF[th].soft;
  if (e) await n.setEffectStyleIdAsync(e.id);
}
function glassCard(n, th) {
  n.fills = [col(th, "bg/card"), sheen(th === "Light" ? 0.35 : 0.05)];
  n.strokes = [{ type: "SOLID", color: { r: 1, g: 1, b: 1 }, opacity: th === "Light" ? 0.9 : 0.2 }];
  n.strokeWeight = 0.8; n.strokeAlign = "INSIDE";
  n.effects = [{ type: "DROP_SHADOW", color: { r: 0, g: 0, b: 0, a: th === "Light" ? 0.06 : 0.3 }, offset: { x: 0, y: 4 }, radius: 14, spread: 0, visible: true, blendMode: "NORMAL" }];
}
function frame(name, w, h, parent, x = 0, y = 0) {
  const f = figma.createFrame(); f.name = name; f.resize(w, h); f.fills = []; parent.appendChild(f); f.x = x; f.y = y; return f;
}
function auto(name, dir, parent, gap = 0) {
  const f = figma.createFrame(); f.name = name; f.layoutMode = dir; f.primaryAxisSizingMode = "AUTO"; f.counterAxisSizingMode = "AUTO";
  f.itemSpacing = gap; f.fills = []; if (parent) parent.appendChild(f); return f;
}
async function text(parent, s, style, th, color = "label/primary", opts = {}) {
  const t = figma.createText();
  const st = TS(style);
  if (st) await t.setTextStyleIdAsync(st.id); else t.fontName = { family: "Roboto", style: "Regular" };
  if (opts.weight) t.fontName = { family: "Roboto", style: opts.weight };
  if (opts.size) t.fontSize = opts.size;
  t.characters = s;
  t.fills = [col(th, color)];
  parent.appendChild(t);
  if (opts.width) { t.textAutoResize = "HEIGHT"; t.resize(opts.width, t.height); }
  if (opts.x !== undefined) { t.x = opts.x; t.y = opts.y; }
  return t;
}
const ICON_IDS = { star: "3:79", book: "3:76", reader: "3:84", clock: "3:88", shield: "3:126" };
const icons = {};
for (const [k, id] of Object.entries(ICON_IDS)) icons[k] = await figma.getNodeByIdAsync(id);
function icon(name, th, size, color = "label/primary") {
  const i = icons[name].createInstance(); i.resize(size, size);
  for (const n of i.findAll(x => "strokes" in x)) {
    if (n.strokes.length) n.strokes = [col(th, color)];
    if ("fills" in n && Array.isArray(n.fills) && n.fills.length) n.fills = [col(th, color)];
  }
  return i;
}
function statusBar(parent, th) {
  const sb = frame("Status bar", 412, 36, parent);
  const t = figma.createText(); t.fontName = { family: "Roboto", style: "SemiBold" }; t.fontSize = 15; t.characters = "9:41"; t.fills = [col(th, "label/primary")]; sb.appendChild(t); t.x = 28; t.y = 10;
  for (const [x, w] of [[326, 18], [348, 16], [368, 24]]) { const r = figma.createRectangle(); r.resize(w, 11); r.cornerRadius = 3; r.fills = [col(th, "label/primary")]; sb.appendChild(r); r.x = x; r.y = 13; }
}
function gestureBar(parent, th) { const h = figma.createRectangle(); h.name = "Gesture bar"; h.resize(108, 4); h.cornerRadius = 2; h.fills = [col(th, "label/primary")]; h.opacity = 0.55; parent.appendChild(h); h.x = 152; h.y = 900; }

// ---------------------------------------------------------------- 3. tab deck screens on the wallpaper
const sec = figma.currentPage.findOne(n => n.type === "SECTION" && n.name === "Screens");
if (!sec) throw new Error("Section 'Screens' not found");
for (const f of sec.findAll(n => n.type === "FRAME" && n.name.indexOf("Tab deck") === 0)) {
  await f.setFillStyleIdAsync(WALLSTYLE[f.name.indexOf("Dark") >= 0 ? "Dark" : "Light"].id);
}

// ---------------------------------------------------------------- 4. new screens
let maxY = 0;
for (const c of sec.children) maxY = Math.max(maxY, c.y + c.height);
const rowY = maxY + 120;
const heading = figma.createText(); heading.fontName = { family: "Roboto", style: "Bold" }; heading.fontSize = 28;
heading.characters = "7 · App screens on the lavender wallpaper"; heading.fills = [{ type: "SOLID", color: { r: 0.1, g: 0.1, b: 0.12 } }];
sec.appendChild(heading); heading.x = 0; heading.y = rowY;

async function phone(name, th, x) {
  const p = figma.createFrame(); p.name = name; p.resize(412, 915); p.cornerRadius = 44; p.clipsContent = true;
  sec.appendChild(p); p.x = x; p.y = rowY + 80;
  await p.setFillStyleIdAsync(WALLSTYLE[th].id);
  p.effects = [{ type: "DROP_SHADOW", color: { r: 0, g: 0, b: 0, a: 0.18 }, offset: { x: 0, y: 30 }, radius: 60, spread: 0, visible: true, blendMode: "NORMAL" }];
  return p;
}

// ---- Start page
const FAVS = [["Google", "#4285F4"], ["YouTube", "#FF3B30"], ["Wikipedia", "#8E8E93"], ["GitHub", "#5E5CE6"], ["Reddit", "#FF6B2C"], ["BBC", "#BF5AF2"], ["X", "#30B0C7"], ["Hacker News", "#FF9F0A"]];
const TOOLBAR = { Light: await figma.getNodeByIdAsync("4:98"), Dark: await figma.getNodeByIdAsync("4:122") };

for (const [i, th] of THEMES.entries()) {
  const p = await phone("Start page — " + th, th, i * 480);
  statusBar(p, th);
  // segmented picker
  const seg = frame("Start · Bookmarks · Reading List · History", 380, 40, p, 16, 48); seg.cornerRadius = 20; await glass(seg, th, false);
  const sel = frame("Selected", 110, 34, seg, 3, 3); sel.cornerRadius = 17;
  sel.fills = [{ type: "SOLID", color: th === "Light" ? { r: 1, g: 1, b: 1 } : rgb("#3A3A3C"), opacity: 0.92 }];
  const sIc = icon("star", th, 17, "accent/tint"); sel.appendChild(sIc); sIc.x = 22; sIc.y = 8.5;
  await text(sel, "Start", "Footnote", th, "label/primary", { weight: "SemiBold", x: 44, y: 9 });
  [["book", 166], ["reader", 252], ["clock", 338]].forEach(([n, x]) => { const ic = icon(n, th, 18, "label/secondary"); seg.appendChild(ic); ic.x = x; ic.y = 11; });
  // favorites
  await text(p, "Favorites", "Title", th, "label/primary", { x: 20, y: 106 });
  for (let k = 0; k < FAVS.length; k++) {
    const cx = 14 + (k % 4) * 96 + 48, ty = 146 + Math.floor(k / 4) * 100;
    const tile = frame("Tile — " + FAVS[k][0], 60, 60, p, cx - 30, ty); tile.cornerRadius = 15; glassCard(tile, th);
    const tint = figma.createRectangle(); tint.resize(60, 60); tint.fills = [{ type: "SOLID", color: rgb(FAVS[k][1]), opacity: th === "Light" ? 0.14 : 0.3 }]; tile.appendChild(tint);
    tile.clipsContent = true;
    const L = figma.createText(); L.fontName = { family: "Roboto", style: "SemiBold" }; L.fontSize = 27; L.characters = FAVS[k][0][0]; L.fills = [{ type: "SOLID", color: rgb(FAVS[k][1]) }];
    tile.appendChild(L); L.x = 30 - L.width / 2; L.y = 30 - L.height / 2;
    const lab = await text(p, FAVS[k][0], "Caption", th); lab.textAlignHorizontal = "CENTER"; lab.textAutoResize = "HEIGHT"; lab.resize(88, lab.height); lab.x = cx - 44; lab.y = ty + 66;
  }
  // privacy report
  await text(p, "Privacy Report", "Title", th, "label/primary", { x: 20, y: 352 });
  const pr = frame("Privacy Report card", 372, 76, p, 20, 390); pr.cornerRadius = 22; glassCard(pr, th);
  const sh = icon("shield", th, 28); pr.appendChild(sh); sh.x = 16; sh.y = 24;
  await text(pr, "1,284", "Title", th, "label/primary", { size: 26, x: 56, y: 21 });
  await text(pr, "ads and trackers have been blocked from loading and profiling you.", "Footnote", th, "label/secondary", { width: 200, x: 150, y: 20 });
  // reading list
  await text(p, "Reading List", "Title", th, "label/primary", { x: 20, y: 492 });
  await text(p, "Show All", "Subhead", th, "accent/tint", { x: 330, y: 497 });
  const READING = [["Chasing the last light over the Volta Lake", "travel.example.com", "#FF8A65"], ["Jollof: the definitive guide", "food.example.com", "#FFB300"]];
  for (let k = 0; k < READING.length; k++) {
    const [t1, t2, c] = READING[k];
    const card = frame("Reading item", 270, 72, p, 20 + k * 282, 530); card.cornerRadius = 18; glassCard(card, th);
    const sw = figma.createRectangle(); sw.resize(44, 44); sw.cornerRadius = 11; sw.fills = [{ type: "SOLID", color: rgb(c) }]; card.appendChild(sw); sw.x = 14; sw.y = 14;
    const tt = await text(card, t1, "Subhead", th, "label/primary", { weight: "SemiBold", x: 68, y: 13 });
    tt.textAutoResize = "TRUNCATE"; tt.resize(190, 20);
    await text(card, t2, "Footnote", th, "label/secondary", { x: 68, y: 38 });
  }
  // edit pill
  const edit = frame("Edit", 80, 36, p, 166, 636); edit.cornerRadius = 18; edit.fills = [col(th, "fill/control")];
  const et = await text(edit, "Edit", "Subhead", th, "label/primary", { weight: "SemiBold" }); et.x = 40 - et.width / 2; et.y = 18 - et.height / 2;
  // toolbar showing the empty address bar
  const tb = TOOLBAR[th].createInstance(); p.appendChild(tb); tb.x = 0; tb.y = 828;
  for (const t of tb.findAll(n => n.type === "TEXT")) {
    if (t.characters === "wikipedia.org") { t.characters = "Search or enter website name"; t.fills = [col(th, "label/secondary")]; t.fontName = { family: "Roboto", style: "Regular" }; }
  }
  for (const n of tb.findAll(n => n.name === "Progress")) n.visible = false;
  for (const n of tb.findAll(n => n.name === "Reload")) n.opacity = 0;
  const lock = tb.findOne(n => n.type === "INSTANCE" && n.name === "icon/lock");
  if (lock) lock.visible = false;
  gestureBar(p, th);
}

// ---- Settings sheet
async function group(sheet, th, y, header, rows) {
  await text(sheet, header.toUpperCase(), "Footnote", th, "label/secondary", { x: 36, y });
  const card = frame(header, 380, 10, sheet, 16, y + 24); card.cornerRadius = 26; glassCard(card, th);
  let cy = 0;
  for (let r = 0; r < rows.length; r++) {
    const row = rows[r];
    const h = row.sub ? 62 : row.slider ? 78 : 50;
    const rf = frame(row.title, 380, h, card, 0, cy);
    await text(rf, row.title, "Body", th, row.titleColor || "label/primary", { x: 16, y: row.sub ? 10 : 14 });
    if (row.sub) await text(rf, row.sub, "Footnote", th, "label/secondary", { x: 16, y: 34 });
    if (row.value !== undefined) {
      const vt = await text(rf, row.value, "Body", th, "label/secondary"); vt.x = 380 - 36 - vt.width; vt.y = 14;
    }
    if (row.chev) { const ch = await text(rf, "›", "Body", th, "label/secondary", { size: 24 }); ch.opacity = 0.6; ch.x = 380 - 26; ch.y = 9; }
    if (row.trail) { const tt = await text(rf, row.trail, "Subhead", th, "accent/tint"); tt.x = 380 - 16 - tt.width; tt.y = row.sub ? 21 : 15; }
    if (row.toggle) {
      const tr = figma.createRectangle(); tr.resize(51, 31); tr.cornerRadius = 15.5; tr.fills = [col(th, "accent/green")]; rf.appendChild(tr); tr.x = 380 - 16 - 51; tr.y = 9.5;
      const th0 = figma.createEllipse(); th0.resize(27, 27); th0.fills = [{ type: "SOLID", color: { r: 1, g: 1, b: 1 } }];
      th0.effects = [{ type: "DROP_SHADOW", color: { r: 0, g: 0, b: 0, a: 0.15 }, offset: { x: 0, y: 2 }, radius: 6, spread: 0, visible: true, blendMode: "NORMAL" }];
      rf.appendChild(th0); th0.x = 380 - 16 - 29; th0.y = 11.5;
    }
    if (row.slider) {
      const track = figma.createRectangle(); track.resize(348, 4); track.cornerRadius = 2; track.fills = [col(th, "fill/control")]; rf.appendChild(track); track.x = 16; track.y = 52;
      const fill = figma.createRectangle(); fill.resize(348 * row.slider, 4); fill.cornerRadius = 2; fill.fills = [col(th, "accent/tint")]; rf.appendChild(fill); fill.x = 16; fill.y = 52;
      const knob = figma.createEllipse(); knob.resize(24, 24); knob.fills = [{ type: "SOLID", color: { r: 1, g: 1, b: 1 } }];
      knob.effects = [{ type: "DROP_SHADOW", color: { r: 0, g: 0, b: 0, a: 0.18 }, offset: { x: 0, y: 2 }, radius: 6, spread: 0, visible: true, blendMode: "NORMAL" }];
      rf.appendChild(knob); knob.x = 16 + 348 * row.slider - 12; knob.y = 42;
    }
    if (r < rows.length - 1) { const sep = figma.createRectangle(); sep.resize(364, 0.5); sep.fills = [col(th, "separator")]; rf.appendChild(sep); sep.x = 16; sep.y = h - 0.5; }
    cy += h;
  }
  card.resize(380, cy);
  return y + 24 + cy;
}
for (const [i, th] of THEMES.entries()) {
  const p = await phone("Settings — " + th, th, 960 + i * 480);
  statusBar(p, th);
  const dim = figma.createRectangle(); dim.name = "Dim"; dim.resize(412, 915); dim.fills = [{ type: "SOLID", color: { r: 0, g: 0, b: 0 }, opacity: th === "Light" ? 0.12 : 0.35 }]; p.appendChild(dim);
  const sheet = frame("Settings sheet", 412, 875, p, 0, 46); sheet.topLeftRadius = 30; sheet.topRightRadius = 30; sheet.clipsContent = true;
  await sheet.setFillStyleIdAsync(WALLSTYLE[th].id);
  const grab = figma.createRectangle(); grab.resize(36, 5); grab.cornerRadius = 2.5; grab.fills = [col(th, "label/secondary")]; grab.opacity = 0.5; sheet.appendChild(grab); grab.x = 188; grab.y = 6;
  const title = await text(sheet, "Settings", "Headline", th); title.x = 206 - title.width / 2; title.y = 26;
  const done = frame("Done", 70, 34, sheet, 326, 20); done.cornerRadius = 17; await glass(done, th, false);
  const dt = await text(done, "Done", "Headline", th, "label/primary", { size: 16 }); dt.x = 35 - dt.width / 2; dt.y = 17 - dt.height / 2;
  let y = 76;
  y = await group(sheet, th, y, "Device Access", [
    { title: "Files & Storage", value: "All Files", chev: true },
    { title: "Camera", value: "Allowed", chev: true },
    { title: "Microphone", value: "Allowed", chev: true },
    { title: "Open File…", chev: true },
  ]);
  y = await group(sheet, th, y + 22, "Downloads", [
    { title: "Save Downloads To", value: "Download/iBrowser", chev: true },
    { title: "Connections per download", value: "16", slider: 15 / 31 },
    { title: "Ask Before Downloading", toggle: true },
  ]);
  y = await group(sheet, th, y + 22, "Websites", [
    { title: "meet.google.com", sub: "Camera: Allow · Microphone: Allow", trail: "Reset" },
    { title: "maps.google.com", sub: "Location: Allow", trail: "Reset" },
  ]);
  await text(sheet, "Websites always ask before using your camera, microphone or location.", "Footnote", th, "label/secondary", { width: 348, x: 32, y: y + 8 });
  gestureBar(p, th);
}

sec.resizeWithoutConstraints(Math.max(sec.width, 1960), rowY + 80 + 915 + 120);
figma.viewport.scrollAndZoomIntoView(sec.children.filter(n => n.y >= rowY));
print("Done: wallpaper tokens + styles added, tab deck updated, 4 new screens created.");
