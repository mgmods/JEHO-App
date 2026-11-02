<template>
  <div class="page-shell" dir="rtl">
    <header class="page-header"><div><h1>صندوق الدعم</h1><p>إدارة تفعيل الصندوق وأهدافه والهدايا ومكافآت الفائز.</p></div><button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? 'جارٍ الحفظ…' : 'حفظ الإعدادات' }}</button></header>
    <div v-if="error" class="alert alert-danger">{{ error }}</div><div v-if="notice" class="alert alert-success">{{ notice }}</div>
    <section class="card p-4"><h2>الإعداد العام</h2>
      <label class="switchrow"><span><b>تفعيل صندوق الدعم</b><small>يظهر الصندوق في الرومات عند التفعيل.</small></span><input v-model="form.enabled" type="checkbox"></label>
      <label class="switchrow"><span><b>إظهار الأيقونة</b><small>إخفاء الأيقونة لا يحذف الإعدادات.</small></span><input v-model="form.iconVisible" type="checkbox"></label>
      <div class="field">
        <span>الهدايا المسموح بها في الصندوق</span>
        <p class="muted">اختر الهدية من صورتها واسمها الحقيقيين؛ لا حاجة لكتابة المعرّف يدويًا.</p>
        <div v-if="catalogLoading" class="muted">جارٍ تحميل كتالوج الهدايا…</div>
        <div v-else class="item-picker">
          <label v-for="gift in catalogGifts" :key="gift.id" class="pick-card" :class="{ selected: form.giftIds.includes(String(gift.id)) }">
            <input type="checkbox" :checked="form.giftIds.includes(String(gift.id))" @change="toggleGift(gift.id, $event.target.checked)">
            <img v-if="itemImage(gift)" :src="assetUrl(itemImage(gift))" :alt="itemName(gift)" loading="lazy">
            <span v-else class="pick-placeholder">🎁</span>
            <strong>{{ itemName(gift) }}</strong>
            <small>{{ Number(gift.coinPrice ?? gift.price ?? 0).toLocaleString() }} كوينز</small>
          </label>
          <div v-if="!catalogGifts.length" class="muted">لم يتم العثور على هدايا فعالة في الكتالوج.</div>
        </div>
      </div>
    </section>
    <section class="card p-4 mt-3">
      <h2>أقسام الهدايا المؤهلة</h2>
      <p class="muted">حدد الأقسام التي تحتسب هداياها ضمن هدف الصندوق. يمكن تفعيل أكثر من قسم.</p>
      <div class="category-picks">
        <label><input v-model="form.giftCategories" type="checkbox" value="normal"> الهدايا العادية</label>
        <label><input v-model="form.giftCategories" type="checkbox" value="lucky"> هدايا الحظ</label>
        <label><input v-model="form.giftCategories" type="checkbox" value="cp"> هدايا CP</label>
      </div>
    </section>
    <section class="card p-4 mt-3">
      <div class="levels-head"><div><h2>مستويات الصندوق</h2><p class="muted">كل مستوى له هدفه ومكافآته المستقلة. ينتقل الصندوق تلقائياً للمستوى التالي.</p></div><button type="button" class="btn btn-secondary" @click="addLevel">إضافة مستوى</button></div>
      <article v-for="(level, index) in form.levels" :key="level.level" class="level-editor">
        <div class="levels-head"><h3>المستوى {{ level.level }}</h3><button v-if="form.levels.length > 1" type="button" class="btn btn-danger" @click="removeLevel(index)">حذف</button></div>
        <label class="field">اسم المستوى<input v-model="level.title" maxlength="80"></label>
        <div class="grid2">
          <label class="field">نوع الهدف<select v-model="level.goalMode"><option value="coins">كوينزات</option><option value="diamonds">ماسات</option><option value="both">كوينزات وماسات</option></select></label>
          <label class="field">عدد الهدايا المطلوبة<input v-model.number="level.giftCount" type="number" min="1"></label>
          <label class="field">هدف الكوينزات<input v-model.number="level.coinTarget" type="number" min="0"></label>
          <label class="field">هدف الماسات<input v-model.number="level.diamondTarget" type="number" min="0"></label>
        </div>
        <label class="field">أقسام الهدايا لهذا المستوى
          <div class="category-picks"><label><input v-model="level.giftCategories" type="checkbox" value="normal"> عادية</label><label><input v-model="level.giftCategories" type="checkbox" value="lucky"> حظ</label><label><input v-model="level.giftCategories" type="checkbox" value="cp"> CP</label></div>
        </label>
        <div class="field"><span>هدايا هذا المستوى تحديداً · {{ (level.giftIds || []).length || 'كل هدايا الأقسام' }}</span><p class="muted">يمكنك تحديد هدايا مختلفة لكل مستوى. اترك القائمة فارغة لاستخدام جميع هدايا الأقسام المختارة.</p><div class="item-picker"><label v-for="gift in catalogGifts" :key="gift.id" class="pick-card" :class="{ selected: (level.giftIds || []).map(String).includes(String(gift.id)) }"><input type="checkbox" :checked="(level.giftIds || []).map(String).includes(String(gift.id))" @change="toggleLevelGift(level, gift.id, $event.target.checked)"><video v-if="isVideo(itemImage(gift))" :src="assetUrl(itemImage(gift))" muted autoplay loop playsinline /><img v-else-if="itemImage(gift)" :src="assetUrl(itemImage(gift))" :alt="itemName(gift)" loading="lazy"><span v-else class="pick-placeholder">🎁</span><strong>{{ itemName(gift) }}</strong><small>{{ Number(gift.coinPrice ?? gift.price ?? 0).toLocaleString() }} كوينز</small></label></div></div>
        <label class="field">تصميم الصندوق / صورة المستوى<input v-model="level.imageUrl" placeholder="رابط صورة أو حركة الصندوق"></label>
        <div v-if="level.imageUrl" class="level-art-preview"><video v-if="isVideo(level.imageUrl)" :src="assetUrl(level.imageUrl)" muted autoplay loop playsinline /><img v-else :src="assetUrl(level.imageUrl)" alt="معاينة تصميم الصندوق"><span>معاينة شكل هذا المستوى داخل الروم</span></div>
        <div class="reward-editor">
          <div class="levels-head"><strong>مكافآت مفجّر الصندوق</strong><span class="muted">{{ (level.rewards || []).length }} مكافأة محددة</span><button type="button" class="btn btn-secondary" @click="addReward(level)">إضافة مكافأة</button></div>
          <div v-for="(reward, rewardIndex) in level.rewards" :key="rewardIndex" class="reward-line">
            <label class="field">نوع المكافأة<select v-model="reward.type"><option value="coins">كوينزات</option><option value="diamonds">ماسات</option><option value="vip">VIP</option><option value="frame">إطار</option><option value="badge">شارة</option></select></label>
            <label class="field">الكمية / المدة<input v-model.number="reward.amount" type="number" min="1"></label>
            <label v-if="reward.type === 'frame'" class="field">الإطار<select v-model="reward.itemId"><option value="">اختر الإطار</option><option v-for="item in frameItems" :key="item.id" :value="item.id">{{ itemName(item) }}</option></select></label>
            <label v-else-if="reward.type === 'vip'" class="field">خطة VIP<select v-model="reward.itemId"><option value="">اختر خطة VIP</option><option v-for="plan in catalogVipPlans" :key="plan.id" :value="plan.id">{{ plan.name || ('VIP '+plan.level) }}</option></select></label>
            <label v-else-if="reward.type === 'badge'" class="field">الشارة<select v-model="reward.itemId"><option value="">اختر الشارة</option><option v-for="badge in catalogBadges" :key="badge.id" :value="badge.id">{{ badge.name }}</option></select></label>
            <button type="button" class="btn btn-danger" @click="level.rewards.splice(rewardIndex,1)">إزالة</button>
          </div>
          <p class="muted">معاينة الجوائز التي سيحصل عليها مفجّر الصندوق · {{ (level.rewards || []).length }} مكافأة:</p>
          <div class="preview-rewards"><div v-for="(reward, n) in level.rewards" :key="n" class="preview-reward-card"><video v-if="isVideo(rewardPreviewImage(reward))" :src="assetUrl(rewardPreviewImage(reward))" muted autoplay loop playsinline /><img v-else-if="rewardPreviewImage(reward)" :src="assetUrl(rewardPreviewImage(reward))" :alt="rewardLabel(reward)" loading="lazy"><span v-else class="preview-reward-icon">{{ rewardIcon(reward.type) }}</span><strong>{{ rewardLabel(reward) }}</strong><small>{{ reward.type === 'vip' ? (Number(reward.amount)||1)+' يوم' : (['coins','diamonds'].includes(reward.type) ? Number(reward.amount||1).toLocaleString() : '× '+(Number(reward.amount)||1)) }}</small></div><span v-if="!level.rewards.length" class="muted">لم تتم إضافة مكافآت.</span></div>
        </div>
      </article>
    </section>
    <section class="card p-4 mt-3"><h2>مكافأة الفائز</h2>
      <label class="field">نوع المكافأة<select v-model="form.rewardType" @change="form.rewardItemId = ''"><option value="none">بدون مكافأة تلقائية</option><option value="coins">كوينزات</option><option value="diamonds">ماسات</option><option value="frame">إطار ملف شخصي</option><option value="badge">شارة</option><option value="vip">VIP</option></select></label>
      <div v-if="['frame','badge'].includes(form.rewardType)" class="field">
        <span>اختر المكافأة المرئية</span>
        <div v-if="catalogLoading" class="muted">جارٍ تحميل الكتالوج…</div>
        <div v-else class="item-picker">
          <label v-for="item in rewardItems" :key="item.id || item.code" class="pick-card" :class="{ selected: String(form.rewardItemId) === String(item.id || item.code) }">
            <input type="radio" name="support-reward-item" :checked="String(form.rewardItemId) === String(item.id || item.code)" @change="form.rewardItemId = String(item.id || item.code)">
            <video v-if="isVideo(itemImage(item))" :src="assetUrl(itemImage(item))" muted autoplay loop playsinline />
            <img v-else-if="itemImage(item)" :src="assetUrl(itemImage(item))" :alt="itemName(item)" loading="lazy">
            <span v-else class="pick-placeholder">{{ form.rewardType === 'badge' ? '🏅' : '🎁' }}</span>
            <strong>{{ itemName(item) }}</strong>
            <small>{{ item.type || item.category || form.rewardType }}</small>
          </label>
          <div v-if="!rewardItems.length" class="muted">لا توجد عناصر من هذا النوع في الكتالوج.</div>
        </div>
      </div>
      <label v-if="form.rewardType === 'vip'" class="field">معرّف خطة VIP<input v-model="form.rewardItemId" placeholder="VIP Plan ID"></label>
      <label class="field">كمية المكافأة<input v-model.number="form.rewardAmount" type="number" min="0"></label>
      <label class="field">تعليمات الصندوق<textarea v-model="form.instructions" rows="4"></textarea></label>
    </section>
    <p class="muted foot">يدعم الخادم مكافآت الكوينزات والماسات والإطارات والشارات مع تسجيلها ضمن المعاملة. مكافأة الهدية من المخزون غير مفعّلة تلقائياً حالياً.</p>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { supportChestApi, giftsApi, cosmeticsApi, vipApi, medalsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'
const form = reactive({ enabled:false, iconVisible:true, goalMode:'coins', coinTarget:100000, diamondTarget:10000, giftIds:[], giftCategories:['normal','lucky','cp'], levels:[{level:1,title:'الصندوق الذهبي',goalMode:'coins',coinTarget:100000,diamondTarget:0,giftCount:1,giftCategories:['normal','lucky','cp'],giftIds:[],imageUrl:'',rewards:[{type:'coins',amount:5000,itemId:''}]}], rewardType:'none', rewardItemId:'', rewardAmount:0, instructions:'كل هدية مؤهلة تساهم في هدف الصندوق. عند اكتماله يحصل مرسل الهدية الأخيرة على المكافأة.' })
const catalogGifts=ref([]), catalogCosmetics=ref([]), catalogVipPlans=ref([]), catalogBadges=ref([]), catalogLoading=ref(false)
const saving=ref(false), error=ref(''), notice=ref('')
function unwrap(r){const d=r?.data?.data ?? r?.data ?? r;return Array.isArray(d)?d:(Array.isArray(d?.items)?d.items:(Array.isArray(d?.data)?d.data:[]))}
function itemName(item){return item?.nameAr || item?.labelAr || item?.name || item?.title || item?.code || 'عنصر'}
function itemImage(item){return item?.animationUrl || item?.iconUrl || item?.previewUrl || item?.imageUrl || item?.badgeUrl || item?.assetUrl || item?.url || ''}
function assetUrl(value){return resolveAsset(value)}
function isVideo(value){return /\.(mp4|webm)(\?|$)/i.test(String(value||''))}
function addLevel(){const next=Math.max(0,...form.levels.map(x=>Number(x.level)||0))+1;form.levels.push({level:next,title:'المستوى '+next,goalMode:'coins',coinTarget:100000,diamondTarget:0,giftCount:1,giftCategories:[...(form.giftCategories||['normal'])],giftIds:[],imageUrl:'',rewards:[{type:'coins',amount:5000,itemId:''}]})}
function removeLevel(index){form.levels.splice(index,1);form.levels.forEach((x,i)=>x.level=i+1)}
function addReward(level){level.rewards=level.rewards||[];level.rewards.push({type:'coins',amount:1000,itemId:''})}
function toggleLevelGift(level,id,checked){const key=String(id);level.giftIds=checked?[...new Set([...(level.giftIds||[]).map(String),key])]: (level.giftIds||[]).map(String).filter(x=>x!==key)}
function rewardCatalogItem(r){const id=String(r?.itemId||'');if(!id)return null;if(r.type==='frame')return frameItems.value.find(x=>String(x.id)===id)||null;if(r.type==='vip')return catalogVipPlans.value.find(x=>String(x.id)===id)||null;if(r.type==='badge')return catalogBadges.value.find(x=>String(x.id)===id)||null;return null}
function rewardPreviewImage(r){const item=rewardCatalogItem(r);return item?itemImage(item):''}
function rewardIcon(type){return ({coins:'🪙',diamonds:'💎',vip:'👑',frame:'🖼️',badge:'🏅'})[type]||'🎁'}
function rewardLabel(r){const names={coins:'كوينزات',diamonds:'ماسات',vip:'VIP',frame:'إطار',badge:'شارة'};const item=rewardCatalogItem(r);return (item?itemName(item):(names[r.type]||r.type||'مكافأة'))+' × '+(Number(r.amount)||1)}
function toggleGift(id, checked){const key=String(id);form.giftIds=checked?[...new Set([...(form.giftIds||[]).map(String),key])]: (form.giftIds||[]).map(String).filter(x=>x!==key)}
const frameItems = computed(()=>catalogCosmetics.value.filter(x=>/frame/i.test(String(x.type||x.category||''))))
const rewardItems = computed(()=> {
  const all=catalogCosmetics.value
  if(form.rewardType==='gift')return catalogGifts.value
  if(form.rewardType==='entry_effect')return all.filter(x=>String(x.type||x.category||'').toLowerCase().includes('entry'))
  if(form.rewardType==='frame')return all.filter(x=>/frame|vip_badge/i.test(String(x.type||x.category||'')))
  if(form.rewardType==='badge')return catalogBadges.value
  return []
})
onMounted(async()=>{
  try{const r=await supportChestApi.getConfig();if(r?.error)throw r.error;const d=r?.data?.data ?? r?.data;if(!d||typeof d!=='object')throw new Error('Empty support chest config');Object.assign(form,d);form.giftIds=Array.isArray(d.giftIds)?d.giftIds.map(String):[];form.giftCategories=Array.isArray(d.giftCategories)?d.giftCategories:['normal','lucky','cp'];form.levels=Array.isArray(d.levels)&&d.levels.length?d.levels:[{level:1,title:'الصندوق الذهبي',goalMode:d.goalMode||'coins',coinTarget:d.coinTarget||100000,diamondTarget:d.diamondTarget||0,giftCount:1,giftCategories:form.giftCategories,giftIds:form.giftIds,imageUrl:'',rewards:[{type:d.rewardType||'coins',amount:d.rewardAmount||1000,itemId:d.rewardItemId||''}]}];form.levels.forEach(level=>{level.giftIds=Array.isArray(level.giftIds)?level.giftIds.map(String):[];level.giftCategories=Array.isArray(level.giftCategories)?level.giftCategories:[...form.giftCategories];level.rewards=Array.isArray(level.rewards)?level.rewards:[]})}
  catch(e){error='تعذّر تحميل إعدادات صندوق الدعم من الخادم: '+String(e?.message||'خطأ غير معروف')}
  catalogLoading.value=true
  try{const [g,c,vip,badges]=await Promise.all([giftsApi.list({limit:500}),cosmeticsApi.adminList({limit:500}),vipApi.plans(),medalsApi.list()]);catalogGifts.value=unwrap(g).filter(x=>x.isActive!==false);catalogCosmetics.value=unwrap(c).filter(x=>x.isActive!==false);catalogVipPlans.value=unwrap(vip).filter(x=>x.isActive!==false);catalogBadges.value=unwrap(badges).filter(x=>x.isActive!==false)}
  catch(e){if(!error.value)error='تعذّر تحميل كتالوج الهدايا والإطارات: '+String(e?.message||'خطأ غير معروف')}
  finally{catalogLoading.value=false}
})
async function save(){saving.value=true;error.value='';notice.value='';try{const payload={...form,giftIds:(form.giftIds||[]).map(String),giftCategories:(form.giftCategories||[]).map(String),levels:(form.levels||[]).map((x,i)=>({...x,level:i+1,giftIds:(x.giftIds||[]).map(String),giftCategories:(x.giftCategories||[]).map(String),rewards:(x.rewards||[]).map(r=>({...r,amount:Math.max(1,Number(r.amount)||1)}))}))};const result=await supportChestApi.saveConfig(payload);if(result?.error)throw result.error;const check=await supportChestApi.getConfig();if(check?.error)throw check.error;const saved=check?.data?.data??check?.data??check;if(!saved||!Array.isArray(saved.levels)||saved.levels.length!==payload.levels.length||saved.enabled!==payload.enabled||JSON.stringify(saved.giftIds||[])!==JSON.stringify(payload.giftIds)||JSON.stringify(saved.giftCategories||[])!==JSON.stringify(payload.giftCategories)||payload.levels.some((level,i)=>{const actual=saved.levels[i];return !actual||Number(actual.level)!==Number(level.level)||String(actual.title||'')!==String(level.title||'')||String(actual.goalMode)!==String(level.goalMode)||Number(actual.coinTarget)!==Number(level.coinTarget)||Number(actual.diamondTarget)!==Number(level.diamondTarget)||Number(actual.giftCount)!==Number(level.giftCount)||JSON.stringify([...(actual.giftIds||[])].map(String).sort())!==JSON.stringify([...(level.giftIds||[])].map(String).sort())||JSON.stringify([...(actual.giftCategories||[])].map(String).sort())!==JSON.stringify([...(level.giftCategories||[])].map(String).sort())||String(actual.imageUrl||'')!==String(level.imageUrl||'')||JSON.stringify(actual.rewards||[])!==JSON.stringify(level.rewards||[])}))throw new Error('لم تتطابق إعدادات الصندوق بعد إعادة قراءتها من الخادم.');Object.assign(form,saved);notice.value='تم حفظ إعدادات صندوق الدعم والتحقق منها بعد إعادة قراءتها من الخادم.'}catch(e){error.value='فشل الحفظ أو التحقق من إعدادات صندوق الدعم: '+String(e?.response?.data?.message||e?.message||'خطأ غير معروف')}finally{saving.value=false}}
</script>
<style scoped>
.page-shell{max-width:980px;margin:auto;padding:22px}.page-header{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}.page-header h1{margin:0 0 5px}.page-header p,.muted{color:var(--text-muted,#888);font-size:13px}.card{background:var(--surface,#fff);border:1px solid var(--border,#ddd);border-radius:16px;margin-bottom:14px}.card h2{font-size:17px;margin:0 0 16px}.switchrow{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:12px 0;border-bottom:1px solid var(--border,#eee)}.switchrow span{display:grid;gap:4px}.switchrow small{color:var(--text-muted,#888)}.field{display:grid;gap:7px;margin:15px 0;font-weight:600}.field input,.field select,.field textarea{width:100%;padding:10px 12px;border-radius:9px;border:1px solid var(--border,#ccc);background:var(--surface,#fff);color:inherit}.grid2{display:grid;grid-template-columns:1fr 1fr;gap:14px}.foot{line-height:1.7}.category-picks{display:flex;gap:18px;flex-wrap:wrap;padding:10px 0}.category-picks label{display:flex;align-items:center;gap:7px}.levels-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.level-editor{border:1px solid var(--border,#ddd);border-radius:13px;padding:14px;margin:12px 0}.level-art-preview{display:flex;align-items:center;gap:12px;margin:8px 0 14px;padding:10px;border:1px dashed var(--border,#bbb);border-radius:12px}.level-art-preview img,.level-art-preview video{width:76px;height:76px;object-fit:contain;border-radius:10px;background:#171724}.level-art-preview span{font-size:12px;color:var(--text-muted,#888)}.reward-editor{background:var(--surface-alt,#f7f7f7);padding:12px;border-radius:12px}.reward-line{display:grid;grid-template-columns:1fr 1fr 1.5fr auto;gap:10px;align-items:end;border-bottom:1px solid var(--border,#ddd);padding:8px 0}.preview-rewards{display:grid;grid-template-columns:repeat(auto-fill,minmax(112px,1fr));gap:9px}.preview-reward-card{display:flex;min-width:0;min-height:112px;flex-direction:column;align-items:center;justify-content:center;gap:6px;padding:10px;border:1px solid var(--border,#ddd);border-radius:12px;background:var(--surface,#fff);text-align:center}.preview-reward-card img,.preview-reward-card video{width:52px;height:52px;object-fit:contain}.preview-reward-card strong{font-size:12px;overflow-wrap:anywhere}.preview-reward-card small{font-size:11px;color:var(--text-muted,#888)}.preview-reward-icon{display:grid;place-items:center;width:52px;height:52px;font-size:30px}.item-picker{display:grid;grid-template-columns:repeat(auto-fill,minmax(112px,1fr));gap:10px}.pick-card{position:relative;display:flex;min-width:0;flex-direction:column;align-items:center;gap:6px;padding:10px;border:1px solid var(--border,#ddd);border-radius:12px;cursor:pointer;text-align:center;background:var(--surface,#fff)}.pick-card.selected{border-color:#35a7e6;box-shadow:0 0 0 2px #35a7e622}.pick-card input{align-self:flex-start}.pick-card img,.pick-card video{width:62px;height:62px;object-fit:contain}.pick-card strong{font-size:12px;overflow-wrap:anywhere}.pick-card small{font-size:11px;color:var(--text-muted,#888)}.pick-placeholder{display:grid;place-items:center;width:62px;height:62px;font-size:30px}@media(max-width:650px){.page-header{align-items:flex-start;flex-direction:column}.grid2,.reward-line{grid-template-columns:1fr}.levels-head{align-items:flex-start;flex-wrap:wrap}}
</style>