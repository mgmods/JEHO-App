<template>
  <div>
    <PageHeader
      title="سياسة المنصة PDF"
      subtitle="ملف للمضيفات وفتّاحي الوكالات والداعمين — تنزيل PDF ينزل الملف مباشرة على جهازك مع دعم العربية"
    >
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="loading" @click="load">
          <i class="bi bi-arrow-clockwise me-1"></i> تحديث البيانات
        </button>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="exporting || loading" @click="onExport">
          <span v-if="exporting" class="spinner-border spinner-border-sm me-1"></span>
          <i v-else class="bi bi-download me-1"></i>
          تنزيل PDF على الجهاز
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="danger" class="mb-3" @dismiss="error = ''" />

    <div class="row g-3">
      <div class="col-lg-4">
        <div class="glass p-3 mb-3">
          <h6 class="mb-2">الأقسام في الملف</h6>
          <div class="d-flex gap-2 mb-2">
            <button class="btn btn-sm btn-ghost" type="button" @click="setAll(true)">الكل</button>
            <button class="btn btn-sm btn-ghost" type="button" @click="setAll(false)">لا شيء</button>
          </div>
          <div class="form-check" v-for="sec in sectionDefs" :key="sec.id">
            <input class="form-check-input" type="checkbox" :id="'sec-' + sec.id" v-model="enabled[sec.id]" />
            <label class="form-check-label" :for="'sec-' + sec.id">{{ sec.label }}</label>
          </div>
        </div>

        <div class="glass p-3 mb-3">
          <h6 class="mb-2">نصوص الغلاف</h6>
          <label class="form-label">عنوان رئيسي</label>
          <input v-model="meta.title" class="form-control mb-2" />
          <label class="form-label">جمهور الملف</label>
          <input v-model="meta.audience" class="form-control mb-2" />
          <label class="form-label">مقدمة قصيرة</label>
          <textarea v-model="meta.intro" class="form-control mb-2" rows="3"></textarea>
          <label class="form-label">نص إضافي للصفحة الأولى</label>
          <textarea v-model="meta.coverExtra" class="form-control" rows="3"></textarea>
        </div>

        <div class="glass p-3">
          <h6 class="mb-2">تعديل أسعار الكتالوج (PDF فقط)</h6>
          <p class="small text-muted mb-2">لا يحفظ على السيرفر ولا يغيّر التطبيق.</p>
          <div v-if="enabled.gifts" class="mb-3">
            <div class="small fw-semibold mb-1">هدايا (أول 30)</div>
            <div v-for="(g, i) in editable.gifts.slice(0, 30)" :key="'g' + i" class="d-flex gap-2 mb-1">
              <input v-model="g.name" class="form-control form-control-sm" />
              <input v-model.number="g.coinPrice" type="number" class="form-control form-control-sm" style="max-width:6.5rem" />
            </div>
          </div>
          <div v-if="enabled.packages" class="mb-3">
            <div class="small fw-semibold mb-1">باقات الشحن</div>
            <div v-for="(p, i) in editable.packages" :key="'p' + i" class="d-flex gap-2 mb-1 align-items-center">
              <span class="small text-muted" style="min-width:3.2rem">${{ p.priceUsd }}</span>
              <input v-model.number="p.coins" type="number" class="form-control form-control-sm" />
              <input v-model.number="p.bonusCoins" type="number" class="form-control form-control-sm" />
            </div>
          </div>
          <div v-if="enabled.offers" class="mb-3">
            <div class="small fw-semibold mb-1">عروض المتجر</div>
            <div v-for="(o, i) in editable.storeOffers" :key="'o' + i" class="d-flex gap-2 mb-1">
              <input v-model="o.title" class="form-control form-control-sm" />
              <input v-model.number="o.priceUsd" type="number" class="form-control form-control-sm" style="max-width:5rem" />
              <input v-model.number="o.coins" type="number" class="form-control form-control-sm" style="max-width:6rem" />
            </div>
          </div>
          <div v-if="enabled.vip" class="mb-2">
            <div class="small fw-semibold mb-1">VIP</div>
            <div v-for="(v, i) in editable.vipPlans.slice(0, 20)" :key="'v' + i" class="d-flex gap-2 mb-1">
              <input v-model="v.name" class="form-control form-control-sm" />
              <input v-model.number="v.priceCoins" type="number" class="form-control form-control-sm" style="max-width:6.5rem" />
            </div>
          </div>
        </div>
      </div>

      <div class="col-lg-8">
        <div class="glass p-2 policy-preview-shell">
          <LoadingSpinner v-if="loading" class="p-5" />
          <div v-show="!loading" ref="brochureEl" id="policy-brochure" class="policy-brochure" dir="rtl">
            <section class="pb-page pb-cover">
              <div class="pb-cover-glow"></div>
              <img class="pb-logo" :src="logoSrc" alt="JEHO CHAT" crossorigin="anonymous" />
              <div class="pb-brand">JEHO CHAT</div>
              <h1>{{ meta.title }}</h1>
              <p class="pb-audience">{{ meta.audience }}</p>
              <p class="pb-intro">{{ meta.intro }}</p>
              <div class="pb-meta">{{ formatDate(snapshot?.generatedAt) }} · JEHO Policy Brochure</div>
              <a class="pb-cta" :href="meta.appDownloadUrl" target="_blank" rel="noopener">
                {{ meta.appDownloadLabel }}
              </a>
            </section>

            <section v-if="enabled.toc" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>•</span> فهرس المحتوى</header>
              <ol class="pb-toc">
                <li v-for="sec in activeSections" :key="'toc-' + sec.id">{{ sec.label }}</li>
              </ol>
            </section>

            <section v-if="enabled.economy" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>١</span> الاقتصاد والعملات</header>
              <p>العملات تُشترى بالشحن وتُنفق على الهدايا والألعاب والعضوية والمول. الألماس يُسكّ من الهدايا للمستلم ويمكن سحبه نقداً وفق الحد الأدنى.</p>
              <ul class="pb-list">
                <li>سكّ هدية عادية → ألماس بنسبة {{ pct(economy.giftDiamondRatio) }} من قيمة العملات</li>
                <li>سكّ هدية حظ → ألماس بنسبة {{ pct(economy.luckyGiftDiamondRatio) }}</li>
                <li>تبديل ألماس → عملات: ×{{ economy.diamondCoinRate }}</li>
                <li>سعر السحب: ١ ألماسة ≈ ${{ economy.diamondUsdRate }}</li>
                <li>حد أدنى للسحب: {{ formatNumber(economy.withdrawTargetDiamonds) }} ألماسة</li>
                <li>ممنوع الدعم الذاتي</li>
              </ul>
              <table v-if="editable.withdrawPackages.length" class="pb-table">
                <thead><tr><th>باقة سحب</th><th>ألماس</th><th>قيمة تقريبية $</th></tr></thead>
                <tbody>
                  <tr v-for="(w, i) in editable.withdrawPackages" :key="'w' + i">
                    <td>{{ i + 1 }}</td>
                    <td>{{ formatNumber(w.diamonds) }}</td>
                    <td>${{ w.usd }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.split" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>٢</span> تقسيم الهدايا والوكالة</header>
              <table class="pb-table">
                <thead><tr><th>الحالة</th><th>المنصة</th><th>الوكالة</th><th>المضيف</th></tr></thead>
                <tbody>
                  <tr>
                    <td>روم وكالة</td>
                    <td>{{ pct(economy.platformShare) }}</td>
                    <td>{{ pct(economy.agencyShare) }}</td>
                    <td>{{ pct(economy.hostShareWithAgency) }}</td>
                  </tr>
                  <tr>
                    <td>بلا وكالة</td>
                    <td>{{ pct(economy.platformShare) }}</td>
                    <td>—</td>
                    <td>{{ pct(economy.hostShareSolo) }}</td>
                  </tr>
                </tbody>
              </table>
              <ul class="pb-list">
                <li>فتح وكالة ≈ {{ formatNumber(economy.agencyCreateCoins) }} عملة + موافقة الإدارة</li>
                <li>لا يوجد نظام عائلات — الوكالة هي النظام الرسمي</li>
                <li>دعوة ضيف جديد (مضيفة وكالة): {{ economy.hostInviteDiamonds }} ألماسة · حد {{ economy.hostInviteMaxPerDay }}/يوم</li>
                <li>روم خدمة العملاء: غرفة رسمية مجانية تضعها الإدارة أعلى القائمة</li>
              </ul>
            </section>

            <section v-if="enabled.gifts" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>٣</span> كتالوج الهدايا</header>
              <table class="pb-table">
                <thead><tr><th></th><th>الهدية</th><th>النوع</th><th>السعر</th></tr></thead>
                <tbody>
                  <tr v-for="(g, i) in editable.gifts" :key="'gt' + i">
                    <td class="pb-ico">
                      <img v-if="g.iconUrl" :src="g.iconUrl" alt="" crossorigin="anonymous" @error="g.iconUrl = ''" />
                    </td>
                    <td>{{ g.name }}</td>
                    <td>{{ giftTypeLabel(g.type) }}</td>
                    <td>{{ formatNumber(g.coinPrice) }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.luckyTiers" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>٤</span> هدايا الحظ</header>
              <p>للداعم: قد يرجع مردود عملات. للمضيف: سكّ ألماس بنسبة أقل من العادي.</p>
              <table class="pb-table">
                <thead><tr><th>الطبقة</th><th>السعر</th><th>سكّ للمستلم</th></tr></thead>
                <tbody>
                  <tr v-for="(t, i) in editable.luckyGiftTiers" :key="'lt' + i">
                    <td>{{ t.name }}</td>
                    <td>{{ formatNumber(t.coinPrice) }} عملة</td>
                    <td>{{ pct(economy.luckyGiftDiamondRatio) }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.packages" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>٥</span> باقات الشحن</header>
              <table class="pb-table">
                <thead><tr><th>السعر</th><th>العملات</th><th>بونص</th><th>الإجمالي</th></tr></thead>
                <tbody>
                  <tr v-for="(p, i) in editable.packages" :key="'pt' + i">
                    <td>${{ p.priceUsd }}</td>
                    <td>{{ formatNumber(p.coins) }}</td>
                    <td>{{ formatNumber(p.bonusCoins || 0) }}</td>
                    <td>{{ formatNumber((p.coins || 0) + (p.bonusCoins || 0)) }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.offers" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>٦</span> عروض المتجر</header>
              <table class="pb-table">
                <thead><tr><th>العرض</th><th>السعر</th><th>عملات</th><th>بونص</th></tr></thead>
                <tbody>
                  <tr v-for="(o, i) in editable.storeOffers" :key="'ot' + i">
                    <td>{{ o.title }}</td>
                    <td>${{ o.priceUsd }}</td>
                    <td>{{ formatNumber(o.coins) }}</td>
                    <td>{{ formatNumber(o.bonusCoins || 0) }}</td>
                  </tr>
                  <tr v-if="!editable.storeOffers.length"><td colspan="4">لا عروض مفعّلة حالياً</td></tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.promos" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>٧</span> العروض والترقيات</header>
              <template v-if="promo">
                <h4 class="pb-sub">عروض شهرية</h4>
                <table class="pb-table">
                  <thead><tr><th>العرض</th><th>العتبة $</th><th>المكافأة</th></tr></thead>
                  <tbody>
                    <tr v-for="(o, i) in (promo.monthlyOffers || [])" :key="'mo' + i">
                      <td>{{ o.titleAr || o.titleEn }}</td>
                      <td>${{ o.thresholdUsd }}</td>
                      <td>{{ o.rewardDays }} يوم</td>
                    </tr>
                  </tbody>
                </table>
                <h4 class="pb-sub">بونص وكلاء الشحن</h4>
                <table class="pb-table">
                  <thead><tr><th>المستوى</th><th>شحن $</th><th>بونص</th></tr></thead>
                  <tbody>
                    <tr v-for="(t, i) in (promo.agentTiers || [])" :key="'at' + i">
                      <td>{{ t.titleAr || t.id }}</td>
                      <td>${{ t.thresholdUsd }}</td>
                      <td>+{{ t.bonusPercent }}%</td>
                    </tr>
                  </tbody>
                </table>
                <h4 class="pb-sub">باقات الداعم</h4>
                <table class="pb-table">
                  <thead><tr><th>الباقة</th><th>العتبة $</th><th>المدة</th></tr></thead>
                  <tbody>
                    <tr v-for="(p, i) in (promo.supporterPacks || [])" :key="'sp' + i">
                      <td>{{ p.titleAr || p.titleEn }}</td>
                      <td>${{ p.thresholdUsd }}</td>
                      <td>{{ p.rewardDays }} يوم</td>
                    </tr>
                  </tbody>
                </table>
                <h4 class="pb-sub">مدد VIP (إيجار فقط — بلا دائم)</h4>
                <table class="pb-table">
                  <thead><tr><th>المدة</th><th>الأيام</th></tr></thead>
                  <tbody>
                    <tr v-for="(d, i) in (promo.vipDurationPacks || [])" :key="'vd' + i">
                      <td>{{ d.labelAr || d.labelEn || d.id }}</td>
                      <td>{{ d.days }}</td>
                    </tr>
                  </tbody>
                </table>
              </template>
              <p v-else class="pb-muted">لا بيانات ترقيات محملة</p>
            </section>

            <section v-if="enabled.vip" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>٨</span> العضوية المميزة VIP</header>
              <table class="pb-table">
                <thead><tr><th>المستوى</th><th>الاسم</th><th>السعر / شهر</th></tr></thead>
                <tbody>
                  <tr v-for="(v, i) in editable.vipPlans" :key="'vt' + i">
                    <td>{{ v.level }}</td>
                    <td>{{ v.name }}</td>
                    <td>{{ formatNumber(v.priceCoins) }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.vanity" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>٩</span> الآي دي المميز</header>
              <p>من التطبيق: الملف الشخصي → الآي دي المميز · يُشترى بالعملات ويستبدل الآيدي الحالي.</p>
              <table class="pb-table">
                <thead><tr><th>الآي دي</th><th>السعر</th></tr></thead>
                <tbody>
                  <tr v-for="(v, i) in editable.vanityIds" :key="'va' + i">
                    <td class="font-monospace">{{ v.publicId }}</td>
                    <td>{{ formatNumber(v.priceCoins) }}</td>
                  </tr>
                  <tr v-if="!editable.vanityIds.length"><td colspan="2">لا أرقام متاحة للبيع حالياً</td></tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.cosmetics" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>١٠</span> متجر المظهر</header>
              <table class="pb-table">
                <thead><tr><th>الاسم</th><th>النوع</th><th>السعر</th></tr></thead>
                <tbody>
                  <tr v-for="(c, i) in editable.cosmetics" :key="'c' + i">
                    <td>{{ c.name }}</td>
                    <td>{{ cosmeticLabel(c.type) }}</td>
                    <td>{{ formatNumber(c.priceCoins) }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.lucky" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>١١</span> صناديق الحظ</header>
              <table class="pb-table">
                <thead><tr><th>الصندوق</th><th>التكلفة</th><th>ملاحظة</th></tr></thead>
                <tbody>
                  <tr v-for="(b, i) in editable.luckyBoxes" :key="'b' + i">
                    <td>{{ b.name }}</td>
                    <td>{{ b.priceCoins ? formatNumber(b.priceCoins) + ' عملة' : 'مجاني' }}</td>
                    <td>{{ b.note || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.hostTarget" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>١٢</span> تارجت المضيفة وجدول الرواتب</header>
              <p>
                التارجت بالكوينز، وقاعدة العرض: <strong>١ كوين = ١ ألماسة</strong>.
                عند إكمال المرحلة: راتب المضيف + راتب الوكيل (بالدولار) حسب الجدول.
              </p>
              <table v-if="hostTargetRows.length" class="pb-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>التارجت (كوينز)</th>
                    <th>١ كوين = ١ ألماسة</th>
                    <th>راتب المضيف</th>
                    <th>راتب الوكيل</th>
                    <th>الإجمالي</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(r, i) in hostTargetRows" :key="'h' + i">
                    <td>{{ r.stage }}</td>
                    <td>{{ formatNumber(r.threshold) }}</td>
                    <td>{{ formatNumber(r.threshold) }}</td>
                    <td>${{ r.hostSalaryUsd }}</td>
                    <td>${{ r.agentSalaryUsd }}</td>
                    <td>${{ r.totalUsd }}</td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="pb-muted">حمّل الجدول من صفحة تارجت المضيف في الداشبورد ثم حدّث البيانات.</p>
              <a class="pb-cta" :href="meta.appDownloadUrl" target="_blank" rel="noopener">
                {{ meta.appDownloadLabel }}
              </a>
            </section>

            <section v-if="enabled.tasks" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>١٣</span> المهام والدعوات</header>
              <ul class="pb-list">
                <li>مركز المهام يومياً — نقاط/فضة/عملات حسب المهمة</li>
                <li>دعوة روم مكتملة: {{ economy.hostInviteDiamonds }} ألماسة (مضيفة وكالة · حد {{ economy.hostInviteMaxPerDay }}/يوم)</li>
                <li>الألماس الأساسي من الهدايا والتارجت والدعوات</li>
              </ul>
            </section>

            <section v-if="enabled.engage" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>١٤</span> مسابقات · ترتيب · كأس · ألعاب · دراما · بنرات</header>
              <ul class="pb-list">
                <li>مسابقات موسمية ولوحات ترتيب (ثروة/سحر) يوم/أسبوع/شهر</li>
                <li>كأس الروم: تنافس الغرف على نشاط الهدايا</li>
                <li>الدراما: حلقات قصيرة مع مكافآت مشاهدة عند التفعيل</li>
                <li>البنرات تظهر في الصفحة الرئيسية للتطبيق</li>
              </ul>
              <table v-if="editable.games.length" class="pb-table">
                <thead><tr><th>الألعاب المتاحة</th></tr></thead>
                <tbody>
                  <tr v-for="(g, i) in editable.games" :key="'gm' + i"><td>{{ g.name }}</td></tr>
                </tbody>
              </table>
              <table v-if="editable.homeBanners.length" class="pb-table">
                <thead><tr><th>بنر</th><th>رابط</th></tr></thead>
                <tbody>
                  <tr v-for="(b, i) in editable.homeBanners" :key="'bn' + i">
                    <td>{{ b.title }}</td>
                    <td class="small">{{ b.link || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </section>

            <section v-if="enabled.payments" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>١٥</span> الشحن والدفع ووكلاء الشحن</header>
              <ul class="pb-list">
                <li>Google Play / متجر التطبيق للشحن الرسمي</li>
                <li v-if="paymentFlags.shamCash">ShamCash متاح كقناة محلية</li>
                <li v-if="paymentFlags.binancePay">Binance Pay عند التفعيل</li>
                <li v-if="paymentFlags.fourthwall">قنوات متجر إضافية عند التفعيل</li>
                <li>وكلاء الشحن المعتمدون داخل التطبيق فقط</li>
              </ul>
              <p class="pb-muted">لا تشارك بيانات الدفع خارج القنوات الرسمية في JEHO CHAT.</p>
            </section>

            <section v-if="enabled.rules" class="pb-page">
              <div class="pb-topbar"><img :src="logoSrc" alt="" /><span>JEHO CHAT</span></div>
              <header class="pb-head"><span>١٦</span> قواعد عامة</header>
              <ul class="pb-list">
                <li>الاحتيال والحسابات المتعددة = إيقاف المكافآت أو الحظر</li>
                <li>التفسير النهائي لإدارة JEHO CHAT</li>
                <li>الأسعار والعروض قابلة للتحديث — اعتمد النسخة الأحدث</li>
              </ul>
              <div class="pb-footer">
                <img :src="logoSrc" alt="JEHO CHAT" />
                <div>
                  <strong>JEHO CHAT</strong>
                  <div>Voice rooms · Gifts · Agencies · Play</div>
                </div>
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { policyBrochureApi } from '@/api'
import { extractList, formatNumber } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { exportPolicyPdf } from '@/utils/exportPolicyPdf'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import brandLogo from '@/assets/brand/logo.png'
import jehoLogo from '@/assets/brand/jeho_logo.png'

const logoSrc = brandLogo || jehoLogo
const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const snapshot = ref(null)
const brochureEl = ref(null)

const sectionDefs = [
  { id: 'toc', label: 'فهرس المحتوى' },
  { id: 'economy', label: 'الاقتصاد والعملات والسحب' },
  { id: 'split', label: 'تقسيم الهدايا والوكالة' },
  { id: 'gifts', label: 'كتالوج الهدايا' },
  { id: 'luckyTiers', label: 'هدايا الحظ' },
  { id: 'packages', label: 'باقات الشحن' },
  { id: 'offers', label: 'عروض المتجر' },
  { id: 'promos', label: 'العروض والترقيات' },
  { id: 'vip', label: 'العضوية VIP' },
  { id: 'vanity', label: 'الآي دي المميز' },
  { id: 'cosmetics', label: 'الإطارات والدخولية' },
  { id: 'lucky', label: 'صناديق الحظ' },
  { id: 'hostTarget', label: 'تارجت المضيفة + جدول الرواتب' },
  { id: 'tasks', label: 'المهام والدعوات' },
  { id: 'engage', label: 'مسابقات / ألعاب / دراما / بنرات' },
  { id: 'payments', label: 'الدفع ووكلاء الشحن' },
  { id: 'rules', label: 'القواعد العامة' },
]

const enabled = reactive(Object.fromEntries(sectionDefs.map((s) => [s.id, true])))

const meta = reactive({
  title: 'دليل سياسة JEHO CHAT',
  audience: 'للمضيفات · فتح الوكالات · الداعمين',
  intro:
    'JEHO CHAT تطبيق غرف صوتية مباشرة: تلتقي بالمضيفات، ترسل الهدايا، تفتح وكالة، وتكسب من التارجت والنشاط داخل الروم. هذا الدليل يشرح الاقتصاد، الرواتب، والشحن بلغة واضحة.',
  coverExtra:
    'استخدم هذا الملف كمرجع رسمي قبل فتح وكالة أو بدء الاستضافة أو الشحن. الأسعار والجداول قابلة للتحديث من لوحة الإدارة.',
  appDownloadUrl: 'https://play.google.com/store/apps/details?id=com.Dramizo.Series',
  appDownloadLabel: 'تحميل تطبيق JEHO CHAT',
})

const editable = reactive({
  gifts: [],
  packages: [],
  storeOffers: [],
  vipPlans: [],
  vanityIds: [],
  cosmetics: [],
  luckyBoxes: [],
  luckyGiftTiers: [],
  withdrawPackages: [],
  games: [],
  homeBanners: [],
})

const economy = computed(() => snapshot.value?.economy || {})
const paymentFlags = computed(() => snapshot.value?.paymentFlags || {})
const promo = computed(() => snapshot.value?.promoCatalog || null)

const activeSections = computed(() =>
  sectionDefs.filter((s) => s.id !== 'toc' && enabled[s.id]),
)

const hostTargetRows = computed(() => {
  const raw = snapshot.value?.hostTarget
  if (!Array.isArray(raw) || !raw.length) return []
  return raw.map((row, i) => {
    const host = Math.max(0, Number(row.hostSalaryUsd) || 0)
    const agent = Math.max(0, Number(row.agentSalaryUsd) || 0)
    const threshold = Number(row.threshold ?? row.target ?? row.targetCoins ?? 0) || 0
    return {
      stage: row.stage ?? row.level ?? row.index ?? i + 1,
      threshold,
      hostSalaryUsd: host,
      agentSalaryUsd: agent,
      totalUsd: Math.max(0, Number(row.totalUsd) || host + agent),
      coins: row.rewardCoins ?? row.coins ?? 0,
      diamonds: row.rewardDiamonds ?? row.diamonds ?? 0,
    }
  })
})

function setAll(v) {
  for (const s of sectionDefs) enabled[s.id] = v
}

function pct(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return n <= 1 ? `${Math.round(n * 100)}٪` : `${Math.round(n)}٪`
}

function formatDate(iso) {
  if (!iso) return ''
  try {
    return new Date(iso).toLocaleDateString('ar-IQ', { year: 'numeric', month: 'long', day: 'numeric' })
  } catch {
    return String(iso).slice(0, 10)
  }
}

function giftTypeLabel(t) {
  const s = String(t || '').toLowerCase()
  if (s.includes('lucky')) return 'حظ'
  if (s.includes('agency')) return 'وكالة'
  return 'عادية'
}

function cosmeticLabel(t) {
  const map = {
    entry_effect: 'دخولية',
    join_toast: 'توست انضمام',
    room_card: 'بطاقة روم',
    room_background: 'خلفية روم',
    vip_badge: 'إطار VIP',
    host_badge: 'شارة مضيف',
    level_badge: 'شارة مستوى',
  }
  return map[t] || t || '—'
}

function applySnapshot(data) {
  snapshot.value = data
  if (data?.notes?.intro) meta.intro = data.notes.intro
  editable.gifts = (data.gifts || []).map((g) => ({
    name: g.name,
    coinPrice: g.coinPrice,
    type: g.type,
    iconUrl: g.iconUrl || '',
  }))
  const packs = extractList(data.packages) || data.packages?.items || data.packages || []
  editable.packages = (Array.isArray(packs) ? packs : []).map((p) => ({
    priceUsd: p.priceUsd ?? p.price ?? 0,
    coins: p.coins ?? 0,
    bonusCoins: p.bonusCoins ?? p.bonus ?? 0,
  }))
  editable.storeOffers = (data.storeOffers || []).map((o) => ({ ...o }))
  editable.vipPlans = (data.vipPlans || []).map((v) => ({
    name: v.name,
    level: v.level,
    priceCoins: v.priceCoins,
  }))
  editable.vanityIds = (data.vanityIds || []).map((v) => ({
    publicId: v.publicId,
    priceCoins: v.priceCoins,
  }))
  editable.cosmetics = (data.cosmetics || []).map((c) => ({ ...c }))
  editable.luckyBoxes = (data.luckyBoxes || []).map((b) => ({ ...b }))
  editable.luckyGiftTiers = (data.luckyGiftTiers || []).map((t) => ({ ...t }))
  editable.withdrawPackages = (data.withdrawPackages || []).map((w) => ({ ...w }))
  editable.games = (data.games || []).map((g) => ({ ...g }))
  editable.homeBanners = (data.homeBanners || []).map((b) => ({ ...b }))
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await policyBrochureApi.snapshot()
  loading.value = false
  if (err) {
    error.value = err.message || 'تعذر تحميل بيانات السياسة'
    toast().danger(error.value)
    return
  }
  applySnapshot(data?.data || data || {})
}

async function onExport() {
  if (loading.value || !snapshot.value) {
    toast().danger('انتظر تحميل السياسة ثم أعد المحاولة')
    return
  }
  exporting.value = true
  try {
    const stamp = new Date().toISOString().slice(0, 10)
    const size = await exportPolicyPdf(
      {
        meta: { ...meta },
        enabled: { ...enabled },
        economy: economy.value,
        paymentFlags: paymentFlags.value,
        promo: promo.value,
        editable: {
          ...editable,
          gifts: editable.gifts.map((g) => ({
            ...g,
            typeLabel: giftTypeLabel(g.type),
          })),
          cosmetics: editable.cosmetics.map((c) => ({
            ...c,
            typeLabel: cosmeticLabel(c.type),
          })),
          vanityIds: editable.vanityIds.map((v) => ({
            ...v,
            code: v.publicId,
          })),
        },
        hostTargetRows: hostTargetRows.value,
        activeSections: activeSections.value,
        logoSrc,
        generatedAt: formatDate(snapshot.value?.generatedAt),
      },
      `JEHO-CHAT-policy-${stamp}.pdf`,
    )
    toast().success(
      size
        ? 'تم تنزيل ملف PDF على جهازك (مجلد التنزيلات)'
        : 'تم إنشاء PDF — راجع مجلد التنزيلات',
    )
  } catch (e) {
    toast().danger(e?.message || 'فشل تنزيل PDF')
  }
  exporting.value = false
}

onMounted(load)
</script>

<style scoped>
.policy-preview-shell {
  max-height: calc(100vh - 8rem);
  overflow: auto;
  background: #0a0c10;
}
.policy-brochure {
  color: #eef1f6;
  font-family: 'IBM Plex Sans Arabic', Sora, system-ui, sans-serif;
  background: #0a0c10;
}
.pb-page {
  position: relative;
  padding: 1.25rem 1.2rem 1.6rem;
  margin: 0 0 1rem;
  border-radius: 18px;
  border: 1px solid rgba(61, 220, 151, 0.18);
  background:
    radial-gradient(ellipse 70% 50% at 100% 0%, rgba(61, 220, 151, 0.12), transparent 55%),
    radial-gradient(ellipse 50% 40% at 0% 100%, rgba(232, 196, 124, 0.1), transparent 50%),
    linear-gradient(165deg, #12151c 0%, #0a0c10 100%);
  page-break-after: always;
  break-after: page;
}
.pb-topbar {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin-bottom: 0.85rem;
  padding-bottom: 0.55rem;
  border-bottom: 1px solid rgba(232, 196, 124, 0.28);
}
.pb-topbar img {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  object-fit: cover;
}
.pb-topbar span {
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #e8c47c;
  font-size: 0.78rem;
}
.pb-cover {
  text-align: center;
  padding: 3rem 1.5rem;
  min-height: 520px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.pb-cover-glow {
  position: absolute;
  inset: 10% 15%;
  background: radial-gradient(circle, rgba(61, 220, 151, 0.2), transparent 70%);
  pointer-events: none;
}
.pb-logo {
  width: 112px;
  height: 112px;
  border-radius: 26px;
  object-fit: cover;
  box-shadow: 0 0 0 3px rgba(232, 196, 124, 0.6), 0 16px 40px rgba(0, 0, 0, 0.45);
  margin-bottom: 1rem;
  position: relative;
  z-index: 1;
  background: #12151c;
}
.pb-brand {
  font-weight: 800;
  letter-spacing: 0.18em;
  color: #e8c47c;
  font-size: 0.9rem;
  margin-bottom: 0.75rem;
  position: relative;
  z-index: 1;
}
.pb-cover h1 {
  font-size: 1.85rem;
  font-weight: 800;
  margin: 0 0 0.5rem;
  position: relative;
  z-index: 1;
}
.pb-audience {
  color: #6ef0b5;
  font-weight: 600;
  margin-bottom: 1rem;
  position: relative;
  z-index: 1;
}
.pb-intro {
  max-width: 36rem;
  color: rgba(238, 241, 246, 0.78);
  line-height: 1.7;
  position: relative;
  z-index: 1;
}
.pb-meta {
  margin-top: 1.5rem;
  font-size: 0.8rem;
  color: rgba(238, 241, 246, 0.45);
  position: relative;
  z-index: 1;
}
.pb-head {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  font-size: 1.12rem;
  font-weight: 800;
  margin-bottom: 0.75rem;
}
.pb-head span {
  width: 1.7rem;
  height: 1.7rem;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1fa86a, #e8c47c);
  color: #04140c;
  font-size: 0.85rem;
}
.pb-sub {
  margin: 1rem 0 0.4rem;
  font-size: 0.92rem;
  color: #6ef0b5;
}
.pb-list {
  padding-inline-start: 1.1rem;
  margin: 0.5rem 0 0;
  line-height: 1.85;
}
.pb-toc {
  line-height: 2;
  padding-inline-start: 1.25rem;
}
.pb-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 0.75rem;
  font-size: 0.84rem;
}
.pb-table th,
.pb-table td {
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 0.4rem 0.5rem;
  text-align: center;
}
.pb-table thead th {
  background: rgba(61, 220, 151, 0.16);
  color: #6ef0b5;
  font-weight: 700;
}
.pb-table tbody tr:nth-child(even) {
  background: rgba(255, 255, 255, 0.03);
}
.pb-ico {
  width: 36px;
}
.pb-ico img {
  width: 28px;
  height: 28px;
  object-fit: contain;
}
.pb-muted {
  color: rgba(238, 241, 246, 0.55);
  font-size: 0.9rem;
}
.pb-footer {
  margin-top: 1.5rem;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(232, 196, 124, 0.25);
}
.pb-footer img {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  object-fit: cover;
}
.pb-footer strong {
  color: #e8c47c;
}

@media print {
  :global(body *) { visibility: hidden !important; }
  .policy-brochure,
  .policy-brochure * { visibility: visible !important; }
  .policy-preview-shell {
    max-height: none !important;
    overflow: visible !important;
    position: absolute;
    inset: 0;
    background: #0a0c10 !important;
  }
  .pb-page {
    break-after: page;
    page-break-after: always;
    margin: 0;
    border-radius: 0;
  }
}
</style>
