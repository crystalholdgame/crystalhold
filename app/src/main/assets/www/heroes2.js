/* Crystalhold — hero dressing and move effects (approved in the Moves Labs, Nov 2026).
   Gameplay numbers live in index.html; this file only adds animation clips, props (hammer, wings),
   visual effects and phone vibration on strong moves. */
(function(){
/* clips: [basic attack, skill 1, skill 2, skill 3] (names inside each hero model) */
const CL={knight:['slash','spin','cry','upslash'],mage:['c1','c3','charged','c4'],archer:['shot1','shot3','drawback','call'],
  ice:['cast0','spin','charged','cast3'],fire:['cast0','charged','slam','charged'],thunder:['throw','jumpslam','slam','spin'],
  laser:[null,'pose','jump','kungfu'],dragon:['cast0','charged','charged','cheer'],wolfm:['punch','howl','cheer','jump'],
  hoop:['pitch','throw','pitch','dunk'],goal:['kickball','flykick','flykick','kickball']};
const ALT={goal:{0:'doublekick'}};/* Sami's power shot alternates fly-kick and double-kick */

/* ---------- vibration (strong moves only, player can turn it off) ---------- */
window.buzz=function(p){try{if(S&&S.vib===false)return;if(navigator.vibrate)navigator.vibrate(p);}catch(e){}};
const VIBL={ar:['الاهتزاز: يعمل','الاهتزاز: متوقف'],en:['Vibration: on','Vibration: off'],es:['Vibración: sí','Vibración: no'],fr:['Vibration : oui','Vibration : non'],pt:['Vibração: sim','Vibração: não'],de:['Vibration: an','Vibration: aus']};
function vibLabel(){const L=VIBL[window.LANG]||VIBL.en;return L[S.vib===false?1:0];}
function addVibButtons(){for(const id of ['soundBtn','soundBtn2']){const sb=document.getElementById(id);if(!sb||document.getElementById(id+'V'))continue;
  const b=document.createElement('button');b.className=sb.className;b.id=id+'V';b.textContent=vibLabel();
  b.addEventListener('click',ev=>{ev.stopPropagation();S.vib=S.vib===false?true:false;try{save();}catch(e){}document.querySelectorAll('#soundBtnV,#soundBtn2V').forEach(x=>x.textContent=vibLabel());if(S.vib!==false)buzz(40);try{sfx('ui');}catch(e){}});
  sb.insertAdjacentElement('afterend',b);}}
const _sl=window.soundLabel;if(_sl)window.soundLabel=function(){_sl.apply(this,arguments);try{addVibButtons();document.querySelectorAll('#soundBtnV,#soundBtn2V').forEach(x=>x.textContent=vibLabel());}catch(e){}};
setTimeout(()=>{try{addVibButtons();}catch(e){}},500);

/* ---------- small helpers ---------- */
const V3=THREE.Vector3,_a=new V3(),_b=new V3(),_Y=new V3(0,1,0);
function sp(x,y,h,vx,vy,vh,col,size,life,g){if(!canAdd())return;G.parts.push({k:'spark',x,y,h,vx,vy,vh,g:g||0,life,max:life,size,color:col,drag:0});}
function later(h,t,fn){heroSeq(h,[[t,fn]]);}
function boneLocal(inner,name,out){const b=inner.getObjectByName(name);if(!b)return null;b.getWorldPosition(out);return inner.worldToLocal(out);}

/* ---------- dressing: clips + props ---------- */
window.heroDress=function(type,inner,H,gm){const C=CL[type];if(!C||!H.mixer||!gm.clips)return;
  const act=n=>{if(!n||!gm.clips[n])return null;const a=H.mixer.clipAction(gm.clips[n]);a.setLoop(THREE.LoopOnce,1);a.clampWhenFinished=true;return a;};
  const at=act(C[0]);if(at)H.acts.attack=at;H.skActs=[act(C[1]),act(C[2]),act(C[3])];
  if(ALT[type]){H.alt={};for(const k in ALT[type])H.alt[k]=[H.skActs[k],act(ALT[type][k])];}
  if(type==='thunder'&&GLBM.prop_hammer&&GLBM.prop_hammer.ready){const g=new THREE.Group(),m=GLBM.prop_hammer.scene.clone();g.add(m);
    const gl=new THREE.Sprite(GLOWM('#7fe8ff'));gl.scale.setScalar(.7);gl.position.y=.56;g.add(gl);inner.add(g);H.hammer=g;H.hamAway=0;}
  if(type==='laser'&&GLBM.prop_wing&&GLBM.prop_wing.ready){const g=new THREE.Group(),W=[];for(const sx of [1,-1]){const piv=new THREE.Group(),m=GLBM.prop_wing.scene.clone();m.position.set(.42,.38,0);const ho=new THREE.Group();ho.add(m);ho.scale.set(.8*sx,.8,.8);piv.add(ho);g.add(piv);W.push(piv);}
    g.userData.W=W;inner.add(g);H.wings=g;}
  /* Fenrik's dark hair read as a black blob from the top camera: brighten his own materials */
  if(type==='wolfm'){const done=new Map();inner.traverse(o=>{if(o.isMesh&&o.material){let m=done.get(o.material);if(!m){m=o.material.clone();m.emissiveIntensity=.7;done.set(o.material,m);}o.material=m;}});}
  H.dressTick=dressTick;};
function dressTick(R,h,dt,T){const inner=R.inner;
  if(R.hammer){const hm=R.hammer;R.hamAway=Math.max(0,R.hamAway-dt);hm.visible=R.hamAway<=0;
    if(R.lastAtkH!==h.atkN){R.lastAtkH=h.atkN;const D=HEROES[h.type],tg=nearestEnemy(h.x,h.y,D.range+.3);if(tg&&!h.dead)throwHammer(R,h,tg);}
    if(hm.visible){const hp=boneLocal(inner,'RightHand',_a),fp=boneLocal(inner,'RightForeArm',_b);if(hp&&fp){const d=hp.clone().sub(fp).normalize();hm.position.copy(hp).addScaledVector(d,-.12);hm.quaternion.setFromUnitVectors(_Y,d);}}}
  if(R.wings){const p=boneLocal(inner,'Spine02',_a)||boneLocal(inner,'Spine01',_a);if(p){R.wings.position.set(p.x,p.y+.05,p.z-.16);}const busy=R.onceT>0||h.leap,f=Math.sin(T*(busy?16:6))*(busy?.55:.25);const [L,Rw]=R.wings.userData.W;L.rotation.set(-1.05,-.25,.25+f);Rw.rotation.set(-1.05,.25,-.25-f);}
  updFly(dt);}

/* ---------- Bruno: the hammer is thrown, spins, hits, vanishes and comes back to his hand ---------- */
const FLY=[];
function flyMesh(R){const g=new THREE.Group(),m=GLBM.prop_hammer.scene.clone();m.position.y=-.4;g.add(m);const gl=new THREE.Sprite(GLOWM('#7fe8ff'));gl.scale.setScalar(.9);g.add(gl);const s=R.inner.getWorldScale(new V3()).x/V.lv.getWorldScale(new V3()).x;g.scale.setScalar(s);V.lv.add(g);return g;}
function throwHammer(R,h,tg,ft,onHit){if(!GLBM.prop_hammer||!GLBM.prop_hammer.ready)return;const from=V.lv.worldToLocal(R.hammer.getWorldPosition(new V3()));const to=new V3(tg.x,tg.fly?.9:.42,tg.y);const d=from.distanceTo(to);ft=ft||Math.max(.12,d/8);
  R.hamAway=ft+.2;FLY.push({m:flyMesh(R),from,to,t:0,ft,onHit,R,h});}
function updFly(dt){for(let i=FLY.length-1;i>=0;i--){const f=FLY[i];f.t+=dt;const k=Math.min(1,f.t/f.ft);_a.lerpVectors(f.from,f.to,k);_a.y+=Math.sin(k*Math.PI)*.3;f.m.position.copy(_a);f.m.rotation.set(-k*Math.PI*6,k*3,0);
    if(Math.random()<.6)sp(_a.x,_a.z,_a.y,0,0,0,'#9ff0ff',.1,.2);
    if(k>=1){V.lv.remove(f.m);FLY.splice(i,1);flashFx(f.to.x,f.to.z,.6,'#bff6ff',.14,f.to.y);burst(f.to.x,f.to.z,'#7fe8ff',10,3,.08,.3,f.to.y);if(f.onHit)try{f.onHit();}catch(e){}
      later(f.h,.12,()=>{const hh=f.h;burst(hh.x,hh.y,'#bff6ff',8,2,.08,.25,.9);});}}}
window.hamChain=function(h,hits){const R=getMeshOf(h);if(!R||!R.hammer||!hits.length)return;let k=0;const step=()=>{if(k>=hits.length)return;const e=hits[k++];throwHammer(R,h,e,.16,()=>{G.bolts.push({pts:[[e.x,e.y,1.4],[e.x,e.y,.2]],life:.2});later(h,.02,step);});};step();R.hamAway=hits.length*.2+.3;};
function getMeshOf(o){try{return live.get(o)||null;}catch(e){return null;}}

/* ---------- Twin: fire orb and ice orb spiral around each other to the target ---------- */
window.twinOrbs=function(h,x,y,ft){const n=12;for(let i=0;i<=n;i++){later(h,i*ft/n,()=>{const u=i/n,cx=h.x+(x-h.x)*u,cy=h.y+(y-h.y)*u,r=.45*(1-u),a=u*Math.PI*4,ht=.9+Math.sin(u*Math.PI)*.6;
  const nx=-(y-h.y),ny=(x-h.x),L=Math.hypot(nx,ny)||1;for(const [s,col] of [[1,'#ff7a2a'],[-1,'#8fdcff']]){const ox=nx/L*Math.cos(a)*r*s,oy=ny/L*Math.cos(a)*r*s;sp(cx+ox,cy+oy,ht+Math.sin(a)*r*s,0,0,0,col,.26,.3);sp(cx+ox,cy+oy,ht+Math.sin(a)*r*s,0,0,.5,s>0?'#ffe08a':'#e8f7ff',.12,.4);}});}
  later(h,ft,()=>{for(let k=0;k<3;k++)later(h,k*.12,()=>{ringFx(x,y,k%2?'#8fdcff':'#ff9a3a',.2,1.8,.45,.1);for(let j=0;j<14;j++){const a=j/14*6.28+k;sp(x+Math.cos(a)*.6,y+Math.sin(a)*.6,.2+k*.4,-Math.sin(a)*3,Math.cos(a)*3,1.4,j%2?'#ff7a2a':'#8fdcff',.16,.5);}});});};

/* ---------- Fenrik: a spirit wolf rises and howls ---------- */
function spiritWolf(h){const m=GLBM.wolf;if(!m||!m.ready)return;const g=THREE.SkeletonUtils.clone(m.scene);const mat=new THREE.MeshBasicMaterial({color:'#cfe4ff',transparent:true,opacity:.55,blending:THREE.AdditiveBlending,depthWrite:false});
  g.traverse(o=>{if(o.isMesh){o.material=mat;o.castShadow=false;}});const bb=new THREE.Box3().setFromObject(g),s=1.5/Math.max(.01,bb.max.y-bb.min.y);const root=new THREE.Group();g.scale.setScalar(s);g.rotation.y=Math.PI/2;root.add(g);
  root.position.set(h.x-Math.cos(h.ang)*.3,.2,h.y-Math.sin(h.ang)*.3);root.rotation.y=-h.ang;V.lv.add(root);let t=0;const tick=()=>{t+=1/30;const u=t/1.4;root.position.y=.2+u*.9;g.rotation.x=-u*.6;mat.opacity=.6*(u<.15?u/.15:1-Math.max(0,u-.5)/.5);if(u<1)later(h,1/30,tick);else V.lv.remove(root);};tick();}

/* ---------- per-move effects (numbers unchanged; these only add visuals + vibration) ---------- */
const FX={
  whirl(h){buzz([20,20,20,20,20,20]);for(let i=0;i<8;i++)later(h,i*.2,()=>{ringFx(h.x,h.y,'#ffd98a',.3,1.3,.25,.1);for(let k=0;k<5;k++){const a=Math.random()*6.28;sp(h.x+Math.cos(a)*1.1,h.y+Math.sin(a)*1.1,.5,-Math.sin(a)*4,Math.cos(a)*4,0,'#fff6d8',.14,.25);}});},
  cry(h){buzz([60,40,100]);for(let i=0;i<3;i++)later(h,i*.12,()=>ringFx(h.x,h.y,'#ffb36b',.2,2.4,.55,.12));lightFlash(h.x,h.y,'#ffd27a',2.4);for(let k=0;k<26;k++){const a=Math.random()*6.28,r=Math.random()*.6;sp(h.x+Math.cos(a)*r,h.y+Math.sin(a)*r,.2,0,0,1.5+Math.random()*1.5,'#8aff9a',.12,.8);}},
  sky(h,x,y){ringFx(x,y,'#9fd8ff',1.35,1.4,.55,.05);for(let i=0;i<6;i++)later(h,i*.08,()=>sp(x,y,3.6-i*.6,0,0,-8,'#e9f6ff',.35,.18));later(h,.55,()=>{buzz([90,40,150]);flashFx(x,y,1.6,'#e9f6ff',.2,.4);ringFx(x,y,'#ffffff',.1,1,.35,.16);debris(x,y,'#c8c0b0',16,4,.2);});},
  chain(h){buzz([30,20,30,20,60]);lightFlash(h.x,h.y,'#c9a6ff',2.2);flashFx(h.x,h.y,.8,'#e6d8ff',.15,1.2);},
  storm(h,x,y){buzz([40,30,40]);for(let i=0;i<6;i++)later(h,i*.45,()=>{smokeFx(x+(Math.random()-.5)*1.6,y+(Math.random()-.5)*1.2,4,.7,true,2.6);});},
  surge(h){buzz([40,40,80]);ringFx(h.x,h.y,'#ffc8f8',.2,2.4,.9,.2);for(let i=0;i<10;i++)later(h,i*.2,()=>{for(let k=0;k<3;k++){const a=Math.random()*6.28;sp(h.x+Math.cos(a)*.8,h.y+Math.sin(a)*.8,.1,0,0,2,'#ffc8f8',.14,.6);}});},
  volley(h,x,y){later(h,.01,()=>{for(const v of (G.volleys||[])){if(Math.hypot(v.x-x,v.y-y)>1.6||v._fx)continue;v._fx=1;const px=v.x,py=v.y;for(let k=0;k<3;k++)later(h,v.t+k*.28,()=>fireFx(px,py,2,.05,.16,.06,1));}});},
  pierce(h){buzz(40);later(h,.02,()=>{const a=h.ang;for(let k=1;k<6;k++)ringFx(h.x+Math.cos(a)*k*1.3,h.y+Math.sin(a)*k*1.3,'#ffe28a',.1,.45,.3,.06);});},
  nova(h){buzz([40,30,80]);lightFlash(h.x,h.y,'#bfeaff',2.4);},
  prison(h){buzz(60);},
  fireball(h,x,y){later(h,.45,()=>{buzz(50);flashFx(x,y,1.3,'#ffb84a',.2,.4);emberFx(x,y,14,.3,3);});},
  rain(h,x,y){for(let k=0;k<5;k++)later(h,.5+k*.25,()=>{shakeIt(.12);if(k%2===0)buzz(35);});},
  tstorm(h,x,y){buzz([40,30,60]);for(let i=0;i<5;i++)later(h,i*.18,()=>smokeFx(x+(Math.random()-.5)*1.4,y+(Math.random()-.5)*1.2,3,.7,true,2.6));},
  hthrow(h){buzz(40);},
  eyebeam(h){buzz(30);},
  lspin(h){buzz([30,30,30,30,60]);},
  fbreath(h){buzz(35);},ibreath(h){buzz(35);},
  howl(h){buzz([50,30,90]);spiritWolf(h);for(let i=0;i<3;i++)later(h,.15+i*.15,()=>ringFx(h.x,h.y,'#e0e0ff',.3,2.4,.6,.08));},
  pack(h){for(let k=0;k<12;k++){const a=k/12*6.28;sp(h.x+Math.cos(a)*.6,h.y+Math.sin(a)*.6,.2,0,0,1.2,'#c8c8ff',.18,.6);}},
  shot(h){buzz(40);later(h,.02,()=>{const a=h.ang,cols=['#ff5a5a','#ffd23a','#7dffd8','#9f8cff','#ff9ff0'];for(let k=0;k<30;k++){const u=k/30*7.5,w=Math.sin(u*2)*.25;sp(h.x+Math.cos(a)*u-Math.sin(a)*w,h.y+Math.sin(a)*u+Math.cos(a)*w,.35+Math.cos(u*2)*.2,0,0,0,cols[k%5],.16,.35+u*.03);}});},
  goalx(h,x,y){later(h,.7,()=>buzz([80,40,160]));},
  three(h,x,y){later(h,.6,()=>buzz(40));},
};
const _sf=window.skillFlash;window.skillFlash=function(h,col,x,y){_sf.apply(this,arguments);try{const R=getMeshOf(h);if(R&&R.alt&&R.alt[h.skIdx||0]){const pr=R.alt[h.skIdx||0];R.altN=(R.altN||0)+1;R.skActs[h.skIdx||0]=pr[R.altN%2]||pr[0];}}catch(e){}try{const sk=HEROES[h.type].skills[h.skIdx||0];const f=FX[sk.id];if(f)f(h,x===undefined?h.x:x,y===undefined?h.y:y);}catch(e){}};
})();
