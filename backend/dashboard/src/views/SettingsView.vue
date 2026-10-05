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

        <template v-else-if="settingsTab === 'splash'">
          <section class="settings-card">
            <h3 class="settings-card-title">Splash Screen ديناميكي</h3>
            <p class="form-text mb-3">الصور والمدة والترتيب تُحفظ على السيرفر وتُستخدم عند التشغيل القادم بدون تحديث APK.</p>
            <div class="row g-3 mb-3">
              <div class="col-md-4">
                <label class="form-label">النظام مفعّل</label>
                <select v-model="splashForm.enabled" class="form-select">
                  <option :value="true">مفعّل</option>
                  <option :value="false">متوقف</option>
                </select>
              </div>
              <div class="col-md-4">
                <label class="form-label">زر التخطي</label>
                <select v-model="splashForm.skipEnabled" class="form-select">
                  <option :value="true">ظاهر</option>
                  <option :value="false">مخفي</option>
                </select>
              </div>
              <div class="col-md-4">
                <label class="form-label">إضافة صورة</label>
                <input type="file" accept="image/*" multiple class="form-control" :disabled="splashUploading" @change="addSplashFiles" />
              </div>
            </div>

            <div v-if="!splashItems.length" class="alert alert-info">لا توجد صور Splash مضافة حالياً.</div>
            <div v-for="(item, idx) in splashItems" :key="item.id || idx" class="border rounded-3 p-3 mb-3">
              <div class="row g-3 align-items-start">
                <div class="col-md-4">
                  <img :src="splashAbsUrl(item.url)" alt="" style="width:100%;max-height:220px;object-fit:contain;background:#fff;border-radius:12px;border:1px solid #e5e7eb" />
                  <input type="file" accept="image/*" class="form-control form-control-sm mt-2" :disabled="splashUploading" @change="(e) => replaceSplashFile(e, item)" />
                </div>
                <div class="col-md-8">
                  <div class="row g-3">
                    <div class="col-sm-6">
                      <label class="form-label">مدة العرض (ثانية)</label>
                      <input v-model.number="item.durationSeconds" type="number" min="1" max="120" class="form-control" />
                    </div>
                    <div class="col-sm-6">
                      <label class="form-label">الترتيب</label>
                      <input v-model.number="item.sortOrder" type="number" min="0" class="form-control" />
                    </div>
                    <div class="col-sm-6">
                      <label class="form-label">الحالة</label>
                      <select v-model="item.active" class="form-select">
                        <option :value="true">فعّالة</option>
                        <option :value="false">معطّلة</option>
                      </select>
                    </div>
                    <div class="col-sm-6 d-flex align-items-end">
                      <button class="btn btn-outline-danger w-100" type="button" @click="removeSplashItem(idx)">حذف الصورة</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="d-flex gap-2 flex-wrap">
              <button class="btn btn-aurora" type="button" :disabled="splashSaving || splashUploading" @click="saveSplash">
                <span v-if="splashSaving" class="spinner-border spinner-border-sm me-1" /> حفظ إعدادات الـSplash
              </button>
              <button class="btn btn-ghost" type="button" :disabled="splashSaving || splashUploading" @click="loadSplash">إعادة تحميل</button>
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
          <!-- GENERAL -->
          <template v-if="settingsTab === 'general'">
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
import { settingsApi, authApi, operatorsApi, uploadsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import EconomyPanel from '@/components/settings/EconomyPanel.vue'
import PaymentSettingsPanel from '@/components/settings/PaymentSettingsPanel.vue'
import FourthwallPaymentPanel from '@/components/settings/FourthwallPaymentPanel.vue'
import ShamCashPaymentPanel from '@/components/settings/ShamCashPaymentPanel.vue'
import ZegoSettingsPanel from '@/components/settings/ZegoSettingsPanel.vue'
import { setDashboardLocale } from '@/i18n'
import { readDashboardLocale } from '@/utils/locale'
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
  const tabs = [
    { id: 'general', label: t('settings.tabGeneral'), icon: 'bi-sliders' },
    { id: 'economy', label: t('settings.tabEconomy'), icon: 'bi-cash-coin' },
    { id: 'features', label: t('settings.tabFeatures'), icon: 'bi-toggles' },
    { id: 'splash', label: 'Splash Screen', icon: 'bi-images' },
    { id: 'moderation', label: t('settings.tabModeration'), icon: 'bi-shield-check' },
    { id: 'payment', label: t('settings.tabPayment'), icon: 'bi-credit-card' },
    { id: 'zego', label: t('settings.tabZego'), icon: 'bi-broadcast-pin' },
    { id: 'account', label: t('settings.tabAccount'), icon: 'bi-person-lock' },
  ]
  if (auth.isSuperAdmin) {
    tabs.push({ id: 'admins', label: t('settings.tabAdmins'), icon: 'bi-people-fill' })
  }
  tabs.push({ id: 'other', label: t('settings.tabOther'), icon: 'bi-three-dots' })
  return tabs
})

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
const splashLoading = ref(false)
const splashSaving = ref(false)
const splashUploading = ref(false)
const splashForm = reactive({ enabled: true, skipEnabled: true })
const splashItems = ref([])

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
  luckyBoxEnabled: true,
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

function splashAbsUrl(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (/^https?:\/\//i.test(value)) return value
  return value.startsWith('/') ? value : '/' + value
}

function parseAppTheme(raw) {
  if (!raw) return { splash: { enabled: true, skipEnabled: true, items: [] } }
  try {
    const parsed = JSON.parse(String(raw))
    const splash = parsed?.splash || {}
    const items = Array.isArray(splash.items) ? splash.items : []
    return {
      ...parsed,
      splash: {
        enabled: splash.enabled !== false,
        skipEnabled: splash.skipEnabled !== false,
        items: items.map((item, index) => ({
          id: String(item?.id || 'splash-' + Date.now() + '-' + index),
          url: splashAbsUrl(item?.url),
          durationSeconds: Math.max(1, Math.min(120, Number(item?.durationSeconds) || 5)),
          active: item?.active !== false,
          sortOrder: Number.isFinite(Number(item?.sortOrder)) ? Number(item.sortOrder) : index,
        })).filter((item) => item.url),
      },
    }
  } catch {
    return { splash: { enabled: true, skipEnabled: true, items: [] } }
  }
}

async function loadSplash() {
  splashLoading.value = true
  const { data, error: err } = await settingsApi.get()
  splashLoading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const raw = data?.settings || data?.data || data || []
  let value = ''
  if (Array.isArray(raw)) value = raw.find((x) => x?.key === 'app_theme')?.value || ''
  else value = raw?.app_theme || ''
  const theme = parseAppTheme(value)
  splashForm.enabled = theme.splash.enabled
  splashForm.skipEnabled = theme.splash.skipEnabled
  splashItems.value = theme.splash.items
}

function newSplashItem(url, index) {
  return {
    id: 'splash-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
    url: splashAbsUrl(url),
    durationSeconds: 5,
    active: true,
    sortOrder: index,
  }
}

async function addSplashFiles(event) {
  const files = Array.from(event?.target?.files || [])
  if (!files.length) return
  splashUploading.value = true
  try {
    for (const file of files) {
      const { data, error: err } = await uploadsApi.upload(file)
      if (err) throw err
      const url = data?.url || data?.data?.url || ''
      if (url) splashItems.value.push(newSplashItem(url, splashItems.value.length))
    }
    toast().success('تم رفع صور الـSplash')
  } catch (err) {
    error.value = err?.message || 'فشل رفع الصورة'
    toast().danger(error.value)
  } finally {
    splashUploading.value = false
    if (event?.target) event.target.value = ''
  }
}

function uploadedFilename(url) {
  const m = String(url || '').match(/\/uploads\/([^/?#]+)$/i)
  return m ? m[1] : ''
}

async function replaceSplashFile(event, item) {
  const file = event?.target?.files?.[0]
  if (!file) return
  splashUploading.value = true
  const oldFile = uploadedFilename(item.url)
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw err
    const url = data?.url || data?.data?.url || ''
    if (!url) throw new Error('لم يرجع السيرفر رابط الصورة')
    item.url = splashAbsUrl(url)
    if (oldFile) await uploadsApi.remove(oldFile)
    toast().success('تم استبدال الصورة')
  } catch (err) {
    error.value = err?.message || 'فشل استبدال الصورة'
    toast().danger(error.value)
  } finally {
    splashUploading.value = false
    if (event?.target) event.target.value = ''
  }
}

async function removeSplashItem(index) {
  const item = splashItems.value[index]
  if (!item) return
  if (!confirm('حذف صورة الـSplash؟')) return
  splashItems.value.splice(index, 1)
  const filename = uploadedFilename(item.url)
  if (filename) await uploadsApi.remove(filename)
}

async function saveSplash() {
  if (!auth.can('settings', 'write')) {
    error.value = t('settings.readOnly')
    toast().danger(error.value)
    return
  }
  splashSaving.value = true
  error.value = ''
  try {
    const current = await settingsApi.get()
    const raw = current.data?.settings || current.data?.data || current.data || []
    let theme = {}
    let oldValue = ''
    if (Array.isArray(raw)) oldValue = raw.find((x) => x?.key === 'app_theme')?.value || ''
    else oldValue = raw?.app_theme || ''
    try { theme = oldValue ? JSON.parse(String(oldValue)) : {} } catch { theme = {} }
    theme.version = Math.max(1, Number(theme.version) || 1) + 1
    theme.updatedAt = new Date().toISOString()
    theme.splash = {
      enabled: !!splashForm.enabled,
      skipEnabled: !!splashForm.skipEnabled,
      items: splashItems.value
        .filter((item) => item?.url)
        .map((item, index) => ({
          id: String(item.id || 'splash-' + index),
          url: splashAbsUrl(item.url),
          durationSeconds: Math.max(1, Math.min(120, Number(item.durationSeconds) || 5)),
          active: item.active !== false,
          sortOrder: Number.isFinite(Number(item.sortOrder)) ? Number(item.sortOrder) : index,
        }))
        .sort((a, b) => a.sortOrder - b.sortOrder),
    }
    const { error: err } = await settingsApi.update({ app_theme: JSON.stringify(theme) })
    if (err) throw err
    success.value = 'تم حفظ إعدادات الـSplash'
    toast().success(success.value)
  } catch (err) {
    error.value = err?.message || 'فشل حفظ إعدادات الـSplash'
    toast().danger(error.value)
  } finally {
    splashSaving.value = false
  }
}

function selectTab(id) {
  settingsTab.value = id
  router.replace({ name: 'settings', query: { tab: id } })
  if (id === 'account') fillAccountFromAuth()
  if (id === 'admins') loadOperators()
  if (id === 'splash') loadSplash()
}

function syncTabFromRoute() {
  const raw = String(route.query.tab || 'general')
  const allowed = new Set(settingTabs.value.map((x) => x.id))
  settingsTab.value = allowed.has(raw) ? raw : 'general'
  if (settingsTab.value === 'account') fillAccountFromAuth()
  if (settingsTab.value === 'admins') loadOperators()
  if (settingsTab.value === 'splash') loadSplash()
}

watch(() => route.query.tab, syncTabFromRoute)

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
    luckyBoxEnabled: bool(map['lucky_box.enabled'] ?? map.luckyBoxEnabled, form.luckyBoxEnabled),
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
  if (isCoreTab.value) load()
  else if (settingsTab.value === 'splash') loadSplash()
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
    'lucky_box.enabled': String(!!form.luckyBoxEnabled),
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
  fillAccountFromAuth()
  load()
})
</script>
