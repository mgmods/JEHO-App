<template>
  <div class="site-wrap legal-page">
    <header class="sec-head" style="text-align:start;max-width:none;margin-bottom:28px">
      <h2>الباقات والأسعار</h2>
      <p>دقائق صوت حية — ادفع بالبطاقة بعد إنشاء الحساب.</p>
    </header>

    <div class="pkg-grid">
      <div v-for="p in packs" :key="p.id" class="pkg-card" :class="{ featured: p.hot }">
        <div v-if="p.hot" class="pkg-badge">{{ p.badge }}</div>
        <h3>{{ p.name }}</h3>
        <div class="pkg-mins">{{ p.mins }} <small>دقيقة</small></div>
        <div class="pkg-price">${{ p.price }}</div>
        <p class="pkg-desc">{{ p.desc }}</p>
        <ul class="price-points">
          <li v-for="f in p.features" :key="f">{{ f }}</li>
        </ul>
        <RouterLink class="btn btn-primary btn-block" :to="{ name: 'register' }">اشترك</RouterLink>
      </div>
    </div>

    <div class="card" style="margin-top:28px">
      <h3>ملاحظات الفوترة</h3>
      <p style="margin-top:8px">
        • الدقائق تُخصم من رصيد حساب المطور حسب الاستخدام الحي.<br />
        • الدفع عبر بوابة البطاقات Fourthwall (نفس بنية الدفع المستخدمة مع تطبيقات المنصة الشقيقة).<br />
        • الحساب الجديد يحصل على دقائق تجريبية مجانية محدودة.<br />
        • الأسعار بالدولار الأمريكي وقد تُعدَّل من لوحة أدمن المنصة.
      </p>
    </div>
  </div>
</template>

<script setup>
const packs = [
  {
    id: 1,
    name: 'Starter',
    mins: '5,000',
    price: '9',
    badge: '',
    hot: false,
    desc: 'للتجربة والإطلاق الأولي',
    features: ['5 آلاف دقيقة صوت', 'تطبيق واحد أو أكثر', 'دعم wss://'],
  },
  {
    id: 2,
    name: 'Growth',
    mins: '25,000',
    price: '39',
    badge: 'الأفضل قيمة',
    hot: true,
    desc: 'غرف واستوديوهات نشطة',
    features: ['25 ألف دقيقة', 'مناسب لعدة تطبيقات', 'أولوية ربط التوكن'],
  },
  {
    id: 3,
    name: 'Scale',
    mins: '100,000',
    price: '99',
    badge: '',
    hot: false,
    desc: 'منتجات بث إنتاجية',
    features: ['100 ألف دقيقة', 'حجم أعلى', 'تكلفة أقل لكل 1000 دقيقة'],
  },
  {
    id: 4,
    name: 'Enterprise',
    mins: '500,000',
    price: '399',
    badge: 'حجم عالٍ',
    hot: false,
    desc: 'فرق ومنصات متعددة',
    features: ['نصف مليون دقيقة', 'استهلاك كثيف', 'تواصل للترتيبات الخاصة'],
  },
]
</script>
