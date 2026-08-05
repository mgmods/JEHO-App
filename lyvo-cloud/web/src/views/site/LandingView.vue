<template>
  <div>
    <!-- Hero -->
    <section class="hero">
      <div class="hero-glow" aria-hidden="true" />
      <div class="hero-inner">
        <p class="hero-kicker">LYVO Cloud · Voice Infrastructure</p>
        <h1>
          ابنِ غرفك الصوتية<br />
          <span>بث مباشر بثقة</span>
        </h1>
        <p class="hero-lead">
          منصة مطورين للصوت الحي: أنشئ تطبيقاً، اربط
          <strong dir="ltr">wss://</strong>، أصدر توكن، وفعّل الميكروفون.
          دفع بالبطاقة عبر Fourthwall ونظام دقائق واضح.
        </p>
        <div class="hero-cta">
          <RouterLink class="btn btn-primary" :to="{ name: 'register' }">إنشاء حساب مجاني</RouterLink>
          <RouterLink class="btn btn-ghost hero-ghost" :to="{ name: 'how-to' }">طريقة الاستخدام</RouterLink>
        </div>
        <div class="hero-wss" dir="ltr">
          <span>Primary signaling</span>
          <code>wss://voice.adastra.bbs.tr</code>
        </div>
      </div>
    </section>

    <!-- Features -->
    <section class="site-section">
      <div class="site-wrap">
        <header class="sec-head">
          <h2>لماذا LYVO؟</h2>
          <p>كل ما يحتاجه تطبيق بث صوتي — بدون تعقيد منصات عامة غير مناسبة لمنطقتك.</p>
        </header>
        <div class="feat-grid">
          <article class="feat-card">
            <div class="feat-ico">1</div>
            <h3>تطبيقات متعددة</h3>
            <p>أنشئ أكثر من تطبيق بث، كل واحد بمفتاحه وAppID وroom prefix.</p>
          </article>
          <article class="feat-card">
            <div class="feat-ico">2</div>
            <h3>wss:// أولاً</h3>
            <p>نقطة إشارة WebSocket آمنة جاهزة للإنتاج. الربط واضح في لوحة المطور وSDK.</p>
          </article>
          <article class="feat-card">
            <div class="feat-ico">3</div>
            <h3>دقائق وباقات</h3>
            <p>اشترِ دقائق صوتية ببطاقتك. الرصيد يظهر فوراً في حسابك.</p>
          </article>
          <article class="feat-card">
            <div class="feat-ico">4</div>
            <h3>لوحة مطور نظيفة</h3>
            <p>إعدادات دفع المنصة للأدمن فقط — الحساب العادي للتطبيقات والـ SDK فقط.</p>
          </article>
        </div>
      </div>
    </section>

    <!-- Steps preview -->
    <section class="site-section site-section-alt">
      <div class="site-wrap">
        <header class="sec-head">
          <h2>ثلاث خطوات للانطلاق</h2>
          <p>من التسجيل حتى أول بث.</p>
        </header>
        <div class="step-row">
          <div class="step-big">
            <span>01</span>
            <h3>سجّل كمطور</h3>
            <p>حساب مجاني — 10,000 دقيقة تجريبية.</p>
          </div>
          <div class="step-big">
            <span>02</span>
            <h3>أنشئ Live App</h3>
            <p>AppID + Key + Secret، واربط wss://.</p>
          </div>
          <div class="step-big">
            <span>03</span>
            <h3>استدعِ التوكن وابثّ</h3>
            <p>POST /api/v1/token ثم connect في SDK.</p>
          </div>
        </div>
        <div class="center-cta">
          <RouterLink class="btn btn-primary" :to="{ name: 'how-to' }">التفاصيل الكاملة</RouterLink>
        </div>
      </div>
    </section>

    <!-- Packages teaser -->
    <section class="site-section">
      <div class="site-wrap">
        <header class="sec-head">
          <h2>باقات الدقائق</h2>
          <p>أسعار شفافة — الدفع بالبطاقة (Fourthwall).</p>
        </header>
        <div class="pkg-grid">
          <div v-for="p in packs" :key="p.name" class="pkg-card" :class="{ featured: p.hot }">
            <div v-if="p.hot" class="pkg-badge">الأكثر طلباً</div>
            <h3>{{ p.name }}</h3>
            <div class="pkg-mins">{{ p.mins }} <small>دقيقة</small></div>
            <div class="pkg-price">${{ p.price }}</div>
            <p class="pkg-desc">{{ p.desc }}</p>
            <RouterLink class="btn btn-primary btn-block" :to="{ name: 'register' }">اشترِ بعد التسجيل</RouterLink>
          </div>
        </div>
        <div class="center-cta">
          <RouterLink class="btn btn-ghost" :to="{ name: 'pricing' }">كل الباقات</RouterLink>
        </div>
      </div>
    </section>

    <!-- Final CTA -->
    <section class="cta-band">
      <div class="site-wrap cta-inner">
        <h2>جاهز تطلق غرفك الصوتية؟</h2>
        <p>أنشئ حساباً مجاناً وادخل لوحة المطور خلال دقائق.</p>
        <RouterLink class="btn btn-primary" :to="{ name: 'register' }">إنشاء حساب</RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
const packs = [
  { name: 'Starter', mins: '5,000', price: '9', desc: 'مشاريع جانبية وتجارب', hot: false },
  { name: 'Growth', mins: '25,000', price: '39', desc: 'استوديوهات وغرف حية', hot: true },
  { name: 'Scale', mins: '100,000', price: '99', desc: 'إنتاج متعدد التطبيقات', hot: false },
]
</script>
