# نظام نقطة البيع RM.POS (Supermarket & Retail POS)

تطبيق أندرويد متكامل لإدارة نقاط البيع (POS)، المخزون، الفواتير، الديون، المشتريات، المصاريف، والتقارير المالية مع طباعة حرارية عبر Bluetooth / Wi‑Fi ومعاينة PDF، مبني بلغة **Kotlin** وواجهات **Jetpack Compose (Material 3)** وفق معمارية **MVVM + Domain Layer** وقاعدة بيانات **Room / SQLCipher (AES-256)**.

- **اسم التطبيق (`app_name`)**: `RM.POS`
- **الإصدار الحالي (`versionName` / `versionCode`)**: `1.3.3` (`7`) — وحدة الجرد وسلة الجرد التفاعلية (مخطط `v23` مع `22.json` و`23.json`)، تثبيت حارس الساعة على الوقت الموثوق فقط مع مخرج وقت الخادم ورمز `CLOCK_RESET` الموقّع، توحيد مصدر حساب الإرجاع الجزئي في المستودع، فرض `LICENSE_SERVER_URL` في بناء `release`، واعتماد Gradle Wrapper (`9.3.1`)
- **قرار معرّف الحزمة النهائي (`applicationId`)**: تم اعتماد وتثبيت `com.aistudio.rmpos.x7m4qp` كمعرّف نهائي دائم للتوزيع (مع الإبقاء على `namespace = "com.example"`، ومُثبَّت آلياً في اختبارات `SqlCipherUpgradeAndAlignmentTest`)
- **إصدار قاعدة البيانات (`Room schemaVersion`)**: `23` (المخططان `22.json` و`23.json` مودَعان في `app/schemas/com.example.data.AppDatabase/`)

---

## 🏗️ بنية المشروع (Project Structure)

> **نطاق قاعدة الـ 500 سطر**: تم تطبيق قاعدة الحفاظ على حجم الملفات دون **500 سطر** على كامل طبقة الواجهات (`ui/screens/`، `ui/components/`)، وحزمة الطباعة (`printer/`)، وحزمة الأمان (`security/`)، ومفوّضات الـ ViewModels (`ui/viewmodels/` باستثناء المنسّق الرئيسي `PosViewModel.kt`)، مع فصل هجرات قاعدة البيانات عن `AppDatabase.kt` إلى ثلاث وحدات (`AppDatabaseMigrationsLegacy.kt`، `AppDatabaseMigrationsFinancial.kt`، `AppDatabaseMigrationsRecent.kt`) وتقسيم `Repository.kt` إلى خمس وحدات عمليات متخصصة. تقتصر الملفات المتبقية فوق 500 سطر على ستة ملفات تجميعية موحّدة بطبيعتها: `Daos.kt`، `Models.kt`، `DatabaseBackupManager.kt`، `ProductExcelManager.kt`، `PosViewModel.kt`، و`GeoLocationHelper.kt`.

```text
├── app/
│   ├── schemas/com.example.data.AppDatabase/   # مخططات Room المُصدَّرة فعلياً: 8، 14، 15، 16، 17، 18، 20، 22، 23 (مخطط 21 غير متوفر في هذه اللقطة)
│   └── src/
│       ├── main/
│       │   ├── assets/leaflet/                 # أصول Leaflet 1.9.4 المحلية (leaflet.js و leaflet.css) لبلاطات المزوّد التجاري المخصّص
│       │   ├── assets/maplibre/                # أصول MapLibre GL JS 4.7.1 المحلية (maplibre-gl.js و maplibre-gl.css) لخرائط OpenFreeMap المجانية
│       │   ├── res/
│       │   │   ├── values/strings.xml          # نصوص الواجهة والأخطاء الافتراضية (EN)
│       │   │   ├── values-ar/strings.xml       # الترجمة العربية الكاملة للواجهة والأخطاء
│       │   │   └── values-fr/strings.xml       # الترجمة الفرنسية الكاملة للواجهة والأخطاء
│       │   └── java/com/example/
│       │       ├── MainActivity.kt             # نقطة الانطلاق، إعادة التحقق من الترخيص عند onResume، والتبديل الآمن لشاشة الاسترداد عند فشل القاعدة
│       │       ├── PosApp.kt                   # تهيئة التطبيق، SQLCipher، وموارد الترجمة
│       │       ├── di/                         # AppContainer (مع DatabaseInitResult وإعادة المحاولة) و PosViewModelFactory
│       │       ├── domain/                     # الحاسبة المالية وحاسبة المرتجعات المخصومة ونتائج العمليات (FinancialCalculator / ReturnRefundCalculator / OperationResult)
│       │       ├── data/                       # AppDatabase v23 + وحدات الهجرات (Legacy/Financial/Recent) + Daos + Models + Repository + ExternalSafBackupManager + النسخ المشفّرة
│       │       ├── printer/                    # حزمة الطباعة الحرارية (PosThermalPrinterService، Bluetooth / USB / Wi-Fi / Android Print، وقائمة الطباعة المجمّعة)
│       │       ├── security/                   # التشفير (AES-256-GCM / PBKDF2)، PasswordPolicy، PasswordHasher (قفل رتيب)، LicenseClockGuard، SecurityRuntimeConfig، SQLCipher، وECDSA P-256
│       │       ├── util/                       # MoneyUtils (بالسنتات + عملة المتجر)، Excel، الموقع الجغرافي، الباركود
│       │       └── ui/
│       │           ├── components/             # SecureLicenseManagerCard، SafeComposeTextToolbar وعناصر واجهة مشتركة
│       │           ├── viewmodels/             # PosViewModel، ViewModels الفرعية، ومفوّضات الترخيص/الأجهزة/الحسابات
│       │           ├── theme/                  # RmPosTheme و Bilingual المربوط بـ strings.xml (AR/FR/EN + أرقام غربية 0-9)
│       │           └── screens/                # شاشات التطبيق (كل ملف دون 500 سطر) + DatabaseRecoveryScreen للاسترداد الآمن بدون انهيار
│       └── test/java/com/example/              # اختبارات الوحدة و Robolectric و Compose UI (مع TestPosApp المعزول عن كود الإنتاج)
├── .github/workflows/android-ci.yml            # خط CI عبر ./gradlew لتشغيل فحص الأسرار، assembleDebug، الاختبارات، Lint، التحقق من LICENSE_SERVER_URL وassembleRelease (غير موّزع)، وDetekt
├── .gitleaks.toml                              # قواعد Gitleaks لحظر تسريب المفاتيح الخاصة أو ملفات التوقيع (.jks / .pem)
├── license_public_key.pem                      # المفتاح العام ECDSA P-256 المعتمد والمطابق للمفتاح المضمّن (kid = k2026-v2)
├── gradlew & gradlew.bat                       # سكربتا Gradle Wrapper الملتزَم بهما في المستودع (Gradle 9.3.1 المتوافق مع AGP 9.1.1)
├── gradle/
│   ├── libs.versions.toml                      # فهرس اعتماديات Gradle الموحّد
│   └── wrapper/                                # ملفات Gradle Wrapper (gradle-wrapper.jar و gradle-wrapper.properties)
├── tools/
│   ├── license_panel.html                      # واجهة ويب محلية مستقلة (خالية من المفاتيح الخاصة؛ تحمّل ملف .pem من خارج المشروع)
│   ├── license_generator.py                    # أداة CLI لتوليد مفاتيح ECDSA P-256 خارج المشروع وتوقيع الرموز
│   ├── check_secrets_precommit.py              # فاحص آلي (Gitleaks + Regex) يرفض أي مفتاح خاص قبل الالتزام أو التصدير
│   ├── install_git_hooks.sh                    # تثبيت خطاف Git pre-commit لمنع تسرب المفاتيح الخاصة تلقائياً
│   └── purge_git_history.sh                    # أداة تنظيف تاريخ Git المنشور عبر git-filter-repo
├── build.gradle.kts & settings.gradle.kts      # إعدادات البناء الرئيسية (Gradle Kotlin DSL)
└── metadata.json                               # بيانات تعريف المشروع المطلوبة لمنصة Google AI Studio (يُتجاهل أثناء بناء Android)
```

> **ملاحظة حول `metadata.json` و`public/assets/aistudio/`**:
> - `metadata.json` ملف تعريفي خاص بمنصة Google AI Studio (لتسمية المشروع وقدرات المنصة) ويتجاهله نظام بناء Android بالكامل.
> - المجلد `public/assets/aistudio/` يحتوي فقط على لقطات مرجعية مرفوعة في المحادثة ولا يرتبط بأي كود أو مورد داخل `app/` (لا يُضمَّن في ملف APK أو AAB). يُنصح باستبعاده قبل التوزيع الخارجي أو إضافته إلى `.gitignore`.

---

## 🛡️ الضوابط الأمنية، تشفير القاعدة، وتدوير المفاتيح

1. **الترقية الذرية لـ SQLCipher (AES-256) وشاشة الاسترداد**:
   - لا يتم نقل ملف القاعدة الأصلي بعيداً قبل التبديل مطلقاً: يُصدَّر الملف المشفّر أولاً مع `PRAGMA wal_checkpoint(TRUNCATE)` ونقل `PRAGMA user_version`، ثم يُنسخ الملف الأصلي احتياطياً مع `fsync`، ثم تُحذف ملفات `-wal`/`-shm`/`-journal` القديمة قبل التبديل، ثم يُستبدل الملف المشفّر فوق الأصلي بعملية `renameTo` ذرية واحدة.
   - عند كل إقلاع (`recoverInterruptedMigrationIfNeeded`)، إذا غاب ملف القاعدة ووُجدت النسخة الاحتياطية تُستعاد فوراً قبل إنشاء أي قاعدة جديدة؛ وإذا وُجد ملف مؤقت مشفّر يُفحص قبل ترقيته أو حذفه.
   - في حال فشل التهيئة أو الترحيل، يُرجع `AppDatabase.initializeDatabase` حالة `DatabaseInitResult.Failed` وتعرض الواجهة `DatabaseRecoveryScreen` مع شرح السبب وزر إعادة المحاولة، كما يفحص كلٌّ من `StockMonitorWorker` و`BackupSyncViewModel` نتيجة `DatabaseInitResult` مباشرةً بدلاً من استدعاء `getDatabase()` المسبّب للانهيار. وتبقى ملفات البيانات الأصلية دون أي حذف أو تعديل.
   - **نسخ احتياطي كامل قبل فتح Room (`backupDatabaseBeforeOpenIfMigrationNeeded`) مع ملفات WAL**: قبل أن يفتح Room القاعدة أو يبدأ أي هجرة، يُفحص إصدار المخطط (`user_version` من ترويسة SQLite أو آخر إصدار مفتوح للقاعدة المشفّرة) وتُنسخ ملفات `.db` و`-wal` و`-shm` معاً بـ `fsync` داخل `migration_backups/` لضمان عدم ضياع أحدث معاملات وضع WAL؛ وتُقلِّم `pruneMigrationBackups` الملفات الرئيسية ومرافقاتها الجانبية معاً.
   - **النسخ التلقائي خارج الجهاز عبر SAF (`ExternalSafBackupManager`) والشفافية في المشاركة**: بالإضافة إلى النسخ المحلي في `filesDir/auto_backups`، يتيح التطبيق اختيار مجلد خارجي عبر Android Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE` دون أي أذونات تخزين واسعة — مثل Google Drive عبر مزوّد SAF أو بطاقة SD أو USB OTG) لتُكتب النسخ المشفّرة إليه تلقائياً مع تقليم قديمها، مع توضيح صريح في الواجهة بأن المشاركة اليدوية تستخدم نافذة مشاركة أندرويد (وليس Drive REST API مباشر). كما جرى تحديث `schemaVersion` في ميتاداتا النسخ الاحتياطي إلى **`23`** (`AppDatabase.CURRENT_SCHEMA_VERSION`).
   - **عزل وضع الاختبارات عن كود الإنتاج (`SecurityRuntimeConfig` + `TestPosApp`)**: أُزيلت دالة `isUnitTestEnvironment()` وفحص Robolectric من `app/src/main` بالكامل؛ وتُفعّل بيئة الاختبار حصرياً من `app/src/test/java/com/example/TestPosApp.kt` عبر `robolectric.properties`.
   - جميع مكتبات JNI بـ 64 بت (بما فيها `libsqlcipher.so` لـ `arm64-v8a` و`x86_64`) محاذاة على **16 KB** مع `useLegacyPackaging = false`.

2. **سياسة كلمات المرور الموحّدة (`PasswordPolicy`) وقفل المحاولات الرتيب (`PasswordHasher`)**:
   - الحد الأدنى الموحّد هو **8 أحرف أو أرقام فأكثر** (`PasswordPolicy.MIN_LENGTH = 8`) مع رفض التسلسلات والتكرارات وكلمات المرور الشائعة.
   - تُطبّق السياسة عند **الإنشاء والتغيير** فقط (إعداد المدير، قفل الدخول، تغيير كلمة المرور، الاسترداد الطارئ، إدارة المستخدمين، كلمات سر الحذف والتعديل، وتصدير النسخ الاحتياطية المشفّرة في `BackupSyncViewModel`)، دون تعطيل كلمات المرور القائمة أو استعادة النسخ القديمة.
   - يعتمد قفل محاولات الدخول الفاشلة في `PasswordHasher` على **`SystemClock.elapsedRealtime()` و`BOOT_COUNT`** بدلاً من ساعة الجهاز الجدارية (`currentTimeMillis`)، فلا يمكن تجاوز القفل بتقديم التاريخ أو بإعادة تشغيل الهاتف.

3. **نظام التفعيل الموقّع رقمياً (`ECDSA P-256 • k2026-v2`) وحارس الساعة الرتيب (`LicenseClockGuard`)**:
   - كود الإنتاج (`LicenseSecurityManager`) يقتصر على **التحقق فقط** بالمفتاح العام؛ جميع دوال التوقيع بالمفتاح الخاص محصورة في اختبارات الوحدة (`TestLicenseTokenSigner`).
   - **التحقق عند الإقلاع والاستئناف وبشكل دوري أثناء التشغيل**: يُعاد التحقق من انتهاء صلاحية الترخيص ومهلة العمل دون اتصال عند إنشاء `AuthSecurityViewModel`، وعند كل عودة للواجهة في `MainActivity.onResume()`، وكذلك عبر حلقة مراقبة دورية كل **5 دقائق** أثناء بقاء التطبيق مفتوحاً (`LicenseLifecycleDelegate.startPeriodicLicenseMonitor`) حتى لا يستمر جهاز نقطة البيع بالعمل بعد انتهاء الرخصة دون إعادة تشغيل.
   - **وحدة كشف التراجع الرتيب في `com.example.security.LicenseClockGuard`**:
     - يتحقّق `LicenseSecurityManager` من الحقل الموقّع `issued_at` (مع دعم `iat` كاحتياط) ويرفض استخدام الرمز إذا كان وقت الجهاز أقدم من تاريخ إصداره.
     - يدير `LicenseClockGuard` نقطة ارتكاز نفس الإقلاع (`BOOT_COUNT` + `SystemClock.elapsedRealtime()`) والحد الأدنى الرتيب (`license_monotonic_floor_time`) انطلاقاً من **الوقت الموثوق فقط** (`issued_at` الموقّع أو وقت الخادم الموثّق أو رمز `CLOCK_RESET` الموقّع) مضافاً إليه فرق العداد الرتيب الحقيقي، ولا يرفع المرساة أو الحد الأدنى من ساعة الجهاز الخام مطلقاً، مما يمنع قفل الترخيص بعد إقلاع أو قفزة أو إعادة تشغيل بساعة متقدمة ثم تصحيحها.
     - لا يُحدَّث سجل الوقت الموثوق (`last_observed_system_time`) من قراءات ساعة الجهاز الخام، ويتوفر مخرجان آمنان لخفض أي حد مرتفع مسبقاً: **التحقق عبر الإنترنت بوقت خادم موثوق** (عبر زر التحقق الظاهر أثناء تعليق الساعة) أو **رمز `CLOCK_RESET` موقّع رقمياً لمرة واحدة** (مربوط بـ `device_id` و`kid` ومضاد لإعادة الاستخدام).
     - **حالات التعليق غير التدميرية (`LicenseClockState.ROLLBACK_SUSPENDED` / `LOCAL_EXPIRY_SUSPENDED`) وتوقيع الإلغاء من الخادم**: عند رصد تراجع حقيقي في الساعة أو تجاوز الساعة المحلية لتاريخ الانتهاء، يُعلَّق الوصول مؤقتاً **دون حذف الرمز الموقّع (`license_number`)**؛ وفور تصحيح التاريخ والوقت يستأنف الترخيص تلقائياً. كما يُشترط لإلغاء الترخيص من الخادم (`valid: false`) وجود رمز إلغاء موقّع رقمياً (`revocation_token` بإجراء `REVOKE` ومربوط بـ `device_id` و`kid` موثوق)، مع حقن رابط الخادم عبر `LICENSE_SERVER_URL` في `BuildConfig` حتى لا يُمسح الترخيص المحلي أبداً بردّ غير موقّع أو نطاق غير مهيّأ.
   - **دقة حساب مرتجعات الزبائن بالسنتات ومصدر الحقيقة الوحيد (`ReturnRefundCalculator` + `RepositorySalesSettlementOperations` + `SaleReturnDialog`)**: المستودع هو المصدر الوحيد لمبلغ الاسترجاع (`refundAmount = null` يحسب الفرق التراكمي الدقيق بالسنتات داخل المعاملة)، كما يمرّر حوار `SaleReturnDialog` الكميات المرتجعة سابقاً للمعاينة ويعرض الكمية المتبقية القابلة للإرجاع لكل صنف بدقة حتى آخر سنت.
   - **تقوية قواعد ProGuard / R8 (`app/proguard-rules.pro`)**: تم إزالة قاعدة `-keep class com.example.security.** { *; }` بحيث يقوم R8 بتعتيم وإعادة تسمية كلاسات ودوال التحقق من الترخيص والتشفير في نسخ `release` لرفع كلفة الهندسة العكسية (لا يعتمد أي جزء من `com.example.security` على الانعكاس).
   - **ثبات هوية الجهاز ومفتاح التوقيع (حرج تشغيلياً)**:
     - يُحسب معرّف الجهاز وفق المعادلة `Device ID = SHA-256(ANDROID_ID + ":" + packageName)`.
     - بدءاً من **Android 8 (API 26) فما فوق**، يربط النظام قيمة `Settings.Secure.ANDROID_ID` تلقائياً بكلٍّ من **معرّف الحزمة (`applicationId`) وشهادة التوقيع (`signing key`)**. لذلك فإن تغيير `applicationId = "com.aistudio.rmpos.x7m4qp"` **أو** فقدان/تغيير مفتاح التوقيع بعد الإصدار سيغيّر `Device ID` لجميع العملاء ويُبطل تراخيصهم القائمة.
     - **التوصية التشغيلية الإلزامية**:
       1. الاحتفاظ بـ **نسختين احتياطيتين مستقلتين غير متصلتين بالإنترنت** لملف `my-upload-key.jks` وكلمات مروره (مثلاً: قرص USB مشفّر + خزنة ورقية/آمنة منفصلة) **خارج مجلد المشروع**.
       2. عند النشر على متجر **Google Play**، تفعيل **Google Play App Signing** حتى تحتفظ Google بمفتاح توقيع التطبيق الدائم وتسمح بإعادة تعيين مفتاح الرفع (`upload key`) في حال فقدانه دون تغيير هوية الأجهزة أو كسر تراخيص العملاء.
   - **قائمة التحقق قبل التوزيع على العملاء أو إرسال نسخة للتقييم**:
     1. تثبيت `applicationId = "com.aistudio.rmpos.x7m4qp"` وعدم تغييره بعد الإصدار.
     2. إنشاء مفتاح توقيع الإصدار (`.jks`) **خارج مجلد المشروع** وحفظ نسختين احتياطيتين مستقلتين آمنتين (وتفعيل Google Play App Signing عند النشر عبر المتجر).
     3. تعيين متغير البيئة الإلزامي `LICENSE_SERVER_URL` (عنوان خادم التراخيص) قبل بناء `release`، والتأكد من عدم توجيه `MAP_TILE_URL_TEMPLATE` إلى خوادم `tile.openstreetmap.org` العامة (تفشل مهمتا `verifyReleaseLicenseServerUrl` و`verifyReleaseMapTileProvider` تلقائياً عند المخالفة).
     4. التأكد من أن الخادم أو أداة إصدار الرموز تستخدم المفتاح الخاص المطابق لـ `k2026-v2`، وأن فحص الأسرار (`python3 tools/check_secrets_precommit.py --all`) ينجح بدون أي تحذيرات.
     5. تشغيل كامل سلسلة التحقق عبر Gradle Wrapper: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` والتأكد من نجاح جميع اختبارات هجرة Room (بما فيها `22.json → 23.json`) وفحوص الصلاحيات الرافضة لغياب المستخدم (`currentUser == null`).
     6. اختبار التفعيل كاملاً على جهاز حقيقي بنسخة `release` الموقّعة بمفتاح الإنتاج (وليس نسخة CI المؤقتة أبداً) قبل إصدار أي كود لعميل.
     7. **وسم كل نسخة مُرسلة للتقييم أو الإصدار بـ Git Tag مُعلَّق (Annotated Tag)** لربط كل نسخة مُسلَّمة بلقطة التزام دقيقة وقابلة للتتبع:
        ```bash
        git tag -a v1.3.3 -m "RM.POS v1.3.3 (versionCode 7, Room schema v23)"
        git push origin v1.3.3
        ```

4. **أصول الخرائط (`MapLibre` + `Leaflet`)، مزوّد `OpenFreeMap` المجاني، و`User-Agent`**:
   - **أصول الخرائط مضمّنة محلياً**: يضم التطبيق كلاً من **MapLibre GL JS `4.7.1`** (`app/src/main/assets/maplibre/`) و**Leaflet `1.9.4`** (`app/src/main/assets/leaflet/`) محلياً حتى تفتح شاشات تحديد موقع العميل/المورد مباشرةً دون الاعتماد على CDN خارجي.
   - **خرائط فورية بدون خوادم OSM العامة**: عند عدم تعيين `MAP_TILE_URL_TEMPLATE`، يعرض التطبيق خرائط متجهية فورية عبر **OpenFreeMap** (`https://tiles.openfreemap.org/styles/liberty` — رخصة MIT مجانية للاستخدام التجاري بدون مفتاح API أو حدود استخدام، وبعيدة تماماً عن خوادم `tile.openstreetmap.org` العامة التزاماً بسياسة OSM).
   - **مزوّد تجاري اختياري عبر `MAP_TILE_URL_TEMPLATE`**: عند تعيين رابط HTTPS لمزوّد مرخّص (مثل **MapTiler** أو **Mapbox** أو **Stadia**)، يتحوّل التطبيق تلقائياً إلى محرك Leaflet المحلي لعرض بلاطات ذلك المزوّد. ويظل استخدام `tile.openstreetmap.org` أو الروابط غير المشفّرة (`http://`) مرفوضاً برمجياً وفي بناء `release` (`verifyReleaseMapTileProvider`).
   - يقرأ `MapLeafletWebView` قيمة `User-Agent` ديناميكياً من `BuildConfig.VERSION_NAME` و`BuildConfig.APPLICATION_ID`.
   - **تنبيه مراقبة الحصة (Quota)**: في حال اختيار مزوّد مدفوع وتمرير مفتاحه عبر `MAP_TILE_URL_TEMPLATE`، يُضمَّن المفتاح داخل الـ APK، لذا يُنصح بضبط حدود الاستهلاك ومراقبة الحصة لدى المزوّد.

5. **فحوص الصلاحيات الرافضة لغياب المستخدم (Fail-Closed)**:
   - جميع عمليات المستودع الحساسة (المسح الشامل للبيانات `wipeAllEntitiesData`، تسجيل/حذف فواتير البيع، تسجيل/حذف فواتير الشراء، وعمليات الجرد `requireInventoryAccess`) بالإضافة إلى عمليات الدفع وتعديل العملاء في الـ ViewModels وأزرار التنقل في الواجهة ترفض التنفيذ فوراً (`SecurityException` / منع الوصول) عند غياب مستخدم مسجّل (`currentUser == null`) ولا تمرّره مطلقاً.

6. **البائع الافتراضي والإيصالات**:
   - القيمة الافتراضية لاسم البائع عند عدم وجود مستخدم مسجّل هي سلسلة فارغة (`""`) لتمكين المستخدم من التحكم الكامل باسمه.
   - تخفي قوالب الإيصالات الحرارية (`EscPosReceiptEncoder`) وفواتير HTML (`HtmlInvoiceAndReceiptFormatter`) ومعاينة الإيصال (`POSThermalReceiptDialog`) سطر «البائع / VENDEUR» تلقائياً عندما يكون الاسم فارغاً.

7. **المخططات (`Room Schemas`) والاختبارات وخط CI**:
   - المخططات المُصدَّرة والموجودة في `app/schemas/com.example.data.AppDatabase/` هي: `8`، `14`، `15`، `16`، `17`، `18`، `20`، `22`، و`23` (تمت استعادة `22.json` وإيداع `23.json` مع التحقق الكامل في `MigrationTest` من مسارات الهجرة المتسلسلة حتى الإصدار `23`).
   - تغطي اختبارات Robolectric Compose UI جميع المسارات الحرجة: البيع والدفع والإيصال (`CheckoutPayDialog` + `ThermalReceiptDialog`)، الإرجاع الجزئي وحدةً وحدة (`SaleReturnDialog`)، الجرد وسلة الجرد وتصدير Excel/PDF (`StocktakingUiAndReportTest` + `StocktakingEditDeleteAndEndToEndTest`)، شاشة الترخيص وحالة تعليق الساعة (`SecureLicenseManagerCard`)، تسجيل الدخول والقفل الرتيب (`LoginScreen`)، والنسخ الاحتياطي وتنبيه التصدير الخارجي (`BackupDataSettingsScreen`).
   - يشمل المستودع خط `.github/workflows/android-ci.yml` الذي يشغّل عبر `./gradlew` (Gradle 9.3.1 / JDK 21): فحص الأسرار، `:app:assembleDebug`، `:app:testDebugUnitTest`، `:app:lintDebug`، اختبار فشل `verifyReleaseLicenseServerUrl` و`verifyReleaseMapTileProvider` عند الفراغ أو استخدام خوادم OSM العامة، اختبار بناء `:app:assembleRelease` بمفتاح توقيع مؤقت وقيم اختبار (مع حذف الـ APK والمفتاح المؤقت فوراً؛ **نسخة الـ APK التي يبنيها CI للاختبار لا تُوزَّع أبداً**)، وتحليل Detekt (`config/detekt/detekt.yml`).

---

## 🚀 البناء والاختبار وإصدار التراخيص (Build, Signing & CLI)

### ملاحظات بيئة البناء والاعتماديات (`app/build.gradle.kts`)
- **Gradle Wrapper (`./gradlew`)**: يحتوي المستودع على Gradle Wrapper (`gradlew`، `gradlew.bat`، `gradle/wrapper/gradle-wrapper.jar`، `gradle/wrapper/gradle-wrapper.properties`) مضبوطاً على **Gradle `9.3.1`** المتوافق مع `agp = "9.1.1"` في `gradle/libs.versions.toml`، وهو الأسلوب المعتمد محلياً وفي CI.
- **توقيع Debug**: يُولَّد `debug.keystore` تلقائياً في بيئة البناء أو في CI (ولا يُودع في المستودع)، مع الإبقاء على `signingConfigs` كما هي لضمان توافق تحديثات التطوير.
- **حارس `LICENSE_SERVER_URL` في بناء الإصدار**: ترتبط مهمة `verifyReleaseLicenseServerUrl` بمهام بناء `release` (`assembleRelease`، `bundleRelease`، `preReleaseBuild`) وتفشل البناء فوراً إذا كان متغير البيئة `LICENSE_SERVER_URL` فارغاً أو غير مضبوط.
- **مجموعة الاعتماديات المتوافقة**: إصدارات Android SDK وCompose BOM وRoom وCoil 2 وVico 1.x مُثبّتة كمجموعة متوافقة ومختبرة معاً (تحديث Coil 2→3 أو Vico 1→2 يتطلب إعادة كتابة كاسرة للواجهات ولذا تُترك مستقرة في هذا الفرع).
- **بناء نسخة التطوير (`assembleDebug`)**:
  ```bash
  keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
  ./gradlew :app:assembleDebug
  ```
- **بناء نسخة الإصدار (`assembleRelease` / `bundleRelease`)**:
  احفظ ملف `.jks` خارج مجلد المشروع ومرّر متغيرات البيئة المطابقة حرفياً لـ `app/build.gradle.kts` (حيث `keyAlias = "upload"`):
  - `LICENSE_SERVER_URL` (**إلزامي في `release`** — يفشل البناء إن كان فارغاً)
  - `MAP_TILE_URL_TEMPLATE` *(اختياري — افتراضياً يعمل التطبيق بـ OpenFreeMap المجاني للتطبيقات التجارية؛ وعند تعيينه يجب أن يكون رابط HTTPS لمزوّد مرخّص مثل MapTiler ويُرفض `tile.openstreetmap.org`)*
  - `MAP_TILE_ATTRIBUTION` *(اختياري — نص الإسناد الظاهر على الخريطة)*
  - `KEYSTORE_PATH` (المسار الكامل لملف `.jks` خارج المشروع؛ الافتراضي الاحتياطي `${rootDir}/my-upload-key.jks`)
  - `STORE_PASSWORD` (كلمة مرور الـ Keystore)
  - `KEY_PASSWORD` (كلمة مرور المفتاح `upload`)
  ```bash
  export LICENSE_SERVER_URL="https://license.your-domain.com"
  export MAP_TILE_URL_TEMPLATE="https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=YOUR_KEY"
  export MAP_TILE_ATTRIBUTION="&copy; MapTiler &copy; OpenStreetMap contributors"
  export KEYSTORE_PATH="$HOME/rmpos-key-vault/my-upload-key.jks"
  export STORE_PASSWORD="<strong-store-password>"
  export KEY_PASSWORD="<strong-key-password>"
  ./gradlew :app:bundleRelease
  ```

### تشغيل الاختبارات و Android Lint وفحص الأسرار
```bash
python3 tools/check_secrets_precommit.py --all
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

### اعتماد الوسوم (Git Tags) لكل نسخة تقييم أو إصدار
```bash
git status
git tag -a v1.3.3 -m "RM.POS v1.3.3 (versionCode 7, Room schema v23)"
git push origin main --tags
```

### توليد المفاتيح خارج المشروع وتوقيع الرموز (`ECDSA P-256 • k2026-v2`)
```bash
# 1. توليد زوج مفاتيح جديد من خارج المشروع وحفظ المفتاح الخاص في خزنة خارجية آمنة
mkdir -p ~/rmpos-key-vault && chmod 700 ~/rmpos-key-vault
(cd /tmp && python3 /path/to/project/tools/license_generator.py generate-keys \
  --kid k2026-v2 \
  --priv ~/rmpos-key-vault/license_private_key_k2026_v2.pem \
  --pub /path/to/project/license_public_key.pem)

# 2. التحقق من تطابق license_public_key.pem مع المفتاح المضمّن في التطبيق
python3 tools/license_generator.py verify-keypair \
  --priv ~/rmpos-key-vault/license_private_key_k2026_v2.pem

# 3. توقيع رمز تفعيل لجهاز محدد بالمفتاح الخارجي
python3 tools/license_generator.py sign-license \
  --key ~/rmpos-key-vault/license_private_key_k2026_v2.pem \
  --device-id <16_HEX_DEVICE_ID> \
  --kid k2026-v2 \
  --type PRO \
  --days 365 \
  --grace-days 30
```
