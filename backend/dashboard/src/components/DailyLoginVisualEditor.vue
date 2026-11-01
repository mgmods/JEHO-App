<template>
  <section class="visual-editor" dir="rtl">
    <header>
      <div><span>JEHO · DAILY REWARDS</span><h3>محرّر نافذة تسجيل الدخول اليومية</h3><p>عدّل الخلفية والشعار والنصوص، وشاهد معاينة الأيام السبعة قبل الحفظ.</p></div>
      <button type="button" @click="reset">استعادة الإعدادات الافتراضية</button>
    </header>
    <div class="editor-grid">
      <div class="controls">
        <details open><summary>الخلفية</summary>
          <label>رابط صورة الخلفية<input v-model.trim="draft.backgroundImageUrl" @change="applyBackgroundUrl" dir="ltr" placeholder="https://..." autocomplete="url" spellcheck="false"></label>
          <label>لون الخلفية الاحتياطي<input v-model="draft.backgroundColor" type="color"></label>
          <label>لون طبقة التعتيم<input v-model="draft.overlayColor" type="color"></label>
          <label>شفافية التعتيم · {{ Math.round(draft.overlayOpacity*100) }}%<input v-model.number="draft.overlayOpacity" type="range" min="0" max="1" step=".05"></label>
        </details>
        <details open><summary>الشعار والنصوص</summary>
          <label>رابط الشعار<input v-model.trim="draft.logoUrl" @change="applyLogoUrl" dir="ltr" placeholder="https://..." autocomplete="url" spellcheck="false"></label>
          <label>النص بجانب الشعار<input v-model="draft.title" maxlength="80" placeholder="مكافآت تسجيل الدخول"></label>
          <label>النص أسفل العنوان<input v-model="draft.subtitle" maxlength="120" placeholder="سجّل دخولك يومياً واحصل على مكافأتك"></label>
          <label>لون العنوان<input v-model="draft.titleColor" type="color"></label>
          <label>لون النص الثانوي<input v-model="draft.subtitleColor" type="color"></label>
        </details>
        <details open><summary>زر الاستلام</summary>
          <label>نص الزر<input v-model="draft.buttonText" maxlength="40"></label>
          <label>لون الزر<input v-model="draft.buttonColor" type="color"></label>
          <label>لون كتابة الزر<input v-model="draft.buttonTextColor" type="color"></label>
        </details>
      </div>
      <div class="preview-wrap">
        <strong>معاينة مباشرة · نافذة المستخدم</strong>
        <div class="preview" :style="previewStyle">
          <div class="preview-head">
            <img v-if="previewLogoUrl" :src="previewLogoUrl" alt="الشعار" loading="lazy" decoding="async">
            <div class="head-copy">
              <h2 :style="{color:draft.titleColor}">{{ draft.title }}</h2>
              <p :style="{color:draft.subtitleColor}">{{ draft.subtitle }}</p>
            </div>
          </div>
          <div class="days">
            <div v-for="d in previewDays" :key="d.day" class="day">
              <b>{{ d.day===7 ? '🏆 اليوم السابع' : 'اليوم '+d.day }}</b>
              <div class="day-art"><img v-if="image(d)" :src="image(d)" alt=""><span v-else>🪙</span></div>
              <small>{{ d.type==='coins' ? Number(d.amount||0).toLocaleString('en-US')+' كوينز' : label(d) }}</small>
            </div>
          </div>
          <div class="claim" :style="{background:draft.buttonColor,color:draft.buttonTextColor}">{{ draft.buttonText }}</div>
        </div>
        <small>التوزيع ثابت: أربعة أيام في الصف الأول وثلاثة في الصف الثاني.</small>
      </div>
    </div>
  </section>
</template>
<script setup>
import { computed, reactive, watch } from 'vue'
const props=defineProps({modelValue:{type:Object,required:true},days:{type:Array,default:()=>[]},cosmetics:{type:Array,default:()=>[]}})
const emit=defineEmits(['update:modelValue'])
const defaults={backgroundColor:'#f5f0e8',backgroundImageUrl:'',overlayColor:'#ffffff',overlayOpacity:.12,logoUrl:'',title:'مكافآت تسجيل الدخول لمدة 7 أيام',subtitle:'سجّل دخولك يومياً واحصل على مكافأتك',titleColor:'#3d3158',subtitleColor:'#766b80',buttonText:'استلام المكافأة',buttonColor:'#7652a5',buttonTextColor:'#ffffff',cardsPerRow:4}
const draft=reactive({...defaults,...props.modelValue,cardsPerRow:4})
watch(()=>props.modelValue,v=>Object.assign(draft,defaults,v||{},{cardsPerRow:4}),{deep:true})
watch(draft,v=>emit('update:modelValue',{...v,cardsPerRow:4}),{deep:true})
const previewDays=computed(()=>Array.from({length:7},(_,i)=>props.days.find(d=>Number(d.day)===i+1)||{day:i+1,type:'coins',amount:0,label:'مكافأة اليوم'}))
const previewStyle=computed(()=>({backgroundColor:draft.backgroundColor,backgroundImage:draft.backgroundImageUrl?'linear-gradient('+draft.overlayColor+Math.round(draft.overlayOpacity*255).toString(16).padStart(2,'0')+','+draft.overlayColor+Math.round(draft.overlayOpacity*255).toString(16).padStart(2,'0')+'),url('+draft.backgroundImageUrl+')':'none'}))
function reset(){Object.assign(draft,defaults)}
function cosmetic(code){return props.cosmetics.find(x=>x.code===code)}
function image(d){const x=cosmetic(d.cosmeticCode);return x?.previewUrl||x?.animationUrl||d.previewUrl||d.animationUrl||''}
function label(d){return cosmetic(d.cosmeticCode)?.name||d.label||'هدية'}
</script>
<style scoped>
.visual-editor{margin:18px 0;padding:20px;border:1px solid #e5deec;border-radius:20px;background:#faf9fc;color:#30283b}.visual-editor>header{display:flex;justify-content:space-between;align-items:flex-start;gap:14px;margin-bottom:18px}.visual-editor>header span{font-size:11px;letter-spacing:1.5px;color:#7652a5;font-weight:900}.visual-editor h3{margin:5px 0;font-weight:900}.visual-editor p{margin:0;color:#81788b}.visual-editor button{border:1px solid #ded5e9;background:#fff;color:#694594;border-radius:10px;padding:9px 12px;font-weight:800}.editor-grid{display:grid;grid-template-columns:minmax(260px,1fr) minmax(300px,1fr);gap:18px}.controls{display:grid;gap:10px;align-content:start}.controls details{background:#fff;border:1px solid #e9e3ef;border-radius:13px;padding:13px}.controls summary{font-weight:900;color:#594174;cursor:pointer}.controls label{display:block;margin-top:12px;font-size:12px;font-weight:800;color:#665d70}.controls input:not([type=color]):not([type=range]){display:block;width:100%;box-sizing:border-box;margin-top:5px;padding:9px;border:1px solid #e4ddea;border-radius:9px}.controls input[type=color]{display:block;width:44px;height:30px;margin-top:5px;border:0;background:transparent}.controls input[type=range]{display:block;width:100%;accent-color:#7652a5}.preview-wrap{position:sticky;top:14px;align-self:start}.preview-wrap>strong{display:block;margin-bottom:9px;color:#594174}.preview{min-height:440px;padding:18px;border:7px solid #eee9f3;border-radius:26px;overflow:hidden;background-size:cover;background-position:center;box-shadow:0 12px 32px #30204018}.preview-head{display:flex;align-items:center;gap:12px;min-height:76px;margin-bottom:20px}.preview-head img{width:52px;height:52px;object-fit:contain;flex:none}.head-copy{min-width:0}.head-copy h2{font-size:19px;font-weight:900;margin:0 0 5px}.head-copy p{font-size:12px;margin:0}.days{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px}.day{min-width:0;min-height:105px;padding:8px 3px;border:1px solid #e7deed;border-radius:13px;background:#ffffffdf;text-align:center;display:flex;flex-direction:column;align-items:center;justify-content:space-between;gap:4px}.day b{font-size:10px;color:#594174}.day-art{height:42px;display:flex;align-items:center;justify-content:center}.day-art img{max-width:42px;max-height:42px;object-fit:contain}.day-art span{font-size:27px}.day small{font-size:10px;font-weight:800;color:#45394f;overflow-wrap:anywhere}.claim{margin-top:18px;min-height:48px;border-radius:24px;display:flex;align-items:center;justify-content:center;font-weight:900;box-shadow:0 5px 14px #0002}.preview-wrap>small{display:block;margin-top:9px;color:#81788b}@media(max-width:850px){.editor-grid{grid-template-columns:1fr}.preview-wrap{position:static}.visual-editor>header{flex-direction:column}}
</style>