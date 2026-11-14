# دليل تركيب مؤثرات البث

مصدر الحقيقة لتركيب الحزم داخل التطبيق.

| الحزمة | المجلد | API |
|--------|--------|-----|
| إطارات المضيف (بروفايل) | `host-frames/` | `HostFrames.render()` |
| توستات الانضمام (افتراضي JS) | `join-toasts/` | `JoinToasts.show()` |
| الهدايا | `gifts/` | `Gifts.show()` |

**تم إلغاء:** كروت قائمة الرومات المصوّرة (`room-frames`) ومؤثرات الدخول بالصور (`entry-effects`).  
قائمة الرومات تستخدم خلفية افتراضية في التطبيق. الانضمام توست برمجي (CSS/JS أو native) حسب VIP.

## قواعد

1. لا تلمس `host-frames` / البروفايل من مسارات كروت الروم.
2. توست الانضمام بدون PNG — ألوان حسب `variant` (`normal|vip|gold|diamond|legend|supporter`).
3. الأسماء عبر `textContent` فقط.

```js
JoinToasts.show({
  variant: "vip",
  username: payload.username,
  message: "انضم إلى الغرفة",
  avatar: payload.avatarUrl,
  duration: 4200
});
```
