<template>
  <div class="site" dir="rtl" lang="ar">
    <header class="site-nav">
      <div class="site-nav-inner">
        <RouterLink class="site-logo" :to="{ name: 'landing' }">
          <span class="brand-mark">LV</span>
          <span class="site-logo-text">
            <strong>LYVO Cloud</strong>
            <small>منصة الصوت المباشر</small>
          </span>
        </RouterLink>

        <nav class="site-links">
          <RouterLink :to="{ name: 'landing' }">الرئيسية</RouterLink>
          <RouterLink :to="{ name: 'how-to' }">طريقة الاستخدام</RouterLink>
          <RouterLink :to="{ name: 'pricing' }">الباقات</RouterLink>
          <RouterLink :to="{ name: 'about' }">عن المنصة</RouterLink>
            <RouterLink :to="{ name: 'privacy' }">الخصوصية</RouterLink>
            <RouterLink :to="{ name: 'terms' }">الشروط</RouterLink>
          </nav>

        <div class="site-actions">
          <RouterLink v-if="!token" class="btn btn-ghost btn-sm" :to="{ name: 'login' }">دخول</RouterLink>
          <RouterLink v-if="!token" class="btn btn-primary btn-sm" :to="{ name: 'register' }">إنشاء حساب</RouterLink>
          <RouterLink v-else class="btn btn-primary btn-sm" :to="{ name: 'home' }">لوحة التحكم</RouterLink>
        </div>
      </div>
    </header>

    <main class="site-main">
      <RouterView />
    </main>

    <footer class="site-footer">
      <div class="site-footer-inner">
        <div class="foot-brand">
          <span class="brand-mark">LV</span>
          <div>
            <strong>LYVO Technologies</strong>
            <p>بنية تحتية للصوت المباشر — تطبيقات البث، الغرف الصوتية، والمكالمات.</p>
          </div>
        </div>
        <div class="foot-cols">
          <div>
            <h4>المنتج</h4>
            <RouterLink :to="{ name: 'how-to' }">كيف تبدأ</RouterLink>
            <RouterLink :to="{ name: 'pricing' }">الأسعار</RouterLink>
            <RouterLink :to="{ name: 'login' }">لوحة المطور</RouterLink>
          </div>
          <div>
            <h4>قانوني</h4>
            <RouterLink :to="{ name: 'privacy' }">سياسة الخصوصية</RouterLink>
            <RouterLink :to="{ name: 'terms' }">شروط الاستخدام</RouterLink>
          </div>
          <div>
            <h4>اتصال</h4>
            <a href="mailto:support@lyvo.cloud">support@lyvo.cloud</a>
            <span class="foot-muted">wss://voice.adastra.bbs.tr</span>
          </div>
        </div>
      </div>
      <div class="site-copy">
        © {{ year }} LYVO Cloud · جميع الحقوق محفوظة
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getToken } from '../api'

const year = new Date().getFullYear()
const token = computed(() => getToken())
</script>
