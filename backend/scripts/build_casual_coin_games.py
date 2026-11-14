# -*- coding: utf-8 -*-
"""Write coin-backed casual HTML games + expand catalog helpers."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "public" / "games"


def write(name: str, html: str) -> None:
    (ROOT / name).write_text(html, encoding="utf-8")
    print("wrote", name, len(html))


SHELL = """<!DOCTYPE html>
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
  b.onclick=()=>{{bet=v;AuraGame.Audio.play('chip');[...chipsEl.children].forEach(x=>x.classList.remove('glow'));b.classList.add('glow');AuraGame.setMsg(msg,'الرهان: '+v+' كوينز',null);}};
  chipsEl.appendChild(b);
}});
async function refresh(){{
  try{{ const s=await AuraGame.api('/games/casual'); balance=Number(s.balance||0); bal.textContent=balance.toLocaleString('en-US'); }}
  catch(e){{ bal.textContent='—'; }}
}}
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

write(
    "slots.html",
    SHELL.format(
        title="سلوتس الجواهر",
        gid="slots",
        hint="اختر الرهان ثم دور",
        body='<div class="aura-stage" style="padding:18px"><div class="reels" id="reels"><div class="reel" id="r0">7️⃣</div><div class="reel" id="r1">💎</div><div class="reel" id="r2">🍒</div></div></div>',
        panel='<button class="aura-btn" id="go">دور بالكوينز</button>',
        extra="<style>.reels{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;max-width:360px;margin:40px auto}.reel{aspect-ratio:1;border-radius:22px;display:flex;align-items:center;justify-content:center;font-size:54px;background:linear-gradient(180deg,#2a1840,#12081c);border:2px solid rgba(255,215,106,.45)}</style>",
        js="""
window.onCasualResult=function(r){const d=(r.detail&&r.detail.reels)||['❓','❓','❓']; r0.textContent=d[0]; r1.textContent=d[1]; r2.textContent=d[2];};
document.getElementById('go').onclick=()=>play(null);
""",
    ),
)

write(
    "roulette.html",
    SHELL.format(
        title="روليت ذهبية",
        gid="roulette",
        hint="اختر لون وارهن كوينز",
        body='<div class="aura-stage" style="display:flex;align-items:center;justify-content:center;height:320px"><div id="wheel" class="wheel">🎡</div></div>',
        panel='<button class="aura-btn ghost" data-c="red">أحمر ×2</button><button class="aura-btn ghost" data-c="black">أسود ×2</button><button class="aura-btn ghost" data-c="green">صفر ×14</button>',
        extra="<style>.wheel{font-size:120px;filter:drop-shadow(0 0 24px rgba(255,215,106,.45));transition:transform 1.2s cubic-bezier(.2,.8,.2,1)}</style>",
        js="""
let choice='red';
document.querySelectorAll('[data-c]').forEach(b=>b.onclick=()=>{choice=b.dataset.c; play(choice); wheel.style.transform='rotate('+((Math.random()*720)+720)+'deg)';});
window.onCasualResult=function(r){const n=r.detail&&r.detail.number; AuraGame.setMsg(msg, (r.won?'ربحت ':' ') + 'الرقم '+(n??'?')+(r.won?(' · +'+r.payout):''), r.won?'win':'lose');};
""",
    ),
)

write(
    "plinko.html",
    SHELL.format(
        title="بلينكو نيون",
        gid="plinko",
        hint="أسقط الشريحة بالكوينز",
        body='<div class="aura-stage"><canvas id="c" width="360" height="420"></canvas></div>',
        panel='<button class="aura-btn" id="go">أسقط</button>',
        extra="",
        js="""
const canvas=document.getElementById('c'), ctx=canvas.getContext('2d');
function draw(bin){ctx.clearRect(0,0,360,420);ctx.fillStyle='#0b1228';ctx.fillRect(0,0,360,420);
for(let r=0;r<8;r++){const count=r+3,y=50+r*38;for(let i=0;i<count;i++){const x=180-(count-1)*18+i*36;ctx.beginPath();ctx.arc(x,y,5,0,Math.PI*2);ctx.fillStyle='#4df0d2';ctx.fill();}}
const bins=[1,2,5,10,5,2,1],bw=360/7;bins.forEach((v,i)=>{ctx.fillStyle=i===bin?'rgba(255,215,106,.35)':'rgba(255,255,255,.08)';ctx.fillRect(i*bw,372,bw,48);ctx.fillStyle='#ffe7a1';ctx.font='bold 13px Tahoma';ctx.textAlign='center';ctx.fillText('x'+v,i*bw+bw/2,400);});}
draw(-1);
window.onCasualResult=function(r){draw((r.detail&&r.detail.bin)||0);};
document.getElementById('go').onclick=()=>play(null);
""",
    ),
)

write(
    "crash.html",
    SHELL.format(
        title="كراش نيون",
        gid="crash",
        hint="اختر مضاعف الخروج قبل الكراش",
        body='<div class="aura-stage" style="padding:20px;text-align:center"><div id="mult" style="font-size:64px;font-weight:900;color:#4df0d2">1.50x</div><input id="cash" type="range" min="1.2" max="5" step="0.1" value="1.5" style="width:90%"/><div id="cashLbl">خروج عند 1.5x</div></div>',
        panel='<button class="aura-btn" id="go">العب بالكوينز</button>',
        extra="",
        js="""
cash.oninput=()=>{cashLbl.textContent='خروج عند '+Number(cash.value).toFixed(1)+'x'; mult.textContent=Number(cash.value).toFixed(2)+'x';};
document.getElementById('go').onclick=()=>play(Number(cash.value));
window.onCasualResult=function(r){const d=r.detail||{}; mult.textContent=(d.crashAt||0)+'x'; AuraGame.setMsg(msg, r.won?('نجحت عند '+d.cashout+'x'):('كراش عند '+d.crashAt+'x'), r.won?'win':'lose');};
""",
    ),
)

write(
    "rps.html",
    SHELL.format(
        title="حجر ورقة مقص",
        gid="rps",
        hint="اختر وارهن كوينز",
        body='<div class="aura-stage" style="display:flex;align-items:center;justify-content:center;gap:18px;height:260px;font-size:64px"><div id="you">❔</div><div>VS</div><div id="bot">❔</div></div>',
        panel='<button class="aura-btn ghost" data-c="rock">🪨</button><button class="aura-btn ghost" data-c="paper">📄</button><button class="aura-btn ghost" data-c="scissors">✂️</button>',
        extra="",
        js="""
const map={rock:'🪨',paper:'📄',scissors:'✂️'};
document.querySelectorAll('[data-c]').forEach(b=>b.onclick=()=>{you.textContent=map[b.dataset.c];play(b.dataset.c);});
window.onCasualResult=function(r){const d=r.detail||{}; bot.textContent=map[d.bot]||'❔'; you.textContent=map[d.you]||you.textContent;};
""",
    ),
)

write(
    "hilo.html",
    SHELL.format(
        title="هاي لو",
        gid="hilo",
        hint="أعلى أم أقل من البطاقة؟",
        body='<div class="aura-stage" style="display:flex;align-items:center;justify-content:center;gap:24px;height:260px"><div class="cardx" id="c1">?</div><div class="cardx" id="c2">?</div></div>',
        panel='<button class="aura-btn" data-c="high">أعلى</button><button class="aura-btn secondary" data-c="low">أقل</button><button class="aura-btn ghost" data-c="same">نفسه</button>',
        extra="<style>.cardx{width:110px;height:150px;border-radius:16px;background:linear-gradient(160deg,#fff,#ddd);color:#111;display:flex;align-items:center;justify-content:center;font-size:42px;font-weight:900;border:2px solid #c9a227}</style>",
        js="""
document.querySelectorAll('[data-c]').forEach(b=>b.onclick=()=>play(b.dataset.c));
window.onCasualResult=function(r){const d=r.detail||{}; c1.textContent=d.card??'?'; c2.textContent=d.next??'?';};
""",
    ),
)

print("casual coin games ready")
