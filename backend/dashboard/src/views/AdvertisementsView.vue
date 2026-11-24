<template>
  <div>
    <PageHeader title="إدارة إعلانات الرومات" subtitle="تحكم مركزي بالإرسال والطابور وشريط الإعلانات" />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />

    <div v-else class="row g-3">
      <div class="col-12">
        <div class="glass p-3">
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
              <input id="sendEnabled" v-model="form.sendButtonEnabled" class="form-check-input" type="checkbox">
              <label class="form-check-label" for="sendEnabled">إظهار زر إرسال إعلان</label>
            </div>
            <div class="col-md-3">
              <label class="form-label small">الحد اليومي للعضو</label>
              <input v-model.number="form.dailyLimit" type="number" min="0" max="1000" class="form-control">
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
          </div>
        </div>
      </div>

      <div class="col-lg-8">
        <div class="glass p-3 h-100">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <h3 class="h6 mb-0">طابور الإعلانات</h3>
            <button class="btn btn-outline-light btn-sm" @click="loadQueue">تحديث</button>
          </div>
          <div class="table-responsive">
            <table class="table table-dark table-sm align-middle mb-0">
              <thead><tr><th>الحالة</th><th>المرسل</th><th>النص</th><th>الترتيب</th><th>إجراء</th></tr></thead>
              <tbody>
                <tr v-for="ad in queue" :key="ad.id">
                  <td>{{ ad.status }}</td><td>{{ ad.senderName }}</td><td class="text-truncate" style="max-width:260px">{{ ad.text }}</td><td>{{ ad.queueOrder }}</td>
                  <td><button class="btn btn-sm btn-outline-danger" :disabled="ad.status === 'playing'" @click="remove(ad.id)">حذف</button></td>
                </tr>
                <tr v-if="!queue.length"><td colspan="5" class="text-center text-muted py-4">الطابور فارغ</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="col-12">
        <div class="glass p-3">
          <h3 class="h6 mb-3">إحصائيات آخر {{ statsDays }} يوم</h3>
          <div class="row g-2">
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">الإجمالي</div><div class="h4 mb-0">{{ stats.total || 0 }}</div></div></div>
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">المعروض</div><div class="h4 mb-0">{{ stats.displayed || 0 }}</div></div></div>
            <div class="col-6 col-md-3"><div class="stat-card"><div class="small text-muted">المرفوض</div><div class="h4 mb-0">{{ stats.rejected || 0 }}</div></div></div>
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
const bannedWords=ref(''), queue=ref([]), stats=ref({}), statsDays=7
const form=reactive({enabled:true,sendButtonEnabled:true,dailyLimit:1,maxTextLength:180,position:'safe-bottom',direction:'rtl',displayDurationMs:9000,gapMs:1800,entrySpeedPxPerSec:120,exitSpeedPxPerSec:120,reducedMotionStatic:true,heightDp:48,avatarDp:34,nameSp:12,textSp:13,nameColor:'#FFD98A',textColor:'#FFFFFF})
function apply(data){ Object.assign(form,data||{}); }
async function load(){
  loading.value=true; error.value=''
  const [c,q,s]=await Promise.all([advertisementsApi.config(),advertisementsApi.queue({limit:100}),advertisementsApi.stats({days:statsDays})])
  if(c.error){error.value=c.error.message||String(c.error);loading.value=false;return}
  apply(c.data?.data??c.data)
  queue.value=q.data?.items||q.data?.data?.items||[]
  stats.value=s.data?.data??s.data??{}
  loading.value=false
}
async function save(){saving.value=true;const r=await advertisementsApi.saveConfig({...form});saving.value=false;if(r.error){error.value=r.error.message||String(r.error);return}success.value='تم حفظ إعدادات الإعلانات';apply(r.data?.data??r.data)}
async function saveWords(){savingWords.value=true;const r=await advertisementsApi.saveBannedWords(bannedWords.value);savingWords.value=false;if(r.error){error.value=r.error.message||String(r.error);return}success.value='تم حفظ الكلمات المحظورة'}
async function loadQueue(){const r=await advertisementsApi.queue({limit:100});if(!r.error)queue.value=r.data?.items||r.data?.data?.items||[]}
async function remove(id){const r=await advertisementsApi.queueAction(id,{action:'remove'});if(r.error){error.value=r.error.message||String(r.error);return}await loadQueue()}
onMounted(load)
</script>