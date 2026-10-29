#!/usr/bin/env python3
"""Convierte bolsillo.html (versión de Claude) en www/index.html (versión APK).

La app es la misma; aquí solo se cambia:
- documento HTML completo con fuentes incluidas (funciona sin internet)
- los datos se guardan en el celular (IndexedDB), sin cuentas
- descargar copias usa el menú Compartir de Android
- botón Atrás de Android y recordatorio de copia de seguridad
"""
import re, sys, shutil, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "src" / "bolsillo.html"
OUT = ROOT / "www" / "index.html"

s = SRC.read_text(encoding="utf-8")

def rep(a, b, count=1):
    global s
    n = s.count(a)
    if n != count:
        sys.exit(f"build: se esperaba {count} vez/veces y hay {n}: {a[:80]!r}")
    s = s.replace(a, b)

# --- cabecera: quitar fuentes remotas y meta de Claude ---
s = re.sub(r'<meta name="claude:viewport-fit-sides"[^>]*>\n', "", s, count=1)
s = re.sub(r'<link rel="preconnect"[^>]*>\n', "", s, count=1)
s = re.sub(r'<link rel="stylesheet" href="https://fonts.googleapis.com[^>]*>\n', "", s, count=1)

FONTS = """
@font-face{font-family:"Bricolage Grotesque";font-weight:500;font-display:swap;src:url(fonts/bricolage-grotesque-latin-500-normal.woff2) format("woff2")}
@font-face{font-family:"Bricolage Grotesque";font-weight:700;font-display:swap;src:url(fonts/bricolage-grotesque-latin-700-normal.woff2) format("woff2")}
@font-face{font-family:"Figtree";font-weight:400;font-display:swap;src:url(fonts/figtree-latin-400-normal.woff2) format("woff2")}
@font-face{font-family:"Figtree";font-weight:500;font-display:swap;src:url(fonts/figtree-latin-500-normal.woff2) format("woff2")}
@font-face{font-family:"Figtree";font-weight:600;font-display:swap;src:url(fonts/figtree-latin-600-normal.woff2) format("woff2")}
@font-face{font-family:"IBM Plex Mono";font-weight:400;font-display:swap;src:url(fonts/ibm-plex-mono-latin-400-normal.woff2) format("woff2")}
@font-face{font-family:"IBM Plex Mono";font-weight:500;font-display:swap;src:url(fonts/ibm-plex-mono-latin-500-normal.woff2) format("woff2")}
@font-face{font-family:"IBM Plex Mono";font-weight:600;font-display:swap;src:url(fonts/ibm-plex-mono-latin-600-normal.woff2) format("woff2")}
html{-webkit-text-size-adjust:100%}
:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}
body{margin:0;font-size:14px;-webkit-tap-highlight-color:transparent}
img{max-width:100%}
.aviso-copia{display:flex;gap:10px;align-items:center;justify-content:space-between;background:color-mix(in srgb,var(--warn) 14%,transparent);border-radius:12px;padding:10px 14px;font-size:13.5px}
"""
rep("<style>\n", "<style>" + FONTS, 1)

# --- textos ---
rep('<p class="note">Tus movimientos son privados: solo tú los ves, en cualquier dispositivo donde abras esta página.</p>',
    '<p class="note">Tus datos se guardan solo en este celular. Haz una copia de vez en cuando en ⚙ → Tus datos.</p>')

# --- quitar libretas (son por cuenta de Claude) ---
s = re.sub(r'    <div class="divider"></div>\n    <div class="panel-head"><h2>Libretas</h2></div>\n.*?<div id="libBox"[^\n]*\n', "", s, count=1, flags=re.S)

# --- aviso de copia arriba del resumen ---
rep('  <section class="panel cuadrar" id="cuadrar" hidden>',
    '  <div class="aviso-copia" id="avisoCopia" hidden></div>\n  <section class="panel cuadrar" id="cuadrar" hidden>')

# --- almacenamiento en el celular (IndexedDB) ---
i = s.index("async function init(){")
j = s.index("\n}\n", i) + 3
IDB = r'''async function init(){
  render();
  try{await navigator.storage?.persist?.()}catch{}
  let base=null;
  const abrir=()=>new Promise((ok,mal)=>{const r=indexedDB.open("bolsillo",1);r.onupgradeneeded=()=>r.result.createObjectStore("kv");r.onsuccess=()=>ok(r.result);r.onerror=()=>mal(r.error)});
  try{base=await abrir()}catch{localStore();$("modo").textContent="Guardado en este celular";return}
  const leer=()=>new Promise(ok=>{const t=base.transaction("kv").objectStore("kv").get("estado");t.onsuccess=()=>ok(t.result||null);t.onerror=()=>ok(null)});
  let est=await leer()||{movs:{},libro:null};
  let pend=Promise.resolve();
  const escribir=()=>{const copia=JSON.parse(JSON.stringify(est));pend=pend.then(()=>new Promise((ok,mal)=>{const tx=base.transaction("kv","readwrite");tx.objectStore("kv").put(copia,"estado");tx.oncomplete=ok;tx.onerror=()=>mal(tx.error)}));return pend};
  const emitir=()=>{movs=Object.entries(est.movs).map(([id,v])=>({id,...v}));setLibroState(est.libro);libroCargado=movsCargados=true;renderCats();render();migrar()};
  store.save=async(m,id)=>{est.movs[id||("m"+uid())]=m;emitir();await escribir()};
  store.remove=async id=>{delete est.movs[id];emitir();await escribir()};
  store.setLibro=async l=>{est.libro=l;emitir();await escribir()};
  store.restore=async(libroN,lista)=>{est={movs:Object.fromEntries(lista.map(({id,...m})=>[id,m])),libro:libroN};emitir();await escribir()};
  $("modo").textContent="Guardado en este celular";
  emitir();
}
'''
s = s[:i] + IDB + s[j:]

# --- descargar con el menú Compartir de Android ---
i = s.index("async function descargar(nombre,texto,tipoMime){")
j = s.index("\n}\n", i) + 3
DL = r'''async function descargar(nombre,texto,tipoMime){
  const P=window.Capacitor?.Plugins;
  if(window.Capacitor?.isNativePlatform?.()&&P?.Filesystem&&P?.Share){
    try{const r=await P.Filesystem.writeFile({path:nombre,data:texto,directory:"CACHE",encoding:"utf8"});
      await P.Share.share({title:nombre,url:r.uri,dialogTitle:"Guardar o enviar "+nombre});return true}
    catch(e){if(String(e?.message||e).toLowerCase().includes("cancel"))return false;toast("No se pudo guardar el archivo");return false}}
  try{const a=document.createElement("a");a.href=URL.createObjectURL(new Blob([texto],{type:tipoMime}));a.download=nombre;document.body.append(a);a.click();setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},1000);return true}
  catch{toast("No se pudo descargar");return false}
}
'''
s = s[:i] + DL + s[j:]

# recordar la fecha de la última copia
rep("  if(ok)toast(`Copia descargada · ${movs.length} movimientos`);",
    "  if(ok){toast(`Copia lista · ${movs.length} movimientos`);try{localStorage.setItem(\"bolsillo.ultimaCopia\",Date.now())}catch{};avisoCopia()}")

# --- extras de la app: aviso de copia y botón Atrás ---
EXTRA = r'''
/* ---------- Solo en la app del celular ---------- */
function avisoCopia(){
  const A=$("avisoCopia");if(!A)return;
  let u=0;try{u=+localStorage.getItem("bolsillo.ultimaCopia")||0}catch{}
  const dias=u?Math.floor((Date.now()-u)/864e5):null;
  const mostrar=movs.length>=15&&(dias===null||dias>=30);
  A.hidden=!mostrar;if(!mostrar)return;
  A.innerHTML=`<span></span><button type="button" class="linkbtn">Hacer copia</button>`;
  A.firstChild.textContent=dias===null?"Todavía no has guardado una copia de tus datos.":`Hace ${dias} días no guardas una copia de tus datos.`;
  A.querySelector("button").onclick=()=>$("bkDown").click();
}
{const _render=render;render=function(){_render();avisoCopia()}}
/* Barra de estado de Android acorde al tema */
function barraEstado(){
  const SB=window.Capacitor?.Plugins?.StatusBar;if(!SB||!window.Capacitor?.isNativePlatform?.())return;
  const R=document.documentElement,t=R.getAttribute("data-theme");
  const oscuro=t?t==="dark":matchMedia("(prefers-color-scheme: dark)").matches;
  SB.setStyle({style:oscuro?"DARK":"LIGHT"}).catch(()=>{});
  SB.setBackgroundColor({color:oscuro?"#11161c":"#eef1f4"}).catch(()=>{});
}
window.addEventListener("bolsillo-tema",barraEstado);
matchMedia("(prefers-color-scheme: dark)").addEventListener?.("change",barraEstado);
barraEstado();
try{
  const App=window.Capacitor?.Plugins?.App;
  App?.addListener?.("backButton",()=>{
    if(!$("ajustes").hidden){$("ajustes").hidden=true;return}
    if(!$("cuadrar").hidden){$("cuadrar").hidden=true;return}
    if(typeof pantalla!=="undefined"&&pantalla!=="inicio"){setPantalla("inicio");return}
    if(editId){resetForm();return}
    App.exitApp();
  });
}catch{}
'''
rep('$("fecha").value=hoy();setTipo("gasto");init();', EXTRA + '$("fecha").value=hoy();setTipo("gasto");init();')

# --- documento completo ---
m = re.match(r"<title>(.*?)</title>\n", s)
title = m.group(1); s = s[m.end():]
doc = f'''<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#eef1f4" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#11161c" media="(prefers-color-scheme: dark)">
<title>{title}</title>
</head>
<body>
{s}
</body>
</html>
'''
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(doc, encoding="utf-8")

# fuentes
fd = OUT.parent / "fonts"; fd.mkdir(exist_ok=True)
nm = ROOT / "node_modules" / "@fontsource"
for fam, ws in [("bricolage-grotesque", [500, 700]), ("figtree", [400, 500, 600]), ("ibm-plex-mono", [400, 500, 600])]:
    for w in ws:
        f = f"{fam}-latin-{w}-normal.woff2"
        src = nm / fam / "files" / f
        if src.exists():
            shutil.copy(src, fd / f)
        elif not (fd / f).exists():
            sys.exit(f"build: falta la fuente {f}")
print(f"OK → {OUT} ({OUT.stat().st_size//1024} KB)")
