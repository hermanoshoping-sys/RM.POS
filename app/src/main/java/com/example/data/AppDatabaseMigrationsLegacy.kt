package com.example.data

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/**
 * Legacy Room schema migrations (versions 7 -> 16).
 * Extracted from AppDatabase.kt to keep database bootstrap concise and modular.
 */
internal object AppDatabaseMigrationsLegacy {
    private const val TAG = "AppDatabaseMigrations"

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 7, toVersion = 8)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS customer_payments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    customerId INTEGER NOT NULL,
                    customerName TEXT NOT NULL,
                    amount REAL NOT NULL,
                    previousBalance REAL NOT NULL DEFAULT 0.0,
                    newBalance REAL NOT NULL DEFAULT 0.0,
                    paymentMethod TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    cashierName TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_payments_customerId ON customer_payments(customerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_payments_timestamp ON customer_payments(timestamp)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS supplier_payments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    supplierId INTEGER NOT NULL,
                    supplierName TEXT NOT NULL,
                    amount REAL NOT NULL,
                    previousDebt REAL NOT NULL DEFAULT 0.0,
                    newDebt REAL NOT NULL DEFAULT 0.0,
                    paymentMethod TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    cashierName TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_supplierId ON supplier_payments(supplierId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_timestamp ON supplier_payments(timestamp)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 8, toVersion = 9)

            db.execSQL("ALTER TABLE global_settings ADD COLUMN allowNegativeStock INTEGER NOT NULL DEFAULT 0")

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
            db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_timestamp ON audit_logs(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_actionType ON audit_logs(actionType)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_username ON audit_logs(username)")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 9, toVersion = 10)

            db.execSQL("""
                UPDATE products SET 
                    purchasePrice = (CAST(ROUND(purchasePrice * 100.0) AS INTEGER) / 100.0),
                    sellPrice = (CAST(ROUND(sellPrice * 100.0) AS INTEGER) / 100.0),
                    wholesalePrice = (CAST(ROUND(wholesalePrice * 100.0) AS INTEGER) / 100.0),
                    specialPrice = (CAST(ROUND(specialPrice * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())

            db.execSQL("""
                UPDATE customers SET 
                    balance = (CAST(ROUND(balance * 100.0) AS INTEGER) / 100.0),
                    totalPurchases = (CAST(ROUND(totalPurchases * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())

            db.execSQL("""
                UPDATE suppliers SET 
                    debtBalance = (CAST(ROUND(debtBalance * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())

            db.execSQL("""
                UPDATE purchases SET 
                    totalAmount = (CAST(ROUND(totalAmount * 100.0) AS INTEGER) / 100.0),
                    paidAmount = (CAST(ROUND(paidAmount * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())
            db.execSQL("""
                UPDATE purchase_items SET 
                    unitPrice = (CAST(ROUND(unitPrice * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())

            db.execSQL("""
                UPDATE sales SET 
                    totalAmount = (CAST(ROUND(totalAmount * 100.0) AS INTEGER) / 100.0),
                    paidAmount = (CAST(ROUND(paidAmount * 100.0) AS INTEGER) / 100.0),
                    discountAmount = (CAST(ROUND(discountAmount * 100.0) AS INTEGER) / 100.0),
                    changeAmount = (CAST(ROUND(changeAmount * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())
            db.execSQL("""
                UPDATE sale_items SET 
                    unitPrice = (CAST(ROUND(unitPrice * 100.0) AS INTEGER) / 100.0),
                    discountValue = (CAST(ROUND(discountValue * 100.0) AS INTEGER) / 100.0),
                    totalLinePrice = (CAST(ROUND(totalLinePrice * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())

            db.execSQL("""
                UPDATE customer_payments SET 
                    amount = (CAST(ROUND(amount * 100.0) AS INTEGER) / 100.0),
                    previousBalance = (CAST(ROUND(previousBalance * 100.0) AS INTEGER) / 100.0),
                    newBalance = (CAST(ROUND(newBalance * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())
            db.execSQL("""
                UPDATE supplier_payments SET 
                    amount = (CAST(ROUND(amount * 100.0) AS INTEGER) / 100.0),
                    previousDebt = (CAST(ROUND(previousDebt * 100.0) AS INTEGER) / 100.0),
                    newDebt = (CAST(ROUND(newDebt * 100.0) AS INTEGER) / 100.0)
            """.trimIndent())

            db.execSQL("""
                INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                VALUES ('FINANCIAL_MIGRATION', 'DATABASE', 'SCHEMA_V10', 'تمت هجرة كافة المبالغ المالية بنجاح إلى دقة السنتيمات الكاملة (Round to Cents * 100)', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
            """.trimIndent())
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 10, toVersion = 11)
            db.execSQL("ALTER TABLE purchases ADD COLUMN invoiceNumber TEXT NOT NULL DEFAULT ''")
            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V11', 'إضافة عمود invoiceNumber إلى جدول المشتريات purchases', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 11, toVersion = 12)
            db.execSQL("ALTER TABLE users ADD COLUMN canEditInvoice INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE users ADD COLUMN canManageInventory INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE users ADD COLUMN canManageClients INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE users ADD COLUMN canManageSuppliers INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE users ADD COLUMN canManageExpenses INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE users ADD COLUMN canManagePayments INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE users ADD COLUMN canManageSettings INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE users SET canEditInvoice = 1, canManageInventory = 1, canManageClients = 1, canManageSuppliers = 1, canManageExpenses = 1, canManagePayments = 1, canManageSettings = 1 WHERE UPPER(role) = 'MANAGER'")
            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V12', 'إضافة صلاحيات المستخدمين التفصيلية وتخصيص القوائم والميزات للمستخدمين', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 12, toVersion = 13)
            db.execSQL("ALTER TABLE customers ADD COLUMN latitude REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE customers ADD COLUMN longitude REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE customers ADD COLUMN geoRadiusMeters REAL NOT NULL DEFAULT 150.0")
            db.execSQL("ALTER TABLE customers ADD COLUMN isGeoLocationRequired INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE users ADD COLUMN enforceCustomerGeoLocation INTEGER NOT NULL DEFAULT 0")
            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V13', 'إضافة ميزة ربط الزبائن بالموقع الجغرافي وفرض التواجد بالموقع عند فتح الفاتورة', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 13, toVersion = 14)
            db.execSQL("ALTER TABLE users ADD COLUMN canEditCustomers INTEGER NOT NULL DEFAULT 1")
            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V14', 'إضافة صلاحية التحكم في تعديل بيانات العملاء بعد إنشائهم', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 14, toVersion = 15)

            db.execSQL("ALTER TABLE customers ADD COLUMN customerNumber INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE customers ADD COLUMN barcode TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_customerNumber ON customers(customerNumber)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_barcode ON customers(barcode)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS customer_sequences (
                    id INTEGER PRIMARY KEY NOT NULL,
                    lastCustomerNumber INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            var cleanShop = "rmpos"
            try {
                val settingsCursor = db.query("SELECT shopName FROM global_settings WHERE id = 1 LIMIT 1")
                if (settingsCursor.moveToFirst()) {
                    val name = settingsCursor.getString(0) ?: ""
                    val clean = name.replace("\\s+".toRegex(), "").lowercase(Locale.ROOT)
                    if (clean.isNotBlank()) cleanShop = clean
                }
                settingsCursor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching shop name in migration: ${e.message}")
            }

            var currentSeq = 0L
            try {
                val custCursor = db.query("SELECT id FROM customers ORDER BY id ASC")
                while (custCursor.moveToNext()) {
                    val cId = custCursor.getLong(0)
                    currentSeq++
                    val bCode = "$cleanShop-$currentSeq"
                    db.execSQL(
                        "UPDATE customers SET customerNumber = ?, barcode = ? WHERE id = ?",
                        arrayOf<Any>(currentSeq, bCode, cId)
                    )
                }
                custCursor.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error backfilling customers in migration: ${e.message}")
            }

            db.execSQL(
                "INSERT OR REPLACE INTO customer_sequences (id, lastCustomerNumber) VALUES (1, ?)",
                arrayOf<Any>(currentSeq)
            )

            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V15', 'إضافة الرقم التسلسلي التلقائي الفريد والباركود للزبائن وتأسيس جدول customer_sequences', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 15, toVersion = 16)
            db.execSQL("ALTER TABLE categories ADD COLUMN imageUri TEXT DEFAULT NULL")
            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V16', 'إضافة عمود imageUri لجدول categories لدعم صور عائلات المنتجات بمقاس 1080x1350 في الكاتالوج', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }
}
