# تثبيت JEHO CHAT على السيرفر · Server installation

دليل عملي لنشر **الـ API + لوحة الإدارة** على سيرفر الإنتاج.  
التطبيق الأندرويد يتصل بنفس الـ API بعد ضبط عنوان السيرفر.

---

## 1) ماذا يُنشر؟

| المكوّن | المسار على السيرفر (مثال) | التشغيل |
|--------|---------------------------|---------|
| NestJS API | `/www/wwwroot/api.adnova.bbs.tr` | PM2: `auralive-api` |
| لوحة الإدارة | `…/admin/` (مخرجات Vite) | ملفات ثابتة عبر Nginx |
| أصول عامة | `…/public/` | صور · visual-system · privacy |
| PostgreSQL | محلي أو مُدار | قاعدة `auralive` (أو اسمك) |
| Redis | محلي | كاش · جلسات · realtime |

> أسماء المضيف والمسارات قابلة للتغيير عبر `scripts/deploy.local.env`.

---

## 2) متطلبات السيرفر

- Linux (موصى به: Ubuntu 22.04 / مشابه)
- **Node.js 20+** و npm
- **PostgreSQL 14+**
- **Redis 6+**
- **Nginx** (أو أي reverse proxy)
- **PM2** عالمياً: `npm i -g pm2`
- أدوات بناء: `build-essential` عند الحاجة لـ native modules
- منفذ مفتوح: `80` / `443` (والـ API خلف البروكسي)

على جهاز التطوير (ويندوز):

- Python 3 + `paramiko` (`pip install paramiko`)
- ملف أسرار محلي: `scripts/deploy.local.env` (gitignored)

---

## 3) إعداد قاعدة البيانات

```bash
sudo -u postgres createuser auralive -P
sudo -u postgres createdb -O auralive auralive
```

حمّل المخطط الابتدائي عند أول تنصيب:

```bash
psql -U auralive -d auralive -f database/schema.sql
```

لاحقاً تُنفَّذ الهجرات عبر سكربتات `backend/scripts/migrate-*.js` عند الحاجة (أو يرفعها `deploy_incremental.py` إذا مرّرت ملف الهجرة صراحة).

---

## 4) رفع كود الـ Backend أول مرة

من جهازك أو على السيرفر مباشرة:

```bash
# على السيرفر
mkdir -p /www/wwwroot/api.adnova.bbs.tr
cd /www/wwwroot/api.adnova.bbs.tr

# انسخ محتويات مجلد backend/ هنا (بدون node_modules إن أمكن)
# ثم:
cp .env.example .env
nano .env
```

### أهم متغيرات `.env` (إنتاج)

```env
NODE_ENV=production
PORT=3000
API_PREFIX=api/v1

DB_HOST=127.0.0.1
DB_PORT=5432
DB_USERNAME=auralive
DB_PASSWORD=********
DB_DATABASE=auralive
DB_SYNCHRONIZE=false

REDIS_HOST=127.0.0.1
REDIS_PORT=6379

# أسرار قوية ومختلفة (≥ 16–32 حرفاً)
JWT_SECRET=********
JWT_REFRESH_SECRET=********-different-from-jwt

CORS_ORIGINS=https://api.adnova.bbs.tr,https://your-admin-origin

# ZEGOCLOUD من لوحة ZEGO
ZEGO_APP_ID=...
ZEGO_APP_SIGN=...
ZEGO_SERVER_SECRET=...

ADMIN_EMAIL=admin@yourdomain.com
ADMIN_PASSWORD=********
ADMIN_USERNAME=admin
```

ثم:

```bash
npm install --omit=dev
npm run build
# أول تشغيل / seed إن لزم:
# npm run seed
pm2 start dist/main.js --name auralive-api --update-env
pm2 save
pm2 startup
```

تحقق:

```bash
pm2 status
curl -s https://api.adnova.bbs.tr/api/v1/health || curl -s http://127.0.0.1:3000/api/v1/health
```

---

## 5) بناء ورفع لوحة الإدارة (`/admin`)

على جهاز التطوير:

```bash
cd backend/dashboard
npm install
npm run build
```

ارفع محتويات `backend/dashboard/dist/` إلى:

```text
/www/wwwroot/api.adnova.bbs.tr/admin/
```

أو استخدم النشر التدريجي (موصى به):

```bash
python scripts/deploy_incremental.py backend/dashboard/src/views/DashboardView.vue
```

السكربت يبني الداشبورد محلياً ويرفع فقط الملفات المتغيّرة تحت `/admin`.

افتح: `https://api.adnova.bbs.tr/admin/`

---

## 6) إعداد Nginx (ملخص)

وجّه النطاق إلى المجلد، ومرّر `/api` و Socket.io إلى Node:

```nginx
server {
  listen 443 ssl http2;
  server_name api.adnova.bbs.tr;

  # ssl_certificate …;
  # ssl_certificate_key …;

  root /www/wwwroot/api.adnova.bbs.tr;
  index index.html;

  location /admin/ {
    try_files $uri $uri/ /admin/index.html;
  }

  location /api/ {
    proxy_pass http://127.0.0.1:3000;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  location /realtime {
    proxy_pass http://127.0.0.1:3000;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
  }

  location / {
    try_files $uri $uri/ =404;
  }
}
```

أعد تحميل Nginx بعد التعديل: `nginx -t && systemctl reload nginx`

---

## 7) النشر التدريجي اليومي (من ويندوز)

### أ) ملف الأسرار (مرة واحدة)

```bash
cd scripts
copy deploy.local.env.example deploy.local.env
# عبّئ:
# JEHO_DEPLOY_HOST=...
# JEHO_DEPLOY_USER=root
# JEHO_DEPLOY_PASSWORD=...
# JEHO_DEPLOY_DIR=/www/wwwroot/api.adnova.bbs.tr
```

`deploy.local.env` **لا يُرفع على Git**.

### ب) أوامر شائعة

```bash
# ملف Backend واحد → رفع + nest build على السيرفر + pm2 restart
python scripts/deploy_incremental.py backend/src/modules/admin/admin.service.ts

# تعديل داشبورد → build محلي + مزامنة /admin
python scripts/deploy_incremental.py backend/dashboard/src/views/PolicyBrochureView.vue

# أصول بصرية (عند الحاجة)
python scripts/deploy_incremental.py --visual-system

# تثبيت APK على جهاز متصل (إن وُجد مسار الـ APK)
python scripts/deploy_incremental.py --apk
```

السكربت يقوم تلقائياً بـ:

1. رفع الملفات المتغيّرة فقط  
2. `npm run build` على السيرفر عند تغيّر مصادر Nest  
3. `pm2 restart auralive-api --update-env`  
4. مزامنة ملفات `/admin` عند طلب الداشبورد  

---

## 8) ربط تطبيق الأندرويد

في إعدادات الشبكة / BuildConfig للتطبيق، ضع Base URL للإنتاج:

```text
https://api.adnova.bbs.tr/api/v1
```

وتأكد أن مفاتيح ZEGO في التطبيق تطابق حساب السيرفر.

بناء نسخة إصدار:

```bash
cd android
./gradlew.bat assembleRelease
# أو bundleRelease للمتجر
```

الإصدار الحالي في المشروع: **2.0.43** · versionCode **35**.

---

## 9) فحص ما بعد التثبيت (Checklist)

- [ ] `pm2 status` → `auralive-api` = **online**
- [ ] فتح Swagger أو health للـ API
- [ ] تسجيل دخول الأدمن على `/admin/`
- [ ] حفظ إعدادات الاقتصاد / التارجت من الداشبورد
- [ ] تنزيل **سياسة المنصة PDF** يعمل ويصل لمجلد التنزيلات
- [ ] غرفة صوتية تفتح صوت ZEGO بدون خطأ توكن
- [ ] الشحن / الوكلاء يظهرون حسب الإعدادات
- [ ] HTTPS صالح و CORS يضم نطاق الأدمن

---

## 10) أعطال شائعة

| العرض | السبب المحتمل | الحل |
|-------|----------------|------|
| أدمن يفتح فارغ / 404 | `admin` غير مبني أو مسار Nginx خاطئ | أعد `npm run build` للداشبورد وارفع `dist` |
| API 502 | PM2 متوقف أو بورت خاطئ | `pm2 logs auralive-api` · تحقق `PORT` |
| فشل JWT / دخول | أسرار قصيرة أو متطابقة | افصل `JWT_SECRET` عن `JWT_REFRESH_SECRET` وطوّلهما |
| غرف بلا صوت | ZEGO ناقص في `.env` | عبّئ `ZEGO_APP_ID` / `APP_SIGN` / `SERVER_SECRET` |
| نشر يفشل من ويندوز | لا يوجد `deploy.local.env` | انسخ المثال وعبّئ البيانات |
| PDF لا ينزل | كاش قديم للداشبورد | `Ctrl+F5` على `/admin` وأعد التنزيل |

---

## 11) روابط سريعة

| الخدمة | الرابط |
|--------|--------|
| API | `https://api.adnova.bbs.tr/api/v1` |
| Admin | `https://api.adnova.bbs.tr/admin/` |
| Privacy | `https://api.adnova.bbs.tr/privacy.html` |
| README الرئيسي | [../README.md](../README.md) |
| قالب النشر | [../scripts/deploy.local.env.example](../scripts/deploy.local.env.example) |

---

**JEHO CHAT** — بعد إكمال الخطوات أعلاه يكون السيرفر جاهزاً والبرنامج مضبوطاً للتشغيل الإنتاجي.
