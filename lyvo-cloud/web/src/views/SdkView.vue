<template>
  <div>
    <div class="page-head">
      <h2>SDK &amp; integration</h2>
      <p>
        دليل تفعيل البث المباشر: الربط، المفاتيح، التوكن، الباقات، ورقم AppID.
        ابدأ دائماً بعنوان <strong>wss://</strong>.
      </p>
    </div>

    <div class="wss-banner">
      <div>
        <h3>LiveKit signaling (required first)</h3>
        <p>ضع هذا في إعدادات التطبيق قبل أي مفتاح آخر</p>
      </div>
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <div class="wss-value">{{ livekitUrl }}</div>
        <button class="btn btn-ghost btn-sm" type="button" style="background:rgba(255,255,255,0.08);color:#fff;border:0" @click="copy(livekitUrl)">Copy wss://</button>
      </div>
    </div>

    <div class="two-col">
      <div class="card">
        <h3>تفعيل خطوة بخطوة</h3>
        <div class="steps">
          <div class="step">
            <div class="step-num">1</div>
            <div>
              <h4>سجّل كمطور</h4>
              <p>لوحة المطور فقط — لا إعدادات دفع Fourthwall. إنشاء تطبيقات + شراء دقائق.</p>
            </div>
          </div>
          <div class="step">
            <div class="step-num">2</div>
            <div>
              <h4>أنشئ Live app</h4>
              <p>من Apps → New app. تحصل على AppID، API Key، API Secret، و room prefix.</p>
            </div>
          </div>
          <div class="step">
            <div class="step-num">3</div>
            <div>
              <h4>اربط wss:// في الكلاينت</h4>
              <p>LiveKit SDK: <code>Room.connect(wssUrl, token)</code>. الإنتاج = <code>wss://</code> فقط.</p>
            </div>
          </div>
          <div class="step">
            <div class="step-num">4</div>
            <div>
              <h4>اصدر Token</h4>
              <p>من سيرفرك أو مباشرة من LYVO: <code>POST /api/v1/token</code> بمفاتيح التطبيق.</p>
            </div>
          </div>
          <div class="step">
            <div class="step-num">5</div>
            <div>
              <h4>فعّل الدقائق (باقات)</h4>
              <p>Buy minutes → ادفع بالبطاقة (Fourthwall). بدون رصيد دقائق لن يُصدر التوكن (402).</p>
            </div>
          </div>
          <div class="step">
            <div class="step-num">6</div>
            <div>
              <h4>انشر الميكروفون</h4>
              <p>setMicrophoneEnabled(true) / publish track — ثم البث يعمل بين المشاركين.</p>
            </div>
          </div>
        </div>
      </div>

      <div>
        <div class="card" style="margin-bottom:16px">
          <h3>الأرقام / الحقول</h3>
          <div class="kv"><b>wss URL</b><span>{{ livekitUrl }}</span></div>
          <div class="kv"><b>Token API</b><span>{{ origin }}/api/v1/token</span></div>
          <div class="kv"><b>AppID</b><span>رقمي من صفحة التطبيق</span></div>
          <div class="kv"><b>API Key</b><span>يبدأ بـ LVK…</span></div>
          <div class="kv" style="border:0"><b>API Secret</b><span>يبدأ بـ LVS… — سري</span></div>
        </div>
        <div class="card">
          <h3>مين يشوف إيش؟</h3>
          <p style="margin-bottom:10px"><strong>المطور (تسجيل عادي):</strong> Apps, SDK, Buy minutes, Balance.</p>
          <p><strong>أدمن المنصة (المالك فقط):</strong> باقات البيع + Fourthwall + الإيراد — قائمة Admin. لا تظهر لمن يسجّل حساب عادي.</p>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top:18px">
      <h3>HTTP — token (server)</h3>
      <div class="code-box">POST {{ origin }}/api/v1/token
Content-Type: application/json

{
  "appId": "12xxxxxxxx",
  "apiKey": "LVK…",
  "apiSecret": "LVS…",
  "room": "party-1",
  "identity": "user-uuid"
}

// Response
{
  "provider": "livekit",
  "livekitUrl": "{{ livekitUrl }}",
  "room": "lyvo_xxx_party-1",
  "token": "eyJ…",
  "identity": "user-uuid"
}</div>
    </div>

    <div class="card" style="margin-top:16px">
      <h3>Android (LiveKit) — minimal</h3>
      <div class="code-box">// dependencies: io.livekit:livekit-android
val url = "{{ livekitUrl }}" // FIRST
val token = fetchFromYourBackend() // or LYVO /api/v1/token

val room = LiveKit.create(appContext)
room.connect(url, token)
room.localParticipant.setMicrophoneEnabled(true)</div>
      <p style="margin-top:10px">لا تضع API Secret في APK إن أمكن — مرّر فقط JWT من سيرفرك.</p>
    </div>

    <div class="card" style="margin-top:16px">
      <h3>باقات الدقائق والربط</h3>
      <p>
        كل حساب له رصيد دقائق (تجريبي + مشترى). عند انتهاء الرصيد <code>/api/v1/token</code> يرجع
        <strong>402 no_minutes_left</strong>. اشترِ باقة من Packages (بطاقة Fourthwall).
      </p>
      <button class="btn btn-primary" type="button" style="margin-top:12px" @click="$router.push({ name: 'packages' })">
        Open packages
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'

const dash = inject('dash')
const livekitUrl = computed(() => dash?.value?.resources?.livekitUrl || 'wss://voice.adastra.bbs.tr')
const origin = computed(() => (typeof location !== 'undefined' ? location.origin : 'https://cloud.adastra.bbs.tr'))

function copy(t) {
  navigator.clipboard?.writeText(t).catch(() => {})
}
</script>
