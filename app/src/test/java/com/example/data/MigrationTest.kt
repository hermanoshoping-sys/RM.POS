package com.example.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * اختبار هجرة قاعدة البيانات من الإصدار 7 إلى 8 (MIGRATION_7_8)
 * يتحقق من إنشاء جداول الدفعات (customer_payments و supplier_payments) وفهارسها بنجاح.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {

    @Test
    fun testMigration7To8CreatesPaymentTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_7_8.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. إنشاء قاعدة بيانات SQLite تحاكي الإصدار 7
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        // 2. تطبيق الهجرة MIGRATION_7_8
        AppDatabase.MIGRATION_7_8.migrate(db)

        // 3. التحقق من وجود جدول دفعات العملاء customer_payments
        val customerCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='customer_payments'")
        assertTrue("يجب أن يكون جدول customer_payments موجوداً بعد الهجرة 7->8", customerCursor.moveToFirst())
        customerCursor.close()

        // 4. التحقق من وجود جدول دفعات الموردين supplier_payments
        val supplierCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='supplier_payments'")
        assertTrue("يجب أن يكون جدول supplier_payments موجوداً بعد الهجرة 7->8", supplierCursor.moveToFirst())
        supplierCursor.close()

        // 5. التحقق من إمكانية إدخال واسترجاع البيانات في الجداول المنشأة
        db.execSQL("""
            INSERT INTO customer_payments (customerId, customerName, amount, previousBalance, newBalance, paymentMethod, notes, timestamp, cashierName)
            VALUES (1, 'عميل تجريبي', 150.0, 300.0, 150.0, 'نقدي', 'دفعة حساب', 1600000000, 'admin')
        """.trimIndent())

        val checkPayment = db.query("SELECT amount, customerName FROM customer_payments WHERE customerId = 1")
        assertTrue(checkPayment.moveToFirst())
        assertEquals(150.0, checkPayment.getDouble(0), 0.001)
        assertEquals("عميل تجريبي", checkPayment.getString(1))
        checkPayment.close()

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration8To9_AddsNegativeStockAndAuditLogsTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_8_9.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. إنشاء قاعدة بيانات SQLite تحاكي الإصدار 8
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS global_settings (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            storeName TEXT NOT NULL,
                            storeAddress TEXT NOT NULL,
                            storePhone TEXT NOT NULL,
                            storeEmail TEXT NOT NULL,
                            taxNumber TEXT NOT NULL,
                            currency TEXT NOT NULL,
                            receiptFooter TEXT NOT NULL,
                            logoPath TEXT,
                            taxRate REAL NOT NULL,
                            isTaxEnabled INTEGER NOT NULL,
                            lowStockThreshold INTEGER NOT NULL,
                            autoBackupEnabled INTEGER NOT NULL,
                            autoBackupIntervalHours INTEGER NOT NULL,
                            lastBackupTimestamp INTEGER NOT NULL,
                            enableSoundEffects INTEGER NOT NULL,
                            receiptPaperSize TEXT NOT NULL,
                            scannerMode TEXT NOT NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        // إدراج إعدادات أولية بالإصدار 8
        db.execSQL("""
            INSERT INTO global_settings (id, storeName, storeAddress, storePhone, storeEmail, taxNumber, currency, receiptFooter, taxRate, isTaxEnabled, lowStockThreshold, autoBackupEnabled, autoBackupIntervalHours, lastBackupTimestamp, enableSoundEffects, receiptPaperSize, scannerMode)
            VALUES (1, 'متجر السعادة', 'حي الأمل', '0522112233', 'pos@store.ma', 'TAX-9988', 'د.ج', 'شكراً لزيارتكم', 19.0, 1, 5, 1, 24, 1600000000, 1, '80mm', 'CAMERA')
        """.trimIndent())

        // 2. تطبيق الهجرة MIGRATION_8_9
        AppDatabase.MIGRATION_8_9.migrate(db)

        // 3. التحقق من إضافة عمود allowNegativeStock إلى جدول global_settings بقيمته الافتراضية 0
        val settingsCursor = db.query("SELECT allowNegativeStock, storeName FROM global_settings WHERE id = 1")
        assertTrue("يجب أن يحتوي جدول global_settings على عمود allowNegativeStock بعد الهجرة 8->9", settingsCursor.moveToFirst())
        assertEquals(0, settingsCursor.getInt(0))
        assertEquals("متجر السعادة", settingsCursor.getString(1))
        settingsCursor.close()

        // 4. التحقق من إنشاء جدول سجل التدقيق audit_logs
        val auditCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='audit_logs'")
        assertTrue("يجب أن يكون جدول audit_logs موجوداً بعد الهجرة 8->9", auditCursor.moveToFirst())
        auditCursor.close()

        // 5. التحقق من إمكانية تسجيل حدث أمني في جدول audit_logs
        db.execSQL("""
            INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
            VALUES ('SECURITY_LOCK', 'AUTH', 'user_admin', 'تم تفعيل الحماية من الاختراق', 'admin', 'MANAGER', 1710000000, 1)
        """.trimIndent())

        val checkAudit = db.query("SELECT actionType, username, isCritical FROM audit_logs WHERE id = 1")
        assertTrue(checkAudit.moveToFirst())
        assertEquals("SECURITY_LOCK", checkAudit.getString(0))
        assertEquals("admin", checkAudit.getString(1))
        assertEquals(1, checkAudit.getInt(2))
        checkAudit.close()

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration9To10_SafelyConvertsAllAmountsToCentsWithRounding() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_9_10.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. إنشاء قاعدة بيانات SQLite تحاكي الإصدار 9 ببيانات نقدية مختلفة
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS products (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            barcode TEXT NOT NULL,
                            name TEXT NOT NULL,
                            description TEXT NOT NULL,
                            categoryId INTEGER NOT NULL,
                            categoryName TEXT NOT NULL,
                            unit TEXT NOT NULL,
                            purchasePrice REAL NOT NULL,
                            sellPrice REAL NOT NULL,
                            wholesalePrice REAL NOT NULL,
                            specialPrice REAL NOT NULL,
                            currentStock REAL NOT NULL,
                            minStock REAL NOT NULL,
                            sku TEXT NOT NULL,
                            taxRate REAL NOT NULL,
                            isTaxable INTEGER NOT NULL,
                            isActive INTEGER NOT NULL,
                            imageUri TEXT,
                            isSynced INTEGER NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS customers (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            phone TEXT NOT NULL,
                            address TEXT NOT NULL,
                            balance REAL NOT NULL,
                            totalPurchases REAL NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS suppliers (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            phone TEXT NOT NULL,
                            email TEXT NOT NULL,
                            address TEXT NOT NULL,
                            debtBalance REAL NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS purchases (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            supplierId INTEGER NOT NULL,
                            supplierName TEXT NOT NULL,
                            totalAmount REAL NOT NULL,
                            paidAmount REAL NOT NULL,
                            isVoided INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS purchase_items (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            purchaseId INTEGER NOT NULL,
                            productId INTEGER NOT NULL,
                            productName TEXT NOT NULL,
                            quantity REAL NOT NULL,
                            unitPrice REAL NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS sales (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            invoiceNumber TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL,
                            customerId INTEGER NOT NULL,
                            customerName TEXT NOT NULL,
                            totalAmount REAL NOT NULL,
                            discountAmount REAL NOT NULL,
                            discountType TEXT NOT NULL,
                            paidAmount REAL NOT NULL,
                            changeAmount REAL NOT NULL,
                            paymentMethod TEXT NOT NULL,
                            isVoided INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS sale_items (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            saleId INTEGER NOT NULL,
                            productId INTEGER NOT NULL,
                            productName TEXT NOT NULL,
                            quantity REAL NOT NULL,
                            unitPrice REAL NOT NULL,
                            discountValue REAL NOT NULL,
                            totalLinePrice REAL NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS customer_payments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            customerId INTEGER NOT NULL,
                            customerName TEXT NOT NULL,
                            amount REAL NOT NULL,
                            previousBalance REAL NOT NULL,
                            newBalance REAL NOT NULL,
                            paymentMethod TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS supplier_payments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            supplierId INTEGER NOT NULL,
                            supplierName TEXT NOT NULL,
                            amount REAL NOT NULL,
                            previousDebt REAL NOT NULL,
                            newDebt REAL NOT NULL,
                            paymentMethod TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS audit_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            actionType TEXT NOT NULL,
                            entityType TEXT NOT NULL,
                            entityId TEXT NOT NULL,
                            details TEXT NOT NULL,
                            username TEXT NOT NULL,
                            userRole TEXT NOT NULL DEFAULT 'CASHIER',
                            timestamp INTEGER NOT NULL,
                            isCritical INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        // إدراج بيانات تجريبية مع كسور عائمة تسببت تاريخياً في أخطاء تقريب
        db.execSQL("""
            INSERT INTO products (id, barcode, name, description, categoryId, categoryName, unit, purchasePrice, sellPrice, wholesalePrice, specialPrice, currentStock, minStock, sku, taxRate, isTaxable, isActive, isSynced, createdAt, updatedAt)
            VALUES (1, 'BAR-101', 'منتج تجريبي', '', 1, 'عام', 'قطعة', 12.345, 25.501, 20.004, 22.999, 10.0, 1.0, '', 0.0, 0, 1, 0, 1000, 1000)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO customers (id, name, phone, address, balance, totalPurchases)
            VALUES (1, 'زبون ديون', '0600000000', 'العنوان', 150.254, 1200.756)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO suppliers (id, name, phone, email, address, debtBalance)
            VALUES (1, 'مورد سلع', '0500000000', 'sup@test.com', 'العنوان', 500.505)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO sales (id, invoiceNumber, timestamp, cashierName, customerId, customerName, totalAmount, discountAmount, discountType, paidAmount, changeAmount, paymentMethod, isVoided)
            VALUES (1, 'INV-10', 1000, 'admin', 1, 'زبون ديون', 99.991, 5.004, 'VALUE', 100.0, 0.0, 'CASH', 0)
        """.trimIndent())

        // 2. تطبيق الهجرة MIGRATION_9_10
        AppDatabase.MIGRATION_9_10.migrate(db)

        // 3. التحقق من تحويل وتقريب القيم النقدية لضربها في 100 بدقة السنتيم
        val productCursor = db.query("SELECT purchasePrice, sellPrice, wholesalePrice, specialPrice FROM products WHERE id = 1")
        assertTrue(productCursor.moveToFirst())
        assertEquals(12.35, productCursor.getDouble(0), 0.0001) // 12.345 rounded to 12.35 (1235 cents)
        assertEquals(25.50, productCursor.getDouble(1), 0.0001) // 25.501 rounded to 25.50 (2550 cents)
        assertEquals(20.00, productCursor.getDouble(2), 0.0001) // 20.004 rounded to 20.00 (2000 cents)
        assertEquals(23.00, productCursor.getDouble(3), 0.0001) // 22.999 rounded to 23.00 (2300 cents)
        productCursor.close()

        val custCursor = db.query("SELECT balance, totalPurchases FROM customers WHERE id = 1")
        assertTrue(custCursor.moveToFirst())
        assertEquals(150.25, custCursor.getDouble(0), 0.0001) // 150.254 rounded to 150.25
        assertEquals(1200.76, custCursor.getDouble(1), 0.0001) // 1200.756 rounded to 1200.76
        custCursor.close()

        val supCursor = db.query("SELECT debtBalance FROM suppliers WHERE id = 1")
        assertTrue(supCursor.moveToFirst())
        assertEquals(500.51, supCursor.getDouble(0), 0.0001) // 500.505 rounded to 500.51
        supCursor.close()

        val saleCursor = db.query("SELECT totalAmount, discountAmount FROM sales WHERE id = 1")
        assertTrue(saleCursor.moveToFirst())
        assertEquals(99.99, saleCursor.getDouble(0), 0.0001)
        assertEquals(5.00, saleCursor.getDouble(1), 0.0001)
        saleCursor.close()

        // 4. التحقق من توثيق حدث الهجرة في audit_logs
        val auditCursor = db.query("SELECT actionType, entityId FROM audit_logs WHERE actionType = 'FINANCIAL_MIGRATION'")
        assertTrue("يجب تسجيل حدث الهجرة في audit_logs", auditCursor.moveToFirst())
        assertEquals("FINANCIAL_MIGRATION", auditCursor.getString(0))
        assertEquals("SCHEMA_V10", auditCursor.getString(1))
        auditCursor.close()

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration10To11_AddsInvoiceNumberToPurchases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_10_11.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS purchases (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, supplierId INTEGER NOT NULL, supplierName TEXT NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL, timestamp INTEGER NOT NULL, isVoided INTEGER NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_10_11.migrate(db)

        val cursor = db.query("PRAGMA table_info(purchases)")
        var found = false
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == "invoiceNumber") {
                found = true
                break
            }
        }
        cursor.close()
        assertTrue("يجب أن يحتوي جدول purchases على عمود invoiceNumber بعد الهجرة 10->11", found)

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration11To12_AddsDetailedUserPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_11_12.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL, role TEXT NOT NULL)")
                    db.execSQL("INSERT INTO users (id, username, role) VALUES (1, 'admin', 'MANAGER')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_11_12.migrate(db)

        val cursor = db.query("SELECT canEditInvoice, canManageSuppliers, canManagePayments FROM users WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
        assertEquals(1, cursor.getInt(1))
        assertEquals(1, cursor.getInt(2))
        cursor.close()

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration12To13_AddsCustomerGeoLocationFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_12_13.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_12_13.migrate(db)

        val custCols = db.query("PRAGMA table_info(customers)")
        val colNames = mutableListOf<String>()
        while (custCols.moveToNext()) {
            colNames.add(custCols.getString(1))
        }
        custCols.close()
        assertTrue(colNames.contains("latitude"))
        assertTrue(colNames.contains("longitude"))
        assertTrue(colNames.contains("geoRadiusMeters"))
        assertTrue(colNames.contains("isGeoLocationRequired"))

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration13To14_AddsCanEditCustomersPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_13_14.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, username TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_13_14.migrate(db)

        val cursor = db.query("PRAGMA table_info(users)")
        var found = false
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == "canEditCustomers") {
                found = true
                break
            }
        }
        cursor.close()
        assertTrue(found)

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration14To15_AddsCustomerNumberAndBarcodeAndSequenceTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_14_15.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                    db.execSQL("INSERT INTO customers (id, name) VALUES (1, 'زبون 1'), (2, 'زبون 2')")
                    db.execSQL("CREATE TABLE IF NOT EXISTS global_settings (id INTEGER PRIMARY KEY NOT NULL, shopName TEXT NOT NULL)")
                    db.execSQL("INSERT INTO global_settings (id, shopName) VALUES (1, 'SuperMarket')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_14_15.migrate(db)

        val seqCursor = db.query("SELECT lastCustomerNumber FROM customer_sequences WHERE id = 1")
        assertTrue("يجب أن يتم ملء customer_sequences", seqCursor.moveToFirst())
        assertEquals(2L, seqCursor.getLong(0))
        seqCursor.close()

        val custCursor = db.query("SELECT customerNumber, barcode FROM customers WHERE id = 1")
        assertTrue(custCursor.moveToFirst())
        assertEquals(1L, custCursor.getLong(0))
        assertEquals("supermarket-1", custCursor.getString(1))
        custCursor.close()

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration15To16_AddsCategoryCatalogImageUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_15_16.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS categories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        AppDatabase.MIGRATION_15_16.migrate(db)

        val catCols = db.query("PRAGMA table_info(categories)")
        var foundImageUri = false
        while (catCols.moveToNext()) {
            if (catCols.getString(1) == "imageUri") {
                foundImageUri = true
                break
            }
        }
        catCols.close()
        assertTrue("يجب أن يحتوي جدول categories على عمود imageUri بعد الهجرة 15->16", foundImageUri)

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration16To17_ConvertsFinancialAmountsToIntegerCents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_16_17.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(16) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 1. customers (v16 schema with REAL balance)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS customers (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            phone TEXT NOT NULL,
                            address TEXT NOT NULL,
                            balance REAL NOT NULL,
                            totalPurchases REAL NOT NULL,
                            latitude REAL,
                            longitude REAL,
                            geoRadiusMeters REAL NOT NULL DEFAULT 150.0,
                            isGeoLocationRequired INTEGER NOT NULL DEFAULT 0,
                            customerNumber INTEGER NOT NULL DEFAULT 0,
                            barcode TEXT NOT NULL DEFAULT ''
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO customers (id, name, phone, address, balance, totalPurchases) VALUES (1, 'أحمد', '0600000000', 'الدار البيضاء', 120.50, 350.25)")

                    // 2. suppliers (v16 schema with REAL debtBalance)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS suppliers (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            phone TEXT NOT NULL,
                            email TEXT NOT NULL DEFAULT '',
                            address TEXT NOT NULL DEFAULT '',
                            debtBalance REAL NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO suppliers (id, name, phone, debtBalance) VALUES (1, 'مورد الألبان', '0611111111', 500.0)")

                    // 3. purchases (v16 schema with REAL totalAmount & paidAmount)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS purchases (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            supplierId INTEGER NOT NULL,
                            supplierName TEXT NOT NULL,
                            totalAmount REAL NOT NULL,
                            paidAmount REAL NOT NULL,
                            isVoided INTEGER NOT NULL DEFAULT 0,
                            invoiceNumber TEXT NOT NULL DEFAULT ''
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO purchases (id, timestamp, supplierId, supplierName, totalAmount, paidAmount) VALUES (1, 1000, 1, 'مورد الألبان', 150.20, 100.0)")

                    // 4. sales (v16 schema with REAL totalAmount, discountAmount, paidAmount, changeAmount)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS sales (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            invoiceNumber TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL,
                            customerId INTEGER NOT NULL DEFAULT 0,
                            customerName TEXT NOT NULL DEFAULT '',
                            totalAmount REAL NOT NULL,
                            discountAmount REAL NOT NULL,
                            discountType TEXT NOT NULL,
                            paidAmount REAL NOT NULL,
                            changeAmount REAL NOT NULL,
                            paymentMethod TEXT NOT NULL,
                            isVoided INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO sales (id, invoiceNumber, timestamp, cashierName, customerId, customerName, totalAmount, discountAmount, discountType, paidAmount, changeAmount, paymentMethod) VALUES (1, 'INV-001', 2000, 'Admin', 1, 'أحمد', 250.75, 10.0, 'FIXED', 200.0, 0.0, 'CASH')")

                    // 5. expenses (v16 schema with REAL amount)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS expenses (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            amount REAL NOT NULL,
                            timestamp INTEGER NOT NULL,
                            category TEXT NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO expenses (id, title, amount, timestamp, category) VALUES (1, 'كهرباء', 45.50, 3000, 'فواتير')")

                    // 6. customer_payments (v16 schema with REAL amount, previousBalance, newBalance)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS customer_payments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            customerId INTEGER NOT NULL,
                            customerName TEXT NOT NULL,
                            amount REAL NOT NULL,
                            previousBalance REAL NOT NULL,
                            newBalance REAL NOT NULL,
                            paymentMethod TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO customer_payments (id, customerId, customerName, amount, previousBalance, newBalance, paymentMethod, notes, timestamp, cashierName) VALUES (1, 1, 'أحمد', 50.0, 120.50, 70.50, 'CASH', '', 4000, 'Admin')")

                    // 7. supplier_payments (v16 schema with REAL amount, previousDebt, newDebt)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS supplier_payments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            supplierId INTEGER NOT NULL,
                            supplierName TEXT NOT NULL,
                            amount REAL NOT NULL,
                            previousDebt REAL NOT NULL,
                            newDebt REAL NOT NULL,
                            paymentMethod TEXT NOT NULL,
                            notes TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO supplier_payments (id, supplierId, supplierName, amount, previousDebt, newDebt, paymentMethod, notes, timestamp, cashierName) VALUES (1, 1, 'مورد الألبان', 200.0, 500.0, 300.0, 'CASH', '', 5000, 'Admin')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        // Execute migration 16 -> 17
        AppDatabase.MIGRATION_16_17.migrate(db)

        // Verify customers table converted to INTEGER cents
        val cCursor = db.query("SELECT balance, totalPurchases FROM customers WHERE id = 1")
        assertTrue(cCursor.moveToFirst())
        assertEquals(12050L, cCursor.getLong(0))
        assertEquals(35025L, cCursor.getLong(1))
        cCursor.close()

        // Verify suppliers table converted to INTEGER cents
        val sCursor = db.query("SELECT debtBalance FROM suppliers WHERE id = 1")
        assertTrue(sCursor.moveToFirst())
        assertEquals(50000L, sCursor.getLong(0))
        sCursor.close()

        // Verify purchases table converted to INTEGER cents
        val pCursor = db.query("SELECT totalAmount, paidAmount FROM purchases WHERE id = 1")
        assertTrue(pCursor.moveToFirst())
        assertEquals(15020L, pCursor.getLong(0))
        assertEquals(10000L, pCursor.getLong(1))
        pCursor.close()

        // Verify sales table converted to INTEGER cents
        val saleCursor = db.query("SELECT totalAmount, discountAmount, paidAmount, changeAmount FROM sales WHERE id = 1")
        assertTrue(saleCursor.moveToFirst())
        assertEquals(25075L, saleCursor.getLong(0))
        assertEquals(1000L, saleCursor.getLong(1))
        assertEquals(20000L, saleCursor.getLong(2))
        assertEquals(0L, saleCursor.getLong(3))
        saleCursor.close()

        // Verify expenses table converted to INTEGER cents
        val expCursor = db.query("SELECT amount FROM expenses WHERE id = 1")
        assertTrue(expCursor.moveToFirst())
        assertEquals(4550L, expCursor.getLong(0))
        expCursor.close()

        // Verify customer_payments table converted to INTEGER cents
        val cpCursor = db.query("SELECT amount, previousBalance, newBalance FROM customer_payments WHERE id = 1")
        assertTrue(cpCursor.moveToFirst())
        assertEquals(5000L, cpCursor.getLong(0))
        assertEquals(12050L, cpCursor.getLong(1))
        assertEquals(7050L, cpCursor.getLong(2))
        cpCursor.close()

        // Verify supplier_payments table converted to INTEGER cents
        val spCursor = db.query("SELECT amount, previousDebt, newDebt FROM supplier_payments WHERE id = 1")
        assertTrue(spCursor.moveToFirst())
        assertEquals(20000L, spCursor.getLong(0))
        assertEquals(50000L, spCursor.getLong(1))
        assertEquals(30000L, spCursor.getLong(2))
        spCursor.close()

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration17To18_AddsSaleIdAndPurchaseIdToPayments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_17_18.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(17) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS customer_payments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            customerId INTEGER NOT NULL,
                            customerName TEXT NOT NULL,
                            amount INTEGER NOT NULL DEFAULT 0,
                            previousBalance INTEGER NOT NULL DEFAULT 0,
                            newBalance INTEGER NOT NULL DEFAULT 0,
                            paymentMethod TEXT NOT NULL DEFAULT 'CASH',
                            notes TEXT NOT NULL DEFAULT '',
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS supplier_payments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            supplierId INTEGER NOT NULL,
                            supplierName TEXT NOT NULL,
                            amount INTEGER NOT NULL DEFAULT 0,
                            previousDebt INTEGER NOT NULL DEFAULT 0,
                            newDebt INTEGER NOT NULL DEFAULT 0,
                            paymentMethod TEXT NOT NULL DEFAULT 'CASH',
                            notes TEXT NOT NULL DEFAULT '',
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("INSERT INTO customer_payments (id, customerId, customerName, amount, paymentMethod, notes, timestamp, cashierName) VALUES (1, 10, 'زبون التجربة', 5000, 'CASH', '', 1000, 'Admin')")
                    db.execSQL("INSERT INTO supplier_payments (id, supplierId, supplierName, amount, paymentMethod, notes, timestamp, cashierName) VALUES (1, 20, 'مورد التجربة', 8000, 'CASH', '', 2000, 'Admin')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        // Apply MIGRATION_17_18
        AppDatabase.MIGRATION_17_18.migrate(db)

        // Verify customer_payments has saleId
        val cpCols = db.query("PRAGMA table_info(customer_payments)")
        var foundSaleId = false
        while (cpCols.moveToNext()) {
            if (cpCols.getString(1) == "saleId") {
                foundSaleId = true
                break
            }
        }
        cpCols.close()
        assertTrue("يجب أن يحتوي customer_payments على عمود saleId", foundSaleId)

        // Verify supplier_payments has purchaseId
        val spCols = db.query("PRAGMA table_info(supplier_payments)")
        var foundPurchaseId = false
        while (spCols.moveToNext()) {
            if (spCols.getString(1) == "purchaseId") {
                foundPurchaseId = true
                break
            }
        }
        spCols.close()
        assertTrue("يجب أن يحتوي supplier_payments على عمود purchaseId", foundPurchaseId)

        // Verify default value is 0 for existing rows
        val cpCursor = db.query("SELECT saleId FROM customer_payments WHERE id = 1")
        assertTrue(cpCursor.moveToFirst())
        assertEquals(0L, cpCursor.getLong(0))
        cpCursor.close()

        val spCursor = db.query("SELECT purchaseId FROM supplier_payments WHERE id = 1")
        assertTrue(spCursor.moveToFirst())
        assertEquals(0L, spCursor.getLong(0))
        spCursor.close()

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration18To19_AddsForeignKeysUnitCostAndExplicitStockReferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_migration_18_19.db")
        if (dbFile.exists()) dbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS sales (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            invoiceNumber TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            cashierName TEXT NOT NULL,
                            customerId INTEGER NOT NULL DEFAULT 0,
                            customerName TEXT NOT NULL DEFAULT '',
                            totalAmount INTEGER NOT NULL DEFAULT 0,
                            discountAmount INTEGER NOT NULL DEFAULT 0,
                            discountType TEXT NOT NULL,
                            paidAmount INTEGER NOT NULL DEFAULT 0,
                            changeAmount INTEGER NOT NULL DEFAULT 0,
                            paymentMethod TEXT NOT NULL,
                            isVoided INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS purchases (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            supplierId INTEGER NOT NULL,
                            supplierName TEXT NOT NULL,
                            totalAmount INTEGER NOT NULL DEFAULT 0,
                            paidAmount INTEGER NOT NULL DEFAULT 0,
                            isVoided INTEGER NOT NULL DEFAULT 0,
                            invoiceNumber TEXT NOT NULL DEFAULT ''
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS sale_items (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            saleId INTEGER NOT NULL,
                            productId INTEGER NOT NULL,
                            productName TEXT NOT NULL,
                            quantity REAL NOT NULL,
                            unitPrice REAL NOT NULL,
                            discountValue REAL NOT NULL,
                            totalLinePrice REAL NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS purchase_items (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            purchaseId INTEGER NOT NULL,
                            productId INTEGER NOT NULL,
                            productName TEXT NOT NULL,
                            quantity REAL NOT NULL,
                            unitPrice REAL NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS stock_movements (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            productId INTEGER NOT NULL,
                            productName TEXT NOT NULL,
                            type TEXT NOT NULL,
                            quantity REAL NOT NULL,
                            timestamp INTEGER NOT NULL,
                            notes TEXT NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("INSERT INTO sales (id, invoiceNumber, timestamp, cashierName, totalAmount, discountAmount, discountType, paidAmount, changeAmount, paymentMethod) VALUES (1, 'INV-18', 1000, 'Admin', 2000, 0, 'FIXED', 2000, 0, 'CASH')")
                    db.execSQL("INSERT INTO sale_items (id, saleId, productId, productName, quantity, unitPrice, discountValue, totalLinePrice) VALUES (1, 1, 5, 'شاي ممتاز', 2.0, 10.0, 0.0, 20.0)")
                    db.execSQL("INSERT INTO stock_movements (id, productId, productName, type, quantity, timestamp, notes) VALUES (1, 5, 'شاي ممتاز', 'SALE', -2.0, 1000, 'فاتورة بيع')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase

        // Apply MIGRATION_18_19
        AppDatabase.MIGRATION_18_19.migrate(db)

        // Verify stock_movements has referenceType and referenceId
        val smCols = db.query("PRAGMA table_info(stock_movements)")
        var foundRefType = false
        var foundRefId = false
        while (smCols.moveToNext()) {
            val name = smCols.getString(1)
            if (name == "referenceType") foundRefType = true
            if (name == "referenceId") foundRefId = true
        }
        smCols.close()
        assertTrue("يجب أن يحتوي stock_movements على referenceType", foundRefType)
        assertTrue("يجب أن يحتوي stock_movements على referenceId", foundRefId)

        // Verify sale_items has unitCost
        val siCols = db.query("PRAGMA table_info(sale_items)")
        var foundUnitCost = false
        while (siCols.moveToNext()) {
            if (siCols.getString(1) == "unitCost") {
                foundUnitCost = true
                break
            }
        }
        siCols.close()
        assertTrue("يجب أن يحتوي sale_items على unitCost", foundUnitCost)

        // Verify foreign key in sale_items
        val fkCursor = db.query("PRAGMA foreign_key_list(sale_items)")
        var hasSalesFk = false
        while (fkCursor.moveToNext()) {
            val table = fkCursor.getString(2)
            if (table == "sales") {
                hasSalesFk = true
                break
            }
        }
        fkCursor.close()
        assertTrue("يجب أن يرتبط sale_items بجدول sales عبر Foreign Key", hasSalesFk)

        db.close()
        dbFile.delete()
    }

    @Test
    fun testMigration19To20_PopulatedDatabaseCopyUnifiesMoneyToCentsAndOpensRoom() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDbFile = context.getDatabasePath("test_v19_populated_source.db")
        val copyDbFile = context.getDatabasePath("test_v19_populated_copy.db")
        sourceDbFile.parentFile?.mkdirs()
        listOf(sourceDbFile, copyDbFile).forEach { f ->
            File(f.path).delete()
            File("${f.path}-wal").delete()
            File("${f.path}-shm").delete()
            File("${f.path}-journal").delete()
        }

        // 1. إنشاء قاعدة بيانات حقيقية بالإصدار 19 مطابقة تماماً لمخطط 19.json مع بيانات واقعية
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(sourceDbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(19) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `username` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `role` TEXT NOT NULL, `canSell` INTEGER NOT NULL, `canPurchase` INTEGER NOT NULL, `canReports` INTEGER NOT NULL, `canModifyPrice` INTEGER NOT NULL, `canDeleteInvoice` INTEGER NOT NULL, `canManageUsers` INTEGER NOT NULL, `canEditInvoice` INTEGER NOT NULL, `canManageInventory` INTEGER NOT NULL, `canManageClients` INTEGER NOT NULL, `canEditCustomers` INTEGER NOT NULL, `canManageSuppliers` INTEGER NOT NULL, `canManageExpenses` INTEGER NOT NULL, `canManagePayments` INTEGER NOT NULL, `canManageSettings` INTEGER NOT NULL, `enforceCustomerGeoLocation` INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `imageUri` TEXT)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `barcode` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, `categoryName` TEXT NOT NULL, `unit` TEXT NOT NULL, `purchasePrice` REAL NOT NULL, `sellPrice` REAL NOT NULL, `wholesalePrice` REAL NOT NULL, `specialPrice` REAL NOT NULL, `currentStock` REAL NOT NULL, `minStock` REAL NOT NULL, `sku` TEXT NOT NULL, `taxRate` REAL NOT NULL, `isTaxable` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `imageUri` TEXT, `isSynced` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_sku` ON `products` (`sku`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_isSynced` ON `products` (`isSynced`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT NOT NULL, `balance` INTEGER NOT NULL, `totalPurchases` INTEGER NOT NULL, `latitude` REAL, `longitude` REAL, `geoRadiusMeters` REAL NOT NULL, `isGeoLocationRequired` INTEGER NOT NULL, `customerNumber` INTEGER NOT NULL, `barcode` TEXT NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_customerNumber` ON `customers` (`customerNumber`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_barcode` ON `customers` (`barcode`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `email` TEXT NOT NULL, `address` TEXT NOT NULL, `debtBalance` INTEGER NOT NULL)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `supplierId` INTEGER NOT NULL, `supplierName` TEXT NOT NULL, `totalAmount` INTEGER NOT NULL, `paidAmount` INTEGER NOT NULL, `isVoided` INTEGER NOT NULL, `invoiceNumber` TEXT NOT NULL)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purchaseId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_productId` ON `purchase_items` (`productId`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `sales` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `invoiceNumber` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `cashierName` TEXT NOT NULL, `customerId` INTEGER NOT NULL, `customerName` TEXT NOT NULL, `totalAmount` INTEGER NOT NULL, `discountAmount` INTEGER NOT NULL, `discountType` TEXT NOT NULL, `paidAmount` INTEGER NOT NULL, `changeAmount` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `isVoided` INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_invoiceNumber` ON `sales` (`invoiceNumber`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `sale_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `saleId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `discountValue` REAL NOT NULL, `totalLinePrice` REAL NOT NULL, `unitCost` REAL NOT NULL, FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_saleId` ON `sale_items` (`saleId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_productId` ON `sale_items` (`productId`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `orders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNumber` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `customerId` INTEGER, `customerName` TEXT NOT NULL, `cashierName` TEXT NOT NULL, `subtotalAmount` REAL NOT NULL, `discountAmount` REAL NOT NULL, `discountType` TEXT NOT NULL, `taxAmount` REAL NOT NULL, `totalAmount` REAL NOT NULL, `paidAmount` REAL NOT NULL, `changeAmount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `isVoided` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_orders_orderNumber` ON `orders` (`orderNumber`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_timestamp` ON `orders` (`timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_customerId` ON `orders` (`customerId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_status` ON `orders` (`status`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `order_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `barcode` TEXT NOT NULL, `productName` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `purchasePrice` REAL NOT NULL, `discountValue` REAL NOT NULL, `totalLinePrice` REAL NOT NULL, FOREIGN KEY(`orderId`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_orderId` ON `order_items` (`orderId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_productId` ON `order_items` (`productId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_barcode` ON `order_items` (`barcode`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `stock_movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `type` TEXT NOT NULL, `quantity` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `notes` TEXT NOT NULL, `referenceType` TEXT NOT NULL, `referenceId` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_referenceType_referenceId` ON `stock_movements` (`referenceType`, `referenceId`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `amount` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `category` TEXT NOT NULL)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `global_settings` (`id` INTEGER NOT NULL, `shopName` TEXT NOT NULL, `shopAddress` TEXT NOT NULL, `shopPhone` TEXT NOT NULL, `taxNumber` TEXT NOT NULL, `receiptHeader` TEXT NOT NULL, `receiptFooter` TEXT NOT NULL, `language` TEXT NOT NULL, `showShopName` INTEGER NOT NULL, `showAddress` INTEGER NOT NULL, `showPhone` INTEGER NOT NULL, `showTaxNumber` INTEGER NOT NULL, `showLogo` INTEGER NOT NULL, `showCashierName` INTEGER NOT NULL, `showCustomerInfo` INTEGER NOT NULL, `showDateTime` INTEGER NOT NULL, `showBarcode` INTEGER NOT NULL, `showPaymentMethod` INTEGER NOT NULL, `showReceiptFooter` INTEGER NOT NULL, `printerConnectionType` TEXT NOT NULL, `printerName` TEXT NOT NULL, `printerMac` TEXT NOT NULL, `printerIp` TEXT NOT NULL, `printerPort` INTEGER NOT NULL, `printerPaperWidth` INTEGER NOT NULL, `autoPrintReceipt` INTEGER NOT NULL, `logoWidth` INTEGER NOT NULL, `logoHeight` INTEGER NOT NULL, `headerHeight` INTEGER NOT NULL, `headerFontSize` INTEGER NOT NULL, `allowNegativeStock` INTEGER NOT NULL, PRIMARY KEY(`id`))")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionNumber` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `cashierName` TEXT NOT NULL, `customerId` INTEGER, `customerName` TEXT NOT NULL, `subtotal` REAL NOT NULL, `discountAmount` REAL NOT NULL, `discountType` TEXT NOT NULL, `taxAmount` REAL NOT NULL, `totalAmount` REAL NOT NULL, `paidAmount` REAL NOT NULL, `changeAmount` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `isVoided` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_transactionNumber` ON `transactions` (`transactionNumber`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_customerId` ON `transactions` (`customerId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_status` ON `transactions` (`status`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_isSynced` ON `transactions` (`isSynced`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `barcode` TEXT NOT NULL, `productName` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `purchasePrice` REAL NOT NULL, `discountAmount` REAL NOT NULL, `taxRate` REAL NOT NULL, `totalPrice` REAL NOT NULL, FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_transactionId` ON `transaction_items` (`transactionId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_productId` ON `transaction_items` (`productId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_barcode` ON `transaction_items` (`barcode`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `cart_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cartSessionId` TEXT NOT NULL, `productId` INTEGER NOT NULL, `barcode` TEXT NOT NULL, `productName` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `unit` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitPrice` REAL NOT NULL, `purchasePrice` REAL NOT NULL, `customPrice` REAL, `lineDiscount` REAL NOT NULL, `totalLinePrice` REAL NOT NULL, `notes` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_cart_items_cartSessionId` ON `cart_items` (`cartSessionId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_cart_items_productId` ON `cart_items` (`productId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_cart_items_barcode` ON `cart_items` (`barcode`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `customer_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerId` INTEGER NOT NULL, `customerName` TEXT NOT NULL, `amount` INTEGER NOT NULL, `previousBalance` INTEGER NOT NULL, `newBalance` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `notes` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `cashierName` TEXT NOT NULL, `saleId` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_customerId` ON `customer_payments` (`customerId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_timestamp` ON `customer_payments` (`timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_payments_saleId` ON `customer_payments` (`saleId`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `supplier_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supplierId` INTEGER NOT NULL, `supplierName` TEXT NOT NULL, `amount` INTEGER NOT NULL, `previousDebt` INTEGER NOT NULL, `newDebt` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `notes` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `cashierName` TEXT NOT NULL, `purchaseId` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_supplierId` ON `supplier_payments` (`supplierId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_timestamp` ON `supplier_payments` (`timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplier_payments_purchaseId` ON `supplier_payments` (`purchaseId`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `actionType` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `details` TEXT NOT NULL, `username` TEXT NOT NULL, `userRole` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isCritical` INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_actionType` ON `audit_logs` (`actionType`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_username` ON `audit_logs` (`username`)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS `customer_sequences` (`id` INTEGER NOT NULL, `lastCustomerNumber` INTEGER NOT NULL, PRIMARY KEY(`id`))")

                    // Populate realistic v19 records across all 8 converted tables + parent tables
                    db.execSQL("INSERT INTO `categories` (`id`, `name`, `imageUri`) VALUES (1, 'مشروبات', NULL)")
                    db.execSQL("INSERT INTO `products` (`id`, `barcode`, `name`, `description`, `categoryId`, `categoryName`, `unit`, `purchasePrice`, `sellPrice`, `wholesalePrice`, `specialPrice`, `currentStock`, `minStock`, `sku`, `taxRate`, `isTaxable`, `isActive`, `imageUri`, `isSynced`, `createdAt`, `updatedAt`) VALUES (10, '6110001', 'زيت الزيتون 1L', 'بكر ممتاز', 1, 'زيوت', 'قارورة', 12.345, 25.501, 22.999, 21.004, 40.0, 5.0, 'SKU-10', 0.0, 0, 1, NULL, 0, 1000, 1000)")
                    db.execSQL("INSERT INTO `suppliers` (`id`, `name`, `phone`, `email`, `address`, `debtBalance`) VALUES (1, 'مورد الزيوت', '0522000001', 'oil@sup.ma', 'فاس', 50000)")
                    db.execSQL("INSERT INTO `purchases` (`id`, `timestamp`, `supplierId`, `supplierName`, `totalAmount`, `paidAmount`, `isVoided`, `invoiceNumber`) VALUES (1, 1710000000000, 1, 'مورد الزيوت', 12350, 5000, 0, 'PUR-19')")
                    db.execSQL("INSERT INTO `purchase_items` (`id`, `purchaseId`, `productId`, `productName`, `quantity`, `unitPrice`) VALUES (1, 1, 10, 'زيت الزيتون 1L', 10.0, 12.345)")

                    db.execSQL("INSERT INTO `sales` (`id`, `invoiceNumber`, `timestamp`, `cashierName`, `customerId`, `customerName`, `totalAmount`, `discountAmount`, `discountType`, `paidAmount`, `changeAmount`, `paymentMethod`, `isVoided`) VALUES (1, '0019', 1710000100000, 'admin', 0, 'زبون عادي', 5001, 99, 'FIXED', 5001, 0, 'CASH', 0)")
                    db.execSQL("INSERT INTO `sale_items` (`id`, `saleId`, `productId`, `productName`, `quantity`, `unitPrice`, `discountValue`, `totalLinePrice`, `unitCost`) VALUES (1, 1, 10, 'زيت الزيتون 1L', 2.0, 25.501, 0.99, 50.01, 12.345)")

                    db.execSQL("INSERT INTO `orders` (`id`, `orderNumber`, `timestamp`, `customerId`, `customerName`, `cashierName`, `subtotalAmount`, `discountAmount`, `discountType`, `taxAmount`, `totalAmount`, `paidAmount`, `changeAmount`, `paymentMethod`, `status`, `notes`, `isVoided`, `isSynced`) VALUES (1, 'ORD-19', 1710000200000, NULL, 'عميل طلبية', 'admin', 25.50, 0.50, 'VALUE', 2.50, 27.50, 30.00, 2.50, 'CASH', 'COMPLETED', '', 0, 0)")
                    db.execSQL("INSERT INTO `order_items` (`id`, `orderId`, `productId`, `barcode`, `productName`, `quantity`, `unitPrice`, `purchasePrice`, `discountValue`, `totalLinePrice`) VALUES (1, 1, 10, '6110001', 'زيت الزيتون 1L', 1.0, 25.50, 12.35, 0.50, 25.00)")

                    db.execSQL("INSERT INTO `transactions` (`id`, `transactionNumber`, `timestamp`, `cashierName`, `customerId`, `customerName`, `subtotal`, `discountAmount`, `discountType`, `taxAmount`, `totalAmount`, `paidAmount`, `changeAmount`, `paymentMethod`, `type`, `status`, `notes`, `isVoided`, `isSynced`, `createdAt`, `updatedAt`) VALUES (1, 'TRX-19', 1710000300000, 'admin', NULL, 'عميل معاملات', 100.00, 5.25, 'FIXED', 10.50, 105.25, 110.00, 4.75, 'CARD', 'SALE', 'COMPLETED', '', 0, 0, 1710000300000, 1710000300000)")
                    db.execSQL("INSERT INTO `transaction_items` (`id`, `transactionId`, `productId`, `barcode`, `productName`, `quantity`, `unitPrice`, `purchasePrice`, `discountAmount`, `taxRate`, `totalPrice`) VALUES (1, 1, 10, '6110001', 'زيت الزيتون 1L', 4.0, 25.50, 12.35, 5.25, 10.0, 96.75)")

                    // Two cart_items: one with customPrice = NULL and one with customPrice = 19.99
                    db.execSQL("INSERT INTO `cart_items` (`id`, `cartSessionId`, `productId`, `barcode`, `productName`, `categoryName`, `unit`, `quantity`, `unitPrice`, `purchasePrice`, `customPrice`, `lineDiscount`, `totalLinePrice`, `notes`, `timestamp`) VALUES (1, 'sess-1', 10, '6110001', 'زيت الزيتون 1L', 'زيوت', 'قارورة', 1.0, 25.50, 12.35, NULL, 0.0, 25.50, '', 1710000400000)")
                    db.execSQL("INSERT INTO `cart_items` (`id`, `cartSessionId`, `productId`, `barcode`, `productName`, `categoryName`, `unit`, `quantity`, `unitPrice`, `purchasePrice`, `customPrice`, `lineDiscount`, `totalLinePrice`, `notes`, `timestamp`) VALUES (2, 'sess-1', 10, '6110001', 'زيت الزيتون 1L', 'زيوت', 'قارورة', 2.0, 25.50, 12.35, 19.99, 1.00, 38.98, 'سعر خاص', 1710000401000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sourceDb = factory.create(config).writableDatabase
        sourceDb.close()

        // 2. أخذ نسخة طبق الأصل من قاعدة البيانات v19 المملوءة لتجربة الترحيل عليها دون المساس بالأصل
        sourceDbFile.copyTo(copyDbFile, overwrite = true)

        // 3. فتح النسخة مباشرة عبر Room بدون fallbackToDestructiveMigration للتحقق الصارم من المخطط v21 وتنفيذ MIGRATION_19_20 ثم MIGRATION_20_21
        val migratedRoomDb = androidx.room.Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            copyDbFile.name
        )
            .addMigrations(
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23
            )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migratedRoomDb.appDao()
            // استعلام فعلي عبر Room DAO لتفعيل التحقق الكامل من تطابق كافة الجداول والفهارس والمفاتيح الأجنبية مع v20
            val migratedProduct = kotlinx.coroutines.runBlocking { dao.getProductById(10L) }
            assertEquals(1235L, migratedProduct!!.purchasePrice)
            assertEquals(2550L, migratedProduct.sellPrice)
            assertEquals(2300L, migratedProduct.wholesalePrice)
            assertEquals(2100L, migratedProduct.specialPrice)

            val migratedSaleItems = kotlinx.coroutines.runBlocking { dao.getSaleItems(1L) }
            assertEquals(1, migratedSaleItems.size)
            assertEquals(2550L, migratedSaleItems[0].unitPrice)
            assertEquals(99L, migratedSaleItems[0].discountValue)
            assertEquals(5001L, migratedSaleItems[0].totalLinePrice)
            assertEquals(1235L, migratedSaleItems[0].unitCost)

            val migratedPurchaseItems = kotlinx.coroutines.runBlocking { dao.getPurchaseItems(1L) }
            assertEquals(1, migratedPurchaseItems.size)
            assertEquals(1235L, migratedPurchaseItems[0].unitPrice)

            // التحقق من الجداول الأربعة الأخرى + cart_items.customPrice (NULL و non-null) مباشرة من SQLite
            val rawMigratedDb = migratedRoomDb.openHelper.writableDatabase
            rawMigratedDb.query("SELECT subtotalAmount, discountAmount, taxAmount, totalAmount, paidAmount, changeAmount FROM orders WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2550L, c.getLong(0))
                assertEquals(50L, c.getLong(1))
                assertEquals(250L, c.getLong(2))
                assertEquals(2750L, c.getLong(3))
                assertEquals(3000L, c.getLong(4))
                assertEquals(250L, c.getLong(5))
            }
            rawMigratedDb.query("SELECT unitPrice, purchasePrice, discountValue, totalLinePrice FROM order_items WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2550L, c.getLong(0))
                assertEquals(1235L, c.getLong(1))
                assertEquals(50L, c.getLong(2))
                assertEquals(2500L, c.getLong(3))
            }
            rawMigratedDb.query("SELECT subtotal, discountAmount, taxAmount, totalAmount, paidAmount, changeAmount FROM transactions WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(10000L, c.getLong(0))
                assertEquals(525L, c.getLong(1))
                assertEquals(1050L, c.getLong(2))
                assertEquals(10525L, c.getLong(3))
                assertEquals(11000L, c.getLong(4))
                assertEquals(475L, c.getLong(5))
            }
            rawMigratedDb.query("SELECT unitPrice, purchasePrice, discountAmount, totalPrice FROM transaction_items WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2550L, c.getLong(0))
                assertEquals(1235L, c.getLong(1))
                assertEquals(525L, c.getLong(2))
                assertEquals(9675L, c.getLong(3))
            }
            rawMigratedDb.query("SELECT id, customPrice, unitPrice, lineDiscount, totalLinePrice FROM cart_items ORDER BY id ASC").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.getLong(0))
                assertTrue("customPrice NULL يجب أن يبقى NULL بعد الهجرة", c.isNull(1))
                assertEquals(2550L, c.getLong(2))
                assertEquals(0L, c.getLong(3))
                assertEquals(2550L, c.getLong(4))

                assertTrue(c.moveToNext())
                assertEquals(2L, c.getLong(0))
                assertEquals(1999L, c.getLong(1))
                assertEquals(2550L, c.getLong(2))
                assertEquals(100L, c.getLong(3))
                assertEquals(3898L, c.getLong(4))
            }

            // التحقق من ترحيل v21 التلقائي بعد v20: بذر عداد الفواتير من '0019' وإنشاء جدول invoice_sequences
            assertEquals(19L, kotlinx.coroutines.runBlocking { dao.getLastInvoiceNumber() })
        } finally {
            migratedRoomDb.close()
            listOf(sourceDbFile, copyDbFile).forEach { f ->
                File(f.path).delete()
                File("${f.path}-wal").delete()
                File("${f.path}-shm").delete()
                File("${f.path}-journal").delete()
            }
        }
    }

    private fun allMigrations() = arrayOf(
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9,
        AppDatabase.MIGRATION_9_10,
        AppDatabase.MIGRATION_10_11,
        AppDatabase.MIGRATION_11_12,
        AppDatabase.MIGRATION_12_13,
        AppDatabase.MIGRATION_13_14,
        AppDatabase.MIGRATION_14_15,
        AppDatabase.MIGRATION_15_16,
        AppDatabase.MIGRATION_16_17,
        AppDatabase.MIGRATION_17_18,
        AppDatabase.MIGRATION_18_19,
        AppDatabase.MIGRATION_19_20,
        AppDatabase.MIGRATION_20_21,
        AppDatabase.MIGRATION_21_22,
        AppDatabase.MIGRATION_22_23
    )

    private fun resolveSchemaJsonFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.example.data.AppDatabase/$version.json"),
            File("app/schemas/com.example.data.AppDatabase/$version.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Exported Room schema JSON for version $version not found in ${candidates.map { it.absolutePath }}")
    }

    private fun createDatabaseFromExportedSchema(
        context: Context,
        dbName: String,
        schemaVersion: Int,
        overrideUserVersion: Int = schemaVersion,
        seedData: (SupportSQLiteDatabase) -> Unit = {}
    ) {
        context.deleteDatabase(dbName)
        val schemaJson = JSONObject(resolveSchemaJsonFile(schemaVersion).readText(Charsets.UTF_8))
        val dbObj = schemaJson.getJSONObject("database")
        val entities = dbObj.getJSONArray("entities")
        val setupQueries = dbObj.optJSONArray("setupQueries")

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(overrideUserVersion) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys=OFF")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val tableName = entity.getString("tableName")
                        val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(createSql)
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) {
                            for (j in 0 until indices.length()) {
                                val indexSql = indices.getJSONObject(j)
                                    .getString("createSql")
                                    .replace("\${TABLE_NAME}", tableName)
                                db.execSQL(indexSql)
                            }
                        }
                    }
                    if (setupQueries != null) {
                        for (i in 0 until setupQueries.length()) {
                            db.execSQL(setupQueries.getString(i))
                        }
                    }
                    seedData(db)
                    db.execSQL("PRAGMA foreign_keys=ON")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        factory.create(config).writableDatabase.close()
    }

    @Test
    fun testAllExportedSchemasMigrateCleanlyToVersion22() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema22 = resolveSchemaJsonFile(AppDatabase.CURRENT_SCHEMA_VERSION)
        assertTrue("Exported Room schema 22.json must exist on disk", schema22.exists() && schema22.length() > 0L)

        val exportedStartVersions = listOf(8, 14, 15, 16, 17, 18, 20, 22)
        for (startVersion in exportedStartVersions) {
            val dbName = "migration_validation_v${startVersion}_to_v23.db"
            createDatabaseFromExportedSchema(context, dbName, startVersion)
            val migratedRoomDb = androidx.room.Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                dbName
            )
                .addMigrations(*allMigrations())
                .allowMainThreadQueries()
                .build()
            try {
                // Opening writableDatabase and executing DAO queries triggers Room's strict full-schema validation against v23
                val seq = kotlinx.coroutines.runBlocking { migratedRoomDb.appDao().getLastInvoiceNumber() }
                if (startVersion <= 20) {
                    assertTrue("invoice_sequences must be initialized for v$startVersion->v23", seq != null && seq >= 0L)
                } else {
                    assertTrue("invoice_sequences query must succeed for v$startVersion->v23", seq == null || seq >= 0L)
                }
                val invSeq = kotlinx.coroutines.runBlocking { migratedRoomDb.stocktakingDao().getLastInventoryNumber() }
                assertTrue("inventory_sequence must be initialized for v$startVersion->v23", invSeq != null && invSeq >= 0L)
                val settings = kotlinx.coroutines.runBlocking { migratedRoomDb.appDao().getSettings() }
                assertTrue("getSettings query should succeed on v$startVersion->v23", settings == null || settings.currency.isNotBlank())
            } finally {
                migratedRoomDb.close()
                context.deleteDatabase(dbName)
            }
        }
    }

    @Test
    fun testMigration20To21_AddsCurrencyColumnAndSeedsMonotonicInvoiceSequence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_validation_v20_seeded.db"
        createDatabaseFromExportedSchema(context, dbName, 20) { db ->
            db.execSQL(
                "INSERT INTO `global_settings` (`id`, `shopName`, `shopAddress`, `shopPhone`, `taxNumber`, `receiptHeader`, `receiptFooter`, `language`, `showShopName`, `showAddress`, `showPhone`, `showTaxNumber`, `showLogo`, `showCashierName`, `showCustomerInfo`, `showDateTime`, `showBarcode`, `showPaymentMethod`, `showReceiptFooter`, `printerConnectionType`, `printerName`, `printerMac`, `printerIp`, `printerPort`, `printerPaperWidth`, `autoPrintReceipt`, `logoWidth`, `logoHeight`, `headerHeight`, `headerFontSize`, `allowNegativeStock`) " +
                    "VALUES (1, 'متجر الأفق', 'الرباط', '0600000000', '', '', '', 'AR', 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 'BLUETOOTH', '', '', '', 9100, 58, 0, 180, 180, 120, 22, 0)"
            )
            db.execSQL(
                "INSERT INTO `sales` (`id`, `invoiceNumber`, `timestamp`, `cashierName`, `customerId`, `customerName`, `totalAmount`, `discountAmount`, `discountType`, `paidAmount`, `changeAmount`, `paymentMethod`, `isVoided`) " +
                    "VALUES (1, '0041', 1000, 'admin', 0, '', 1000, 0, 'VALUE', 1000, 0, 'CASH', 0)"
            )
            db.execSQL(
                "INSERT INTO `sales` (`id`, `invoiceNumber`, `timestamp`, `cashierName`, `customerId`, `customerName`, `totalAmount`, `discountAmount`, `discountType`, `paidAmount`, `changeAmount`, `paymentMethod`, `isVoided`) " +
                    "VALUES (2, '0042', 2000, 'admin', 0, '', 2000, 0, 'VALUE', 2000, 0, 'CASH', 0)"
            )
            // Higher invoice number deleted earlier but preserved in audit_logs
            db.execSQL(
                "INSERT INTO `audit_logs` (`actionType`, `entityType`, `entityId`, `details`, `username`, `userRole`, `timestamp`, `isCritical`) " +
                    "VALUES ('DELETE', 'SALE', '0045', 'حذف فاتورة', 'admin', 'MANAGER', 3000, 1)"
            )
        }

        val migratedRoomDb = androidx.room.Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23)
            .allowMainThreadQueries()
            .build()

        try {
            val settings = kotlinx.coroutines.runBlocking { migratedRoomDb.appDao().getSettings() }
            assertEquals("MAD", settings?.currency)
            assertEquals(90, settings?.logoQuality)
            assertEquals("TEXT", settings?.headerContentType)
            assertEquals(520, settings?.headerWidth)
            assertEquals("TEXT", settings?.footerContentType)
            assertEquals(520, settings?.footerWidth)
            assertEquals(50, settings?.footerHeight)
            assertEquals(12, settings?.documentFontSize)
            val seq = kotlinx.coroutines.runBlocking { migratedRoomDb.appDao().getLastInvoiceNumber() }
            assertEquals(45L, seq)
        } finally {
            migratedRoomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration21To22_DedicatedDirectMigrationAddsPrintLayoutColumnsAndPreservesData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_dedicated_v21_to_v22.db"
        // Build an authentic version-21 database: start from exported v20 schema, apply MIGRATION_20_21 directly,
        // and set user_version = 21 with seeded store settings and invoice sequence.
        createDatabaseFromExportedSchema(context, dbName, schemaVersion = 20, overrideUserVersion = 21) { db ->
            AppDatabase.MIGRATION_20_21.migrate(db)
            db.execSQL(
                "INSERT INTO `global_settings` (`id`, `shopName`, `shopAddress`, `shopPhone`, `taxNumber`, `receiptHeader`, `receiptFooter`, `language`, `showShopName`, `showAddress`, `showPhone`, `showTaxNumber`, `showLogo`, `showCashierName`, `showCustomerInfo`, `showDateTime`, `showBarcode`, `showPaymentMethod`, `showReceiptFooter`, `printerConnectionType`, `printerName`, `printerMac`, `printerIp`, `printerPort`, `printerPaperWidth`, `autoPrintReceipt`, `logoWidth`, `logoHeight`, `headerHeight`, `headerFontSize`, `allowNegativeStock`, `currency`) " +
                    "VALUES (1, 'متجر الأطلس 21', 'مراكش', '0612345678', 'ICE-21', 'ترويسة 21', 'شكراً 21', 'FR', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 'WIFI', 'Thermal-80', '', '192.168.1.50', 9100, 80, 1, 200, 160, 110, 24, 1, 'EUR')"
            )
            db.execSQL("INSERT OR REPLACE INTO `invoice_sequences` (`id`, `lastInvoiceNumber`) VALUES (1, 128)")
        }

        // Migrate strictly from v21 -> v22 using ONLY MIGRATION_21_22
        val migratedDb = androidx.room.Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23)
            .allowMainThreadQueries()
            .build()

        try {
            val settings = kotlinx.coroutines.runBlocking { migratedDb.appDao().getSettings() }
            assertNotNull("Seeded v21 GlobalSettings row must survive MIGRATION_21_22", settings)
            // Verify all existing v21 fields are preserved intact
            assertEquals("متجر الأطلس 21", settings!!.shopName)
            assertEquals("EUR", settings.currency)
            assertEquals("WIFI", settings.printerConnectionType)
            assertEquals("192.168.1.50", settings.printerIp)
            assertEquals(80, settings.printerPaperWidth)
            assertEquals(128L, kotlinx.coroutines.runBlocking { migratedDb.appDao().getLastInvoiceNumber() })

            // Verify all 7 new v22 print-layout columns exist with exact default values
            assertEquals(90, settings.logoQuality)
            assertEquals("TEXT", settings.headerContentType)
            assertEquals(520, settings.headerWidth)
            assertEquals("TEXT", settings.footerContentType)
            assertEquals(520, settings.footerWidth)
            assertEquals(50, settings.footerHeight)
            assertEquals(12, settings.documentFontSize)

            // Idempotency check: re-running MIGRATION_21_22 on an already-v22 DB must not fail
            AppDatabase.MIGRATION_21_22.migrate(migratedDb.openHelper.writableDatabase)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration20To22_FullChainPreservesStoreDataAndValidatesSchema22() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema22File = resolveSchemaJsonFile(AppDatabase.CURRENT_SCHEMA_VERSION)
        assertTrue("Exported schema 22.json must be committed and non-empty", schema22File.exists() && schema22File.length() > 0L)
        val schema22Json = JSONObject(schema22File.readText(Charsets.UTF_8))
        assertEquals(AppDatabase.CURRENT_SCHEMA_VERSION, schema22Json.getJSONObject("database").getInt("version"))

        val dbName = "migration_explicit_v20_to_v22.db"
        createDatabaseFromExportedSchema(context, dbName, schemaVersion = 20) { db ->
            db.execSQL("INSERT INTO `categories` (`id`, `name`, `imageUri`) VALUES (1, 'عصائر', NULL)")
            db.execSQL(
                "INSERT INTO `products` (`id`, `barcode`, `name`, `description`, `categoryId`, `categoryName`, `unit`, `purchasePrice`, `sellPrice`, `wholesalePrice`, `specialPrice`, `currentStock`, `minStock`, `sku`, `taxRate`, `isTaxable`, `isActive`, `imageUri`, `isSynced`, `createdAt`, `updatedAt`) " +
                    "VALUES (10, '6112022', 'عصير برتقال', '', 1, 'عصائر', 'قطعة', 500, 850, 750, 800, 24.0, 4.0, 'SKU-2022', 0.0, 0, 1, NULL, 0, 1750000000000, 1750000000000)"
            )
            db.execSQL(
                "INSERT INTO `customers` (`id`, `name`, `phone`, `address`, `balance`, `totalPurchases`, `latitude`, `longitude`, `geoRadiusMeters`, `isGeoLocationRequired`, `customerNumber`, `barcode`) " +
                    "VALUES (5, 'زبون الهجرة', '0600998877', 'فاس', 3500, 12000, NULL, NULL, 150.0, 0, 1, 'store-1')"
            )
            db.execSQL(
                "INSERT INTO `global_settings` (`id`, `shopName`, `shopAddress`, `shopPhone`, `taxNumber`, `receiptHeader`, `receiptFooter`, `language`, `showShopName`, `showAddress`, `showPhone`, `showTaxNumber`, `showLogo`, `showCashierName`, `showCustomerInfo`, `showDateTime`, `showBarcode`, `showPaymentMethod`, `showReceiptFooter`, `printerConnectionType`, `printerName`, `printerMac`, `printerIp`, `printerPort`, `printerPaperWidth`, `autoPrintReceipt`, `logoWidth`, `logoHeight`, `headerHeight`, `headerFontSize`, `allowNegativeStock`) " +
                    "VALUES (1, 'متجر 20 إلى 22', 'طنجة', '0611223344', 'ICE-2022', 'مرحباً', 'شكراً', 'AR', 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 'BLUETOOTH', 'BT-58', '00:11:22:33:44:55', '', 9100, 58, 1, 180, 180, 120, 22, 0)"
            )
            db.execSQL(
                "INSERT INTO `sales` (`id`, `invoiceNumber`, `timestamp`, `cashierName`, `customerId`, `customerName`, `totalAmount`, `discountAmount`, `discountType`, `paidAmount`, `changeAmount`, `paymentMethod`, `isVoided`) " +
                    "VALUES (1, '0099', 1750000000000, 'admin', 5, 'زبون الهجرة', 850, 0, 'VALUE', 850, 0, 'CASH', 0)"
            )
        }

        val migratedDb = androidx.room.Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(AppDatabase.MIGRATION_20_21, AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migratedDb.appDao()
            val settings = kotlinx.coroutines.runBlocking { dao.getSettings() }
            assertNotNull(settings)
            assertEquals("متجر 20 إلى 22", settings!!.shopName)
            assertEquals("MAD", settings.currency)
            assertEquals(90, settings.logoQuality)
            assertEquals("TEXT", settings.headerContentType)
            assertEquals(520, settings.headerWidth)
            assertEquals("TEXT", settings.footerContentType)
            assertEquals(520, settings.footerWidth)
            assertEquals(50, settings.footerHeight)
            assertEquals(12, settings.documentFontSize)

            val prod = kotlinx.coroutines.runBlocking { dao.getProductById(10L) }
            assertNotNull(prod)
            assertEquals("عصير برتقال", prod!!.name)
            assertEquals(850L, prod.sellPrice)

            val customer = kotlinx.coroutines.runBlocking { dao.getCustomerById(5L) }
            assertNotNull(customer)
            assertEquals(3500L, customer!!.balance)

            val seq = kotlinx.coroutines.runBlocking { dao.getLastInvoiceNumber() }
            assertEquals(99L, seq)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration22To23_DirectMigrationCreatesStocktakingTablesAndPreservesData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema22File = resolveSchemaJsonFile(22)
        val schema23File = resolveSchemaJsonFile(AppDatabase.CURRENT_SCHEMA_VERSION)
        assertTrue("Exported schema 22.json must exist on disk", schema22File.exists() && schema22File.length() > 0L)
        assertTrue("Exported schema 23.json must exist on disk", schema23File.exists() && schema23File.length() > 0L)
        assertEquals(22, JSONObject(schema22File.readText(Charsets.UTF_8)).getJSONObject("database").getInt("version"))
        assertEquals(23, JSONObject(schema23File.readText(Charsets.UTF_8)).getJSONObject("database").getInt("version"))

        val dbName = "migration_dedicated_v22_to_v23.db"
        createDatabaseFromExportedSchema(context, dbName, schemaVersion = 22) { db ->
            db.execSQL("INSERT INTO `categories` (`id`, `name`, `imageUri`) VALUES (1, 'مواد غذائية', NULL)")
            db.execSQL(
                "INSERT INTO `products` (`id`, `barcode`, `name`, `description`, `categoryId`, `categoryName`, `unit`, `purchasePrice`, `sellPrice`, `wholesalePrice`, `specialPrice`, `currentStock`, `minStock`, `sku`, `taxRate`, `isTaxable`, `isActive`, `imageUri`, `isSynced`, `createdAt`, `updatedAt`) " +
                    "VALUES (22, '6112223', 'دقيق ممتاز', '', 1, 'مواد غذائية', 'كيس', 1500, 2100, 1900, 1950, 30.0, 5.0, 'SKU-22', 0.0, 0, 1, NULL, 0, 1760000000000, 1760000000000)"
            )
            db.execSQL("INSERT OR REPLACE INTO `invoice_sequences` (`id`, `lastInvoiceNumber`) VALUES (1, 220)")
        }

        val migratedDb = androidx.room.Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(AppDatabase.MIGRATION_22_23)
            .allowMainThreadQueries()
            .build()

        try {
            val prod = kotlinx.coroutines.runBlocking { migratedDb.appDao().getProductById(22L) }
            assertNotNull("Existing v22 product must survive MIGRATION_22_23", prod)
            assertEquals("دقيق ممتاز", prod!!.name)
            assertEquals(2100L, prod.sellPrice)
            assertEquals(220L, kotlinx.coroutines.runBlocking { migratedDb.appDao().getLastInvoiceNumber() })

            val invSeq = kotlinx.coroutines.runBlocking { migratedDb.stocktakingDao().getLastInventoryNumber() }
            assertEquals(0L, invSeq)
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
