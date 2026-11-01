"""Verify UI-only scope and feature entry points against the recorded main revision."""
from pathlib import Path
from collections import Counter
import subprocess,json,hashlib,re
root=Path(__file__).resolve().parents[1];base='1c5782746ae2c822033ed6c1f0ec027398922307'
def old(path):return subprocess.check_output(['git','-C',str(root),'show',base+':'+path])
paths=subprocess.check_output(['git','-C',str(root),'ls-tree','-r','--name-only',base],text=True).splitlines()
protected=[]
for p in paths:
 if p.startswith('app/src/main/') and '/ui/' not in p:
  expected=old(p)
  if p=='app/src/main/res/values/themes.xml':
   expected=expected.replace(b'<string name="app_name">iBrowser</string>',b'<string name="app_name">Prism Browser</string>')
  elif p=='app/src/main/assets/privacy.html':
   expected=expected.replace(b'iBrowser',b'Prism Browser').replace(b'com.gr4nt.browser',b'com.gr4nt.prism.browser')
  assert (root/p).read_bytes()==expected,'Non-UI feature changed: '+p
  protected.append(p)
page='app/src/main/java/com/gr4nt/browser/ui/PageMenu.kt'
callbacks=lambda s:Counter(re.findall(r'Browser\.(\w+)\(',s))
assert callbacks((root/page).read_text())==callbacks(old(page).decode()),'Page menu lost a browser operation'
app=(root/'app/src/main/java/com/gr4nt/browser/ui/BrowserScreen.kt').read_text()
for endpoint in ['AddressEntry','TabsOverview','PageMenu','TabButtonMenu','DownloadsSheet','MediaSheet','SettingsSheet','PrivacySheet','SiteSettingsSheet','LibrarySheet','ArcActionsSheet','ArchiveSheet','SummaryOverlay','ReaderOverlay','ContextMenuOverlay','MiniPlayer','DownloadDialog','PermissionPromptOverlay','FullscreenOverlay','MediaPlayerOverlay']:
 assert endpoint+'(' in app,'Feature overlay missing: '+endpoint
scene=json.loads((root/'design/prism-scene.json').read_text())
def lum(h):
 vals=[int(h[i:i+2],16)/255 for i in [1,3,5]]
 return sum(w*(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4) for v,w in zip(vals,[.2126,.7152,.0722]))
def ratio(a,b):x,y=sorted([lum(a),lum(b)]);return(y+.05)/(x+.05)
contrast={}
for mode,palette in scene['themes'].items():
 contrast[mode]={}
 for key in ['ink','muted','accent']:
  worst=min(ratio(palette[key],palette[b]) for b in ['background','glow1','glow2'])
  assert worst>=4.5,f'Text contrast {mode}/{key}: {worst}'
  contrast[mode][key]=round(worst,2)
report={'base':base,'protected_files_unchanged':len(protected),'page_menu_browser_calls_preserved':sum(callbacks(old(page).decode()).values()),'feature_overlays_present':20,'theme_text_contrast_minimum':contrast,'note':'Source guards and theme checks do not replace Android interaction or visual tests.'}
print(json.dumps(report,indent=2))
