# Database · قاعدة البيانات

الملف الحقيقي = **بيانات سيرفر الإنتاج**. الباكند يعرض ما في القاعدة فقط،
ولا يعيد كتابة الأسعار/الكتالوج عند الإقلاع (إلا إذا `BOOT_SEED_CATALOGS=1`).

| الملف | الوصف |
|-------|--------|
| `schema.sql` | المخطط فقط (أحدث من السيرفر) |
| `auralive_prod_YYYYMMDD.sql` | نسخة كاملة (schema + data) من الإنتاج |
| `auralive_latest.sql` | آخر dump كامل (alias) |

## تحديث النسخة من السيرفر

```bash
python scripts/pull_prod_database.py
python scripts/pull_prod_backend.py   # كود + public من السيرفر
```

## استعادة محلية

```bash
createdb auralive
psql -U auralive -d auralive -f database/auralive_latest.sql
```

> تحذير: الملف الكامل يحتوي بيانات تشغيل حقيقية — المستودع خاص (Private). لا تنشره علناً.
