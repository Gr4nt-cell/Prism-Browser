module.exports=({themes,icons,scene})=>`// Run in Figma Scripter. All UI is editable text, vector icons, components and frames.
async function buildPrismBrowser(){
 const existing=figma.root.children.find(p=>p.name==='iBrowser / Prism');
 if(existing)throw new Error('This design page already exists. Rename it to retain an older version before importing again.');
 const available=await figma.listAvailableFontsAsync();
 const fonts={};for(const style of ['Regular','Medium','Bold']){const f=available.find(f=>f.fontName.family==='Roboto'&&f.fontName.style===style);if(!f)throw new Error('Roboto '+style+' is required to match Android typography.');fonts[style]=f.fontName;}
 await Promise.all(Object.values(fonts).map(f=>figma.loadFontAsync(f)));
 const page=figma.createPage();page.name='iBrowser / Prism';await figma.setCurrentPageAsync(page);
 const ids=[page.id],collection=figma.variables.createVariableCollection('iBrowser / Prism');const light=collection.defaultModeId;collection.renameMode(light,'Light');const dark=collection.addMode('Dark');
 const rgb=h=>({r:parseInt(h.slice(1,3),16)/255,g:parseInt(h.slice(3,5),16)/255,b:parseInt(h.slice(5,7),16)/255});
 const vars={};for(const name of Object.keys(themes.light)){const v=figma.variables.createVariable(name,collection,'COLOR');v.scopes=['FRAME_FILL','SHAPE_FILL','TEXT_FILL','STROKE_COLOR'];v.setValueForMode(light,{...rgb(themes.light[name]),a:1});v.setValueForMode(dark,{...rgb(themes.dark[name]),a:1});vars[name]=v;}
 const space=figma.variables.createVariable('spacing/base',collection,'FLOAT');space.scopes=['GAP'];space.setValueForMode(light,8);space.setValueForMode(dark,8);
 const paint=(key,opacity=1)=>{const p={type:'SOLID',color:rgb(themes.light[key]||key),opacity};return vars[key]?figma.variables.setBoundVariableForPaint(p,'color',vars[key]):p;};
 const board=figma.createFrame();board.name='iBrowser / Prism / Design references';board.resize(scene.w,scene.h);board.fills=[paint('#0D1919')];board.x=Math.max(0,...figma.currentPage.children.map(n=>n.x+n.width))+160;board.y=100;page.appendChild(board);ids.push(board.id);
 const library=figma.createFrame();library.name='iBrowser / Shared components';library.resize(1280,100);library.fills=[];library.clipsContent=false;library.x=board.x+scene.w+160;library.y=100;page.appendChild(library);ids.push(library.id);
 const components={};let libY=20,libW=1280;
 function style(n,s){if(s.kind==='text'){n.fontName=fonts[s.weight||'Regular'];n.characters=s.value;n.fontSize=s.size;n.textAutoResize='HEIGHT';n.resize(s.w,s.h);n.lineHeight={unit:'PERCENT',value:130};n.textAlignHorizontal=s.align||'LEFT';n.fills=[paint(s.color)];}
 else if(s.kind!=='icon'){n.fills=s.gradient?[{type:'GRADIENT_LINEAR',gradientTransform:[[1,0,0],[0,1,0]],gradientStops:s.gradient.map((v,i)=>({position:i/(s.gradient.length-1),color:{...rgb(themes[s.theme||'light'][v]||v),a:1}}))}]:s.fill?[paint(s.fill,s.alpha??1)]:[];n.cornerRadius=s.r||0;if(s.glass){n.strokes=[paint('rim',.45)];n.strokeWeight=1;n.effects=[{type:'BACKGROUND_BLUR',radius:18,visible:true}];}}}
 function override(n,s){n.resize(s.w,s.h);n.x=s.x||0;n.y=s.y||0;if(s.kind!=='icon')style(n,s);if(s.kind!=='icon')s.children?.forEach((child,i)=>{if(n.children[i])override(n.children[i],child);});}
 function create(parent,s,main=false){let n;
 if(s.component&&!main){let c=components[s.component];if(!c){c=create(library,{...s,x:20,y:libY},true);libY+=s.h+36;libW=Math.max(libW,s.w+40);c.description='Native Compose iBrowser control: '+s.component+'. Theme colors inherit from the reference screen.';components[s.component]=c;}n=c.createInstance();parent.appendChild(n);ids.push(n.id,...n.findAll(()=>true).map(x=>x.id));override(n,s);}
 else if(s.kind==='text'){n=figma.createText();parent.appendChild(n);ids.push(n.id);style(n,s);}
 else if(s.kind==='icon'){const svg=icons[s.key].replace(/fill="(?!none)[^"]*"/g,'').replace('<svg ','<svg fill="'+(themes.light[s.color]||s.color)+'" ');n=figma.createNodeFromSvg(svg);parent.appendChild(n);n.resize(s.w,s.h);ids.push(n.id,...n.findAll(()=>true).map(x=>x.id));for(const v of n.findAll(v=>v.type==='VECTOR'&&v.fills.length>0))v.fills=[paint(s.color)];}
 else{n=main?figma.createComponent():figma.createFrame();parent.appendChild(n);ids.push(n.id);n.resize(s.w,s.h);style(n,s);n.clipsContent=!!s.clip;s.children?.forEach(child=>create(n,child));if(s.layout){n.layoutMode=s.layout;n.primaryAxisSizingMode='FIXED';n.counterAxisSizingMode='FIXED';n.counterAxisAlignItems='CENTER';n.itemSpacing=0;}if(s.name.startsWith('Button / ')){n.layoutMode='HORIZONTAL';n.primaryAxisAlignItems='CENTER';n.counterAxisAlignItems='CENTER';n.primaryAxisSizingMode='FIXED';n.counterAxisSizingMode='FIXED';}}
 n.name=s.name;n.x=s.x||0;n.y=s.y||0;if(s.theme)n.setExplicitVariableModeForCollection(collection,s.theme==='dark'?dark:light);return n;
 }
 for(const item of scene.children)create(board,item);
 library.resize(libW,libY+20);figma.viewport.scrollAndZoomIntoView([board]);
 const nodes=board.findAll(()=>true),types={};for(const n of nodes)types[n.type]=(types[n.type]||0)+1;
 if(nodes.some(n=>Array.isArray(n.fills)&&n.fills.some(p=>p.type==='IMAGE')))throw new Error('Unexpected raster UI');
 if(nodes.some(n=>n.type==='TEXT'&&n.fontName.family!=='Roboto'))throw new Error('Font mismatch');
 return {pageId:page.id,boardId:board.id,createdNodeIds:ids,componentCount:Object.keys(components).length,screenCount:scene.children.filter(n=>!n.kind).length,variableIds:[...Object.values(vars).map(v=>v.id),space.id],fontFamily:'Roboto',types,rasterImages:0};
}
const themes=${JSON.stringify(themes)};const icons=${JSON.stringify(icons)};const scene=${JSON.stringify(scene)};
console.log(await buildPrismBrowser());
`;
