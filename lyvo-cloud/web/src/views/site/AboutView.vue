<template>
  <div class="site-wrap legal-page">
    <header class="sec-head" style="text-align:start;max-width:none;margin-bottom:24px">
      <h2>عن LYVO Cloud</h2>
      <p>منصة مطورين للصوت المباشر — LYVO Technologies.</p>
    </header>

    <div class="about-grid">
      <div class="card">
        <h3>المهمة</h3>
        <p style="margin-top:10px">
          تسهيل بناء غرف صوت وبث مباشر: مفاتيح واضحة، إشارة
          <span dir="ltr">wss://</span>، دقائق قابلة للشراء، ولوحات منفصلة للمطور وصاحب المنصة.
        </p>
      </div>
      <div class="card">
        <h3>لمن؟</h3>
        <p style="margin-top:10px">
          استوديوهات، تطبيقات دردشة صوتية، فعاليات، ومنصات تحتاج RTC صوتي دون تعقيد فواتير خارجية غير مناسبة.
        </p>
      </div>
      <div class="card">
        <h3>التقنية</h3>
        <p style="margin-top:10px">
          بث صوتي عبر WebRTC مبنياً على طبقة LiveKit ذاتية الاستضافة، مع واجهة LYVO لإدارة التطبيقات والباقات.
        </p>
      </div>
    </div>

    <div class="center-cta" style="margin-top:32px">
      <RouterLink class="btn btn-primary" :to="{ name: 'register' }">انضم كمطور</RouterLink>
      <RouterLink class="btn btn-ghost" :to="{ name: 'how-to' }">طريقة الاستخدام</RouterLink>
    </div>
  </div>
</template>
