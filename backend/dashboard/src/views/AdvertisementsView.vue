<template>
  <div>
    <PageHeader title="إدارة إعلانات الرومات" subtitle="تحكم مركزي بالإرسال والطابور وشريط الإعلانات" />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />

    <div v-else class="row g-3">
      <div class="col-12">
        <div class="glass p-3">
          <div class="mb-3 d-flex flex-column align-items-center gap-2">
            <div class="small text-muted align-self-start">معاينة شاشة الروم — اسحب شريط الإعلان إلى المكان الذي تريده تماماً</div>
            <div class="room-preview-stage position-relative overflow-hidden rounded-4"
                 style="width:min(100%,360px);height:600px;touch-action:pan-y;background:radial-gradient(ellipse at 50% 25%,#243c54 0%,#101b2c 55%,#080e18 100%);border:1px solid rgba(255,255,255,.16);box-shadow:0 18px 45px rgba(0,0,0,.35)">
              <div class="d-flex justify-content-between align-items-center px-3 pt-3 text-white small"><span class="opacity-75">‹　 01:28</span><span class="fw-bold">JEHO ROOM</span><span class="opacity-75">♧ 128</span></div>
              <div class="text-center text-white-50 small mt-3">غرفة الأصدقاء　 •　 بث مباشر</div>
              <div class="d-flex justify-content-center gap-3 mt-5">
                <div v-for="n in 5" :key="n" class="rounded-circle d-flex align-items-center justify-content-center text-white" :style="{width:'48px',height:'48px',background:['#526b87','#6d527e','#8b7046','#3e7771','#765d68'][n-1],border:'2px solid rgba(255,255,255,.3)'}">♙</div>
              </div>
              <div class="position-absolute start-0 end-0 bottom-0 px-3 pb-4"><div class="rounded-3 p-2 mb-2 text-white-50 small" style="background:rgba(255,255,255,.07)">الدردشة والرسائل تظهر هنا</div><div class="d-flex justify-content-between text-white-50 small"><span>الجميع</span><span>دردشة</span><span>هدية</span></div></div>
              <div class="position-absolute ad-preview-marker d-flex align-items-center px-2"
                   :style="{left:(Number(form.positionXPercent)||50)+'%',top:(Number(form.positionYPercent)||52)+'%',transform:'translate(-50%,-50%)',width:'88%',minHeight:Math.max(36,Number(form.heightDp)||48)+'px',height:Math.max(36,Number(form.heightDp)||48)+'px',borderRadius:(form.radiusDp||18)+'px',background:form.background||'linear-gradient(90deg,#21142d,#32184c)',backgroundImage:form.backgroundImage?'linear-gradient(rgba(12,12,22,'+(1-(form.backgroundOpacity||92)/100)+'),rgba(12,12,22,'+(1-(form.backgroundOpacity||92)/100)+')),url('+form.backgroundImage+')':undefined,backgroundSize:'cover',opacity:1,cursor:dragging?'grabbing':'grab',boxShadow:'0 8px 22px rgba(0,0,0,.35)',border:'1px solid rgba(255,255,255,.22)',zIndex:10,touchAction:'none',gap:(form.spacingDp||0)+'px'}"
                   @pointerdown.stop.prevent="startDrag" @pointermove.stop.prevent="moveDrag" @pointerup.stop.prevent="stopDrag" @pointercancel="stopDrag">
                <img v-if="queue[0]?.senderImageUrl" :src="queue[0].senderImageUrl" alt="صورة المرسل" :style="{width:(form.avatarDp||34)+'px',height:(form.avatarDp||34)+'px',borderRadius:form.avatarShape==='circle'?'50%':'7px',objectFit:'cover',flex:'0 0 auto',transform:'translate('+(form.avatarOffsetXDp||0)+'px,'+(form.avatarOffsetYDp||0)+'px)'}">
                <div v-else class="d-flex align-items-center justify-content-center text-white" :style="{width:(form.avatarDp||34)+'px',height:(form.avatarDp||34)+'px',borderRadius:form.avatarShape==='circle'?'50%':'7px',background:'rgba(255,255,255,.16)',flex:'0 0 auto',transform:'translate('+(form.avatarOffsetXDp||0)+'px,'+(form.avatarOffsetYDp||0)+'px)'}">👤</div>
                <strong class="text-nowrap" :style="{fontSize:(form.nameSp||12)+'px',width:(form.nameWidthDp||90)+'px',overflow:'hidden',textOverflow:'ellipsis',transform:'translate('+(form.nameOffsetXDp||0)+'px,'+(form.nameOffsetYDp||0)+'px)',fontFamily:form.fontFamily==='custom'&&form.customFontUrl?'JEHOAdvertisementCustom':form.fontFamily==='serif'?'serif':form.fontFamily==='mono'?'monospace':'sans-serif',background:form.nameGradientEnabled?'linear-gradient(90deg,'+form.nameGradientStartColor+','+form.nameGradientEndColor+')':'none',backgroundClip:form.nameGradientEnabled?'text':'border-box',WebkitBackgroundClip:form.nameGradientEnabled?'text':'border-box',color:form.nameGradientEnabled?'transparent':form.nameColor}">{{ queue[0]?.senderName || 'اسم المرسل' }}</strong>
                <span class="text-nowrap overflow-hidden" :style="{flex:form.textWidthDp>0?'0 0 auto':'1 1 auto',width:form.textWidthDp>0?form.textWidthDp+'px':'auto',transform:'translate('+(form.textOffsetXDp||0)+'px,'+(form.textOffsetYDp||0)+'px)',fontSize:(form.textSp||13)+'px',fontFamily:form.fontFamily==='custom'&&form.customFontUrl?'JEHOAdvertisementCustom':form.fontFamily==='serif'?'serif':form.fontFamily==='mono'?'monospace':'sans-serif',background:form.textGradientEnabled?'linear-gradient(90deg,'+form.textGradientStartColor+','+form.textGradientEndColor+')':'none',backgroundClip:form.textGradientEnabled?'text':'border-box',WebkitBackgroundClip:form.textGradientEnabled?'text':'border-box',color:form.textGradientEnabled?'transparent':form.textColor}">{{ queue[0]?.text || 'نص الإعلان يبدأ مباشرة بجانب الاسم والصورة' }}</span>
                <span class="position-absolute top-0 end-0 badge bg-warning text-dark" style="transform:translate(20%,-45%)">اسحبني</span>
              </div>
            </div>
            <div class="small text-muted">الموقع: أفقي {{ Math.round(form.positionXPercent||50) }}% · عمودي {{ Math.round(form.positionYPercent||52) }}%</div>
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
            <div class="col-md-3">
              <label class="form-label small">مدة الانتظار قبل نشر الإعلان (ثانية)</label>
              <input v-model.number="form.publishDelayMs" type="number" min="1" max="60" step="1" class="form-control">
              <div class="form-text">من ثانية واحدة إلى دقيقة، قبل ظهوره في جميع الرومات.</div>
            </div>
            <div class="col-md-3">
              <label class="form-label small">الحد الأقصى للكلمات</label>
              <input v-model.number="form.maxWords" type="number" min="1" max="200" class="form-control">
              <div class="form-text">أي إعلان يتجاوز هذا العدد يُرفض قبل دخوله الطابور.</div>
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
            <div class="col-6"><label class="form-label small">الموضع</label><select v-model="form.position" class="form-select"><option value="safe-top">أعلى آمن</option><option value="safe-bottom">أسفل آمن</option><option value="custom-safe">موضع مخصص آمن</option><option value="free">موضع حر بالسحب</option></select></div>
            <div class="col-6"><label class="form-label small">إزاحة الموضع المخصص dp</label><input v-model.number="form.customOffsetDp" type="number" min="-100" max="100" class="form-control" :disabled="form.position!=='custom-safe'"></div>
            <div class="col-6"><label class="form-label small">الموقع الأفقي %</label><input v-model.number="form.positionXPercent" type="number" min="0" max="100" class="form-control" :disabled="form.position!=='free'"></div><div class="col-6"><label class="form-label small">الموقع العمودي %</label><input v-model.number="form.positionYPercent" type="number" min="0" max="100" class="form-control" :disabled="form.position!=='free'"></div><div class="col-6"><label class="form-label small">الاتجاه</label><select v-model="form.direction" class="form-select"><option value="rtl">RTL</option><option value="ltr">LTR</option></select></div>
            <div class="col-6"><label class="form-label small">نوع الانتقال</label><select v-model="form.transitionType" class="form-select"><option value="slide">انزلاق</option><option value="fade">تلاشي</option></select></div>
            <div class="col-12"><div class="fw-semibold small mt-2">حركة عناصر الإعلان بشكل مستقل</div><div class="small text-muted mb-2">اختر اتجاه حركة النص والاسم والصورة كلٌّ على حدة، أو ثبّته أو اجعله يتلاشى</div></div>
            <div class="col-4"><label class="form-label small">حركة نص الإعلان</label><select v-model="form.textMotion" class="form-select"><option value="right-to-left">من اليمين لليسار</option><option value="left-to-right">من اليسار لليمين</option><option value="static">ثابت</option><option value="fade">تلاشي</option></select></div>
            <div class="col-4"><label class="form-label small">حركة الاسم</label><select v-model="form.nameMotion" class="form-select"><option value="right-to-left">من اليمين لليسار</option><option value="left-to-right">من اليسار لليمين</option><option value="static">ثابت</option><option value="fade">تلاشي</option></select></div>
            <div class="col-4"><label class="form-label small">حركة الصورة</label><select v-model="form.avatarMotion" class="form-select"><option value="right-to-left">من اليمين لليسار</option><option value="left-to-right">من اليسار لليمين</option><option value="static">ثابتة</option><option value="fade">تلاشي</option></select></div>
            <div class="col-6"><label class="form-label small">قالب الشريط</label><select v-model="form.template" class="form-select" @change="applyTemplate"><option value="premium-v1">فاخر</option><option value="compact">مختصر</option><option value="gold">ذهبي</option></select></div>
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
            <div class="col-6"><label class="form-label small">نوع الخط</label><select v-model="form.fontFamily" class="form-select"><option value="default">افتراضي</option><option value="sans">Sans</option><option value="serif">Serif</option><option value="mono">Mono</option><option value="custom" :disabled="!form.customFontUrl">الخط المرفوع</option></select></div>
            <div class="col-12"><label class="form-label small">رفع خط خاص للشريط (TTF / OTF)</label><input class="form-control" type="file" accept=".ttf,.otf,font/ttf,font/otf" :disabled="fontUploading" @change="uploadFont"><div class="small text-muted mt-1">{{ fontUploading ? 'جارٍ رفع الخط...' : (form.customFontName ? 'الخط الحالي: '+form.customFontName : 'ارفع ملف خط، ثم اختر «الخط المرفوع» من القائمة') }}</div><button v-if="form.customFontUrl" type="button" class="btn btn-outline-danger btn-sm mt-2" @click="clearFont">إزالة الخط المرفوع</button></div>
            <div class="col-12"><hr class="border-secondary"><div class="fw-semibold small mb-2">تلوين الاسم ونص الإعلان</div></div>
            <div class="col-12 form-check form-switch"><input id="nameGradientEnabled" v-model="form.nameGradientEnabled" class="form-check-input" type="checkbox"><label class="form-check-label" for="nameGradientEnabled">تفعيل تدرّج ألوان اسم المرسل</label></div>
            <div class="col-6"><label class="form-label small">لون الاسم الأول</label><input v-model="form.nameGradientStartColor" type="color" class="form-control form-control-color w-100" :disabled="!form.nameGradientEnabled"></div>
            <div class="col-6"><label class="form-label small">لون الاسم الثاني</label><input v-model="form.nameGradientEndColor" type="color" class="form-control form-control-color w-100" :disabled="!form.nameGradientEnabled"></div>
            <div class="col-12 form-check form-switch"><input id="textGradientEnabled" v-model="form.textGradientEnabled" class="form-check-input" type="checkbox"><label class="form-check-label" for="textGradientEnabled">تفعيل تدرّج ألوان نص الإعلان</label></div>
            <div class="col-6"><label class="form-label small">لون النص الأول</label><input v-model="form.textGradientStartColor" type="color" class="form-control form-control-color w-100" :disabled="!form.textGradientEnabled"></div>
            <div class="col-6"><label class="form-label small">لون النص الثاني</label><input v-model="form.textGradientEndColor" type="color" class="form-control form-control-color w-100" :disabled="!form.textGradientEnabled"></div>
            <div class="col-12"><hr class="border-secondary"><div class="fw-semibold small mb-2">موضع كل عنصر بشكل مستقل (dp)</div></div>
            <div class="col-6"><label class="form-label small">الصورة: إزاحة أفقية</label><input v-model.number="form.avatarOffsetXDp" type="number" min="-300" max="300" class="form-control"></div>
            <div class="col-6"><label class="form-label small">الصورة: إزاحة عمودية</label><input v-model.number="form.avatarOffsetYDp" type="number" min="-100" max="100" class="form-control"></div>
            <div class="col-6"><label class="form-label small">الاسم: إزاحة أفقية</label><input v-model.number="form.nameOffsetXDp" type="number" min="-300" max="300" class="form-control"></div>
            <div class="col-6"><label class="form-label small">الاسم: إزاحة عمودية</label><input v-model.number="form.nameOffsetYDp" type="number" min="-100" max="100" class="form-control"></div>
            <div class="col-6"><label class="form-label small">عرض الاسم dp</label><input v-model.number="form.nameWidthDp" type="number" min="24" max="260" class="form-control"></div>
            <div class="col-6"><label class="form-label small">النص: إزاحة أفقية</label><input v-model.number="form.textOffsetXDp" type="number" min="-300" max="300" class="form-control"></div>
            <div class="col-6"><label class="form-label small">النص: إزاحة عمودية</label><input v-model.number="form.textOffsetYDp" type="number" min="-100" max="100" class="form-control"></div>
            <div class="col-6"><label class="form-label small">عرض منطقة النص dp (0 تلقائي)</label><input v-model.number="form.textWidthDp" type="number" min="0" max="1000" class="form-control"></div>
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
                  <td><span :class="ad.status === 'playing' ? 'badge bg-success' : 'badge bg-secondary'">{{ ad.status === 'playing' ? 'يُعرض الآن' : ad.status === 'queued' ? 'بانتظار العرض' : ad.status }}</span></td><td>{{ ad.senderName }}</td><td class="text-truncate" style="max-width:260px">{{ ad.text }}<div v-if="ad.rejectionReason" class="small text-danger text-wrap mt-1">سبب الرفض: {{ ad.rejectionReason }} <span v-if="ad.rejectionCode">({{ ad.rejectionCode }})</span></div></td><td>{{ ad.queueOrder }}</td><td>{{ ad.startedAt || ad.createdAt }}</td>
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
import { advertisementsApi, uploadsApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const loading=ref(true), saving=ref(false), savingWords=ref(false), fontUploading=ref(false), error=ref(''), success=ref(''), dragging=ref(false)
const bannedWords=ref(''), queue=ref([]), stats=ref({}), statsDays=ref(7), queueSearch=ref(''), showHistory=ref(false)
const form=reactive({enabled:true,sendButtonEnabled:true,paused:false,dailyLimit:1,maxQueueSize:500,maxTextLength:180,maxWords:30,publishDelayMs:10000,position:'free',positionXPercent:50,positionYPercent:52,direction:'rtl',textMotion:'right-to-left',nameMotion:'static',avatarMotion:'static',displayDurationMs:9000,gapMs:1800,entrySpeedPxPerSec:120,exitSpeedPxPerSec:120,reducedMotionStatic:true,heightDp:48,avatarDp:34,nameSp:12,textSp:13,nameColor:'#FFD98A',textColor:'#FFFFFF',background:'',backgroundImage:'',backgroundOpacity:92,radiusDp:18,spacingDp:8,avatarShape:'circle',resetTimezone:'UTC',transitionType:'slide',fontFamily:'default',customFontUrl:'',customFontName:'',nameGradientEnabled:false,nameGradientStartColor:'#FFD98A',nameGradientEndColor:'#FF6B9D',textGradientEnabled:false,textGradientStartColor:'#FFFFFF',textGradientEndColor:'#9EDCFF',template:'premium-v1',customOffsetDp:0,avatarOffsetXDp:0,avatarOffsetYDp:0,nameOffsetXDp:0,nameOffsetYDp:0,nameWidthDp:90,textOffsetXDp:0,textOffsetYDp:0,textWidthDp:0})
function apply(data){ Object.assign(form,data||{}); form.publishDelayMs=Math.round((Number(form.publishDelayMs)||10000)/1000); if(form.positionXPercent==null)form.positionXPercent=50; if(form.positionYPercent==null)form.positionYPercent=52; }
function startDrag(event){dragging.value=true;event.currentTarget.setPointerCapture?.(event.pointerId);form.position='free';moveDrag(event)}
function moveDrag(event){if(!dragging.value)return;const stage=event.currentTarget.closest('.room-preview-stage');if(!stage)return;const rect=stage.getBoundingClientRect();form.positionXPercent=Math.max(2,Math.min(98,Math.round(((event.clientX-rect.left)/rect.width)*100)));form.positionYPercent=Math.max(6,Math.min(94,Math.round(((event.clientY-rect.top)/rect.height)*100)))}
function stopDrag(){dragging.value=false}
function applyTemplate(){
  if(form.template==='compact') Object.assign(form,{heightDp:42,avatarDp:28,radiusDp:12,spacingDp:6,nameSp:11,textSp:12,background:'linear-gradient(90deg,#17212B,#263746)'})
  else if(form.template==='gold') Object.assign(form,{heightDp:52,avatarDp:36,radiusDp:20,spacingDp:10,nameSp:13,textSp:14,nameColor:'#FFE2A0',textColor:'#FFFFFF',background:'linear-gradient(90deg,#35220A,#6A4817)'})
  else Object.assign(form,{heightDp:48,avatarDp:34,radiusDp:18,spacingDp:8,nameSp:12,textSp:13,nameColor:'#FFD98A',textColor:'#FFFFFF',background:'linear-gradient(90deg,#21142d,#32184c)'})
}
async function load(){
  loading.value=true; error.value=''
  const [c,q,s,b]=await Promise.all([advertisementsApi.config(),advertisementsApi.queue({limit:100,q:queueSearch.value,history:showHistory.value}),advertisementsApi.stats({days:statsDays.value}),advertisementsApi.bannedWords()])
  if(c.error){error.value=c.error.message||String(c.error);loading.value=false;return}
  const configData=c.data?.data??c.data
  apply(configData?.ticker??configData)
  applyPreviewFont()
  queue.value=q.data?.items||q.data?.data?.items||[]
  stats.value=s.data?.data??s.data??{}
  bannedWords.value=b.data?.words??b.data?.data?.words??''
  loading.value=false
}
async function uploadFont(event){const file=event.target.files?.[0];event.target.value='';if(!file)return;const ext=file.name.toLowerCase().split('.').pop();if(!['ttf','otf'].includes(ext)){error.value='يرجى اختيار ملف خط TTF أو OTF';return}fontUploading.value=true;const r=await uploadsApi.upload(file);fontUploading.value=false;if(r.error){error.value=r.error.message||String(r.error);return}const data=r.data?.data??r.data;const url=data?.url||data?.path;if(!url){error.value='تم رفع الملف لكن لم يصل رابط الخط من الخادم';return}form.customFontUrl=url;form.customFontName=file.name;form.fontFamily='custom';applyPreviewFont();success.value='تم رفع الخط. اضغط حفظ الإعدادات لتطبيقه على الرومات'}
function applyPreviewFont(){if(typeof document==='undefined')return;let style=document.getElementById('jeho-ad-preview-font');const url=String(form.customFontUrl||'').replace(/["\\)]/g,'');if(!url){style?.remove();return}if(!style){style=document.createElement('style');style.id='jeho-ad-preview-font';document.head.appendChild(style)}const format=url.toLowerCase().endsWith('.otf')?'opentype':'truetype';style.textContent='@font-face{font-family:JEHOAdvertisementCustom;src:url("'+url+'") format("'+format+'");font-display:swap;}'}
function clearFont(){form.customFontUrl='';form.customFontName='';if(form.fontFamily==='custom')form.fontFamily='default';applyPreviewFont()}
async function save(){saving.value=true;const r=await advertisementsApi.saveConfig({...form,publishDelayMs:Math.max(1000,Math.min(60000,Number(form.publishDelayMs)||10)*1000)});saving.value=false;if(r.error){error.value=r.error.message||String(r.error);return}success.value='تم حفظ إعدادات الإعلانات';apply(r.data?.data??r.data)}
async function saveWords(){savingWords.value=true;const r=await advertisementsApi.saveBannedWords(bannedWords.value);savingWords.value=false;if(r.error){error.value=r.error.message||String(r.error);return}success.value='تم حفظ الكلمات المحظورة'}
async function loadQueue(){const r=await advertisementsApi.queue({limit:100,q:queueSearch.value,history:showHistory.value});if(!r.error)queue.value=r.data?.items||r.data?.data?.items||[]}
async function loadStats(){const r=await advertisementsApi.stats({days:statsDays.value});if(!r.error)stats.value=r.data?.data??r.data??{}}
async function prioritize(id){const r=await advertisementsApi.queueAction(id,{action:'priority',queueOrder:0});if(r.error){error.value=r.error.message||String(r.error);return}await loadQueue()}
async function remove(id){const r=await advertisementsApi.queueAction(id,{action:'remove'});if(r.error){error.value=r.error.message||String(r.error);return}await loadQueue()}
onMounted(load)
</script>