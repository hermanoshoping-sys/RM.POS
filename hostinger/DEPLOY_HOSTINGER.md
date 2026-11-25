# دليل استضافة ونشر نظام RM.POS على Hostinger

تطبيق **RM.POS** هو تطبيق أندرويد (`.apk`) يعمل على الهواتف، أما ما تستضيفه على **Hostinger** فهو:
1. **خادم التحقق من التراخيص وتجديدها وإلغائها الموقّع رقمياً (`https://your-domain.com/api/verify-license.php`)** — المتوافق 100% مع `LicenseOnlineClient.kt` و`LICENSE_SERVER_URL`.
2. **لوحة إدارة تراخيص العملاء (`https://your-domain.com/admin/`)** — لتوليد رموز التفعيل و`CLOCK_RESET` و`RECOVERY` وإيقاف الأجهزة عن بُعد.
3. **صفحة التحميل الرسمية لنسخة Android APK (`https://your-domain.com/`)** مع ملف `downloads/version.json` وبصمة `SHA-256`.

---

## المتطلبات على Hostinger (hPanel)
- خطة استضافة تدعم **PHP 8.1 أو أحدث** (مع إضافتي `openssl` و`pdo_mysql` — مفعلتان افتراضياً في Hostinger).
- **نطاق مربوط بشهادة SSL (HTTPS)** مفعّلة من `hPanel -> Websites -> SSL` (تطبيق أندرويد يرفض أي رابط غير `https://`).
- **قاعدة بيانات MySQL** واحدة من `hPanel -> Databases -> Management`.

---

## خطوات النشر خطوة بخطوة

### الخطوة 1: إنشاء قاعدة البيانات واستيراد المخطط
1. في **Hostinger hPanel**، افتح **Databases -> Management** وأنشئ قاعدة بيانات جديدة ومستخدماً بكلمة مرور قوية.
2. افتح **phpMyAdmin** لقاعدة البيانات الجديدة -> تبويب **Import** -> اختر الملف:
   `hostinger/database/schema.sql` ثم اضغط **Import**.

### الخطوة 2: تجهيز المجلد الخاص خارج `public_html` (مهم جداً للأمان)
1. افتح **File Manager** في hPanel وانتقل إلى مجلد نطاقك (أعلى من `public_html` بدرجة واحدة):
   `/home/<u_account>/domains/<your-domain.com>/`
2. أنشئ مجلداً جديداً باسم `rmpos-private` (بجوار `public_html` وليس بداخله!).
3. ارفع إلى داخل `rmpos-private/`:
   - ملف المفتاح الخاص الخارجي `license_private_key_k2026_v2.pem` واضبط صلاحياته إلى **`0600`**.
   - ملف المفتاح العام `license_public_key.pem` (الموجود في جذر المشروع).
   - انسخ `hostinger/private_config_template/config.example.php` وأعد تسميته إلى `config.php` داخل `rmpos-private/`.
4. ولّد هاش كلمة مرور قوية للوحة الإدارة (عبر SSH أو محلياً):
   ```bash
   php -r 'echo password_hash("YourStrongAdminPass#2026", PASSWORD_DEFAULT) . PHP_EOL;'
   ```
5. عدّل `rmpos-private/config.php` وأدخل:
   - بيانات MySQL (`db_host`, `db_name`, `db_user`, `db_pass`)
   - `admin_username` و`admin_password_hash` الناتج من الأمر أعلاه.

### الخطوة 3: رفع ملفات `public_html`
ارفع محتويات `hostinger/public_html/` بالكامل إلى مجلد `public_html/` في Hostinger بحيث يصبح الهيكل:
```text
domains/your-domain.com/
├── rmpos-private/                       # خارج الويب (غير متاح للعموم)
│   ├── config.php                       # chmod 600
│   ├── license_private_key_k2026_v2.pem # chmod 600
│   └── license_public_key.pem
└── public_html/
    ├── .htaccess
    ├── index.html
    ├── admin/
    │   └── index.php
    ├── api/
    │   ├── bootstrap.php
    │   ├── crypto.php
    │   └── verify-license.php
    └── downloads/
        ├── .htaccess
        ├── version.json
        └── RM-POS-v1.3.3-release.apk    # (ترفعها بعد الخطوة 4)
```

### الخطوة 4: بناء نسخة Android `release` مربوطة بنطاقك على Hostinger
على جهازك المحلي، اضبط رابط خادم التحقق الفعلي على Hostinger ومفتاح التوقيع الخاص بك ثم ابنِ نسخة `release`:
```bash
export LICENSE_SERVER_URL="https://your-domain.com/api/verify-license.php"
export KEYSTORE_PATH="$HOME/rmpos-key-vault/my-upload-key.jks"
export STORE_PASSWORD="<your-store-password>"
export KEY_PASSWORD="<your-key-password>"
./gradlew :app:assembleRelease
```
احسب بصمة `SHA-256` للنسخة الناتجة:
```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```
ثم:
1. أعد تسمية الملف إلى `RM-POS-v1.3.3-release.apk` وارفعه إلى `public_html/downloads/`.
2. حدّث قيمة `sha256` و`apkFile` داخل `public_html/downloads/version.json`.

### الخطوة 5: اختبار سريع بعد الرفع
1. افتح `https://your-domain.com/` وتأكد من ظهور صفحة التحميل وعمل زر تحميل الـ APK.
2. افتح `https://your-domain.com/admin/` وسجّل الدخول وأصدر ترخيصاً تجريبياً لجهازك.
3. تأكد أن `https://your-domain.com/api/bootstrap.php` و`https://your-domain.com/api/crypto.php` يرجعان **403 Forbidden** (محميان بـ `.htaccess`).
4. اختبر زر «التحقق عبر الإنترنت» داخل تطبيق RM.POS وتأكد من تجديد المهلة وتحديث وقت الخادم.
