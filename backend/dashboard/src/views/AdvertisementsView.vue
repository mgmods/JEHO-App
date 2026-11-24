<template>
  <div>
    <PageHeader title="إدارة إعلانات الرومات" subtitle="تحكم مركزي بالإرسال والطابور وشريط الإعلانات" />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />

    <div v-else class="row g-3">
      <div class="col-12">
        <div class="glass p-3">
          <div class="mb-3 p-3 rounded" :style="{background: form.background || 'linear-gradient(90deg,#21142d,#32184c)', backgroundImage: form.backgroundImage ? 'url(' + form.backgroundImage + ')' : undefined, backgroundSize: 'cover', backgroundPosition: 'center'}">
            <div class="small">معاينة مباشرة</div>
            <strong>{{ queue[0]?.senderName || 'اسم المرسل' }}</strong>
            <span class="ms-2">{{ queue[0]?.text || 'نص الإعلان سيظهر هنا بشكل متحرك داخل التطبيق' }}</span>
          </div>
          <div class="d-flex justify-content-between align-items-center mb-3">
            <div>
              <h3 class="h6 mb-1">التحكم العام</h3>
              <div class="small text-muted">التغييرات تنعكس مباشرة على الرومات بدون تحديث APK.</div>
            </div>
            <button class="btn btn-aurora btn-sm" :disabled="saving" @click="save">حفظ الإعدادات</button>
          </div>
          <div class="row g-3">
            <div class="col-md-3 form-check form-switch">
              <input id="adsEnabled" v-model="form.enabled" class="form-check-input" type="checkbox">
              <label class="form-check-label" for="adsEnabled">تشغيل النظام</label>
            </div>
            <div class="col-md-3 form-check form-switch">
              <input id="paused" v-model="form.paused" class="form-check-input" type="checkbox">
              <label class="form-check-label" for="paused">إيقاف الطابور مؤقتاً</label>
            </div>
            <div class="col-md-3 form-check form-switch">
              <input id="sendEnabled" v-model="form.sendButtonEnabled" class="form-check-input" type="checkbox">
              <label class="form-check-label" for="sendEnabled">إظهار زر إرسال إعلان</label>
            </div>
            <div class="col-md-3">
              <label class="form-label small">منطقة إعادة ضبط الحد اليومي (IANA)</label>
              <input v-model="form.resetTimezone" type="text" placeholder="Asia/Riyadh" class="form-control" dir="ltr">
            </div>
            <div class="col-md-3">
              <label class="form-label small">الحد اليومي للعضو</label>
              <input v-model.number="form.dailyLimit" type="number" min="0" max="1000" class="form-control">
            </div>
            <div class="col-md-3">
              <label class="form-label small">أقصى عدد منتظرين</label>
              <input v-model.number="form.maxQueueSize" type="number" min="1" max="5000" class="form-control">
            </div>
            <div class="col-md-3">
              <label class="form-label small">الحد الأقصى للأحرف</label>
              <input v-model.number="form.maxTextLength" type="number" min="1" max="500" class="form-control">
            </div>
          </div>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">إدارة الكلمات المحظورة</h3>
          <label class="form-label small">كلمة واحدة أو أكثر، مفصولة بفواصل</label>
          <textarea v-model="bannedWords" class="form-control" rows="5" dir="rtl" placeholder="كلمة1, كلمة2, كلمة3"></textarea>
          <div class="small text-muted mt-2">الفحص يتم على السيرفر قبل دخول الإعلان للطابور.</div>
          <button class="btn btn-outline-light btn-sm mt-3" :disabled="savingWords" @click="saveWords">حفظ الكلمات</button>
        </div>
      </div>

      <div class="col-lg-6">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">شريط الإعلان</h3>
          <div class="row g-2">
            <div class="col-6"><label class="form-label small">الموضع</label><select v-model="form.position" class="form-select"><option value="safe-top">أعلى آمن</option><option value="safe-bottom">أسفل آمن</option><option value="custom-safe">موضع مخصص آمن</option></select></div>
            <div class="col-6"><label class="form-label small">الاتجاه</label><select v-model="form.direction" class="form-select"><option value="rtl">RTL</option><option value="ltr">LTR</option></select></div>
            <div class="col-6"><label class="form-label small">نوع الانتقال</label><select v-model="form.transitionType" class="form-select"><option value="slide">انزلاق</option><option value="fade">تلاشي</option></select></div>
            <div class="col-6"><label class="form-label small">قالب الشريط</label><select v-model="form.template" class="form-select"><option value="premium-v1">فاخر</option><option value="compact">مختصر</option><option value="gold">ذهبي</option></select></div>
            <div class="col-6"><label class="form-label small">مدة العرض ms</label><input v-model.number="form.displayDurationMs" type="number" min="1000" max="120000" class="form-control"></div>
            <div class="col-6"><label class="form-label small">الفاصل ms</label><input v-model.number="form.gapMs" type="number" min="0" max="120000" class="form-control"></div>
            <div class="col-6"><label class="form-label small">سرعة الدخول px/s</label><input v-model.number="form.entrySpeedPxPerSec" type="number" min="20" max="800" class="form-control"></div>
            <div class="col-6"><label class="form-label small">سرعة الخروج px/s</label><input v-model.number="form.exitSpeedPxPerSec" type="number" min="20" max="800" class="form-control"></div>
          </div>
          <div class="form-check form-switch mt-3"><input id="reduceMotion" v-model="form.reducedMotionStatic" class="form-check-input" type="checkbox"><label class="form-check-label" for="reduceMotion">عرض ثابت عند تقليل الحركة</label></div>
        </div>
      </div>

      <div class="col-lg-4">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">المظهر</h3>
          <div class="row g-2">
            <div class="col-6"><label class="form-label small">ارتفاع dp</label><input v-model.number="form.heightDp" type="number" class="form-control"></div>
            <div class="col-6"><label class="form-label small">الصورة dp</label><input v-model.number="form.avatarDp" type="number" class="form-control"></div>
            <div class="col-6"><label class="form-label small">اسم sp</label><input v-model.number="form.nameSp" type="number" class="form-control"></div>
            <div class="col-6"><label class="form-label small">النص sp</label><input v-model.number="form.textSp" type="number" class="form-control"></div>
            <div class="col-6"><label class="form-label small">لون الاسم</label><input v-model="form.nameColor" type="text" class="form-control" dir="ltr"></div>
            <div class="col-6"><label class="form-label small">لون النص</label><input v-model="form.textColor" type="text" class="form-control" dir="ltr"></div>
            <div class="col-6"><label class="form-label small">لون/خلفية الشريط</label><input v-model="form.background" type="text" class="form-control" dir="ltr"></div>
            <div class="col-6"><label class="form-label small">صورة الخلفية URL</label><input v-model="form.backgroundImage" type="text" class="form-control" dir="ltr"></div>
            <div class="col-6"><label class="form-label small">شفافية الخلفية %</label><input v-model.number="form.backgroundOpacity" type="number" min="0" max="100" class="form-control"></div>
            <div class="col-6"><label class="form-label small">الزوايا dp</label><input v-model.number="form.radiusDp" type="number" min="0" max="40" class="form-control"></div>
            <div class="col-6"><label class="form-label small">المسافة dp</label><input v-model.number="form.spacingDp" type="number" min="0" max="30" class="form-control"></div>
            <div class="col-6"><label class="form-label small">شكل الصورة</label><select v-model="form.avatarShape" class="form-select"><option value="circle">دائرية</option><option value="square">مربعة</option></select></div>
            <div class="col-6"><label class="form-label small">نوع الخط</label><select v-model="form.fontFamily" class="form-select"><option value="default">افتراضي</option><option value="sans">Sans</option><option value="serif">Serif</option><option value="mono">Mono</option></select></div>
          </div>
        </div>
      </div>

      <div class="col-lg-8">
        <div class="glass p-3 h-100">
          <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
            <h3 class="h6 mb-0">طابور الإعلانات وسجل العرض</h3>
            <div class="d-flex gap-2">
              <input v-model="queueSearch" class="form-control form-control-sm" placeholder="بحث بالعضو أو النص أو ID" @keyup.enter="loadQueue">
              <button class="btn btn-outline-light btn-sm" @click="loadQueue">بحث</button>
              <button class="btn btn-outline-light btn-sm" @click="showHistory=!showHistory;loadQueue()">{{ showHistory ? 'عرض الطابور' : 'سجل الإعلانات' }}</button>
            </div>
          </div>
          <div class="table-responsive">
            <table class="table table-dark table-sm align-middle mb-0">
              <thead><tr><th>الحالة</th><th>المرسل</th><th>النص</th><th>الترتيب</th><th>التوقيت</th><th>إجراء</th></tr></thead>
              <tbody>
                <tr v-for="ad in queue" :key="ad.id">
                  <td><span :class="ad.status === 'playing' ? 'badge bg-success' : 'badge bg-secondary'">{{ ad.status === 'playing' ? 'يُعرض الآن' : ad.status === 'queued' ? 'بانتظار العرض' : ad.status }}</span></td><td>{{ ad.senderName }}</td><td class="text-truncate" style="max-width:260px">{{ ad.text }}</td><td>{{ ad.queueOrder }}</td><td>{{ ad.startedAt || ad.createdAt }}</td>
                  <td class="d-flex gap-1"><button v-if="!showHistory && ad.status === 'queued'" class="btn btn-sm btn-outline-warning" @click="prioritize(ad.id)">أولوية</button><button v-if="!showHistory" class="btn btn-sm btn-outline-danger" :disabled="ad.status === 'playing'" @click="remove(ad.id)">حذف</button></td>
                </tr>
                <tr v-if="!queue.length"><td colspan="6" class="text-center text-muted py-4">لا توجد إعلانات</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="col-12">
        <div class="glass p-3">
          <div class="d-flex align-items-center justify-content-between mb-3"><h3 class="h6 mb-0">إحصائيات آخر {{ statsDays }} يوم</h3><select v-model.number="statsDays" class="form-select form-select-sm" style="width:110px" @change="loadStats"><option :value="1">اليوم</option><option :value="7">7 أيام</option><option :value="30">30 يوم</option></select></div>
          <div class="row g-2">
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">الإجمالي</div><div class="h4 mb-0">{{ stats.total || 0 }}</div></div></div>
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">المعروض</div><div class="h4 mb-0">{{ stats.displayed || 0 }}</div></div></div>
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">المرفوض</div><div class="h4 mb-0">{{ stats.rejected || 0 }}</div></div></div>
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">بانتظار العرض</div><div class="h4 mb-0">{{ stats.waiting || 0 }}</div></div></div>
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">المقبول</div><div class="h4 mb-0">{{ stats.accepted || 0 }}</div></div></div>
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">محاولات التكرار</div><div class="h4 mb-0">{{ stats.duplicateAttempts || 0 }}</div></div></div>
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">كلمات محظورة</div><div class="h4 mb-0">{{ stats.blockedwordattempts || stats.blockedWordAttempts || 0 }}</div></div></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { advertisementsApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const loading=ref(true), saving=ref(false), savingWords=ref(false), error=ref(''), success=ref('')
const bannedWords=ref(''), queue=ref([]), stats=ref({}), statsDays=ref(7), queueSearch=ref(''), showHistory=ref(false)
const form=reactive({enabled:true,sendButtonEnabled:true,paused:false,dailyLimit:1,maxQueueSize:500,maxTextLength:180,position:'safe-bottom',direction:'rtl',displayDurationMs:9000,gapMs:1800,entrySpeedPxPerSec:120,exitSpeedPxPerSec:120,reducedMotionStatic:true,heightDp:48,avatarDp:34,nameSp:12,textSp:13,nameColor:'#FFD98A',textColor:'#FFFFFF',background:'',backgroundImage:'',backgroundOpacity:92,radiusDp:18,spacingDp:8,avatarShape:'circle',resetTimezone:'UTC',transitionType:'slide',fontFamily:'default',template:'premium-v1'})
function apply(data){ Object.assign(form,data||{}); }
async function load(){
  loading.value=true; error.value=''
  const [c,q,s,b]=await Promise.all([advertisementsApi.config(),advertisementsApi.queue({limit:100,q:queueSearch.value,history:showHistory.value}),advertisementsApi.stats({days:statsDays.value}),advertisementsApi.bannedWords()])
  if(c.error){error.value=c.error.message||String(c.error);loading.value=false;return}
  apply(c.data?.data??c.data)
  queue.value=q.data?.items||q.data?.data?.items||[]
  stats.value=s.data?.data??s.data??{}
  bannedWords.value=b.data?.words??b.data?.data?.words??''
  loading.value=false
}
async function save(){saving.value=true;const r=await advertisementsApi.saveConfig({...form});saving.value=false;if(r.error){error.value=r.error.message||String(r.error);return}success.value='تم حفظ إعدادات الإعلانات';apply(r.data?.data??r.data)}
async function saveWords(){savingWords.value=true;const r=await advertisementsApi.saveBannedWords(bannedWords.value);savingWords.value=false;if(r.error){error.value=r.error.message||String(r.error);return}success.value='تم حفظ الكلمات المحظورة'}
async function loadQueue(){const r=await advertisementsApi.queue({limit:100,q:queueSearch.value,history:showHistory.value});if(!r.error)queue.value=r.data?.items||r.data?.data?.items||[]}
async function loadStats(){const r=await advertisementsApi.stats({days:statsDays.value});if(!r.error)stats.value=r.data?.data??r.data??{}}
async function prioritize(id){const r=await advertisementsApi.queueAction(id,{action:'priority',queueOrder:0});if(r.error){error.value=r.error.message||String(r.error);return}await loadQueue()}
async function remove(id){const r=await advertisementsApi.queueAction(id,{action:'remove'});if(r.error){error.value=r.error.message||String(r.error);return}await loadQueue()}
onMounted(load)
</script>