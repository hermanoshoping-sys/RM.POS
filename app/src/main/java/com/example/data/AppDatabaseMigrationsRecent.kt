package com.example.data

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Recent Room schema migrations (versions 19 -> 22) and full ordered migration registry.
 */
internal object AppDatabaseMigrationsRecent {
    private const val TAG = "AppDatabaseMigrations"

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 19, toVersion = 20)
            db.execSQL("PRAGMA foreign_keys = OFF")

            // 1. products
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS products_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    barcode TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    categoryId INTEGER NOT NULL,
                    categoryName TEXT NOT NULL,
                    unit TEXT NOT NULL,
                    purchasePrice INTEGER NOT NULL DEFAULT 0,
                    sellPrice INTEGER NOT NULL DEFAULT 0,
                    wholesalePrice INTEGER NOT NULL DEFAULT 0,
                    specialPrice INTEGER NOT NULL DEFAULT 0,
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
                INSERT INTO products_new (
                    id, barcode, name, description, categoryId, categoryName, unit,
                    purchasePrice, sellPrice, wholesalePrice, specialPrice,
                    currentStock, minStock, sku, taxRate, isTaxable, isActive,
                    imageUri, isSynced, createdAt, updatedAt
                )
                SELECT
                    id, barcode, name, description, categoryId, categoryName, unit,
                    CAST(ROUND(purchasePrice * 100.0) AS INTEGER),
                    CAST(ROUND(sellPrice * 100.0) AS INTEGER),
                    CAST(ROUND(wholesalePrice * 100.0) AS INTEGER),
                    CAST(ROUND(specialPrice * 100.0) AS INTEGER),
                    currentStock, minStock, sku, taxRate, isTaxable, isActive,
                    imageUri, isSynced, createdAt, updatedAt
                FROM products
            """.trimIndent())
            db.execSQL("DROP TABLE products")
            db.execSQL("ALTER TABLE products_new RENAME TO products")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_barcode ON products(barcode)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_categoryId ON products(categoryId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_name ON products(name)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_sku ON products(sku)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_isSynced ON products(isSynced)")

            // 2. purchase_items
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS purchase_items_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    purchaseId INTEGER NOT NULL,
                    productId INTEGER NOT NULL,
                    productName TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unitPrice INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(purchaseId) REFERENCES purchases(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO purchase_items_new (id, purchaseId, productId, productName, quantity, unitPrice)
                SELECT id, purchaseId, productId, productName, quantity, CAST(ROUND(unitPrice * 100.0) AS INTEGER) FROM purchase_items
            """.trimIndent())
            db.execSQL("DROP TABLE purchase_items")
            db.execSQL("ALTER TABLE purchase_items_new RENAME TO purchase_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_items_purchaseId ON purchase_items(purchaseId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_items_productId ON purchase_items(productId)")

            // 3. sale_items
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sale_items_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    saleId INTEGER NOT NULL,
                    productId INTEGER NOT NULL,
                    productName TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unitPrice INTEGER NOT NULL DEFAULT 0,
                    discountValue INTEGER NOT NULL DEFAULT 0,
                    totalLinePrice INTEGER NOT NULL DEFAULT 0,
                    unitCost INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(saleId) REFERENCES sales(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO sale_items_new (id, saleId, productId, productName, quantity, unitPrice, discountValue, totalLinePrice, unitCost)
                SELECT
                    id, saleId, productId, productName, quantity,
                    CAST(ROUND(unitPrice * 100.0) AS INTEGER),
                    CAST(ROUND(discountValue * 100.0) AS INTEGER),
                    CAST(ROUND(totalLinePrice * 100.0) AS INTEGER),
                    CAST(ROUND(unitCost * 100.0) AS INTEGER)
                FROM sale_items
            """.trimIndent())
            db.execSQL("DROP TABLE sale_items")
            db.execSQL("ALTER TABLE sale_items_new RENAME TO sale_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_saleId ON sale_items(saleId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_items_productId ON sale_items(productId)")

            // 4. orders
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS orders_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    orderNumber TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    customerId INTEGER,
                    customerName TEXT NOT NULL,
                    cashierName TEXT NOT NULL,
                    subtotalAmount INTEGER NOT NULL DEFAULT 0,
                    discountAmount INTEGER NOT NULL DEFAULT 0,
                    discountType TEXT NOT NULL,
                    taxAmount INTEGER NOT NULL DEFAULT 0,
                    totalAmount INTEGER NOT NULL DEFAULT 0,
                    paidAmount INTEGER NOT NULL DEFAULT 0,
                    changeAmount INTEGER NOT NULL DEFAULT 0,
                    paymentMethod TEXT NOT NULL,
                    status TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    isVoided INTEGER NOT NULL,
                    isSynced INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO orders_new (
                    id, orderNumber, timestamp, customerId, customerName, cashierName,
                    subtotalAmount, discountAmount, discountType, taxAmount, totalAmount,
                    paidAmount, changeAmount, paymentMethod, status, notes, isVoided, isSynced
                )
                SELECT
                    id, orderNumber, timestamp, customerId, customerName, cashierName,
                    CAST(ROUND(subtotalAmount * 100.0) AS INTEGER),
                    CAST(ROUND(discountAmount * 100.0) AS INTEGER),
                    discountType,
                    CAST(ROUND(taxAmount * 100.0) AS INTEGER),
                    CAST(ROUND(totalAmount * 100.0) AS INTEGER),
                    CAST(ROUND(paidAmount * 100.0) AS INTEGER),
                    CAST(ROUND(changeAmount * 100.0) AS INTEGER),
                    paymentMethod, status, notes, isVoided, isSynced
                FROM orders
            """.trimIndent())
            db.execSQL("DROP TABLE orders")
            db.execSQL("ALTER TABLE orders_new RENAME TO orders")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_orders_orderNumber ON orders(orderNumber)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_timestamp ON orders(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_customerId ON orders(customerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_orders_status ON orders(status)")

            // 5. order_items
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS order_items_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    orderId INTEGER NOT NULL,
                    productId INTEGER NOT NULL,
                    barcode TEXT NOT NULL,
                    productName TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unitPrice INTEGER NOT NULL DEFAULT 0,
                    purchasePrice INTEGER NOT NULL DEFAULT 0,
                    discountValue INTEGER NOT NULL DEFAULT 0,
                    totalLinePrice INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(orderId) REFERENCES orders(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO order_items_new (id, orderId, productId, barcode, productName, quantity, unitPrice, purchasePrice, discountValue, totalLinePrice)
                SELECT
                    id, orderId, productId, barcode, productName, quantity,
                    CAST(ROUND(unitPrice * 100.0) AS INTEGER),
                    CAST(ROUND(purchasePrice * 100.0) AS INTEGER),
                    CAST(ROUND(discountValue * 100.0) AS INTEGER),
                    CAST(ROUND(totalLinePrice * 100.0) AS INTEGER)
                FROM order_items
            """.trimIndent())
            db.execSQL("DROP TABLE order_items")
            db.execSQL("ALTER TABLE order_items_new RENAME TO order_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_items_orderId ON order_items(orderId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_items_productId ON order_items(productId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_order_items_barcode ON order_items(barcode)")

            // 6. transactions
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS transactions_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    transactionNumber TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    cashierName TEXT NOT NULL,
                    customerId INTEGER,
                    customerName TEXT NOT NULL,
                    subtotal INTEGER NOT NULL DEFAULT 0,
                    discountAmount INTEGER NOT NULL DEFAULT 0,
                    discountType TEXT NOT NULL,
                    taxAmount INTEGER NOT NULL DEFAULT 0,
                    totalAmount INTEGER NOT NULL DEFAULT 0,
                    paidAmount INTEGER NOT NULL DEFAULT 0,
                    changeAmount INTEGER NOT NULL DEFAULT 0,
                    paymentMethod TEXT NOT NULL,
                    type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    isVoided INTEGER NOT NULL,
                    isSynced INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO transactions_new (
                    id, transactionNumber, timestamp, cashierName, customerId, customerName,
                    subtotal, discountAmount, discountType, taxAmount, totalAmount,
                    paidAmount, changeAmount, paymentMethod, type, status, notes,
                    isVoided, isSynced, createdAt, updatedAt
                )
                SELECT
                    id, transactionNumber, timestamp, cashierName, customerId, customerName,
                    CAST(ROUND(subtotal * 100.0) AS INTEGER),
                    CAST(ROUND(discountAmount * 100.0) AS INTEGER),
                    discountType,
                    CAST(ROUND(taxAmount * 100.0) AS INTEGER),
                    CAST(ROUND(totalAmount * 100.0) AS INTEGER),
                    CAST(ROUND(paidAmount * 100.0) AS INTEGER),
                    CAST(ROUND(changeAmount * 100.0) AS INTEGER),
                    paymentMethod, type, status, notes,
                    isVoided, isSynced, createdAt, updatedAt
                FROM transactions
            """.trimIndent())
            db.execSQL("DROP TABLE transactions")
            db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_transactionNumber ON transactions(transactionNumber)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_customerId ON transactions(customerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type ON transactions(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_status ON transactions(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_isSynced ON transactions(isSynced)")

            // 7. transaction_items
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS transaction_items_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    transactionId INTEGER NOT NULL,
                    productId INTEGER NOT NULL,
                    barcode TEXT NOT NULL,
                    productName TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unitPrice INTEGER NOT NULL DEFAULT 0,
                    purchasePrice INTEGER NOT NULL DEFAULT 0,
                    discountAmount INTEGER NOT NULL DEFAULT 0,
                    taxRate REAL NOT NULL,
                    totalPrice INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(transactionId) REFERENCES transactions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO transaction_items_new (id, transactionId, productId, barcode, productName, quantity, unitPrice, purchasePrice, discountAmount, taxRate, totalPrice)
                SELECT
                    id, transactionId, productId, barcode, productName, quantity,
                    CAST(ROUND(unitPrice * 100.0) AS INTEGER),
                    CAST(ROUND(purchasePrice * 100.0) AS INTEGER),
                    CAST(ROUND(discountAmount * 100.0) AS INTEGER),
                    taxRate,
                    CAST(ROUND(totalPrice * 100.0) AS INTEGER)
                FROM transaction_items
            """.trimIndent())
            db.execSQL("DROP TABLE transaction_items")
            db.execSQL("ALTER TABLE transaction_items_new RENAME TO transaction_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_items_transactionId ON transaction_items(transactionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_items_productId ON transaction_items(productId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_items_barcode ON transaction_items(barcode)")

            // 8. cart_items
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cart_items_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    cartSessionId TEXT NOT NULL,
                    productId INTEGER NOT NULL,
                    barcode TEXT NOT NULL,
                    productName TEXT NOT NULL,
                    categoryName TEXT NOT NULL,
                    unit TEXT NOT NULL,
                    quantity REAL NOT NULL,
                    unitPrice INTEGER NOT NULL DEFAULT 0,
                    purchasePrice INTEGER NOT NULL DEFAULT 0,
                    customPrice INTEGER,
                    lineDiscount INTEGER NOT NULL DEFAULT 0,
                    totalLinePrice INTEGER NOT NULL DEFAULT 0,
                    notes TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO cart_items_new (
                    id, cartSessionId, productId, barcode, productName, categoryName, unit,
                    quantity, unitPrice, purchasePrice, customPrice, lineDiscount, totalLinePrice,
                    notes, timestamp
                )
                SELECT
                    id, cartSessionId, productId, barcode, productName, categoryName, unit,
                    quantity,
                    CAST(ROUND(unitPrice * 100.0) AS INTEGER),
                    CAST(ROUND(purchasePrice * 100.0) AS INTEGER),
                    CASE WHEN customPrice IS NULL THEN NULL ELSE CAST(ROUND(customPrice * 100.0) AS INTEGER) END,
                    CAST(ROUND(lineDiscount * 100.0) AS INTEGER),
                    CAST(ROUND(totalLinePrice * 100.0) AS INTEGER),
                    notes, timestamp
                FROM cart_items
            """.trimIndent())
            db.execSQL("DROP TABLE cart_items")
            db.execSQL("ALTER TABLE cart_items_new RENAME TO cart_items")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cart_items_cartSessionId ON cart_items(cartSessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cart_items_productId ON cart_items(productId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cart_items_barcode ON cart_items(barcode)")

            db.execSQL("PRAGMA foreign_keys = ON")

            try {
                db.execSQL("""
                    INSERT INTO audit_logs (actionType, entityType, entityId, details, username, userRole, timestamp, isCritical)
                    VALUES ('SCHEMA_MIGRATION', 'DATABASE', 'SCHEMA_V20', 'توحيد كافة المبالغ المالية المتبقية في قاعدة البيانات إلى أعداد صحيحة بالسنتيم (INTEGER Long Cents)', 'SYSTEM', 'SYSTEM', strftime('%s','now') * 1000, 0)
                """.trimIndent())
            } catch (e: Exception) {
                Log.i(TAG, "Audit log not recorded in migration: ${e.message}")
            }
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, 20, 21)

            var hasCurrencyCol = false
            db.query("PRAGMA table_info(`global_settings`)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0 && cursor.getString(nameIdx) == "currency") {
                        hasCurrencyCol = true
                        break
                    }
                }
            }
            if (!hasCurrencyCol) {
                db.execSQL("ALTER TABLE global_settings ADD COLUMN currency TEXT NOT NULL DEFAULT 'MAD'")
            }

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `invoice_sequences` (`id` INTEGER NOT NULL, `lastInvoiceNumber` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )

            var highestInvoiceNumber = 0L
            val numericRegex = Regex("^\\d{1,8}$")

            db.query("SELECT invoiceNumber FROM sales").use { cursor ->
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(0)?.trim().orEmpty()
                    if (numericRegex.matches(raw)) {
                        val parsed = raw.toLongOrNull() ?: 0L
                        if (parsed > highestInvoiceNumber) highestInvoiceNumber = parsed
                    }
                }
            }

            db.query("SELECT entityId FROM audit_logs WHERE entityType = 'SALE'").use { cursor ->
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(0)?.trim().orEmpty()
                    if (numericRegex.matches(raw)) {
                        val parsed = raw.toLongOrNull() ?: 0L
                        if (parsed > highestInvoiceNumber) highestInvoiceNumber = parsed
                    }
                }
            }

            db.execSQL(
                "INSERT OR REPLACE INTO `invoice_sequences` (`id`, `lastInvoiceNumber`) VALUES (1, $highestInvoiceNumber)"
            )
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, 21, 22)
            val existingColumns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`global_settings`)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) existingColumns.add(cursor.getString(nameIdx))
                }
            }
            val columnsToAdd = listOf(
                "logoQuality" to "INTEGER NOT NULL DEFAULT 90",
                "headerContentType" to "TEXT NOT NULL DEFAULT 'TEXT'",
                "headerWidth" to "INTEGER NOT NULL DEFAULT 520",
                "footerContentType" to "TEXT NOT NULL DEFAULT 'TEXT'",
                "footerWidth" to "INTEGER NOT NULL DEFAULT 520",
                "footerHeight" to "INTEGER NOT NULL DEFAULT 50",
                "documentFontSize" to "INTEGER NOT NULL DEFAULT 12"
            )
            for ((colName, colDef) in columnsToAdd) {
                if (colName !in existingColumns) {
                    db.execSQL("ALTER TABLE global_settings ADD COLUMN $colName $colDef")
                }
            }
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppDatabase.backupDatabaseBeforeMigration(db, fromVersion = 22, toVersion = 23)

            // 1. inventories
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `inventories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `reference` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `scopeSummary` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdBy` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    `approvedBy` TEXT,
                    `duplicateScanMode` TEXT NOT NULL,
                    `totalItemsCount` INTEGER NOT NULL,
                    `countedItemsCount` INTEGER NOT NULL,
                    `discrepancyItemsCount` INTEGER NOT NULL,
                    `totalShortageValueCents` INTEGER NOT NULL,
                    `totalSurplusValueCents` INTEGER NOT NULL,
                    `netDiscrepancyValueCents` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventories_reference` ON `inventories` (`reference`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventories_status` ON `inventories` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventories_createdAt` ON `inventories` (`createdAt`)")

            // 2. inventory_items
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `inventory_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `inventoryId` INTEGER NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `productName` TEXT NOT NULL,
                    `barcode` TEXT NOT NULL,
                    `sku` TEXT NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    `categoryName` TEXT NOT NULL,
                    `unit` TEXT NOT NULL,
                    `imageUri` TEXT,
                    `expectedQuantity` REAL NOT NULL,
                    `systemStockAtCount` REAL,
                    `countedQuantity` REAL,
                    `difference` REAL NOT NULL,
                    `unitCostCents` INTEGER NOT NULL,
                    `unitSellPriceCents` INTEGER NOT NULL,
                    `discrepancyValueCents` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `countedAt` INTEGER,
                    `countedBy` TEXT,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`inventoryId`) REFERENCES `inventories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_items_inventoryId_productId` ON `inventory_items` (`inventoryId`, `productId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_inventoryId_status` ON `inventory_items` (`inventoryId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_inventoryId_barcode` ON `inventory_items` (`inventoryId`, `barcode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_productId` ON `inventory_items` (`productId`)")

            // 3. inventory_adjustments
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `inventory_adjustments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `inventoryId` INTEGER NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `productName` TEXT NOT NULL,
                    `barcode` TEXT NOT NULL,
                    `unit` TEXT NOT NULL,
                    `snapshotExpectedQuantity` REAL NOT NULL,
                    `systemStockAtCount` REAL NOT NULL,
                    `countedQuantity` REAL NOT NULL,
                    `previousQuantity` REAL NOT NULL,
                    `adjustedQuantity` REAL NOT NULL,
                    `difference` REAL NOT NULL,
                    `concurrentDeltaAfterCount` REAL NOT NULL,
                    `unitCostCents` INTEGER NOT NULL,
                    `financialImpactCents` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL,
                    `approvedBy` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`inventoryId`) REFERENCES `inventories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_adjustments_inventoryId_productId` ON `inventory_adjustments` (`inventoryId`, `productId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_adjustments_productId` ON `inventory_adjustments` (`productId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_adjustments_createdAt` ON `inventory_adjustments` (`createdAt`)")

            // 4. inventory_sequence
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `inventory_sequence` (`id` INTEGER NOT NULL, `lastInventoryNumber` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `inventory_sequence` (`id`, `lastInventoryNumber`) VALUES (1, 0)"
            )
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        AppDatabaseMigrationsLegacy.MIGRATION_7_8,
        AppDatabaseMigrationsLegacy.MIGRATION_8_9,
        AppDatabaseMigrationsLegacy.MIGRATION_9_10,
        AppDatabaseMigrationsLegacy.MIGRATION_10_11,
        AppDatabaseMigrationsLegacy.MIGRATION_11_12,
        AppDatabaseMigrationsLegacy.MIGRATION_12_13,
        AppDatabaseMigrationsLegacy.MIGRATION_13_14,
        AppDatabaseMigrationsLegacy.MIGRATION_14_15,
        AppDatabaseMigrationsLegacy.MIGRATION_15_16,
        AppDatabaseMigrationsFinancial.MIGRATION_16_17,
        AppDatabaseMigrationsFinancial.MIGRATION_17_18,
        AppDatabaseMigrationsFinancial.MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23
    )
}
