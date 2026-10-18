<template>
  <div class="page-shell" dir="rtl">
    <header class="page-header"><div><h1>صندوق الدعم</h1><p>إدارة تفعيل الصندوق وأهدافه والهدايا ومكافآت الفائز.</p></div><button class="btn btn-primary" :disabled="saving" @click="save">{{ saving ? 'جارٍ الحفظ…' : 'حفظ الإعدادات' }}</button></header>
    <div v-if="error" class="alert alert-danger">{{ error }}</div><div v-if="notice" class="alert alert-success">{{ notice }}</div>
    <section class="card p-4"><h2>الإعداد العام</h2>
      <label class="switchrow"><span><b>تفعيل صندوق الدعم</b><small>يظهر الصندوق في الرومات عند التفعيل.</small></span><input v-model="form.enabled" type="checkbox"></label>
      <label class="switchrow"><span><b>إظهار الأيقونة</b><small>إخفاء الأيقونة لا يحذف الإعدادات.</small></span><input v-model="form.iconVisible" type="checkbox"></label>
      <label class="field">نوع الهدف<select v-model="form.goalMode"><option value="coins">كوينزات فقط</option><option value="diamonds">ماسات فقط</option><option value="both">كوينزات وماسات معاً</option></select></label>
      <div class="grid2"><label class="field">هدف الكوينزات<input v-model.number="form.coinTarget" type="number" min="0"></label><label class="field">هدف الماسات<input v-model.number="form.diamondTarget" type="number" min="0"></label></div>
      <label class="field">معرّفات الهدايا المسموح بها من كتالوج JEHO<textarea v-model="giftIdsText" rows="3" placeholder="معرّفات الهدايا مفصولة بفواصل"></textarea></label>
      <p class="muted">استخدم معرّفات الهدايا الحقيقية من كتالوج التطبيق.</p>
    </section>
    <section class="card p-4 mt-3"><h2>مكافأة الفائز</h2>
      <label class="field">نوع المكافأة<select v-model="form.rewardType"><option value="none">بدون مكافأة تلقائية</option><option value="coins">كوينزات</option><option value="diamonds">ماسات</option><option value="frame">إطار ملف شخصي</option><option value="badge">شارة</option><option value="gift" disabled>هدية (غير مدعومة تلقائياً حالياً)</option></select></label>
      <label class="field">معرّف المكافأة من كتالوج JEHO<input v-model="form.rewardItemId" type="text" placeholder="معرّف الإطار / الشارة / الهدية"></label>
      <label class="field">كمية المكافأة<input v-model.number="form.rewardAmount" type="number" min="0"></label>
      <label class="field">تعليمات الصندوق<textarea v-model="form.instructions" rows="4"></textarea></label>
    </section>
    <p class="muted foot">يدعم الخادم مكافآت الكوينزات والماسات والإطارات والشارات مع تسجيلها ضمن المعاملة. مكافأة الهدية من المخزون غير مفعّلة تلقائياً حالياً.</p>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { supportChestApi } from '@/api'
const form = reactive({ enabled:false, iconVisible:false, goalMode:'coins', coinTarget:100000, diamondTarget:10000, giftIds:[], rewardType:'none', rewardItemId:'', rewardAmount:0, instructions:'كل هدية مؤهلة تساهم في هدف الصندوق. عند اكتماله يحصل مرسل الهدية الأخيرة على المكافأة.' })
const giftIdsText=ref(''), saving=ref(false), error=ref(''), notice=ref('')
onMounted(async()=>{try{const r=await supportChestApi.getConfig();const d=r?.data?.data ?? r?.data ?? r;if(d&&typeof d==='object'){Object.assign(form,d);giftIdsText.value=(d.giftIds||[]).join(', ')}}catch(e){error='تعذّر تحميل الإعدادات من الخادم.'}})
async function save(){saving.value=true;error.value='';notice.value='';try{form.giftIds=giftIdsText.value.split(',').map(x=>x.trim()).filter(Boolean);await supportChestApi.saveConfig({...form});notice.value='تم إرسال الإعدادات للحفظ.'}catch(e){error='فشل الحفظ من الخادم؛ لم يتم تأكيد حفظ الإعدادات.'}finally{saving.value=false}}
</script>
<style scoped>
.page-shell{max-width:980px;margin:auto;padding:22px}.page-header{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:20px}.page-header h1{margin:0 0 5px}.page-header p,.muted{color:var(--text-muted,#888);font-size:13px}.card{background:var(--surface,#fff);border:1px solid var(--border,#ddd);border-radius:16px;margin-bottom:14px}.card h2{font-size:17px;margin:0 0 16px}.switchrow{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:12px 0;border-bottom:1px solid var(--border,#eee)}.switchrow span{display:grid;gap:4px}.switchrow small{color:var(--text-muted,#888)}.field{display:grid;gap:7px;margin:15px 0;font-weight:600}.field input,.field select,.field textarea{width:100%;padding:10px 12px;border-radius:9px;border:1px solid var(--border,#ccc);background:var(--surface,#fff);color:inherit}.grid2{display:grid;grid-template-columns:1fr 1fr;gap:14px}.foot{line-height:1.7}@media(max-width:650px){.page-header{align-items:flex-start;flex-direction:column}.grid2{grid-template-columns:1fr}}
</style>