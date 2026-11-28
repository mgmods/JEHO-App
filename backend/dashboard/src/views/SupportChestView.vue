<template>
  <div class="page-shell" dir="rtl">
    <header class="page-header"><div><h1>صندوق الدعم</h1><p>إعدادات موحّدة للصندوق، مع مستوى وتقدّم مستقل لكل روم، وثلاثة مستويات متتابعة.</p></div><button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? 'جارٍ الحفظ…' : 'حفظ الإعدادات' }}</button></header>
    <div v-if="error" class="alert alert-danger">{{ error }}</div><div v-if="notice" class="alert alert-success">{{ notice }}</div>
    <section class="card p-4"><h2>الإعداد العام</h2>
      <label class="switchrow"><span><b>تفعيل صندوق الدعم</b><small>يظهر الصندوق في الرومات عند التفعيل.</small></span><input v-model="form.enabled" type="checkbox"></label>
      <label class="switchrow"><span><b>إظهار الأيقونة</b><small>إخفاء الأيقونة لا يحذف الإعدادات.</small></span><input v-model="form.iconVisible" type="checkbox"></label>\n      <label class="switchrow"><span><b>صوت تفجير الصندوق</b><small>تشغيل صوت الانفجار لجميع أعضاء الروم عند اكتمال المستوى.</small></span><input v-model="form.soundEnabled" type="checkbox"></label>
      <div class="field">
        <span>الهدايا المسموح بها في الصندوق</span>
        <label class="field">نطاق الهدايا العامة<select v-model="form.giftSelectionMode"><option value="all">كل الهدايا ضمن الأقسام المختارة</option><option value="selected">هدايا محددة فقط</option></select></label>
        <p class="muted">يُطبّق هذا كفلتر عام على جميع صفحات الصناديق.</p>
        <p v-if="form.giftSelectionMode === 'selected'" class="muted">اختر الهدايا من صورها وأسمائها الحقيقية.</p>
        <div v-if="form.giftSelectionMode === 'selected' && catalogLoading" class="muted">جارٍ تحميل كتالوج الهدايا…</div>
        <div v-else-if="form.giftSelectionMode === 'selected'" class="item-picker">
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
      <h2>مؤثر انفجار الصندوق</h2>
      <p class="muted">ارفع صورة أو GIF أو فيديو MP4/WebM من جهازك. يمكن أن يحمل الفيديو صوته، أو ترفع ملف صوت منفصل يعمل مع الصورة. هذا المؤثر المخصص هو الذي يظهر لجميع الموجودين عند تفجير الصندوق.</p>
      <label class="switchrow"><span><b>تفعيل المؤثر المتحرك المخصص</b><small>عند الإيقاف يعود التطبيق إلى مؤثر الانفجار الافتراضي.</small></span><input v-model="form.explosionEffect.enabled" type="checkbox"></label>
      <label class="field">ملف مؤثر الانفجار (صورة / GIF / فيديو)<input type="file" accept=".png,.jpg,.jpeg,.webp,.gif,.webm,.mp4,image/*,video/webm,video/mp4" :disabled="uploadingEffect" @change="uploadExplosionEffect"><small class="muted">{{ uploadingEffect ? 'جارٍ رفع الملف…' : (form.explosionEffect.mediaUrl ? 'تم اختيار المؤثر ورفعه. يمكنك استبداله بملف آخر.' : 'اختر ملفاً من جهازك لرفعه مباشرة.') }}</small></label>
      <div v-if="effectUploadError" class="alert alert-danger">{{ effectUploadError }}</div>
      <button v-if="form.explosionEffect.mediaUrl" type="button" class="btn btn-secondary" :disabled="uploadingEffect" @click="form.explosionEffect.mediaUrl = ''">إزالة المؤثر المخصص</button>
      <div class="effect-preview">
        <video v-if="isVideo(form.explosionEffect.mediaUrl)" :src="assetUrl(form.explosionEffect.mediaUrl)" muted autoplay loop playsinline />
        <img v-else-if="form.explosionEffect.mediaUrl" :src="assetUrl(form.explosionEffect.mediaUrl)" alt="معاينة مؤثر الانفجار">
        <span v-else>💥 معاينة مؤثر الانفجار</span>
      </div>
      <label class="field">صوت المؤثر (اختياري: MP3 / WAV / OGG)<input type="file" accept=".mp3,.wav,.m4a,.aac,audio/*" :disabled="uploadingSound" @change="uploadExplosionSound"><small class="muted">{{ uploadingSound ? 'جارٍ رفع الصوت…' : (form.explosionEffect.soundUrl ? 'تم رفع صوت المؤثر.' : 'إذا كان الملف صورة، ارفع صوتاً منفصلاً هنا. صوت الفيديو المرفوع سيعمل أيضاً.') }}</small></label>
      <button v-if="form.explosionEffect.soundUrl" type="button" class="btn btn-secondary" :disabled="uploadingSound" @click="form.explosionEffect.soundUrl = ''">إزالة الصوت المخصص</button>
      <div class="grid2">
        <label class="field">العرض النهائي (%)<input v-model.number="form.explosionEffect.width" type="number" min="10" max="180"></label>
        <label class="field">الارتفاع النهائي (%)<input v-model.number="form.explosionEffect.height" type="number" min="10" max="180"></label>
        <label class="field">نقطة البداية أفقياً (%)<input v-model.number="form.explosionEffect.startX" type="number" min="0" max="100"></label>
        <label class="field">نقطة البداية عمودياً (%)<input v-model.number="form.explosionEffect.startY" type="number" min="0" max="100"></label>
        <label class="field">نقطة الانتشار أفقياً (%)<input v-model.number="form.explosionEffect.endX" type="number" min="0" max="100"></label>
        <label class="field">نقطة الانتشار عمودياً (%)<input v-model.number="form.explosionEffect.endY" type="number" min="0" max="100"></label>
        <label class="field">مدة الحركة (مللي ثانية)<input v-model.number="form.explosionEffect.durationMs" type="number" min="400" max="8000" step="100"></label>
        <label class="field">الشفافية (%)<input v-model.number="form.explosionEffect.opacity" type="number" min="10" max="100"></label>
      </div>
      <p class="muted">القيم الافتراضية تجعل الانفجار ينطلق من منتصف منطقة الصندوق ويتوسع ليغطي معظم شاشة الروم. استخدم ملفاً شفاف الخلفية حتى تبقى المقاعد والمحادثة ظاهرة.</p>
    </section>
    <section class="card p-4 mt-3">
      <h2>تصميم توست تفجير الصندوق</h2>
      <p class="muted">تحكم بخلفية إشعار الفوز وحجمه ومكانه وصورة المكافأة، ويُطبّق على جميع أعضاء الروم.</p>
      <label class="switchrow"><span><b>إظهار توست صندوق الدعم</b><small>إظهار إشعار الفوز أعلى الروم عند التفجير.</small></span><input v-model="form.toast.enabled" type="checkbox"></label>
      <label class="switchrow"><span><b>إظهار اسم الفائز</b><small>إظهار اسم صاحب تفجير الصندوق في التوست.</small></span><input v-model="form.toast.showWinnerName" type="checkbox"></label>
      <label class="field">خلفية التوست (صورة)<input type="file" accept=".png,.jpg,.jpeg,.webp,image/*" :disabled="uploadingToastBackground" @change="uploadToastBackground"><small class="muted">{{ uploadingToastBackground ? 'جارٍ رفع الخلفية…' : (form.toast.backgroundUrl ? 'تم رفع خلفية التوست.' : 'ارفع صورة الخلفية التي تريد ظهورها خلف النص والمكافأة.') }}</small></label>
      <button v-if="form.toast.backgroundUrl" type="button" class="btn btn-secondary" :disabled="uploadingToastBackground" @click="form.toast.backgroundUrl = ''">إزالة خلفية التوست</button>
      <div class="effect-preview toast-preview" :style="form.toast.backgroundUrl ? { backgroundImage: 'url(' + assetUrl(form.toast.backgroundUrl) + ')', backgroundSize: 'cover', backgroundPosition: 'center' } : {}">
        <span>🎁 {{ form.toast.showWinnerName ? 'اسم الفائز' : 'تفجير صندوق الدعم' }} · معاينة التوست</span>
      </div>
      <div class="grid2">
        <label class="field">ارتفاع التوست (dp)<input v-model.number="form.toast.height" type="number" min="60" max="240"></label>
        <label class="field">المسافة من أعلى الشاشة (dp)<input v-model.number="form.toast.yOffset" type="number" min="0" max="300"></label>
        <label class="field">حجم اسم الفائز (sp)<input v-model.number="form.toast.titleSizeSp" type="number" min="10" max="36"></label>
        <label class="field">حجم نص التوست (sp)<input v-model.number="form.toast.bodySizeSp" type="number" min="8" max="30"></label>
        <label class="field">حجم صورة الهدية (dp)<input v-model.number="form.toast.giftSizeDp" type="number" min="24" max="120"></label>
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
      <div class="levels-head"><div><h2>صفحات صناديق الدعم · 3 مستويات</h2><p class="muted">صفحة HTML واحدة تعرض المستويات الثلاثة معاً. لكل مستوى اسم وصورة وهدف وهدايا ومكافآت ومميزات، والمستويات التالية تظهر مقفلة حتى يحين دورها.</p></div><button type="button" class="btn btn-secondary" :disabled="form.levels.length >= 3" @click="addLevel">إضافة مستوى جديد</button></div>
      <article v-for="(level, index) in form.levels" :key="level.level" class="level-editor">
        <div class="levels-head"><h3>صفحة الصندوق · المستوى {{ level.level }}</h3><button v-if="form.levels.length > 3" type="button" class="btn btn-danger" @click="removeLevel(index)">حذف</button></div>
        <label class="field">اسم المستوى<input v-model="level.title" maxlength="80"></label>
        <label class="field">مميزات هذا المستوى (كل ميزة في سطر)<textarea v-model="level.featuresText" rows="3" maxlength="1200" placeholder="مكافآت حصرية\nهدايا خاصة بهذا المستوى\nمؤثر فتح مميز"></textarea></label>
        <div class="grid2">
          <label class="field">نوع الهدف<select v-model="level.goalMode"><option value="coins">كوينزات</option><option value="diamonds">ماسات</option><option value="both">كوينزات وماسات</option></select></label>
          <label class="field">عدد الهدايا المطلوبة<input v-model.number="level.giftCount" type="number" min="1"></label>
          <label class="field">هدف الكوينزات<input v-model.number="level.coinTarget" type="number" min="0"></label>
          <label class="field">هدف الماسات<input v-model.number="level.diamondTarget" type="number" min="0"></label>
        </div>
        <label class="field">أقسام الهدايا لهذا المستوى
          <div class="category-picks"><label><input v-model="level.giftCategories" type="checkbox" value="normal"> عادية</label><label><input v-model="level.giftCategories" type="checkbox" value="lucky"> حظ</label><label><input v-model="level.giftCategories" type="checkbox" value="cp"> CP</label></div>
        </label>
        <div class="field"><span>الهدايا التي تقبلها صفحة هذا المستوى</span><label class="field">نطاق الهدايا<select v-model="level.giftSelectionMode"><option value="all">كل الهدايا من الأقسام المسموحة</option><option value="selected">هدايا محددة فقط</option></select></label><p class="muted">يمكن أن يقبل المستوى كل الهدايا أو تحدد له هدايا مختلفة عن بقية المستويات.</p><div v-if="level.giftSelectionMode === 'selected'" class="item-picker"><label v-for="gift in catalogGifts" :key="gift.id" class="pick-card" :class="{ selected: (level.giftIds || []).map(String).includes(String(gift.id)) }"><input type="checkbox" :checked="(level.giftIds || []).map(String).includes(String(gift.id))" @change="toggleLevelGift(level, gift.id, $event.target.checked)"><video v-if="isVideo(itemImage(gift))" :src="assetUrl(itemImage(gift))" muted autoplay loop playsinline /><img v-else-if="itemImage(gift)" :src="assetUrl(itemImage(gift))" :alt="itemName(gift)" loading="lazy"><span v-else class="pick-placeholder">🎁</span><strong>{{ itemName(gift) }}</strong><small>{{ Number(gift.coinPrice ?? gift.price ?? 0).toLocaleString() }} كوينز</small></label></div></div>
        <label class="field">تصميم الصندوق / صورة المستوى<input v-model="level.imageUrl" placeholder="رابط صورة أو حركة الصندوق"></label>
        <div v-if="level.imageUrl" class="level-art-preview"><video v-if="isVideo(level.imageUrl)" :src="assetUrl(level.imageUrl)" muted autoplay loop playsinline /><img v-else :src="assetUrl(level.imageUrl)" alt="معاينة تصميم الصندوق"><span>معاينة شكل هذا المستوى داخل الروم</span></div>
        <label class="field">المدة الافتراضية لمكافآت هذا المستوى (بالأيام، 0 = دائم)<input v-model.number="level.rewardDays" type="number" min="0" max="3650" step="1"></label><p class="muted">تُستخدم هذه المدة كقيمة افتراضية للمكافآت القديمة أو التي لم تُحدد لها مدة مستقلة. يمكنك تحديد مدة منفصلة لكل إطار أو شارة. VIP مدته من خانة الأيام، والعملات والماسات أرصدة دائمة.</p><div class="reward-editor">
          <div class="levels-head"><strong>مكافآت مفجّر الصندوق</strong><span class="muted">{{ (level.rewards || []).length }} مكافأة محددة</span><button type="button" class="btn btn-secondary" @click="addReward(level)">إضافة مكافأة</button></div>
          <div v-for="(reward, rewardIndex) in level.rewards" :key="rewardIndex" class="reward-line">
            <label class="field">نوع المكافأة<select v-model="reward.type"><option value="gift">هدية من كتالوج الهدايا</option><option value="coins">كوينزات</option><option value="diamonds">ماسات</option><option value="vip">VIP</option><option value="frame">إطار</option><option value="badge">شارة</option></select></label>
            <label v-if="!['frame','badge'].includes(reward.type)" class="field">{{ reward.type === 'vip' ? 'مدة VIP (بالأيام)' : 'الكمية' }}<input v-model.number="reward.amount" type="number" min="1"></label>
            <label v-if="['frame','badge'].includes(reward.type)" class="field">مدة صلاحية هذه المكافأة (بالأيام، 0 = دائم)<input v-model.number="reward.durationDays" type="number" min="0" max="3650" step="1"></label>
            <small v-if="['frame','badge'].includes(reward.type)" class="muted">الإطار أو الشارة تُمنح كملكية واحدة؛ يمكنك إضافة أكثر من مكافأة تجميلية باختيار عناصر مختلفة. المدة هنا مستقلة لكل مكافأة.</small>
            <label v-if="reward.type === 'gift'" class="field">الهدية الفعلية<select v-model="reward.itemId"><option value="">اختر الهدية</option><option v-for="gift in catalogGifts" :key="gift.id" :value="gift.id">{{ itemName(gift) }}</option></select></label>
            <label v-if="reward.type === 'frame'" class="field">الإطار<select v-model="reward.itemId"><option value="">اختر الإطار</option><option v-for="item in frameItems" :key="item.id" :value="item.id">{{ itemName(item) }}</option></select></label>
            <label v-else-if="reward.type === 'vip'" class="field">خطة VIP<select v-model="reward.itemId"><option value="">اختر خطة VIP</option><option v-for="plan in catalogVipPlans" :key="plan.id" :value="plan.id">{{ plan.name || ('VIP '+plan.level) }}</option></select></label>
            <label v-else-if="reward.type === 'badge'" class="field">الشارة<select v-model="reward.itemId"><option value="">اختر الشارة</option><option v-for="badge in catalogBadges" :key="badge.id" :value="badge.id">{{ badge.name }}</option></select></label>
            <button type="button" class="btn btn-danger" @click="level.rewards.splice(rewardIndex,1)">إزالة</button>
          </div>
          <p class="muted">معاينة جميع الجوائز التي سيحصل عليها مفجّر الصندوق عند اكتمال هذا المستوى:</p>
          <div class="preview-rewards"><div v-for="(reward, n) in level.rewards" :key="n" class="preview-reward-card"><video v-if="isVideo(rewardPreviewImage(reward))" :src="assetUrl(rewardPreviewImage(reward))" muted autoplay loop playsinline /><img v-else-if="rewardPreviewImage(reward)" :src="assetUrl(rewardPreviewImage(reward))" :alt="rewardLabel(reward)" loading="lazy"><span v-else class="preview-reward-icon">{{ rewardIcon(reward.type) }}</span><strong>{{ rewardLabel(reward) }}</strong><small>{{ ['frame','badge'].includes(reward.type) ? ((Number(reward.durationDays ?? level.rewardDays)||0) ? (Number(reward.durationDays ?? level.rewardDays)+' يوم') : 'دائم') : (reward.type === 'vip' ? (Number(reward.amount)||1)+' يوم' : (['coins','diamonds'].includes(reward.type) ? Number(reward.amount||1).toLocaleString() : '× '+(Number(reward.amount)||1))) }}</small></div><span v-if="!level.rewards.length" class="muted">لم تتم إضافة مكافآت.</span></div>
        </div>
      </article>
    </section>
    <p class="muted foot">يعرض كل مستوى جميع الجوائز المحددة للمعاينة، ثم يمنح الخادم جميع المكافآت المحددة للمستوى للفائز بعد تأكيد اكتمال الهدف. تُسجّل الهدية المجانية في سجل هدايا الفائز وتصل إليه بإشعار ومؤثر داخل الروم.</p>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { supportChestApi, giftsApi, cosmeticsApi, vipApi, medalsApi, uploadsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'
const form = reactive({ explosionEffect:{enabled:true,mediaUrl:'',soundUrl:'',width:100,height:100,startX:50,startY:46,endX:50,endY:50,durationMs:1800,opacity:100}, toast:{enabled:true,backgroundUrl:'',height:104,yOffset:72,titleSizeSp:17,bodySizeSp:14,giftSizeDp:58,showWinnerName:true}, enabled:false, iconVisible:true, soundEnabled:true, goalMode:'coins', coinTarget:100000, diamondTarget:10000, giftIds:[], giftSelectionMode:'all', giftCategories:['normal','lucky','cp'], levels:[{level:1,title:'الصندوق الذهبي',goalMode:'coins',coinTarget:100000,diamondTarget:0,giftCount:1,giftCategories:['normal','lucky','cp'],giftSelectionMode:'all',giftIds:[],imageUrl:'',featuresText:'',rewardDays:0,rewards:[{type:'coins',amount:5000,itemId:''}]}], rewardType:'none', rewardItemId:'', rewardAmount:0, instructions:'كل هدية مؤهلة تساهم في هدف الصندوق. عند اكتماله ينفجر الصندوق وتُمنح جميع المكافآت المحددة لمفجّر الصندوق تلقائياً.' })
const catalogGifts=ref([]), catalogCosmetics=ref([]), catalogVipPlans=ref([]), catalogBadges=ref([]), catalogLoading=ref(false)
const saving=ref(false), error=ref(''), notice=ref(''), uploadingEffect=ref(false), uploadingSound=ref(false), uploadingToastBackground=ref(false), effectUploadError=ref('')
function unwrap(r){const d=r?.data?.data ?? r?.data ?? r;return Array.isArray(d)?d:(Array.isArray(d?.items)?d.items:(Array.isArray(d?.data)?d.data:[]))}
function itemName(item){return item?.nameAr || item?.labelAr || item?.name || item?.title || item?.code || 'عنصر'}
function itemImage(item){return item?.iconUrl || item?.previewUrl || item?.imageUrl || item?.badgeUrl || item?.animationUrl || item?.assetUrl || item?.url || ''}
function assetUrl(value){return resolveAsset(value)}
async function uploadExplosionEffect(event){
  const input=event?.target;const file=input?.files?.[0];if(!file)return;
  effectUploadError.value='';
  const allowed=/\.(png|jpe?g|webp|gif|webm|mp4)$/i.test(String(file.name||''));
  if(!allowed){effectUploadError.value='الملف غير مدعوم. اختر صورة أو GIF أو WebM أو MP4.';input.value='';return}
  if(file.size>50*1024*1024){effectUploadError.value='حجم الملف يتجاوز الحد المسموح (50 ميغابايت).';input.value='';return}
  uploadingEffect.value=true;
  try{const result=await uploadsApi.upload(file);if(result?.error)throw result.error;const uploaded=result?.data;const url=String(uploaded?.url||uploaded?.path||'').trim();if(!url)throw new Error('الخادم لم يُرجع مسار الملف بعد الرفع.');form.explosionEffect.mediaUrl=url;form.explosionEffect.enabled=true;notice.value='تم رفع المؤثر وتفعيله. اضغط حفظ الإعدادات ليظهر لجميع الموجودين في الرومات.'}
  catch(e){effectUploadError.value='فشل رفع مؤثر الانفجار: '+String(e?.message||e?.response?.data?.message||'خطأ غير معروف')}
  finally{uploadingEffect.value=false;input.value=''}
}
async function uploadExplosionSound(event){
  const input=event?.target,file=input?.files?.[0];if(!file)return;
  effectUploadError.value='';
  if(!/\.(mp3|wav|m4a|aac)$/i.test(String(file.name||''))){effectUploadError.value='اختر ملف صوت MP3 أو WAV أو M4A أو AAC.';input.value='';return}
  if(file.size>20*1024*1024){effectUploadError.value='حجم ملف الصوت يتجاوز 20 ميغابايت.';input.value='';return}
  uploadingSound.value=true;
  try{const result=await uploadsApi.upload(file);if(result?.error)throw result.error;const uploaded=result?.data;const url=String(uploaded?.url||uploaded?.path||'').trim();if(!url)throw new Error('الخادم لم يُرجع مسار الصوت.');form.explosionEffect.soundUrl=url;form.explosionEffect.enabled=true;notice.value='تم رفع صوت الانفجار وتفعيل المؤثر المخصص. احفظ الإعدادات لتفعيله.'}
  catch(e){effectUploadError.value='فشل رفع صوت الانفجار: '+String(e?.message||'خطأ غير معروف')}
  finally{uploadingSound.value=false;input.value=''}
}
async function uploadToastBackground(event){
  const input=event?.target,file=input?.files?.[0];if(!file)return;
  if(!/\.(png|jpe?g|webp)$/i.test(String(file.name||''))){error.value='خلفية التوست يجب أن تكون PNG أو JPG أو WebP.';input.value='';return}
  if(file.size>15*1024*1024){error.value='حجم خلفية التوست يتجاوز 15 ميغابايت.';input.value='';return}
  uploadingToastBackground.value=true;
  try{const result=await uploadsApi.upload(file);if(result?.error)throw result.error;const uploaded=result?.data;const url=String(uploaded?.url||uploaded?.path||'').trim();if(!url)throw new Error('الخادم لم يُرجع مسار الخلفية.');form.toast.backgroundUrl=url;notice.value='تم رفع خلفية التوست. احفظ الإعدادات لتفعيلها.'}
  catch(e){error.value='فشل رفع خلفية التوست: '+String(e?.message||'خطأ غير معروف')}
  finally{uploadingToastBackground.value=false;input.value=''}
}
function isVideo(value){return /\.(mp4|webm)(\?|$)/i.test(String(value||''))}
function addLevel(){if(form.levels.length>=3){error.value='تم الوصول إلى الحد الأقصى: ثلاثة مستويات مستقلة.';return}const next=Math.max(0,...form.levels.map(x=>Number(x.level)||0))+1;form.levels.push({level:next,title:'المستوى '+next,goalMode:'coins',coinTarget:next*100000,diamondTarget:0,giftCount:1,giftCategories:[...(form.giftCategories||['normal'])],giftSelectionMode:'all',giftIds:[],imageUrl:'',featuresText:'',rewardDays:0,rewards:[{type:'coins',amount:5000*next,itemId:''}]})}
function removeLevel(index){form.levels.splice(index,1);form.levels.forEach((x,i)=>x.level=i+1)}
function addReward(level){level.rewards=level.rewards||[];level.rewards.push({type:'coins',amount:1000,itemId:'',durationDays:Math.max(0,Math.floor(Number(level.rewardDays)||0))})}
function toggleLevelGift(level,id,checked){const key=String(id);level.giftIds=checked?[...new Set([...(level.giftIds||[]).map(String),key])]: (level.giftIds||[]).map(String).filter(x=>x!==key)}
function rewardCatalogItem(r){const id=String(r?.itemId||'');if(!id)return null;if(r.type==='gift')return catalogGifts.value.find(x=>String(x.id)===id)||null;if(r.type==='frame')return frameItems.value.find(x=>String(x.id)===id)||null;if(r.type==='vip')return catalogVipPlans.value.find(x=>String(x.id)===id)||null;if(r.type==='badge')return catalogBadges.value.find(x=>String(x.id)===id)||null;return null}
function rewardPreviewImage(r){const item=rewardCatalogItem(r);return item?itemImage(item):''}
function rewardIcon(type){return ({gift:'🎁',coins:'🪙',diamonds:'💎',vip:'👑',frame:'🖼️',badge:'🏅'})[type]||'🎁'}
function rewardLabel(r){const names={gift:'هدية',coins:'كوينزات',diamonds:'ماسات',vip:'VIP',frame:'إطار',badge:'شارة'};const item=rewardCatalogItem(r);return (item?itemName(item):(names[r.type]||r.type||'مكافأة'))+' × '+(Number(r.amount)||1)}
function toggleGift(id, checked){const key=String(id);form.giftIds=checked?[...new Set([...(form.giftIds||[]).map(String),key])]: (form.giftIds||[]).map(String).filter(x=>x!==key)}
const frameItems = computed(()=>catalogCosmetics.value.filter(x=>{const type=String(x.type||'').toLowerCase();const category=String(x.category||x.meta?.category||x.meta?.type||'').toLowerCase();return /frame|vip_badge|host_badge/.test(type)||/frame|vip_badge|host_badge/.test(category)}))
const rewardItems = computed(()=> {
  const all=catalogCosmetics.value
  if(form.rewardType==='gift')return catalogGifts.value
  if(form.rewardType==='entry_effect')return all.filter(x=>String(x.type||x.category||'').toLowerCase().includes('entry'))
  if(form.rewardType==='frame')return all.filter(x=>/frame|vip_badge/i.test(String(x.type||x.category||'')))
  if(form.rewardType==='badge')return catalogBadges.value
  return []
})
onMounted(async()=>{
  try{const r=await supportChestApi.getConfig();if(r?.error)throw r.error;const d=r?.data?.data ?? r?.data;if(!d||typeof d!=='object')throw new Error('Empty support chest config');Object.assign(form,d);form.explosionEffect={enabled:true,mediaUrl:'',soundUrl:'',width:100,height:100,startX:50,startY:46,endX:50,endY:50,durationMs:1800,opacity:100,...(d.explosionEffect||{})};form.toast={enabled:true,backgroundUrl:'',height:104,yOffset:72,titleSizeSp:17,bodySizeSp:14,giftSizeDp:58,showWinnerName:true,...(d.toast||{})};form.giftIds=Array.isArray(d.giftIds)?d.giftIds.map(String):[];form.giftSelectionMode=String(d.giftSelectionMode||(form.giftIds.length?'selected':'all'));form.giftCategories=Array.isArray(d.giftCategories)?d.giftCategories:['normal','lucky','cp'];form.levels=Array.isArray(d.levels)&&d.levels.length?d.levels:[{level:1,title:'الصندوق الذهبي',goalMode:d.goalMode||'coins',coinTarget:d.coinTarget||100000,diamondTarget:d.diamondTarget||0,giftCount:1,giftCategories:form.giftCategories,giftSelectionMode:form.giftSelectionMode,giftIds:form.giftIds,imageUrl:'',featuresText:'',rewards:[{type:d.rewardType||'coins',amount:d.rewardAmount||1000,itemId:d.rewardItemId||''}]}];while(form.levels.length<3){const n=form.levels.length+1;form.levels.push({level:n,title:'الصندوق - المستوى '+n,goalMode:'coins',coinTarget:n*100000,diamondTarget:0,giftCount:1,giftCategories:[...form.giftCategories],giftSelectionMode:'all',giftIds:[],imageUrl:'',featuresText:'',rewardDays:0,rewards:[{type:'coins',amount:5000*n,itemId:''}]})}form.levels=form.levels.slice(0,3);form.soundEnabled=d.soundEnabled!==false;form.levels.forEach(level=>{level.rewardDays=Math.max(0,Math.floor(Number(level.rewardDays)||0));level.featuresText=String(level.featuresText || (Array.isArray(level.features)?level.features.join('\n'):'') || '');level.giftIds=Array.isArray(level.giftIds)?level.giftIds.map(String):[];level.giftSelectionMode=String(level.giftSelectionMode||(level.giftIds.length?'selected':'all'));level.giftCategories=Array.isArray(level.giftCategories)?level.giftCategories:[...form.giftCategories];level.rewards=(Array.isArray(level.rewards)?level.rewards:[]).map(reward=>({...reward,durationDays:Math.max(0,Math.min(3650,Math.floor(Number(reward.durationDays ?? level.rewardDays)||0)))}))})}
  catch(e){error.value='تعذّر تحميل إعدادات صندوق الدعم من الخادم: '+String(e?.message||'خطأ غير معروف')}
  catalogLoading.value=true
  try{const [g,c,vip,badges]=await Promise.all([giftsApi.list({limit:500}),cosmeticsApi.adminList({limit:500}),vipApi.plans(),medalsApi.list()]);catalogGifts.value=unwrap(g).filter(x=>x.isActive!==false);catalogCosmetics.value=unwrap(c).filter(x=>x.isActive!==false);catalogVipPlans.value=unwrap(vip).filter(x=>x.isActive!==false);catalogBadges.value=unwrap(badges).filter(x=>x.isActive!==false)}
  catch(e){if(!error.value)error.value='تعذّر تحميل كتالوج الهدايا والإطارات: '+String(e?.message||'خطأ غير معروف')}
  finally{catalogLoading.value=false}
})
function canonicalLevel(level){
  const uniqueStrings=(values)=>[...new Set((Array.isArray(values)?values:[]).map(v=>String(v??'').trim()).filter(Boolean))];
  return {
    level:Number(level?.level)||0,
    title:String(level?.title||'').slice(0,80),
    goalMode:String(level?.goalMode||'coins'),
    coinTarget:Math.max(0,Math.floor(Number(level?.coinTarget)||0)),
    diamondTarget:Math.max(0,Math.floor(Number(level?.diamondTarget)||0)),
    giftCount:Math.max(1,Math.min(100000,Math.floor(Number(level?.giftCount)||1))),
    giftCategories:uniqueStrings(level?.giftCategories).sort(),
    giftSelectionMode:String(level?.giftSelectionMode||((level?.giftIds||[]).length?'selected':'all')),
    giftIds:uniqueStrings(level?.giftIds).sort(),
    imageUrl:String(level?.imageUrl||'').trim().slice(0,2048),
    features:uniqueStrings(level?.features ?? String(level?.featuresText||'').split(/\r?\n/)).slice(0,20),
    rewardDays:Math.max(0,Math.min(3650,Math.floor(Number(level?.rewardDays)||0))),
    rewards:(Array.isArray(level?.rewards)?level.rewards:[]).map(r=>({
      type:String(r?.type||''),
      amount:['frame','badge'].includes(String(r?.type))?1:Math.max(1,Math.floor(Number(r?.amount)||1)),
      itemId:String(r?.itemId||'').trim().slice(0,160),
      durationDays:Math.max(0,Math.min(3650,Math.floor(Number(r?.durationDays ?? level?.rewardDays)||0))),
    })),
  };
}
function canonicalConfig(config){
  const uniqueStrings=(values)=>[...new Set((Array.isArray(values)?values:[]).map(v=>String(v??'').trim()).filter(Boolean))];
  return {
    enabled:config?.enabled===true,
    iconVisible:config?.iconVisible!==false,
    soundEnabled:config?.soundEnabled!==false,
    toast:{enabled:config?.toast?.enabled!==false,backgroundUrl:String(config?.toast?.backgroundUrl||'').trim().slice(0,2048),height:Math.max(60,Math.min(240,Math.floor(Number(config?.toast?.height)||104))),yOffset:Math.max(0,Math.min(300,Math.floor(Number(config?.toast?.yOffset)||0))),titleSizeSp:Math.max(10,Math.min(36,Number(config?.toast?.titleSizeSp)||17)),bodySizeSp:Math.max(8,Math.min(30,Number(config?.toast?.bodySizeSp)||14)),giftSizeDp:Math.max(24,Math.min(120,Math.floor(Number(config?.toast?.giftSizeDp)||58))),showWinnerName:config?.toast?.showWinnerName!==false},
    goalMode:String(config?.goalMode||'coins'),
    coinTarget:Math.max(0,Math.floor(Number(config?.coinTarget)||0)),
    diamondTarget:Math.max(0,Math.floor(Number(config?.diamondTarget)||0)),
    giftIds:uniqueStrings(config?.giftIds).sort(),
    giftSelectionMode:String(config?.giftSelectionMode||((config?.giftIds||[]).length?'selected':'all')),
    giftCategories:uniqueStrings(config?.giftCategories).sort(),
    rewardType:String(config?.rewardType||'none'),
    rewardItemId:String(config?.rewardItemId||'').trim(),
    rewardAmount:Math.max(0,Math.floor(Number(config?.rewardAmount)||0)),
    instructions:String(config?.instructions||'').slice(0,3000),
    explosionEffect:{enabled:config?.explosionEffect?.enabled===true,mediaUrl:String(config?.explosionEffect?.mediaUrl||'').trim().slice(0,2048),soundUrl:String(config?.explosionEffect?.soundUrl||'').trim().slice(0,2048),width:Math.max(10,Math.min(180,Number(config?.explosionEffect?.width)||100)),height:Math.max(10,Math.min(180,Number(config?.explosionEffect?.height)||100)),startX:Math.max(0,Math.min(100,Number(config?.explosionEffect?.startX)||0)),startY:Math.max(0,Math.min(100,Number(config?.explosionEffect?.startY)||0)),endX:Math.max(0,Math.min(100,Number(config?.explosionEffect?.endX)||0)),endY:Math.max(0,Math.min(100,Number(config?.explosionEffect?.endY)||0)),durationMs:Math.max(400,Math.min(8000,Math.floor(Number(config?.explosionEffect?.durationMs)||1800))),opacity:Math.max(10,Math.min(100,Number(config?.explosionEffect?.opacity)||100))},
    levels:(Array.isArray(config?.levels)?config.levels:[]).map(canonicalLevel),
  };
}
async function save(){
  saving.value=true;error.value='';notice.value='';
  try{
    const payload={...form,giftIds:(form.giftIds||[]).map(String),giftSelectionMode:form.giftSelectionMode||'all',giftCategories:(form.giftCategories||[]).map(String),levels:(form.levels||[]).map((x,i)=>({...x,level:i+1,giftIds:(x.giftIds||[]).map(String),giftSelectionMode:x.giftSelectionMode||((x.giftIds||[]).length?'selected':'all'),giftCategories:(x.giftCategories||[]).map(String),features:String(x.featuresText||'').split(/\r?\n/).map(v=>v.trim()).filter(Boolean),rewardDays:Math.max(0,Math.min(3650,Math.floor(Number(x.rewardDays)||0))),rewards:(x.rewards||[]).map(r=>({type:String(r.type||''),amount:['frame','badge'].includes(String(r.type))?1:Math.max(1,Math.floor(Number(r.amount)||1)),itemId:String(r.itemId||'').trim(),durationDays:Math.max(0,Math.min(3650,Math.floor(Number(r.durationDays ?? x.rewardDays)||0)))}))}))};
    const result=await supportChestApi.saveConfig(payload);
    if(result?.error)throw result.error;
    const saved=result?.data;
    if(!saved||!Array.isArray(saved.levels)||saved.levels.length!==3)throw new Error('الخادم لم يُرجع نسخة الإعدادات المحفوظة كاملة؛ لم نعرض نجاحاً غير مؤكد.');
    if(JSON.stringify(canonicalConfig(saved))!==JSON.stringify(canonicalConfig(payload)))throw new Error('الخادم حفظ نسخة مختلفة عن الإعدادات المرسلة. لم نعرض نجاحاً غير مؤكد.');
    Object.assign(form,saved);
    form.giftIds=Array.isArray(saved.giftIds)?saved.giftIds.map(String):[];
    form.giftCategories=Array.isArray(saved.giftCategories)?saved.giftCategories.map(String):[];
    form.levels=(saved.levels||[]).map(level=>({...level,featuresText:String(level.featuresText||(Array.isArray(level.features)?level.features.join('\n'):'')||''),giftIds:Array.isArray(level.giftIds)?level.giftIds.map(String):[],giftSelectionMode:String(level.giftSelectionMode||((level.giftIds||[]).length?'selected':'all')),giftCategories:Array.isArray(level.giftCategories)?level.giftCategories.map(String):[...form.giftCategories],rewards:Array.isArray(level.rewards)?level.rewards:[]}));
    notice.value='تم حفظ إعدادات صندوق الدعم والتحقق من النسخة المحفوظة بنجاح.';
  }catch(e){error.value='فشل حفظ إعدادات صندوق الدعم: '+String(e?.response?.data?.message||e?.message||'خطأ غير معروف')}
  finally{saving.value=false}
}
</script>
<style scoped>
.page-shell{max-width:980px;margin:auto;padding:22px}.page-header{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}.page-header h1{margin:0 0 5px}.page-header p,.muted{color:var(--text-muted,#888);font-size:13px}.card{background:var(--surface,#fff);border:1px solid var(--border,#ddd);border-radius:16px;margin-bottom:14px}.card h2{font-size:17px;margin:0 0 16px}.switchrow{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:12px 0;border-bottom:1px solid var(--border,#eee)}.switchrow span{display:grid;gap:4px}.switchrow small{color:var(--text-muted,#888)}.field{display:grid;gap:7px;margin:15px 0;font-weight:600}.field input,.field select,.field textarea{width:100%;padding:10px 12px;border-radius:9px;border:1px solid var(--border,#ccc);background:var(--surface,#fff);color:inherit}.grid2{display:grid;grid-template-columns:1fr 1fr;gap:14px}.foot{line-height:1.7}.category-picks{display:flex;gap:18px;flex-wrap:wrap;padding:10px 0}.category-picks label{display:flex;align-items:center;gap:7px}.levels-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.level-editor{border:1px solid var(--border,#ddd);border-radius:13px;padding:14px;margin:12px 0}.level-art-preview{display:flex;align-items:center;gap:12px;margin:8px 0 14px;padding:10px;border:1px dashed var(--border,#bbb);border-radius:12px}.effect-preview{display:flex;align-items:center;justify-content:center;min-height:150px;margin:10px 0 16px;border:1px dashed var(--border,#bbb);border-radius:12px;background:radial-gradient(circle,#3a2d19,#11121d);overflow:hidden;color:#ffe2a0}.effect-preview img,.effect-preview video{max-width:100%;max-height:220px;object-fit:contain}.level-art-preview img,.level-art-preview video{width:76px;height:76px;object-fit:contain;border-radius:10px;background:#171724}.level-art-preview span{font-size:12px;color:var(--text-muted,#888)}.reward-editor{background:var(--surface-alt,#f7f7f7);padding:12px;border-radius:12px}.reward-line{display:grid;grid-template-columns:1fr 1fr 1.5fr auto;gap:10px;align-items:end;border-bottom:1px solid var(--border,#ddd);padding:8px 0}.preview-rewards{display:grid;grid-template-columns:repeat(auto-fill,minmax(112px,1fr));gap:9px}.preview-reward-card{display:flex;min-width:0;min-height:112px;flex-direction:column;align-items:center;justify-content:center;gap:6px;padding:10px;border:1px solid var(--border,#ddd);border-radius:12px;background:var(--surface,#fff);text-align:center}.preview-reward-card img,.preview-reward-card video{width:52px;height:52px;object-fit:contain}.preview-reward-card strong{font-size:12px;overflow-wrap:anywhere}.preview-reward-card small{font-size:11px;color:var(--text-muted,#888)}.preview-reward-icon{display:grid;place-items:center;width:52px;height:52px;font-size:30px}.item-picker{display:grid;grid-template-columns:repeat(auto-fill,minmax(112px,1fr));gap:10px}.pick-card{position:relative;display:flex;min-width:0;flex-direction:column;align-items:center;gap:6px;padding:10px;border:1px solid var(--border,#ddd);border-radius:12px;cursor:pointer;text-align:center;background:var(--surface,#fff)}.pick-card.selected{border-color:#35a7e6;box-shadow:0 0 0 2px #35a7e622}.pick-card input{align-self:flex-start}.pick-card img,.pick-card video{width:62px;height:62px;object-fit:contain}.pick-card strong{font-size:12px;overflow-wrap:anywhere}.pick-card small{font-size:11px;color:var(--text-muted,#888)}.pick-placeholder{display:grid;place-items:center;width:62px;height:62px;font-size:30px}@media(max-width:650px){.page-header{align-items:flex-start;flex-direction:column}.grid2,.reward-line{grid-template-columns:1fr}.levels-head{align-items:flex-start;flex-wrap:wrap}}
</style>