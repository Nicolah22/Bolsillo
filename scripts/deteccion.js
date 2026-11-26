/* ---------- Movimientos detectados en las notificaciones de los bancos (solo APK) ----------
   Android guarda las notificaciones que parecen movimientos de plata; aquí se leen,
   se interpretan y se muestran en una bandeja para confirmarlas con un toque. */

/* ===== Lector de mensajes (sin dependencias, se prueba con node) ===== */
function montoDe(txt){
  const conPeso=txt.match(/\$\s?([\d.,]+\d)/);
  const sinPeso=txt.match(/(?<![\d*\/:])(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?)(?![\d\/:])/);
  const crudo=conPeso?conPeso[1]:sinPeso?sinPeso[1]:null;
  if(!crudo)return null;
  let s=crudo;
  const ip=s.lastIndexOf("."),ic=s.lastIndexOf(",");
  if(ip>=0&&ic>=0){const dec=ip>ic?".":",",mil=dec==="."?",":".";s=s.split(mil).join("").replace(dec,".")}
  else{const sep=ip>=0?".":ic>=0?",":null;
    if(sep){const partes=s.split(sep);const ult=partes[partes.length-1];
      if(partes.length>2||ult.length===3)s=partes.join("");else s=partes.slice(0,-1).join("")+"."+ult}}
  const n=Math.round(parseFloat(s));
  return n>0?n:null;
}
const BANCOS=[
  ["Bancolombia",/bancolombia|com\.todo1/i],
  ["Nequi",/nequi/i],
  ["Nu",/\bnu\s?bank|\bnubank|com\.nu\.|^nu$/i],
  ["Daviplata",/daviplata/i],
  ["Davivienda",/davivienda/i],
  ["Banco de Bogotá",/banco de bogot/i],
  ["BBVA",/bbva/i],
  ["Lulo",/lulo/i],
  ["Movii",/movii/i],
  ["Dale",/\bdale\b/i]
];
function bancoDe(n){
  const txt=(n.titulo+" "+n.texto);
  const pref=txt.match(/^\s*([A-Za-zÁÉÍÓÚáéíóúñÑ ]{3,20}):/);
  const fuentes=[pref?pref[1]:"",n.app||"",n.pkg||"",txt];
  for(const f of fuentes)for(const[nombre,re]of BANCOS)if(re.test(f))return nombre;
  return (n.app||"Otra cuenta").trim();
}
const RE_ENTRA=/recibiste|te lleg[oó]|te enviaron|te consignaron|consignaci[oó]n|abono a tu|recibimos|ingres[oó] a tu|te transfiri/i;
const RE_SALE=/transferiste|enviaste|env[ií]o de plata|pagaste|compraste|compra |pago (exitoso|de|a |por)|retiraste|retiro|d[eé]bito|cargo|giraste|recargaste/i;
function parsearNoti(n){
  const txt=((n.titulo||"")+" · "+(n.texto||"")).replace(/\s+/g," ").trim();
  const monto=montoDe(txt);if(!monto)return null;
  let dir=null;
  if(RE_ENTRA.test(txt))dir="entra";else if(RE_SALE.test(txt))dir="sale";
  if(!dir)return null;
  const cuenta=bancoDe(n);
  const destino=(txt.match(/a la cuenta\s*\*?\s*(\d{3,})/i)||txt.match(/al (?:n[uú]mero|celular)\s*\*?\s*(\d{3,})/i)||[])[1]||null;
  let de=(txt.match(/\bde\s+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ]+(?:\s+[A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ]+)+)/)||[])[1]||null;
  const comercio=(txt.match(/\ben\s+([A-Z0-9][A-Z0-9 &.\-*']{2,40}?)(?=\s+(?:el|con|desde|por|a las)\b|[.,·]|$)/)||[])[1]||null;
  let fecha=null;const f=txt.match(/(\d{1,2})\/(\d{1,2})\/(\d{2,4})/);
  if(f){const y=f[3].length===2?"20"+f[3]:f[3];fecha=`${y}-${String(f[2]).padStart(2,"0")}-${String(f[1]).padStart(2,"0")}`}
  return {id:n.id,cuando:n.cuando||Date.now(),fecha,monto,dir,cuenta,destino,de,comercio:comercio?comercio.trim():null,texto:txt,app:n.app||""};
}
const normNombre=s=>(s||"").normalize("NFD").replace(/[̀-ͯ]/g,"").toUpperCase().replace(/[^A-Z ]/g," ").split(/\s+/).filter(Boolean);
function esMio(nombre,yo){
  const a=normNombre(nombre),b=normNombre(yo);if(!a.length||!b.length)return false;
  return b.filter(p=>a.includes(p)).length>=Math.min(2,b.length);
}
/* Junta una salida y una entrada del mismo monto en cuentas distintas (minutos de diferencia) */
function emparejar(lista,cfg){
  const usados=new Set(),out=[];
  const orden=[...lista].sort((a,b)=>a.cuando-b.cuando);
  for(const x of orden){
    if(usados.has(x.id))continue;
    if(x.dir==="sale"){
      const y=orden.find(z=>!usados.has(z.id)&&z.id!==x.id&&z.dir==="entra"&&z.monto===x.monto&&z.cuenta!==x.cuenta&&Math.abs(z.cuando-x.cuando)<20*60*1000);
      if(y){usados.add(x.id);usados.add(y.id);out.push({ids:[x.id,y.id],tipo:"traspaso",monto:x.monto,desde:x.cuenta,hacia:y.cuenta,cuando:x.cuando,fecha:x.fecha,textos:[x.texto,y.texto],nota:""});continue}
      if(x.destino&&cfg.mias&&cfg.mias[x.destino]){usados.add(x.id);out.push({ids:[x.id],tipo:"traspaso",monto:x.monto,desde:x.cuenta,hacia:cfg.mias[x.destino],cuando:x.cuando,fecha:x.fecha,textos:[x.texto],destino:x.destino,nota:""});continue}
      usados.add(x.id);out.push({ids:[x.id],tipo:"gasto",monto:x.monto,desde:x.cuenta,cuando:x.cuando,fecha:x.fecha,textos:[x.texto],comercio:x.comercio,destino:x.destino,cat:x.comercio&&cfg.comercios?cfg.comercios[x.comercio.toUpperCase()]||null:null,nota:x.comercio||""});
    }else{
      const sale=orden.find(z=>!usados.has(z.id)&&z.id!==x.id&&z.dir==="sale"&&z.monto===x.monto&&z.cuenta!==x.cuenta&&Math.abs(z.cuando-x.cuando)<20*60*1000);
      if(sale){usados.add(x.id);usados.add(sale.id);out.push({ids:[sale.id,x.id],tipo:"traspaso",monto:x.monto,desde:sale.cuenta,hacia:x.cuenta,cuando:sale.cuando,fecha:sale.fecha||x.fecha,textos:[sale.texto,x.texto],nota:""});continue}
      const mio=x.de&&esMio(x.de,cfg.nombre);
      usados.add(x.id);out.push({ids:[x.id],tipo:mio?"traspaso":"ingreso",monto:x.monto,desde:mio?null:null,hacia:x.cuenta,cuando:x.cuando,fecha:x.fecha,textos:[x.texto],de:x.de,nota:mio?"":(x.de?"De "+x.de.toLowerCase().replace(/\b\w/g,c=>c.toUpperCase()):"")});
    }
  }
  return out.sort((a,b)=>b.cuando-a.cuando);
}
if(typeof module!=="undefined")module.exports={montoDe,bancoDe,parsearNoti,emparejar,esMio};

/* ===== Bandeja dentro de la app ===== */
if(typeof window!=="undefined"&&typeof document!=="undefined"&&typeof $==="function"){
(function(){
  const NB=typeof plug==="function"?plug("NotiBanco"):null;
  const LS_D="bolsillo.detectados",LS_C="bolsillo.detectCfg";
  const leerLS=(k,d)=>{try{return JSON.parse(localStorage.getItem(k))??d}catch{return d}};
  const guardarLS=(k,v)=>{try{localStorage.setItem(k,JSON.stringify(v))}catch{}};
  let crudos=leerLS(LS_D,[]);           // notificaciones interpretadas, sin decidir
  let cfg=Object.assign({nombre:"",mias:{},comercios:{}},leerLS(LS_C,{}));
  let activo=null, abierta=false;

  /* --- interfaz --- */
  const banner=document.createElement("div");banner.className="aviso-copia aviso-det";banner.hidden=true;
  const panel=document.createElement("section");panel.className="panel det";panel.id="detPanel";panel.hidden=true;
  const ref=document.querySelector(".summary");ref.parentNode.insertBefore(banner,ref);ref.parentNode.insertBefore(panel,ref);
  const st=document.createElement("style");st.textContent=`
  .aviso-det{background:var(--accent-soft)}
  .det{gap:12px}
  .det-it{border:1px solid var(--line);border-radius:12px;padding:12px;display:grid;gap:10px}
  .det-top{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:8px;align-items:start}
  .det-top b{font-family:var(--num);font-size:17px}
  .det-top .q{font-weight:600}
  .det-top small{display:block;color:var(--muted);font-size:12.5px}
  .det-txt{font-size:12.5px;color:var(--muted);background:var(--bg);border-radius:8px;padding:8px 10px;line-height:1.4;overflow-wrap:anywhere}
  .det-f{display:grid;grid-template-columns:1fr 1fr;gap:8px}
  .det-f select,.det-f input{border:1px solid var(--line);background:var(--surface);border-radius:8px;padding:8px;width:100%;min-width:0}
  .det-f .full{grid-column:1/-1}
  .det-a{display:flex;gap:8px;flex-wrap:wrap}
  .det-a .btn{flex:1}
  .det-dup{font-size:12.5px;color:var(--warn);font-weight:600}
  .det-cfg{display:grid;gap:10px}
  .det-cfg input{border:1px solid var(--line);background:var(--bg);border-radius:9px;padding:9px 10px;width:100%}
  .det-st{display:flex;justify-content:space-between;gap:10px;align-items:center;font-size:14px}
  .det-st b.on{color:var(--in)} .det-st b.off{color:var(--out)}`;
  document.head.append(st);

  /* --- ajustes: activar y tu nombre --- */
  const cfgBox=document.createElement("div");cfgBox.className="det-cfg";
  cfgBox.innerHTML=`<div class="divider"></div><div class="panel-head"><h2>Movimientos de tus bancos</h2></div>
    <p class="mini" style="margin:0">Bolsillo puede leer las notificaciones de Bancolombia, Nequi, Nu, Daviplata y otros, y mostrarte cada movimiento para que lo agregues con un toque. Todo queda en este celular.</p>
    <div class="det-st"><span>Estado: <b id="detEstado">…</b></span><button type="button" class="btn ghost" id="detActivar">Activar</button></div>
    <label class="field"><span class="label">Tu nombre como aparece en los bancos</span><input id="detNombre" maxlength="60" placeholder="Ej: Nicolás Martínez"></label>
    <p class="mini" style="margin:0">Sirve para reconocer cuando te pasas plata a ti mismo entre tus cuentas.</p>`;
  const datos=[...document.querySelectorAll("#ajustes .panel-head h2")].find(h=>h.textContent==="Tus datos");
  const antes=datos?datos.closest(".panel-head").previousElementSibling:null;
  if(antes)antes.parentNode.insertBefore(cfgBox,antes);else $("ajustes").append(cfgBox);
  $("detNombre").value=cfg.nombre||"";
  $("detNombre").onchange=()=>{cfg.nombre=$("detNombre").value.trim();guardarLS(LS_C,cfg);pintar()};
  $("detActivar").onclick=async()=>{try{await NB?.abrirAjustes()}catch{toast("Abre Ajustes → Notificaciones → Acceso a notificaciones y activa Bolsillo")}};
  async function revisarEstado(){
    let disp=true;
    try{const r=await NB?.estado();activo=!!r?.activo;if(r&&r.disponible===false)disp=false}catch{activo=false}
    if(!disp){cfgBox.querySelectorAll(":scope > :not(.divider):not(.panel-head):not(.det-no)").forEach(e=>e.hidden=true);
      if(!cfgBox.querySelector(".det-no")){const p=document.createElement("p");p.className="mini det-no";p.style.margin="0";
        p.textContent="Esta versión de Bolsillo no lee notificaciones, porque Google bloquea esa función en las apps instaladas fuera de la Play Store. Para usarla, instala la versión \"con lector\".";cfgBox.append(p)}
      return}
    $("detEstado").textContent=activo?"Activado":"Desactivado";$("detEstado").className=activo?"on":"off";
    $("detActivar").textContent=activo?"Cambiar en Android":"Activar";
  }

  /* --- traer de Android --- */
  async function traer(){
    if(!NB)return;
    await revisarEstado();
    let r;try{r=await NB.pendientes()}catch{return}
    const lista=(r&&r.lista)||[];
    if(lista.length){
      const ya=new Set(crudos.map(c=>c.id));
      lista.forEach(n=>{if(ya.has(n.id))return;const p=parsearNoti(n);if(p)crudos.push(p)});
      crudos=crudos.slice(-300);guardarLS(LS_D,crudos);
      try{await NB.borrar({ids:lista.map(n=>n.id)})}catch{}
    }
    pintar();
  }

  /* --- decidir --- */
  const asegurarCuenta=n=>{if(!n)return;const L=listaCuentas();if(!L.includes(n)){cuentasL=[...L,n];llenarMedios();guardarCats()}};
  function quitar(ids){crudos=crudos.filter(c=>!ids.includes(c.id));guardarLS(LS_D,crudos);pintar()}
  const yaRegistrado=s=>movs.some(m=>m.monto===s.monto&&(m.medio===s.desde||m.medio===s.hacia||m.hacia===s.hacia)&&m.fecha===(s.fecha||fechaDe(s.cuando)));
  const fechaDe=t=>{const d=new Date(t);return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`};

  function pintar(){
    const sug=emparejar(crudos,cfg);
    banner.hidden=!sug.length;
    banner.innerHTML=`<span>🔔 <b>${sug.length}</b> movimiento${sug.length>1?"s":""} detectado${sug.length>1?"s":""} en tus bancos</span><button type="button" class="linkbtn">${abierta?"Ocultar":"Revisar"}</button>`;
    banner.querySelector("button").onclick=()=>{abierta=!abierta;pintar();if(abierta)panel.scrollIntoView({behavior:"smooth",block:"start"})};
    panel.hidden=!(abierta&&sug.length);if(panel.hidden)return;
    panel.innerHTML=`<div class="panel-head"><div style="display:grid;gap:2px"><h2>Movimientos detectados</h2><span class="hint">Revisa y agrega con un toque</span></div><button type="button" class="linkbtn" id="detTodo">Ignorar todos</button></div>`;
    panel.querySelector("#detTodo").onclick=()=>{const b=panel.querySelector("#detTodo");if(!b.dataset.c){b.dataset.c=1;b.textContent="¿Seguro? Toca otra vez";return}quitar(crudos.map(c=>c.id))};
    const L=listaCuentas();
    sug.forEach(s=>{
      const w=document.createElement("div");w.className="det-it";
      const f=s.fecha||fechaDe(s.cuando);
      const hora=new Date(s.cuando).toLocaleTimeString("es-CO",{hour:"numeric",minute:"2-digit"});
      w.innerHTML=`<div class="det-top"><div><span class="q"></span><small></small></div><b></b></div><div class="det-txt"></div>
        <div class="det-f"><label class="field full"><span class="label">Es un…</span><select class="tp"><option value="gasto">Gasto</option><option value="ingreso">Ingreso</option><option value="traspaso">Movimiento entre mis cuentas</option></select></label>
        <span class="zona" style="display:contents"></span>
        <label class="field"><span class="label">Monto</span><input class="mo" inputmode="decimal"></label>
        <label class="field"><span class="label">Fecha</span><input class="fe" type="date"></label>
        <label class="field full"><span class="label">Nota (opcional)</span><input class="no" maxlength="80"></label></div>
        <div class="det-dup" hidden>Parece que ya lo registraste. Revisa antes de agregarlo.</div>
        <div class="det-a"><button type="button" class="btn ok">Agregar</button><button type="button" class="btn ghost ig">Ignorar</button></div>`;
      const q=w.querySelector(".q");
      q.textContent=s.tipo==="traspaso"?`${s.desde||"¿?"} → ${s.hacia||"¿?"}`:s.tipo==="gasto"?`Salió de ${s.desde}`:`Entró a ${s.hacia}`;
      w.querySelector("small").textContent=`${new Date(f+"T12:00:00").toLocaleDateString("es-CO",{weekday:"short",day:"numeric",month:"short"})} · ${hora}`;
      w.querySelector(".det-top b").textContent=fmt.format(s.monto);
      w.querySelector(".det-txt").textContent=s.textos.join("\n\n");
      w.querySelector(".det-txt").style.whiteSpace="pre-line";
      const tp=w.querySelector(".tp"),zona=w.querySelector(".zona");
      tp.value=s.tipo;w.querySelector(".mo").value=s.monto.toLocaleString("es-CO");w.querySelector(".fe").value=f;w.querySelector(".no").value=s.nota||"";
      const opts=(sel,arr,v)=>{arr.forEach(x=>{const o=document.createElement("option");o.textContent=x;sel.append(o)});if(v)sel.value=v};
      const conCuentas=extra=>{const set=[...L];extra.forEach(x=>{if(x&&!set.includes(x))set.push(x)});return set};
      function zonaPintar(){
        zona.innerHTML="";const t=tp.value;
        if(t==="traspaso"){
          zona.innerHTML=`<label class="field"><span class="label">Desde</span><select class="de"></select></label><label class="field"><span class="label">Hacia</span><select class="ha"></select></label>`;
          const cs=conCuentas([s.desde,s.hacia]);
          const de=zona.querySelector(".de"),ha=zona.querySelector(".ha");
          if(!s.desde){const o=document.createElement("option");o.value="";o.textContent="Elige la cuenta";o.disabled=true;de.append(o)}
          opts(de,cs,s.desde||"");opts(ha,cs,s.hacia||s.desde);
          if(!s.desde)de.value="";
        }else{
          const cat=document.createElement("label");cat.className="field";cat.innerHTML=`<span class="label">Categoría</span><select class="ca"></select>`;
          const cu=document.createElement("label");cu.className="field";cu.innerHTML=`<span class="label">Cuenta</span><select class="cu"></select>`;
          opts(cat.querySelector("select"),catsDe(t),t==="gasto"?(s.cat||""):"");
          opts(cu.querySelector("select"),conCuentas([s.desde,s.hacia]),t==="gasto"?(s.desde||s.hacia):(s.hacia||s.desde));
          zona.append(cat,cu);
        }
        w.querySelector(".ok").textContent=t==="traspaso"?"Agregar movimiento entre cuentas":t==="gasto"?"Agregar gasto":"Agregar ingreso";
      }
      tp.onchange=zonaPintar;zonaPintar();
      w.querySelector(".det-dup").hidden=!yaRegistrado(s);
      w.querySelector(".ig").onclick=()=>quitar(s.ids);
      w.querySelector(".ok").onclick=async()=>{
        const t=tp.value,monto=parseMonto(w.querySelector(".mo").value),fecha=w.querySelector(".fe").value||f,nota=w.querySelector(".no").value.trim();
        if(!(monto>0)){toast("Revisa el monto");return}
        let m;
        if(t==="traspaso"){
          const de=zona.querySelector(".de").value,ha=zona.querySelector(".ha").value;
          if(!de){toast("Elige desde qué cuenta salió");return}
          if(de===ha){toast("El origen y el destino son la misma cuenta");return}
          asegurarCuenta(de);asegurarCuenta(ha);
          m={tipo:"traspaso",cat:"Traspaso",monto,medio:de,hacia:ha,fecha,nota};
          if(s.destino&&!cfg.mias[s.destino]){cfg.mias[s.destino]=ha;guardarLS(LS_C,cfg)}
        }else{
          const ca=zona.querySelector(".ca").value,cu=zona.querySelector(".cu").value;
          asegurarCuenta(cu);
          m={tipo:t,cat:ca,monto,medio:cu,fecha,nota};
          if(t==="gasto"&&s.comercio){cfg.comercios[s.comercio.toUpperCase()]=ca;guardarLS(LS_C,cfg)}
        }
        m.creado=new Date().toISOString();m.detectado=true;
        try{await store.save(m,"n"+uid());quitar(s.ids);toast(t==="traspaso"?`${m.medio} → ${m.hacia} · ${fmt.format(monto)}`:`${t==="gasto"?"Gasto":"Ingreso"} agregado · ${fmt.format(monto)}`)}
        catch{toast("No se pudo guardar")}
      };
      panel.append(w);
    });
  }

  /* --- cuándo revisar --- */
  traer();
  try{plug("App")?.addListener?.("resume",()=>traer())}catch{}
  setInterval(()=>{if(!document.hidden)traer()},30000);
  const _abrirAj=$("ajBtn").onclick;$("ajBtn").onclick=e=>{_abrirAj&&_abrirAj(e);revisarEstado()};
  window.__det={traer,pintar};
})();
}
