<template>
  <div class="settings-page">
    <PageHeader :title="t('settings.title')" :subtitle="t('settings.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="loading" @click="reloadActive">
          {{ t('common.reload') }}
        </button>
        <button
          v-if="isCoreTab && canWriteSettings"
          class="btn btn-aurora btn-sm"
          type="button"
          :disabled="saving || loading"
          @click="save"
        >
          <span v-if="saving" class="spinner-border spinner-border-sm me-1" />
          {{ t('settings.saveSettings') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="settings-shell">
      <nav class="settings-rail" aria-label="Settings sections">
        <button
          v-for="tab in settingTabs"
          :key="tab.id"
          type="button"
          class="settings-rail-item"
          :class="{ active: settingsTab === tab.id }"
          @click="selectTab(tab.id)"
        >
          <i class="bi" :class="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <div class="settings-main">
        <LoadingSpinner v-if="loading && isCoreTab" />

        <template v-else-if="settingsTab === 'payment'">
          <PaymentSettingsPanel />
          <FourthwallPaymentPanel />
          <ShamCashPaymentPanel />
        </template>



        <template v-else-if="settingsTab === 'section-background'">
          <SectionBackgroundPanel />
        </template>

        <template v-else-if="settingsTab === 'staff-identity'">
          <section class="settings-card">
            <h3 class="settings-card-title">هوية السوبر أدمن والأدمن والمانجر</h3>
            <p class="form-text mb-3">ارفع أصولاً مستقلة لكل رتبة. بعد الحفظ تُستخدم الهوية المخصصة للرتبة فقط، ولا تُعرض هذه الأصول كعناصر قابلة للشراء في المتجر.</p>
            <div v-if="staffIdentityLoading" class="py-4"><LoadingSpinner /></div>
            <div v-else>
              <div v-for="role in staffIdentityRoles" :key="role.key" class="border rounded-4 p-3 mb-3">
                <div class="d-flex align-items-center gap-2 mb-3"><span class="badge rounded-pill" :style="{background: staffIdentityForm[role.key].accentColor, color:'#111'}">{{ role.title }}</span><span class="small text-secondary">أصول حصرية لهذه الرتبة</span></div>
                <div class="row g-3 mb-3"><div class="col-md-6"><label class="form-label">اسم الرتبة الظاهر</label><input v-model="staffIdentityForm[role.key].label" maxlength="32" class="form-control" /></div>
                  <div class="col-md-6"><label class="form-label">لون الهوية</label><div class="d-flex gap-2"><input v-model="staffIdentityForm[role.key].accentColor" type="color" class="form-control form-control-color" /><input v-model="staffIdentityForm[role.key].accentColor" maxlength="7" class="form-control" dir="ltr" /></div></div></div>
                <div class="row g-3"><div v-for="asset in staffIdentityAssets" :key="role.key + '-' + asset.key" class="col-md-6 col-xl-4"><div class="h-100 rounded-3 border p-3">
                  <label class="form-label fw-semibold">{{ asset.title }}</label><div class="d-flex align-items-center justify-content-center rounded-3 bg-body-tertiary mb-2 overflow-hidden" style="min-height:110px">
                    <SvgaPreview v-if="staffIdentityForm[role.key][asset.key] && staffIdentityIsSvga(staffIdentityForm[role.key][asset.key])" :src="staffIdentityForm[role.key][asset.key]" />
                    <video v-else-if="staffIdentityForm[role.key][asset.key] && staffIdentityIsVideo(staffIdentityForm[role.key][asset.key])" :src="resolveAsset(staffIdentityForm[role.key][asset.key])" controls muted loop playsinline style="max-width:100%;max-height:140px" />
                    <img v-else-if="staffIdentityForm[role.key][asset.key]" :src="resolveAsset(staffIdentityForm[role.key][asset.key])" :alt="asset.title" style="max-width:100%;max-height:120px;object-fit:contain" /><span v-else class="small text-secondary">لم يتم رفع ملف</span></div>
                  <input :ref="el => setStaffIdentityInput(role.key, asset.key, el)" type="file" class="form-control form-control-sm mb-2" :accept="asset.accept" @change="uploadStaffIdentityAsset(role.key, asset, $event)" />
                  <div class="d-flex gap-2 flex-wrap"><button type="button" class="btn btn-outline-secondary btn-sm" :disabled="staffIdentityUploading === role.key + ':' + asset.key" @click="chooseStaffIdentityFile(role.key, asset.key)">{{ staffIdentityUploading === role.key + ':' + asset.key ? 'جارٍ الرفع…' : 'رفع ملف' }}</button>
                    <button v-if="staffIdentityForm[role.key][asset.key]" type="button" class="btn btn-link btn-sm text-danger" @click="staffIdentityForm[role.key][asset.key] = ''">إزالة</button></div><div class="form-text mt-2">{{ asset.help }}</div>
                </div></div></div></div>
              <div class="alert alert-info small">الإطار، الشارة، بطاقة الاسم، بطاقة الرسائل، والدخولية محفوظة كإعدادات هوية رتبة وليست عناصر متجر.</div>
              <div class="d-flex justify-content-end"><button type="button" class="btn btn-aurora" :disabled="staffIdentitySaving || staffIdentityLoading || !auth.can('settings', 'write')" @click="saveStaffIdentity"><span v-if="staffIdentitySaving" class="spinner-border spinner-border-sm me-1"></span>حفظ هوية الرتب</button></div>
            </div>
          </section>
        </template>
        <template v-else-if="settingsTab === 'mysterious-man'">
          <section class="settings-card">
            <h3 class="settings-card-title">إعدادات الرجل الغامض</h3>
            <p class="form-text mb-3">
              ارفع هوية موحّدة للحسابات التي فعّلت وضع الرجل الغامض. عند إيقاف الميزة هنا، تُلغى فوراً لكل المستخدمين وتعود هوياتهم الأصلية، ولا تعود تلقائياً عند إعادة تشغيلها.
            </p>
            <div class="border rounded-3 p-3 mb-3">
              <div class="form-check form-switch">
                <input id="mysterious-man-enabled" v-model="mysteryForm.enabled" class="form-check-input" type="checkbox" role="switch" />
                <label class="form-check-label fw-semibold" for="mysterious-man-enabled">تفعيل ميزة الرجل الغامض لجميع المستخدمين</label>
              </div>
              <div class="small text-secondary mt-1">عند إيقاف هذا الخيار، سيتم إلغاء التفعيل المحفوظ لجميع الحسابات. يجب على المستخدم تفعيل الميزة مجدداً بعد تشغيلها، مع وجود اشتراك VIP مؤهل وساري.</div>
            </div>
            <div class="border rounded-3 p-3 mb-3">
              <div class="fw-semibold mb-2">اشتراكات VIP المؤهلة للميزة</div>
              <div class="d-flex flex-wrap gap-3">
                <div v-for="level in [1,2,3,4,5,6,7]" :key="level" class="form-check">
                  <input :id="'mysterious-vip-' + level" v-model="mysteryForm.eligibleVipLevels" class="form-check-input" type="checkbox" :value="level" />
                  <label class="form-check-label" :for="'mysterious-vip-' + level">VIP{{ level }}</label>
                </div>
              </div>
              <div class="small text-secondary mt-1">انتهاء الاشتراك أو إزالته من المستويات المؤهلة يعيد الحساب تلقائياً لوضعه الطبيعي.</div>
            </div>
            <div class="row g-3 mb-3">
              <div class="col-md-4">
                <label class="form-label">نوع الدخولية الموحدة</label>
                <select v-model="mysteryForm.entryMode" class="form-select">
                  <option value="text">دخولية نصية</option>
                  <option value="video">دخولية فيديو / حركة</option>
                </select>
              </div>
              <div class="col-md-8" v-if="mysteryForm.entryMode === 'text'">
                <label class="form-label">نص الدخولية</label>
                <input v-model="mysteryForm.entryText" maxlength="160" class="form-control" placeholder="دخل الرجل الغامض" />
              </div>
            </div>
            <div v-if="mysteryLoading" class="py-4"><LoadingSpinner /></div>
            <div v-else class="row g-3">
              <div class="col-md-4" v-for="asset in mysteryAssets" :key="asset.key">
                <div class="border rounded-3 p-3 h-100">
                  <h5 class="mb-1">{{ asset.title }}</h5>
                  <p class="small text-secondary">{{ asset.help }}</p>
                  <div class="rounded-3 bg-body-tertiary d-flex align-items-center justify-content-center overflow-hidden mb-3" style="min-height:150px">
                    <img v-if="asset.key === 'avatarUrl' && mysteryForm[asset.key]" :src="previewMysteryUrl(mysteryForm[asset.key])" alt="معاينة الصورة" style="max-width:100%;max-height:180px;object-fit:contain" />
                    <video v-else-if="asset.key !== 'avatarUrl' && mysteryForm[asset.key] && /\.(mp4|webm|mov)(\?|$)/i.test(mysteryForm[asset.key])" :src="mysteryForm[asset.key]" controls muted loop style="max-width:100%;max-height:180px"></video>
                    <a v-else-if="mysteryForm[asset.key]" :href="previewMysteryUrl(mysteryForm[asset.key])" target="_blank" rel="noopener">معاينة الملف المرفوع</a>
                    <span v-else class="text-secondary small">لم يتم رفع ملف بعد</span>
                  </div>
                  <input :ref="el => setMysteryInput(asset.key, el)" type="file" class="form-control form-control-sm mb-2" :accept="asset.accept" @change="uploadMysteryAsset(asset, $event)" />
                  <button type="button" class="btn btn-outline-secondary btn-sm" :disabled="mysteryUploading === asset.key" @click="chooseMysteryFile(asset.key)">
                    <span v-if="mysteryUploading === asset.key" class="spinner-border spinner-border-sm me-1"></span>
                    {{ mysteryUploading === asset.key ? 'جارٍ الرفع…' : 'اختيار ورفع ملف' }}
                  </button>
                  <button v-if="mysteryForm[asset.key]" type="button" class="btn btn-link btn-sm text-danger" @click="mysteryForm[asset.key] = ''">إزالة</button>
                </div>
              </div>
            </div>
            <div class="alert alert-info small mt-3">
              الصورة: PNG أو JPG أو WEBP. الدخولية والتأثير: MP4 أو WEBM أو SVGA بحسب دعم المشغّل. لن يتم تطبيق تأثير الكتابة في التطبيق قبل ربط مشغّله.
            </div>
            <div class="d-flex justify-content-end mt-3">
              <button type="button" class="btn btn-aurora" :disabled="mysterySaving || mysteryLoading || !auth.can('settings', 'write')" @click="saveMysteryConfig">
                <span v-if="mysterySaving" class="spinner-border spinner-border-sm me-1"></span>
                حفظ إعدادات الرجل الغامض
              </button>
            </div>
          </section>
        </template>

        <template v-else-if="settingsTab === 'zego'">
          <ZegoSettingsPanel />
        </template>

        <template v-else-if="settingsTab === 'account'">
          <section class="settings-card">
            <h3 class="settings-card-title">{{ t('settings.accountTitle') }}</h3>
            <p class="form-text mb-3">{{ t('settings.accountHint') }}</p>
            <div class="alert alert-info py-2 small mb-3" v-if="auth.isSuperAdmin">
              {{ t('settings.youAreSuper') }}
              <button type="button" class="btn btn-sm btn-aurora ms-2" @click="selectTab('admins')">
                {{ t('settings.goToAdmins') }}
              </button>
            </div>
            <div class="row g-3">
              <div class="col-md-6">
                <label class="form-label">{{ t('settings.accountEmail') }}</label>
                <input v-model="accountForm.email" type="email" class="form-control" dir="ltr" autocomplete="username" />
              </div>
              <div class="col-md-6">
                <label class="form-label">{{ t('settings.accountCurrentPassword') }}</label>
                <input
                  v-model="accountForm.currentPassword"
                  type="password"
                  class="form-control"
                  dir="ltr"
                  autocomplete="current-password"
                />
              </div>
              <div class="col-md-6">
                <label class="form-label">{{ t('settings.accountNewPassword') }}</label>
                <input
                  v-model="accountForm.newPassword"
                  type="password"
                  class="form-control"
                  dir="ltr"
                  autocomplete="new-password"
                  placeholder="••••••••"
                />
              </div>
              <div class="col-md-6">
                <label class="form-label">{{ t('settings.accountConfirmPassword') }}</label>
                <input
                  v-model="accountForm.confirmPassword"
                  type="password"
                  class="form-control"
                  dir="ltr"
                  autocomplete="new-password"
                  placeholder="••••••••"
                />
              </div>
            </div>
            <div class="mt-3">
              <button
                class="btn btn-aurora"
                type="button"
                :disabled="accountSaving"
                @click="saveAccount"
              >
                <span v-if="accountSaving" class="spinner-border spinner-border-sm me-1" />
                {{ t('settings.accountSave') }}
              </button>
            </div>
          </section>

          <!-- Super: second dashboard accounts right on this screen too -->
          <section v-if="auth.isSuperAdmin" class="settings-card mt-3">
            <h3 class="settings-card-title">{{ t('settings.adminsTitle') }}</h3>
            <p class="form-text mb-3">{{ t('settings.adminsHint') }}</p>
            <button type="button" class="btn btn-aurora" @click="selectTab('admins')">
              {{ t('settings.goToAdmins') }} →
            </button>
          </section>
        </template>

        <template v-else-if="settingsTab === 'admins'">
          <section class="settings-card">
            <h3 class="settings-card-title">{{ t('settings.adminsTitle') }}</h3>
            <p class="form-text mb-3">{{ t('settings.adminsHint') }}</p>

            <div class="glass p-3 mb-4 border rounded-3">
              <h5 class="mb-3">{{ t('settings.adminsCreate') }}</h5>
              <div class="row g-2">
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.accountEmail') }}</label>
                  <input v-model="opForm.email" type="email" class="form-control" dir="ltr" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.adminsDisplayName') }}</label>
                  <input v-model="opForm.displayName" type="text" class="form-control" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.adminsPassword') }}</label>
                  <input v-model="opForm.password" type="password" class="form-control" dir="ltr" />
                </div>
              </div>
              <div class="mt-3">
                <div class="small text-secondary mb-2">{{ t('settings.adminsPerms') }}</div>
                <div class="row g-2">
                  <div
                    v-for="mod in moduleKeys"
                    :key="'new-' + mod"
                    class="col-md-4 col-lg-3"
                  >
                    <label class="form-label small mb-0">{{ moduleLabel(mod) }}</label>
                    <select v-model="opForm.permissions[mod]" class="form-select form-select-sm">
                      <option value="none">{{ t('settings.permNone') }}</option>
                      <option value="read">{{ t('settings.permRead') }}</option>
                      <option value="write">{{ t('settings.permWrite') }}</option>
                    </select>
                  </div>
                </div>
              </div>
              <div class="mt-3">
                <button class="btn btn-aurora" type="button" :disabled="opSaving" @click="createOperator">
                  <span v-if="opSaving" class="spinner-border spinner-border-sm me-1" />
                  {{ t('settings.adminsCreateBtn') }}
                </button>
              </div>
            </div>

            <LoadingSpinner v-if="opLoading" />
            <div v-else class="table-responsive">
              <table class="table table-glass table-hover align-middle mb-0">
                <thead>
                  <tr>
                    <th>{{ t('settings.adminsDisplayName') }}</th>
                    <th>{{ t('settings.accountEmail') }}</th>
                    <th>{{ t('settings.adminsPerms') }}</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="!operators.length">
                    <td colspan="4" class="text-secondary">{{ t('settings.adminsEmpty') }}</td>
                  </tr>
                  <tr v-for="op in operators" :key="op.id">
                    <td>
                      <div class="fw-semibold">{{ op.displayName || op.name }}</div>
                      <div class="small text-secondary">{{ op.username }}</div>
                    </td>
                    <td dir="ltr">{{ op.email }}</td>
                    <td>
                      <div class="d-flex flex-wrap gap-1">
                        <span
                          v-for="p in summaryPerms(op.permissions)"
                          :key="op.id + p"
                          class="badge text-bg-secondary"
                        >{{ p }}</span>
                      </div>
                      <button
                        class="btn btn-link btn-sm px-0"
                        type="button"
                        @click="editOperator(op)"
                      >{{ t('settings.adminsEdit') }}</button>
                    </td>
                    <td class="text-end">
                      <button class="btn btn-sm btn-outline-danger" type="button" @click="removeOperator(op)">
                        {{ t('settings.adminsRemove') }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="editingOp" class="glass p-3 mt-4 border rounded-3">
              <h5 class="mb-3">{{ t('settings.adminsEdit') }} — {{ editingOp.email }}</h5>
              <div class="row g-2 mb-2">
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.adminsDisplayName') }}</label>
                  <input v-model="editForm.displayName" type="text" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.adminsPasswordOptional') }}</label>
                  <input v-model="editForm.password" type="password" class="form-control" dir="ltr" />
                </div>
              </div>
              <div class="row g-2">
                <div
                  v-for="mod in moduleKeys"
                  :key="'edit-' + mod"
                  class="col-md-4 col-lg-3"
                >
                  <label class="form-label small mb-0">{{ moduleLabel(mod) }}</label>
                  <select v-model="editForm.permissions[mod]" class="form-select form-select-sm">
                    <option value="none">{{ t('settings.permNone') }}</option>
                    <option value="read">{{ t('settings.permRead') }}</option>
                    <option value="write">{{ t('settings.permWrite') }}</option>
                  </select>
                </div>
              </div>
              <div class="mt-3 d-flex gap-2">
                <button class="btn btn-aurora" type="button" :disabled="opSaving" @click="saveOperatorEdit">
                  {{ t('settings.adminsSave') }}
                </button>
                <button class="btn btn-outline-secondary" type="button" @click="editingOp = null">
                  {{ t('app.cancel') }}
                </button>
              </div>
            </div>
          </section>
        </template>

        <form v-else class="settings-form" @submit.prevent="save">
          <!-- APP UPDATE -->
          <template v-if="settingsTab === 'app-update'">
            <section class="settings-card app-update-admin-card">
              <div class="d-flex align-items-center gap-3 mb-3">
                <div class="app-update-admin-icon"><i class="bi bi-cloud-arrow-down-fill" /></div>
                <div>
                  <h3 class="settings-card-title mb-1">تحديث JEHO الإجباري</h3>
                  <p class="form-text mb-0">انشر رابط APK جديداً، وحدد الإصدار الأدنى. عند تفعيل الإلزام لن يستطيع المستخدم متابعة الدخول قبل تثبيت النسخة الجديدة.</p>
                </div>
              </div>
              <label class="settings-switch mb-3">
                <div>
                  <div class="settings-switch-title">نشر التحديث للمستخدمين</div>
                  <div class="settings-switch-hint">اتركه مغلقاً أثناء تجهيز ملف APK. عند التفعيل يبدأ التطبيق بفحص هذا الإصدار عند التشغيل.</div>
                </div>
                <input v-model="appUpdateForm.enabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch mb-3">
                <div>
                  <div class="settings-switch-title">فرض التحديث على الجميع</div>
                  <div class="settings-switch-hint">عند التفعيل تظهر صفحة التحديث كاملة الشاشة، ولا يوجد زر «لاحقاً». سيحتاج المستخدم إلى تأكيد التثبيت من Android.</div>
                </div>
                <input v-model="appUpdateForm.forceUpdate" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <div class="row g-3">
                <div class="col-md-4">
                  <label class="form-label">رقم الإصدار (versionCode)</label>
                  <input v-model.number="appUpdateForm.latestVersionCode" class="form-control" type="number" min="1" required />
                  <div class="form-text">رقم صحيح أكبر من رقم النسخة المثبتة حالياً.</div>
                </div>
                <div class="col-md-4">
                  <label class="form-label">اسم الإصدار</label>
                  <input v-model.trim="appUpdateForm.latestVersionName" class="form-control" placeholder="2.0.51" required />
                </div>
                <div class="col-md-4">
                  <label class="form-label">أدنى إصدار مسموح</label>
                  <input v-model.number="appUpdateForm.minVersionCode" class="form-control" type="number" min="0" />
                  <div class="form-text">أي نسخة أقدم من هذا الرقم ستُجبر على التحديث.</div>
                </div>
                <div class="col-12">
                  <label class="form-label">رابط تحميل APK المباشر</label>
                  <input v-model.trim="appUpdateForm.storeUrl" class="form-control" type="url" dir="ltr" placeholder="https://example.com/releases/JEHO.apk" :required="appUpdateForm.enabled" />
                  <div class="form-text">استخدم رابط HTTPS مباشر لملف APK، وليس رابط صفحة ويب. يجب أن يكون الملف موقّعاً بنفس شهادة التطبيق الحالي وبالحزمة com.Dramizo.Series حتى يثبت كتحديث فوق النسخة الموجودة دون حذف بيانات المستخدم.</div>
                </div>
                <div class="col-md-6">
                  <label class="form-label">عنوان التحديث بالعربية</label>
                  <input v-model.trim="appUpdateForm.titleAr" class="form-control" maxlength="120" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">عنوان التحديث بالإنجليزية</label>
                  <input v-model.trim="appUpdateForm.titleEn" class="form-control" maxlength="120" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">تفاصيل التحديث بالعربية</label>
                  <textarea v-model.trim="appUpdateForm.messageAr" class="form-control" rows="3" maxlength="600" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">تفاصيل التحديث بالإنجليزية</label>
                  <textarea v-model.trim="appUpdateForm.messageEn" class="form-control" rows="3" maxlength="600" />
                </div>
              </div>
              <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mt-4">
                <span class="small text-secondary">نسخة البناء الحالية: versionCode 49 · 2.0.51</span>
                <button type="button" class="btn btn-aurora" :disabled="appUpdateSaving || !canWriteSettings" @click="saveAppUpdate">
                  <span v-if="appUpdateSaving" class="spinner-border spinner-border-sm me-1" />
                  حفظ إعدادات التحديث
                </button>
              </div>
            </section>
          </template>

          <!-- GENERAL -->
          <template v-else-if="settingsTab === 'general'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.general') }}</h3>
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.dashboardLanguage') }}</label>
                  <select v-model="dashboardLocale" class="form-select" @change="setDashboardLocale(dashboardLocale)">
                    <option value="en">{{ t('app.english') }}</option>
                    <option value="ar">{{ t('app.arabic') }}</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.defaultLocale') }}</label>
                  <select v-model="form.defaultLocale" class="form-select">
                    <option value="en">{{ t('app.english') }}</option>
                    <option value="ar">{{ t('app.arabic') }}</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.appName') }}</label>
                  <input v-model="form.appName" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportEmail') }}</label>
                  <input v-model="form.supportEmail" type="email" class="form-control" dir="ltr" />
                </div>
              </div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.supportChannels') }}</h3>
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportWhatsapp') }}</label>
                  <input v-model="form.supportWhatsapp" class="form-control" placeholder="+905xxxxxxxxx" dir="ltr" />
                  <div class="form-text">{{ t('settings.supportWhatsappHint') }}</div>
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportTelegram') }}</label>
                  <input v-model="form.supportTelegram" class="form-control" placeholder="@username" dir="ltr" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportInstagram') }}</label>
                  <input v-model="form.supportInstagram" class="form-control" placeholder="@username" dir="ltr" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">الموقع الرسمي للدعم</label>
                  <input v-model="form.supportWebsite" class="form-control" placeholder="https://example.com" dir="ltr" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.supportPhone') }}</label>
                  <input v-model="form.supportPhone" class="form-control" placeholder="+90..." dir="ltr" />
                </div>
              </div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.platformToggles') }}</h3>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.maintenanceMode') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.maintenanceModeHint') }}</div>
                </div>
                <input v-model="form.maintenanceMode" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.allowRegistrations') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.allowRegistrationsHint') }}</div>
                </div>
                <input v-model="form.registrationEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
            </section>
          </template>

          <!-- ECONOMY -->
          <template v-else-if="settingsTab === 'operations'">
          <OperationalSettingsPanel />
        </template>

        <template v-else-if="settingsTab === 'economy'">
            <EconomyPanel class="mb-3" />
            <section class="settings-card mb-3">
              <h3 class="settings-card-title">{{ t('settings.economyExplainerTitle') }}</h3>
              <ol class="mb-0 ps-3 small" style="line-height:1.8">
                <li>{{ t('settings.economyExplainer1') }}</li>
                <li>{{ t('settings.economyExplainer2') }}</li>
                <li>{{ t('settings.economyExplainer3') }}</li>
                <li>{{ t('settings.economyExplainer4') }}</li>
                <li>{{ t('settings.economyExplainer5') }}</li>
              </ol>
            </section>
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.economy') }}</h3>
              <div class="row g-3">
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.coinUsdRate') }}</label>
                  <input v-model.number="form.coinToUsd" type="number" step="0.0001" min="0" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.minWithdraw') }}</label>
                  <input v-model.number="form.minWithdraw" type="number" step="0.01" min="0" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.hostGiftShare') }}</label>
                  <input v-model.number="form.hostGiftShare" type="number" min="0" max="100" class="form-control" />
                </div>
                <div class="col-md-6">
                  <label class="form-label">{{ t('settings.agencyCommission') }}</label>
                  <input v-model.number="form.agencyCommission" type="number" min="0" max="100" class="form-control" />
                </div>
              </div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">دعم النفس وإرسال الهدية للنفس</h3>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">السماح للمستخدم بدعم نفسه</div>
                  <div class="settings-switch-hint">عند التفعيل يستطيع المستخدم إرسال هدية إلى حسابه. عند الإيقاف يتم منع دعم النفس من السيرفر.</div>
                </div>
                <input v-model="form.selfGiftAllowed" class="form-check-input" type="checkbox" role="switch" />
              </label>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.roomEconomy') }}</h3>
              <div class="row g-3">
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.supporterMinCoins') }}</label>
                  <input v-model.number="form.supporterMinCoins" type="number" min="0" class="form-control" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.legendaryMinCoins') }}</label>
                  <input v-model.number="form.legendaryMinCoins" type="number" min="0" class="form-control" />
                </div>
                <div class="col-md-4">
                  <label class="form-label">{{ t('settings.gameWinReward') }}</label>
                  <input v-model.number="form.gameWinRewardCoins" type="number" min="0" class="form-control" />
                  <div class="form-text">{{ t('settings.gameWinRewardHint') }}</div>
                </div>
              </div>
            </section>
          </template>

          <!-- FEATURES -->
          <template v-else-if="settingsTab === 'features'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.appFeatures') }}</h3>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">تحديات PK بين الرومات</div>
                  <div class="settings-switch-hint">عند الإيقاف تختفي تحديات PK من التطبيق ويتوقف احتساب نقاطها. يتطبق التغيير تلقائياً على الجميع خلال ثوانٍ، ويمكن استخدامه لإيقاف الميزة وقت ضغط السيرفر.</div>
                </div>
                <input v-model="form.roomPkEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.luckyBoxesEnabled') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.luckyBoxesHint') }}</div>
                </div>
                <input v-model="form.luckyBoxEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.femaleOnlyVoiceHosts') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.femaleOnlyVoiceHostsHint') }}</div>
                </div>
                <input v-model="form.femaleOnlyVoiceHosts" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.genderAutoAccept') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.genderAutoAcceptHint') }}</div>
                </div>
                <input v-model="form.genderVerificationAutoAccept" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.micWithoutHostApproval') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.micWithoutHostApprovalHint') }}</div>
                </div>
                <input v-model="form.micWithoutHostApproval" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.giftSoundsEnabled') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.giftSoundsEnabledHint') }}</div>
                </div>
                <input v-model="form.giftSoundsEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.requireGiftToDm') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.requireGiftToDmHint') }}</div>
                </div>
                <input v-model="form.requireGiftToDm" class="form-check-input" type="checkbox" role="switch" />
              </label>
            </section>
          </template>

          <!-- MODERATION / CONDITIONS -->
          <template v-else-if="settingsTab === 'moderation'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.moderation') }}</h3>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.autoModeration') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.autoModerationHint') }}</div>
                </div>
                <input v-model="form.autoModeration" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.liveNsfwEnabled') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.nsfwHint') }}</div>
                </div>
                <input v-model="form.liveNsfwEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.chatPromoFilter') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.chatPromoFilterHint') }}</div>
                </div>
                <input v-model="form.chatPromoFilterEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.chatPromoKick') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.chatPromoKickHint') }}</div>
                </div>
                <input v-model="form.chatPromoKickEnabled" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <label class="settings-switch">
                <div>
                  <div class="settings-switch-title">{{ t('settings.requireStreamReview') }}</div>
                  <div class="settings-switch-hint">{{ t('settings.requireStreamReviewHint') }}</div>
                </div>
                <input v-model="form.requireStreamReview" class="form-check-input" type="checkbox" role="switch" />
              </label>
              <div class="mt-3">
                <label class="form-label">{{ t('settings.chatBlockedKeywords') }}</label>
                <textarea
                  v-model="form.chatBlockedExtraKeywords"
                  class="form-control"
                  rows="3"
                  :placeholder="t('settings.chatBlockedKeywordsHint')"
                />
              </div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.nsfwRules') }}</h3>
              <div class="row g-3">
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.reportSla') }}</label>
                  <input v-model.number="form.reportSlaHours" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwConfidence') }}</label>
                  <input v-model.number="form.liveNsfwConfidence" type="number" min="0.5" max="0.99" step="0.01" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwConsecutive') }}</label>
                  <input v-model.number="form.liveNsfwConsecutive" type="number" min="2" max="8" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwStreamBanHours') }}</label>
                  <input v-model.number="form.liveNsfwStreamBanHours" type="number" min="1" max="720" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwWarnStrikes') }}</label>
                  <input v-model.number="form.liveNsfwWarnStrikes" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwMuteStrikes') }}</label>
                  <input v-model.number="form.liveNsfwMuteStrikes" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwStreamBanStrikes') }}</label>
                  <input v-model.number="form.liveNsfwStreamBanStrikes" type="number" min="1" class="form-control" />
                </div>
                <div class="col-md-3">
                  <label class="form-label">{{ t('settings.nsfwPermBanStrikes') }}</label>
                  <input v-model.number="form.liveNsfwPermBanStrikes" type="number" min="1" class="form-control" />
                </div>
              </div>
            </section>
          </template>

          <!-- OTHER -->
          <template v-else-if="settingsTab === 'other'">
            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.roomRules') }}</h3>
              <label class="form-label">{{ t('settings.roomRulesText') }}</label>
              <textarea v-model="form.roomRulesText" class="form-control" rows="3" maxlength="200" />
              <div class="form-text">{{ t('settings.roomRulesHint') }}</div>
            </section>

            <section class="settings-card">
              <h3 class="settings-card-title">{{ t('settings.quickLinks') }}</h3>
              <div class="settings-links">
                <RouterLink class="settings-link" :to="{ name: 'settings', query: { tab: 'payment' } }">
                  <i class="bi bi-credit-card" /> {{ t('nav.paymentSettings') }}
                </RouterLink>
                <RouterLink class="settings-link" :to="{ name: 'settings', query: { tab: 'zego' } }">
                  <i class="bi bi-broadcast-pin" /> {{ t('nav.zegoSettings') }}
                </RouterLink>
                <RouterLink class="settings-link" :to="{ name: 'vip' }">
                  <i class="bi bi-diamond" /> {{ t('nav.vip') }}
                </RouterLink>
                <RouterLink class="settings-link" :to="{ name: 'coins' }">
                  <i class="bi bi-coin" /> {{ t('nav.coins') }}
                </RouterLink>
              </div>
            </section>
          </template>

          <div v-if="isCoreTab" class="settings-footer">
            <button class="btn btn-aurora" type="submit" :disabled="saving">
              <span v-if="saving" class="spinner-border spinner-border-sm me-2" />
              {{ t('settings.saveSettings') }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { settingsApi, authApi, operatorsApi, mysteriousManApi, staffIdentityApi, uploadsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import SvgaPreview from '@/components/SvgaPreview.vue'
import EconomyPanel from '@/components/settings/EconomyPanel.vue'
import OperationalSettingsPanel from '@/components/settings/OperationalSettingsPanel.vue'
import PaymentSettingsPanel from '@/components/settings/PaymentSettingsPanel.vue'
import FourthwallPaymentPanel from '@/components/settings/FourthwallPaymentPanel.vue'
import ShamCashPaymentPanel from '@/components/settings/ShamCashPaymentPanel.vue'
import ZegoSettingsPanel from '@/components/settings/ZegoSettingsPanel.vue'
import SectionBackgroundPanel from '@/components/settings/SectionBackgroundPanel.vue'
import { setDashboardLocale } from '@/i18n'
import { readDashboardLocale } from '@/utils/locale'
import { resolveAsset } from '@/utils/assets'
import { useAuthStore } from '@/stores/auth'
import { DASHBOARD_MODULES } from '@/utils/dashboard-permissions'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const dashboardLocale = ref(readDashboardLocale())
const settingsTab = ref('general')
const CORE_TABS = new Set(['general', 'economy', 'features', 'moderation', 'other'])
const isCoreTab = computed(() => CORE_TABS.has(settingsTab.value))
const canWriteSettings = computed(() => auth.can('settings', 'write'))

const settingTabs = computed(() => {
  // Tabs inherit the narrowest existing module permission. Global/security tabs
  // remain Super-Admin-only and are not merely disabled for limited staff.
  const tabs = [
    { id: 'general', label: t('settings.tabGeneral'), icon: 'bi-sliders', permission: 'settings' },
    { id: 'app-update', label: 'تحديث التطبيق', icon: 'bi-cloud-arrow-down', permission: 'settings' },
    { id: 'economy', label: t('settings.tabEconomy'), icon: 'bi-cash-coin', permission: 'settings' },
    { id: 'operations', label: 'التشغيل المتقدم', icon: 'bi-gear-wide-connected', superOnly: true },
    { id: 'section-background', label: 'خلفية الأقسام الرئيسية', icon: 'bi-image', permission: 'settings' },
    { id: 'mysterious-man', label: 'إعدادات الرجل الغامض', icon: 'bi-incognito', superOnly: true },
    { id: 'features', label: t('settings.tabFeatures'), icon: 'bi-toggles', permission: 'settings' },
    { id: 'moderation', label: t('settings.tabModeration'), icon: 'bi-shield-check', permission: 'settings' },
    { id: 'payment', label: t('settings.tabPayment'), icon: 'bi-credit-card', permission: 'settings' },
    { id: 'zego', label: t('settings.tabZego'), icon: 'bi-broadcast-pin', permission: 'settings' },
    { id: 'account', label: t('settings.tabAccount'), icon: 'bi-person-lock', self: true },
    { id: 'other', label: t('settings.tabOther'), icon: 'bi-three-dots', permission: 'settings' },
  ]
  if (auth.isSuperAdmin) {
    tabs.push({ id: 'admins', label: t('settings.tabAdmins'), icon: 'bi-people-fill', superOnly: true })
  }
  return tabs.filter((tab) => {
    if (tab.superOnly) return auth.isSuperAdmin
    if (tab.self) return true
    return tab.permission ? auth.can(tab.permission, 'read') : false
  })
})

const staffIdentityLoading = ref(false)
const staffIdentitySaving = ref(false)
const staffIdentityUploading = ref('')
const staffIdentityInputs = {}
const staffIdentityRoles = [{ key: 'super', title: 'Super Admin' }, { key: 'admin', title: 'Admin' }, { key: 'manager', title: 'Manager' }]
const staffIdentityAssets = [
  { key: 'frameUrl', title: 'إطار الحساب', help: 'إطار خاص بهذه الرتبة.', accept: 'image/png,image/jpeg,image/webp,image/gif,.svga,video/mp4,video/webm' },
  { key: 'badgeUrl', title: 'شارة / شعار الرتبة', help: 'شارة بصرية خاصة بالرتبة.', accept: 'image/png,image/jpeg,image/webp,image/gif' },
  { key: 'nameplateUrl', title: 'بطاقة الاسم', help: 'لوحة اسم مخصصة لهذه الرتبة.', accept: 'image/png,image/jpeg,image/webp,image/gif' },
  { key: 'messageCardUrl', title: 'بطاقة الرسائل', help: 'تصميم بطاقة رسائل الموظف.', accept: 'image/png,image/jpeg,image/webp,image/gif' },
  { key: 'entryEffectUrl', title: 'الدخولية / تأثير الدخول', help: 'تأثير الدخول المخصص للرتبة.', accept: 'video/mp4,video/webm,video/quicktime,.svga,image/gif' },
]
const staffIdentityForm = reactive({
  super: { label: 'Super Admin', accentColor: '#D8B35B', badgeUrl: '', frameUrl: '', nameplateUrl: '', messageCardUrl: '', entryEffectUrl: '' },
  admin: { label: 'Admin', accentColor: '#9AA8C7', badgeUrl: '', frameUrl: '', nameplateUrl: '', messageCardUrl: '', entryEffectUrl: '' },
  manager: { label: 'Manager', accentColor: '#37A889', badgeUrl: '', frameUrl: '', nameplateUrl: '', messageCardUrl: '', entryEffectUrl: '' },
})
function setStaffIdentityInput(role, key, el) { if (el) staffIdentityInputs[role + ':' + key] = el }
function chooseStaffIdentityFile(role, key) { staffIdentityInputs[role + ':' + key]?.click() }
function staffIdentityIsSvga(raw) { return /\.svga(\?|$)/i.test(String(raw || '')) }
function staffIdentityIsVideo(raw) { return /\.(mp4|webm|mov)(\?|$)/i.test(String(raw || '')) }
async function loadStaffIdentity() {
  staffIdentityLoading.value = true
  const { data, error: err } = await staffIdentityApi.get()
  staffIdentityLoading.value = false
  if (err) { error.value = err.message; toast().danger(err.message); return }
  const cfg = data?.data || data || {}
  for (const role of staffIdentityRoles) Object.assign(staffIdentityForm[role.key], cfg[role.key] || {})
}
async function uploadStaffIdentityAsset(role, asset, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  const image = /\.(png|jpe?g|webp|gif)$/i.test(file.name)
  const motion = /\.(mp4|webm|mov|svga)$/i.test(file.name)
  if (!(image || motion) || (asset.key !== 'entryEffectUrl' && !image && asset.key !== 'frameUrl')) { toast().danger('صيغة الملف غير مدعومة لهذا الحقل'); event.target.value = ''; return }
  const max = motion ? 40 : 8
  if (file.size > max * 1024 * 1024) { toast().danger('حجم الملف يتجاوز الحد المسموح (' + max + ' ميغابايت)'); event.target.value = ''; return }
  staffIdentityUploading.value = role + ':' + asset.key
  const { data, error: err } = await uploadsApi.upload(file)
  staffIdentityUploading.value = ''; event.target.value = ''
  if (err) { toast().danger(err.message); return }
  const uploaded = data?.data || data || {}
  const url = String(uploaded.url || uploaded.path || (uploaded.filename ? '/uploads/' + uploaded.filename : '')).trim()
  if (!url) { toast().danger('لم يرجع الخادم رابطاً صالحاً للملف'); return }
  staffIdentityForm[role][asset.key] = url
  toast().success('تم رفع الملف. اضغط حفظ هوية الرتب لتطبيقه.')
}
async function saveStaffIdentity() {
  if (!auth.can('settings', 'write')) { toast().danger(t('settings.readOnly')); return }
  staffIdentitySaving.value = true; error.value = ''
  const { data, error: err } = await staffIdentityApi.update(JSON.parse(JSON.stringify(staffIdentityForm)))
  staffIdentitySaving.value = false
  if (err) { error.value = err.message; toast().danger(err.message); return }
  const cfg = data?.data || data || {}
  for (const role of staffIdentityRoles) Object.assign(staffIdentityForm[role.key], cfg[role.key] || {})
  success.value = 'تم حفظ إعدادات هوية السوبر أدمن والأدمن والمانجر'
  toast().success(success.value)
}
const mysteryLoading = ref(false)
const mysterySaving = ref(false)
const mysteryUploading = ref('')
const mysteryInputs = {}
const mysteryForm = reactive({ enabled: false, eligibleVipLevels: [], avatarUrl: '', entryMode: 'video', entryText: 'دخل الرجل الغامض', entryEffectUrl: '', messageEffectUrl: '' })
const mysteryAssets = [
  { key: 'avatarUrl', title: 'الصورة الشخصية الموحدة', help: 'تظهر بدل الصورة الأصلية في مواضع الهوية المخفية.', accept: 'image/png,image/jpeg,image/webp' },
  { key: 'entryEffectUrl', title: 'دخولية الرجل الغامض', help: 'فيديو/حركة الدخول الموحدة داخل الرومات.', accept: 'video/mp4,video/webm,video/quicktime,.svga' },
  { key: 'messageEffectUrl', title: 'تأثير الرسائل', help: 'ملف تأثير اختياري موحد؛ يحتاج دعم مشغّل داخل التطبيق.', accept: 'video/mp4,video/webm,video/quicktime,.svga' },
]
function previewMysteryUrl(raw) {
  return resolveAsset(raw)
}
function setMysteryInput(key, el) { if (el) mysteryInputs[key] = el }
function chooseMysteryFile(key) { mysteryInputs[key]?.click() }
async function loadMysteryConfig() {
  mysteryLoading.value = true
  const { data, error: err } = await mysteriousManApi.get()
  mysteryLoading.value = false
  if (err) { error.value = err.message; toast().danger(err.message); return }
  const cfg = data?.data || data || {}
  Object.assign(mysteryForm, {
    enabled: cfg.enabled === true,
    eligibleVipLevels: Array.isArray(cfg.eligibleVipLevels) ? cfg.eligibleVipLevels.map(Number).filter(n => Number.isInteger(n) && n >= 1 && n <= 7) : [],
    avatarUrl: String(cfg.avatarUrl || ''),
    entryMode: cfg.entryMode === 'text' ? 'text' : 'video',
    entryText: String(cfg.entryText || 'دخل الرجل الغامض'),
    entryEffectUrl: String(cfg.entryEffectUrl || ''),
    messageEffectUrl: String(cfg.messageEffectUrl || ''),
  })
}
async function uploadMysteryAsset(asset, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  const isAvatar = asset.key === 'avatarUrl'
  const max = isAvatar ? 8 : 40
  if (file.size > max * 1024 * 1024) {
    toast().danger(`حجم الملف يجب ألا يتجاوز ${max} ميغابايت`)
    event.target.value = ''
    return
  }
  const extOk = isAvatar
    ? /\.(png|jpe?g|webp)$/i.test(file.name)
    : /\.(mp4|webm|mov|svga)$/i.test(file.name)
  if (!extOk) {
    toast().danger(isAvatar ? 'اختر صورة PNG أو JPG أو WEBP' : 'اختر MP4 أو WEBM أو MOV أو SVGA')
    event.target.value = ''
    return
  }
  mysteryUploading.value = asset.key
  const { data, error: err } = await uploadsApi.upload(file)
  mysteryUploading.value = ''
  event.target.value = ''
  if (err) { toast().danger(err.message); return }
  const uploaded = data?.data || data || {}
  const url = String(uploaded.url || uploaded.path || (uploaded.filename ? `/uploads/${uploaded.filename}` : '')).trim()
  if (!url) { toast().danger('تم رفع الملف لكن لم يصل رابط صالح من الخادم'); return }
  mysteryForm[asset.key] = url
  toast().success('تم رفع الملف. اضغط حفظ لتطبيق الإعدادات على التطبيق.')
}
async function saveMysteryConfig() {
  if (!auth.can('settings', 'write')) { toast().danger(t('settings.readOnly')); return }
  mysterySaving.value = true
  const { data, error: err } = await mysteriousManApi.update({ ...mysteryForm })
  mysterySaving.value = false
  if (err) { error.value = err.message; toast().danger(err.message); return }
  const cfg = data?.data || data || {}
  Object.assign(mysteryForm, cfg)
  success.value = 'تم حفظ إعدادات الرجل الغامض'
  toast().success(success.value)
}

const moduleKeys = DASHBOARD_MODULES

function emptyPerms() {
  const o = {}
  for (const m of moduleKeys) o[m] = 'none'
  return o
}

const opLoading = ref(false)
const opSaving = ref(false)
const operators = ref([])
const editingOp = ref(null)
const opForm = reactive({
  email: '',
  displayName: '',
  password: '',
  permissions: emptyPerms(),
})
const editForm = reactive({
  displayName: '',
  password: '',
  permissions: emptyPerms(),
})

function moduleLabel(mod) {
  return t(`settings.mod.${mod}`, mod)
}

function summaryPerms(perms) {
  if (!perms) return []
  return Object.entries(perms)
    .filter(([, v]) => v === 'read' || v === 'write')
    .map(([k, v]) => `${moduleLabel(k)}:${v === 'write' ? '✎' : '👁'}`)
}

async function loadOperators() {
  if (!auth.isSuperAdmin) return
  opLoading.value = true
  const { data, error: err } = await operatorsApi.list()
  opLoading.value = false
  if (err) {
    error.value = err.message
    return
  }
  operators.value = data?.items || data?.data?.items || []
}

async function createOperator() {
  error.value = ''
  success.value = ''
  opSaving.value = true
  const { error: err } = await operatorsApi.create({
    email: opForm.email,
    password: opForm.password,
    displayName: opForm.displayName,
    permissions: { ...opForm.permissions },
  })
  opSaving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = t('settings.adminsCreated')
  toast().success(success.value)
  opForm.email = ''
  opForm.displayName = ''
  opForm.password = ''
  opForm.permissions = emptyPerms()
  await loadOperators()
}

function editOperator(op) {
  editingOp.value = op
  editForm.displayName = op.displayName || op.name || ''
  editForm.password = ''
  editForm.permissions = { ...emptyPerms(), ...(op.permissions || {}) }
}

async function saveOperatorEdit() {
  if (!editingOp.value) return
  opSaving.value = true
  const payload = {
    displayName: editForm.displayName,
    permissions: { ...editForm.permissions },
  }
  if (editForm.password) payload.password = editForm.password
  const { error: err } = await operatorsApi.update(editingOp.value.id, payload)
  opSaving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = t('settings.adminsUpdated')
  toast().success(success.value)
  editingOp.value = null
  await loadOperators()
}

async function removeOperator(op) {
  if (!confirm(t('settings.adminsRemoveConfirm', { email: op.email }))) return
  const { error: err } = await operatorsApi.remove(op.id)
  if (err) {
    toast().danger(err.message)
    return
  }
  toast().success(t('settings.adminsRemoved'))
  if (editingOp.value?.id === op.id) editingOp.value = null
  await loadOperators()
}

const loading = ref(false)
const saving = ref(false)
const accountSaving = ref(false)
const error = ref('')
const success = ref('')
const accountForm = reactive({
  email: '',
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function fillAccountFromAuth() {
  accountForm.email = String(auth.user?.email || '').trim()
  accountForm.currentPassword = ''
  accountForm.newPassword = ''
  accountForm.confirmPassword = ''
}

async function saveAccount() {
  error.value = ''
  success.value = ''
  const email = String(accountForm.email || '').trim().toLowerCase()
  const currentPassword = String(accountForm.currentPassword || '')
  const newPassword = String(accountForm.newPassword || '')
  const confirmPassword = String(accountForm.confirmPassword || '')
  if (!currentPassword) {
    error.value = t('settings.accountCurrentPassword')
    toast().danger(error.value)
    return
  }
  const currentEmail = String(auth.user?.email || '').trim().toLowerCase()
  const emailChanged = email && email !== currentEmail
  if (!emailChanged && !newPassword) {
    error.value = t('settings.accountNeedChange')
    toast().danger(error.value)
    return
  }
  if (newPassword && newPassword.length < 8) {
    error.value = t('settings.accountPasswordShort')
    toast().danger(error.value)
    return
  }
  if (newPassword && newPassword !== confirmPassword) {
    error.value = t('settings.accountPasswordMismatch')
    toast().danger(error.value)
    return
  }
  accountSaving.value = true
  const payload = { currentPassword }
  if (emailChanged) payload.newEmail = email
  if (newPassword) payload.newPassword = newPassword
  const { data, error: err } = await authApi.updateCredentials(payload)
  accountSaving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const nextEmail = data?.email || email || currentEmail
  if (auth.user) {
    auth.setSession(auth.token, { ...auth.user, email: nextEmail })
  }
  fillAccountFromAuth()
  success.value = t('settings.accountUpdated')
  toast().success(success.value)
}
const form = reactive({
  appName: 'JEHO CHAT',
  supportEmail: 'support@adnova.bbs.tr',
  supportWhatsapp: '',
  supportTelegram: '',
  supportInstagram: '',
  supportWebsite: '',
  supportPhone: '',
  defaultLocale: 'en',
  maintenanceMode: false,
  registrationEnabled: true,
  coinToUsd: 0.01,
  minWithdraw: 50,
  hostGiftShare: 60,
  agencyCommission: 20,
  supporterMinCoins: 10000,
  legendaryMinCoins: 50000,
  gameWinRewardCoins: 100,
  selfGiftAllowed: false,
  luckyBoxEnabled: true,
  roomPkEnabled: false,
  femaleOnlyVoiceHosts: false,
  genderVerificationAutoAccept: true,
  micWithoutHostApproval: true,
  giftSoundsEnabled: true,
  requireGiftToDm: false,
  roomRulesText: 'احترموا القوانين واستمتعوا بالجلسة',
  autoModeration: true,
  liveNsfwEnabled: true,
  chatPromoFilterEnabled: true,
  chatPromoKickEnabled: true,
  chatBlockedExtraKeywords: '',
  requireStreamReview: false,
  reportSlaHours: 24,
  liveNsfwConfidence: 0.78,
  liveNsfwConsecutive: 3,
  liveNsfwStreamBanHours: 24,
  liveNsfwWarnStrikes: 1,
  liveNsfwMuteStrikes: 2,
  liveNsfwStreamBanStrikes: 3,
  liveNsfwPermBanStrikes: 5,
})

const appUpdateSaving = ref(false)
const appUpdateForm = reactive({
  enabled: false,
  latestVersionCode: 49,
  latestVersionName: '2.0.51',
  minVersionCode: 0,
  forceUpdate: false,
  storeUrl: '',
  titleAr: 'يوجد تحديث جديد',
  messageAr: 'حدّث تطبيق JEHO للاستمتاع بالتحسينات والإصلاحات الجديدة.',
  titleEn: 'A new update is available',
  messageEn: 'Update JEHO to enjoy the latest improvements and fixes.',
})

function normalizeAppName(raw) {
  const name = String(raw ?? '').trim()
  if (!name) return 'JEHO CHAT'
  const lower = name.toLowerCase()
  if (
    name.includes('همس')
    || lower.includes('auralive')
    || lower.includes('aura live')
    || lower.includes('jeho live')
    || name.includes('جيرو')
    || lower.includes('giro')
  ) {
    return 'JEHO CHAT'
  }
  return name
}

function selectTab(id) {
  settingsTab.value = id
  if (id === 'mysterious-man') loadMysteryConfig()
  if (id === 'staff-identity') loadStaffIdentity()
  router.replace({ name: 'settings', query: { tab: id } })
  if (id === 'account') fillAccountFromAuth()
  if (id === 'admins') loadOperators()
}

function syncTabFromRoute() {
  const raw = String(route.query.tab || '')
  const tabs = settingTabs.value
  const allowed = new Set(tabs.map((x) => x.id))
  // Never leave a hidden/unpermitted tab active, including after a role refresh.
  settingsTab.value = allowed.has(raw)
    ? raw
    : (allowed.has(settingsTab.value) ? settingsTab.value : (tabs[0]?.id || 'account'))
  if (settingsTab.value === 'account') fillAccountFromAuth()
  if (settingsTab.value === 'admins') loadOperators()
}

watch(() => route.query.tab, syncTabFromRoute)
watch(settingTabs, () => {
  const allowed = new Set(settingTabs.value.map((x) => x.id))
  if (!allowed.has(settingsTab.value)) syncTabFromRoute()
}, { deep: true })

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const raw = data?.settings || data?.data || data || {}
  const map = {}
  if (Array.isArray(raw)) {
    for (const row of raw) {
      if (row?.key != null) map[row.key] = row.value
    }
  } else if (raw && typeof raw === 'object') {
    Object.assign(map, raw)
  }
  Object.assign(appUpdateForm, {
    enabled: ['true', '1', 'yes'].includes(String(map['app.update_enabled'] ?? appUpdateForm.enabled).toLowerCase()),
    latestVersionCode: Number(map['app.latest_version_code'] ?? appUpdateForm.latestVersionCode) || appUpdateForm.latestVersionCode,
    latestVersionName: String(map['app.latest_version_name'] ?? appUpdateForm.latestVersionName),
    minVersionCode: Number(map['app.min_version_code'] ?? appUpdateForm.minVersionCode) || 0,
    forceUpdate: ['true', '1', 'yes'].includes(String(map['app.update_force'] ?? appUpdateForm.forceUpdate).toLowerCase()),
    storeUrl: String(map['app.update_store_url'] ?? appUpdateForm.storeUrl),
    titleAr: String(map['app.update_title_ar'] ?? appUpdateForm.titleAr),
    messageAr: String(map['app.update_message_ar'] ?? appUpdateForm.messageAr),
    titleEn: String(map['app.update_title_en'] ?? appUpdateForm.titleEn),
    messageEn: String(map['app.update_message_en'] ?? appUpdateForm.messageEn),
  })
  const bool = (v, fallback) => {
    if (v === undefined || v === null || v === '') return fallback
    if (typeof v === 'boolean') return v
    const s = String(v).toLowerCase()
    return s === '1' || s === 'true' || s === 'yes'
  }
  const num = (v, fallback) => {
    const n = Number(v)
    return Number.isFinite(n) ? n : fallback
  }
  Object.assign(form, {
    appName: normalizeAppName(map.appName ?? map.app_name ?? form.appName),
    supportEmail: map.supportEmail ?? map.support_email ?? form.supportEmail,
    supportWhatsapp: map.support_whatsapp ?? map.supportWhatsapp ?? form.supportWhatsapp,
    supportTelegram: map.support_telegram ?? map.supportTelegram ?? form.supportTelegram,
    supportInstagram: map.support_instagram ?? map.supportInstagram ?? form.supportInstagram,
    supportWebsite: map.support_website ?? map.supportWebsite ?? form.supportWebsite,
    supportPhone: map.support_phone ?? map.supportPhone ?? form.supportPhone,
    defaultLocale: map.defaultLocale ?? map.default_locale ?? form.defaultLocale,
    maintenanceMode: bool(map.maintenanceMode ?? map.maintenance_mode, form.maintenanceMode),
    registrationEnabled: bool(map.registrationEnabled ?? map.registration_enabled, form.registrationEnabled),
    coinToUsd: num(map.coinToUsd ?? map.coin_to_usd, form.coinToUsd),
    minWithdraw: num(map.minWithdraw ?? map.min_withdraw, form.minWithdraw),
    hostGiftShare: num(map.hostGiftShare ?? map.host_gift_share, form.hostGiftShare),
    agencyCommission: num(
      map.agencyCommission ?? map.agency_default_commission_percent ?? map.agency_commission,
      form.agencyCommission,
    ),
    supporterMinCoins: num(map['room.supporter_min_coins'] ?? map.supporterMinCoins, form.supporterMinCoins),
    legendaryMinCoins: num(map['room.legendary_min_coins'] ?? map.legendaryMinCoins, form.legendaryMinCoins),
    gameWinRewardCoins: num(
      map['games.win_reward_coins'] ?? map['game.win_reward_coins'] ?? map.gameWinRewardCoins,
      form.gameWinRewardCoins,
    ),
    selfGiftAllowed: !bool(
      map['economy.self_gift_blocked'],
      !form.selfGiftAllowed,
    ),
    luckyBoxEnabled: bool(map['lucky_box.enabled'] ?? map.luckyBoxEnabled, form.luckyBoxEnabled),
    roomPkEnabled: bool(map['features.room_pk_enabled'] ?? map.roomPkEnabled, form.roomPkEnabled),
    femaleOnlyVoiceHosts: bool(
      map['features.female_only_voice_hosts'] ?? map.femaleOnlyVoiceHosts,
      form.femaleOnlyVoiceHosts,
    ),
    genderVerificationAutoAccept: bool(
      map['gender_verification.auto_accept'] ?? map.genderVerificationAutoAccept,
      form.genderVerificationAutoAccept,
    ),
    micWithoutHostApproval: bool(
      map['rooms.mic_without_host_approval'] ?? map.micWithoutHostApproval,
      form.micWithoutHostApproval,
    ),
    giftSoundsEnabled: bool(
      map['gifts.sound_enabled'] ?? map.giftSoundsEnabled,
      form.giftSoundsEnabled,
    ),
    requireGiftToDm: bool(
      map['chat.requireGiftToDm'] ?? map.requireGiftToDm,
      form.requireGiftToDm,
    ),
    roomRulesText: String(map['rooms.rules_text'] ?? map.roomRulesText ?? form.roomRulesText),
    autoModeration: bool(map.autoModeration ?? map.auto_moderation, form.autoModeration),
    liveNsfwEnabled: bool(map.live_nsfw_enabled ?? map.liveNsfwEnabled, form.liveNsfwEnabled),
    chatPromoFilterEnabled: bool(
      map.chat_promo_filter_enabled ?? map.chatPromoFilterEnabled,
      form.chatPromoFilterEnabled,
    ),
    chatPromoKickEnabled: bool(
      map.chat_promo_kick_enabled ?? map.chatPromoKickEnabled,
      form.chatPromoKickEnabled,
    ),
    chatBlockedExtraKeywords: String(
      map.chat_blocked_extra_keywords ?? map.chatBlockedExtraKeywords ?? form.chatBlockedExtraKeywords,
    ),
    requireStreamReview: bool(map.requireStreamReview ?? map.require_stream_review, form.requireStreamReview),
    reportSlaHours: num(map.reportSlaHours ?? map.report_sla_hours, form.reportSlaHours),
    liveNsfwConfidence: num(map.live_nsfw_confidence ?? map.liveNsfwConfidence, form.liveNsfwConfidence),
    liveNsfwConsecutive: num(map.live_nsfw_consecutive ?? map.liveNsfwConsecutive, form.liveNsfwConsecutive),
    liveNsfwStreamBanHours: num(map.live_nsfw_stream_ban_hours ?? map.liveNsfwStreamBanHours, form.liveNsfwStreamBanHours),
    liveNsfwWarnStrikes: num(map.live_nsfw_warn_strikes ?? map.liveNsfwWarnStrikes, form.liveNsfwWarnStrikes),
    liveNsfwMuteStrikes: num(map.live_nsfw_mute_strikes ?? map.liveNsfwMuteStrikes, form.liveNsfwMuteStrikes),
    liveNsfwStreamBanStrikes: num(map.live_nsfw_stream_ban_strikes ?? map.liveNsfwStreamBanStrikes, form.liveNsfwStreamBanStrikes),
    liveNsfwPermBanStrikes: num(map.live_nsfw_perm_ban_strikes ?? map.liveNsfwPermBanStrikes, form.liveNsfwPermBanStrikes),
  })
}

function reloadActive() {
  if (isCoreTab.value || settingsTab.value === 'app-update') load()
  else if (settingsTab.value === 'staff-identity') loadStaffIdentity()
  else if (settingsTab.value === 'mysterious-man') loadMysteryConfig()
  else if (settingsTab.value === 'admins') loadOperators()
}

async function saveAppUpdate() {
  if (!auth.can('settings', 'write')) {
    toast().danger(t('settings.readOnly'))
    return
  }
  const versionCode = Number(appUpdateForm.latestVersionCode)
  const minVersionCode = Number(appUpdateForm.minVersionCode) || 0
  const apkUrl = String(appUpdateForm.storeUrl || '').trim()
  if (!Number.isInteger(versionCode) || versionCode < 1) {
    toast().danger('رقم الإصدار يجب أن يكون رقماً صحيحاً أكبر من صفر')
    return
  }
  if (minVersionCode > versionCode) {
    toast().danger('أدنى إصدار لا يمكن أن يكون أكبر من الإصدار الجديد')
    return
  }
  if (appUpdateForm.enabled && !/^https:\/\//i.test(apkUrl)) {
    toast().danger('أدخل رابط HTTPS مباشر لملف APK قبل نشر التحديث')
    return
  }
  if (apkUrl && !/^https:\/\//i.test(apkUrl)) {
    toast().danger('يجب أن يبدأ رابط التحميل بـ HTTPS')
    return
  }
  appUpdateSaving.value = true
  const payload = {
    'app.update_enabled': String(!!appUpdateForm.enabled),
    'app.latest_version_code': String(versionCode),
    'app.latest_version_name': String(appUpdateForm.latestVersionName || '').trim(),
    'app.min_version_code': String(minVersionCode),
    'app.update_force': String(!!appUpdateForm.forceUpdate),
    'app.update_store_url': apkUrl,
    'app.update_title_ar': String(appUpdateForm.titleAr || 'يوجد تحديث جديد').trim(),
    'app.update_message_ar': String(appUpdateForm.messageAr || '').trim(),
    'app.update_title_en': String(appUpdateForm.titleEn || 'Update available').trim(),
    'app.update_message_en': String(appUpdateForm.messageEn || '').trim(),
  }
  const { error: err } = await settingsApi.update(payload)
  appUpdateSaving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = 'تم حفظ إعدادات تحديث JEHO'
  toast().success(success.value)
}

async function save() {
  if (!auth.can('settings', 'write')) {
    error.value = t('settings.readOnly')
    toast().danger(error.value)
    return
  }
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    appName: String(form.appName ?? ''),
    supportEmail: String(form.supportEmail ?? ''),
    support_whatsapp: String(form.supportWhatsapp ?? ''),
    support_telegram: String(form.supportTelegram ?? ''),
    support_instagram: String(form.supportInstagram ?? ''),
    support_website: String(form.supportWebsite ?? ''),
    support_phone: String(form.supportPhone ?? ''),
    defaultLocale: String(form.defaultLocale ?? 'ar'),
    maintenanceMode: String(!!form.maintenanceMode),
    registrationEnabled: String(!!form.registrationEnabled),
    coinToUsd: String(form.coinToUsd ?? ''),
    minWithdraw: String(form.minWithdraw ?? ''),
    hostGiftShare: String(form.hostGiftShare ?? ''),
    agencyCommission: String(form.agencyCommission ?? ''),
    agency_default_commission_percent: String(form.agencyCommission ?? ''),
    'room.supporter_min_coins': String(form.supporterMinCoins ?? 10000),
    'room.legendary_min_coins': String(form.legendaryMinCoins ?? 50000),
    'games.win_reward_coins': String(form.gameWinRewardCoins ?? 100),
    'economy.self_gift_blocked': String(!form.selfGiftAllowed),
    'lucky_box.enabled': String(!!form.luckyBoxEnabled),
    'features.room_pk_enabled': String(!!form.roomPkEnabled),
    'features.female_only_voice_hosts': String(!!form.femaleOnlyVoiceHosts),
    'gender_verification.auto_accept': String(!!form.genderVerificationAutoAccept),
    'rooms.mic_without_host_approval': String(!!form.micWithoutHostApproval),
    'gifts.sound_enabled': String(!!form.giftSoundsEnabled),
    'chat.requireGiftToDm': String(!!form.requireGiftToDm),
    'rooms.rules_text': String(form.roomRulesText ?? ''),
    autoModeration: String(!!form.autoModeration),
    auto_moderation: String(!!form.autoModeration),
    live_nsfw_enabled: String(!!form.liveNsfwEnabled),
    chat_promo_filter_enabled: String(!!form.chatPromoFilterEnabled),
    chat_promo_kick_enabled: String(!!form.chatPromoKickEnabled),
    chat_blocked_extra_keywords: String(form.chatBlockedExtraKeywords ?? ''),
    requireStreamReview: String(!!form.requireStreamReview),
    reportSlaHours: String(form.reportSlaHours ?? ''),
    live_nsfw_confidence: String(form.liveNsfwConfidence ?? 0.78),
    live_nsfw_consecutive: String(form.liveNsfwConsecutive ?? 3),
    live_nsfw_stream_ban_hours: String(form.liveNsfwStreamBanHours ?? 24),
    live_nsfw_warn_strikes: String(form.liveNsfwWarnStrikes ?? 1),
    live_nsfw_mute_strikes: String(form.liveNsfwMuteStrikes ?? 2),
    live_nsfw_stream_ban_strikes: String(form.liveNsfwStreamBanStrikes ?? 3),
    live_nsfw_perm_ban_strikes: String(form.liveNsfwPermBanStrikes ?? 5),
  }
  const { error: err } = await settingsApi.update(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = t('common.saved')
  toast().success(success.value)
}

onMounted(async () => {
  // Always refresh Super/operator flags so "أدمنز اللوحة" appears after deploy
  await auth.fetchMe()
  syncTabFromRoute()
  if (settingsTab.value === 'mysterious-man') loadMysteryConfig()
  if (settingsTab.value === 'staff-identity') loadStaffIdentity()
  fillAccountFromAuth()
  load()
})
</script>
