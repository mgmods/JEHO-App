# -*- coding: utf-8 -*-
"""Generate 20 additional distinct HTML5 games for JEHO CHAT."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "public" / "games"
ROOT.mkdir(parents=True, exist_ok=True)

COIN_SHELL = """<!DOCTYPE html>
<html lang="ar" dir="rtl"><head>
<meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<title>{title}</title>
<link rel="stylesheet" href="./common/aura-kit.css"/>
{extra}
</head><body><div class="aura-shell">
<div class="aura-top"><div class="aura-title">{title}</div>
<div class="aura-chip">🪙 <span id="bal">…</span></div></div>
<div class="aura-badge-row"><span class="aura-badge" id="stampBadge"></span><span class="aura-badge" id="emojiBadge"></span></div>
{body}
<div class="aura-msg" id="msg">{hint}</div>
<div class="aura-panel chips" id="chips"></div>
<div class="aura-panel">{panel}</div>
</div>
<script src="./common/aura-kit.js"></script>
<script>
let balance=0, bet=100, busy=false;
const chipsEl=document.getElementById('chips');
[50,100,500,1000,5000].forEach(v=>{{
  const b=document.createElement('button'); b.className='aura-btn ghost'; b.textContent=v;
  b.onclick=()=>{{bet=v;AuraGame.Audio.play('chip');[...chipsEl.children].forEach(x=>x.classList.remove('glow'));b.classList.add('glow');}};
  chipsEl.appendChild(b);
}});
async function refresh(){{ try{{ const s=await AuraGame.api('/games/casual'); balance=Number(s.balance||0); bal.textContent=balance.toLocaleString('en-US'); }}catch(e){{ bal.textContent='—'; }} }}
async function play(choice){{
  if(busy)return; busy=true;
  try{{
    AuraGame.Audio.play('spin');
    const r=await AuraGame.api('/games/casual/play',{{method:'POST',body:JSON.stringify({{gameId:'{gid}',amount:bet,roomId:AuraGame.roomId()||undefined,choice}})}});
    balance=Number(r.balance||0); bal.textContent=balance.toLocaleString('en-US');
    if(window.onCasualResult) window.onCasualResult(r);
    AuraGame.setMsg(msg, r.won?('ربحت '+r.payout+' كوينز'):'حظاً أوفر', r.won?'win':'lose');
    AuraGame.Audio.play(r.won?'win':'lose');
  }}catch(e){{ AuraGame.setMsg(msg, e.message||'خطأ','lose'); AuraGame.Audio.play('lose'); }}
  finally{{ busy=false; }}
}}
refresh();
{js}
</script></body></html>
"""

LOCAL_SHELL = """<!DOCTYPE html>
<html lang="ar" dir="rtl"><head>
<meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"/>
<title>{title}</title>
<link rel="stylesheet" href="./common/aura-kit.css"/>
{extra}
</head><body><div class="aura-shell">
<div class="aura-top"><div class="aura-title">{title}</div><div class="aura-chip" id="scoreChip">نقاط 0</div></div>
<div class="aura-badge-row"><span class="aura-badge" id="stampBadge"></span><span class="aura-badge" id="emojiBadge"></span></div>
{body}
<div class="aura-msg" id="msg">{hint}</div>
<div class="aura-panel">{panel}</div>
</div>
<script src="./common/aura-kit.js"></script>
<script>
{js}
</script></body></html>
"""


def write(name, html):
    (ROOT / name).write_text(html, encoding="utf-8")
    print("wrote", name, len(html))


# 1 Witch
write("witch.html", COIN_SHELL.format(
    title="الساحرة", gid="witch", hint="اسأل الساحرة وارهن كوينز",
    body='<div class="aura-stage" style="padding:18px;text-align:center"><div style="font-size:72px">🧙‍♀️</div><div id="oracle" style="min-height:80px;font-size:18px;margin-top:12px;color:#e9d5ff">الكرة البلورية جاهزة…</div></div>',
    panel='<button class="aura-btn" id="go">استشر الساحرة</button>',
    extra="",
    js="""
const lines=['نعم قريباً','احذر الطريق','كنز بانتظارك','الحظ معك الليلة','انتظر إشارة','قوة خفية تعمل','ابتسم للقدر'];
document.getElementById('go').onclick=()=>{AuraGame.Audio.play('magic');play(null)};
window.onCasualResult=function(r){const t=(r.detail&&r.detail.prophecy)||lines[Math.floor(Math.random()*lines.length)]; oracle.textContent=t;};
""",
))

# 2 Snake
write("snake.html", LOCAL_SHELL.format(
    title="الثعبان النيون", hint="حرّك بالأسهم أو الأزرار",
    body='<div class="aura-stage"><canvas id="c" width="360" height="360"></canvas></div>',
    panel='<button class="aura-btn ghost" data-d="U">↑</button><button class="aura-btn ghost" data-d="L">←</button><button class="aura-btn ghost" data-d="D">↓</button><button class="aura-btn ghost" data-d="R">→</button><button class="aura-btn" id="restart">جديد</button>',
    extra="",
    js="""
const canvas=c.getContext('2d'); let dir={x:1,y:0}, snake=[{x:8,y:8}], food={x:12,y:8}, score=0, alive=true;
function place(){food={x:AuraGame.randInt(0,17),y:AuraGame.randInt(0,17)};}
function draw(){canvas.fillStyle='#071018';canvas.fillRect(0,0,360,360); const s=20;
 snake.forEach((p,i)=>{canvas.fillStyle=i? '#4df0d2':'#ffd76a';canvas.fillRect(p.x*s,p.y*s,s-2,s-2);});
 canvas.fillStyle='#ff5ea8';canvas.fillRect(food.x*s,food.y*s,s-2,s-2);}
function step(){ if(!alive)return; const h={x:snake[0].x+dir.x,y:snake[0].y+dir.y};
 if(h.x<0||h.y<0||h.x>17||h.y>17||snake.some(p=>p.x===h.x&&p.y===h.y)){alive=false;AuraGame.setMsg(msg,'انتهت!', 'lose');AuraGame.Audio.play('lose');return;}
 snake.unshift(h); if(h.x===food.x&&h.y===food.y){score++;scoreChip.textContent='نقاط '+score;AuraGame.Audio.play('pop');place();} else snake.pop(); draw();}
document.querySelectorAll('[data-d]').forEach(b=>b.onclick=()=>{const d=b.dataset.d; if(d==='U'&&dir.y!==1)dir={x:0,y:-1}; if(d==='D'&&dir.y!==-1)dir={x:0,y:1}; if(d==='L'&&dir.x!==1)dir={x:-1,y:0}; if(d==='R'&&dir.x!==-1)dir={x:1,y:0}; AuraGame.Audio.play('click');});
restart.onclick=()=>{snake=[{x:8,y:8}];dir={x:1,y:0};score=0;alive=true;scoreChip.textContent='نقاط 0';place();draw();AuraGame.setMsg(msg,'انطلق',null)};
place();draw(); setInterval(step,140);
""",
))

# 3 2048
write("g2048.html", LOCAL_SHELL.format(
    title="2048 رويال", hint="اسحب أو استخدم الأزرار",
    body='<div class="aura-stage" style="padding:12px"><div id="grid" class="g2048"></div></div>',
    panel='<button class="aura-btn ghost" data-m="U">↑</button><button class="aura-btn ghost" data-m="L">←</button><button class="aura-btn ghost" data-m="D">↓</button><button class="aura-btn ghost" data-m="R">→</button><button class="aura-btn" id="restart">جديد</button>',
    extra="<style>.g2048{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}.cell{aspect-ratio:1;border-radius:12px;display:flex;align-items:center;justify-content:center;font-weight:900;font-size:22px;background:rgba(255,255,255,.08)}</style>",
    js="""
let board=Array(16).fill(0), score=0;
function render(){grid.innerHTML='';board.forEach(v=>{const d=document.createElement('div');d.className='cell';d.textContent=v||'';d.style.background=v?('hsla('+(Math.log2(v)*28)+',70%,45%,.85)'):'rgba(255,255,255,.08)';grid.appendChild(d);}); scoreChip.textContent='نقاط '+score;}
function spawn(){const empty=board.map((v,i)=>v?null:i).filter(v=>v!=null); if(!empty.length)return; board[empty[Math.floor(Math.random()*empty.length)]]=Math.random()<0.9?2:4;}
function slide(line){const a=line.filter(Boolean); for(let i=0;i<a.length-1;i++){if(a[i]===a[i+1]){a[i]*=2;score+=a[i];a[i+1]=0;AuraGame.Audio.play('coin');}} return a.filter(Boolean).concat(Array(4).fill(0)).slice(0,4);}
function move(dir){const old=board.slice(); const get=(r,c)=>board[r*4+c]; const set=(r,c,v)=>board[r*4+c]=v;
 for(let i=0;i<4;i++){let line=[]; if(dir==='L'||dir==='R'){for(let c=0;c<4;c++)line.push(get(i,c)); if(dir==='R')line.reverse(); line=slide(line); if(dir==='R')line.reverse(); line.forEach((v,c)=>set(i,c,v));}
 else {for(let r=0;r<4;r++)line.push(get(r,i)); if(dir==='D')line.reverse(); line=slide(line); if(dir==='D')line.reverse(); line.forEach((v,r)=>set(r,i,v));}}
 if(board.some((v,i)=>v!==old[i])){spawn();render();}
}
document.querySelectorAll('[data-m]').forEach(b=>b.onclick=()=>move(b.dataset.m));
restart.onclick=()=>{board=Array(16).fill(0);score=0;spawn();spawn();render();};
restart.click();
""",
))

# 4 Flappy
write("flappy.html", LOCAL_SHELL.format(
    title="طيران النيون", hint="اضغط للطيران",
    body='<div class="aura-stage"><canvas id="c" width="360" height="480"></canvas></div>',
    panel='<button class="aura-btn" id="flap">اطير</button><button class="aura-btn ghost" id="restart">جديد</button>',
    extra="",
    js="""
const ctx=c.getContext('2d'); let y=200,vy=0,pipes=[],score=0,alive=true,t=0;
function reset(){y=200;vy=0;pipes=[{x:360,gap:180}];score=0;alive=true;scoreChip.textContent='نقاط 0';AuraGame.setMsg(msg,'اضغط للطيران',null);}
function frame(){ t++; ctx.fillStyle='#071428';ctx.fillRect(0,0,360,480);
 if(alive){vy+=0.35;y+=vy; if(t%90===0)pipes.push({x:360,gap:AuraGame.randInt(120,300)});
 pipes.forEach(p=>{p.x-=2.4; ctx.fillStyle='#4df0d2';ctx.fillRect(p.x,0,46,p.gap-70);ctx.fillRect(p.x,p.gap+70,46,480);
 if(p.x===40){score++;scoreChip.textContent='نقاط '+score;AuraGame.Audio.play('coin');}
 if(p.x<80&&p.x>30&&(y<p.gap-70||y>p.gap+70)){alive=false;AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'سقطت!','lose');}});
 if(y>460||y<0){alive=false;AuraGame.Audio.play('lose');}}
 ctx.beginPath();ctx.arc(70,y,14,0,Math.PI*2);ctx.fillStyle='#ffd76a';ctx.fill();
 requestAnimationFrame(frame);}
flap.onclick=()=>{if(!alive)return;vy=-6.2;AuraGame.Audio.play('whoosh');}; restart.onclick=reset; reset(); frame();
""",
))

# 5 Basket
write("basket.html", LOCAL_SHELL.format(
    title="رمية السلة", hint="اضغط للتسديد",
    body='<div class="aura-stage"><canvas id="c" width="360" height="420"></canvas></div>',
    panel='<button class="aura-btn" id="shoot">سدد</button>',
    extra="",
    js="""
const ctx=c.getContext('2d'); let ball={x:180,y:360,vx:0,vy:0,flying:false}, score=0;
function draw(){ctx.clearRect(0,0,360,420);ctx.fillStyle='#102018';ctx.fillRect(0,0,360,420);
 ctx.strokeStyle='#ffd76a';ctx.lineWidth=4;ctx.beginPath();ctx.arc(180,70,36,0,Math.PI*2);ctx.stroke();
 ctx.fillStyle='#ff7a1a';ctx.beginPath();ctx.arc(ball.x,ball.y,14,0,Math.PI*2);ctx.fill();}
function loop(){ if(ball.flying){ball.vy+=0.28;ball.x+=ball.vx;ball.y+=ball.vy;
  if(Math.hypot(ball.x-180,ball.y-70)<34 && ball.vy>0){score++;scoreChip.textContent='نقاط '+score;AuraGame.Audio.play('win');AuraGame.setMsg(msg,'سلة!','win');ball.flying=false;ball={x:180,y:360,vx:0,vy:0,flying:false};}
  if(ball.y>430||ball.x<0||ball.x>360){ball.flying=false;ball={x:180,y:360,vx:0,vy:0,flying:false};AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'خطأ','lose');}}
 draw(); requestAnimationFrame(loop);}
shoot.onclick=()=>{if(ball.flying)return;ball.flying=true;ball.vx=(Math.random()*2-1)*1.2;ball.vy=-10-Math.random()*2;AuraGame.Audio.play('whoosh');};
loop();
""",
))

# 6 Archery
write("archery.html", LOCAL_SHELL.format(
    title="رماية السهام", hint="أطلق نحو الهدف المتحرك",
    body='<div class="aura-stage"><canvas id="c" width="360" height="420"></canvas></div>',
    panel='<button class="aura-btn" id="fire">أطلق</button>',
    extra="",
    js="""
const ctx=c.getContext('2d'); let tx=40,td=2,arrow=null,score=0;
function draw(){ctx.fillStyle='#12100a';ctx.fillRect(0,0,360,420); ctx.fillStyle='#c0392b';ctx.beginPath();ctx.arc(tx,80,28,0,Math.PI*2);ctx.fill();ctx.fillStyle='#fff';ctx.beginPath();ctx.arc(tx,80,12,0,Math.PI*2);ctx.fill();
 ctx.fillStyle='#ffd76a';ctx.fillRect(170,340,20,40); if(arrow){ctx.fillStyle='#4df0d2';ctx.fillRect(arrow.x-2,arrow.y,4,28);} }
function loop(){tx+=td; if(tx<40||tx>320)td*=-1; if(arrow){arrow.y-=10; if(arrow.y<100){ const hit=Math.abs(arrow.x-tx)<30; if(hit){score++;scoreChip.textContent='نقاط '+score;AuraGame.Audio.play('hit');AuraGame.setMsg(msg,'إصابة!','win');} else {AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'أخطأت','lose');} arrow=null; }}
 draw(); requestAnimationFrame(loop);}
fire.onclick=()=>{if(arrow)return;arrow={x:180,y:320};AuraGame.Audio.play('whoosh');}; loop();
""",
))

# 7 Mines
write("mines.html", LOCAL_SHELL.format(
    title="الألغام", hint="افتح الخانات بدون لغم",
    body='<div class="aura-stage" style="padding:10px"><div id="grid" class="mines"></div></div>',
    panel='<button class="aura-btn" id="restart">جديد</button>',
    extra="<style>.mines{display:grid;grid-template-columns:repeat(6,1fr);gap:6px}.m{aspect-ratio:1;border:0;border-radius:10px;background:rgba(255,255,255,.1);color:#fff;font-weight:800}</style>",
    js="""
const N=6, BOMBS=7; let cells=[], open=0, dead=false;
function build(){dead=false;open=0;cells=Array(N*N).fill(0); let b=0; while(b<BOMBS){const i=AuraGame.randInt(0,N*N-1); if(!cells[i]){cells[i]=9;b++;}}
 for(let i=0;i<N*N;i++){ if(cells[i]===9)continue; let n=0; const x=i%N,y=(i/N)|0; for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){const nx=x+dx,ny=y+dy; if(nx<0||ny<0||nx>=N||ny>=N)continue; if(cells[ny*N+nx]===9)n++;} cells[i]=n; }
 grid.innerHTML=''; cells.forEach((v,i)=>{const btn=document.createElement('button');btn.className='m';btn.onclick=()=>reveal(i,btn);grid.appendChild(btn);}); AuraGame.setMsg(msg,'حذارِ الألغام',null);}
function reveal(i,btn){if(dead||btn.dataset.o)return;btn.dataset.o=1;AuraGame.Audio.play('click');
 if(cells[i]===9){btn.textContent='💣';dead=true;AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'انفجار!','lose');return;}
 btn.textContent=cells[i]||''; btn.style.background='#1f3d2e'; open++; if(open>=N*N-BOMBS){AuraGame.Audio.play('win');AuraGame.setMsg(msg,'نجحت!','win');}}
restart.onclick=build; build();
""",
))

# 8 Race
write("race.html", LOCAL_SHELL.format(
    title="سباق النيون", hint="تجنب السيارات",
    body='<div class="aura-stage"><canvas id="c" width="360" height="480"></canvas></div>',
    panel='<button class="aura-btn ghost" id="left">يسار</button><button class="aura-btn ghost" id="right">يمين</button><button class="aura-btn" id="restart">جديد</button>',
    extra="",
    js="""
const ctx=c.getContext('2d'); let x=160, cars=[], score=0, alive=true;
function reset(){x=160;cars=[];score=0;alive=true;scoreChip.textContent='نقاط 0';}
function loop(){ctx.fillStyle='#0a0f1c';ctx.fillRect(0,0,360,480); ctx.fillStyle='#222';ctx.fillRect(150,0,60,480);
 if(alive){ if(Math.random()<0.03)cars.push({x:AuraGame.randInt(0,2)*60+150,y:-40}); cars.forEach(car=>{car.y+=5;ctx.fillStyle='#ff5ea8';ctx.fillRect(car.x,car.y,50,70); if(car.y>400&&Math.abs(car.x-x)<45){alive=false;AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'تصادم','lose');} if(car.y>480){score++;scoreChip.textContent='نقاط '+score;AuraGame.Audio.play('tick');}}); cars=cars.filter(car=>car.y<=500);}
 ctx.fillStyle='#4df0d2';ctx.fillRect(x,400,50,70); requestAnimationFrame(loop);}
left.onclick=()=>{x=Math.max(150,x-60);AuraGame.Audio.play('click');}; right.onclick=()=>{x=Math.min(270,x+60);AuraGame.Audio.play('click');}; restart.onclick=reset; reset(); loop();
""",
))

# 9 Fruit
write("fruit.html", LOCAL_SHELL.format(
    title="تقطيع الفواكه", hint="اضغط الفاكهة قبل سقوطها",
    body='<div class="aura-stage"><canvas id="c" width="360" height="420"></canvas></div>',
    panel='<button class="aura-btn" id="restart">جديد</button>',
    extra="",
    js="""
const ctx=c.getContext('2d'); const fruits=['🍉','🍊','🥝','🍓','🍍']; let items=[], score=0, alive=true;
function spawn(){items.push({x:AuraGame.randInt(30,330),y:420,vy:-AuraGame.randInt(9,13),vx:AuraGame.randInt(-2,2),emoji:fruits[AuraGame.randInt(0,fruits.length-1)],r:28});}
function loop(){ctx.clearRect(0,0,360,420);ctx.fillStyle='#1a1020';ctx.fillRect(0,0,360,420);
 if(alive && Math.random()<0.04)spawn();
 items.forEach(f=>{f.vy+=0.25;f.x+=f.vx;f.y+=f.vy;ctx.font='34px serif';ctx.fillText(f.emoji,f.x,f.y); if(f.y>440){alive=false;AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'فاتتك!','lose');}});
 items=items.filter(f=>f.y<=440); requestAnimationFrame(loop);}
c.onclick=(e)=>{const rect=c.getBoundingClientRect(); const x=(e.clientX-rect.left)*(360/rect.width), y=(e.clientY-rect.top)*(420/rect.height);
 for(let i=items.length-1;i>=0;i--){const f=items[i]; if(Math.hypot(f.x-x,f.y-y)<36){items.splice(i,1);score++;scoreChip.textContent='نقاط '+score;AuraGame.Audio.play('pop'); break;}}};
restart.onclick=()=>{items=[];score=0;alive=true;scoreChip.textContent='نقاط 0';AuraGame.setMsg(msg,'قطّع',null)}; loop();
""",
))

# 10 Match3 simplified click swap adjacent
write("match3.html", LOCAL_SHELL.format(
    title="جواهر الماتش", hint="بدّل جوهرتين متجاورتين",
    body='<div class="aura-stage" style="padding:10px"><div id="grid" class="m3"></div></div>',
    panel='<button class="aura-btn" id="restart">خلط</button>',
    extra="<style>.m3{display:grid;grid-template-columns:repeat(6,1fr);gap:6px}.g{aspect-ratio:1;border:0;border-radius:12px;font-size:22px;background:rgba(255,255,255,.08)}.sel{outline:2px solid #ffd76a}</style>",
    js="""
const gems=['💎','⭐','🔮','❤️','🍀','🔶']; let board=[], sel=null, score=0;
function build(){board=Array.from({length:36},()=>gems[AuraGame.randInt(0,gems.length-1)]); render();}
function render(){grid.innerHTML='';board.forEach((g,i)=>{const b=document.createElement('button');b.className='g'+(sel===i?' sel':'');b.textContent=g;b.onclick=()=>tap(i);grid.appendChild(b);}); scoreChip.textContent='نقاط '+score;}
function tap(i){AuraGame.Audio.play('click'); if(sel==null){sel=i;render();return;} const a=sel,b=i; sel=null;
 const ax=a%6,ay=(a/6)|0,bx=b%6,by=(b/6)|0; if(Math.abs(ax-bx)+Math.abs(ay-by)!==1){render();return;}
 [board[a],board[b]]=[board[b],board[a]];
 // clear matches
 let cleared=0; for(let r=0;r<6;r++)for(let c=0;c<4;c++){const i0=r*6+c; if(board[i0]&&board[i0]===board[i0+1]&&board[i0]===board[i0+2]){board[i0]=board[i0+1]=board[i0+2]='';cleared+=3;}}
 for(let c=0;c<6;c++)for(let r=0;r<4;r++){const i0=r*6+c; if(board[i0]&&board[i0]===board[i0+6]&&board[i0]===board[i0+12]){board[i0]=board[i0+6]=board[i0+12]='';cleared+=3;}}
 if(cleared){score+=cleared;AuraGame.Audio.play('coin'); board=board.map(v=>v||gems[AuraGame.randInt(0,gems.length-1)]);} else {[board[a],board[b]]=[board[b],board[a]];AuraGame.Audio.play('lose');}
 render();}
restart.onclick=()=>{score=0;build();}; build();
""",
))

# 11 Blackjack coin
write("blackjack.html", COIN_SHELL.format(
    title="بلاك جاك", gid="blackjack", hint="اقترب من 21",
    body='<div class="aura-stage" style="padding:16px;text-align:center"><div id="dealer">🏠 ؟</div><div id="you" style="margin-top:18px;font-size:28px">أنت</div></div>',
    panel='<button class="aura-btn" id="go">وزّع</button>',
    extra="",
    js="document.getElementById('go').onclick=()=>play(null); window.onCasualResult=function(r){const d=r.detail||{}; you.textContent='أنت '+(d.you||'?'); dealer.textContent='🏠 '+(d.dealer||'?');};",
))

# 12 Poker dice coin
write("poker.html", COIN_SHELL.format(
    title="نرد البوكر", gid="poker", hint="ارمِ النرد الخمس",
    body='<div class="aura-stage" style="display:flex;align-items:center;justify-content:center;gap:8px;height:220px;font-size:42px" id="diceRow">🎲🎲🎲🎲🎲</div>',
    panel='<button class="aura-btn" id="go">ارمِ</button>',
    extra="",
    js="document.getElementById('go').onclick=()=>play(null); window.onCasualResult=function(r){const d=(r.detail&&r.detail.dice)||[]; diceRow.textContent=d.map(x=>'⚀⚁⚂⚃⚄⚅'[x-1]||'🎲').join(' ');};",
))

# 13 Trivia local
write("trivia.html", LOCAL_SHELL.format(
    title="أسئلة سريعة", hint="اختر الإجابة الصحيحة",
    body='<div class="aura-stage" style="padding:16px"><div id="q" style="font-size:18px;margin-bottom:14px"></div><div id="opts" class="aura-panel" style="flex-direction:column"></div></div>',
    panel='<button class="aura-btn ghost" id="next">التالي</button>',
    extra="",
    js="""
const bank=[{q:'عاصمة فرنسا؟',a:['باريس','مدريد','روما','برلين'],c:0},{q:'2+2*2؟',a:['6','8','4','2'],c:0},{q:'لون السماء نهاراً؟',a:['أخضر','أزرق','أحمر','أسود'],c:1},{q:'كم يوماً بالأسبوع؟',a:['5','6','7','8'],c:2}];
let i=0,score=0;
function show(){const item=bank[i%bank.length]; q.textContent=item.q; opts.innerHTML=''; item.a.forEach((t,idx)=>{const b=document.createElement('button');b.className='aura-btn ghost';b.textContent=t;b.onclick=()=>{if(idx===item.c){score++;AuraGame.Audio.play('win');AuraGame.setMsg(msg,'صح','win');}else{AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'خطأ','lose');} scoreChip.textContent='نقاط '+score;}; opts.appendChild(b);});}
next.onclick=()=>{i++;show();}; show();
""",
))

# 14 Hangman
write("hangman.html", LOCAL_SHELL.format(
    title="الكلمة المعلقة", hint="خمّن الحروف",
    body='<div class="aura-stage" style="padding:16px;text-align:center"><div id="word" style="letter-spacing:8px;font-size:28px;font-weight:900"></div><div id="lives" style="margin:10px 0">❤❤❤❤❤❤</div><div id="letters" class="aura-panel"></div></div>',
    panel='<button class="aura-btn" id="restart">كلمة جديدة</button>',
    extra="",
    js="""
const words=['سحر','قمر','ذهب','نجم','ورد','ليل','حب']; let secret='', mask=[], left=6, used=new Set();
function start(){secret=words[AuraGame.randInt(0,words.length-1)]; mask=Array(secret.length).fill('_'); left=6; used.clear(); render(); AuraGame.setMsg(msg,'خمّن',null);}
function render(){word.textContent=mask.join(' '); lives.textContent='❤'.repeat(left)+'·'.repeat(6-left); letters.innerHTML='';
 'ابتثجحخدذرزسشصضطظعغفقكلمنهوي'.split('').forEach(ch=>{const b=document.createElement('button');b.className='aura-btn ghost';b.textContent=ch;b.disabled=used.has(ch);b.onclick=()=>guess(ch);letters.appendChild(b);}); scoreChip.textContent='متبقي '+left;}
function guess(ch){used.add(ch);AuraGame.Audio.play('click'); let hit=false; for(let i=0;i<secret.length;i++) if(secret[i]===ch){mask[i]=ch;hit=true;} if(!hit){left--;AuraGame.Audio.play('tick');} if(mask.join('')===secret){AuraGame.Audio.play('win');AuraGame.setMsg(msg,'أحسنت!','win');} if(left<=0){AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'الكلمة: '+secret,'lose');} render();}
restart.onclick=start; start();
""",
))

# 15 Bubbles
write("bubbles.html", LOCAL_SHELL.format(
    title="قاعات الألوان", hint="اضغط الفقاعات المتشابهة",
    body='<div class="aura-stage" style="padding:10px"><div id="grid" class="bub"></div></div>',
    panel='<button class="aura-btn" id="restart">جديد</button>',
    extra="<style>.bub{display:grid;grid-template-columns:repeat(6,1fr);gap:8px}.b{aspect-ratio:1;border-radius:50%;border:0;font-size:18px}</style>",
    js="""
const colors=['#e74c3c','#3498db','#2ecc71','#f1c40f','#9b59b6']; let cells=[], score=0;
function build(){cells=Array.from({length:36},()=>colors[AuraGame.randInt(0,colors.length-1)]); render();}
function render(){grid.innerHTML='';cells.forEach((col,i)=>{const b=document.createElement('button');b.className='b';b.style.background=col;b.onclick=()=>pop(i,col);grid.appendChild(b);}); scoreChip.textContent='نقاط '+score;}
function pop(i,col){AuraGame.Audio.play('pop'); let n=0; cells=cells.map((c,idx)=>{if(c===col){n++;return colors[AuraGame.randInt(0,colors.length-1)];}return c;}); score+=n; render();}
restart.onclick=()=>{score=0;build();}; build();
""",
))

# 16 Tower
write("tower.html", LOCAL_SHELL.format(
    title="برج التوازن", hint="أوقف القطعة فوق البرج",
    body='<div class="aura-stage"><canvas id="c" width="360" height="480"></canvas></div>',
    panel='<button class="aura-btn" id="drop">أوقف</button><button class="aura-btn ghost" id="restart">جديد</button>',
    extra="",
    js="""
const ctx=c.getContext('2d'); let stack=[{x:120,w:120}], mover={x:20,w:120,dir:2}, score=0, alive=true;
function draw(){ctx.fillStyle='#0b1020';ctx.fillRect(0,0,360,480); stack.forEach((s,i)=>{ctx.fillStyle=i%2?'#ffd76a':'#4df0d2';ctx.fillRect(s.x,440-i*24,s.w,22);}); if(alive){ctx.fillStyle='#ff5ea8';ctx.fillRect(mover.x,440-stack.length*24,mover.w,22);} }
function loop(){ if(alive){mover.x+=mover.dir; if(mover.x<10||mover.x+mover.w>350)mover.dir*=-1;} draw(); requestAnimationFrame(loop);}
drop.onclick=()=>{ if(!alive)return; const top=stack[stack.length-1]; const left=Math.max(mover.x,top.x); const right=Math.min(mover.x+mover.w,top.x+top.w); const w=right-left;
 if(w<=8){alive=false;AuraGame.Audio.play('lose');AuraGame.setMsg(msg,'سقط البرج','lose');return;}
 stack.push({x:left,w:w}); mover={x:10,w:w,dir:2+(score*0.15)}; score++; scoreChip.textContent='نقاط '+score; AuraGame.Audio.play('hit');};
restart.onclick=()=>{stack=[{x:120,w:120}];mover={x:20,w:120,dir:2};score=0;alive=true;scoreChip.textContent='نقاط 0';}; loop();
""",
))

# 17 Colors
write("colors.html", LOCAL_SHELL.format(
    title="تحدي الألوان", hint="اضغط اللون المطلوب بسرعة",
    body='<div class="aura-stage" style="padding:18px;text-align:center"><div id="need" style="font-size:28px;font-weight:900;margin-bottom:16px">?</div><div id="opts" class="aura-panel"></div></div>',
    panel='<button class="aura-btn" id="restart">جولة</button>',
    extra="",
    js="""
const cols=[{n:'أحمر',c:'#e74c3c'},{n:'أزرق',c:'#3498db'},{n:'أخضر',c:'#2ecc71'},{n:'أصفر',c:'#f1c40f'}]; let target=0,score=0;
function round(){target=AuraGame.randInt(0,cols.length-1); need.textContent='اضغط: '+cols[target].n; need.style.color=cols[AuraGame.randInt(0,cols.length-1)].c; opts.innerHTML='';
 cols.forEach((col,i)=>{const b=document.createElement('button');b.className='aura-btn';b.style.background=col.c;b.textContent=col.n;b.onclick=()=>{if(i===target){score++;AuraGame.Audio.play('win');}else{AuraGame.Audio.play('lose');} scoreChip.textContent='نقاط '+score; round();}; opts.appendChild(b);});}
restart.onclick=()=>{score=0;round();}; round();
""",
))

# 18 Tarot coin
write("tarot.html", COIN_SHELL.format(
    title="تاروت السحر", gid="tarot", hint="اسحب بطاقة القدر",
    body='<div class="aura-stage" style="display:flex;align-items:center;justify-content:center;height:260px"><div id="card" style="width:140px;height:210px;border-radius:16px;background:linear-gradient(160deg,#2a1040,#12081c);border:2px solid #ffd76a;display:flex;align-items:center;justify-content:center;font-size:42px">🂠</div></div>',
    panel='<button class="aura-btn" id="go">اسحب بطاقة</button>',
    extra="",
    js="document.getElementById('go').onclick=()=>{AuraGame.Audio.play('magic');play(null)}; window.onCasualResult=function(r){card.textContent=(r.detail&&r.detail.card)||'✨';};",
))

# 19 Penalty coin
write("penalty.html", COIN_SHELL.format(
    title="ركلة الجزاء", gid="penalty", hint="اختر الزاوية",
    body='<div class="aura-stage" style="padding:16px;text-align:center"><div style="font-size:64px">🥅</div><div id="shot" style="margin-top:12px;font-size:20px">اختر اتجاه الركلة</div></div>',
    panel='<button class="aura-btn ghost" data-c="L">يسار</button><button class="aura-btn ghost" data-c="C">وسط</button><button class="aura-btn ghost" data-c="R">يمين</button>',
    extra="",
    js="document.querySelectorAll('[data-c]').forEach(b=>b.onclick=()=>play(b.dataset.c)); window.onCasualResult=function(r){const d=r.detail||{}; shot.textContent=(r.won?'⚽ هدف!':'🧤 تصدى الحارس')+' ('+(d.keeper||'?')+')';};",
))

# 20 Reaction coin
write("reaction.html", COIN_SHELL.format(
    title="سرعة الرد", gid="reaction", hint="انتظر الأخضر ثم اضغط",
    body='<div class="aura-stage" style="display:flex;align-items:center;justify-content:center;height:260px"><button id="pad" class="pad">انتظر…</button></div>',
    panel='<button class="aura-btn" id="arm">جهّز الجولة</button>',
    extra="<style>.pad{width:180px;height:180px;border-radius:50%;border:0;font-size:22px;font-weight:900;background:#c0392b;color:#fff}.pad.ready{background:#27ae60}</style>",
    js="""
let armed=false, ready=false, t0=0;
arm.onclick=()=>{ if(busy)return; armed=true; ready=false; pad.classList.remove('ready'); pad.textContent='انتظر…'; AuraGame.Audio.play('tick');
 setTimeout(()=>{ if(!armed)return; ready=true; t0=performance.now(); pad.classList.add('ready'); pad.textContent='الآن!'; AuraGame.Audio.play('pop'); }, 800+Math.random()*1600);};
pad.onclick=()=>{ if(!armed)return; if(!ready){AuraGame.setMsg(msg,'مبكر!','lose');AuraGame.Audio.play('lose');armed=false;return;}
 const ms=Math.round(performance.now()-t0); armed=false; ready=false; pad.classList.remove('ready'); pad.textContent=ms+'ms'; play(ms);};
window.onCasualResult=function(r){/* balance updated */};
""",
))

print("20 games ready")
