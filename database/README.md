# Database · قاعدة البيانات

ملفات مأخوذة من سيرفر الإنتاج (`api.adnova.bbs.tr`).

| الملف | الوصف |
|-------|--------|
| `schema.sql` | المخطط فقط (أحدث من السيرفر) |
| `auralive_prod_YYYYMMDD.sql` | نسخة كاملة (schema + data) من الإنتاج |

## تحديث النسخة من السيرفر

```bash
python scripts/pull_prod_database.py
```

## استعادة محلية

```bash
createdb auralive
psql -U auralive -d auralive -f database/auralive_prod_20260803.sql
```

> تحذير: الملف الكامل يحتوي بيانات تشغيل حقيقية — المستودع خاص (Private). لا تنشره علناً.
