<template>
  <div>
    <PageHeader title="إدارة الستوريات" subtitle="تحكم مباشر بميزة الستوري لجميع مستخدمي JEHO دون تحديث التطبيق">
      <template #actions>
        <button class="btn btn-outline-light btn-sm" :disabled="loading || saving" @click="load">تحديث</button>
        <button class="btn btn-aurora btn-sm" :disabled="loading || saving" @click="save">حفظ وتطبيق فوراً</button>
      </template>
    </PageHeader>
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />
    <template v-else>
      <div class="row g-3 mb-3">
        <div class="col-xl-8">
          <section class="glass p-3 h-100">
            <h2 class="h5 mb-1">التحكم العام</h2>
            <p class="small text-muted mb-3">تُطبّق هذه الإعدادات من السيرفر على جميع نسخ التطبيق المتوافقة.</p>
            <div class="row g-3">
              <div class="col-md-6" v-for="item in switches" :key="item.key">
                <label class="story-switch d-flex align-items-center justify-content-between gap-3 p-3 rounded-3">
                  <span><strong class="d-block">{{ item.title }}</strong><small class="text-muted">{{ item.hint }}</small></span>
                  <input v-model="form[item.key]" class="form-check-input" type="checkbox" role="switch">
                </label>
              </div>
            </div>
          </section>
        </div>
        <div class="col-xl-4">
          <section class="glass p-3 h-100 emergency-card">
            <h2 class="h5 mb-2"><i class="bi bi-shield-exclamation me-2"></i>وضع حماية السيرفر</h2>
            <p class="small text-muted">إيقاف النشر الجديد فوراً مع إبقاء مشاهدة الستوريات الحالية إذا كانت المشاهدة مفعلة.</p>
            <label class="story-switch d-flex align-items-center justify-content-between p-3 rounded-3">
              <span><strong>تفعيل وضع الطوارئ</strong><small class="d-block text-muted">إيقاف النشر فوراً</small></span>
              <input v-model="form.emergencyMode" class="form-check-input" type="checkbox" role="switch">
            </label>
          </section>
        </div>
      </div>
      <section class="glass p-3 mb-3">
        <h2 class="h5 mb-3">حدود النشر والتخزين</h2>
        <div class="row g-3">
          <div class="col-sm-6 col-lg-3"><label class="form-label">مدة الستوري (ساعة)</label><input v-model.number="form.durationHours" type="number" min="1" max="24" class="form-control"><small class="text-muted">الافتراضي 24 ساعة</small></div>
          <div class="col-sm-6 col-lg-3"><label class="form-label">أقصى مدة فيديو (ثانية)</label><input v-model.number="form.maxVideoSeconds" type="number" min="5" max="60" class="form-control"></div>
          <div class="col-sm-6 col-lg-3"><label class="form-label">أقصى حجم ملف (MB)</label><input v-model.number="form.maxMediaMb" type="number" min="1" max="50" class="form-control"></div>
          <div class="col-sm-6 col-lg-3"><label class="form-label d-block">التخزين المؤقت</label><label class="story-switch d-flex align-items-center justify-content-between p-2 rounded-3"><span>السماح بالكاش على الأجهزة</span><input v-model="form.cacheEnabled" class="form-check-input" type="checkbox" role="switch"></label></div>
        </div>
      </section>
      <section class="glass p-3">
        <div class="d-flex justify-content-between align-items-center mb-3"><div><h2 class="h5 mb-1">مراقبة الستوريات</h2><p class="small text-muted mb-0">آخر الستوريات المنشورة وإمكانية حذف المحتوى المخالف.</p></div><button class="btn btn-outline-light btn-sm" :disabled="loadingList" @click="loadList">تحديث القائمة</button></div>
        <LoadingSpinner v-if="loadingList" />
        <div v-else-if="!stories.length" class="empty-state p-4 text-center">لا توجد ستوريات حالياً</div>
        <div v-else class="table-responsive"><table class="table table-glass align-middle"><thead><tr><th>المستخدم</th><th>النوع</th><th>المشاهدات</th><th>النشر</th><th>الانتهاء</th><th></th></tr></thead><tbody>
          <tr v-for="story in stories" :key="story.id"><td>{{ story.displayName || story.username || story.publicId || 'مستخدم' }}</td><td>{{ story.mediaType === 'video' ? 'فيديو' : 'صورة' }}</td><td>{{ Number(story.viewCount || 0).toLocaleString() }}</td><td>{{ date(story.createdAt) }}</td><td>{{ date(story.expiresAt) }}</td><td class="text-end"><button class="btn btn-outline-danger btn-sm" :disabled="deleting === story.id" @click="removeStory(story)">حذف</button></td></tr>
        </tbody></table></div>
      </section>
    </template>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { storiesAdminApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
const loading=ref(false), loadingList=ref(false), saving=ref(false), deleting=ref(''), error=ref(''), success=ref('')
const stories=ref([])
const form=reactive({enabled:true,publishingEnabled:true,viewingEnabled:true,imagesEnabled:true,videosEnabled:true,emergencyMode:false,cacheEnabled:true,durationHours:24,maxVideoSeconds:30,maxMediaMb:20})
const switches=[
 {key:'enabled',title:'تفعيل الستوريات بالكامل',hint:'المفتاح العام لتشغيل أو إيقاف الميزة'},
 {key:'publishingEnabled',title:'السماح بالنشر',hint:'إيقاف نشر ستوري جديدة'},
 {key:'viewingEnabled',title:'السماح بالمشاهدة',hint:'إيقاف فتح الستوريات الحالية'},
 {key:'imagesEnabled',title:'السماح بالصور',hint:'تشغيل أو إيقاف نشر الصور'},
 {key:'videosEnabled',title:'السماح بالفيديو',hint:'تشغيل أو إيقاف نشر الفيديو'}
]
const date=v=>{if(!v)return '—';const d=new Date(v);return Number.isNaN(d.getTime())?'—':d.toLocaleString()}
async function load(){loading.value=true;error.value='';const r=await storiesAdminApi.config();loading.value=false;if(r.error){error.value=r.error.message||String(r.error);return}Object.assign(form,r.data?.data??r.data??{});await loadList()}
async function loadList(){loadingList.value=true;const r=await storiesAdminApi.list();loadingList.value=false;if(r.error){error.value=r.error.message||String(r.error);return}const p=r.data?.data??r.data;stories.value=Array.isArray(p)?p:[]}
async function save(){saving.value=true;error.value='';success.value='';const r=await storiesAdminApi.update({...form});saving.value=false;if(r.error){error.value=r.error.message||String(r.error);toast().danger(error.value);return}Object.assign(form,r.data?.data??r.data??{});success.value='تم حفظ إعدادات الستوريات وتطبيقها على جميع المستخدمين';toast().success(success.value)}
async function removeStory(story){if(!window.confirm('هل تريد حذف هذه الستوري؟'))return;deleting.value=story.id;const r=await storiesAdminApi.remove(story.id);deleting.value='';if(r.error){error.value=r.error.message||String(r.error);toast().danger(error.value);return}stories.value=stories.value.filter(x=>x.id!==story.id);toast().success('تم حذف الستوري')}
onMounted(load)
</script>
<style scoped>
.story-switch{background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.08);cursor:pointer;min-height:68px}
.story-switch .form-check-input{width:2.7em;height:1.4em;cursor:pointer;flex:0 0 auto}
.emergency-card{border-color:rgba(255,92,92,.28)}
</style>
