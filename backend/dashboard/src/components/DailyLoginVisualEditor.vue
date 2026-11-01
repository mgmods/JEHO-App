<template>
  <section class="visual-editor">
    <header><div><span>LIVE DESIGN STUDIO</span><h3>محرّر تصميم نافذة JEHO</h3><p>تحكم بالخلفية والشعار والعنوان والخط والبطاقات والزر ومواقع العناصر، مع معاينة مباشرة.</p></div><button type="button" @click="reset">استعادة التصميم الأصلي</button></header>
    <div class="editor-grid">
      <div class="controls">
        <details open><summary>الخلفية والشعار</summary>
          <label>لون الخلفية <input v-model="draft.backgroundColor" type="color"></label>
          <label>رابط صورة الخلفية<input v-model="draft.backgroundImageUrl" placeholder="https://..." /></label>
          <label>رابط شعار JEHO<input v-model="draft.logoUrl" placeholder="https://..." /></label>
          <label>لون طبقة التعتيم<input v-model="draft.overlayColor" type="color"></label>
          <label>شفافية الطبقة · {{ Math.round(draft.overlayOpacity*100) }}%<input v-model.number="draft.overlayOpacity" type="range" min="0" max="1" step=".01"></label>
        </details>
        <details open><summary>العنوان والخط والمواقع</summary>
          <label>عنوان النافذة<input v-model="draft.title"></label>
          <label>لون العنوان<input v-model="draft.titleColor" type="color"></label>
          <label>حجم العنوان · {{ draft.titleSize }}<input v-model.number="draft.titleSize" type="range" min="12" max="48"></label>
          <label>المسافة العلوية · {{ draft.titleTop }}<input v-model.number="draft.titleTop" type="range" min="0" max="120"></label>
          <label>المسافة بعد الرأس · {{ draft.headerGap }}<input v-model.number="draft.headerGap" type="range" min="0" max="60"></label>
          <label>نوع الخط<select v-model="draft.fontFamily"><option value="default">خط التطبيق</option><option value="sans">Sans</option><option value="serif">Serif</option></select></label>
        </details>
        <details open><summary>بطاقات المكافآت</summary>
          <label>لون البطاقة<input v-model="draft.cardColor" type="color"></label>
          <label>لون الحدود<input v-model="draft.cardBorderColor" type="color"></label>
          <label>استدارة البطاقة · {{ draft.cardRadius }}<input v-model.number="draft.cardRadius" type="range" min="0" max="40"></label>
          <label>عدد البطاقات في الصف<select v-model.number="draft.cardsPerRow"><option :value="4">4 بطاقات (4 + 3)</option><option :value="3">3 بطاقات</option><option :value="2">بطاقتان</option></select></label><label>ارتفاع البطاقة · {{ draft.cardHeight }}<input v-model.number="draft.cardHeight" type="range" min="82" max="180"></label><label>المسافة بين البطاقات · {{ draft.cardGap }}<input v-model.number="draft.cardGap" type="range" min="0" max="32"></label>
          <label>حجم صورة الهدية · {{ draft.cardImageSize }}<input v-model.number="draft.cardImageSize" type="range" min="24" max="140"></label>
          <label>حجم كتابة البطاقة · {{ draft.cardLabelSize }}<input v-model.number="draft.cardLabelSize" type="range" min="8" max="28"></label>
        </details>
        <details open><summary>زر الاستلام</summary>
          <label>نص الزر<input v-model="draft.buttonText"></label>
          <label>لون الزر<input v-model="draft.buttonColor" type="color"></label>
          <label>لون النص<input v-model="draft.buttonTextColor" type="color"></label>
          <label>استدارة الزر · {{ draft.buttonRadius }}<input v-model.number="draft.buttonRadius" type="range" min="0" max="80"></label>
          <label>ارتفاع الزر · {{ draft.buttonHeight }}<input v-model.number="draft.buttonHeight" type="range" min="36" max="100"></label>
          <label>المسافة السفلية · {{ draft.buttonBottom }}<input v-model.number="draft.buttonBottom" type="range" min="0" max="100"></label>
        </details>
        <details><summary>مسافات المحتوى</summary>
          <label>المسافة من أعلى المحتوى · {{ draft.contentTop }}<input v-model.number="draft.contentTop" type="range" min="0" max="100"></label>
          <label>التباعد العام · {{ draft.contentGap }}<input v-model.number="draft.contentGap" type="range" min="0" max="60"></label>
        </details>
      </div>
      <div class="preview-wrap"><strong>معاينة مباشرة · شكل النافذة للمستخدم</strong>
        <div class="preview" :style="previewStyle">
          <div class="preview-head" :style="{paddingTop:draft.titleTop+'px',gap:draft.headerGap+'px'}"><img v-if="draft.logoUrl" :src="draft.logoUrl"><h2 :style="{color:draft.titleColor,fontSize:draft.titleSize+'px',fontFamily:font}">{{ draft.title }}</h2></div>
          <div class="days" :style="{gap:draft.cardGap+'px',marginTop:draft.contentTop+'px','--daily-cols':draft.cardsPerRow}">
            <div v-for="d in days" :key="d.day" class="day" :style="{background:draft.cardColor,borderColor:draft.cardBorderColor,borderRadius:draft.cardRadius+'px'}"><b>اليوم {{ d.day }}</b><div :style="{height:draft.cardImageSize+'px'}"><img v-if="image(d)" :src="image(d)"><span v-else>🪙</span></div><small :style="{fontSize:draft.cardLabelSize+'px'}">{{ d.type==='coins' ? Number(d.amount||0).toLocaleString('en-US')+' كوينز' : label(d) }}</small></div>
          </div>
          <div class="claim" :style="{background:draft.buttonColor,color:draft.buttonTextColor,borderRadius:draft.buttonRadius+'px',height:draft.buttonHeight+'px',marginTop:draft.contentGap+'px',marginBottom:draft.buttonBottom+'px'}">{{ draft.buttonText }}</div>
        </div><small>معاينة تقريبية بمقاس نافذة الهاتف؛ العناصر الفعلية تأتي من إعدادات الأيام.</small>
      </div>
    </div>
  </section>
</template>
<script setup>
import { computed, reactive, watch } from 'vue'
const props=defineProps({modelValue:{type:Object,required:true},days:{type:Array,default:()=>[]},cosmetics:{type:Array,default:()=>[]}})
const emit=defineEmits(['update:modelValue'])
const defaults={backgroundColor:'#dff0b8',backgroundImageUrl:'',overlayColor:'#ffffff',overlayOpacity:.12,logoUrl:'',title:'مكافآت تسجيل الدخول لمدة 7 أيام',titleColor:'#176b32',titleSize:28,titleTop:24,headerGap:14,cardColor:'#fffef4',cardBorderColor:'#e8efcf',cardRadius:20,cardGap:12,cardsPerRow:4,cardHeight:112,cardImageSize:76,cardLabelSize:16,buttonText:'تسجيل الدخول',buttonColor:'#168b36',buttonTextColor:'#ffffff',buttonRadius:64,buttonHeight:64,buttonBottom:20,contentTop:18,contentGap:16,fontFamily:'default'}
const draft=reactive({...defaults,...props.modelValue})
watch(()=>props.modelValue,v=>Object.assign(draft,defaults,v||{}),{deep:true})
watch(draft,v=>emit('update:modelValue',{...v}),{deep:true})
const font=computed(()=>draft.fontFamily==='serif'?'serif':'sans-serif')
const previewStyle=computed(()=>({backgroundColor:draft.backgroundColor,backgroundImage:draft.backgroundImageUrl?'linear-gradient('+draft.overlayColor+Math.round(draft.overlayOpacity*255).toString(16).padStart(2,'0')+','+draft.overlayColor+Math.round(draft.overlayOpacity*255).toString(16).padStart(2,'0')+'),url('+draft.backgroundImageUrl+')':'none',backgroundSize:'cover',backgroundPosition:'center'}))
function reset(){Object.assign(draft,defaults)}
function cosmetic(code){return props.cosmetics.find(x=>x.code===code)}
function image(d){const x=cosmetic(d.cosmeticCode);return x?.previewUrl||x?.animationUrl||''}
function label(d){return cosmetic(d.cosmeticCode)?.name||d.label||'هدية'}
</script>
<style scoped>
.visual-editor{margin:18px 0 22px;padding:20px;border:1px solid #dcebe1;border-radius:22px;background:#f5faf6;color:#20382a}.visual-editor>header{display:flex;justify-content:space-between;align-items:flex-start;gap:15px;margin-bottom:16px}.visual-editor>header span{font-size:11px;letter-spacing:2px;color:#20864f;font-weight:900}.visual-editor h3{margin:5px 0;font-weight:900}.visual-editor p{margin:0;color:#718078}.visual-editor button{border:1px solid #cfe4d5;background:#fff;color:#187847;border-radius:11px;padding:9px 13px;font-weight:800}.editor-grid{display:grid;grid-template-columns:minmax(280px,1fr) minmax(300px,1fr);gap:18px}.controls{display:grid;gap:9px;align-content:start}.controls details{background:#fff;border:1px solid #e0ebe4;border-radius:13px;padding:12px 14px}.controls summary{font-weight:900;color:#245a39;cursor:pointer}.controls label{display:block;font-size:12px;font-weight:800;color:#52665a;margin-top:11px}.controls input:not([type=color]):not([type=range]),.controls select{display:block;width:100%;margin-top:5px;padding:8px;border:1px solid #dbe7df;border-radius:9px;background:white;color:#24372b}.controls input[type=color]{float:left;width:40px;height:28px;border:0;background:transparent}.controls input[type=range]{display:block;width:100%;accent-color:#20864f}.preview-wrap{position:sticky;top:15px;align-self:start}.preview-wrap>strong{display:block;margin-bottom:9px;color:#315c40}.preview{min-height:520px;border:8px solid #e5e7d1;border-radius:28px;padding:14px;overflow:hidden;box-shadow:0 12px 34px #173e251c}.preview-head{display:flex;align-items:center;justify-content:center;min-height:100px}.preview-head img{width:48px;height:48px;object-fit:contain}.preview-head h2{font-weight:900;text-align:center;text-shadow:0 2px 4px #ffffffaa}.days{display:grid;grid-template-columns:repeat(var(--daily-cols,4),minmax(0,1fr))}.day{border:2px solid;min-width:0;padding:7px 3px;text-align:center;box-shadow:0 4px 10px #0001;display:flex;flex-direction:column;justify-content:space-between;gap:5px}.day b{font-size:10px;color:#386b3e}.day>div{display:flex;align-items:center;justify-content:center}.day img{max-width:100%;max-height:100%;object-fit:contain}.day span{font-size:30px}.day small{color:#26352b;font-weight:800;overflow:hidden;text-overflow:ellipsis}.claim{display:flex;align-items:center;justify-content:center;font-weight:900;box-shadow:0 6px 14px #0002}.preview-wrap>small{display:block;color:#718078;margin-top:9px}@media(max-width:850px){.editor-grid{grid-template-columns:1fr}.preview-wrap{position:static}.visual-editor>header{flex-direction:column}}
</style>