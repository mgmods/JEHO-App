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
                   :style="{left:(Number(form.positionXPercent)||50)+'%',top:(Number(form.positionYPercent)||52)+'%',transform:'translate(-50%,-50%)',width:'88%',minHeight:Math.max(36,Number(form.heightDp)||48)+'px',height:Math.max(36,Number(form.heightDp)||48)+'px',borderRadius:(form.radiusDp||18)+'px',background:'transparent',backgroundColor:'transparent',backgroundImage:form.backgroundImage?'url('+form.backgroundImage+')':'none',backgroundSize:'100% 100%',backgroundRepeat:'no-repeat',borderRadius:0,opacity:1,cursor:dragging?'grabbing':'grab',boxShadow:'none',border:'none',zIndex:10,touchAction:'none',gap:(form.spacingDp||0)+'px'}"
                   @pointerdown.stop.prevent="startDrag" @pointermove.stop.prevent="moveDrag" @pointerup.stop.prevent="stopDrag" @pointercancel="stopDrag">
                <img v-if="queue[0]?.senderImageUrl" :src="queue[0].senderImageUrl" alt="صورة المرسل" :style="{width:(form.avatarDp||34)+'px',height:(form.avatarDp||34)+'px',borderRadius:form.avatarShape==='circle'?'50%':'7px',objectFit:'cover',flex:'0 0 auto',transform:'translate('+(form.avatarOffsetXDp||0)+'px,'+(form.avatarOffsetYDp||0)+'px)'}">
                <div v-else class="d-flex align-items-center justify-content-center text-white" :style="{width:(form.avatarDp||34)+'px',height:(form.avatarDp||34)+'px',borderRadius:form.avatarShape==='circle'?'50%':'7px',background:'rgba(255,255,255,.16)',flex:'0 0 auto',transform:'translate('+(form.avatarOffsetXDp||0)+'px,'+(form.avatarOffsetYDp||0)+'px)'}">👤</div>
                <strong class="text-nowrap" :style="{fontSize:(form.nameSp||12)+'px',width:(form.nameWidthDp||90)+'px',overflow:'hidden',textOverflow:'ellipsis',transform:'translate('+(form.nameOffsetXDp||0)+'px,'+(form.nameOffsetYDp||0)+'px)',fontFamily:form.fontFamily==='custom'&&form.customFontUrl?'JEHOAdvertisementCustom':form.fontFamily==='serif'?'serif':form.fontFamily==='mono'?'monospace':'sans-serif',background:form.nameGradientEnabled?'linear-gradient(90deg,'+form.nameGradientStartColor+','+form.nameGradientEndColor+')':'none',backgroundClip:form.nameGradientEnabled?'text':'border-box',WebkitBackgroundClip:form.nameGradientEnabled?'text':'border-box',color:form.nameGradientEnabled?'transparent':form.nameColor}">{{ queue[0]?.senderName || 'اسم المرسل' }}</strong>
                <span class="text-nowrap overflow-hidden" :style="{flex:form.textWidthDp>0?'0 0 auto':'1 1 auto',width:form.textWidthDp>0?form.textWidthDp+'px':'auto',transform:'translate('+(form.textOffsetXDp||0)+'px,'+(form.textOffsetYDp||0)+'px)',fontSize:(form.textSp||13)+'px',fontFamily:form.fontFamily==='custom'&&form.customFontUrl?'JEHOAdvertisementCustom':form.fontFamily==='serif'?'serif':form.fontFamily==='mono'?'monospace':'sans-serif',background:form.textGradientEnabled?'linear-gradient(90deg,'+form.textGradientStartColor+','+form.textGradientEndColor+')':'none',backgroundClip:form.textGradientEnabled?'text':'border-box',WebkitBackgroundClip:form.textGradientEnabled?'text':'border-box',color:'#111827'}">{{ queue[0]?.text || 'نص الإعلان يبدأ مباشرة بجانب الاسم والصورة' }}</span>
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
              <input v-model.number="form.publishDelayMs" type="number" min="0" max="60" step="1" class="form-control">
              <div class="form-text">صفر = نشر فوري تقريباً، أو حدد مهلة من ثانية إلى دقيقة قبل ظهوره في جميع الرومات.</div>
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
            <div class="col-6"><label class="form-label small">دخول احترافي للشريط</label><select v-model="form.entryAnimation" class="form-select"><option value="slide-top">سحب من الأعلى</option><option value="slide-bottom">سحب من الأسفل</option><option value="slide-right">سحب من اليمين</option><option value="slide-left">سحب من اليسار</option><option value="fade">تلاشي ناعم</option><option value="zoom">تكبير ناعم</option></select></div>
            <div class="col-6"><label class="form-label small">خروج احترافي للشريط</label><select v-model="form.exitAnimation" class="form-select"><option value="slide-top">خروج للأعلى</option><option value="slide-bottom">خروج للأسفل</option><option value="slide-right">خروج لليمين</option><option value="slide-left">خروج لليسار</option><option value="fade">تلاشي ناعم</option><option value="zoom">تصغير ناعم</option></select></div>
            <div class="col-12"><div class="fw-semibold small mt-2">حركة عناصر الإعلان بشكل مستقل</div><div class="small text-muted mb-2">اختر اتجاه حركة النص والاسم والصورة كلٌّ على حدة، أو ثبّته أو اجعله يتلاشى</div></div>
            <div class="col-4"><label class="form-label small">حركة نص الإعلان</label><select v-model="form.textMotion" class="form-select"><option value="right-to-left">من اليمين لليسار</option><option value="left-to-right">من اليسار لليمين</option><option value="static">ثابت</option><option value="fade">تلاشي</option></select></div>
            <div class="col-4"><label class="form-label small">حركة الاسم</label><select v-model="form.nameMotion" class="form-select"><option value="right-to-left">من اليمين لليسار</option><option value="left-to-right">من اليسار لليمين</option><option value="static">ثابت</option><option value="fade">تلاشي</option></select></div>
            <div class="col-4"><label class="form-label small">حركة الصورة</label><select v-model="form.avatarMotion" class="form-select"><option value="right-to-left">من اليمين لليسار</option><option value="left-to-right">من اليسار لليمين</option><option value="static">ثابتة</option><option value="fade">تلاشي</option></select></div>
            <div class="col-6"><label class="form-label small">قالب الشريط</label><select v-model="form.template" class="form-select" @change="applyTemplate"><option value="premium-v1">فاخر</option><option value="compact">مختصر</option><option value="premium-v1">ذهبي</option></select></div>
            <div class="col-6"><label class="form-label small">مدة العرض ms</label><input v-model.number="form.displayDurationMs" type="number" min="1000" max="120000" class="form-control"></div>
            <div class="col-6">
              <label class="form-label small">الفاصل بين الإعلانات</label>
              <div class="input-group">
                <input v-model.number="form.gapValue" type="number" min="0" :max="form.gapUnit==='hours'?24:form.gapUnit==='minutes'?1440:86400" step="0.1" class="form-control">
                <select v-model="form.gapUnit" class="form-select" style="max-width:112px">
                  <option value="seconds">ثانية</option>
                  <option value="minutes">دقيقة</option>
                  <option value="hours">ساعة</option>
                </select>
              </div>
              <div class="form-text">مدة انتظار بعد اختفاء الإعلان الحالي وقبل بدء الإعلان التالي في جميع الرومات. صفر = دون فاصل.</div>
            </div>
            <div class="col-6"><label class="form-label small">سرعة الدخول px/s</label><input v-model.number="form.entrySpeedPxPerSec" type="number" min="20" max="800" class="form-control"></div>
            <div class="col-6"><label class="form-label small">سرعة الخروج px/s</label><input v-model.number="form.exitSpeedPxPerSec" type="number" min="20" max="800" class="form-control"></div>
          </div>
          <div class="form-check form-switch mt-3"><input id="reduceMotion" v-model="form.reducedMotionStatic" class="form-check-input" type="checkbox"><label class="form-check-label" for="reduceMotion">عرض ثابت عند تقليل الحركة</label></div>
        </div>
      </div>

      <div class="col-lg-4">
        <div class="glass p-3 h-100">
          <h3 class="h6 mb-3">المظهر</h3>
          <div class="composer-admin-panel mt-3">
            <div class="fw-semibold small mb-2">مظهر نافذة إرسال الإعلان داخل التطبيق</div>
            <div class="small text-muted mb-3">هذه الإعدادات تخص نافذة «إرسال إعلان» نفسها داخل الروم، وليست شريط الإعلان الذي يظهر بعد الإرسال.</div>
            <div class="row g-2">
              <div class="col-12"><label class="form-label small">رابط خلفية النافذة</label><input v-model="form.composerBackgroundImage" type="text" class="form-control" dir="ltr" placeholder="https://..."></div>
              <div class="col-6"><label class="form-label small">لون الخلفية</label><input v-model="form.composerBackgroundColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">شفافية الخلفية %</label><input v-model.number="form.composerBackgroundOpacity" type="number" min="0" max="100" class="form-control"></div>
              <div class="col-12"><label class="form-label small">رابط أيقونة زر/علامة الإعلان</label><input v-model="form.composerIconUrl" type="text" class="form-control" dir="ltr" placeholder="https://..."></div>
              <div class="col-6"><label class="form-label small">لون العنوان</label><input v-model="form.composerTitleColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">لون الوصف</label><input v-model="form.composerSubtitleColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">لون نص الإعلان المكتوب</label><input v-model="form.composerInputTextColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">لون النص الإرشادي</label><input v-model="form.composerInputHintColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">خلفية حقل الكتابة</label><input v-model="form.composerInputBackgroundColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">حدود حقل الكتابة</label><input v-model="form.composerInputBorderColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">لون نص الإلغاء</label><input v-model="form.composerCancelTextColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">خلفية الإلغاء</label><input v-model="form.composerCancelBackgroundColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">حدود الإلغاء</label><input v-model="form.composerCancelBorderColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">لون نص الإرسال</label><input v-model="form.composerSendTextColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">بداية تدرج زر الإرسال</label><input v-model="form.composerSendGradientStartColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">نهاية تدرج زر الإرسال</label><input v-model="form.composerSendGradientEndColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">حدود زر الإرسال</label><input v-model="form.composerSendBorderColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">دوران الأزرار dp</label><input v-model.number="form.composerButtonRadiusDp" type="number" min="0" max="40" class="form-control"></div>
              <div class="col-6"><label class="form-label small">حجم أيقونة الإعلان dp</label><input v-model.number="form.composerIconSizeDp" type="number" min="20" max="80" class="form-control"></div>
            </div>
          </div>

          <div class="composer-admin-panel mt-3">
            <div class="fw-semibold small mb-2">زر فتح إعلان الروم (الذي يظهر فوق إعدادات الغرفة)</div>
            <div class="small text-muted mb-3">تحكم باسم الزر وألوان النص والخلفية والحدود والحجم. تُرسل الإعدادات مباشرة إلى جميع الرومات عند الحفظ.</div>
            <div class="row g-2">
              <div class="col-12"><label class="form-label small">النص الكامل على الزر</label><input v-model="form.composerButtonText" maxlength="48" class="form-control" placeholder="مثال: ✦ إنشاء إعلان"></div>
              <div class="col-6"><label class="form-label small">لون نص الزر</label><input v-model="form.composerButtonTextColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">بداية لون الخلفية</label><input v-model="form.composerButtonBackgroundStartColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">نهاية لون الخلفية</label><input v-model="form.composerButtonBackgroundEndColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">لون الحدود</label><input v-model="form.composerButtonBorderColor" type="color" class="form-control form-control-color w-100"></div>
              <div class="col-6"><label class="form-label small">سماكة الحدود dp</label><input v-model.number="form.composerButtonBorderWidthDp" type="number" min="0" max="8" class="form-control"></div>
              <div class="col-6"><label class="form-label small">استدارة الزوايا dp</label><input v-model.number="form.composerButtonRadiusDp" type="number" min="0" max="40" class="form-control"></div>
              <div class="col-4"><label class="form-label small">عرض الزر dp (0 تلقائي)</label><input v-model.number="form.composerButtonWidthDp" type="number" min="0" max="600" class="form-control"></div>
              <div class="col-4"><label class="form-label small">ارتفاع الزر dp</label><input v-model.number="form.composerButtonHeightDp" type="number" min="32" max="80" class="form-control"></div>
              <div class="col-4"><label class="form-label small">الحشو الأفقي dp</label><input v-model.number="form.composerButtonHorizontalPaddingDp" type="number" min="0" max="80" class="form-control"></div>
              <div class="col-4"><label class="form-label small">حجم الخط sp</label><input v-model.number="form.composerButtonTextSizeSp" type="number" min="9" max="24" class="form-control"></div>
            </div>
            <div class="mt-3 d-flex justify-content-center">
              <span class="px-4 d-inline-flex align-items-center justify-content-center fw-bold"
                :style="{width:Number(form.composerButtonWidthDp)>0?form.composerButtonWidthDp+'px':'100%',minHeight:Math.max(32,Number(form.composerButtonHeightDp)||46)+'px',padding:'0 '+Math.max(0,Number(form.composerButtonHorizontalPaddingDp)||0)+'px',borderRadius:(form.composerButtonRadiusDp ?? 24)+'px',fontSize:(form.composerButtonTextSizeSp||13)+'px',color:form.composerButtonTextColor,background:'linear-gradient(90deg,'+form.composerButtonBackgroundStartColor+','+form.composerButtonBackgroundEndColor+')',border:(form.composerButtonBorderWidthDp||0)+'px solid '+form.composerButtonBorderColor}">
                {{ form.composerButtonText || 'زر إعلان الروم' }}
              </span>
            </div>
          </div>

          <div class="row g-2">
            <div class="col-6"><label class="form-label small">ارتفاع dp</label><input v-model.number="form.heightDp" type="number" class="form-control"></div>
            <div class="col-6"><label class="form-label small">الصورة dp</label><input v-model.number="form.avatarDp" type="number" class="form-control"></div>
            <div class="col-6"><label class="form-label small">اسم sp</label><input v-model.number="form.nameSp" type="number" class="form-control"></div>
            <div class="col-6"><label class="form-label small">النص sp</label><input v-model.number="form.textSp" type="number" class="form-control"></div>
            <div class="col-6"><label class="form-label small">لون الاسم</label><input v-model="form.nameColor" type="text" class="form-control" dir="ltr"></div>
            <div class="col-6"><label class="form-label small">لون النص</label><input v-model="form.textColor" type="text" class="form-control" dir="ltr"></div>
            <div class="col-6"><label class="form-label small">صورة إطار PNG الشفاف URL</label><input v-model="form.backgroundImage" type="text" class="form-control" dir="ltr"></div>
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

      <div class="col-12">
        <div class="glass p-3">
          <div class="d-flex align-items-center justify-content-between mb-3 flex-wrap gap-2">
            <div>
              <h3 class="h6 mb-1">صلاحيات الإعلان الخاصة</h3>
              <div class="small text-muted">VIP والمستخدم المحدد بالـID يرثان كل إعدادات شريط الإعلان الأساسي. الاستثناء الوحيد للمظهر هو الخلفية؛ الموضع والخط والحركة والدخول والخروج والأحجام تبقى من الإعلان الأساسي.</div>
            </div>
          </div>
          <div class="row g-3">
            <div class="col-lg-6">
              <div class="border rounded-3 p-3">
                <div class="fw-semibold small mb-2">سياسة VIP</div>
                <div class="row g-2">
                  <div class="col-6"><label class="form-label small">مدة صلاحية VIP (أيام، 0 = تتبع اشتراك VIP)</label><input v-model.number="vipPolicy.durationDays" type="number" min="0" max="3650" class="form-control"></div>
                  <div class="col-6"><label class="form-label small">عدد الإعلانات يومياً</label><input v-model.number="vipPolicy.dailyLimit" type="number" min="0" max="1000" class="form-control"></div>
                  <div class="col-6"><label class="form-label small">مدة الإعلان (ثانية)</label><input v-model.number="vipPolicy.displayDurationSeconds" type="number" min="1" max="120" class="form-control"></div>
                  <div class="col-12"><label class="form-label small">إطار VIP الشفاف — URL</label><input v-model="vipPolicy.backgroundImage" type="text" class="form-control" dir="ltr" placeholder="https://... أو /uploads/..."></div>
                  <div class="col-12"><label class="form-label small">لون/تدرج خلفية VIP</label><input v-model="vipPolicy.background" type="text" class="form-control" dir="ltr" placeholder="linear-gradient(...) أو #..."></div>
                  <div class="col-12"><label class="form-label small">رفع إطار PNG شفاف لـVIP</label><div class="d-flex gap-2"><input type="file" accept="image/*" class="form-control" :disabled="policyAssetUploading" @change="uploadPolicyAsset($event,vipPolicy,'backgroundImage')"><input v-model="vipPolicy.backgroundImage" type="text" class="form-control" dir="ltr" placeholder="أو ضع URL الخلفية"></div></div>
                  <div class="col-12"><button class="btn btn-aurora btn-sm" :disabled="policySaving" @click="saveVipPolicy">حفظ سياسة VIP</button></div>
                </div>
              </div>
            </div>
            <div class="col-lg-6">
              <div class="border rounded-3 p-3">
                <div class="fw-semibold small mb-2">مستخدم محدد بالـID</div>
                <div class="row g-2">
                  <div class="col-8"><label class="form-label small">ID المستخدم</label><input v-model="policyRef" type="text" class="form-control" dir="ltr" placeholder="Public ID أو UUID"></div>
                  <div class="col-4 d-flex align-items-end"><button class="btn btn-outline-light w-100" :disabled="policyLoading" @click="loadUserPolicy">جلب</button></div>
                  <div v-if="policyUser" class="col-12 small text-muted">المستخدم: {{ policyUser.displayName || policyUser.publicId || policyUser.id }}</div>
                  <div class="col-6"><label class="form-label small">مدة التفعيل (أيام)</label><input v-model.number="policy.durationDays" type="number" min="1" max="3650" class="form-control"></div>
                  <div class="col-6"><label class="form-label small">عدد الإعلانات يومياً</label><input v-model.number="policy.dailyLimit" type="number" min="0" max="1000" class="form-control"></div>
                  <div class="col-6"><label class="form-label small">مدة الإعلان (ثانية)</label><input v-model.number="policy.displayDurationSeconds" type="number" min="1" max="120" class="form-control"></div>
                  <div class="col-12"><label class="form-label small">إطار المستخدم المحدد الشفاف — URL</label><input v-model="policy.backgroundImage" type="text" class="form-control" dir="ltr" placeholder="https://... أو /uploads/..."></div>
                  <div class="col-12"><label class="form-label small">رفع إطار PNG شفاف للمستخدم المحدد</label><div class="d-flex gap-2"><input type="file" accept="image/*" class="form-control" :disabled="policyAssetUploading" @change="uploadPolicyAsset($event,policy,'backgroundImage')"><input v-model="policy.backgroundImage" type="text" class="form-control" dir="ltr" placeholder="أو ضع URL الخلفية"></div></div>
                  <div class="col-12"><button class="btn btn-aurora btn-sm" :disabled="policySaving" @click="saveUserPolicy">حفظ سياسة المستخدم</button></div>
                </div>
              </div>
            </div>
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

const loading=ref(true), saving=ref(false), savingWords=ref(false), fontUploading=ref(false), policyAssetUploading=ref(false), error=ref(''), success=ref(''), dragging=ref(false), policyLoading=ref(false), policySaving=ref(false), policyRef=ref(''), policyUser=ref(null)
const policy=reactive({durationDays:7,displayDurationSeconds:9,dailyLimit:0,background:'',backgroundImage:'',backgroundOpacity:92,radiusDp:18,heightDp:48,avatarDp:34,avatarShape:'circle',nameSp:12,textSp:13,nameColor:'',textColor:'',nameGradientEnabled:false,nameGradientStartColor:'#FFD98A',nameGradientEndColor:'#FF6B9D',textGradientEnabled:false,textGradientStartColor:'#111827',textGradientEndColor:'#111827',spacingDp:8,fontFamily:'default',customFontUrl:'',customFontName:'',textMotion:'right-to-left',nameMotion:'static',avatarMotion:'static',entryAnimation:'slide-right',exitAnimation:'slide-left',transitionType:'slide',direction:'rtl',position:'safe-bottom',positionXPercent:50,positionYPercent:52,customOffsetDp:0,entrySpeedPxPerSec:120,exitSpeedPxPerSec:120})
const vipPolicy=reactive({durationDays:0,displayDurationSeconds:9,dailyLimit:5,background:'',backgroundImage:'',backgroundOpacity:92,radiusDp:18,heightDp:48,avatarDp:34,avatarShape:'circle',nameSp:12,textSp:13,nameColor:'',textColor:'',nameGradientEnabled:false,nameGradientStartColor:'#FFD98A',nameGradientEndColor:'#FF6B9D',textGradientEnabled:false,textGradientStartColor:'#111827',textGradientEndColor:'#111827',spacingDp:8,fontFamily:'default',customFontUrl:'',customFontName:'',textMotion:'right-to-left',nameMotion:'static',avatarMotion:'static',entryAnimation:'gold',exitAnimation:'gold',transitionType:'slide',direction:'rtl',position:'safe-bottom',positionXPercent:50,positionYPercent:52,customOffsetDp:0,entrySpeedPxPerSec:120,exitSpeedPxPerSec:120})
const bannedWords=ref(''), queue=ref([]), stats=ref({}), statsDays=ref(7), queueSearch=ref(''), showHistory=ref(false)
const form=reactive({enabled:true,sendButtonEnabled:true,paused:false,dailyLimit:1,vipDailyLimit:5,entryAnimation:'slide-right',exitAnimation:'slide-left',maxQueueSize:500,maxTextLength:180,maxWords:30,publishDelayMs:0,position:'free',positionXPercent:50,positionYPercent:52,composerBackgroundImage:'',composerBackgroundOpacity:100,composerBackgroundColor:'#F7F9FC',composerIconUrl:'',composerTitleColor:'#172033',composerSubtitleColor:'#66758B',composerInputTextColor:'#111827',composerInputHintColor:'#9AA7B8',composerInputBackgroundColor:'#FFFFFF',composerInputBorderColor:'#D8E0EA',composerCancelTextColor:'#43536A',composerCancelBackgroundColor:'#F4F7FB',composerCancelBorderColor:'#DCE4EE',composerSendTextColor:'#241C14',composerSendGradientStartColor:'#EEC577',composerSendGradientEndColor:'#BC7E2F',composerSendBorderColor:'#EFC981',composerButtonRadiusDp:24,composerIconSizeDp:42,composerButtonText:'✦  إعلان عام',composerButtonTextColor:'#FFFFFF',composerButtonBackgroundStartColor:'#694117',composerButtonBackgroundEndColor:'#E5B85B',composerButtonBorderColor:'#EFC981',composerButtonBorderWidthDp:1,composerButtonWidthDp:0,composerButtonHorizontalPaddingDp:20,composerButtonHeightDp:46,composerButtonTextSizeSp:13,direction:'rtl',textMotion:'right-to-left',nameMotion:'static',avatarMotion:'static',displayDurationMs:9000,gapValue:1.8,gapUnit:'seconds',entrySpeedPxPerSec:120,exitSpeedPxPerSec:120,reducedMotionStatic:true,heightDp:48,avatarDp:34,nameSp:12,textSp:13,nameColor:'#9A681F',textColor:'#111827',background:'linear-gradient(90deg,#F8FAFC,#E8EEF6)',backgroundImage:'',backgroundOpacity:92,radiusDp:18,spacingDp:8,avatarShape:'circle',resetTimezone:'UTC',transitionType:'slide',fontFamily:'default',customFontUrl:'',customFontName:'',nameGradientEnabled:false,nameGradientStartColor:'#FFD98A',nameGradientEndColor:'#FF6B9D',textGradientEnabled:false,textGradientStartColor:'#111827',textGradientEndColor:'#111827',template:'premium-v1',customOffsetDp:0,avatarOffsetXDp:0,avatarOffsetYDp:0,nameOffsetXDp:0,nameOffsetYDp:0,nameWidthDp:90,textOffsetXDp:0,textOffsetYDp:0,textWidthDp:0})
function apply(data){
  Object.assign(form,data||{});
  const gapMs=Math.max(0,Number(data?.gapMs ?? form.gapMs ?? 1800)||0);
  if(gapMs>=3600000){form.gapUnit='hours';form.gapValue=Number((gapMs/3600000).toFixed(3))}
  else if(gapMs>=60000){form.gapUnit='minutes';form.gapValue=Number((gapMs/60000).toFixed(3))}
  else {form.gapUnit='seconds';form.gapValue=Number((gapMs/1000).toFixed(3))}
  delete form.gapMs;
  form.publishDelayMs=Math.round(Math.max(0,Number(form.publishDelayMs)||0)/1000);
  if(!['slide-top','slide-bottom','slide-left','slide-right','fade','zoom'].includes(form.entryAnimation))form.entryAnimation='slide-right';
  if(!['slide-top','slide-bottom','slide-left','slide-right','fade','zoom'].includes(form.exitAnimation))form.exitAnimation='slide-left';
  if(form.positionXPercent==null)form.positionXPercent=50;
  if(form.positionYPercent==null)form.positionYPercent=52;
}
function startDrag(event){dragging.value=true;event.currentTarget.setPointerCapture?.(event.pointerId);form.position='free';moveDrag(event)}
function moveDrag(event){if(!dragging.value)return;const stage=event.currentTarget.closest('.room-preview-stage');if(!stage)return;const rect=stage.getBoundingClientRect();form.positionXPercent=Math.max(2,Math.min(98,Math.round(((event.clientX-rect.left)/rect.width)*100)));form.positionYPercent=Math.max(6,Math.min(94,Math.round(((event.clientY-rect.top)/rect.height)*100)))}
function stopDrag(){dragging.value=false}
function applyTemplate(){
  if(form.template==='compact') Object.assign(form,{heightDp:42,avatarDp:28,radiusDp:12,spacingDp:6,nameSp:11,textSp:12,textColor:'#111827',background:'linear-gradient(90deg,#F8FAFC,#E9EFF7)'})
    else Object.assign(form,{heightDp:48,avatarDp:34,radiusDp:18,spacingDp:8,nameSp:12,textSp:13,nameColor:'#FFD98A',textColor:'#111827',background:'linear-gradient(90deg,#F8FAFC,#E8EEF6)'})
}
async function load(){
  loading.value=true; error.value=''
  const [c,q,s,b]=await Promise.all([advertisementsApi.config(),advertisementsApi.queue({limit:100,q:queueSearch.value,history:showHistory.value}),advertisementsApi.stats({days:statsDays.value}),advertisementsApi.bannedWords()])
  if(c.error){error.value=c.error.message||String(c.error);loading.value=false;return}
  const configData=c.data?.data??c.data
  apply(configData?.ticker??configData)
  Object.assign(vipPolicy, configData?.ticker?.vipPolicy || configData?.vipPolicy || {})
  if(configData?.ticker?.vipDailyLimit!=null) vipPolicy.dailyLimit=Number(configData.ticker.vipDailyLimit)
  applyPreviewFont()
  queue.value=q.data?.items||q.data?.data?.items||[]
  stats.value=s.data?.data??s.data??{}
  bannedWords.value=b.data?.words??b.data?.data?.words??''
  loading.value=false
}
async function uploadFont(event){const file=event.target.files?.[0];event.target.value='';if(!file)return;const ext=file.name.toLowerCase().split('.').pop();if(!['ttf','otf'].includes(ext)){error.value='يرجى اختيار ملف خط TTF أو OTF';return}fontUploading.value=true;const r=await uploadsApi.upload(file);fontUploading.value=false;if(r.error){error.value=r.error.message||String(r.error);return}const data=r.data?.data??r.data;const url=data?.url||data?.path;if(!url){error.value='تم رفع الملف لكن لم يصل رابط الخط من الخادم';return}form.customFontUrl=url;form.customFontName=file.name;form.fontFamily='custom';applyPreviewFont();success.value='تم رفع الخط. اضغط حفظ الإعدادات لتطبيقه على الرومات'}
function applyPreviewFont(){if(typeof document==='undefined')return;let style=document.getElementById('jeho-ad-preview-font');const url=String(form.customFontUrl||'').replace(/["\\)]/g,'');if(!url){style?.remove();return}if(!style){style=document.createElement('style');style.id='jeho-ad-preview-font';document.head.appendChild(style)}const format=url.toLowerCase().endsWith('.otf')?'opentype':'truetype';style.textContent='@font-face{font-family:JEHOAdvertisementCustom;src:url("'+url+'") format("'+format+'");font-display:swap;}'}
function clearFont(){form.customFontUrl='';form.customFontName='';if(form.fontFamily==='custom')form.fontFamily='default';applyPreviewFont()}
async function uploadPolicyAsset(event, target, field, font=false){
  const file=event.target.files?.[0]; event.target.value=''; if(!file)return;
  if(font && !['ttf','otf'].includes(file.name.toLowerCase().split('.').pop())){error.value='يرجى اختيار ملف خط TTF أو OTF';return}
  policyAssetUploading.value=true;
  const r=await uploadsApi.upload(file);
  policyAssetUploading.value=false;
  if(r.error){error.value=r.error.message||String(r.error);return}
  const data=r.data?.data??r.data; const url=data?.url||data?.path;
  if(!url){error.value='تم رفع الملف لكن لم يصل رابطه من الخادم';return}
  target[field]=url;
  if(font){target.customFontName=file.name;target.fontFamily='custom'}
  success.value=font?'تم رفع الخط المخصص':'تم رفع خلفية الشريط المخصصة';
}
async function save(){
  saving.value=true;
  const unitMultiplier=form.gapUnit==='hours'?3600000:form.gapUnit==='minutes'?60000:1000;
  const gapMs=Math.max(0,Math.min(86400000,(Number(form.gapValue)||0)*unitMultiplier));
  const payload={...form,gapMs,publishDelayMs:Math.max(0,Math.min(60000,Number(form.publishDelayMs)||0)*1000)};
  delete payload.gapValue; delete payload.gapUnit;
  const r=await advertisementsApi.saveConfig(payload);
  saving.value=false;
  if(r.error){error.value=r.error.message||String(r.error);return}
  success.value='تم حفظ إعدادات الإعلانات';
  apply(r.data?.data??r.data)
}
async function loadUserPolicy(){
  const ref=String(policyRef.value||'').trim(); if(!ref){error.value='أدخل ID المستخدم أولاً';return}
  policyLoading.value=true; const r=await advertisementsApi.userPolicy(ref); policyLoading.value=false
  if(r.error){error.value=r.error.message||String(r.error);return}
  const d=r.data?.data??r.data; policyUser.value=d?.user||null; Object.assign(policy,d?.policy||{}); if(d?.policy?.expiresAt) success.value='تنتهي الصلاحية: '+new Date(d.policy.expiresAt).toLocaleString(); else success.value='تم جلب صلاحية الإعلان للمستخدم'
}
async function saveVipPolicy(){
  policySaving.value=true; const r=await advertisementsApi.saveVipPolicy({...vipPolicy}); policySaving.value=false
  if(r.error){error.value=r.error.message||String(r.error);return}
  success.value='تم حفظ صلاحيات إعلانات VIP'
}
async function saveUserPolicy(){
  const ref=String(policyRef.value||'').trim(); if(!ref){error.value='أدخل ID المستخدم';return}
  policySaving.value=true; const r=await advertisementsApi.saveUserPolicy({userId:ref,...policy}); policySaving.value=false
  if(r.error){error.value=r.error.message||String(r.error);return}
  success.value='تم حفظ صلاحية الإعلان للمستخدم'
}
async function saveWords(){savingWords.value=true;const r=await advertisementsApi.saveBannedWords(bannedWords.value);savingWords.value=false;if(r.error){error.value=r.error.message||String(r.error);return}success.value='تم حفظ الكلمات المحظورة'}
async function loadQueue(){const r=await advertisementsApi.queue({limit:100,q:queueSearch.value,history:showHistory.value});if(!r.error)queue.value=r.data?.items||r.data?.data?.items||[]}
async function loadStats(){const r=await advertisementsApi.stats({days:statsDays.value});if(!r.error)stats.value=r.data?.data??r.data??{}}
async function prioritize(id){const r=await advertisementsApi.queueAction(id,{action:'priority',queueOrder:0});if(r.error){error.value=r.error.message||String(r.error);return}await loadQueue()}
async function remove(id){const r=await advertisementsApi.queueAction(id,{action:'remove'});if(r.error){error.value=r.error.message||String(r.error);return}await loadQueue()}
onMounted(load)
</script>