// iBrowser / Safari 27 redesign. Run in the Figma Scripter plugin or the Plugin API.
// Creates editable components, text, vectors, themed variables and four reference screens.
// It does not import screenshots, read browsing data, or modify existing pages.
async function buildSafariRedesign() {
  const pageName = 'iBrowser / Safari 27';
  if (figma.root.children.some(p => p.name === pageName)) throw new Error('This design page already exists. Keep it or rename it before running again.');
  const fonts = await figma.listAvailableFontsAsync();
  for (const style of ['Regular', 'Medium', 'Bold']) {
    if (!fonts.some(f => f.fontName.family === 'Roboto' && f.fontName.style === style)) throw new Error('Roboto ' + style + ' is required to match the Android system font.');
    await figma.loadFontAsync({ family: 'Roboto', style });
  }
  const page = figma.createPage(); page.name = pageName; await figma.setCurrentPageAsync(page);
  const ids = [page.id];
  const color = (h, a = 1) => ({ r: parseInt(h.slice(1, 3), 16) / 255, g: parseInt(h.slice(3, 5), 16) / 255, b: parseInt(h.slice(5, 7), 16) / 255, a });
  const paint = (h, a = 1) => { const {r, g, b} = color(h); return {type: 'SOLID', color: {r, g, b}, opacity: a}; };
  const collection = figma.variables.createVariableCollection('iBrowser / Safari theme');
  collection.renameMode(collection.defaultModeId, 'Light'); const light = collection.defaultModeId, dark = collection.addMode('Dark');
  const tokens = {};
  for (const [name, l, d] of [
    ['label', color('#000000'), color('#FFFFFF')], ['secondary', color('#3C3C43', .6), color('#EBEBF5', .6)],
    ['tint', color('#007AFF'), color('#0A84FF')], ['surface', color('#FFFFFF', .72), color('#2C2C2E', .75)],
    ['rim', color('#FFFFFF', .72), color('#FFFFFF', .24)], ['wall', color('#F1F2F8'), color('#14151D')]
  ]) {
    const v = figma.variables.createVariable(name, collection, 'COLOR'); v.scopes = name === 'rim' ? ['STROKE_COLOR'] : ['FRAME_FILL', 'SHAPE_FILL', 'TEXT_FILL'];
    v.setValueForMode(light, l); v.setValueForMode(dark, d); v.setVariableCodeSyntax('ANDROID', 'Ios.c.' + name); tokens[name] = v; ids.push(v.id);
  }
  ids.push(collection.id);
  const bound = name => figma.variables.setBoundVariableForPaint(paint('#FFFFFF'), 'color', tokens[name]);
  const effect = figma.createEffectStyle(); effect.name = 'iBrowser / Glass depth';
  effect.effects = [{type: 'DROP_SHADOW', color: color('#263554', .12), offset: {x: 0, y: 5}, radius: 18, visible: true, blendMode: 'NORMAL'}]; ids.push(effect.id);
  const textStyles = {};
  for (const [name, size, style] of [['Title', 22, 'Bold'], ['Body', 15, 'Regular'], ['Label', 13, 'Medium'], ['Caption', 12, 'Regular']]) {
    const s = figma.createTextStyle(); s.name = 'iBrowser / ' + name; s.fontName = {family:'Roboto', style}; s.fontSize = size; textStyles[name] = s; ids.push(s.id);
  }
  const add = (p, n) => { p.appendChild(n); ids.push(n.id); return n; };
  const at = (n, x, y) => {n.x = x; n.y = y; return n;};
  function frame(p, name, w, h, layout) {
    const n = layout ? figma.createAutoLayout(layout) : figma.createFrame(); n.name = name; n.fills = []; n.resize(w, h);
    if (layout) { n.primaryAxisSizingMode = 'FIXED'; n.counterAxisSizingMode = 'FIXED'; } return add(p, n);
  }
  function text(p, content, role = 'Body', fill = 'label') {
    const n = figma.createText(); n.fontName = textStyles[role].fontName; n.textStyleId = textStyles[role].id; n.characters = content;
    n.fills = [tokens[fill] ? bound(fill) : paint(fill)]; return add(p, n);
  }
  const vectors = {
    back: '<path d="m14.5 4.5-7.5 7.5 7.5 7.5"/>', forward: '<path d="m9.5 4.5 7.5 7.5-7.5 7.5"/>',
    plus: '<path d="M12 4v16M4 12h16"/>', tabs: '<path d="M8 3h10q2 0 2 2v10M5 7h10q2 0 2 2v10q0 2-2 2H5q-2 0-2-2V9q0-2 2-2Z"/>',
    share: '<path d="M8 10H5v11h14V10h-3M12 15V2M7.5 6.5 12 2l4.5 4.5"/>',
    sidebar: '<rect x="3" y="4.5" width="18" height="15" rx="2"/><path d="M9 4.5v15"/>',
    page: '<path d="M4 6h16M4 10.5h16M4 15h8M4 19.5h8M16 20l2.5-6 2.5 6M17 18h3"/>',
    reload: '<path d="M20 8a8 8 0 1 0 0 8M20 3v5h-5"/>', search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
    download: '<circle cx="12" cy="12" r="9"/><path d="M12 6.5v10m-4-4 4 4 4-4"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>', check: '<path d="m5 12 4 4L19 6"/>',
    more: '<circle cx="5" cy="12" r=".8"/><circle cx="12" cy="12" r=".8"/><circle cx="19" cy="12" r=".8"/>',
    shield: '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/>',
    book: '<path d="M12 5c-3-2-6-2-9-1v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-3-1-6-1-9 1Zm0 0v15"/>'
  };
  function icon(p, name, ink = '#202124', size = 22) {
    const n = figma.createNodeFromSvg(`<svg width="${size}" height="${size}" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" fill="none" stroke="${ink}" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round">${vectors[name]}</svg>`);
    n.name = 'Symbol / ' + name; add(p, n); ids.push(...n.findAll(() => true).map(c => c.id)); return n;
  }
  // Build once, instance throughout; a content wrapper keeps instances editable without detaching.
  const material = figma.createComponent(); material.name = 'Glass / Surface'; material.resize(180, 48); material.cornerRadius = 24;
  material.fills = [bound('surface')]; material.strokes = [bound('rim')]; material.strokeWeight = 1; material.effectStyleId = effect.id;
  material.description = 'Shared translucent material from Components.kt / AdaptiveGlass.kt. Readable frost, fine rim, diffuse depth.';
  add(page, material); at(material, 1600, 100);
  function glass(p, name, w, h, r = h / 2, layout = 'HORIZONTAL') {
    const wrapper = frame(p, name, w, h, layout); wrapper.cornerRadius = r; wrapper.primaryAxisAlignItems = 'CENTER'; wrapper.counterAxisAlignItems = 'CENTER';
    const n = material.createInstance(); add(wrapper, n); n.resize(w, h); n.cornerRadius = r; n.layoutPositioning = 'ABSOLUTE'; n.x = 0; n.y = 0; return wrapper;
  }
  const board = frame(page, 'Safari 27 / Phone and Tablet', 1430, 1870); board.fills = [paint('#E5E8EF')]; at(board, 80, 80);
  at(text(board, 'iBrowser', 'Title'), 40, 30); at(text(board, 'Safari compact on phone. Safari desktop on tablet.', 'Body', 'secondary'), 40, 65);
  function wallpaper(p, w, h, night = false) {
    const bg = figma.createRectangle(); bg.name = 'Wallpaper'; bg.resize(w, h); bg.fills = [bound('wall')]; add(p, bg);
    for (const [name, hex, x, y] of [['Blue', night ? '#343552' : '#D6DFF3', -.25*w, -.35*h], ['Rose', night ? '#442A4E' : '#EAD7EB', .35*w, .1*h], ['Mint', night ? '#1C3942' : '#D5EAE9', -.2*w, .55*h]]) {
      const orb = figma.createEllipse(); orb.name = 'Glow / ' + name; orb.resize(Math.max(w,h)*1.1, Math.max(w,h)*1.1);
      orb.fills = [{type:'GRADIENT_RADIAL', gradientTransform:[[1,0,0],[0,1,0]], gradientStops:[{position:0,color:color(hex,.85)},{position:1,color:color(hex,0)}]}]; add(p,orb); at(orb,x,y);
    }
  }
  function phone(name, x, night = false) {
    const p = frame(board,name,390,844); p.cornerRadius = 30; p.clipsContent = true; p.setExplicitVariableModeForCollection(collection,night?dark:light); at(p,x,115); wallpaper(p,390,844,night);
    at(text(p,'9:41','Label'),24,17); const home=figma.createRectangle(); home.name='System gesture area'; home.resize(134,5); home.cornerRadius=3; home.fills=[bound('label')]; add(p,home); at(home,128,829); return p;
  }
  function address(p, name, w, h = 48, night = false) {
    const n = glass(p,'Address',w,h); n.paddingLeft=10; n.paddingRight=10; n.primaryAxisAlignItems='SPACE_BETWEEN';
    icon(n,'page',night?'#FFFFFF':'#202124',20); const title=text(n,name.startsWith('Search')&&w<300?'Search or Enter Website…':name,'Label',name.startsWith('Search')?'secondary':'label'); title.textAutoResize='HEIGHT'; title.resize(Math.max(40,w-82),32); title.textAlignHorizontal='CENTER'; title.textAlignVertical='CENTER';
    icon(n,'reload',night?'#FFFFFF':'#202124',18); return n;
  }
  function toolbar(p, domain, night = false) {
    const row=frame(p,'Compact controls',366,48,'HORIZONTAL'); at(row,12,762); row.itemSpacing=8;
    const back=glass(row,'Back',48,48); icon(back,'back',night?'#FFFFFF':'#202124'); address(row,domain,254,48,night);
    const tabs=glass(row,'Tab Overview',48,48); icon(tabs,'tabs',night?'#FFFFFF':'#202124');
  }
  function favorites(p, x, y, width, columns=4) {
    at(text(p,'Favorites','Title'),x,y);
    const names=['Google','Wikipedia','YouTube','GitHub','Reddit','DuckDuckGo','BBC','Bing'];
    const inks=['#4285F4','#242424','#E62117','#24292F','#FF4500','#DE5833','#222222','#008E92'];
    const list=frame(p,'Favorites grid',width,columns===8?100:200,'VERTICAL');at(list,x,y+38);list.itemSpacing=16;
    for(let r=0;r<names.length/columns;r++){const row=frame(list,'Favorite row',width,88,'HORIZONTAL');row.itemSpacing=0;for(let c=0;c<columns;c++){
      const i=r*columns+c, item=frame(row,names[i],width/columns,88,'VERTICAL');item.counterAxisAlignItems='CENTER';item.itemSpacing=6;
      const tile=glass(item,'Website tile',58,58,14); const letter=text(tile,names[i][0],'Title',inks[i]);letter.fontSize=26;
      const label=text(item,names[i],'Caption');label.textAlignHorizontal='CENTER';
    }}
  }
  const start=phone('Phone / Start Page',40); const pick=glass(start,'Start sections',358,40);at(pick,16,59);pick.itemSpacing=18;
  for(const s of ['Start','Bookmarks','Reading List','History'])text(pick,s,'Caption',s==='Start'?'tint':'secondary');
  favorites(start,20,132,350);
  at(text(start,'Privacy Report','Title'),20,400);const privacy=glass(start,'Privacy report',350,104,22);at(privacy,20,438);privacy.itemSpacing=16;icon(privacy,'shield');const details=frame(privacy,'Privacy detail',260,70,'VERTICAL');details.itemSpacing=8;text(details,'128 trackers prevented','Label');text(details,'Your browsing stays your business.','Caption','secondary');
  const edit=glass(start,'Edit start page',80,36);at(edit,155,585);text(edit,'Edit','Label');toolbar(start,'Search or Enter Website Name');
  const web=phone('Phone / Browsing — Dark',520,true);const sheet=frame(web,'Example webpage',390,690);sheet.fills=[paint('#191A1D')];at(sheet,0,47);at(text(sheet,'WIKIPEDIA','Title','#FFFFFF'),25,30);at(text(sheet,'The free encyclopedia','Caption','#AAAAAE'),25,64);at(text(sheet,'Ghana','Title','#FFFFFF'),25,130);const body=text(sheet,'Ghana is a country in West Africa.\n\nExplore its history, culture,\nlandscapes and communities.','Body','#D3D3D6');at(body,25,175);body.lineHeight={unit:'PIXELS',value:26};toolbar(web,'en.wikipedia.org',true);
  const overview=phone('Phone / Tab Overview',1000);at(text(overview,'4 Tabs','Title'),157,65);at(text(overview,'Archive','Caption','tint'),20,72);at(text(overview,'Settings','Caption','tint'),321,72);
  const search=glass(overview,'Search Tabs',350,44);at(search,20,107);search.itemSpacing=10;icon(search,'search','#73767C',18);text(search,'Search Tabs','Body','secondary');
  for(let i=0;i<4;i++){const card=glass(overview,'Tab / '+i,169,238,20,'VERTICAL');at(card,20+(i%2)*181,172+Math.floor(i/2)*252);card.paddingLeft=8;card.paddingRight=8;card.paddingTop=8;card.paddingBottom=8;card.itemSpacing=8;
    const head=frame(card,'Tab title',153,28,'HORIZONTAL');head.primaryAxisAlignItems='SPACE_BETWEEN';text(head,['Wikipedia','GitHub','BBC','Start Page'][i],'Label');icon(head,'close','#73767C',16);
    const preview=frame(card,'Editable page preview',153,184,'VERTICAL');preview.cornerRadius=12;preview.fills=[paint('#FFFFFF',.85)];preview.paddingTop=20;preview.paddingLeft=12;preview.itemSpacing=10;text(preview,['Ghana','Open source','World news','Favorites'][i],'Label');text(preview,'Page preview','Caption','secondary');
  }
  const tabBar=frame(overview,'Tab controls',350,48,'HORIZONTAL');at(tabBar,20,762);tabBar.primaryAxisAlignItems='SPACE_BETWEEN';const plus=glass(tabBar,'New Tab',48,48);icon(plus,'plus');const modes=glass(tabBar,'Normal and private',190,46);modes.itemSpacing=24;text(modes,'Private','Label','secondary');text(modes,'4 Tabs','Label','tint');const done=glass(tabBar,'Done',48,48);icon(done,'check','#007AFF');
  const tablet=frame(board,'Tablet / Desktop Safari',1180,780);at(tablet,125,1040);tablet.cornerRadius=24;tablet.clipsContent=true;tablet.setExplicitVariableModeForCollection(collection,light);wallpaper(tablet,1180,780);
  const chrome=glass(tablet,'Desktop toolbar',1180,58,0);chrome.paddingLeft=16;chrome.paddingRight=16;chrome.itemSpacing=12;icon(chrome,'sidebar');icon(chrome,'back');icon(chrome,'forward');const gap=frame(chrome,'Flexible space',70,20);icon(chrome,'shield');address(chrome,'Search or Enter Website Name',520,38);frame(chrome,'Flexible space',70,20);icon(chrome,'download');icon(chrome,'share');icon(chrome,'tabs');icon(chrome,'more');
  const strip=glass(tablet,'Horizontal tab strip',1180,42,0);at(strip,0,58);strip.paddingLeft=8;strip.itemSpacing=5;strip.primaryAxisAlignItems='MIN';for(const label of ['Start Page','Wikipedia','GitHub']){const tab=glass(strip,label,190,33,9);tab.itemSpacing=35;text(tab,label,'Label');icon(tab,'close','#73767C',14);}icon(strip,'plus');
  favorites(tablet,160,170,860,8);at(text(tablet,'Privacy Report','Title'),160,340);const report=glass(tablet,'Privacy Report',860,92,22);at(report,160,385);report.itemSpacing=18;icon(report,'shield');text(report,'128 trackers prevented from profiling you','Body');
  const customize=glass(tablet,'Customize Start Page',84,36);at(customize,548,532);text(customize,'Edit','Label');
  at(text(board,'Phone / Start, browsing and tab overview','Label','secondary'),40,985);
  at(text(board,'Tablet / horizontal tabs, centered address, optional library sidebar','Label','secondary'),125,1835);
  const nodes=board.findAll(()=>true), counts={}; for(const n of nodes)counts[n.type]=(counts[n.type]||0)+1;
  const result={pageId:page.id,boardId:board.id,createdNodeIds:ids,counts,images:nodes.filter(n=>'fills' in n&&Array.isArray(n.fills)&&n.fills.some(p=>p.type==='IMAGE')).length};
  figma.viewport.scrollAndZoomIntoView([board]);return result;
}
console.log(await buildSafariRedesign());
