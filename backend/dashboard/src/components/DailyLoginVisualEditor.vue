<template>
  <section class="visual-editor" dir="rtl">
    <header>
      <div><span>JEHO · DAILY REWARDS</span><h3>محرّر نافذة تسجيل الدخول الأسبوعية</h3><p>التغييرات تظهر فوراً في المعاينة، وتُحفظ دفعة واحدة عند الضغط على حفظ الإعدادات.</p></div>
      <button type="button" @click="reset">استعادة الإعدادات الافتراضية</button>
    </header>
    <div class="editor-grid">
      <div class="controls">
        <details open><summary>الخلفية والصور</summary>
          <label>رابط صورة الخلفية<input v-model.trim="backgroundUrlInput" dir="ltr" placeholder="https://..." autocomplete="url" spellcheck="false" @change="applyBackgroundUrl"></label>
          <label class="upload-label">أو ارفع صورة الخلفية
            <input type="file" accept=".jpg,.jpeg,.png,.gif,.webp,.svg,image/*" :disabled="uploadingBackground" @change="uploadImage($event,'background')">
            <small>{{ uploadingBackground ? 'جارٍ رفع الصورة…' : 'يدعم JPG وPNG وGIF المتحرك وWebP وSVG' }}</small>
          </label>
          <label>رابط الشعار<input v-model.trim="logoUrlInput" dir="ltr" placeholder="https://..." autocomplete="url" spellcheck="false" @change="applyLogoUrl"></label>
          <label class="upload-label">أو ارفع الشعار
            <input type="file" accept=".jpg,.jpeg,.png,.gif,.webp,.svg,image/*" :disabled="uploadingLogo" @change="uploadImage($event,'logo')">
            <small>{{ uploadingLogo ? 'جارٍ رفع الشعار…' : 'يدعم الصور الثابتة والمتحركة' }}</small>
          </label>
          <label>لون الخلفية الاحتياطي<input v-model="draft.backgroundColor" type="color"></label>
          <label>لون طبقة التعتيم<input v-model="draft.overlayColor" type="color"></label>
          <label>شفافية التعتيم · {{ Math.round(Number(draft.overlayOpacity)*100) }}%<input v-model.number="draft.overlayOpacity" type="range" min="0" max="1" step=".05"></label>
        </details>
        <details open><summary>العنوان والنصوص</summary>
          <label>العنوان<input v-model="draft.title" maxlength="80"></label>
          <label>النص أسفل العنوان<input v-model="draft.subtitle" maxlength="120"></label>
          <label>لون العنوان<input v-model="draft.titleColor" type="color"></label>
          <label>لون النص الثانوي<input v-model="draft.subtitleColor" type="color"></label>
          <label>حجم العنوان · {{ draft.titleSize }}px<input v-model.number="draft.titleSize" type="range" min="14" max="40"></label>
          <label>المسافة أعلى المحتوى · {{ draft.contentTop }}px<input v-model.number="draft.contentTop" type="range" min="0" max="80"></label>
          <label>المسافة بين العنوان والبطاقات · {{ draft.headerGap }}px<input v-model.number="draft.headerGap" type="range" min="0" max="48"></label>
        </details>
        <details open><summary>بطاقات الأيام السبعة</summary>
          <label>لون البطاقات<input v-model="draft.cardColor" type="color"></label>
          <label>لون حدود البطاقات<input v-model="draft.cardBorderColor" type="color"></label>
          <label>استدارة البطاقات · {{ draft.cardRadius }}px<input v-model.number="draft.cardRadius" type="range" min="0" max="36"></label>
          <label>المسافة بين البطاقات · {{ draft.cardGap }}px<input v-model.number="draft.cardGap" type="range" min="2" max="28"></label>
          <label>حجم صورة المكافأة · {{ draft.cardImageSize }}px<input v-model.number="draft.cardImageSize" type="range" min="28" max="120"></label>
          <label>حجم نص المكافأة · {{ draft.cardLabelSize }}px<input v-model.number="draft.cardLabelSize" type="range" min="9" max="22"></label>
        </details>
        <details open><summary>زر الاستلام</summary>
          <label>نص الزر<input v-model="draft.buttonText" maxlength="40"></label>
          <label>لون الزر<input v-model="draft.buttonColor" type="color"></label>
          <label>لون كتابة الزر<input v-model="draft.buttonTextColor" type="color"></label>
          <label>استدارة الزر · {{ draft.buttonRadius }}px<input v-model.number="draft.buttonRadius" type="range" min="0" max="80"></label>
          <label>ارتفاع الزر · {{ draft.buttonHeight }}px<input v-model.number="draft.buttonHeight" type="range" min="36" max="90"></label>
          <label>المسافة أسفل الزر · {{ draft.buttonBottom }}px<input v-model.number="draft.buttonBottom" type="range" min="0" max="60"></label>
        </details>
      </div>
      <div class="preview-wrap">
        <strong>معاينة مباشرة · نافذة المستخدم</strong>
        <div class="preview" :style="previewStyle">
          <div class="preview-head" :style="{marginBottom: draft.headerGap+'px'}">
            <img v-if="draft.logoUrl" :src="draft.logoUrl" alt="الشعار" loading="lazy" decoding="async">
            <div class="head-copy">
              <h2 :style="{color:draft.titleColor,fontSize:draft.titleSize+'px'}">{{ draft.title }}</h2>
              <p :style="{color:draft.subtitleColor}">{{ draft.subtitle }}</p>
            </div>
          </div>
          <div class="days" :style="{gap:draft.cardGap+'px'}">
            <div v-for="d in previewDays" :key="d.day" class="day" :style="{background:draft.cardColor,borderColor:draft.cardBorderColor,borderRadius:draft.cardRadius+'px'}">
              <b>{{ d.day===7 ? '🏆 اليوم السابع' : 'اليوم '+d.day }}</b>
              <div class="day-art" :style="{height:draft.cardImageSize+'px'}"><img v-if="image(d)" :src="image(d)" alt="" :style="{maxWidth:draft.cardImageSize+'px',maxHeight:draft.cardImageSize+'px'}"><span v-else>🪙</span></div>
              <small :style="{fontSize:draft.cardLabelSize+'px'}">{{ d.type==='coins' ? Number(d.amount||0).toLocaleString('en-US')+' كوينز' : label(d) }}</small>
            </div>
          </div>
          <div class="claim" :style="{background:draft.buttonColor,color:draft.buttonTextColor,borderRadius:draft.buttonRadius+'px',minHeight:draft.buttonHeight+'px',marginBottom:draft.buttonBottom+'px'}">{{ draft.buttonText }}</div>
        </div>
        <small>التوزيع ثابت: أربعة أيام في الصف الأول وثلاثة في الصف الثاني.</small>
        <p v-if="uploadError" class="upload-error">{{ uploadError }}</p>
      </div>
    </div>
  </section>
</template>
<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { uploadsApi } from '@/api'
const props=defineProps({modelValue:{type:Object,required:true},days:{type:Array,default:()=>[]},cosmetics:{type:Array,default:()=>[]}})
const emit=defineEmits(['update:modelValue'])
const defaults={backgroundColor:'#dff0b8',backgroundImageUrl:'',overlayColor:'#ffffff',overlayOpacity:.12,logoUrl:'',title:'مكافآت تسجيل الدخول لمدة 7 أيام',subtitle:'سجّل دخولك يومياً واحصل على مكافأتك',titleColor:'#176b32',subtitleColor:'#766b80',titleSize:28,titleTop:24,headerGap:14,cardColor:'#fffef4',cardBorderColor:'#e8efcf',cardRadius:20,cardGap:12,cardImageSize:76,cardLabelSize:16,buttonText:'تسجيل الدخول',buttonColor:'#168b36',buttonTextColor:'#ffffff',buttonRadius:64,buttonHeight:64,buttonBottom:20,contentTop:18,contentGap:16,fontFamily:'default',cardsPerRow:4}
const draft=reactive({...defaults,...props.modelValue,cardsPerRow:4})
const backgroundUrlInput=ref(draft.backgroundImageUrl)
const logoUrlInput=ref(draft.logoUrl)
const uploadingBackground=ref(false)
const uploadingLogo=ref(false)
const uploadError=ref('')
let emitTimer=null
let applyingExternal=false
const snapshot=()=>({...draft,cardsPerRow:4})
function flush(){if(emitTimer){clearTimeout(emitTimer);emitTimer=null}emit('update:modelValue',snapshot())}
function scheduleEmit(){if(applyingExternal)return;if(emitTimer)clearTimeout(emitTimer);emitTimer=setTimeout(flush,140)}
watch(draft,scheduleEmit,{deep:true,flush:'post'})
watch(()=>props.modelValue,v=>{
  const incoming={...defaults,...(v||{}),cardsPerRow:4}
  const current=JSON.stringify(snapshot())
  const next=JSON.stringify(incoming)
  if(current===next)return
  applyingExternal=true
  Object.assign(draft,incoming)
  backgroundUrlInput.value=draft.backgroundImageUrl||''
  logoUrlInput.value=draft.logoUrl||''
  applyingExternal=false
},{deep:true})
const previewDays=computed(()=>Array.from({length:7},(_,i)=>props.days.find(d=>Number(d.day)===i+1)||{day:i+1,type:'coins',amount:0,label:'مكافأة اليوم'}))
const previewStyle=computed(()=>{
  const opacity=Math.max(0,Math.min(1,Number(draft.overlayOpacity)||0))
  const hex=String(draft.overlayColor||'#ffffff').replace('#','')
  const rgb=hex.length===3?hex.split('').map(x=>parseInt(x+x,16)): [parseInt(hex.slice(0,2),16),parseInt(hex.slice(2,4),16),parseInt(hex.slice(4,6),16)]
  const color=rgb.every(Number.isFinite)?'rgba('+rgb.join(',')+','+opacity+')':'rgba(255,255,255,'+opacity+')'
  const imageUrl=String(draft.backgroundImageUrl||'').trim()
  return {backgroundColor:draft.backgroundColor,backgroundImage:imageUrl?'linear-gradient('+color+','+color+'),url('+JSON.stringify(imageUrl)+')':'none',paddingTop:draft.contentTop+'px'}
})
function applyBackgroundUrl(){draft.backgroundImageUrl=backgroundUrlInput.value.trim()}
function applyLogoUrl(){draft.logoUrl=logoUrlInput.value.trim()}
async function uploadImage(event,kind){
  const file=event?.target?.files?.[0]
  if(!file)return
  uploadError.value=''
  if(!/^image\/(jpeg|png|gif|webp|svg\+xml)$/i.test(file.type) && !/\.(jpe?g|png|gif|webp|svg)$/i.test(file.name)){
    uploadError.value='صيغة الصورة غير مدعومة.';event.target.value='';return
  }
  if(file.size>20*1024*1024){uploadError.value='حجم الصورة أكبر من 20 ميغابايت.';event.target.value='';return}
  const busy=kind==='background'?uploadingBackground:uploadingLogo
  busy.value=true
  try{
    const {data,error}=await uploadsApi.upload(file)
    if(error)throw new Error(error.message||'فشل رفع الصورة')
    const payload=data?.data??data??{}
    const url=payload.url||payload.path
    if(!url)throw new Error('تم الرفع دون إرجاع رابط للصورة')
    if(kind==='background'){draft.backgroundImageUrl=url;backgroundUrlInput.value=url}
    else{draft.logoUrl=url;logoUrlInput.value=url}
    flush()
  }catch(err){uploadError.value=err?.message||'تعذر رفع الصورة'}
  finally{busy.value=false;event.target.value=''}
}
function reset(){Object.assign(draft,defaults);backgroundUrlInput.value='';logoUrlInput.value='';flush()}
function cosmetic(code){return props.cosmetics.find(x=>x.code===code)}
function image(d){const x=cosmetic(d.cosmeticCode);return x?.previewUrl||x?.animationUrl||d.previewUrl||d.animationUrl||''}
function label(d){return cosmetic(d.cosmeticCode)?.name||d.label||'هدية'}
defineExpose({flush})
onBeforeUnmount(()=>{if(emitTimer)clearTimeout(emitTimer)})
</script>
<style scoped>
.visual-editor{margin:18px 0;padding:20px;border:1px solid #e5deec;border-radius:20px;background:#faf9fc;color:#30283b}.visual-editor>header{display:flex;justify-content:space-between;align-items:flex-start;gap:14px;margin-bottom:18px}.visual-editor>header span{font-size:11px;letter-spacing:1.5px;color:#7652a5;font-weight:900}.visual-editor h3{margin:5px 0;font-weight:900}.visual-editor p{margin:0;color:#81788b}.visual-editor button{border:1px solid #ded5e9;background:#fff;color:#694594;border-radius:10px;padding:9px 12px;font-weight:800}.editor-grid{display:grid;grid-template-columns:minmax(260px,1fr) minmax(300px,1fr);gap:18px}.controls{display:grid;gap:10px;align-content:start}.controls details{background:#fff;border:1px solid #e9e3ef;border-radius:13px;padding:13px}.controls summary{font-weight:900;color:#594174;cursor:pointer}.controls label{display:block;margin-top:12px;font-size:12px;font-weight:800;color:#665d70}.controls input:not([type=color]):not([type=range]){display:block;width:100%;box-sizing:border-box;margin-top:5px;padding:9px;border:1px solid #e4ddea;border-radius:9px}.controls input[type=color]{display:block;width:44px;height:30px;margin-top:5px;border:0;background:transparent}.controls input[type=range]{display:block;width:100%;accent-color:#7652a5}.upload-label small{display:block;color:#847b8e;font-weight:500;margin-top:4px}.preview-wrap{position:sticky;top:14px;align-self:start}.preview-wrap>strong{display:block;margin-bottom:9px;color:#594174}.preview{min-height:440px;padding:18px;border:7px solid #eee9f3;border-radius:26px;overflow:hidden;background-size:cover;background-position:center;box-shadow:0 12px 32px #30204018}.preview-head{display:flex;align-items:center;gap:12px;min-height:76px}.preview-head img{width:52px;height:52px;object-fit:contain;flex:none}.head-copy{min-width:0}.head-copy h2{font-weight:900;margin:0 0 5px}.head-copy p{font-size:12px;margin:0}.days{display:grid;grid-template-columns:repeat(4,minmax(0,1fr))}.day{min-width:0;min-height:105px;padding:8px 3px;border:1px solid #e7deed;text-align:center;display:flex;flex-direction:column;align-items:center;justify-content:space-between;gap:4px}.day b{font-size:10px;color:#594174}.day-art{display:flex;align-items:center;justify-content:center}.day-art img{object-fit:contain}.day-art span{font-size:27px}.day small{font-weight:800;color:#45394f;overflow-wrap:anywhere}.claim{margin-top:18px;display:flex;align-items:center;justify-content:center;font-weight:900;box-shadow:0 5px 14px #0002}.preview-wrap>small{display:block;margin-top:9px;color:#81788b}.upload-error{margin-top:10px!important;color:#b42318!important;font-size:12px}@media(max-width:850px){.editor-grid{grid-template-columns:1fr}.preview-wrap{position:static}.visual-editor>header{flex-direction:column}}
</style>