package com.example.data

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Financial precision & relational integrity migrations (versions 16 -> 19).
 */
internal object AppDatabaseMigrationsFinancial {
    private const val TAG = "AppDatabaseMigrations"

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 16, toVersion = 17)
            db.execSQL("PRAGMA foreign_keys = OFF")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS customers_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    address TEXT NOT NULL,
                    balance INTEGER NOT NULL DEFAULT 0,
                    totalPurchases INTEGER NOT NULL DEFAULT 0,
                    latitude REAL,
                    longitude REAL,
                    geoRadiusMeters REAL NOT NULL DEFAULT 150.0,
                    isGeoLocationRequired INTEGER NOT NULL DEFAULT 0,
                    customerNumber INTEGER NOT NULL DEFAULT 0,
                    barcode TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO customers_new (id, name, phone, address, balance, totalPurchases, latitude, longitude, geoRadiusMeters, isGeoLocationRequired, customerNumber, barcode)
                SELECT id, name, phone, address, CAST(ROUND(balance * 100.0) AS INTEGER), CAST(ROUND(totalPurchases * 100.0) AS INTEGER), latitude, longitude, geoRadiusMeters, isGeoLocationRequired, customerNumber, barcode FROM customers
            """.trimIndent())
            db.execSQL("DROP TABLE customers")
            db.execSQL("ALTER TABLE customers_new RENAME TO customers")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_customerNumber ON customers(customerNumber)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_barcode ON customers(barcode)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS suppliers_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    email TEXT NOT NULL DEFAULT '',
                    address TEXT NOT NULL DEFAULT '',
                    debtBalance INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO suppliers_new (id, name, phone, email, address, debtBalance)
                SELECT id, name, phone, email, address, CAST(ROUND(debtBalance * 100.0) AS INTEGER) FROM suppliers
            """.trimIndent())
            db.execSQL("DROP TABLE suppliers")
            db.execSQL("ALTER TABLE suppliers_new RENAME TO suppliers")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS purchases_new (
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
                INSERT INTO purchases_new (id, timestamp, supplierId, supplierName, totalAmount, paidAmount, isVoided, invoiceNumber)
                SELECT id, timestamp, supplierId, supplierName, CAST(ROUND(totalAmount * 100.0) AS INTEGER), CAST(ROUND(paidAmount * 100.0) AS INTEGER), isVoided, invoiceNumber FROM purchases
            """.trimIndent())
            db.execSQL("DROP TABLE purchases")
            db.execSQL("ALTER TABLE purchases_new RENAME TO purchases")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sales_new (
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
                INSERT INTO sales_new (id, invoiceNumber, timestamp, cashierName, customerId, customerName, totalAmount, discountAmount, discountType, paidAmount, changeAmount, paymentMethod, isVoided)
                SELECT id, invoiceNumber, timestamp, cashierName, customerId, customerName, CAST(ROUND(totalAmount * 100.0) AS INTEGER), CAST(ROUND(discountAmount * 100.0) AS INTEGER), discountType, CAST(ROUND(paidAmount * 100.0) AS INTEGER), CAST(ROUND(changeAmount * 100.0) AS INTEGER), paymentMethod, isVoided FROM sales
            """.trimIndent())
            db.execSQL("DROP TABLE sales")
            db.execSQL("ALTER TABLE sales_new RENAME TO sales")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_invoiceNumber ON sales(invoiceNumber)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS expenses_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 0,
                    timestamp INTEGER NOT NULL,
                    category TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO expenses_new (id, title, amount, timestamp, category)
                SELECT id, title, CAST(ROUND(amount * 100.0) AS INTEGER), timestamp, category FROM expenses
            """.trimIndent())
            db.execSQL("DROP TABLE expenses")
            db.execSQL("ALTER TABLE expenses_new RENAME TO expenses")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS customer_payments_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    customerId INTEGER NOT NULL,
                    customerName TEXT NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 0,
                    previousBalance INTEGER NOT NULL DEFAULT 0,
                    newBalance INTEGER NOT NULL DEFAULT 0,
                    paymentMethod TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    cashierName TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO customer_payments_new (id, customerId, customerName, amount, previousBalance, newBalance, paymentMethod, notes, timestamp, cashierName)
                SELECT id, customerId, customerName, CAST(ROUND(amount * 100.0) AS INTEGER), CAST(ROUND(previousBalance * 100.0) AS INTEGER), CAST(ROUND(newBalance * 100.0) AS INTEGER), paymentMethod, notes, timestamp, cashierName FROM customer_payments
            """.trimIndent())
            db.execSQL("DROP TABLE customer_payments")
            db.execSQL("ALTER TABLE customer_payments_new RENAME TO customer_payments")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_payments_customerId ON customer_payments(customerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_payments_timestamp ON customer_payments(timestamp)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS supplier_payments_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    supplierId INTEGER NOT NULL,
                    supplierName TEXT NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 0,
                    previousDebt INTEGER NOT NULL DEFAULT 0,
                    newDebt INTEGER NOT NULL DEFAULT 0,
                    paymentMethod TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    cashierName TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO supplier_payments_new (id, supplierId, supplierName, amount, previousDebt, newDebt, paymentMethod, notes, timestamp, cashierName)
                SELECT id, supplierId, supplierName, CAST(ROUND(amount * 100.0) AS INTEGER), CAST(ROUND(previousDebt * 100.0) AS INTEGER), CAST(ROUND(newDebt * 100.0) AS INTEGER), paymentMethod, notes, timestamp, cashierName FROM supplier_payments
            """.trimIndent())
            db.execSQL("DROP TABLE supplier_payments")
            db.execSQL("ALTER TABLE supplier_payments_new RENAME TO supplier_payments")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_supplierId ON supplier_payments(supplierId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_timestamp ON supplier_payments(timestamp)")

            db.execSQL("PRAGMA foreign_keys = ON")

            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V17', 'تحويل كافة الأرصدة والمجاميع المالية في قاعدة البيانات إلى أعداد صحيحة بالسنتيم (INTEGER Long Cents)', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 17, toVersion = 18)
            db.execSQL("ALTER TABLE customer_payments ADD COLUMN saleId INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_payments_saleId ON customer_payments(saleId)")
            db.execSQL("ALTER TABLE supplier_payments ADD COLUMN purchaseId INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_purchaseId ON supplier_payments(purchaseId)")
            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V18', 'إضافة أعمدة saleId و purchaseId لربط الدفعات بالفواتير مباشرة دون البحث بالنص', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 18, toVersion = 19)

            db.execSQL("ALTER TABLE stock_movements ADD COLUMN referenceType TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE stock_movements ADD COLUMN referenceId INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_referenceType_referenceId ON stock_movements(referenceType, referenceId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_productId ON stock_movements(productId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sale_items_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    saleId INTEGER NOT NULL,
                    productId INTEGER NOT NULL,
                    productName TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unitPrice REAL NOT NULL,
                    discountValue REAL NOT NULL,
                    totalLinePrice REAL NOT NULL,
                    unitCost REAL NOT NULL DEFAULT 0.0,
                    FOREIGN KEY(saleId) REFERENCES sales(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO sale_items_new (id, saleId, productId, productName, quantity, unitPrice, discountValue, totalLinePrice, unitCost)
                SELECT id, saleId, productId, productName, quantity, unitPrice, discountValue, totalLinePrice, 0.0 FROM sale_items
            """.trimIndent())
            db.execSQL("DROP TABLE sale_items")
            db.execSQL("ALTER TABLE sale_items_new RENAME TO sale_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_saleId ON sale_items(saleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_productId ON sale_items(productId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS purchase_items_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    purchaseId INTEGER NOT NULL,
                    productId INTEGER NOT NULL,
                    productName TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unitPrice REAL NOT NULL,
                    FOREIGN KEY(purchaseId) REFERENCES purchases(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO purchase_items_new (id, purchaseId, productId, productName, quantity, unitPrice)
                SELECT id, purchaseId, productId, productName, quantity, unitPrice FROM purchase_items
            """.trimIndent())
            db.execSQL("DROP TABLE purchase_items")
            db.execSQL("ALTER TABLE purchase_items_new RENAME TO purchase_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_items_purchaseId ON purchase_items(purchaseId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_items_productId ON purchase_items(productId)")

            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V19', 'إضافة مراجع صريحة لحركات المخزون، والمفاتيح الأجنبية، وحقل تكلفة الوحدة لحساب الأرباح', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }
}
