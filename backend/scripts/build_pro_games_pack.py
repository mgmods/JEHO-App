# -*- coding: utf-8 -*-
"""Write polished HTML5 game pages into backend/public/games."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "public" / "games"
ROOT.mkdir(parents=True, exist_ok=True)

HEAD = """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover"/>
<title>{title}</title>
<link rel="stylesheet" href="./common/aura-kit.css"/>
{extra_css}
</head>
<body>
<div class="aura-shell">
  <div class="aura-top">
    <div class="aura-title">{title}</div>
    <div class="aura-chip" id="metaChip">{chip}</div>
  </div>
  <div class="aura-badge-row"><span class="aura-badge" id="stampBadge"></span><span class="aura-badge" id="emojiBadge"></span></div>
  {body}
  <div class="aura-msg" id="msg">{hint}</div>
  <div class="aura-panel">{panel}</div>
</div>
<script src="./common/aura-kit.js"></script>
<script>
{js}
</script>
</body>
</html>
"""


def write(name: str, html: str) -> None:
    path = ROOT / name
    path.write_text(html, encoding="utf-8")
    print("wrote", path.name, "bytes", path.stat().st_size)


def page(title, chip, hint, body, panel, js, extra_css=""):
    return HEAD.format(
        title=title,
        chip=chip,
        hint=hint,
        body=body,
        panel=panel,
        js=js,
        extra_css=extra_css,
    )


# ── 1) Dice 3D ──────────────────────────────────────────────────────────────
write(
    "dice.html",
    page(
        "نرد ثلاثي الأبعاد",
        "1–6",
        "اضغط ارمي للنرد الذهبي",
        '<div class="aura-stage glow" style="display:flex;align-items:center;justify-content:center;height:360px;perspective:900px">'
        '<div id="cube" class="dice3d"><div class="face f1">1</div><div class="face f2">2</div><div class="face f3">3</div>'
        '<div class="face f4">4</div><div class="face f5">5</div><div class="face f6">6</div></div></div>',
        '<button class="aura-btn" id="roll">ارمِ النرد</button>'
        '<button class="aura-btn ghost" id="again">مرة أخرى</button>',
        r"""
const map={1:[0,0],2:[0,-90],3:[0,90],4:[180,0],5:[-90,0],6:[90,0]};
const cube=document.getElementById('cube');
let busy=false;
function show(n){const [x,y]=map[n];cube.style.transform=`rotateX(${x}deg) rotateY(${y}deg)`;}
document.getElementById('roll').onclick=async()=>{
  if(busy)return;busy=true;AuraGame.Audio.play('dice');
  AuraGame.setMsg(msg,'يرمي...',null);
  let n=1;
  for(let i=0;i<14;i++){n=AuraGame.randInt(1,6);show(n);await new Promise(r=>setTimeout(r,45+i*8));}
  n=AuraGame.randInt(1,6);show(n);
  AuraGame.setMsg(msg,'النتيجة: '+n,'win');
  AuraGame.Audio.play('coin');busy=false;
};
document.getElementById('again').onclick=()=>{show(1);AuraGame.setMsg(msg,'جاهز',null)};
show(1);
""",
        extra_css="""
<style>
.dice3d{width:120px;height:120px;position:relative;transform-style:preserve-3d;transition:transform .55s cubic-bezier(.2,.8,.2,1)}
.face{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:42px;font-weight:900;color:#1a1408;
border-radius:18px;background:linear-gradient(145deg,#ffe7a1,#d79b2d);border:2px solid rgba(255,255,255,.35);box-shadow:inset 0 0 24px rgba(255,255,255,.25)}
.f1{transform:rotateY(0deg) translateZ(60px)}.f2{transform:rotateY(90deg) translateZ(60px)}
.f3{transform:rotateY(-90deg) translateZ(60px)}.f4{transform:rotateY(180deg) translateZ(60px)}
.f5{transform:rotateX(90deg) translateZ(60px)}.f6{transform:rotateX(-90deg) translateZ(60px)}
</style>
""",
    ),
)

# ── 2) XO ───────────────────────────────────────────────────────────────────
write(
    "xo.html",
    page(
        "إكس أو نيون",
        "1 ضد 1 / AI",
        "أنت X · اضغط خانة",
        '<div class="aura-stage" style="padding:16px"><div id="board" class="xo-board"></div></div>',
        '<button class="aura-btn" id="reset">جولة جديدة</button>'
        '<button class="aura-btn secondary" id="mode">تبديل AI/صديق</button>',
        r"""
const boardEl=document.getElementById('board');
let cells=Array(9).fill(null), turn='X', vsAi=true, lock=false;
function render(){
  boardEl.innerHTML='';
  cells.forEach((v,i)=>{
    const d=document.createElement('button');
    d.className='xo-cell'+(v==='X'?' x':v==='O'?' o':'');
    d.textContent=v||'';
    d.onclick=()=>play(i);
    boardEl.appendChild(d);
  });
}
function winner(c){
  const L=[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];
  for(const [a,b,d] of L){ if(c[a]&&c[a]===c[b]&&c[a]===c[d]) return c[a]; }
  return c.every(Boolean)?'draw':null;
}
function aiMove(){
  const empty=cells.map((v,i)=>v?null:i).filter(v=>v!=null);
  // win/block
  for(const mark of ['O','X']){
    for(const i of empty){const t=cells.slice();t[i]=mark;if(winner(t)===mark){return i;}}
  }
  if(empty.includes(4)) return 4;
  return empty[Math.floor(Math.random()*empty.length)];
}
function play(i){
  if(lock||cells[i])return;
  AuraGame.Audio.play('click');
  cells[i]=turn; render();
  const w=winner(cells);
  if(w){ end(w); return; }
  turn=turn==='X'?'O':'X';
  if(vsAi&&turn==='O'){
    lock=true; setTimeout(()=>{
      const m=aiMove(); cells[m]='O'; render();
      const w2=winner(cells); if(w2) end(w2); else {turn='X'; lock=false;}
    },280);
  }
}
function end(w){
  lock=true;
  if(w==='draw'){AuraGame.setMsg(msg,'تعادل','');AuraGame.Audio.play('lose');}
  else if(w==='X'){AuraGame.setMsg(msg,'فوز X ✨','win');AuraGame.Audio.play('win');}
  else {AuraGame.setMsg(msg,'فوز O','lose');AuraGame.Audio.play(vsAi?'lose':'win');}
}
document.getElementById('reset').onclick=()=>{cells=Array(9).fill(null);turn='X';lock=false;AuraGame.setMsg(msg,'جاهز',null);render();AuraGame.Audio.play('chip');};
document.getElementById('mode').onclick=()=>{vsAi=!vsAi; document.getElementById('metaChip').textContent=vsAi?'ضد الذكاء':'صديقان'; document.getElementById('reset').click();};
render();
""",
        extra_css="""
<style>
.xo-board{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;max-width:360px;margin:0 auto}
.xo-cell{aspect-ratio:1;border:0;border-radius:18px;font-size:42px;font-weight:900;cursor:pointer;
background:radial-gradient(circle at 30% 20%,rgba(255,255,255,.12),rgba(0,0,0,.25));
border:1px solid rgba(255,255,255,.14);color:#fff}
.xo-cell.x{color:#4df0d2;text-shadow:0 0 18px rgba(77,240,210,.6)}
.xo-cell.o{color:#ff5ea8;text-shadow:0 0 18px rgba(255,94,168,.6)}
</style>
""",
    ),
)

# ── 3) Memory ───────────────────────────────────────────────────────────────
write(
    "memory.html",
    page(
        "ذاكرة الجواهر",
        "8 أزواج",
        "طابق الجواهر للهدايا",
        '<div class="aura-stage" style="padding:14px"><div class="mem-grid" id="grid"></div></div>',
        '<button class="aura-btn" id="reset">خلط جديد</button>',
        r"""
const icons=['💎','👑','🔮','⭐','🌹','🎵','🔥','🎁'];
let deck=[], first=null, lock=false, matched=0;
function build(){
  deck=[...icons,...icons].sort(()=>Math.random()-0.5);
  matched=0; first=null; lock=false;
  const grid=document.getElementById('grid'); grid.innerHTML='';
  deck.forEach((icon,i)=>{
    const el=document.createElement('button');
    el.className='mem-card'; el.textContent='✦';
    el.onclick=()=>{
      if(lock||el.classList.contains('open')||el.classList.contains('done'))return;
      AuraGame.Audio.play('click'); el.classList.add('open'); el.textContent=icon;
      if(!first){first=el; first._v=icon; return;}
      lock=true;
      if(first._v===icon){
        first.classList.add('done'); el.classList.add('done'); first=null; lock=false; matched++;
        AuraGame.Audio.play('chip');
        if(matched===icons.length){AuraGame.setMsg(msg,'أحسنت! أكملت اللوحة','win');AuraGame.Audio.play('win');}
      } else {
        const a=first,b=el; setTimeout(()=>{a.classList.remove('open');b.classList.remove('open');a.textContent='✦';b.textContent='✦';first=null;lock=false;AuraGame.Audio.play('lose');},520);
      }
    };
    grid.appendChild(el);
  });
  AuraGame.setMsg(msg,'طابق الجواهر',null);
}
document.getElementById('reset').onclick=build; build();
""",
        extra_css="""
<style>
.mem-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}
.mem-card{aspect-ratio:1;border:0;border-radius:16px;font-size:28px;cursor:pointer;color:#ffe7a1;
background:linear-gradient(160deg,rgba(255,255,255,.1),rgba(255,94,168,.12));border:1px solid rgba(255,180,200,.25)}
.mem-card.open,.mem-card.done{background:linear-gradient(145deg,#ff6b9d,#7a1748);color:#fff}
</style>
""",
    ),
)

# ── 4) Roulette ─────────────────────────────────────────────────────────────
write(
    "roulette.html",
    page(
        "روليت ذهبية",
        "0–36",
        "اختر لون ثم لف",
        '<div class="aura-stage" style="display:flex;align-items:center;justify-content:center;height:380px">'
        '<canvas id="c" width="360" height="360"></canvas><div class="ball" id="ball"></div></div>',
        '<button class="aura-btn ghost" data-bet="red">أحمر</button>'
        '<button class="aura-btn ghost" data-bet="black">أسود</button>'
        '<button class="aura-btn ghost" data-bet="green">صفر</button>'
        '<button class="aura-btn" id="spin">لف العجلة</button>',
        r"""
const nums=[0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26];
const red=new Set([1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36]);
const canvas=document.getElementById('c'), ctx=canvas.getContext('2d');
let ang=0, bet=null, spinning=false;
function colorOf(n){ if(n===0) return '#1f8a4c'; return red.has(n)?'#c62828':'#111'; }
function draw(){
  const cx=180,cy=180,r=168;
  ctx.clearRect(0,0,360,360);
  const slice=(Math.PI*2)/nums.length;
  for(let i=0;i<nums.length;i++){
    const a0=ang+i*slice, a1=a0+slice;
    ctx.beginPath(); ctx.moveTo(cx,cy); ctx.arc(cx,cy,r,a0,a1); ctx.closePath();
    ctx.fillStyle=colorOf(nums[i]); ctx.fill();
    ctx.save(); ctx.translate(cx,cy); ctx.rotate(a0+slice/2); ctx.fillStyle='#fff'; ctx.font='bold 11px Tahoma';
    ctx.fillText(String(nums[i]), r-28, 4); ctx.restore();
  }
  ctx.beginPath(); ctx.arc(cx,cy,42,0,Math.PI*2); ctx.fillStyle='#ffd76a'; ctx.fill();
  ctx.fillStyle='#1a1408'; ctx.font='bold 14px Tahoma'; ctx.textAlign='center'; ctx.fillText('JEHO',cx,cy+5);
  // pointer
  ctx.beginPath(); ctx.moveTo(cx,10); ctx.lineTo(cx-12,36); ctx.lineTo(cx+12,36); ctx.closePath();
  ctx.fillStyle='#ffe7a1'; ctx.fill();
}
draw();
document.querySelectorAll('[data-bet]').forEach(b=>b.onclick=()=>{
  bet=b.dataset.bet; AuraGame.Audio.play('chip');
  AuraGame.setMsg(msg,'اختيارك: '+(bet==='red'?'أحمر':bet==='black'?'أسود':'صفر'),null);
});
document.getElementById('spin').onclick=async()=>{
  if(spinning)return; if(!bet){AuraGame.setMsg(msg,'اختر لون أولاً','lose');return;}
  spinning=true; AuraGame.Audio.play('spin');
  const target=AuraGame.randInt(0,nums.length-1);
  const slice=(Math.PI*2)/nums.length;
  const goal= (Math.PI*2*6) + (Math.PI*1.5 - (target+0.5)*slice);
  const start=ang; const t0=performance.now();
  function frame(t){
    const p=Math.min(1,(t-t0)/4200); const e=1-Math.pow(1-p,3);
    ang=start + (goal-start)*e; draw();
    if(p<1) requestAnimationFrame(frame); else {
      const n=nums[target]; const col=n===0?'green':(red.has(n)?'red':'black');
      const win=col===bet; AuraGame.setMsg(msg, 'الرقم '+n+(win?' · ربحت!':' · حظاً أوفر'), win?'win':'lose');
      AuraGame.Audio.play(win?'win':'lose'); spinning=false;
    }
  }
  requestAnimationFrame(frame);
};
""",
        extra_css="""
<style>.aura-stage{position:relative}.ball{display:none}</style>
""",
    ),
)

# ── 5) Slots ────────────────────────────────────────────────────────────────
write(
    "slots.html",
    page(
        "سلوتس الجواهر",
        "3 بكرات",
        "أدر البكرات واربح",
        '<div class="aura-stage" style="padding:18px"><div class="reels" id="reels">'
        '<div class="reel" id="r0">7️⃣</div><div class="reel" id="r1">💎</div><div class="reel" id="r2">🍒</div></div></div>',
        '<button class="aura-btn" id="spin">دور</button>',
        r"""
const sym=['7️⃣','💎','🍒','⭐','🍋','🔔'];
let busy=false;
async function spinOne(el){
  for(let i=0;i<12;i++){ el.textContent=sym[AuraGame.randInt(0,sym.length-1)]; await new Promise(r=>setTimeout(r,40+i*6)); }
  const v=sym[AuraGame.randInt(0,sym.length-1)]; el.textContent=v; return v;
}
document.getElementById('spin').onclick=async()=>{
  if(busy)return; busy=true; AuraGame.Audio.play('spin');
  const vals=await Promise.all([spinOne(r0),spinOne(r1),spinOne(r2)]);
  const win=vals[0]===vals[1]&&vals[1]===vals[2];
  const pair=vals[0]===vals[1]||vals[1]===vals[2]||vals[0]===vals[2];
  if(win){AuraGame.setMsg(msg,'جاكبوت! 🎉','win');AuraGame.Audio.play('win');}
  else if(pair){AuraGame.setMsg(msg,'زوج قريب','win');AuraGame.Audio.play('coin');}
  else {AuraGame.setMsg(msg,'حاول مجدداً','lose');AuraGame.Audio.play('lose');}
  busy=false;
};
""",
        extra_css="""
<style>
.reels{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;max-width:360px;margin:40px auto}
.reel{aspect-ratio:1;border-radius:22px;display:flex;align-items:center;justify-content:center;font-size:54px;
background:linear-gradient(180deg,#2a1840,#12081c);border:2px solid rgba(255,215,106,.45);box-shadow:inset 0 0 30px rgba(255,94,168,.15)}
</style>
""",
    ),
)

# ── 6) Plinko ───────────────────────────────────────────────────────────────
write(
    "plinko.html",
    page(
        "بلينكو نيون",
        "أسقط الشريحة",
        "أسقط الشريحة الذهبية",
        '<div class="aura-stage"><canvas id="c" width="360" height="480"></canvas></div>',
        '<button class="aura-btn" id="drop">أسقط</button>',
        r"""
const canvas=document.getElementById('c'), ctx=canvas.getContext('2d');
const W=360,H=480; const rows=8; const pegs=[];
for(let r=0;r<rows;r++){
  const count=r+3; const y=70+r*40;
  for(let i=0;i<count;i++){ const x=W/2 - (count-1)*18 + i*36; pegs.push({x,y,r:5}); }
}
const bins=[1,2,5,10,5,2,1];
let ball=null, busy=false;
function draw(){
  ctx.clearRect(0,0,W,H);
  const g=ctx.createLinearGradient(0,0,0,H); g.addColorStop(0,'#12203f'); g.addColorStop(1,'#070b18');
  ctx.fillStyle=g; ctx.fillRect(0,0,W,H);
  pegs.forEach(p=>{ctx.beginPath();ctx.arc(p.x,p.y,p.r,0,Math.PI*2);ctx.fillStyle='#4df0d2';ctx.fill();});
  const bw=W/bins.length;
  bins.forEach((v,i)=>{ctx.fillStyle=i%2?'rgba(255,215,106,.18)':'rgba(255,94,168,.16)';ctx.fillRect(i*bw,H-48,bw,48);
    ctx.fillStyle='#ffe7a1';ctx.font='bold 14px Tahoma';ctx.textAlign='center';ctx.fillText('x'+v,i*bw+bw/2,H-20);});
  if(ball){ctx.beginPath();ctx.arc(ball.x,ball.y,9,0,Math.PI*2);ctx.fillStyle='#ffd76a';ctx.fill();}
}
function step(){
  if(!ball) return;
  ball.vy+=0.28; ball.x+=ball.vx; ball.y+=ball.vy;
  for(const p of pegs){
    const dx=ball.x-p.x, dy=ball.y-p.y, d=Math.hypot(dx,dy);
    if(d<p.r+9){ const nx=dx/d, ny=dy/d; ball.vx=nx*2.2+(Math.random()-.5); ball.vy=Math.abs(ny)*2.1+0.4; ball.x=p.x+nx*(p.r+9); ball.y=p.y+ny*(p.r+9); AuraGame.Audio.play('chip',{volume:.25}); }
  }
  if(ball.x<12){ball.x=12;ball.vx=Math.abs(ball.vx);} if(ball.x>W-12){ball.x=W-12;ball.vx=-Math.abs(ball.vx);}
  if(ball.y>=H-56){
    const idx=Math.min(bins.length-1, Math.max(0, Math.floor(ball.x/(W/bins.length))));
    AuraGame.setMsg(msg,'مضاعف x'+bins[idx]+'!','win'); AuraGame.Audio.play('win'); ball=null; busy=false; draw(); return;
  }
  draw(); requestAnimationFrame(step);
}
document.getElementById('drop').onclick=()=>{
  if(busy)return; busy=true; AuraGame.Audio.play('click');
  ball={x:W/2+(Math.random()*20-10), y:24, vx:(Math.random()-.5)*1.2, vy:0};
  AuraGame.setMsg(msg,'...','');
  requestAnimationFrame(step);
};
draw();
""",
    ),
)

# ── 7) Ludo polish wrapper (keep playable board) — rewrite compact quality ──
write(
    "ludo.html",
    page(
        "لودو رويال",
        "ضد الكمبيوتر",
        "ارمِ النرد وحرّك قطعك",
        '<div class="aura-stage" style="padding:10px"><canvas id="c" width="420" height="420"></canvas></div>',
        '<div class="die" id="die">-</div><button class="aura-btn" id="roll">ارمِ</button><button class="aura-btn ghost" id="new">جديد</button>',
        r"""
const canvas=document.getElementById('c'), ctx=canvas.getContext('2d');
const COLORS=['#e74c3c','#3498db','#2ecc71','#f1c40f'];
let tokens=[[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]];
let turn=0, dice=0, canRoll=true, winner=null;
const PATH=52, FINISH=57;
function homeXY(p,i){const bases=[[2.2,2.2],[11.8,2.2],[11.8,11.8],[2.2,11.8]]; const b=bases[p]; const ox=(i%2)*1.3, oy=(i>>1)*1.3; return {x:b[0]+ox,y:b[1]+oy};}
function trackXY(p,prog){
  if(prog<=0) return homeXY(p,0);
  if(prog>PATH){ const d=prog-PATH; const home=[[7,1.2],[12.8,7],[7,12.8],[1.2,7]]; const h=home[p];
    if(p===0)return{x:h[0],y:h[1]+d}; if(p===1)return{x:h[0]-d,y:h[1]}; if(p===2)return{x:h[0],y:h[1]-d}; return{x:h[0]+d,y:h[1]}; }
  const ring=[]; for(let x=6;x<=8;x++)ring.push([x,0]); for(let y=1;y<=5;y++)ring.push([8,y]);
  for(let x=9;x<=14;x++)ring.push([x,6]); for(let y=6;y<=8;y++)ring.push([14,y]); for(let x=13;x>=9;x--)ring.push([x,8]);
  for(let y=9;y<=14;y++)ring.push([8,y]); for(let x=8;x>=6;x--)ring.push([x,14]); for(let y=13;y>=9;y--)ring.push([6,y]);
  for(let x=5;x>=0;x--)ring.push([x,8]); for(let y=8;y>=6;y--)ring.push([0,y]); for(let x=1;x<=5;x++)ring.push([x,6]);
  for(let y=5;y>=1;y--)ring.push([6,y]);
  const start=[0,13,26,39][p]; const idx=(start+prog-1)%PATH; return {x:ring[idx][0]+.5,y:ring[idx][1]+.5};
}
function draw(){
  const S=420/15; ctx.clearRect(0,0,420,420);
  ctx.fillStyle='#0b221f'; ctx.fillRect(0,0,420,420);
  for(let y=0;y<15;y++) for(let x=0;x<15;x++){ ctx.strokeStyle='rgba(255,255,255,.05)'; ctx.strokeRect(x*S,y*S,S,S); }
  [[0,0,'#e74c3c'],[9,0,'#3498db'],[9,9,'#2ecc71'],[0,9,'#f1c40f']].forEach(([x,y,c])=>{ctx.fillStyle=c+'33';ctx.fillRect(x*S,y*S,6*S,6*S);ctx.strokeStyle=c;ctx.lineWidth=3;ctx.strokeRect(x*S+4,y*S+4,6*S-8,6*S-8);});
  ctx.fillStyle='#c9a227'; ctx.fillRect(6*S,6*S,3*S,3*S);
  for(let p=0;p<4;p++) for(let i=0;i<4;i++){ const pos=tokens[p][i]; const xy=pos<=0?homeXY(p,i):trackXY(p,pos);
    ctx.beginPath(); ctx.arc(xy.x*S,xy.y*S,S*0.38,0,Math.PI*2); ctx.fillStyle=COLORS[p]; ctx.fill(); ctx.strokeStyle='#fff'; ctx.stroke(); }
}
function legal(p,i,d){ const cur=tokens[p][i]; if(cur<=0) return d===6?1:null; const n=cur+d; return n<=FINISH?n:null; }
function anyMove(p,d){ for(let i=0;i<4;i++) if(legal(p,i,d)!=null) return true; return false; }
function aiPlay(){
  if(winner!=null)return;
  dice=AuraGame.randInt(1,6); die.textContent=dice; AuraGame.Audio.play('dice');
  if(!anyMove(turn,dice)){ turn=(turn+1)%4; canRoll=true; if(turn!==0) setTimeout(aiPlay,500); else AuraGame.setMsg(msg,'دورك',null); draw(); return; }
  for(let i=0;i<4;i++){ const n=legal(turn,i,dice); if(n!=null){ tokens[turn][i]=n; break; } }
  if(tokens[turn].every(v=>v>=FINISH)){ winner=turn; AuraGame.setMsg(msg,'فاز اللاعب '+(turn+1), turn===0?'win':'lose'); AuraGame.Audio.play(turn===0?'win':'lose'); draw(); return; }
  if(dice!==6) turn=(turn+1)%4;
  canRoll=true; draw();
  if(turn!==0) setTimeout(aiPlay,550); else AuraGame.setMsg(msg,'دورك',null);
}
canvas.onclick=(ev)=>{
  if(turn!==0||canRoll||winner!=null)return;
  const S=420/15; const rect=canvas.getBoundingClientRect(); const x=(ev.clientX-rect.left)*(420/rect.width)/S; const y=(ev.clientY-rect.top)*(420/rect.height)/S;
  for(let i=0;i<4;i++){ const cur=tokens[0][i]; const xy=cur<=0?homeXY(0,i):trackXY(0,cur); if(Math.hypot(xy.x-x,xy.y-y)<0.7){ const n=legal(0,i,dice); if(n==null)return; tokens[0][i]=n; AuraGame.Audio.play('chip');
    if(tokens[0].every(v=>v>=FINISH)){winner=0;AuraGame.setMsg(msg,'فزت!','win');AuraGame.Audio.play('win');draw();return;}
    if(dice!==6) turn=1; canRoll=true; draw(); if(turn!==0) setTimeout(aiPlay,500); return; } }
};
document.getElementById('roll').onclick=()=>{
  if(turn!==0||!canRoll||winner!=null)return; dice=AuraGame.randInt(1,6); die.textContent=dice; canRoll=false; AuraGame.Audio.play('dice');
  if(!anyMove(0,dice)){ AuraGame.setMsg(msg,'لا حركة',''); setTimeout(()=>{turn=1;canRoll=true;aiPlay();},400); return; }
  AuraGame.setMsg(msg,'اختر قطعة',null);
};
document.getElementById('new').onclick=()=>{tokens=[[0,0,0,0],[0,0,0,0],[0,0,0,0],[0,0,0,0]];turn=0;dice=0;canRoll=true;winner=null;die.textContent='-';AuraGame.setMsg(msg,'جولة جديدة',null);draw();};
draw();
""",
        extra_css="""
<style>.die{width:54px;height:54px;border-radius:14px;background:linear-gradient(180deg,#fff,#ddd);color:#111;display:flex;align-items:center;justify-content:center;font-size:26px;font-weight:900;border:2px solid #c9a227}</style>
""",
    ),
)

# ── 8) Ono (Uno-like) ───────────────────────────────────────────────────────
write(
    "ono.html",
    page(
        "أونو نيون",
        "ضد الكمبيوتر",
        "طابق اللون أو الرقم",
        '<div class="aura-stage" style="padding:14px">'
        '<div class="pile" id="pile"></div><div class="hand" id="hand"></div></div>',
        '<button class="aura-btn secondary" id="draw">اسحب</button><button class="aura-btn ghost" id="new">جديد</button>',
        r"""
const COLORS=['#e74c3c','#2ecc71','#3498db','#f1c40f'];
function card(){return {c:COLORS[AuraGame.randInt(0,3)], n:AuraGame.randInt(0,9)};}
let top=card(), hand=[], ai=[], turn='you';
function paintCard(el,c){ el.style.background=c.c; el.textContent=c.n; }
function canPlay(c){ return c.c===top.c || c.n===top.n; }
function render(){
  paintCard(pile, top);
  handEl.innerHTML='';
  hand.forEach((c,i)=>{ const b=document.createElement('button'); b.className='card'; paintCard(b,c);
    b.onclick=()=>{ if(turn!=='you'||!canPlay(c)) return; AuraGame.Audio.play('chip'); top=c; hand.splice(i,1); render();
      if(!hand.length){AuraGame.setMsg(msg,'فزت!','win');AuraGame.Audio.play('win');turn='end';return;} turn='ai'; setTimeout(aiTurn,450); };
    handEl.appendChild(b); });
  document.getElementById('metaChip').textContent='يدك '+hand.length+' · AI '+ai.length;
}
function aiTurn(){
  if(turn!=='ai')return;
  const idx=ai.findIndex(canPlay);
  if(idx>=0){ top=ai.splice(idx,1)[0]; AuraGame.Audio.play('click'); }
  else { ai.push(card()); }
  render();
  if(!ai.length){AuraGame.setMsg(msg,'فاز الكمبيوتر','lose');AuraGame.Audio.play('lose');turn='end';return;}
  turn='you'; AuraGame.setMsg(msg,'دورك',null);
}
const pile=document.getElementById('pile'), handEl=document.getElementById('hand');
document.getElementById('draw').onclick=()=>{ if(turn!=='you')return; hand.push(card()); AuraGame.Audio.play('click'); render(); turn='ai'; setTimeout(aiTurn,400); };
document.getElementById('new').onclick=()=>{ hand=[card(),card(),card(),card(),card()]; ai=[card(),card(),card(),card(),card()]; top=card(); turn='you'; AuraGame.setMsg(msg,'طابق اللون أو الرقم',null); render(); };
document.getElementById('new').click();
""",
        extra_css="""
<style>
.pile{width:86px;height:120px;border-radius:14px;margin:10px auto 18px;display:flex;align-items:center;justify-content:center;font-size:36px;font-weight:900;color:#fff;border:2px solid rgba(255,255,255,.3)}
.hand{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
.card{width:64px;height:92px;border:0;border-radius:12px;color:#fff;font-size:24px;font-weight:900;cursor:pointer;border:2px solid rgba(255,255,255,.25)}
</style>
""",
    ),
)

# ── 9) Domino ───────────────────────────────────────────────────────────────
write(
    "domino.html",
    page(
        "دومينو كلاسيك",
        "ضد الكمبيوتر",
        "صل الطرف المطابق",
        '<div class="aura-stage" style="padding:14px"><div class="chain" id="chain"></div><div class="hand" id="hand"></div></div>',
        '<button class="aura-btn secondary" id="draw">اسحب</button><button class="aura-btn ghost" id="new">جديد</button>',
        r"""
function tile(){return [AuraGame.randInt(0,6),AuraGame.randInt(0,6)];}
let left=0,right=0, chain=[], hand=[], ai=[], turn='you';
function render(){
  chainEl.innerHTML=chain.map(t=>`<span class="tile">${t[0]}|${t[1]}</span>`).join('');
  handEl.innerHTML='';
  hand.forEach((t,i)=>{
    const b=document.createElement('button'); b.className='tile btn'; b.textContent=t[0]+'|'+t[1];
    b.onclick=()=>play(i); handEl.appendChild(b);
  });
  document.getElementById('metaChip').textContent='يدك '+hand.length+' · AI '+ai.length;
}
function place(t, side){
  if(side==='L'){ if(t[1]===left){ chain.unshift(t); left=t[0]; return true;} if(t[0]===left){ chain.unshift([t[1],t[0]]); left=t[1]; return true;} }
  else { if(t[0]===right){ chain.push(t); right=t[1]; return true;} if(t[1]===right){ chain.push([t[1],t[0]]); right=t[0]; return true;} }
  return false;
}
function play(i){
  if(turn!=='you')return; const t=hand[i];
  if(place(t,'R')||place(t,'L')){ hand.splice(i,1); AuraGame.Audio.play('chip'); render();
    if(!hand.length){AuraGame.setMsg(msg,'فزت!','win');AuraGame.Audio.play('win');turn='end';return;} turn='ai'; setTimeout(aiTurn,450);}
  else AuraGame.setMsg(msg,'لا تطابق','lose');
}
function aiTurn(){
  for(let i=0;i<ai.length;i++){ const t=ai[i]; if(place(t,'R')||place(t,'L')){ ai.splice(i,1); AuraGame.Audio.play('click'); render();
    if(!ai.length){AuraGame.setMsg(msg,'فاز الكمبيوتر','lose');AuraGame.Audio.play('lose');turn='end';return;} turn='you'; AuraGame.setMsg(msg,'دورك',null); return; } }
  ai.push(tile()); turn='you'; render(); AuraGame.setMsg(msg,'AI سحب · دورك',null);
}
const chainEl=document.getElementById('chain'), handEl=document.getElementById('hand');
document.getElementById('draw').onclick=()=>{ if(turn!=='you')return; hand.push(tile()); AuraGame.Audio.play('click'); render(); turn='ai'; setTimeout(aiTurn,350); };
document.getElementById('new').onclick=()=>{
  const start=tile(); chain=[start]; left=start[0]; right=start[1];
  hand=[tile(),tile(),tile(),tile(),tile()]; ai=[tile(),tile(),tile(),tile(),tile()]; turn='you'; AuraGame.setMsg(msg,'صل الطرف المطابق',null); render();
};
document.getElementById('new').click();
""",
        extra_css="""
<style>
.chain{min-height:70px;display:flex;flex-wrap:wrap;gap:6px;justify-content:center;margin-bottom:16px}
.hand{display:flex;flex-wrap:wrap;gap:8px;justify-content:center}
.tile{display:inline-flex;align-items:center;justify-content:center;min-width:58px;height:40px;padding:0 8px;border-radius:10px;
background:linear-gradient(180deg,#f7f3ea,#d9d0c0);color:#1a1408;font-weight:900;border:1px solid #b9a88a}
.tile.btn{cursor:pointer;border:0}
</style>
""",
    ),
)

# Keep lucky-wheel as-is (already large). Add a thin note file? Skip overwrite.

print("pack done")
