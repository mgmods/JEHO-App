<template>
<div class="container-fluid py-2">
  <PageHeader title="الميداليات وإهداؤها" subtitle="إدارة الميداليات وإهداؤها لحساب محدد أو لجميع الحسابات."/>
  <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''"/>
  <div v-if="notice" class="alert alert-success">{{ notice }}</div>
  <div class="row g-3 mb-3">
    <div class="col-xl-7"><section class="glass p-3">
      <h5>{{ editingId ? 'تعديل الميدالية' : 'إضافة ميدالية للمتجر' }}</h5>
      <form class="row g-3" @submit.prevent="save">
        <div class="col-md-6"><label class="form-label">اسم الميدالية</label><input v-model.trim="form.name" class="form-control" maxlength="80" required/></div>
        <div class="col-md-6"><label class="form-label">الفئة</label><input v-model.trim="form.category" class="form-control" maxlength="32"/></div>
        <div class="col-12"><label class="form-label">رابط صورة الميدالية</label><input v-model.trim="form.imageUrl" class="form-control" maxlength="512" required/></div>
        <div class="col-md-7"><label class="form-label">رفع صورة</label><input type="file" class="form-control" accept="image/*" :disabled="uploadingImage" @change="uploadImage"/><small v-if="uploadingImage">جارٍ رفع الصورة…</small></div>
        <div class="col-md-5 d-flex align-items-end gap-2"><div class="badge-preview"><img v-if="form.imageUrl" :src="form.imageUrl" alt="معاينة الميدالية"/><span v-else>معاينة الميدالية</span></div></div>
        <div class="col-12"><label class="form-label">الوصف</label><textarea v-model.trim="form.description" class="form-control" rows="2" maxlength="500"/></div>
        <div class="col-md-4"><label class="form-label">طريقة الحصول</label><select v-model="form.awardType" class="form-select"><option value="manual">يدوي / إهداء</option><option value="achievement">إنجاز تلقائي</option></select></div>
        <template v-if="form.awardType === 'achievement'">
          <div class="col-md-4"><label class="form-label">شرط الإنجاز</label><select v-model="form.achievementKey" class="form-select" required><option :value="null" disabled>اختر الشرط</option><option value="level">المستوى</option><option value="experience">الخبرة</option><option value="followers">المتابعون</option><option value="recharge">إجمالي الشحن</option><option value="support">إجمالي الدعم</option><option value="received">إجمالي الاستلام</option></select></div>
          <div class="col-md-4"><label class="form-label">الحد المطلوب</label><input v-model.number="form.achievementThreshold" type="number" min="1" class="form-control" required/></div>
        </template>
        <div class="col-md-4"><label class="form-label">ترتيب العرض</label><input v-model.number="form.sortOrder" type="number" class="form-control"/></div>
        <div class="col-md-8 d-flex align-items-end gap-3"><label class="form-check"><input v-model="form.isActive" class="form-check-input" type="checkbox"/> مفعّلة</label><label class="form-check"><input v-model="form.isAnimated" class="form-check-input" type="checkbox"/> متحركة</label></div>
        <div class="col-12 d-flex gap-2"><button class="btn btn-primary" :disabled="saving || uploadingImage">{{ saving ? 'جارٍ الحفظ…' : 'حفظ الميدالية' }}</button><button v-if="editingId" type="button" class="btn btn-outline-secondary" @click="reset">إلغاء التعديل</button></div>
      </form>
    </section></div>
    <div class="col-xl-5">
      <section class="glass p-3 mb-3">
        <h5>إهداء ميدالية لمستخدم واحد</h5><p class="small text-muted">تظهر الميدالية في ملفه الشخصي ويمكن للآخرين رؤيتها.</p>
        <label class="form-label">الميدالية</label><select v-model="grant.badgeId" class="form-select mb-2"><option value="">اختر ميدالية</option><option v-for="b in badges" :key="b.id" :value="b.id">{{ b.name }}</option></select>
        <label class="form-label">معرّف المستخدم العام أو UUID</label><input v-model.trim="grant.userId" class="form-control mb-2" placeholder="ID المستخدم"/>
        <label class="form-label">المدة</label><select v-model.number="grant.durationDays" class="form-select mb-3"><option :value="0">دائمة</option><option :value="7">7 أيام</option><option :value="15">15 يوماً</option><option :value="30">30 يوماً</option><option :value="90">90 يوماً</option><option :value="365">سنة</option></select>
        <div class="d-flex gap-2"><button class="btn btn-success flex-fill" :disabled="busy || !grant.badgeId || !grant.userId" @click="grantOne">إهداء</button><button class="btn btn-outline-danger" :disabled="busy || !grant.badgeId || !grant.userId" @click="revokeOne">سحب</button></div>
      </section>
      <section class="glass p-3">
        <h5>إهداء لجميع المستخدمين</h5><p class="small text-muted">يُهدى الاختيار لكل الحسابات المسجلة؛ تُتخطى الميداليات المملوكة بالفعل، وتُجدد المنتهية.</p>
        <label class="form-label">الميدالية</label><select v-model="allGrant.badgeId" class="form-select mb-2"><option value="">اختر ميدالية</option><option v-for="b in badges" :key="b.id" :value="b.id">{{ b.name }}</option></select>
        <label class="form-label">المدة</label><select v-model.number="allGrant.durationDays" class="form-select mb-3"><option :value="0">دائمة</option><option :value="7">7 أيام</option><option :value="15">15 يوماً</option><option :value="30">30 يوماً</option><option :value="90">90 يوماً</option><option :value="365">سنة</option></select>
        <button class="btn btn-success w-100" :disabled="busy || !allGrant.badgeId" @click="grantEveryone">{{ busy ? 'جارٍ التنفيذ…' : 'إهداء الميدالية لكل الحسابات' }}</button>
      </section>
    </div>
  </div>
  <section class="glass p-0 overflow-hidden">
    <div class="p-3 d-flex align-items-center justify-content-between"><h5 class="mb-0">الميداليات المتاحة ({{ badges.length }})</h5><button class="btn btn-outline-secondary btn-sm" :disabled="loading" @click="load">تحديث</button></div>
    <div class="table-responsive"><table class="table align-middle mb-0"><thead><tr><th>الميدالية</th><th>الوصف</th><th>الفئة</th><th>الحالة</th><th>الإجراءات</th></tr></thead><tbody>
      <tr v-for="b in badges" :key="b.id"><td><div class="d-flex align-items-center gap-2"><img :src="b.imageUrl" :alt="b.name" class="table-badge"/><strong>{{ b.name }}</strong></div></td><td>{{ b.description || '—' }}</td><td>{{ b.category || 'عام' }}</td><td>{{ b.isActive ? 'مفعّلة' : 'مخفية' }}</td><td class="text-nowrap"><button class="btn btn-sm btn-outline-primary me-2" @click="edit(b)">تعديل</button><button class="btn btn-sm btn-outline-danger" @click="remove(b)">حذف</button></td></tr>
      <tr v-if="!loading && !badges.length"><td colspan="5" class="text-center p-4">لا توجد ميداليات بعد.</td></tr>
    </tbody></table></div>
  </section>
</div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { badgesApi, uploadsApi } from '@/api'
const badges = ref([]), loading = ref(false), saving = ref(false), busy = ref(false), uploadingImage = ref(false), error = ref(''), notice = ref(''), editingId = ref('')
const blank = () => ({ name:'', description:'', imageUrl:'', category:'general', awardType:'manual', achievementKey:null, achievementThreshold:0, isActive:true, isAnimated:false, sortOrder:0 })
const form = reactive(blank()), grant = reactive({ badgeId:'', userId:'', durationDays:0 }), allGrant = reactive({ badgeId:'', durationDays:0 })
async function load() { loading.value=true; try { const r=await badgesApi.list(); badges.value=r?.data?.data ?? r?.data ?? [] } catch(e) { error.value=e?.response?.data?.message || 'تعذر تحميل الميداليات. تحقق من صلاحيات الإدارة.' } finally { loading.value=false } }
async function uploadImage(event) { const file=event?.target?.files?.[0]; if(!file)return; if(!file.type.startsWith('image/')) { error.value='اختر ملف صورة صالحاً.'; event.target.value=''; return } uploadingImage.value=true; try { const r=await uploadsApi.upload(file); const p=r?.data?.data ?? r?.data ?? {}; if(!p.url)throw new Error('لم يُرجع الخادم رابط الصورة.'); form.imageUrl=p.url } catch(e) { error.value=e?.response?.data?.message || e?.message || 'تعذر رفع الصورة.' } finally { uploadingImage.value=false; event.target.value='' } }
function reset() { editingId.value=''; Object.assign(form,blank()) }
function edit(b) { editingId.value=b.id; Object.assign(form,{...blank(),...b}); window.scrollTo({top:0,behavior:'smooth'}) }
async function save() { saving.value=true; error.value=''; try { const p={...form}; if(editingId.value)await badgesApi.update(editingId.value,p); else await badgesApi.create(p); notice.value=editingId.value?'تم تحديث الميدالية.':'تمت إضافة الميدالية.'; reset(); await load() } catch(e) { error.value=e?.response?.data?.message || 'تعذر حفظ الميدالية.' } finally { saving.value=false } }
async function grantOne() { busy.value=true; error.value=''; try { await badgesApi.grant(grant.badgeId,grant.userId,grant.durationDays); notice.value='تم إهداء الميدالية للمستخدم وإرسال إشعار إليه.'; grant.userId='' } catch(e) { error.value=e?.response?.data?.message || 'تعذر إهداء الميدالية؛ تحقق من ID أو من ملكية الميدالية.' } finally { busy.value=false } }
async function revokeOne() { if(!window.confirm('هل تريد سحب الميدالية من هذا المستخدم؟'))return; busy.value=true; try { await badgesApi.revoke(grant.badgeId,grant.userId); notice.value='تم سحب الميدالية.' } catch(e) { error.value=e?.response?.data?.message || 'تعذر سحب الميدالية.' } finally { busy.value=false } }
async function grantEveryone() { const b=badges.value.find(x=>x.id===allGrant.badgeId); if(!b || !window.confirm('تأكيد إهداء ميدالية «'+b.name+'» لكل الحسابات المسجلة؟'))return; busy.value=true; error.value=''; notice.value=''; try { const r=await badgesApi.grantAll(allGrant.badgeId,allGrant.durationDays); const p=r?.data?.data ?? r?.data ?? {}; notice.value='اكتمل الإهداء: '+(p.granted??0)+' حساب جديد، تجديد '+(p.renewed??0)+'، وتخطي '+(p.skipped??0)+'.' } catch(e) { error.value=e?.response?.data?.message || 'تعذر إهداء الميدالية للجميع.' } finally { busy.value=false } }
async function remove(b) { if(!window.confirm('حذف الميدالية «'+b.name+'» وسجلات ملكيتها؟'))return; try { await badgesApi.remove(b.id); notice.value='تم حذف الميدالية.'; await load() } catch(e) { error.value=e?.response?.data?.message || 'تعذر حذف الميدالية.' } }
onMounted(load)
</script>
<style scoped>
.badge-preview{width:84px;height:84px;display:flex;align-items:center;justify-content:center;border:1px dashed var(--bs-border-color,#d7dce3);border-radius:14px;background:rgba(255,255,255,.55);padding:8px;color:var(--bs-secondary-color,#777);font-size:11px;text-align:center;overflow:hidden}
.badge-preview img{width:100%;height:100%;object-fit:contain}.table-badge{width:42px;height:42px;object-fit:contain;border-radius:10px;background:rgba(255,255,255,.45);padding:3px}
</style>
