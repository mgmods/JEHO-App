<template>
  <section class="conversation-page" dir="rtl">
    <div class="head">
      <div><h1>جميع المحادثات</h1><p>عرض ومراجعة فقط — لا يتم تعديل حالة أي رسالة عند المشاهدة.</p></div>
      <span class="readonly"><i class="bi bi-eye"></i> قراءة فقط</span>
    </div>

    <div class="filters">
      <input v-model="filters.search" placeholder="بحث باسم المستخدم أو اسم المستخدم أو المعرف" @keyup.enter="load(1)">
      <select v-model="filters.type" @change="load(1)">
        <option value="">كل المحادثات</option><option value="direct">مباشرة</option><option value="group">جماعية</option>
      </select>
      <input v-model="filters.from" type="date" @change="load(1)">
      <input v-model="filters.to" type="date" @change="load(1)">
      <button @click="load(1)"><i class="bi bi-search"></i> بحث</button>
    </div>

    <div class="layout">
      <aside class="list card">
        <div class="list-title"><b>أحدث المحادثات</b><span>{{ total }} محادثة</span></div>
        <div v-if="loadingList" class="state">جارِ تحميل المحادثات…</div>
        <div v-else-if="!items.length" class="state">لا توجد محادثات.</div>
        <button v-for="c in items" :key="c.id" class="row" :class="{active:selectedId===c.id}" @click="open(c.id)">
          <div class="avatars">
            <img v-for="p in c.participants.slice(0,2)" :key="p.id" :src="avatar(p.avatarUrl)" @error="fallback">
          </div>
          <div class="row-main">
            <div class="row-top"><b>{{ c.title }}</b><small>{{ date(c.lastMessageAt) }}</small></div>
            <div class="preview"><span>{{ label(c.lastMessage?.type) }}</span>{{ preview(c.lastMessage) }}</div>
            <div class="meta">{{ c.messageCount }} رسالة · {{ c.participants.length }} مستخدم <em v-if="c.unreadCount">· غير مقروءة {{ c.unreadCount }}</em></div>
          </div>
        </button>
        <div class="pager" v-if="pages>1">
          <button :disabled="page<=1" @click="load(page-1)">السابق</button><span>{{page}} / {{pages}}</span>
          <button :disabled="page>=pages" @click="load(page+1)">التالي</button>
        </div>
      </aside>

      <main class="detail card">
        <div v-if="!selected" class="empty"><i class="bi bi-chat-square-text"></i><h2>اختر محادثة</h2><p>ستظهر هنا الرسائل والمرفقات المحفوظة فعلياً في النظام.</p></div>
        <template v-else>
          <header class="detail-head">
            <div><h2>{{ selected.title || 'محادثة' }}</h2><div class="people"><span v-for="p in selected.participants" :key="p.id">{{p.displayName || p.username || p.id}} <small v-if="p.publicId">#{{p.publicId}}</small></span></div></div>
            <div class="stats">{{selected.messageCount}} رسالة · {{selected.unreadCount}} غير مقروءة</div>
          </header>
          <div ref="viewport" class="messages" @scroll="scroll">
            <div v-if="loadingOlder" class="loading">جارِ تحميل الرسائل السابقة…</div>
            <article v-for="m in messages" :key="m.id" class="message">
              <img class="msg-avatar" :src="avatar(m.sender?.avatarUrl)" @error="fallback">
              <div class="msg-body">
                <div class="msg-meta"><b>{{m.sender?.displayName || m.sender?.username || m.senderId}}</b><span v-if="m.sender?.publicId">#{{m.sender.publicId}}</span><time>{{date(m.createdAt,true)}}</time><i v-if="m.isEdited">معدلة</i></div>
                <div class="bubble">
                  <div v-if="m.content" class="text">{{m.content}}</div>
                  <img v-if="image(m)" class="image" :src="media(m.media.url)" loading="lazy" @click="zoom(m.media.url)">
                  <video v-else-if="video(m)" class="video" controls preload="metadata" :src="media(m.media.url)"></video>
                  <audio v-else-if="audio(m)" class="audio" controls preload="metadata" :src="media(m.media.url)"></audio>
                  <a v-else-if="m.media" class="file" :href="media(m.media.url)" target="_blank" rel="noopener">
                    <i class="bi bi-file-earmark"></i><span><b>{{fileName(m.media.url)}}</b><small>{{m.media.mimeType || m.type}} · {{bytes(m.media.size)}}</small></span><i class="bi bi-box-arrow-up-right"></i>
                  </a>
                  <div v-if="m.replyTo" class="reply"><b>رد على:</b> {{m.replyTo.content || label(m.replyTo.type)}}</div>
                  <div v-if="!m.content && !m.media" class="empty-msg">{{label(m.type)}}</div>
                </div>
              </div>
            </article>
          </div>
        </template>
      </main>
    </div>

    <div v-if="lightbox" class="lightbox" @click.self="lightbox=''"><button @click="lightbox=''">×</button><img :src="lightbox"></div>
  </section>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const auth=useAuthStore()
const items=ref([]), selected=ref(null), selectedId=ref('')
const messages=ref([]), page=ref(1), pages=ref(0), total=ref(0)
const loadingList=ref(false), loadingOlder=ref(false), messagePage=ref(1), messagePages=ref(0)
const viewport=ref(null), lightbox=ref('')
const filters=ref({search:'',type:'',from:'',to:''})
const placeholder='data:image/svg+xml;charset=UTF-8,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" rx="40" fill="#eee"/><circle cx="40" cy="31" r="13" fill="#aaa"/><path d="M14 69c7-18 45-18 52 0" fill="#aaa"/></svg>')

function base(){
  const raw=import.meta.env.VITE_API_URL || '/api/v1'
  return new URL(raw,window.location.origin).href.replace(/\/$/,'')
}
async function request(path,params={}){
  const res=await axios.get(base()+path,{params,headers:auth.token?{Authorization:'Bearer '+auth.token}:{}})
  let data=res.data
  if(data?.success===true && 'data' in data) data=data.data
  return data
}
async function load(p=1){
  loadingList.value=true
  try{
    const d=await request('/admin/conversations',{page:p,limit:20,search:filters.value.search||undefined,type:filters.value.type||undefined,from:filters.value.from||undefined,to:filters.value.to||undefined})
    items.value=d?.items||[]; page.value=d?.meta?.page||p; pages.value=d?.meta?.totalPages||0; total.value=d?.meta?.total||0
  }finally{loadingList.value=false}
}
async function open(id){
  selectedId.value=id; selected.value=null; messages.value=[]; messagePage.value=1; messagePages.value=0
  const [c,m]=await Promise.all([request('/admin/conversations/'+id),request('/admin/conversations/'+id+'/messages',{page:1,limit:50})])
  selected.value=c; messages.value=m?.items||[]; messagePages.value=m?.meta?.totalPages||0
  await nextTick(); if(viewport.value) viewport.value.scrollTop=viewport.value.scrollHeight
}
async function older(){
  if(loadingOlder.value || messagePage.value>=messagePages.value) return
  loadingOlder.value=true
  const el=viewport.value, h=el?.scrollHeight||0, t=el?.scrollTop||0
  try{
    const p=messagePage.value+1, d=await request('/admin/conversations/'+selectedId.value+'/messages',{page:p,limit:50})
    messagePage.value=p; messages.value=[...(d?.items||[]),...messages.value]; messagePages.value=d?.meta?.totalPages||messagePages.value
    await nextTick(); if(el) el.scrollTop=t+(el.scrollHeight-h)
  }finally{loadingOlder.value=false}
}
function scroll(e){if(e.target.scrollTop<70) older()}
function label(t){return ({text:'نص',image:'صورة',video:'فيديو',audio:'صوت',file:'ملف',gift:'هدية',system:'نظام'})[t]||t||'محتوى'}
function preview(m){return !m?'لا توجد رسائل':m.content||label(m.type)}
function media(u){if(!u)return '';if(/^https?:\/\//i.test(u))return u;const origin=new URL(import.meta.env.VITE_API_URL||window.location.origin,window.location.origin).origin;return new URL(u.startsWith('/')?u:'/'+u,origin).href}
function avatar(u){return u?media(u):placeholder}
function fallback(e){e.target.src=placeholder}
function image(m){return !!m.media && (m.type==='image'||String(m.media.mimeType||'').startsWith('image/'))}
function video(m){return !!m.media && (m.type==='video'||String(m.media.mimeType||'').startsWith('video/'))}
function audio(m){return !!m.media && (m.type==='audio'||String(m.media.mimeType||'').startsWith('audio/'))}
function fileName(u){try{return decodeURIComponent(String(u).split('?')[0].split('/').pop()||'مرفق')}catch{return 'مرفق'}}
function bytes(v){let n=Number(v||0);if(!n)return 'الحجم غير متوفر';const a=['B','KB','MB','GB'];let i=0;while(n>=1024&&i<a.length-1){n/=1024;i++}return (i?n.toFixed(n>=10?0:1):n.toFixed(0))+' '+a[i]}
function date(v,time=false){if(!v)return '—';try{return new Intl.DateTimeFormat('ar',{dateStyle:'medium',...(time?{timeStyle:'short'}:{})}).format(new Date(v))}catch{return '—'}}
function zoom(u){lightbox.value=media(u)}
onMounted(()=>load())
</script>

<style scoped>
.conversation-page{padding:18px;direction:rtl}.head{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px}.head h1{margin:0;font-weight:800}.head p{margin:4px 0;color:#777}.readonly{background:#eaf6ed;color:#26703a;border-radius:999px;padding:7px 12px;font-weight:700}.filters{display:grid;grid-template-columns:1fr 170px 150px 150px auto;gap:8px;padding:12px;margin-bottom:14px;background:#fff;border-radius:14px;box-shadow:0 3px 15px #00000008}.filters input,.filters select,.filters button{min-height:42px;border:1px solid #ddd;border-radius:9px;padding:7px 10px}.filters button{background:#6b55b8;color:white;border:0;font-weight:700}.layout{display:grid;grid-template-columns:minmax(340px,.9fr) minmax(0,1.7fr);gap:14px;min-height:680px}.card{background:#fff;border-radius:14px;box-shadow:0 3px 18px #00000008;overflow:hidden}.list-title,.detail-head{padding:14px;border-bottom:1px solid #eee}.list-title{display:flex;justify-content:space-between}.list-title span,.meta,.row-top small,.stats{color:#777;font-size:12px}.row{width:100%;display:flex;gap:10px;text-align:right;padding:11px;border:0;border-bottom:1px solid #f1f1f1;background:#fff;cursor:pointer}.row:hover,.row.active{background:#faf7ef}.avatars{width:48px;min-width:48px}.avatars img{width:38px;height:38px;border-radius:50%;object-fit:cover;border:2px solid white}.row-main{min-width:0;flex:1}.row-top,.preview{display:flex;justify-content:space-between;gap:8px}.preview{justify-content:flex-start;margin:4px 0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.preview span{background:#f0eefb;color:#5d4aa6;border-radius:999px;padding:2px 6px;font-size:10px}.meta em{font-style:normal;color:#a56b00}.pager{display:flex;justify-content:center;gap:12px;align-items:center;padding:12px}.pager button{border:1px solid #ddd;background:#fff;border-radius:7px;padding:5px 10px}.detail{min-width:0}.detail-head{display:flex;justify-content:space-between;gap:12px}.detail-head h2{margin:0;font-size:18px}.people{display:flex;flex-wrap:wrap;gap:6px;margin-top:7px}.people span{background:#f5f5f5;border-radius:999px;padding:3px 7px;font-size:12px}.messages{height:620px;overflow:auto;padding:18px;background:#fafafa}.message{display:flex;gap:8px;margin-bottom:14px}.msg-avatar{width:34px;height:34px;border-radius:50%;object-fit:cover}.msg-body{max-width:80%;min-width:0}.msg-meta{display:flex;gap:6px;align-items:center;flex-wrap:wrap;color:#777;font-size:11px;margin-bottom:4px}.msg-meta b{color:#333}.msg-meta i{font-style:normal;color:#999}.bubble{background:white;border:1px solid #e7e7e7;border-radius:13px;padding:9px}.text{white-space:pre-wrap;word-break:break-word}.image{display:block;max-width:430px;max-height:430px;border-radius:9px;object-fit:contain;cursor:zoom-in}.video{display:block;width:min(540px,100%);max-height:430px;background:#000;border-radius:9px}.audio{width:min(430px,100%)}.file{display:flex;align-items:center;gap:9px;min-width:250px;color:inherit;text-decoration:none;border:1px solid #ddd;border-radius:9px;padding:9px}.file span{flex:1;min-width:0;display:flex;flex-direction:column}.file small{color:#777;margin-top:2px}.reply{margin-top:7px;padding:5px;border-right:3px solid #d6b35c;background:#faf8f1;font-size:11px}.empty-msg,.state,.empty{text-align:center;color:#777;padding:25px}.empty{height:100%;min-height:500px;display:flex;flex-direction:column;justify-content:center;align-items:center}.empty i{font-size:42px}.loading{text-align:center;color:#777;padding:8px}.lightbox{position:fixed;inset:0;z-index:3000;background:#000b;display:flex;align-items:center;justify-content:center}.lightbox img{max-width:94vw;max-height:90vh}.lightbox button{position:absolute;top:16px;left:16px;border:0;border-radius:50%;width:40px;height:40px;font-size:25px}@media(max-width:1100px){.filters{grid-template-columns:1fr 1fr}.layout{grid-template-columns:1fr}}@media(max-width:650px){.filters{grid-template-columns:1fr}.detail-head{flex-direction:column}.messages{height:540px}.msg-body{max-width:90%}}
</style>