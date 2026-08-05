<template>
  <div class="site-wrap legal-page">
    <header class="sec-head" style="text-align:start;max-width:none;margin-bottom:28px">
      <h2>طريقة الاستخدام</h2>
      <p>دليل عملي من التسجيل حتى البث المباشر.</p>
    </header>

    <div class="wss-banner" style="margin-bottom:28px">
      <div>
        <h3>العنوان الأساسي (استخدمه أولاً)</h3>
        <p>إشارة WebSocket للإنتاج</p>
      </div>
      <div class="wss-value" dir="ltr">wss://voice.adastra.bbs.tr</div>
    </div>

    <ol class="howto-list">
      <li>
        <h3>إنشاء حساب مطور</h3>
        <p>
          من <RouterLink :to="{ name: 'register' }">إنشاء حساب</RouterLink> سجّل بريدك وكلمة مرور.
          تحصل على دقائق تجريبية. هذا حساب <strong>مطور</strong> — لا يرى إعدادات دفع المنصة.
        </p>
      </li>
      <li>
        <h3>دخول لوحة التحكم</h3>
        <p>
          بعد الدخول تُفتح <strong>Developer Console</strong>: نظرة عامة، التطبيقات، SDK، شراء الدقائق.
        </p>
      </li>
      <li>
        <h3>إنشاء Live App</h3>
        <p>
          من «Live apps» اضغط Create. انسخ: <strong>AppID</strong>، <strong>API Key</strong>،
          <strong>API Secret</strong>، وroom prefix.
        </p>
      </li>
      <li>
        <h3>ربط wss:// في التطبيق</h3>
        <p>
          في كود Android / Web / iOS ضع عنوان الإشارة:
          <code dir="ltr">wss://voice.adastra.bbs.tr</code>
          — هذا أول إعداد، قبل المفاتيح.
        </p>
      </li>
      <li>
        <h3>إصدار Token</h3>
        <p>
          من سيرفرك (يفضّل) أو تجريبياً من LYVO:
        </p>
        <pre class="code-box" dir="ltr">POST https://cloud.adastra.bbs.tr/api/v1/token
{
  "appId": "…",
  "apiKey": "LVK…",
  "apiSecret": "LVS…",
  "room": "lobby",
  "identity": "user-1"
}</pre>
        <p>الرد يتضمن <code>token</code> و <code>livekitUrl</code>.</p>
      </li>
      <li>
        <h3>الاتصال ونشر الصوت</h3>
        <p>
          <code dir="ltr">room.connect(wssUrl, token)</code> ثم تفعيل الميكروفون.
          راجع صفحة SDK داخل اللوحة بعد الدخول.
        </p>
      </li>
      <li>
        <h3>شراء دقائق</h3>
        <p>
          من Buy minutes اختر باقة وادفع بالبطاقة (Fourthwall). عند نفاد الرصيد يعيد الـ API
          رمز <code>402</code> حتى تشتري باقة.
        </p>
      </li>
    </ol>

    <div class="center-cta" style="margin-top:32px">
      <RouterLink class="btn btn-primary" :to="{ name: 'register' }">ابدأ الآن</RouterLink>
      <RouterLink class="btn btn-ghost" :to="{ name: 'pricing' }">عرض الباقات</RouterLink>
    </div>
  </div>
</template>
