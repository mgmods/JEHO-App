<template>
  <div class="cp-admin-page">
    <PageHeader title="إعدادات علاقات CP" subtitle="إدارة الأنواع والهدايا والأسعار والمستويات والمكافآت وسجل العمليات. لا توجد أسعار افتراضية." >
      <template #actions>
        <button class="btn btn-outline-secondary btn-sm" :disabled="loading || saving" @click="load">تحديث البيانات</button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <div class="alert alert-warning small">
      تفعيل النوع يتطلب تحديد هدية موجودة وسعر موجب. لا يتم اعتماد أي سعر أو مستوى تجريبي تلقائياً.
      نظام الصوت وإعداداته غير قابل للتعديل من هذه الصفحة.
    </div>
    <LoadingSpinner v-if="loading" />

    <template v-else>
      <section class="settings-card mb-3 cp-feature-control">
        <div class="d-flex align-items-start justify-content-between gap-3 flex-wrap mb-3">
          <div>
            <h3 class="settings-card-title mb-1">تشغيل قسم CB/CP والمهمات</h3>
            <p class="form-text mb-0">إيقاف القسم يمنع بدء علاقات جديدة ويوقف عرض مهمات CP والمطالبة بمكافآتها. تبقى العلاقات والسجلات السابقة محفوظة.</p>
          </div>
          <label class="form-check form-switch mb-0">
            <input v-model="cpFeatures.globallyEnabled" class="form-check-input" type="checkbox" role="switch">
            <span class="form-check-label">{{ cpFeatures.globallyEnabled ? 'القسم مفعّل' : 'القسم متوقف' }}</span>
          </label>
        </div>
        <div class="row g-3">
          <div v-for="(task, taskIndex) in cpFeatures.tasks" :key="task.id" class="col-12 col-xl-6">
            <div class="border rounded-3 p-3 h-100">
              <div class="d-flex justify-content-between align-items-center gap-2 mb-3">
                <h4 class="h6 mb-0">مهمة CP {{ taskIndex + 1 }}</h4>
                <div class="d-flex align-items-center gap-2">
                  <label class="form-check form-switch mb-0">
                    <input v-model="task.enabled" class="form-check-input" type="checkbox" role="switch">
                    <span class="form-check-label">{{ task.enabled ? 'مفعّلة' : 'متوقفة' }}</span>
                  </label>
                  <button v-if="!['memory_today','earn_points_today'].includes(task.id)" class="btn btn-sm btn-outline-danger" type="button" @click="removeCpTask(task.id)">حذف</button>
                </div>
              </div>
              <div class="small text-secondary mb-2">معرّف المهمة: <code>{{ task.id }}</code></div>
              <label class="form-label">اسم المهمة</label>
              <input v-model.trim="task.title" class="form-control mb-2" maxlength="100" placeholder="مثال: اجمعوا نقاطًا معًا">
              <label class="form-label">وصف المهمة للعضو</label>
              <input v-model.trim="task.description" class="form-control mb-3" maxlength="240" placeholder="شرح مختصر لما يجب إنجازه">
              <label class="form-label">شرط إكمال المهمة</label>
              <select v-model="task.condition" class="form-select mb-3">
                <option value="points_today">جمع نقاط علاقة خلال اليوم</option>
                <option value="memory_count">إضافة ذكريات مشتركة خلال اليوم</option>
              </select>
              <div class="row g-3">
                <div class="col-6">
                  <label class="form-label">{{ task.condition === 'memory_count' ? 'عدد الذكريات المطلوبة' : 'نقاط العلاقة المطلوبة' }}</label>
                  <input v-model.number="task.target" class="form-control" type="number" min="1" max="1000000" step="1">
                </div>
                <div class="col-6">
                  <label class="form-label">مكافأة نقاط العلاقة</label>
                  <input v-model.number="task.rewardPoints" class="form-control" type="number" min="0" max="1000000" step="1">
                </div>
              </div>
              <label class="form-label mt-3">هدية المكافأة من متجر CP</label>
              <select v-model="task.rewardCosmeticCode" class="form-select">
                <option value="">بدون هدية إضافية</option>
                <option v-for="gift in cpStoreItems.filter(item => item.isActive)" :key="gift.code" :value="gift.code">{{ gift.name }} · {{ format(gift.coinPrice) }} عملة</option>
              </select>
              <div v-if="selectedCpReward(task.rewardCosmeticCode)" class="d-flex align-items-center gap-3 mt-2 p-2 rounded-3 bg-light">
                <img :src="selectedCpReward(task.rewardCosmeticCode).previewUrl" :alt="selectedCpReward(task.rewardCosmeticCode).name" width="64" height="64" style="object-fit:contain">
                <div class="small"><strong>{{ selectedCpReward(task.rewardCosmeticCode).name }}</strong><div>{{ cosmeticTypeLabel(selectedCpReward(task.rewardCosmeticCode).type) }} · {{ format(selectedCpReward(task.rewardCosmeticCode).coinPrice) }} عملة</div></div>
              </div>
              <p class="form-text mt-2 mb-0">تظهر المهمة للأعضاء عند تفعيلها. المكافأة تُطالب مرة واحدة يوميًا لكل مستخدم، وتُضاف الهدية المحددة إلى حقيبته.</p>
            </div>
          </div>
          <div class="col-12">
            <button class="btn btn-outline-primary" type="button" @click="addCpTask">＋ إضافة مهمة جديدة</button>
          </div>
        </div>
        <div class="d-flex justify-content-end mt-3">
          <button class="btn btn-aurora" type="button" :disabled="saving" @click="saveFeatures">
            <span v-if="savingFeatures" class="spinner-border spinner-border-sm me-1" />
            حفظ إعدادات القسم والمهمات
          </button>
        </div>
      </section>
      <section v-for="item in types" :key="item.type" class="settings-card mb-3">
        <div class="d-flex align-items-start justify-content-between gap-3 flex-wrap mb-3">
          <div>
            <h3 class="settings-card-title mb-1">{{ label(item.type) }}</h3>
            <p class="form-text mb-0">معرّف النوع: <code>{{ item.type }}</code></p>
          </div>
          <label class="form-check form-switch mb-0">
            <input v-model="item.enabled" class="form-check-input" type="checkbox" role="switch">
            <span class="form-check-label">{{ item.enabled ? 'مفعّل' : 'متوقف' }}</span>
          </label>
        </div>
        <div class="row g-3">
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">السعر بالعملات</label>
            <input v-model="item.priceInput" class="form-control" type="number" min="1" max="1000000000" step="1" placeholder="غير محدد">
            <div class="form-text">اتركه فارغاً لإبقاء السعر غير محدد.</div>
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">هدية بدء العلاقة</label>
            <select v-model="item.giftId" class="form-select">
              <option value="">اختر هدية موجودة</option>
              <option v-for="gift in gifts" :key="gift.id" :value="gift.id">{{ gift.name }} · {{ gift.coinPrice ?? gift.price ?? '—' }}</option>
            </select>
            <div v-if="selectedGift(item)" class="d-flex align-items-center gap-2 mt-2 small">
              <img v-if="selectedGift(item).iconUrl" :src="selectedGift(item).iconUrl" :alt="selectedGift(item).name" width="40" height="40" style="object-fit:contain" />
              <div>الهدية: {{ selectedGift(item).name }}<div class="form-text">تُحفظ لقطة من الصورة والأنيميشن عند إرسال الطلب.</div></div>
            </div>
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">نقاط هدية البدء</label>
            <input v-model.number="item.giftPoints" class="form-control" type="number" min="0" max="1000000000" step="1">
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">نقاط كل هدية متبادلة بين الشريكين</label>
            <input v-model.number="item.giftInteractionPoints" class="form-control" type="number" min="0" max="1000000000" step="1">
            <div class="form-text">تُمنح بعد تسجيل الهدية بنجاح، وتُخصم مرة واحدة فقط لكل سجل هدية.</div>
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">الحد الأقصى للمستوى</label>
            <input v-model.number="item.maxLevel" class="form-control" type="number" min="1" max="1000" step="1" placeholder="حسب الجدول">
          </div>
          <div class="col-12">
            <label class="form-label">جدول المستويات والمكافآت (JSON)</label>
            <textarea v-model="item.levelsText" class="form-control font-monospace" rows="5" dir="ltr" spellcheck="false" placeholder='[{"level":1,"pointsRequired":0,"reward":"إطار البداية"}]'></textarea>
            <div class="form-text">كل عنصر: level و pointsRequired، واختيارياً reward. يجب ترتيب النقاط تصاعدياً. لا يوجد عدد مستويات مفروض مسبقاً.</div>
          </div>
          <div class="col-12">
            <label class="form-label">قواعد النوع (JSON)</label>
            <textarea v-model="item.rulesText" class="form-control font-monospace" rows="3" dir="ltr" spellcheck="false" placeholder="{}"></textarea>
            <div class="form-text">قواعد إضافية خاصة بنوع العلاقة.</div>
          </div>
          <div class="col-12">
            <div class="border rounded-3 p-3">
              <div class="d-flex align-items-center justify-content-between gap-3 flex-wrap mb-3">
                <div>
                  <h4 class="h6 mb-1">تأثير العلاقة بين مقاعد الروم</h4>
                  <p class="form-text mb-0">يظهر فقط عندما يكون الشريكان معاً على المقاعد. إذا كان أحدهما خارج المقاعد، لا يظهر التأثير.</p>
                </div>
                <label class="form-check form-switch mb-0">
                  <input v-model="item.seatEffectEnabled" class="form-check-input" type="checkbox" role="switch">
                  <span class="form-check-label">{{ item.seatEffectEnabled ? 'مفعّل' : 'متوقف' }}</span>
                </label>
              </div>
              <div class="row g-3 align-items-end">
                <div class="col-md-6">
                  <label class="form-label">صورة التأثير المتحرك (GIF أو WebP) أو صورة ثابتة</label>
                  <input class="form-control" type="file" accept=".gif,.webp,.png,.jpg,.jpeg,image/gif,image/webp,image/png,image/jpeg" :disabled="item.uploadingSeatEffect" @change="uploadSeatEffect(item, $event)">
                  <div class="form-text">ارفع الصورة المرفوعة تتكرر تلقائياً كشريط متحرك بين الشريكين، والحجم قابل للتصغير لتجنب إزعاج الروم. لن يتم تغيير الصوت أو إعدادات الروم.</div>
                  <div v-if="item.uploadingSeatEffect" class="small text-primary mt-2">جارٍ رفع الصورة…</div>
                </div>
                <div class="col-md-6">
                  <label class="form-label">رابط الصورة</label>
                  <input v-model.trim="item.seatEffectUrl" class="form-control" type="url" dir="ltr" placeholder="https://…">
                </div>
                <div class="col-md-6">
                  <label class="form-label">حجم الرمز الواحد (dp)</label>
                  <input v-model.number="item.seatEffectSize" class="form-control" type="number" min="10" max="36" step="1">
                </div>
                <div class="col-md-6">
                  <label class="form-label">مدة الحركة (مللي ثانية)</label>
                  <input v-model.number="item.seatEffectDuration" class="form-control" type="number" min="500" max="10000" step="100">
                </div>
                <div v-if="item.seatEffectUrl" class="col-12 d-flex align-items-center gap-3">
                  <img :src="item.seatEffectUrl" :alt="'تأثير ' + label(item.type)" style="width:72px;height:72px;object-fit:contain;background:#111;border-radius:12px;padding:6px">
                  <span class="small text-secondary">معاينة التأثير الحالي · {{ label(item.type) }}</span>
                </div>
              </div>
            </div>
          </div>
          <div class="col-12">
            <div class="border rounded-3 p-3">
              <h4 class="h6 mb-1">خلفية بطاقة CP في الملف الشخصي</h4>
              <p class="form-text">تُطبّق على بطاقة العلاقة فقط، مع الحفاظ على أبعادها ومواقع الصور والقلب والنصوص.</p>
              <div class="row g-3 align-items-end">
                <div class="col-md-6">
                  <label class="form-label">رفع خلفية البطاقة (PNG أو JPG أو WebP)</label>
                  <input class="form-control" type="file" accept=".webp,.png,.jpg,.jpeg,image/webp,image/png,image/jpeg" :disabled="item.uploadingCpCardBackground" @change="uploadCpCardBackground(item, $event)">
                  <div v-if="item.uploadingCpCardBackground" class="small text-primary mt-2">جارٍ رفع الخلفية…</div>
                </div>
                <div class="col-md-6">
                  <label class="form-label">رابط خلفية البطاقة</label>
                  <input v-model.trim="item.cpCardBackgroundUrl" class="form-control" type="url" dir="ltr" placeholder="https://…">
                </div>
                <div v-if="item.cpCardBackgroundUrl" class="col-12">
                  <img :src="item.cpCardBackgroundUrl" :alt="'خلفية بطاقة ' + label(item.type)" style="width:100%;max-width:420px;height:110px;object-fit:cover;border-radius:12px;border:1px solid #d6b46b">
                  <button class="btn btn-sm btn-outline-secondary mt-2" type="button" @click="item.cpCardBackgroundUrl = ''">إزالة الخلفية</button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="d-flex justify-content-end mt-3">
          <button class="btn btn-aurora" :disabled="saving" @click="saveType(item)">
            <span v-if="savingType === item.type" class="spinner-border spinner-border-sm me-1" />
            حفظ {{ label(item.type) }}
          </button>
        </div>
      </section>

      <section class="settings-card mb-3 cp-store-admin">
        <div class="d-flex align-items-start justify-content-between gap-3 flex-wrap mb-2">
          <div>
            <h3 class="settings-card-title mb-1">متجر CP · العناصر التجميلية</h3>
            <p class="form-text mb-0">أنشئ عناصر خاصة بعالم CP، وحدد السعر والمدة والصورة. العناصر المحفوظة هنا تظهر في متجر CP داخل تطبيق Android.</p>
          </div>
          <span class="badge rounded-pill text-bg-light border">متصل بكتالوج المتجر</span>
        </div>
        <div class="alert alert-info small">الشراء وخصم العملات وارتداء العنصر يتم عبر الخادم. لا تضفوا عناصر CP بإعدادات شكلية فقط؛ يجب حفظ صورة معاينة صالحة وسعر واضح.</div>
        <div class="row g-3">
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">رمز العنصر (فريد)</label>
            <input v-model.trim="cpStoreForm.code" class="form-control" dir="ltr" placeholder="cp_heart_frame">
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">اسم العنصر</label>
            <input v-model.trim="cpStoreForm.name" class="form-control" placeholder="إطار قلبين">
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">الفئة</label>
            <select v-model="cpStoreForm.type" class="form-select">
              <option value="profile_card">بطاقة الملف الشخصي</option>
              <option value="entry_effect">تأثير دخول</option>
              <option value="room_card">بطاقة الروم</option>
              <option value="nameplate">لوحة الاسم</option>
              <option value="chat_bubble">فقاعة الدردشة</option>
            </select>
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">السعر بالعملات</label>
            <input v-model.number="cpStoreForm.coinPrice" class="form-control" type="number" min="0" max="1000000000" step="1">
          </div>
          <div class="col-md-6">
            <label class="form-label">رفع صورة معاينة العنصر</label>
            <input class="form-control" type="file" accept=".gif,.webp,.png,.jpg,.jpeg,image/gif,image/webp,image/png,image/jpeg" :disabled="uploadingCpStorePreview" @change="uploadCpStorePreview">
            <div v-if="uploadingCpStorePreview" class="small text-primary mt-2">جارٍ رفع المعاينة…</div>
          </div>
          <div class="col-md-6">
            <label class="form-label">رابط صورة المعاينة</label>
            <input v-model.trim="cpStoreForm.previewUrl" class="form-control" dir="ltr" placeholder="https://…">
            <div v-if="cpStoreForm.previewUrl" class="mt-2"><img :src="cpStoreForm.previewUrl" alt="معاينة عنصر CP" style="width:72px;height:72px;object-fit:contain;border-radius:12px;border:1px solid #e7c9d8;background:#fff;padding:5px"></div>
          </div>
          <div class="col-md-6">
            <label class="form-label">رابط الحركة (اختياري)</label>
            <input v-model.trim="cpStoreForm.animationUrl" class="form-control" dir="ltr" placeholder="https://…">
          </div>
          <div class="col-md-6">
            <label class="form-label">الوصف الذي يظهر للمستخدم</label>
            <input v-model.trim="cpStoreForm.description" class="form-control" placeholder="لمسة رومانسية تظهر على ملفك">
          </div>
          <div class="col-md-3">
            <label class="form-label">مدة الملكية بالأيام</label>
            <input v-model.number="cpStoreForm.leaseDays" class="form-control" type="number" min="1" max="3650" step="1">
          </div>
          <div class="col-md-3 d-flex align-items-end">
            <button class="btn btn-aurora w-100" :disabled="savingCpStore" @click="createCpStoreItem">
              <span v-if="savingCpStore" class="spinner-border spinner-border-sm me-1" />إضافة إلى متجر CP
            </button>
          </div>
        </div>
        <hr class="my-4">
        <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
          <h4 class="h6 mb-0">العناصر المنشورة ({{ cpStoreItems.length }})</h4>
          <button class="btn btn-outline-secondary btn-sm" :disabled="loadingCpStore" @click="loadCpStoreItems">تحديث عناصر المتجر</button>
        </div>
        <div v-if="loadingCpStore" class="text-secondary small py-3">جارٍ تحميل عناصر متجر CP…</div>
        <div v-else-if="!cpStoreItems.length" class="text-secondary small py-3">لا توجد عناصر CP خاصة بعد. أضف أول عنصر من النموذج أعلاه.</div>
        <div v-for="item in cpStoreItems" :key="item.id" class="d-flex align-items-center gap-3 py-3 border-top flex-wrap">
          <img :src="item.previewUrl" :alt="item.name" width="58" height="58" class="rounded-3 border" style="object-fit:contain;background:#fff">
          <div class="flex-grow-1" style="min-width:210px">
            <label class="form-label small mb-1">اسم العنصر</label>
            <input v-model.trim="item.name" class="form-control form-control-sm mb-2">
            <div class="small text-secondary mb-1">{{ cosmeticTypeLabel(item.type) }} · <code>{{ item.code }}</code></div>
            <div class="d-flex gap-2 flex-wrap">
              <div><label class="form-label small mb-1">السعر</label><input v-model.number="item.coinPrice" class="form-control form-control-sm" type="number" min="0" max="1000000000"></div>
              <div><label class="form-label small mb-1">المدة بالأيام</label><input v-model.number="item.meta.leaseDays" class="form-control form-control-sm" type="number" min="1" max="3650"></div>
            </div>
          </div>
          <label class="form-check form-switch mb-0">
            <input v-model="item.isActive" class="form-check-input" type="checkbox" role="switch">
            <span class="form-check-label">{{ item.isActive ? 'منشور' : 'مخفي' }}</span>
          </label>
          <button class="btn btn-outline-primary btn-sm" :disabled="savingCpStore" @click="saveCpStoreItem(item)">حفظ</button>
        </div>
      </section>

      <section class="settings-card mb-3">
        <h3 class="settings-card-title">سياسة الطلبات</h3>
        <p class="form-text">الرفض والانتهاء والإلغاء من صاحب الطلب يعيد المبلغ الأصلي كاملاً. لا يمكن تعطيل الاسترداد الكامل من لوحة التحكم.</p>
        <div class="row g-3 align-items-end">
          <div class="col-sm-6 col-lg-4">
            <label class="form-label">مهلة الرد بالدقائق</label>
            <input v-model.number="policy.requestExpiryMinutes" class="form-control" type="number" min="5" max="1440" step="1">
          </div>
          <div class="col-sm-6 col-lg-4">
            <label class="form-label">قواعد عامة (JSON)</label>
            <input v-model="policy.rulesText" class="form-control font-monospace" dir="ltr" placeholder="{}">
          </div>
          <div class="col-lg-4">
            <button class="btn btn-aurora" :disabled="saving" @click="savePolicy">حفظ سياسة الطلبات</button>
          </div>
        </div>
      </section>

      <section class="settings-card mb-3">
        <h3 class="settings-card-title">تعديل نقاط علاقة (إجراء إداري مسجّل)</h3>
        <p class="form-text">يُستخدم للتصحيح الإداري فقط. لا يمنح المستخدم صلاحية إرسال نقاط لنفسه، وكل تعديل يسجل في سجل التدقيق.</p>
        <div class="row g-3">
          <div class="col-md-6"><label class="form-label">معرّف العلاقة</label><input v-model.trim="pointForm.relationshipId" class="form-control font-monospace" dir="ltr" placeholder="UUID"></div>
          <div class="col-md-3"><label class="form-label">النقاط</label><input v-model.number="pointForm.points" class="form-control" type="number" min="0" step="1"></div>
          <div class="col-md-3"><label class="form-label">نوع الحدث</label><input v-model.trim="pointForm.eventType" class="form-control" placeholder="admin_adjustment"></div>
          <div class="col-md-6"><label class="form-label">نوع المرجع الفريد</label><input v-model.trim="pointForm.referenceType" class="form-control" dir="ltr" placeholder="admin_adjustment"></div>
          <div class="col-md-6"><label class="form-label">معرّف المرجع الفريد</label><input v-model.trim="pointForm.referenceId" class="form-control" dir="ltr" placeholder="Unique ID"></div>
        </div>
        <div class="mt-3 d-flex justify-content-end"><button class="btn btn-aurora" :disabled="savingPoints" @click="adjustPoints"><span v-if="savingPoints" class="spinner-border spinner-border-sm me-1" />تسجيل تعديل النقاط</button></div>
      </section>

      <section class="settings-card mb-3">
        <div class="d-flex justify-content-between align-items-center gap-2 flex-wrap mb-3">
          <div>
            <h3 class="settings-card-title mb-1">طلبات CP وسجل الاسترداد</h3>
            <p class="form-text mb-0">يعرض آخر الطلبات وحالاتها؛ لا تُعرض بيانات المحافظ الخاصة.</p>
          </div>
          <button class="btn btn-outline-secondary btn-sm" :disabled="loadingLogs" @click="loadLogs">تحديث السجلات</button>
        </div>
        <LoadingSpinner v-if="loadingLogs" />
        <div v-else class="table-responsive">
          <table class="table table-sm align-middle">
            <thead><tr><th>النوع</th><th>المرسل</th><th>المستلم</th><th>السعر</th><th>الحالة</th><th>الهدية</th><th>الاسترداد</th><th>تاريخ الطلب</th></tr></thead>
            <tbody>
              <tr v-for="row in requests" :key="row.id">
                <td>{{ label(row.type) }}</td><td>{{ row.fromUsername || row.fromUserId }}</td><td>{{ row.toUsername || row.toUserId }}</td>
                <td>{{ format(row.priceCoins) }}</td><td><span class="badge text-bg-light">{{ row.status }}</span></td>
                <td>{{ row.giftName || '—' }}</td><td>{{ row.refundedAt ? 'مسترد' : '—' }}</td><td>{{ date(row.createdAt) }}</td>
              </tr>
              <tr v-if="!requests.length"><td colspan="8" class="text-center text-secondary py-4">لا توجد طلبات مسجلة.</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="settings-card mb-3">
        <div class="d-flex justify-content-between align-items-center gap-2 flex-wrap mb-3">
          <div><h3 class="settings-card-title mb-1">سجل تدقيق CP</h3><p class="form-text mb-0">تغييرات الإعدادات وتعديلات النقاط الإدارية.</p></div>
          <button class="btn btn-outline-secondary btn-sm" :disabled="loadingLogs" @click="loadLogs">تحديث</button>
        </div>
        <div class="table-responsive">
          <table class="table table-sm align-middle">
            <thead><tr><th>الوقت</th><th>الإجراء</th><th>العنصر</th><th>التفاصيل</th><th>المدير</th></tr></thead>
            <tbody>
              <tr v-for="row in audit" :key="row.id"><td>{{ date(row.createdAt) }}</td><td>{{ row.action }}</td><td>{{ row.entityType }} / {{ row.entityId || '—' }}</td><td><code class="small">{{ JSON.stringify(row.details || {}) }}</code></td><td>{{ row.adminUserId || '—' }}</td></tr>
              <tr v-if="!audit.length"><td colspan="5" class="text-center text-secondary py-4">لا توجد أحداث تدقيق حتى الآن.</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { cpAdminApi, giftsApi, uploadsApi, cosmeticsApi } from '@/api'
import { extractList } from '@/composables/useUtils'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const types = ref([])
const gifts = ref([])
const requests = ref([])
const audit = ref([])
const loading = ref(false)
const loadingLogs = ref(false)
const saving = ref(false)
const savingType = ref('')
const savingPoints = ref(false)
const pointForm = ref({ relationshipId: '', points: 0, eventType: 'admin_adjustment', referenceType: 'admin_adjustment', referenceId: '' })
const error = ref('')
const success = ref('')
const policy = ref({ requestExpiryMinutes: 30, rulesText: '{}' })
const savingFeatures = ref(false)
const cpFeatures = ref({
  globallyEnabled: true,
  tasks: [
    { id: 'memory_today', enabled: true, title: 'ذكرى جديدة', description: 'أضيفوا ذكرى مشتركة اليوم', condition: 'memory_count', target: 1, rewardPoints: 10, rewardCosmeticCode: '' },
    { id: 'earn_points_today', enabled: true, title: 'نشاط مشترك', description: 'اجمعوا نقاط العلاقة اليوم', condition: 'points_today', target: 10, rewardPoints: 15, rewardCosmeticCode: '' },
  ],
})
const cpStoreItems = ref([])
const loadingCpStore = ref(false)
const savingCpStore = ref(false)
const uploadingCpStorePreview = ref(false)
const cpStoreForm = ref({ code: '', name: '', type: 'profile_card', coinPrice: 1000, previewUrl: '', animationUrl: '', description: '', leaseDays: 30 })

function label(type) {
  return ({ friendship: 'الصداقة', brotherhood: 'الأخوّة', love: 'الحب', engagement: 'الخطوبة', marriage: 'الزواج' })[type] || type
}
function format(value) { return Number(value || 0).toLocaleString('ar') }
function cosmeticTypeLabel(type) {
  return ({ profile_card: 'بطاقة الملف الشخصي', entry_effect: 'تأثير دخول', room_card: 'بطاقة الروم', nameplate: 'لوحة الاسم', chat_bubble: 'فقاعة الدردشة' })[type] || type
}
function isCpStoreItem(item) {
  return item && (item.meta?.cpStore === true || String(item.code || '').startsWith('cp_'))
}
async function loadCpStoreItems() {
  loadingCpStore.value = true
  try {
    const response = await cosmeticsApi.adminList()
    if (response?.error) throw new Error(response.error.message || 'تعذر تحميل عناصر متجر CP')
    cpStoreItems.value = extractList(payloadOf(response)).filter(isCpStoreItem).map(item => ({ ...item, meta: { ...(item.meta || {}), leaseDays: Number(item.meta?.leaseDays || 30) } }))
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل عناصر متجر CP'
  } finally { loadingCpStore.value = false }
}
async function uploadCpStorePreview(event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  if (!/\.(gif|webp|png|jpe?g)$/i.test(file.name)) {
    error.value = 'اختر صورة GIF أو WebP أو PNG أو JPG.'
    event.target.value = ''
    return
  }
  if (file.size > 40 * 1024 * 1024) {
    error.value = 'حجم الصورة يجب ألا يتجاوز 40 ميغابايت.'
    event.target.value = ''
    return
  }
  uploadingCpStorePreview.value = true
  error.value = ''
  try {
    const response = await uploadsApi.upload(file)
    if (response?.error) throw new Error(response.error.message || 'تعذر رفع صورة المعاينة')
    const data = payloadOf(response)
    const uploadedUrl = data?.url || data?.path
    if (!uploadedUrl) throw new Error('رد رفع الصورة غير مكتمل.')
    cpStoreForm.value.previewUrl = /^https?:\/\//i.test(uploadedUrl)
      ? uploadedUrl
      : new URL(uploadedUrl, window.location.origin).toString()
    success.value = 'تم رفع صورة المعاينة. أكمل بيانات العنصر ثم أضفه إلى متجر CP.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع صورة المعاينة'
  } finally {
    uploadingCpStorePreview.value = false
    if (event?.target) event.target.value = ''
  }
}

async function createCpStoreItem() {
  error.value = ''; success.value = ''
  const form = cpStoreForm.value
  if (!form.code || !form.name || !form.previewUrl) {
    error.value = 'أدخل رمز العنصر واسمه ورابط صورة المعاينة.'
    return
  }
  if (!/^[a-zA-Z0-9_-]{2,64}$/.test(form.code)) {
    error.value = 'رمز العنصر يجب أن يحتوي أحرفاً إنجليزية أو أرقاماً أو _ أو - فقط.'
    return
  }
  if (!/^https?:\/\//i.test(form.previewUrl)) {
    error.value = 'رابط المعاينة يجب أن يبدأ بـ https:// أو http://.'
    return
  }
  if (!Number.isSafeInteger(Number(form.coinPrice)) || Number(form.coinPrice) < 0 ||
      !Number.isInteger(Number(form.leaseDays)) || Number(form.leaseDays) < 1 || Number(form.leaseDays) > 3650) {
    error.value = 'تحقق من السعر ومدة الملكية.'
    return
  }
  savingCpStore.value = true
  try {
    const response = await cosmeticsApi.create({
      type: form.type, code: form.code, name: form.name,
      description: form.description || '', previewUrl: form.previewUrl,
      animationUrl: form.animationUrl || null, coinPrice: Number(form.coinPrice),
      isActive: true, sortOrder: 0,
      meta: { cpStore: true, leaseDays: Number(form.leaseDays) },
    })
    if (response?.error) throw new Error(response.error.message || 'تعذر إضافة العنصر')
    success.value = 'تمت إضافة العنصر إلى كتالوج متجر CP.'
    cpStoreForm.value = { code: '', name: '', type: 'profile_card', coinPrice: 1000, previewUrl: '', animationUrl: '', description: '', leaseDays: 30 }
    await loadCpStoreItems()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر إضافة عنصر متجر CP' }
  finally { savingCpStore.value = false }
}
async function saveCpStoreItem(item) {
  savingCpStore.value = true; error.value = ''; success.value = ''
  try {
    const price = Number(item.coinPrice)
    const leaseDays = Number(item.meta?.leaseDays || 30)
    if (!item.name?.trim() || !Number.isSafeInteger(price) || price < 0 ||
        !Number.isInteger(leaseDays) || leaseDays < 1 || leaseDays > 3650) {
      throw new Error('تحقق من اسم العنصر والسعر والمدة.')
    }
    const response = await cosmeticsApi.update(item.id, {
      name: item.name.trim(),
      coinPrice: price,
      isActive: !!item.isActive,
      meta: { ...(item.meta || {}), cpStore: true, leaseDays },
    })
    if (response?.error) throw new Error(response.error.message || 'تعذر حفظ العنصر')
    success.value = 'تم حفظ حالة العنصر. سيظهر التغيير في المتجر بعد تحديثه.'
    await loadCpStoreItems()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ العنصر' }
  finally { savingCpStore.value = false }
}
function date(value) { if (!value) return '—'; const d = new Date(value); return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('ar') }
function selectedGift(item) { return gifts.value.find(g => String(g.id) === String(item.giftId)) || null }
function selectedCpReward(code) { return cpStoreItems.value.find(item => item.code === code) || null }
function addCpTask() {
  const id = 'cp_task_' + Math.random().toString(36).slice(2, 10)
  cpFeatures.value.tasks.push({ id, enabled: true, title: '', description: '', condition: 'points_today', target: 10, rewardPoints: 0, rewardCosmeticCode: '' })
}
function removeCpTask(id) {
  cpFeatures.value.tasks = cpFeatures.value.tasks.filter(task => task.id !== id)
}
function payloadOf(response) { return response?.data?.data ?? response?.data ?? response ?? {} }

async function load() {
  loading.value = true; error.value = ''; success.value = ''
  try {
    const [configResponse, giftsResponse] = await Promise.all([cpAdminApi.config(), giftsApi.list({ limit: 500 })])
    const config = payloadOf(configResponse)
    gifts.value = extractList(giftsResponse?.data?.data ?? giftsResponse?.data ?? giftsResponse).filter(gift => gift && gift.isActive !== false)
    const configuredTasks = config.tasks && typeof config.tasks === 'object' ? config.tasks : {}
    const taskEntries = Object.entries(configuredTasks).map(([id, task]) => ({
      id,
      enabled: task?.enabled !== false,
      title: String(task?.title || ''),
      description: String(task?.description || ''),
      condition: task?.condition || (id === 'memory_today' ? 'memory_count' : 'points_today'),
      target: Number(task?.target || (id === 'memory_today' ? 1 : 10)),
      rewardPoints: Number(task?.rewardPoints ?? (id === 'memory_today' ? 10 : 15)),
      rewardCosmeticCode: String(task?.rewardCosmeticCode || ''),
    }))
    for (const [id, defaults] of [
      ['memory_today', { title: 'ذكرى جديدة', description: 'أضيفوا ذكرى مشتركة اليوم', condition: 'memory_count', target: 1, rewardPoints: 10 }],
      ['earn_points_today', { title: 'نشاط مشترك', description: 'اجمعوا نقاط العلاقة اليوم', condition: 'points_today', target: 10, rewardPoints: 15 }],
    ]) {
      if (!taskEntries.some(task => task.id === id)) taskEntries.unshift({ id, enabled: true, ...defaults, rewardCosmeticCode: '' })
    }
    cpFeatures.value = { globallyEnabled: config.globallyEnabled !== false, tasks: taskEntries }
    types.value = (config.types || []).map(item => ({
      ...item,
      priceInput: item.priceCoins == null ? '' : String(item.priceCoins),
      giftId: item.giftId || item.gift?.id || '',
      giftPoints: Number(item.giftPoints || 0),
      giftInteractionPoints: Number(item.giftInteractionPoints || 0),
      maxLevel: item.maxLevel == null ? null : Number(item.maxLevel),
      levelsText: JSON.stringify(item.levels || [], null, 2),
      rulesText: JSON.stringify(item.rules || {}, null, 2),
      seatEffectEnabled: item.rules?.seatEffect?.enabled !== false,
      seatEffectUrl: item.rules?.seatEffect?.url || '',
      seatEffectSize: Math.max(10, Math.min(36, Number(item.rules?.seatEffect?.sizeDp || 18))),
      seatEffectDuration: Math.max(500, Math.min(10000, Number(item.rules?.seatEffect?.durationMs || 1800))),
      cpCardBackgroundUrl: item.rules?.cardBackgroundUrl || '',
      uploadingSeatEffect: false,
      uploadingCpCardBackground: false,
    }))
    policy.value = {
      requestExpiryMinutes: Number(config.policy?.requestExpiryMinutes || 30),
      rulesText: JSON.stringify(config.policy?.rules || {}, null, 2),
    }
    await Promise.all([loadLogs(), loadCpStoreItems()])
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل إعدادات CP' }
  finally { loading.value = false }
}

async function uploadSeatEffect(item, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  const allowed = /\.(gif|webp|png|jpe?g)$/i.test(file.name)
  if (!allowed) { error.value = 'اختر صورة GIF أو WebP أو PNG أو JPG.'; event.target.value = ''; return }
  if (file.size > 40 * 1024 * 1024) { error.value = 'حجم الصورة يجب ألا يتجاوز 40 ميغابايت.'; event.target.value = ''; return }
  item.uploadingSeatEffect = true
  error.value = ''
  try {
    const response = await uploadsApi.upload(file)
    if (response?.error) throw new Error(response.error.message || 'تعذر رفع الصورة')
    const data = payloadOf(response)
    if (!data?.url && !data?.path) throw new Error('تم استلام رد رفع غير مكتمل من الخادم.')
    const uploadedUrl = data.url || data.path
    item.seatEffectUrl = /^https?:\/\//i.test(uploadedUrl)
      ? uploadedUrl
      : new URL(uploadedUrl, window.location.origin).toString()
    success.value = 'تم رفع الصورة. اضغط حفظ ' + label(item.type) + ' لاعتمادها.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع صورة التأثير'
  } finally {
    item.uploadingSeatEffect = false
    if (event?.target) event.target.value = ''
  }
}

async function uploadCpCardBackground(item, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  if (!/\.(webp|png|jpe?g)$/i.test(file.name)) {
    error.value = 'اختر خلفية بصيغة WebP أو PNG أو JPG.'
    event.target.value = ''
    return
  }
  if (file.size > 40 * 1024 * 1024) {
    error.value = 'حجم الخلفية يجب ألا يتجاوز 40 ميغابايت.'
    event.target.value = ''
    return
  }
  item.uploadingCpCardBackground = true
  error.value = ''
  try {
    const response = await uploadsApi.upload(file)
    if (response?.error) throw new Error(response.error.message || 'تعذر رفع الخلفية')
    const data = payloadOf(response)
    if (!data?.url && !data?.path) throw new Error('رد رفع الخلفية غير مكتمل.')
    const uploadedUrl = data.url || data.path
    item.cpCardBackgroundUrl = /^https?:\/\//i.test(uploadedUrl)
      ? uploadedUrl
      : new URL(uploadedUrl, window.location.origin).toString()
    success.value = 'تم رفع الخلفية. اضغط حفظ ' + label(item.type) + ' لاعتمادها.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع خلفية البطاقة'
  } finally {
    item.uploadingCpCardBackground = false
    if (event?.target) event.target.value = ''
  }
}

async function saveType(item) {
  error.value = ''; success.value = ''; saving.value = true; savingType.value = item.type
  try {
    let levels, rules
    try { levels = JSON.parse(item.levelsText || '[]'); rules = JSON.parse(item.rulesText || '{}') }
    catch { throw new Error('تحقق من صيغة JSON في المستويات أو القواعد.') }
    if (!Array.isArray(levels) || !rules || typeof rules !== 'object' || Array.isArray(rules)) throw new Error('المستويات يجب أن تكون قائمة والقواعد كائناً JSON.')
    rules.seatEffect = {
      enabled: item.seatEffectEnabled === true,
      url: String(item.seatEffectUrl || '').trim(),
      sizeDp: Math.max(10, Math.min(36, Number(item.seatEffectSize || 18))),
      durationMs: Math.max(500, Math.min(10000, Number(item.seatEffectDuration || 1800))),
    }
    rules.cardBackgroundUrl = String(item.cpCardBackgroundUrl || '').trim()
    const payload = {
      enabled: !!item.enabled,
      giftId: item.giftId || '',
      giftPoints: Number(item.giftPoints || 0),
      giftInteractionPoints: Number(item.giftInteractionPoints || 0),
      maxLevel: item.maxLevel == null || item.maxLevel === '' ? undefined : Number(item.maxLevel),
      levels, rules,
    }
    if (String(item.priceInput).trim() !== '') {
      const price = Number(item.priceInput)
      if (!Number.isSafeInteger(price) || price <= 0) throw new Error('السعر يجب أن يكون عدداً صحيحاً موجباً.')
      payload.priceCoins = price
    } else if (item.priceCoins != null) {
      payload.priceCoins = 0
    }
    await cpAdminApi.updateType(item.type, payload)
    success.value = 'تم حفظ إعدادات ' + label(item.type) + '.'
    await load()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ إعدادات النوع' }
  finally { saving.value = false; savingType.value = '' }
}

async function saveFeatures() {
  error.value = ''; success.value = ''; savingFeatures.value = true
  try {
    const tasks = {}
    const seen = new Set()
    for (const task of cpFeatures.value.tasks) {
      const id = String(task.id || '').trim()
      const target = Number(task.target)
      const rewardPoints = Number(task.rewardPoints)
      if (!/^[a-zA-Z0-9_-]{2,64}$/.test(id) || seen.has(id) ||
          !task.title?.trim() || task.title.trim().length > 100 ||
          !Number.isSafeInteger(target) || target < 1 || target > 1000000 ||
          !Number.isSafeInteger(rewardPoints) || rewardPoints < 0 || rewardPoints > 1000000 ||
          !['memory_count', 'points_today'].includes(task.condition)) {
        throw new Error('تحقق من معرّف المهمة واسمها وشرطها والهدف والمكافأة.')
      }
      seen.add(id)
      tasks[id] = { enabled: !!task.enabled, title: task.title.trim(), description: String(task.description || '').trim().slice(0, 240), condition: task.condition, target, rewardPoints, rewardCosmeticCode: String(task.rewardCosmeticCode || '').trim() }
    }
    await cpAdminApi.updateFeatures({ globallyEnabled: !!cpFeatures.value.globallyEnabled, tasks })
    success.value = 'تم حفظ تشغيل قسم CP وإعدادات المهمات.'
    await load()
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ إعدادات المهمات'
  } finally { savingFeatures.value = false }
}

async function savePolicy() {
  error.value = ''; success.value = ''; saving.value = true
  try {
    let rules
    try { rules = JSON.parse(policy.value.rulesText || '{}') } catch { throw new Error('قواعد السياسة ليست JSON صالحاً.') }
    await cpAdminApi.updatePolicy({ requestExpiryMinutes: Number(policy.value.requestExpiryMinutes), rules })
    success.value = 'تم حفظ سياسة الطلبات.'
    await load()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ السياسة' }
  finally { saving.value = false }
}


async function adjustPoints() {
  error.value = ''; success.value = ''
  if (!pointForm.value.relationshipId || !pointForm.value.referenceId || !pointForm.value.referenceType || !pointForm.value.eventType ||
      !Number.isSafeInteger(Number(pointForm.value.points)) || Number(pointForm.value.points) < 0) {
    error.value = 'أكمل بيانات تعديل النقاط بشكل صحيح.'
    return
  }
  savingPoints.value = true
  try {
    await cpAdminApi.adjustPoints({ ...pointForm.value, points: Number(pointForm.value.points), metadata: { source: 'dashboard_manual_adjustment' } })
    success.value = 'تم تسجيل الحدث. إذا كان المرجع مستخدماً سابقاً فلن تُضاف النقاط مرتين.'
    pointForm.value.referenceId = ''
    await loadLogs()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر تسجيل تعديل النقاط' }
  finally { savingPoints.value = false }
}

async function loadLogs() {
  loadingLogs.value = true
  try {
    const [requestResponse, auditResponse] = await Promise.all([cpAdminApi.requests(), cpAdminApi.audit()])
    requests.value = extractList(payloadOf(requestResponse))
    audit.value = extractList(payloadOf(auditResponse))
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل السجلات' }
  finally { loadingLogs.value = false }
}

onMounted(load)
</script>
