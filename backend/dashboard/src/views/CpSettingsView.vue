<template>
  <div class="cp-admin-page">
    <PageHeader title="إعدادات CP" subtitle="إدارة الأنواع والهدايا والأسعار والمستويات والمكافآت وسجل العمليات. لا توجد أسعار افتراضية." >
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
      <nav class="nav nav-pills cp-admin-tabs mb-3" aria-label="أقسام إعدادات CP">
        <button type="button" class="nav-link" :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">النظرة العامة والمهام</button>
        <button type="button" class="nav-link" :class="{ active: activeTab === 'levels' }" @click="activeTab = 'levels'">الأنواع والمستويات</button>
        <button type="button" class="nav-link" :class="{ active: activeTab === 'store' }" @click="activeTab = 'store'">المتجر والمكافآت</button>
        <button type="button" class="nav-link" :class="{ active: activeTab === 'operations' }" @click="activeTab = 'operations'">الطلبات والسياسات والسجلات</button>
      </nav>
      <section v-show="activeTab === 'overview'" class="settings-card mb-3 cp-feature-control">
        <div class="d-flex align-items-start justify-content-between gap-3 flex-wrap mb-3">
          <div>
            <h3 class="settings-card-title mb-1">تشغيل قسم CP والمهمات</h3>
            <p class="form-text mb-0">إيقاف القسم يمنع بدء علاقات جديدة ويوقف عرض مهمات CP والمطالبة بمكافآتها. تبقى العلاقات والسجلات السابقة محفوظة.</p>
          </div>
          <label class="form-check form-switch mb-0">
            <input v-model="cpFeatures.globallyEnabled" class="form-check-input" type="checkbox" role="switch">
            <span class="form-check-label">{{ cpFeatures.globallyEnabled ? 'القسم مفعّل' : 'القسم متوقف' }}</span>
          </label>
        </div>
        <div class="d-flex align-items-center justify-content-between gap-3 flex-wrap border rounded-3 p-3 mb-3">
          <div>
            <div class="fw-semibold">تبويب هدايا CP في نافذة الهدايا</div>
            <div class="form-text">تحكم مستقل بإظهار أو إخفاء تبويب هدايا CP للمستخدمين، دون إيقاف نظام علاقات CP بالكامل.</div>
          </div>
          <label class="form-check form-switch mb-0">
            <input v-model="cpFeatures.giftTabEnabled" class="form-check-input" type="checkbox" role="switch">
            <span class="form-check-label">{{ cpFeatures.giftTabEnabled ? 'التبويب ظاهر' : 'التبويب مخفي' }}</span>
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
              <select v-model="task.condition" class="form-select mb-3" @change="normalizeCpTaskTarget(task)">
                <option value="points_today">جمع نقاط علاقة خلال اليوم</option>
                <option value="gift_exchange_today">أن يرسل كل شريك هدية للآخر خلال اليوم</option>
                <option value="memory_count">إضافة ذكريات مشتركة خلال اليوم</option>
              </select>
              <div class="row g-3">
                <div class="col-6">
                  <label class="form-label">{{ task.condition === 'memory_count' ? 'عدد الذكريات المطلوبة' : task.condition === 'gift_exchange_today' ? 'عدد الشريكين اللذين أرسلا هدية اليوم (2 لإكمال التبادل)' : 'نقاط العلاقة المطلوبة' }}</label>
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
      <section v-show="activeTab === 'levels'" v-for="item in types" :key="item.type" class="settings-card mb-3">
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
              <option value="">اختر هدية CP مصنّفة من قسم الهدايا</option>
              <option v-for="gift in cpGifts" :key="gift.id" :value="gift.id">{{ gift.name }} · {{ gift.coinPrice ?? gift.price ?? '—' }}</option>
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
            <label class="form-label">عدد مستويات CP</label>
            <input class="form-control" type="number" :value="item.levelsEditor.length" disabled>
          </div>
          <div class="col-12">
            <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 border rounded-3 p-3 bg-light-subtle">
              <div>
                <h4 class="h6 mb-1">مستويات العلاقة</h4>
                <p class="form-text mb-0">أضف العدد الذي تريده من المستويات. لكل مستوى نقاطه وهداياه وخلفيته ومكافآته المستقلة.</p>
              </div>
              <button type="button" class="btn btn-outline-primary btn-sm" :disabled="item.levelsEditor.length >= 100" @click="addLevel(item)">＋ إضافة مستوى</button>
            </div>
          </div>
          <div class="col-12">
            <div class="border rounded-3 p-3">
              <h4 class="h6 mb-2">تصميم المستويات ومكافآت CP من الداشبورد</h4>
              <p class="form-text">ارفع أيقونة أفقية تظهر بجانب VIP والثروة والشعبية، وإطاراً وخلفية لكل مستوى، واختر هدية مكافأة من كتالوج الهدايا العادي. التغييرات تُحفظ ضمن إعدادات المستوى وتصل للواجهات عبر API.</p>
              <div v-for="level in item.levelsEditor" :key="level.level" class="border rounded-3 p-3 mb-3">
                <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
                  <strong>المستوى {{ level.level }} · {{ level.name || level.presentation?.title || 'بدون اسم' }}</strong>
                  <div class="d-flex align-items-center gap-2 flex-wrap">
                    <span class="small text-secondary">يفتح عند {{ format(level.pointsRequired) }} نقطة</span>
                  </div>
                </div>
                <div class="row g-3">
                  <div class="col-md-4">
                    <label class="form-label">اسم المستوى / اللقب</label>
                    <input v-model.trim="level.name" class="form-control" maxlength="100" @input="syncLevelEditor(item)">
                    <label class="form-label mt-2">عنوان يظهر في الملف الشخصي</label>
                    <input v-model.trim="level.presentation.title" class="form-control" maxlength="100" @input="syncLevelEditor(item)">
                    <label class="form-label mt-2">تفاصيل المستوى التي تظهر في التطبيق</label>
                    <textarea v-model.trim="level.description" class="form-control" rows="2" maxlength="500" placeholder="اكتب تفاصيل ومزايا هذا المستوى للمستخدمين" @input="syncLevelEditor(item)"></textarea>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">أيقونة CP العرضية بجانب VIP والثروة والشعبية (صورة أو GIF)</label>
                    <input class="form-control" type="file" accept=".gif,.webp,.png,.jpg,.jpeg,image/gif,image/webp,image/png,image/jpeg" @change="uploadLevelAsset(item,level,'levelLogoUrl',$event)">
                    <input v-model.trim="level.presentation.levelLogoUrl" class="form-control mt-2" type="url" dir="ltr" placeholder="رابط الشعار" @input="syncLevelEditor(item)">
                    <img v-if="level.presentation.levelLogoUrl" :src="level.presentation.levelLogoUrl" alt="شعار المستوى" width="54" height="54" class="mt-2" style="object-fit:contain">
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">خلفية بطاقة المستوى</label>
                    <input class="form-control" type="file" accept=".webp,.png,.jpg,.jpeg,image/webp,image/png,image/jpeg" @change="uploadLevelAsset(item,level,'cardBackgroundUrl',$event)">
                    <input v-model.trim="level.presentation.cardBackgroundUrl" class="form-control mt-2" type="url" dir="ltr" placeholder="رابط الخلفية" @input="syncLevelEditor(item)">
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">هدايا مكافأة المستوى (يمكن اختيار عدة هدايا)</label>
                    <select v-model="level.rewardGiftIds" class="form-select" multiple size="5" @change="syncLevelEditor(item)">
                      <option v-for="gift in normalGifts" :key="gift.id" :value="gift.id">{{ gift.name }} · {{ gift.coinPrice ?? gift.price ?? '—' }}</option>
                    </select>
                    <div class="form-text">يمكن تحديد أكثر من هدية باستخدام الاختيار المتعدد. ستظهر كل الهدايا المختارة في معاينة مكافآت هذا المستوى داخل التطبيق.</div>
                    <div v-if="(level.rewardGiftIds || []).length" class="d-flex flex-wrap gap-2 mt-2">
                      <template v-for="giftId in level.rewardGiftIds" :key="giftId">
                        <div v-if="selectedGift({giftId})" class="d-flex align-items-center gap-2 border rounded-3 p-2">
                          <img :src="selectedGift({giftId}).animationUrl || selectedGift({giftId}).iconUrl" :alt="selectedGift({giftId}).name" width="42" height="42" style="object-fit:contain">
                          <span class="small">{{ selectedGift({giftId}).name }}</span>
                        </div>
                      </template>
                    </div>
                  </div>
                  <div class="col-md-6">
                    <label class="form-label">مكافأة تجميلية تلقائية عند بلوغ المستوى</label>
                    <select v-model="level.rewardCosmeticCode" class="form-select" @focus="$event.target.dataset.previousValue = level.rewardCosmeticCode || ''" @change="onPrimaryRewardChange(item, level, $event)">
                      <option value="">بدون مكافأة تجميلية تلقائية</option>
                      <option v-for="cosmetic in rewardCosmetics" :key="cosmetic.code" :value="cosmetic.code">{{ cosmetic.name }} · {{ cosmeticTypeLabel(cosmetic.type) }}</option>
                    </select>
                    <div class="form-text">تُضاف لحساب كل شريك مرة واحدة عند بلوغ المستوى.</div>
                    <div v-if="selectedCpReward(level.rewardCosmeticCode)" class="d-flex align-items-center gap-2 mt-2 p-2 border rounded-3">
                      <img :src="selectedCpReward(level.rewardCosmeticCode).animationUrl || selectedCpReward(level.rewardCosmeticCode).previewUrl" :alt="selectedCpReward(level.rewardCosmeticCode).name" style="width:58px;height:58px;object-fit:contain">
                      <div class="small"><strong>{{ selectedCpReward(level.rewardCosmeticCode).name }}</strong><div>{{ cosmeticTypeLabel(selectedCpReward(level.rewardCosmeticCode).type) }}</div></div>
                    </div>
                  </div>
                  <div class="col-12">
                    <label class="form-label">مكافآت إضافية لهذا المستوى</label>
                    <div class="row g-2">
                      <div v-for="cosmetic in rewardCosmetics" :key="cosmetic.code" class="col-sm-6 col-lg-4">
                        <label class="d-flex align-items-center gap-2 border rounded-3 p-2 h-100">
                          <input class="form-check-input flex-shrink-0" type="checkbox" :value="cosmetic.code" v-model="level.rewardCosmeticCodes" @change="syncLevelEditor(item)">
                          <img :src="cosmetic.animationUrl || cosmetic.previewUrl" :alt="cosmetic.name" style="width:44px;height:44px;object-fit:contain">
                          <span class="small">{{ cosmetic.name }}<span class="d-block text-secondary">{{ cosmeticTypeLabel(cosmetic.type) }}</span></span>
                        </label>
                      </div>
                    </div>
                    <div v-if="!rewardCosmetics.length" class="form-text">لا توجد عناصر تجميلية نشطة. أضف إطاراً أو شارة من قسم متجر CP أولاً.</div>
                  </div>
                  <div class="col-md-3">
                    <label class="form-label">مدة المكافأة (يوم)</label>
                    <input v-model.number="level.rewardDays" class="form-control" type="number" min="1" max="365" @change="syncLevelEditor(item)">
                  </div>
                  <div class="col-md-3">
                    <label class="form-check mt-4">
                      <input v-model="level.rewardPermanent" class="form-check-input" type="checkbox" @change="syncLevelEditor(item)">
                      <span class="form-check-label">مكافأة دائمة</span>
                    </label>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">إطار بطاقة المستوى</label>
                    <input class="form-control" type="file" accept=".gif,.webp,.png,.jpg,.jpeg" @change="uploadLevelAsset(item,level,'frameUrl',$event)">
                    <input v-model.trim="level.presentation.frameUrl" class="form-control mt-2" type="url" dir="ltr" placeholder="رابط الإطار" @input="syncLevelEditor(item)">
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">لون اسم الثنائي</label>
                    <input v-model.trim="level.presentation.nameColor" class="form-control" type="color" @input="syncLevelEditor(item)">
                  </div>
                  <div class="col-md-4">
                    <label class="form-check mt-4">
                      <input v-model="level.presentation.nameGlow" class="form-check-input" type="checkbox" @change="syncLevelEditor(item)">
                      <span class="form-check-label">توهج اسم الثنائي</span>
                    </label>
                  </div>
                  <div class="col-md-6">
                    <label class="form-label">أنيميشن هدية الثنائي لهذا المستوى (GIF / WebP)</label>
                    <input class="form-control" type="file" accept=".gif,.webp,.png" @change="uploadLevelAsset(item,level,'giftAnimationUrl',$event)">
                    <input v-model.trim="level.giftPresentation.animationUrl" class="form-control mt-2" type="url" dir="ltr" placeholder="رابط أنيميشن الهدية" @input="syncLevelEditor(item)">
                    <label class="form-check mt-2">
                      <input v-model="level.giftPresentation.showPartnerAvatars" class="form-check-input" type="checkbox" @input="syncLevelEditor(item)">
                      <span class="form-check-label">إظهار صور الشريكين داخل الهدية</span>
                    </label>
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">نسبة نقاط إضافية لهدايا الشريكين %</label>
                    <input v-model.number="level.giftBonusPercent" class="form-control" type="number" min="0" max="500" @input="syncLevelEditor(item)">
                  </div>
                  <div class="col-12">
                    <div class="border rounded-3 p-3">
                      <div class="d-flex align-items-center justify-content-between gap-2 flex-wrap mb-2">
                        <strong>مؤثر المقاعد لهذا المستوى</strong>
                        <label class="form-check form-switch mb-0">
                          <input v-model="level.seatEffect.enabled" class="form-check-input" type="checkbox" @input="syncLevelEditor(item)">
                          <span class="form-check-label">{{ level.seatEffect.enabled ? 'مفعّل' : 'متوقف' }}</span>
                        </label>
                      </div>
                      <p class="form-text">يظهر هذا المؤثر المتحرك بين مقعدي الشريكين فقط عندما يجلسان معاً في الروم، ويُختار حسب مستوى العلاقة الحالي.</p>
                      <div class="row g-2 align-items-end">
                        <div class="col-md-6">
                          <label class="form-label">صورة أو GIF أو WebP للمؤثر</label>
                          <input class="form-control" type="file" accept=".gif,.webp,.png,.jpg,.jpeg,image/gif,image/webp,image/png,image/jpeg" @change="uploadLevelAsset(item,level,'seatEffectUrl',$event)">
                          <input v-model.trim="level.seatEffect.url" class="form-control mt-2" type="url" dir="ltr" placeholder="رابط مؤثر هذا المستوى" @input="syncLevelEditor(item)">
                        </div>
                        <div class="col-md-3">
                          <label class="form-label">حجم الحركة (dp)</label>
                          <input v-model.number="level.seatEffect.sizeDp" class="form-control" type="number" min="20" max="72" @input="syncLevelEditor(item)">
                        </div>
                        <div class="col-md-3">
                          <label class="form-label">سرعة الدورة (ms)</label>
                          <input v-model.number="level.seatEffect.durationMs" class="form-control" type="number" min="500" max="10000" step="100" @input="syncLevelEditor(item)">
                        </div>
                        <div v-if="level.seatEffect.url" class="col-12 d-flex align-items-center gap-3">
                          <img :src="level.seatEffect.url" alt="معاينة مؤثر مقاعد المستوى" style="width:72px;height:72px;object-fit:contain;background:#111;border-radius:12px;padding:6px">
                          <span class="small text-secondary">معاينة المؤثر الخاص بالمستوى {{ level.level }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="col-12">
                    <div class="cp-level-live-preview border rounded-3 p-3">
                      <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-2">
                        <strong>معاينة مباشرة للمستوى {{ level.level }}</strong>
                        <span class="small text-secondary">{{ format(level.pointsRequired) }} نقطة مطلوبة</span>
                      </div>
                      <div class="cp-level-preview-card" :style="{ backgroundImage: level.presentation.cardBackgroundUrl ? 'url(' + level.presentation.cardBackgroundUrl + ')' : 'linear-gradient(135deg,#24182f,#56304b)' }">
                        <img v-if="level.presentation.frameUrl" class="cp-level-preview-frame" :src="level.presentation.frameUrl" alt="إطار المستوى">
                        <img v-if="level.presentation.levelLogoUrl" class="cp-level-preview-logo" :src="level.presentation.levelLogoUrl" alt="أيقونة CP">
                        <div class="cp-level-preview-copy">
                          <strong :style="{color:level.presentation.nameColor || '#ffffff',textShadow:level.presentation.nameGlow ? '0 0 10px currentColor' : 'none'}">{{ level.name || level.presentation.title || ('CP · المستوى ' + level.level) }}</strong>
                          <span v-if="level.presentation.title">{{ level.presentation.title }}</span>
                          <span v-else>معاً · مستوى {{ level.level }}</span>
                        </div>
                        <img v-if="level.giftPresentation.animationUrl" class="cp-level-preview-gift" :src="level.giftPresentation.animationUrl" alt="معاينة هدية CP">
                      </div>
                      <div v-for="gift in selectedGifts(level)" :key="gift.id" class="d-flex align-items-center gap-2 mt-2 small">
                        <img :src="gift.animationUrl || gift.iconUrl" :alt="gift.name" style="width:48px;height:48px;object-fit:contain">
                        <span>هدية المستوى: <strong>{{ gift.name }}</strong></span>
                      </div>
                      <div v-for="reward in rewardCosmetics.filter(entry => level.rewardCosmeticCodes.includes(entry.code) || entry.code === level.rewardCosmeticCode)" :key="reward.code" class="d-inline-flex align-items-center gap-2 mt-2 me-3 small">
                        <img :src="reward.animationUrl || reward.previewUrl" :alt="reward.name" style="width:42px;height:42px;object-fit:contain">
                        <span>{{ reward.name }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div v-if="!item.levelsEditor.length" class="text-muted small">أضف مستوى جديداً من الزر أعلاه، ثم خصص نقاطه ومكافآته وتصميمه.</div>
            </div>
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
          <button class="btn btn-aurora" :disabled="saving || Number(item.uploadingLevelAssets || 0) > 0" @click="saveType(item)">
            <span v-if="savingType === item.type || Number(item.uploadingLevelAssets || 0) > 0" class="spinner-border spinner-border-sm me-1" />
            {{ Number(item.uploadingLevelAssets || 0) > 0 ? 'انتظر اكتمال رفع صور المستويات…' : savingType === item.type ? 'جارٍ حفظ إعدادات ' + label(item.type) + '…' : 'حفظ ' + label(item.type) }}
          </button>
        </div>
      </section>

      <section v-show="activeTab === 'store'" class="settings-card mb-3 cp-store-admin">
        <div class="d-flex align-items-start justify-content-between gap-3 flex-wrap mb-2">
          <div>
            <h3 class="settings-card-title mb-1">متجر CP · الهدايا والمكافآت</h3>
            <p class="form-text mb-0">أنشئ عناصر خاصة بعالم CP، وحدد السعر والمدة والصورة. العناصر المحفوظة هنا تظهر في متجر CP داخل تطبيق Android.</p>
          </div>
          <span class="badge rounded-pill text-bg-light border">متصل بكتالوج المتجر</span>
        </div>
        <div class="alert alert-info small">كتالوج CP مستقل: العناصر التي ترفعها هنا فقط تظهر في متجر CP. عناصر المتجر العادي لا تُعرض هنا ولا يمكن شراؤها من واجهة CP.</div>
        <div class="border rounded-3 p-3 mb-4">
          <h4 class="h6 mb-1">هدايا CP الخاصة</h4>
          <p class="form-text">ارفع هدايا CP من هنا مباشرة. تُحفظ في محرك الهدايا نفسه لكن بتصنيف CP مستقل، ولا تختلط بهدايا التبويبات العادية.</p>
          <div class="row g-3 align-items-end">
            <div class="col-sm-6 col-xl-3"><label class="form-label">اسم الهدية</label><input v-model.trim="cpGiftForm.name" class="form-control" maxlength="64" placeholder="هدية قلب CP"></div>
            <div class="col-sm-6 col-xl-2"><label class="form-label">السعر بالكوين</label><input v-model.number="cpGiftForm.coinPrice" class="form-control" type="number" min="1" max="1000000000"></div>
            <div class="col-sm-6 col-xl-2"><label class="form-label">ألماس المستلم</label><input v-model.number="cpGiftForm.diamondValue" class="form-control" type="number" min="0" max="1000000000"></div>
            <div class="col-sm-6 col-xl-2"><label class="form-label">نقاط CP لكل هدية</label><input v-model.number="cpGiftForm.cpPoints" class="form-control" type="number" min="0" max="1000000000"></div>
            <div class="col-sm-6 col-xl-3"><label class="form-label">أيقونة الهدية</label><input class="form-control" type="file" accept=".gif,.webp,.png,.jpg,.jpeg,image/gif,image/webp,image/png,image/jpeg" :disabled="uploadingCpGiftIcon" @change="uploadCpGiftAsset('iconUrl',$event)"><input v-model.trim="cpGiftForm.iconUrl" class="form-control form-control-sm mt-2" dir="ltr" placeholder="رابط الأيقونة"></div>
            <div class="col-sm-6 col-xl-3"><label class="form-label">حركة الهدية (اختياري)</label><input class="form-control" type="file" accept=".gif,.webp,.mp4,.webm,image/gif,image/webp,video/mp4,video/webm" :disabled="uploadingCpGiftAnimation" @change="uploadCpGiftAsset('animationUrl',$event)"><input v-model.trim="cpGiftForm.animationUrl" class="form-control form-control-sm mt-2" dir="ltr" placeholder="رابط الحركة"></div>
            <div class="col-sm-6 col-xl-3"><label class="form-label">نوع الهدية</label><select v-model="cpGiftForm.type" class="form-select"><option value="normal">عادية</option><option value="premium">مميزة</option><option value="combo">متتابعة</option></select></div>
            <div class="col-sm-6 col-xl-3"><button class="btn btn-aurora w-100" type="button" :disabled="savingCpGift || uploadingCpGiftIcon || uploadingCpGiftAnimation" @click="createCpGift"><span v-if="savingCpGift" class="spinner-border spinner-border-sm me-1" />إضافة هدية CP</button></div>
          </div>
          <div v-if="cpGiftForm.iconUrl" class="d-flex align-items-center gap-2 mt-3"><img :src="cpGiftForm.animationUrl || cpGiftForm.iconUrl" alt="معاينة هدية CP" width="58" height="58" style="object-fit:contain"><span class="small text-secondary">معاينة الهدية قبل النشر</span></div>
          <hr class="my-3">
          <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2"><strong>هدايا CP المنشورة ({{ cpGiftItems.length }})</strong><span class="small text-secondary">لا تظهر الهدايا العادية في هذه القائمة</span></div>
          <div v-if="!cpGiftItems.length" class="text-secondary small py-2">لم تُرفع هدايا CP بعد.</div>
          <div v-for="gift in cpGiftItems" :key="gift.id" class="d-flex align-items-center gap-3 flex-wrap border-top py-3">
            <img :src="gift.animationUrl || gift.iconUrl" :alt="gift.name" width="54" height="54" style="object-fit:contain">
            <div class="flex-grow-1" style="min-width:180px"><input v-model.trim="gift.name" class="form-control form-control-sm mb-2"><div class="d-flex flex-wrap gap-2"><div><label class="form-label small mb-1">السعر</label><input v-model.number="gift.coinPrice" class="form-control form-control-sm" type="number" min="1"></div><div><label class="form-label small mb-1">ألماس</label><input v-model.number="gift.diamondValue" class="form-control form-control-sm" type="number" min="0"></div><div><label class="form-label small mb-1">نقاط CP</label><input v-model.number="gift.cpPoints" class="form-control form-control-sm" type="number" min="0"></div></div></div>
            <label class="form-check form-switch mb-0"><input v-model="gift.isActive" class="form-check-input" type="checkbox" role="switch"><span class="form-check-label">{{ gift.isActive ? 'منشورة' : 'مخفية' }}</span></label>
            <button class="btn btn-outline-primary btn-sm" type="button" :disabled="savingCpGift" @click="saveCpGift(gift)">حفظ الهدية</button>
          </div>
        </div>
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
              <option value="cp_background">خلفية CP</option>
              <option value="cp_logo">شعار CP</option>
              <option value="vip_badge">إطار صورة شخصية</option>
              <option value="host_badge">شارة خاصة</option>
              <option value="profile_card">بطاقة / إطار الملف الشخصي</option>
              <option value="level_badge">شارة مستوى</option>
              <option value="entry_effect">تأثير دخول</option>
              <option value="join_toast">بطاقة / تنبيه دخول</option>
              <option value="room_card">بطاقة الروم</option>
              <option value="nameplate">لوحة الاسم</option>
              <option value="chat_bubble">فقاعة الدردشة</option>
            </select>
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">السعر بالعملات</label>
            <input v-model.number="cpStoreForm.coinPrice" class="form-control" type="number" min="1" max="1000000000" step="1">
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
        <div v-for="item in cpStoreItems" :key="item.id" class="py-3 border-top">
          <div class="d-flex align-items-start gap-3 flex-wrap">
            <img :src="item.previewUrl" :alt="item.name" width="64" height="64" class="rounded-3 border" style="object-fit:contain;background:#fff">
            <div class="flex-grow-1" style="min-width:240px">
              <div class="row g-2">
                <div class="col-md-6"><label class="form-label small mb-1">اسم العنصر</label><input v-model.trim="item.name" class="form-control form-control-sm"></div>
                <div class="col-md-6">
                  <label class="form-label small mb-1">نوع العنصر</label>
                  <select v-model="item.type" class="form-select form-select-sm">
                    <option value="cp_background">خلفية CP</option><option value="cp_logo">شعار CP</option>
                    <option value="profile_card">بطاقة / إطار الملف الشخصي</option><option value="vip_badge">إطار صورة شخصية</option>
                    <option value="host_badge">إطار مضيف</option><option value="level_badge">شارة مستوى</option>
                    <option value="entry_effect">تأثير دخول متحرك</option><option value="join_toast">بطاقة / تنبيه دخول</option>
                    <option value="room_card">بطاقة الروم</option><option value="nameplate">لوحة الاسم</option>
                    <option value="chat_bubble">فقاعة الدردشة</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label small mb-1">رابط صورة / معاينة العنصر</label>
                  <input v-model.trim="item.previewUrl" class="form-control form-control-sm mb-1" dir="ltr">
                  <input class="form-control form-control-sm" type="file" accept=".gif,.webp,.png,.jpg,.jpeg,image/gif,image/webp,image/png,image/jpeg" :disabled="uploadingCpStorePreview" @change="uploadCpStorePreview($event, item)">
                </div>
                <div class="col-md-6"><label class="form-label small mb-1">رابط الحركة (GIF / WebP / SVGA / فيديو)</label><input v-model.trim="item.animationUrl" class="form-control form-control-sm" dir="ltr" placeholder="https://…"></div>
                <div class="col-md-6"><label class="form-label small mb-1">الوصف</label><input v-model.trim="item.description" class="form-control form-control-sm"></div>
                <div class="col-md-6"><div class="d-flex gap-2">
                  <div class="flex-fill"><label class="form-label small mb-1">السعر بالعملات</label><input v-model.number="item.coinPrice" class="form-control form-control-sm" type="number" min="1" max="1000000000"></div>
                  <div class="flex-fill"><label class="form-label small mb-1">المدة بالأيام</label><input v-model.number="item.meta.leaseDays" class="form-control form-control-sm" type="number" min="1" max="3650"></div>
                </div></div>
              </div>
              <div class="small text-secondary mt-2"><code>{{ item.code }}</code> · {{ cosmeticTypeLabel(item.type) }}</div>
            </div>
            <div class="d-flex flex-column align-items-end gap-2">
              <label class="form-check form-switch mb-0"><input v-model="item.isActive" class="form-check-input" type="checkbox" role="switch"><span class="form-check-label">{{ item.isActive ? 'منشور' : 'مخفي' }}</span></label>
              <button class="btn btn-outline-primary btn-sm" :disabled="savingCpStore" @click="saveCpStoreItem(item)">حفظ التعديلات</button>
            </div>
          </div>
        </div>
      </section>

      <section v-show="activeTab === 'operations'" class="settings-card mb-3">
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

      <section v-show="activeTab === 'operations'" class="settings-card mb-3">
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

      <section v-show="activeTab === 'operations'" class="settings-card mb-3">
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

      <section v-show="activeTab === 'operations'" class="settings-card mb-3">
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
import { computed, onMounted, ref } from 'vue'
import { cpAdminApi, giftsApi, uploadsApi } from '@/api'
import { extractList } from '@/composables/useUtils'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const activeTab = ref('overview')
const types = ref([])
const gifts = ref([])
// CP gifts remain selectable even when configured as free; visibility in the room must not be gated by price.
const cpGiftItems = computed(() => gifts.value.filter(gift => String(gift.category || '').toLowerCase() === 'cp'))
const cpGifts = computed(() => cpGiftItems.value.filter(gift => gift.isActive !== false))
// CP level rewards may select only gifts uploaded specifically for CP.
const normalGifts = computed(() => gifts.value.filter(gift => gift && gift.isActive !== false && String(gift.category || '').toLowerCase() !== 'cp' && Number(gift.coinPrice ?? gift.price ?? 0) > 0))
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
  giftTabEnabled: true,
  tasks: [
    { id: 'memory_today', enabled: true, title: 'ذكرى جديدة', description: 'أضيفوا ذكرى مشتركة اليوم', condition: 'memory_count', target: 1, rewardPoints: 10, rewardCosmeticCode: '' },
    { id: 'earn_points_today', enabled: true, title: 'نشاط مشترك', description: 'اجمعوا نقاط العلاقة اليوم', condition: 'points_today', target: 10, rewardPoints: 15, rewardCosmeticCode: '' },
  ],
})
const cpStoreItems = ref([])
const rewardCosmetics = ref([])
const loadingCpStore = ref(false)
const savingCpStore = ref(false)
const uploadingCpStorePreview = ref(false)
const cpStoreForm = ref({ code: '', name: '', type: 'cp_background', coinPrice: 1000, previewUrl: '', animationUrl: '', description: '', leaseDays: 30 })
const cpGiftForm = ref({ name: '', coinPrice: 100, diamondValue: 80, cpPoints: 10, type: 'normal', iconUrl: '', animationUrl: '' })
const savingCpGift = ref(false)
const uploadingCpGiftIcon = ref(false)
const uploadingCpGiftAnimation = ref(false)

function label(type) {
  return type === 'relationship' ? 'نظام الارتباط الموحد' : 'نظام الارتباط الموحد'
}
function format(value) { return Number(value || 0).toLocaleString('ar') }
function cosmeticTypeLabel(type) {
  return ({ cp_background: 'خلفية CP', cp_logo: 'شعار CP', profile_card: 'بطاقة الملف الشخصي', entry_effect: 'تأثير دخول', room_card: 'بطاقة الروم', nameplate: 'لوحة الاسم', chat_bubble: 'فقاعة الدردشة', level_badge: 'شارة مستوى', vip_badge: 'شارة VIP', host_badge: 'شارة مضيف', room_background: 'خلفية روم', join_toast: 'تأثير دخول' })[type] || type
}
function isCpStoreItem(item) {
  return item && (item.meta?.cpStore === true || String(item.code || '').startsWith('cp_'))
}
async function loadCpStoreItems() {
  loadingCpStore.value = true
  try {
    const response = await cpAdminApi.storeItems()
    if (response?.error) throw new Error(response.error.message || 'تعذر تحميل عناصر متجر CP')
    const allItems = extractList(payloadOf(response)).filter(Boolean)
    cpStoreItems.value = allItems.map(item => ({ ...item, meta: { ...(item.meta || {}), leaseDays: Number(item.meta?.leaseDays || 30) } }))
    rewardCosmetics.value = cpStoreItems.value.filter(item => item.isActive !== false)
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل عناصر متجر CP'
  } finally { loadingCpStore.value = false }
}
async function uploadCpStorePreview(event, target = cpStoreForm.value) {
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
    target.previewUrl = /^https?:\/\//i.test(uploadedUrl)
      ? uploadedUrl
      : new URL(uploadedUrl, window.location.origin).toString()
    success.value = target === cpStoreForm.value ? 'تم رفع صورة المعاينة. أكمل بيانات العنصر ثم أضفه إلى متجر CP.' : 'تم رفع صورة معاينة العنصر. احفظ التعديلات لاعتمادها.'
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
  if (!Number.isSafeInteger(Number(form.coinPrice)) || Number(form.coinPrice) <= 0 ||
      !Number.isInteger(Number(form.leaseDays)) || Number(form.leaseDays) < 1 || Number(form.leaseDays) > 3650) {
    error.value = 'يجب أن يكون السعر أكبر من صفر، وتكون مدة الملكية بين يوم و3650 يوماً.'
    return
  }
  savingCpStore.value = true
  try {
    const response = await cpAdminApi.createStoreItem({
      type: form.type, code: form.code, name: form.name,
      description: form.description || '', previewUrl: form.previewUrl,
      animationUrl: form.animationUrl || null, coinPrice: Number(form.coinPrice),
      isActive: true, sortOrder: 0,
      leaseDays: Number(form.leaseDays),
      meta: { cpStore: true, leaseDays: Number(form.leaseDays) },
    })
    if (response?.error) throw new Error(response.error.message || 'تعذر إضافة العنصر')
    success.value = 'تمت إضافة العنصر إلى كتالوج متجر CP.'
    cpStoreForm.value = { code: '', name: '', type: 'cp_background', coinPrice: 1000, previewUrl: '', animationUrl: '', description: '', leaseDays: 30 }
    await loadCpStoreItems()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر إضافة عنصر متجر CP' }
  finally { savingCpStore.value = false }
}
async function saveCpStoreItem(item) {
  savingCpStore.value = true; error.value = ''; success.value = ''
  try {
    const price = Number(item.coinPrice)
    const leaseDays = Number(item.meta?.leaseDays || 30)
    if (!item.name?.trim() || !/^https?:\/\//i.test(String(item.previewUrl || '').trim()) ||
        (item.animationUrl && !/^https?:\/\//i.test(String(item.animationUrl).trim())) ||
        !Number.isSafeInteger(price) || price <= 0 ||
        !Number.isInteger(leaseDays) || leaseDays < 1 || leaseDays > 3650) {
      throw new Error('تحقق من الاسم وروابط المعاينة والحركة والسعر والمدة.')
    }
    const response = await cpAdminApi.updateStoreItem(item.id, {
      name: item.name.trim(),
      type: item.type,
      previewUrl: String(item.previewUrl || '').trim(),
      animationUrl: String(item.animationUrl || '').trim() || null,
      description: String(item.description || '').trim(),
      coinPrice: price,
      leaseDays,
      isActive: !!item.isActive,
      meta: { ...(item.meta || {}), cpStore: true, leaseDays },
    })
    if (response?.error) throw new Error(response.error.message || 'تعذر حفظ العنصر')
    success.value = 'تم حفظ حالة العنصر. سيظهر التغيير في المتجر بعد تحديثه.'
    await loadCpStoreItems()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ العنصر' }
  finally { savingCpStore.value = false }
}
async function loadCpGifts() {
  const response = await giftsApi.list({ limit: 500 })
  if (response?.error) throw new Error(response.error.message || 'تعذر تحميل هدايا CP')
  gifts.value = extractList(response?.data?.data ?? response?.data ?? response)
    .filter(gift => gift && gift.isActive !== false)
}
async function uploadCpGiftAsset(field, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  const isAnimation = field === 'animationUrl'
  const allowed = isAnimation ? /\.(gif|webp|mp4|webm)$/i.test(file.name) : /\.(gif|webp|png|jpe?g)$/i.test(file.name)
  if (!allowed) { error.value = isAnimation ? 'اختر GIF أو WebP أو MP4 أو WebM للحركة.' : 'اختر GIF أو WebP أو PNG أو JPG للأيقونة.'; event.target.value = ''; return }
  if (file.size > 40 * 1024 * 1024) { error.value = 'حجم الملف يجب ألا يتجاوز 40 ميغابايت.'; event.target.value = ''; return }
  if (isAnimation) uploadingCpGiftAnimation.value = true
  else uploadingCpGiftIcon.value = true
  error.value = ''
  try {
    const response = await uploadsApi.upload(file)
    if (response?.error) throw new Error(response.error.message || 'تعذر رفع ملف الهدية')
    const data = payloadOf(response)
    const uploadedUrl = data?.url || data?.path
    if (!uploadedUrl) throw new Error('رد رفع الملف غير مكتمل.')
    cpGiftForm.value[field] = /^https?:\/\//i.test(uploadedUrl) ? uploadedUrl : new URL(uploadedUrl, window.location.origin).toString()
    success.value = 'تم رفع الملف. احفظ الهدية لإضافتها إلى قسم CP.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع ملف الهدية'
  } finally {
    if (isAnimation) uploadingCpGiftAnimation.value = false
    else uploadingCpGiftIcon.value = false
    if (event?.target) event.target.value = ''
  }
}
async function createCpGift() {
  error.value = ''; success.value = ''
  const form = cpGiftForm.value
  const coinPrice = Number(form.coinPrice), diamondValue = Number(form.diamondValue), cpPoints = Number(form.cpPoints)
  if (!form.name?.trim() || !form.iconUrl || !/^https?:\/\//i.test(form.iconUrl) ||
      !Number.isSafeInteger(coinPrice) || coinPrice < 1 || !Number.isSafeInteger(diamondValue) || diamondValue < 0 ||
      !Number.isSafeInteger(cpPoints) || cpPoints < 0) {
    error.value = 'أدخل اسم الهدية وأيقونتها وسعراً صحيحاً وقيمة ألماس ونقاط CP صحيحة.'
    return
  }
  savingCpGift.value = true
  try {
    const response = await giftsApi.create({
      name: form.name.trim(), coinPrice, diamondValue, type: form.type || 'normal',
      category: 'cp', cpRelationshipType: 'love', cpPoints,
      iconUrl: form.iconUrl, animationUrl: form.animationUrl || null,
      isActive: true, sortOrder: 0,
    })
    if (response?.error) throw new Error(response.error.message || 'تعذر إضافة هدية CP')
    cpGiftForm.value = { name: '', coinPrice: 100, diamondValue: 80, cpPoints: 10, type: 'normal', iconUrl: '', animationUrl: '' }
    success.value = 'تمت إضافة الهدية إلى كتالوج هدايا CP فقط.'
    await loadCpGifts()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر إضافة هدية CP' }
  finally { savingCpGift.value = false }
}
async function saveCpGift(gift) {
  error.value = ''; success.value = ''
  const coinPrice = Number(gift.coinPrice), diamondValue = Number(gift.diamondValue), cpPoints = Number(gift.cpPoints || 0)
  if (!gift.name?.trim() || !Number.isSafeInteger(coinPrice) || coinPrice < 1 ||
      !Number.isSafeInteger(diamondValue) || diamondValue < 0 || !Number.isSafeInteger(cpPoints) || cpPoints < 0) {
    error.value = 'تحقق من اسم الهدية والسعر والألماس ونقاط CP.'
    return
  }
  savingCpGift.value = true
  try {
    const response = await giftsApi.update(gift.id, {
      name: gift.name.trim(), coinPrice, diamondValue, cpPoints,
      category: 'cp', cpRelationshipType: gift.cpRelationshipType || 'love',
      type: gift.type || 'normal', iconUrl: gift.iconUrl,
      animationUrl: gift.animationUrl || null, isActive: gift.isActive === true,
    })
    if (response?.error) throw new Error(response.error.message || 'تعذر حفظ هدية CP')
    success.value = 'تم حفظ هدية CP.'
    await loadCpGifts()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ هدية CP' }
  finally { savingCpGift.value = false }
}
function date(value) { if (!value) return '—'; const d = new Date(value); return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('ar') }
function selectedGift(item) { return gifts.value.find(g => String(g.id) === String(item.giftId)) || null }
function selectedGifts(level) {
  const ids = Array.from(new Set([...(Array.isArray(level?.rewardGiftIds) ? level.rewardGiftIds : []), level?.rewardGiftId].map(id => String(id || '').trim()).filter(Boolean)))
  return ids.map(id => gifts.value.find(gift => String(gift.id) === id)).filter(Boolean)
}
function selectedCpReward(code) { return rewardCosmetics.value.find(item => item.code === code) || null }
function onPrimaryRewardChange(item, level, event) {
  const previous = String(event?.target?.dataset?.previousValue || '').trim()
  const current = String(level.rewardCosmeticCode || '').trim()
  if (previous && previous !== current) {
    // The primary reward is separate from the additional reward checklist.
    // Remove its previous selection so changing the primary reward cannot
    // leave the old cosmetic silently granted as an extra reward.
    level.rewardCosmeticCodes = (Array.isArray(level.rewardCosmeticCodes) ? level.rewardCosmeticCodes : [])
      .filter(code => String(code || '').trim() !== previous)
  }
  if (event?.target) event.target.dataset.previousValue = current
  syncLevelEditor(item)
}
function normalizeCpTaskTarget(task) {
  if (task?.condition === 'gift_exchange_today') task.target = 2
}
function addCpTask() {
  const id = 'cp_task_' + Math.random().toString(36).slice(2, 10)
  cpFeatures.value.tasks.push({ id, enabled: true, title: '', description: '', condition: 'points_today', target: 10, rewardPoints: 0, rewardCosmeticCode: '' })
}
function removeCpTask(id) {
  cpFeatures.value.tasks = cpFeatures.value.tasks.filter(task => task.id !== id)
}
function canonicalJson(value) {
  if (Array.isArray(value)) return value.map(canonicalJson)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonicalJson(value[key])]))
  }
  return value
}
function decodeOuterJson(value) {
  let decoded = value
  for (let depth = 0; depth < 3 && typeof decoded === 'string'; depth += 1) {
    const candidate = decoded.trim()
    if (!candidate) break
    try { decoded = JSON.parse(candidate) } catch { break }
  }
  return decoded
}
function sameJson(a, b) {
  return JSON.stringify(canonicalJson(decodeOuterJson(a))) === JSON.stringify(canonicalJson(decodeOuterJson(b)))
}
function payloadOf(response) {
  let value = response
  // safeRequest already unwraps the normal Nest envelope, but accept one or
  // more legacy { data: ... } wrappers so a transport envelope is never
  // mistaken for the persisted CP configuration itself.
  for (let depth = 0; depth < 3 && value && typeof value === 'object'; depth += 1) {
    if (value.error) return value
    if (value.success === true && 'data' in value) { value = value.data; continue }
    if ('data' in value && Object.keys(value).every(key => ['data', 'error', 'status', 'headers', 'config'].includes(key))) {
      value = value.data
      continue
    }
    break
  }
  return value || {}
}

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
    cpFeatures.value = { globallyEnabled: config.globallyEnabled !== false, giftTabEnabled: config.giftTabEnabled !== false, tasks: taskEntries }
    types.value = (config.types || []).map(item => ({
      ...item,
      priceInput: item.priceCoins == null ? '' : String(item.priceCoins),
      giftId: item.giftId || item.gift?.id || '',
      giftPoints: Number(item.giftPoints || 0),
      giftInteractionPoints: Number(item.giftInteractionPoints || 0),
      maxLevel: item.maxLevel == null ? null : Number(item.maxLevel),
      levelsText: JSON.stringify((Array.isArray(item.levels) ? item.levels : []), null, 2),
      levelsEditor: (Array.isArray(item.levels) && item.levels.length ? item.levels : [{ level: 1, pointsRequired: 0, name: 'CP', presentation: { title: 'CP', levelLogoUrl: '', frameUrl: '', cardBackgroundUrl: '', nameColor: '#ffffff', nameGlow: false }, giftPresentation: { animationUrl: '', showPartnerAvatars: true }, seatEffect: { enabled: true, sizeDp: 28, durationMs: 1800 }, rewardGiftId: '', rewardGiftIds: [], rewardCosmeticCode: '', rewardCosmeticCodes: [], rewardDays: 30, rewardPermanent: false, giftBonusPercent: 0 }]).map(level => {
        const benefits = [...(Array.isArray(level.benefits) ? level.benefits : []), ...(Array.isArray(level.rewards) ? level.rewards : [])]
        const rewardCosmeticCodes = Array.from(new Set([...(Array.isArray(level.rewardCosmeticCodes) ? level.rewardCosmeticCodes : []), ...benefits.map(entry => entry?.cosmeticCode || entry?.code)].map(code => String(code || '').trim()).filter(Boolean)))
        return { ...level, presentation: { ...(level.presentation || {}) }, giftPresentation: { showPartnerAvatars: true, ...(level.giftPresentation || {}) }, seatEffect: { enabled: true, sizeDp: 28, durationMs: 1800, ...(level.seatEffect || {}) }, giftBonusPercent: Number(level.giftBonusPercent || 0), rewardGiftId: String(level.rewardGiftId || level.giftId || ''), rewardGiftIds: Array.from(new Set([...(Array.isArray(level.rewardGiftIds) ? level.rewardGiftIds : []), level.rewardGiftId || level.giftId || ''].map(id => String(id || '').trim()).filter(Boolean))), rewardCosmeticCode: String(level.rewardCosmeticCode || ''), rewardCosmeticCodes, rewardDays: Number(level.rewardDays || 30), rewardPermanent: level.rewardPermanent === true }
      }),
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

function parseLevelInteger(value, fallback = 0) {
  if (value === null || value === undefined || String(value).trim() === '') return fallback
  const normalized = String(value)
    .replace(/[٠-٩]/g, digit => String('٠١٢٣٤٥٦٧٨٩'.indexOf(digit)))
    .replace(/[۰-۹]/g, digit => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(digit)))
    .replace(/[٬,،\s]/g, '')
  const number = Number(normalized)
  return Number.isSafeInteger(number) ? number : NaN
}

function addLevel(item) {
  const levels = Array.isArray(item.levelsEditor) ? item.levelsEditor : []
  if (levels.length >= 100) { error.value = 'وصلت إلى الحد الأقصى وهو 100 مستوى.'; return }
  const levelNumber = levels.length + 1
  levels.push({
    level: levelNumber,
    pointsRequired: Math.max(0, ...levels.map(level => Number(level.pointsRequired) || 0)) + 1,
    name: 'المستوى ' + levelNumber,
    description: '',
    presentation: { title: '', levelLogoUrl: '', logoUrl: '', frameUrl: '', badgeUrl: '', cardBackgroundUrl: '', nameColor: '#ffffff', nameGlow: false },
    giftPresentation: { animationUrl: '', showPartnerAvatars: true },
    seatEffect: { enabled: true, sizeDp: 28, durationMs: 1800, url: '' },
    rewardGiftId: '', rewardGiftIds: [], rewardCosmeticCode: '', rewardCosmeticCodes: [],
    rewardDays: 30, rewardPermanent: false, giftBonusPercent: 0, benefits: [], rewards: [],
  })
  item.levelsEditor = levels
  syncLevelEditor(item)
}
function syncLevelEditor(item) {
  const levels = (Array.isArray(item.levelsEditor) ? item.levelsEditor : []).map((level, index) => ({
    ...level,
    level: index + 1,
    pointsRequired: parseLevelInteger(level.pointsRequired, 0),
    name: String(level.name ?? '').trim(),
    presentation: { ...(level.presentation || {}) },
    giftPresentation: { ...(level.giftPresentation || {}) },
    seatEffect: { enabled: true, sizeDp: 28, durationMs: 1800, ...(level.seatEffect || {}) },
    giftBonusPercent: Math.max(0, Math.min(500, Number(level.giftBonusPercent) || 0)),
    rewardGiftIds: Array.from(new Set((Array.isArray(level.rewardGiftIds) ? level.rewardGiftIds : []).map(id => String(id || '').trim()).filter(Boolean))),
    rewardGiftId: String((Array.isArray(level.rewardGiftIds) ? level.rewardGiftIds[0] : '') || ''),
    rewardCosmeticCode: String(level.rewardCosmeticCode || ''),
    rewardCosmeticCodes: Array.from(new Set((Array.isArray(level.rewardCosmeticCodes) ? level.rewardCosmeticCodes : []).map(code => String(code || '').trim()).filter(Boolean))),
    // Keep legacy cosmetic entries out of both legacy rewards and benefits;
    // otherwise an unchecked/changed reward can still be granted from the old array.
    rewards: (Array.isArray(level.rewards) ? level.rewards : [])
      .filter(entry => !(entry && typeof entry === 'object' && (entry.cosmeticCode || entry.code))),
    benefits: [
      ...(Array.isArray(level.benefits) ? level.benefits : []).filter(entry => !(entry && typeof entry === 'object' && (entry.cosmeticCode || entry.code))),
      ...Array.from(new Set((Array.isArray(level.rewardCosmeticCodes) ? level.rewardCosmeticCodes : []).map(code => String(code || '').trim()).filter(Boolean))).map(code => ({ type: 'cosmetic', code, days: Number(level.rewardDays) || 30, permanent: level.rewardPermanent === true })),
    ],
    rewardDays: Math.max(1, Math.min(365, Number(level.rewardDays) || 30)),
    rewardPermanent: level.rewardPermanent === true,
  }))
  item.levelsText = JSON.stringify(levels, null, 2)
}

async function uploadLevelAsset(item, level, field, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  const allowed = field === 'cardBackgroundUrl'
    ? /\.(webp|png|jpe?g)$/i.test(file.name)
    : /\.(gif|webp|png|jpe?g)$/i.test(file.name)
  if (!allowed) { error.value = 'صيغة الصورة غير مدعومة لهذا الحقل.'; event.target.value = ''; return }
  if (file.size > 40 * 1024 * 1024) { error.value = 'حجم الصورة يجب ألا يتجاوز 40 ميغابايت.'; event.target.value = ''; return }
  error.value = ''
  item.uploadingLevelAssets = Number(item.uploadingLevelAssets || 0) + 1
  try {
    const response = await uploadsApi.upload(file)
    if (response?.error) throw new Error(response.error.message || 'تعذر رفع صورة المستوى')
    const data = payloadOf(response)
    const uploadedUrl = data?.url || data?.path
    if (!uploadedUrl) throw new Error('رد رفع الصورة غير مكتمل.')
    const finalUrl = /^https?:\/\//i.test(uploadedUrl) ? uploadedUrl : new URL(uploadedUrl, window.location.origin).toString()
    if (field === 'giftAnimationUrl') level.giftPresentation = { ...(level.giftPresentation || {}), animationUrl: finalUrl }
    else if (field === 'seatEffectUrl') level.seatEffect = { enabled: true, sizeDp: 28, durationMs: 1800, ...(level.seatEffect || {}), url: finalUrl }
    else level.presentation = { ...(level.presentation || {}), [field]: finalUrl }
    syncLevelEditor(item)
    success.value = 'تم رفع أصل المستوى. احفظ إعدادات النوع لاعتماده.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع أصل المستوى'
  } finally {
    item.uploadingLevelAssets = Math.max(0, Number(item.uploadingLevelAssets || 1) - 1)
    if (event?.target) event.target.value = ''
  }
}

function refreshLevelEditor(item) {
  try {
    const levels = JSON.parse(item.levelsText || '[]')
    if (!Array.isArray(levels) || levels.length < 1 || levels.length > 100) throw new Error('يجب أن يحتوي جدول CP على مستوى واحد على الأقل وبحد أقصى 100 مستوى.')
    item.levelsEditor = levels.map(level => {
      const benefits = [...(Array.isArray(level.benefits) ? level.benefits : []), ...(Array.isArray(level.rewards) ? level.rewards : [])]
      const rewardCosmeticCodes = Array.from(new Set([...(Array.isArray(level.rewardCosmeticCodes) ? level.rewardCosmeticCodes : []), ...benefits.map(entry => entry?.cosmeticCode || entry?.code)].map(code => String(code || '').trim()).filter(Boolean)))
      return { ...level, presentation: { ...(level.presentation || {}) }, giftPresentation: { showPartnerAvatars: true, ...(level.giftPresentation || {}) }, giftBonusPercent: Number(level.giftBonusPercent || 0), rewardGiftId: String(level.rewardGiftId || level.giftId || ''), rewardGiftIds: Array.from(new Set([...(Array.isArray(level.rewardGiftIds) ? level.rewardGiftIds : []), level.rewardGiftId || level.giftId || ''].map(id => String(id || '').trim()).filter(Boolean))), rewardCosmeticCode: String(level.rewardCosmeticCode || ''), rewardCosmeticCodes, rewardDays: Number(level.rewardDays || 30), rewardPermanent: level.rewardPermanent === true }
    })
  } catch (e) { error.value = e?.message || 'تعذر قراءة جدول المستويات.' }
}

async function saveType(item) {
  if (Number(item.uploadingLevelAssets || 0) > 0) {
    error.value = 'انتظر حتى يكتمل رفع جميع صور وملفات المستوى قبل الحفظ.'
    return
  }
  error.value = ''; success.value = ''; saving.value = true; savingType.value = item.type
  try {
    // Use the reactive editor as the only source of truth. Re-parsing the
    // mirrored JSON string could submit stale values after fast edits.
    let levels, rules
    try {
      levels = JSON.parse(JSON.stringify(Array.isArray(item.levelsEditor) ? item.levelsEditor : []))
      rules = JSON.parse(item.rulesText || '{}')
    } catch { throw new Error('تحقق من صيغة JSON في المستويات أو القواعد.') }
    if (!Array.isArray(levels) || !rules || typeof rules !== 'object' || Array.isArray(rules)) throw new Error('تحقق من إعدادات المستويات والقواعد.')
    if (levels.length < 1 || levels.length > 100) throw new Error('أضف مستوى واحداً على الأقل، والحد الأقصى 100 مستوى.')
    levels = levels.map((level, index) => {
      const pointsRequired = parseLevelInteger(level.pointsRequired, 0)
      if (!Number.isSafeInteger(pointsRequired) || pointsRequired < 0 || pointsRequired > 1000000000) {
        throw new Error('اكتب عدد نقاط صحيحاً من 0 إلى 1,000,000 لكل مستوى.')
      }
      return { ...level, level: index + 1, pointsRequired }
    })
    // Thresholds are sorted automatically; names and rewards stay attached to their level.
    levels.sort((a, b) => a.pointsRequired - b.pointsRequired || a.level - b.level)
    levels = levels.map((level, index) => ({ ...level, level: index + 1 }))
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
      maxLevel: levels.length,
      levels: levels.map(level => ({ ...level, benefits: [...(level.benefits || [])] })), rules,
    }
    if (String(item.priceInput).trim() !== '') {
      const price = Number(item.priceInput)
      if (!Number.isSafeInteger(price) || price <= 0) throw new Error('السعر يجب أن يكون عدداً صحيحاً موجباً.')
      payload.priceCoins = price
    } else if (item.priceCoins != null) {
      payload.priceCoins = 0
    }
    // Compare exactly what HTTP JSON can transmit. Vue editor objects may contain
    // undefined fields; Axios/JSON.stringify omits those object keys (and converts
    // undefined array entries to null). Comparing the richer in-memory object with
    // the server's JSON response falsely reports that levels/images/rewards were
    // not saved even when PostgreSQL contains the submitted configuration.
    const wirePayload = JSON.parse(JSON.stringify(payload))

    // safeRequest resolves with { data, error } even when the API rejects the save.
    // Check the normalized error explicitly; never report success for a rejected write.
    const response = await cpAdminApi.updateType(item.type, wirePayload)
    if (response?.error) {
      throw new Error(response.error.message || 'رفض الخادم حفظ إعدادات المستوى.')
    }
    // The protected PATCH endpoint already commits the settings, reads them
    // back from the database, and rejects the request if that read-back differs.
    // Use that authoritative response instead of issuing a second GET: a
    // separate request can hit another backend instance/read path and return an
    // older snapshot, falsely reporting that a successful save did not persist.
    const persistedConfig = payloadOf(response)
    const persistedType = Array.isArray(persistedConfig?.types)
      ? persistedConfig.types.find(entry => entry.type === item.type)
      : null
    if (!persistedType) {
      throw new Error('حفظ الخادم الإعدادات لكنه لم يُرجع نوع CP المحفوظ. حدّث الصفحة قبل المتابعة.')
    }
    // Never replace the server read-back with the submitted browser payload.
    // A successful HTTP response is not enough: compare the persisted values
    // returned by the API before showing a success message.
    // Use the shared canonical JSON comparator so object-key order does not
    // create false mismatches in nested level presentation assets and rewards.
    const savedTypePayload = {
      enabled: !!wirePayload.enabled,
      giftId: wirePayload.giftId || '',
      giftPoints: Number(wirePayload.giftPoints || 0),
      giftInteractionPoints: Number(wirePayload.giftInteractionPoints || 0),
      maxLevel: Number(wirePayload.maxLevel || wirePayload.levels?.length || 0),
      levels: wirePayload.levels || [],
      rules: wirePayload.rules || {},
    }
    const savedTypeResponse = {
      enabled: !!persistedType.enabled,
      giftId: persistedType.giftId || '',
      giftPoints: Number(persistedType.giftPoints || 0),
      giftInteractionPoints: Number(persistedType.giftInteractionPoints || 0),
      maxLevel: Number(persistedType.maxLevel || persistedType.levels?.length || 0),
      levels: persistedType.levels || [],
      rules: persistedType.rules || {},
    }
    if (!sameJson(savedTypeResponse, savedTypePayload)) {
      throw new Error('الخادم لم يُرجع نفس إعدادات CP التي أرسلتها بعد قراءتها من قاعدة البيانات. لم يتم اعتماد الحفظ؛ راجع اتصال قاعدة البيانات وسجلات الخادم.')
    }

    // Update this editor from the verified authoritative response. Do not call
    // load() here: it also waits for unrelated request/audit logs and the cosmetic
    // catalog, so a successful CP write could leave the save spinner running.
    item.levelsText = JSON.stringify(persistedType.levels, null, 2)
    item.levelsEditor = (persistedType.levels || []).map(level => ({
      ...level,
      presentation: { ...(level.presentation || {}) },
      giftPresentation: { showPartnerAvatars: true, ...(level.giftPresentation || {}) },
      seatEffect: { enabled: true, sizeDp: 28, durationMs: 1800, ...(level.seatEffect || {}) },
      rewardGiftIds: Array.from(new Set([
        ...(Array.isArray(level.rewardGiftIds) ? level.rewardGiftIds : []),
        level.rewardGiftId || level.giftId || '',
      ].map(id => String(id || '').trim()).filter(Boolean))),
      rewardCosmeticCodes: Array.from(new Set([
        ...(Array.isArray(level.rewardCosmeticCodes) ? level.rewardCosmeticCodes : []),
      ].map(code => String(code || '').trim()).filter(Boolean))),
    }))
    success.value = 'تم حفظ إعدادات CP والتحقق من تطابقها مع البيانات المقروءة من قاعدة البيانات.'
    // Refresh logs in the background; log latency must not block the save action.
    void loadLogs()
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
          !['memory_count', 'points_today', 'gift_exchange_today'].includes(task.condition)) {
        throw new Error('تحقق من معرّف المهمة واسمها وشرطها والهدف والمكافأة.')
      }
      seen.add(id)
      tasks[id] = { enabled: !!task.enabled, title: task.title.trim(), description: String(task.description || '').trim().slice(0, 240), condition: task.condition, target, rewardPoints, rewardCosmeticCode: String(task.rewardCosmeticCode || '').trim() }
    }
    const response = await cpAdminApi.updateFeatures({ globallyEnabled: !!cpFeatures.value.globallyEnabled, giftTabEnabled: !!cpFeatures.value.giftTabEnabled, tasks })
    if (response?.error) throw new Error(response.error.message || 'رفض الخادم حفظ إعدادات CP والمهمات.')
    await load()
    const savedTasks = Object.fromEntries(cpFeatures.value.tasks.map(task => [task.id, task]))
    if (cpFeatures.value.globallyEnabled !== !!payloadOf(response).globallyEnabled || cpFeatures.value.giftTabEnabled !== !!payloadOf(response).giftTabEnabled || Object.keys(tasks).some(id => !savedTasks[id] || savedTasks[id].enabled !== tasks[id].enabled || savedTasks[id].title !== tasks[id].title || savedTasks[id].condition !== tasks[id].condition || Number(savedTasks[id].target) !== tasks[id].target || Number(savedTasks[id].rewardPoints) !== tasks[id].rewardPoints || String(savedTasks[id].rewardCosmeticCode || '') !== tasks[id].rewardCosmeticCode)) throw new Error('لم تتطابق إعدادات CP والمهمات بعد إعادة تحميلها من الخادم.')
    success.value = 'تم حفظ إعدادات CP والمهمات والتحقق منها بعد إعادة التحميل.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ إعدادات المهمات'
  } finally { savingFeatures.value = false }
}

async function savePolicy() {
  error.value = ''; success.value = ''; saving.value = true
  try {
    let rules
    try { rules = JSON.parse(policy.value.rulesText || '{}') } catch { throw new Error('قواعد السياسة ليست JSON صالحاً.') }
    const response = await cpAdminApi.updatePolicy({ requestExpiryMinutes: Number(policy.value.requestExpiryMinutes), rules })
    if (response?.error) throw new Error(response.error.message || 'رفض الخادم حفظ سياسة CP.')
    await load()
    success.value = 'تم حفظ سياسة الطلبات وإعادة تحميلها من الخادم.'
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

<style scoped>
.cp-admin-tabs{display:flex;gap:.5rem;flex-wrap:wrap;padding:.35rem;border:1px solid var(--bs-border-color);border-radius:14px;background:var(--bs-tertiary-bg)}
.cp-admin-tabs .nav-link{border-radius:10px;font-weight:600;white-space:normal}
.cp-admin-tabs .nav-link:not(.active){color:var(--bs-body-color)}
.cp-level-live-preview{background:var(--bs-tertiary-bg)}
.cp-level-preview-card{position:relative;display:flex;align-items:center;gap:14px;min-height:112px;max-width:560px;padding:14px 18px;border-radius:16px;background-size:cover;background-position:center;overflow:hidden;color:#fff}
.cp-level-preview-card:before{content:"";position:absolute;inset:0;background:linear-gradient(90deg,rgba(0,0,0,.56),rgba(0,0,0,.08))}
.cp-level-preview-frame{position:absolute;inset:0;width:100%;height:100%;object-fit:fill;z-index:1;pointer-events:none}
.cp-level-preview-logo,.cp-level-preview-gift{position:relative;z-index:2;width:54px;height:54px;object-fit:contain;flex:0 0 auto}
.cp-level-preview-copy{position:relative;z-index:2;display:flex;flex-direction:column;gap:4px;min-width:0}
.cp-level-preview-copy span{font-size:.8rem;color:rgba(255,255,255,.8)}
.cp-level-preview-gift{margin-inline-start:auto}
</style>
