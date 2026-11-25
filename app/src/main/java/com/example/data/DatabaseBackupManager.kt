package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.util.cents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Full SQLite Database Backup & Restore to Local Storage & Android Share/SAF.
 */
object DatabaseBackupManager {
    private const val TAG = "DatabaseBackupManager"

    /**
     * Builds a comprehensive JSON backup representing all store tables.
     */
    suspend fun createBackupJson(
        db: AppDatabase,
        linkedEmail: String,
        linkedPhone: String
    ): String = withContext(Dispatchers.IO) {
        val dao = db.appDao()
        val root = JSONObject()

        val maxInvFromSales = dao.getAllInvoiceNumbers().mapNotNull { inv ->
            val clean = inv.trim()
            if (clean.matches(Regex("^\\d{1,8}$"))) clean.toLongOrNull() else null
        }.maxOrNull() ?: 0L
        val seqCounter = dao.getLastInvoiceNumber() ?: 0L
        val maxInvNum = maxOf(seqCounter, maxInvFromSales)

        // Metadata
        val meta = JSONObject().apply {
            put("app", "SupermarketPOS")
            put("version", "2.2")
            put("schemaVersion", AppDatabase.CURRENT_SCHEMA_VERSION)
            put("moneyUnit", "CENTS")
            put("timestamp", System.currentTimeMillis())
            put("formattedDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            put("linkedEmail", linkedEmail)
            put("linkedPhone", linkedPhone)
            put("store", dao.getSettings()?.shopName ?: "Supermarket POS")
            put("lastInvoiceSequence", maxInvNum)
        }
        root.put("metadata", meta)

        // 1. Categories
        val catsArray = JSONArray()
        dao.getAllCategoriesList().forEach { c ->
            val o = JSONObject()
            o.put("id", c.id)
            o.put("name", c.name)
            if (c.imageUri != null) o.put("imageUri", c.imageUri) else o.put("imageUri", JSONObject.NULL)
            catsArray.put(o)
        }
        root.put("categories", catsArray)

        // 2. Products
        val prodsArray = JSONArray()
        dao.getAllProductsList().forEach { p ->
            val o = JSONObject()
            o.put("id", p.id)
            o.put("barcode", p.barcode)
            o.put("name", p.name)
            o.put("description", p.description)
            o.put("categoryId", p.categoryId)
            o.put("categoryName", p.categoryName)
            o.put("unit", p.unit)
            o.put("purchasePrice", p.purchasePrice)
            o.put("sellPrice", p.sellPrice)
            o.put("wholesalePrice", p.wholesalePrice)
            o.put("specialPrice", p.specialPrice)
            o.put("currentStock", p.currentStock)
            o.put("minStock", p.minStock)
            prodsArray.put(o)
        }
        root.put("products", prodsArray)

        // 3. Customers
        val custArray = JSONArray()
        dao.getAllCustomersList().forEach { c ->
            val o = JSONObject()
            o.put("id", c.id)
            o.put("name", c.name)
            o.put("phone", c.phone)
            o.put("address", c.address)
            o.put("balance", c.balance)
            o.put("totalPurchases", c.totalPurchases)
            if (c.latitude != null) o.put("latitude", c.latitude) else o.put("latitude", JSONObject.NULL)
            if (c.longitude != null) o.put("longitude", c.longitude) else o.put("longitude", JSONObject.NULL)
            o.put("geoRadiusMeters", c.geoRadiusMeters)
            o.put("isGeoLocationRequired", c.isGeoLocationRequired)
            o.put("customerNumber", c.customerNumber)
            o.put("barcode", c.barcode)
            custArray.put(o)
        }
        root.put("customers", custArray)

        // 4. Suppliers
        val supArray = JSONArray()
        dao.getAllSuppliersList().forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("phone", s.phone)
            o.put("email", s.email)
            o.put("address", s.address)
            o.put("debtBalance", s.debtBalance)
            supArray.put(o)
        }
        root.put("suppliers", supArray)

        // 5. Sales & Sale Items
        val salesArray = JSONArray()
        dao.getAllSalesList().forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("invoiceNumber", s.invoiceNumber)
            o.put("timestamp", s.timestamp)
            o.put("cashierName", s.cashierName)
            o.put("customerId", s.customerId)
            o.put("customerName", s.customerName)
            o.put("totalAmount", s.totalAmount)
            o.put("discountAmount", s.discountAmount)
            o.put("discountType", s.discountType)
            o.put("paidAmount", s.paidAmount)
            o.put("changeAmount", s.changeAmount)
            o.put("paymentMethod", s.paymentMethod)
            o.put("isVoided", s.isVoided)
            salesArray.put(o)
        }
        root.put("sales", salesArray)

        val saleItemsArray = JSONArray()
        dao.getAllSaleItemsList().forEach { si ->
            val o = JSONObject()
            o.put("id", si.id)
            o.put("saleId", si.saleId)
            o.put("productId", si.productId)
            o.put("productName", si.productName)
            o.put("quantity", si.quantity)
            o.put("unitPrice", si.unitPrice)
            o.put("unitCost", si.unitCost)
            o.put("discountValue", si.discountValue)
            o.put("totalLinePrice", si.totalLinePrice)
            saleItemsArray.put(o)
        }
        root.put("sale_items", saleItemsArray)

        // 6. Purchases & Purchase Items
        val purchasesArray = JSONArray()
        dao.getAllPurchasesList().forEach { p ->
            val o = JSONObject()
            o.put("id", p.id)
            o.put("timestamp", p.timestamp)
            o.put("supplierId", p.supplierId)
            o.put("supplierName", p.supplierName)
            o.put("totalAmount", p.totalAmount)
            o.put("paidAmount", p.paidAmount)
            o.put("isVoided", p.isVoided)
            o.put("invoiceNumber", p.invoiceNumber)
            purchasesArray.put(o)
        }
        root.put("purchases", purchasesArray)

        val purchaseItemsArray = JSONArray()
        dao.getAllPurchaseItemsList().forEach { pi ->
            val o = JSONObject()
            o.put("id", pi.id)
            o.put("purchaseId", pi.purchaseId)
            o.put("productId", pi.productId)
            o.put("productName", pi.productName)
            o.put("quantity", pi.quantity)
            o.put("unitPrice", pi.unitPrice)
            purchaseItemsArray.put(o)
        }
        root.put("purchase_items", purchaseItemsArray)

        // 7. Expenses
        val expArray = JSONArray()
        dao.getAllExpensesList().forEach { e ->
            val o = JSONObject()
            o.put("id", e.id)
            o.put("title", e.title)
            o.put("amount", e.amount)
            o.put("timestamp", e.timestamp)
            o.put("category", e.category)
            expArray.put(o)
        }
        root.put("expenses", expArray)

        // 8. Global Settings
        val set = dao.getSettings()
        if (set != null) {
            val sObj = JSONObject()
            sObj.put("shopName", set.shopName)
            sObj.put("shopAddress", set.shopAddress)
            sObj.put("shopPhone", set.shopPhone)
            sObj.put("taxNumber", set.taxNumber)
            sObj.put("receiptHeader", set.receiptHeader)
            sObj.put("receiptFooter", set.receiptFooter)
            sObj.put("language", set.language)
            sObj.put("showShopName", set.showShopName)
            sObj.put("showAddress", set.showAddress)
            sObj.put("showPhone", set.showPhone)
            sObj.put("showTaxNumber", set.showTaxNumber)
            sObj.put("showLogo", set.showLogo)
            sObj.put("showCashierName", set.showCashierName)
            sObj.put("showCustomerInfo", set.showCustomerInfo)
            sObj.put("showDateTime", set.showDateTime)
            sObj.put("showBarcode", set.showBarcode)
            sObj.put("showPaymentMethod", set.showPaymentMethod)
            sObj.put("showReceiptFooter", set.showReceiptFooter)
            sObj.put("printerConnectionType", set.printerConnectionType)
            sObj.put("printerName", set.printerName)
            sObj.put("printerMac", set.printerMac)
            sObj.put("printerIp", set.printerIp)
            sObj.put("printerPort", set.printerPort)
            sObj.put("printerPaperWidth", set.printerPaperWidth)
            sObj.put("autoPrintReceipt", set.autoPrintReceipt)
            sObj.put("logoWidth", set.logoWidth)
            sObj.put("logoHeight", set.logoHeight)
            sObj.put("logoQuality", set.logoQuality)
            sObj.put("headerHeight", set.headerHeight)
            sObj.put("headerFontSize", set.headerFontSize)
            sObj.put("headerContentType", set.headerContentType)
            sObj.put("headerWidth", set.headerWidth)
            sObj.put("footerContentType", set.footerContentType)
            sObj.put("footerWidth", set.footerWidth)
            sObj.put("footerHeight", set.footerHeight)
            sObj.put("documentFontSize", set.documentFontSize)
            sObj.put("currency", set.currency)
            root.put("settings", sObj)
        }

        // 9. Users & Credentials
        val usersArray = JSONArray()
        dao.getAllUsersList().forEach { u ->
            val o = JSONObject()
            o.put("id", u.id)
            o.put("username", u.username)
            val safeHash = if (com.example.security.PasswordHasher.isHashed(u.passwordHash)) {
                u.passwordHash
            } else if (u.passwordHash.isNotBlank()) {
                com.example.security.PasswordHasher.hashAsync(u.passwordHash)
            } else {
                ""
            }
            o.put("passwordHash", safeHash)
            o.put("role", u.role)
            o.put("canSell", u.canSell)
            o.put("canPurchase", u.canPurchase)
            o.put("canReports", u.canReports)
            o.put("canModifyPrice", u.canModifyPrice)
            o.put("canDeleteInvoice", u.canDeleteInvoice)
            o.put("canManageUsers", u.canManageUsers)
            o.put("canEditInvoice", u.canEditInvoice)
            o.put("canManageInventory", u.canManageInventory)
            o.put("canManageClients", u.canManageClients)
            o.put("canEditCustomers", u.canEditCustomers)
            o.put("canManageSuppliers", u.canManageSuppliers)
            o.put("canManageExpenses", u.canManageExpenses)
            o.put("canManagePayments", u.canManagePayments)
            o.put("canManageSettings", u.canManageSettings)
            o.put("enforceCustomerGeoLocation", u.enforceCustomerGeoLocation)
            usersArray.put(o)
        }
        root.put("users", usersArray)

        // 10. Customer Payments
        val custPaymentsArray = JSONArray()
        dao.getAllCustomerPaymentsList().forEach { cp ->
            val o = JSONObject()
            o.put("id", cp.id)
            o.put("saleId", cp.saleId)
            o.put("customerId", cp.customerId)
            o.put("customerName", cp.customerName)
            o.put("amount", cp.amount)
            o.put("previousBalance", cp.previousBalance)
            o.put("newBalance", cp.newBalance)
            o.put("paymentMethod", cp.paymentMethod)
            o.put("notes", cp.notes)
            o.put("timestamp", cp.timestamp)
            o.put("cashierName", cp.cashierName)
            custPaymentsArray.put(o)
        }
        root.put("customerPayments", custPaymentsArray)

        // 11. Supplier Payments
        val supPaymentsArray = JSONArray()
        dao.getAllSupplierPaymentsList().forEach { sp ->
            val o = JSONObject()
            o.put("id", sp.id)
            o.put("purchaseId", sp.purchaseId)
            o.put("supplierId", sp.supplierId)
            o.put("supplierName", sp.supplierName)
            o.put("amount", sp.amount)
            o.put("previousDebt", sp.previousDebt)
            o.put("newDebt", sp.newDebt)
            o.put("paymentMethod", sp.paymentMethod)
            o.put("notes", sp.notes)
            o.put("timestamp", sp.timestamp)
            o.put("cashierName", sp.cashierName)
            supPaymentsArray.put(o)
        }
        root.put("supplierPayments", supPaymentsArray)

        // 12. Stocktaking Inventories, Items & Adjustments (v23+)
        val stockDao = db.stocktakingDao()
        val inventoriesArray = JSONArray()
        stockDao.getAllInventoriesList().forEach { inv ->
            val o = JSONObject()
            o.put("id", inv.id)
            o.put("reference", inv.reference)
            o.put("title", inv.title)
            o.put("type", inv.type)
            o.put("status", inv.status)
            o.put("scopeSummary", inv.scopeSummary)
            o.put("notes", inv.notes)
            o.put("createdBy", inv.createdBy)
            o.put("createdAt", inv.createdAt)
            o.put("updatedAt", inv.updatedAt)
            if (inv.completedAt != null) o.put("completedAt", inv.completedAt) else o.put("completedAt", JSONObject.NULL)
            if (inv.approvedBy != null) o.put("approvedBy", inv.approvedBy) else o.put("approvedBy", JSONObject.NULL)
            o.put("duplicateScanMode", inv.duplicateScanMode)
            o.put("totalItemsCount", inv.totalItemsCount)
            o.put("countedItemsCount", inv.countedItemsCount)
            o.put("discrepancyItemsCount", inv.discrepancyItemsCount)
            o.put("totalShortageValueCents", inv.totalShortageValueCents)
            o.put("totalSurplusValueCents", inv.totalSurplusValueCents)
            o.put("netDiscrepancyValueCents", inv.netDiscrepancyValueCents)
            inventoriesArray.put(o)
        }
        root.put("inventories", inventoriesArray)

        val invItemsArray = JSONArray()
        stockDao.getAllInventoryItemsList().forEach { itItem ->
            val o = JSONObject()
            o.put("id", itItem.id)
            o.put("inventoryId", itItem.inventoryId)
            o.put("productId", itItem.productId)
            o.put("productName", itItem.productName)
            o.put("barcode", itItem.barcode)
            o.put("sku", itItem.sku)
            o.put("categoryId", itItem.categoryId)
            o.put("categoryName", itItem.categoryName)
            o.put("unit", itItem.unit)
            if (itItem.imageUri != null) o.put("imageUri", itItem.imageUri) else o.put("imageUri", JSONObject.NULL)
            o.put("expectedQuantity", itItem.expectedQuantity)
            if (itItem.systemStockAtCount != null) o.put("systemStockAtCount", itItem.systemStockAtCount) else o.put("systemStockAtCount", JSONObject.NULL)
            if (itItem.countedQuantity != null) o.put("countedQuantity", itItem.countedQuantity) else o.put("countedQuantity", JSONObject.NULL)
            o.put("difference", itItem.difference)
            o.put("unitCostCents", itItem.unitCostCents)
            o.put("unitSellPriceCents", itItem.unitSellPriceCents)
            o.put("discrepancyValueCents", itItem.discrepancyValueCents)
            o.put("status", itItem.status)
            o.put("notes", itItem.notes)
            if (itItem.countedAt != null) o.put("countedAt", itItem.countedAt) else o.put("countedAt", JSONObject.NULL)
            if (itItem.countedBy != null) o.put("countedBy", itItem.countedBy) else o.put("countedBy", JSONObject.NULL)
            o.put("updatedAt", itItem.updatedAt)
            invItemsArray.put(o)
        }
        root.put("inventory_items", invItemsArray)

        val invAdjArray = JSONArray()
        stockDao.getAllAdjustmentsList().forEach { adj ->
            val o = JSONObject()
            o.put("id", adj.id)
            o.put("inventoryId", adj.inventoryId)
            o.put("productId", adj.productId)
            o.put("productName", adj.productName)
            o.put("barcode", adj.barcode)
            o.put("unit", adj.unit)
            o.put("snapshotExpectedQuantity", adj.snapshotExpectedQuantity)
            o.put("systemStockAtCount", adj.systemStockAtCount)
            o.put("countedQuantity", adj.countedQuantity)
            o.put("previousQuantity", adj.previousQuantity)
            o.put("adjustedQuantity", adj.adjustedQuantity)
            o.put("difference", adj.difference)
            o.put("concurrentDeltaAfterCount", adj.concurrentDeltaAfterCount)
            o.put("unitCostCents", adj.unitCostCents)
            o.put("financialImpactCents", adj.financialImpactCents)
            o.put("reason", adj.reason)
            o.put("approvedBy", adj.approvedBy)
            o.put("createdAt", adj.createdAt)
            invAdjArray.put(o)
        }
        root.put("inventory_adjustments", invAdjArray)
        root.put("lastInventorySequence", stockDao.getLastInventoryNumber() ?: 0L)

        root.toString(2)
    }

    /**
     * Creates an AES-256-GCM encrypted safety backup snapshot in internal storage before any restore or database wipe.
     */
    suspend fun createSafetyBackupBeforeRestore(context: Context, db: AppDatabase): File = withContext(Dispatchers.IO) {
        val backupJson = createBackupJson(db, "", "")
        val encryptedSafety = com.example.security.BackupEncryptionManager.encryptSystemBackup(context, backupJson)
        val safetyDir = File(context.filesDir, "safety_backups").apply { if (!exists()) mkdirs() }
        val safetyFile = File(safetyDir, "safety_backup_${System.currentTimeMillis()}.enc.json")
        safetyFile.writeText(encryptedSafety, java.nio.charset.StandardCharsets.UTF_8)
        pruneOldBackups(safetyDir, 10)
        safetyFile
    }

    /**
     * Restores entire database from JSON string snapshot.
     * Validates entities first in temporary database before performing atomic live replacement.
     */
    suspend fun restoreDatabaseFromJson(
        db: AppDatabase,
        jsonString: String,
        context: Context? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        val dao = db.appDao()
        try {
            val root = JSONObject(jsonString)

            var metaEmail: String? = null
            var metaPhone: String? = null
            var metaInvoiceSeq: Long? = null
            val metaObj = if (root.has("metadata")) root.optJSONObject("metadata") else null
            val isCentsBackup = metaObj?.optString("moneyUnit") == "CENTS" ||
                (metaObj?.optInt("schemaVersion", 0) ?: 0) >= 20

            fun JSONObject.readMoneyCents(key: String, defaultCents: Long = 0L): Long {
                if (!has(key) || isNull(key)) return defaultCents
                return if (isCentsBackup) {
                    optLong(key, defaultCents)
                } else {
                    optDouble(key, 0.0).cents
                }
            }

            if (metaObj != null) {
                metaEmail = metaObj.optString("linkedEmail", "").takeIf { it.isNotBlank() }
                metaPhone = metaObj.optString("linkedPhone", "").takeIf { it.isNotBlank() }
                if (metaObj.has("lastInvoiceSequence")) {
                    metaInvoiceSeq = metaObj.optLong("lastInvoiceSequence", 0L)
                }
            }

            // 1. Pre-parse ALL lists completely in memory so JSON syntax/type errors abort before DB modifications
            val categoriesList = mutableListOf<CategoryEntity>()
            if (root.has("categories")) {
                val arr = root.getJSONArray("categories")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    categoriesList.add(
                        CategoryEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", ""),
                            imageUri = if (o.has("imageUri") && !o.isNull("imageUri")) o.optString("imageUri") else null
                        )
                    )
                }
            }

            val productsList = mutableListOf<ProductEntity>()
            if (root.has("products")) {
                val arr = root.getJSONArray("products")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    productsList.add(
                        ProductEntity(
                            id = o.optLong("id", 0L),
                            barcode = o.optString("barcode", ""),
                            name = o.optString("name", ""),
                            description = o.optString("description", ""),
                            categoryId = o.optLong("categoryId", 0L),
                            categoryName = o.optString("categoryName", ""),
                            unit = o.optString("unit", "قطعة / Unité"),
                            purchasePrice = o.readMoneyCents("purchasePrice", 0L),
                            sellPrice = o.readMoneyCents("sellPrice", 0L),
                            wholesalePrice = o.readMoneyCents("wholesalePrice", 0L),
                            specialPrice = o.readMoneyCents("specialPrice", 0L),
                            currentStock = o.optDouble("currentStock", 0.0),
                            minStock = o.optDouble("minStock", 0.0)
                        )
                    )
                }
            }

            val customersList = mutableListOf<CustomerEntity>()
            if (root.has("customers")) {
                val arr = root.getJSONArray("customers")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    customersList.add(
                        CustomerEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", ""),
                            phone = o.optString("phone", ""),
                            address = o.optString("address", ""),
                            balance = o.readMoneyCents("balance", 0L),
                            totalPurchases = o.readMoneyCents("totalPurchases", 0L),
                            latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
                            longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
                            geoRadiusMeters = o.optDouble("geoRadiusMeters", 150.0),
                            isGeoLocationRequired = o.optBoolean("isGeoLocationRequired", false),
                            customerNumber = o.optLong("customerNumber", 0L),
                            barcode = o.optString("barcode", "")
                        )
                    )
                }
            }

            val suppliersList = mutableListOf<SupplierEntity>()
            if (root.has("suppliers")) {
                val arr = root.getJSONArray("suppliers")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    suppliersList.add(
                        SupplierEntity(
                            id = o.optLong("id", 0L),
                            name = o.optString("name", ""),
                            phone = o.optString("phone", ""),
                            email = o.optString("email", ""),
                            address = o.optString("address", ""),
                            debtBalance = o.readMoneyCents("debtBalance", 0L)
                        )
                    )
                }
            }

            val salesList = mutableListOf<SaleEntity>()
            if (root.has("sales")) {
                val arr = root.getJSONArray("sales")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    salesList.add(
                        SaleEntity(
                            id = o.optLong("id", 0L),
                            invoiceNumber = o.optString("invoiceNumber", ""),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            cashierName = o.optString("cashierName", ""),
                            customerId = o.optLong("customerId", 0L),
                            customerName = o.optString("customerName", "عميل نقدي"),
                            totalAmount = o.readMoneyCents("totalAmount", 0L),
                            discountAmount = o.readMoneyCents("discountAmount", 0L),
                            discountType = o.optString("discountType", "VALUE"),
                            paidAmount = o.readMoneyCents("paidAmount", 0L),
                            changeAmount = o.readMoneyCents("changeAmount", 0L),
                            paymentMethod = o.optString("paymentMethod", "CASH"),
                            isVoided = o.optBoolean("isVoided", false)
                        )
                    )
                }
            }

            val saleItemsList = mutableListOf<SaleItemEntity>()
            if (root.has("sale_items")) {
                val arr = root.getJSONArray("sale_items")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    saleItemsList.add(
                        SaleItemEntity(
                            id = o.optLong("id", 0L),
                            saleId = o.optLong("saleId", 0L),
                            productId = o.optLong("productId", 0L),
                            productName = o.optString("productName", ""),
                            quantity = o.optDouble("quantity", 1.0),
                            unitPrice = o.readMoneyCents("unitPrice", 0L),
                            unitCost = o.readMoneyCents("unitCost", 0L),
                            discountValue = o.readMoneyCents("discountValue", 0L),
                            totalLinePrice = o.readMoneyCents("totalLinePrice", 0L)
                        )
                    )
                }
            }

            val purchasesList = mutableListOf<PurchaseEntity>()
            if (root.has("purchases")) {
                val arr = root.getJSONArray("purchases")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    purchasesList.add(
                        PurchaseEntity(
                            id = o.optLong("id", 0L),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            supplierId = o.optLong("supplierId", 0L),
                            supplierName = o.optString("supplierName", ""),
                            totalAmount = o.readMoneyCents("totalAmount", 0L),
                            paidAmount = o.readMoneyCents("paidAmount", 0L),
                            isVoided = o.optBoolean("isVoided", false),
                            invoiceNumber = o.optString("invoiceNumber", "")
                        )
                    )
                }
            }

            val purchaseItemsList = mutableListOf<PurchaseItemEntity>()
            if (root.has("purchase_items")) {
                val arr = root.getJSONArray("purchase_items")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    purchaseItemsList.add(
                        PurchaseItemEntity(
                            id = o.optLong("id", 0L),
                            purchaseId = o.optLong("purchaseId", 0L),
                            productId = o.optLong("productId", 0L),
                            productName = o.optString("productName", ""),
                            quantity = o.optDouble("quantity", 1.0),
                            unitPrice = o.readMoneyCents("unitPrice", 0L)
                        )
                    )
                }
            }

            val expensesList = mutableListOf<ExpenseEntity>()
            if (root.has("expenses")) {
                val arr = root.getJSONArray("expenses")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    expensesList.add(
                        ExpenseEntity(
                            id = o.optLong("id", 0L),
                            title = o.optString("title", ""),
                            amount = o.readMoneyCents("amount", 0L),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            category = o.optString("category", "General")
                        )
                    )
                }
            }

            var settingsEntity: GlobalSettingsEntity? = null
            if (root.has("settings")) {
                val sObj = root.getJSONObject("settings")
                settingsEntity = GlobalSettingsEntity(
                    shopName = sObj.optString("shopName", "Supermarket POS"),
                    shopAddress = sObj.optString("shopAddress", ""),
                    shopPhone = sObj.optString("shopPhone", ""),
                    taxNumber = sObj.optString("taxNumber", ""),
                    receiptHeader = sObj.optString("receiptHeader", ""),
                    receiptFooter = sObj.optString("receiptFooter", ""),
                    language = sObj.optString("language", "AR"),
                    showShopName = sObj.optBoolean("showShopName", true),
                    showAddress = sObj.optBoolean("showAddress", true),
                    showPhone = sObj.optBoolean("showPhone", true),
                    showTaxNumber = sObj.optBoolean("showTaxNumber", true),
                    showLogo = sObj.optBoolean("showLogo", true),
                    showCashierName = sObj.optBoolean("showCashierName", true),
                    showCustomerInfo = sObj.optBoolean("showCustomerInfo", true),
                    showDateTime = sObj.optBoolean("showDateTime", true),
                    showBarcode = sObj.optBoolean("showBarcode", true),
                    showPaymentMethod = sObj.optBoolean("showPaymentMethod", true),
                    showReceiptFooter = sObj.optBoolean("showReceiptFooter", true),
                    printerConnectionType = sObj.optString("printerConnectionType", "BLUETOOTH"),
                    printerName = sObj.optString("printerName", ""),
                    printerMac = sObj.optString("printerMac", ""),
                    printerIp = sObj.optString("printerIp", ""),
                    printerPort = sObj.optInt("printerPort", 9100),
                    printerPaperWidth = sObj.optInt("printerPaperWidth", 80),
                    autoPrintReceipt = sObj.optBoolean("autoPrintReceipt", false),
                    logoWidth = sObj.optInt("logoWidth", 110),
                    logoHeight = sObj.optInt("logoHeight", 70),
                    logoQuality = sObj.optInt("logoQuality", 90).coerceIn(40, 100),
                    headerHeight = sObj.optInt("headerHeight", 30),
                    headerFontSize = sObj.optInt("headerFontSize", 13),
                    headerContentType = sObj.optString("headerContentType", "TEXT").ifBlank { "TEXT" },
                    headerWidth = sObj.optInt("headerWidth", 520),
                    footerContentType = sObj.optString("footerContentType", "TEXT").ifBlank { "TEXT" },
                    footerWidth = sObj.optInt("footerWidth", 520),
                    footerHeight = sObj.optInt("footerHeight", 50),
                    documentFontSize = sObj.optInt("documentFontSize", 12).coerceIn(8, 20),
                    currency = sObj.optString("currency", "MAD").trim().ifBlank { "MAD" }
                )
            }

            val usersList = mutableListOf<UserEntity>()
            if (root.has("users")) {
                val arr = root.getJSONArray("users")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val rawPassword = o.optString("passwordHash", "")
                    val secureHash = if (com.example.security.PasswordHasher.isHashed(rawPassword)) {
                        rawPassword
                    } else if (rawPassword.isNotBlank()) {
                        com.example.security.PasswordHasher.hashAsync(rawPassword)
                    } else {
                        ""
                    }
                    val role = o.optString("role", "CASHIER")
                    usersList.add(
                        UserEntity(
                            id = o.optLong("id", 0L),
                            username = o.optString("username", "cashier"),
                            passwordHash = secureHash,
                            role = role,
                            canSell = o.optBoolean("canSell", false),
                            canPurchase = o.optBoolean("canPurchase", false),
                            canReports = o.optBoolean("canReports", false),
                            canModifyPrice = o.optBoolean("canModifyPrice", false),
                            canDeleteInvoice = o.optBoolean("canDeleteInvoice", false),
                            canManageUsers = o.optBoolean("canManageUsers", false),
                            canEditInvoice = o.optBoolean("canEditInvoice", false),
                            canManageInventory = o.optBoolean("canManageInventory", false),
                            canManageClients = o.optBoolean("canManageClients", false),
                            canEditCustomers = o.optBoolean("canEditCustomers", false),
                            canManageSuppliers = o.optBoolean("canManageSuppliers", false),
                            canManageExpenses = o.optBoolean("canManageExpenses", false),
                            canManagePayments = o.optBoolean("canManagePayments", false),
                            canManageSettings = o.optBoolean("canManageSettings", false),
                            enforceCustomerGeoLocation = o.optBoolean("enforceCustomerGeoLocation", false)
                        )
                    )
                }
            }

            val customerPaymentsList = mutableListOf<CustomerPaymentEntity>()
            if (root.has("customerPayments")) {
                val arr = root.getJSONArray("customerPayments")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    customerPaymentsList.add(
                        CustomerPaymentEntity(
                            id = o.optLong("id", 0L),
                            saleId = o.optLong("saleId", 0L),
                            customerId = o.optLong("customerId", 0L),
                            customerName = o.optString("customerName", ""),
                            amount = o.readMoneyCents("amount", 0L),
                            previousBalance = o.readMoneyCents("previousBalance", 0L),
                            newBalance = o.readMoneyCents("newBalance", 0L),
                            paymentMethod = o.optString("paymentMethod", "CASH"),
                            notes = o.optString("notes", ""),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            cashierName = o.optString("cashierName", "")
                        )
                    )
                }
            }

            val supplierPaymentsList = mutableListOf<SupplierPaymentEntity>()
            if (root.has("supplierPayments")) {
                val arr = root.getJSONArray("supplierPayments")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    supplierPaymentsList.add(
                        SupplierPaymentEntity(
                            id = o.optLong("id", 0L),
                            purchaseId = o.optLong("purchaseId", 0L),
                            supplierId = o.optLong("supplierId", 0L),
                            supplierName = o.optString("supplierName", ""),
                            amount = o.readMoneyCents("amount", 0L),
                            previousDebt = o.readMoneyCents("previousDebt", 0L),
                            newDebt = o.readMoneyCents("newDebt", 0L),
                            paymentMethod = o.optString("paymentMethod", "CASH"),
                            notes = o.optString("notes", ""),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                            cashierName = o.optString("cashierName", "")
                        )
                    )
                }
            }

            val inventoriesList = mutableListOf<InventoryEntity>()
            if (root.has("inventories")) {
                val arr = root.getJSONArray("inventories")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    inventoriesList.add(
                        InventoryEntity(
                            id = o.optLong("id", 0L),
                            reference = o.optString("reference", ""),
                            title = o.optString("title", ""),
                            type = o.optString("type", "GENERAL"),
                            status = o.optString("status", "DRAFT"),
                            scopeSummary = o.optString("scopeSummary", ""),
                            notes = o.optString("notes", ""),
                            createdBy = o.optString("createdBy", ""),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                            completedAt = if (o.isNull("completedAt") || !o.has("completedAt")) null else o.optLong("completedAt"),
                            approvedBy = if (o.isNull("approvedBy") || !o.has("approvedBy")) null else o.optString("approvedBy"),
                            duplicateScanMode = o.optString("duplicateScanMode", "INCREMENT"),
                            totalItemsCount = o.optInt("totalItemsCount", 0),
                            countedItemsCount = o.optInt("countedItemsCount", 0),
                            discrepancyItemsCount = o.optInt("discrepancyItemsCount", 0),
                            totalShortageValueCents = o.optLong("totalShortageValueCents", 0L),
                            totalSurplusValueCents = o.optLong("totalSurplusValueCents", 0L),
                            netDiscrepancyValueCents = o.optLong("netDiscrepancyValueCents", 0L)
                        )
                    )
                }
            }

            val inventoryItemsList = mutableListOf<InventoryItemEntity>()
            if (root.has("inventory_items")) {
                val arr = root.getJSONArray("inventory_items")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    inventoryItemsList.add(
                        InventoryItemEntity(
                            id = o.optLong("id", 0L),
                            inventoryId = o.optLong("inventoryId", 0L),
                            productId = o.optLong("productId", 0L),
                            productName = o.optString("productName", ""),
                            barcode = o.optString("barcode", ""),
                            sku = o.optString("sku", ""),
                            categoryId = o.optLong("categoryId", 0L),
                            categoryName = o.optString("categoryName", ""),
                            unit = o.optString("unit", ""),
                            imageUri = if (o.isNull("imageUri") || !o.has("imageUri")) null else o.optString("imageUri"),
                            expectedQuantity = o.optDouble("expectedQuantity", 0.0),
                            systemStockAtCount = if (o.isNull("systemStockAtCount") || !o.has("systemStockAtCount")) null else o.optDouble("systemStockAtCount"),
                            countedQuantity = if (o.isNull("countedQuantity") || !o.has("countedQuantity")) null else o.optDouble("countedQuantity"),
                            difference = o.optDouble("difference", 0.0),
                            unitCostCents = o.optLong("unitCostCents", 0L),
                            unitSellPriceCents = o.optLong("unitSellPriceCents", 0L),
                            discrepancyValueCents = o.optLong("discrepancyValueCents", 0L),
                            status = o.optString("status", "UNCOUNTED"),
                            notes = o.optString("notes", ""),
                            countedAt = if (o.isNull("countedAt") || !o.has("countedAt")) null else o.optLong("countedAt"),
                            countedBy = if (o.isNull("countedBy") || !o.has("countedBy")) null else o.optString("countedBy"),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            val inventoryAdjustmentsList = mutableListOf<InventoryAdjustmentEntity>()
            if (root.has("inventory_adjustments")) {
                val arr = root.getJSONArray("inventory_adjustments")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    inventoryAdjustmentsList.add(
                        InventoryAdjustmentEntity(
                            id = o.optLong("id", 0L),
                            inventoryId = o.optLong("inventoryId", 0L),
                            productId = o.optLong("productId", 0L),
                            productName = o.optString("productName", ""),
                            barcode = o.optString("barcode", ""),
                            unit = o.optString("unit", ""),
                            snapshotExpectedQuantity = o.optDouble("snapshotExpectedQuantity", 0.0),
                            systemStockAtCount = o.optDouble("systemStockAtCount", 0.0),
                            countedQuantity = o.optDouble("countedQuantity", 0.0),
                            previousQuantity = o.optDouble("previousQuantity", 0.0),
                            adjustedQuantity = o.optDouble("adjustedQuantity", 0.0),
                            difference = o.optDouble("difference", 0.0),
                            concurrentDeltaAfterCount = o.optDouble("concurrentDeltaAfterCount", 0.0),
                            unitCostCents = o.optLong("unitCostCents", 0L),
                            financialImpactCents = o.optLong("financialImpactCents", 0L),
                            reason = o.optString("reason", ""),
                            approvedBy = o.optString("approvedBy", ""),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            val restoredInventorySequence = root.optLong("lastInventorySequence", inventoriesList.size.toLong())

            // 2. STAGING INTEGRITY VALIDATION: Test-insert into a temporary in-memory database to verify schema and constraints before touching live data
            if (context != null) {
                try {
                    val tempDb = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
                    try {
                        val tempDao = tempDb.appDao()
                        val tempStockDao = tempDb.stocktakingDao()
                        if (categoriesList.isNotEmpty()) tempDao.insertCategoriesBatch(categoriesList)
                        if (productsList.isNotEmpty()) tempDao.insertProductsBatch(productsList)
                        if (customersList.isNotEmpty()) tempDao.insertCustomersBatch(customersList)
                        if (suppliersList.isNotEmpty()) tempDao.insertSuppliersBatch(suppliersList)
                        if (salesList.isNotEmpty()) tempDao.insertSalesBatch(salesList)
                        if (saleItemsList.isNotEmpty()) tempDao.insertSaleItemsBatch(saleItemsList)
                        if (purchasesList.isNotEmpty()) tempDao.insertPurchasesBatch(purchasesList)
                        if (purchaseItemsList.isNotEmpty()) tempDao.insertPurchaseItemsBatch(purchaseItemsList)
                        if (expensesList.isNotEmpty()) tempDao.insertExpensesBatch(expensesList)
                        if (usersList.isNotEmpty()) tempDao.insertUsersBatch(usersList)
                        if (customerPaymentsList.isNotEmpty()) tempDao.insertCustomerPaymentsBatch(customerPaymentsList)
                        if (supplierPaymentsList.isNotEmpty()) tempDao.insertSupplierPaymentsBatch(supplierPaymentsList)
                        if (inventoriesList.isNotEmpty()) tempStockDao.insertInventoriesBatch(inventoriesList)
                        if (inventoryItemsList.isNotEmpty()) tempStockDao.insertInventoryItemsBatch(inventoryItemsList)
                        if (inventoryAdjustmentsList.isNotEmpty()) tempStockDao.insertAdjustmentsBatch(inventoryAdjustmentsList)
                    } finally {
                        tempDb.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Integrity validation in staging database failed", e)
                    return@withContext RestoreResult(false, "فشل التحقق من صحة وتكامل بيانات النسخة في قاعدة البيانات المؤقتة: ${e.localizedMessage}")
                }
            }

            // 3. ATOMIC RESTORE: Wipe old records and batch-insert all entities within a single database transaction.
            // If any insertion or constraint fails, the entire transaction rolls back cleanly.
            val stockDao = db.stocktakingDao()
            db.withTransaction {
                // Delete children first to respect foreign key constraints
                stockDao.clearAllAdjustments()
                stockDao.clearAllInventoryItems()
                stockDao.clearAllInventories()
                dao.clearCart("ACTIVE")
                dao.clearAllSaleItems()
                dao.clearAllSales()
                dao.clearAllStockMovements()
                dao.clearAllCustomerPayments()
                dao.clearAllSupplierPayments()
                dao.clearAllPurchaseItems()
                dao.clearAllPurchases()
                dao.clearAllExpenses()
                dao.clearAllProducts()
                dao.clearAllCategories()
                dao.clearAllCustomers()
                dao.clearAllSuppliers()

                // Insert parents first then children
                if (categoriesList.isNotEmpty()) dao.insertCategoriesBatch(categoriesList)
                if (productsList.isNotEmpty()) dao.insertProductsBatch(productsList)
                if (customersList.isNotEmpty()) {
                    dao.insertCustomersBatch(customersList)
                    val maxCustomerNum = customersList.maxOfOrNull { it.customerNumber } ?: 0L
                    if (maxCustomerNum > 0L) {
                        val lastSeq = dao.getLastCustomerNumber() ?: 0L
                        if (maxCustomerNum > lastSeq) {
                            dao.setCustomerSequence(CustomerSequenceEntity(id = 1, lastCustomerNumber = maxCustomerNum))
                        }
                    }
                }
                if (suppliersList.isNotEmpty()) dao.insertSuppliersBatch(suppliersList)
                if (salesList.isNotEmpty()) dao.insertSalesBatch(salesList)
                if (saleItemsList.isNotEmpty()) dao.insertSaleItemsBatch(saleItemsList)
                if (purchasesList.isNotEmpty()) dao.insertPurchasesBatch(purchasesList)
                if (purchaseItemsList.isNotEmpty()) dao.insertPurchaseItemsBatch(purchaseItemsList)
                if (expensesList.isNotEmpty()) dao.insertExpensesBatch(expensesList)
                if (usersList.isNotEmpty()) dao.insertUsersBatch(usersList)
                if (customerPaymentsList.isNotEmpty()) dao.insertCustomerPaymentsBatch(customerPaymentsList)
                if (supplierPaymentsList.isNotEmpty()) dao.insertSupplierPaymentsBatch(supplierPaymentsList)
                if (inventoriesList.isNotEmpty()) stockDao.insertInventoriesBatch(inventoriesList)
                if (inventoryItemsList.isNotEmpty()) stockDao.insertInventoryItemsBatch(inventoryItemsList)
                if (inventoryAdjustmentsList.isNotEmpty()) stockDao.insertAdjustmentsBatch(inventoryAdjustmentsList)
                stockDao.setInventorySequence(InventorySequenceEntity(id = 1, lastInventoryNumber = restoredInventorySequence))
                if (settingsEntity != null) {
                    val current = dao.getSettings() ?: GlobalSettingsEntity()
                    dao.insertSettings(
                        current.copy(
                            shopName = settingsEntity.shopName,
                            shopAddress = settingsEntity.shopAddress,
                            shopPhone = settingsEntity.shopPhone,
                            taxNumber = settingsEntity.taxNumber,
                            receiptHeader = settingsEntity.receiptHeader,
                            receiptFooter = settingsEntity.receiptFooter,
                            language = settingsEntity.language,
                            showShopName = settingsEntity.showShopName,
                            showAddress = settingsEntity.showAddress,
                            showPhone = settingsEntity.showPhone,
                            showTaxNumber = settingsEntity.showTaxNumber,
                            showLogo = settingsEntity.showLogo,
                            showCashierName = settingsEntity.showCashierName,
                            showCustomerInfo = settingsEntity.showCustomerInfo,
                            showDateTime = settingsEntity.showDateTime,
                            showBarcode = settingsEntity.showBarcode,
                            showPaymentMethod = settingsEntity.showPaymentMethod,
                            showReceiptFooter = settingsEntity.showReceiptFooter,
                            printerConnectionType = settingsEntity.printerConnectionType,
                            printerName = settingsEntity.printerName,
                            printerMac = settingsEntity.printerMac,
                            printerIp = settingsEntity.printerIp,
                            printerPort = settingsEntity.printerPort,
                            printerPaperWidth = settingsEntity.printerPaperWidth,
                            autoPrintReceipt = settingsEntity.autoPrintReceipt,
                            logoWidth = settingsEntity.logoWidth,
                            logoHeight = settingsEntity.logoHeight,
                            logoQuality = settingsEntity.logoQuality,
                            headerHeight = settingsEntity.headerHeight,
                            headerFontSize = settingsEntity.headerFontSize,
                            headerContentType = settingsEntity.headerContentType,
                            headerWidth = settingsEntity.headerWidth,
                            footerContentType = settingsEntity.footerContentType,
                            footerWidth = settingsEntity.footerWidth,
                            footerHeight = settingsEntity.footerHeight,
                            documentFontSize = settingsEntity.documentFontSize,
                            currency = settingsEntity.currency
                        )
                    )
                }
                val restoredInvSeq = maxOf(
                    metaInvoiceSeq ?: 0L,
                    salesList.mapNotNull { it.invoiceNumber.trim().toLongOrNull() }.maxOrNull() ?: 0L
                )
                val existingInvSeq = dao.getLastInvoiceNumber() ?: 0L
                val persistedInvSeq = maxOf(existingInvSeq, restoredInvSeq)
                if (persistedInvSeq > 0L) {
                    dao.setInvoiceSequence(InvoiceSequenceEntity(id = 1, lastInvoiceNumber = persistedInvSeq))
                }
            }

            val maxSalesSeq = salesList.mapNotNull { it.invoiceNumber.trim().toLongOrNull() }.maxOrNull() ?: 0L
            val finalInvoiceSeq = maxOf(metaInvoiceSeq ?: 0L, maxSalesSeq)

            RestoreResult(
                success = true,
                message = "تمت استعادة البيانات بنجاح: ${productsList.size} منتج، ${customersList.size} عميل، ${salesList.size} فاتورة بيع، ${purchasesList.size} فاتورة شراء",
                categoriesCount = categoriesList.size,
                productsCount = productsList.size,
                customersCount = customersList.size,
                suppliersCount = suppliersList.size,
                salesCount = salesList.size,
                purchasesCount = purchasesList.size,
                expensesCount = expensesList.size,
                usersCount = usersList.size,
                linkedEmail = metaEmail,
                linkedPhone = metaPhone,
                lastInvoiceSequence = finalInvoiceSeq
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup JSON", e)
            RestoreResult(
                success = false,
                message = "خطأ أثناء استعادة النسخة: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Retains only the most recent [maxKeep] backup files in [backupDir] and purges older ones.
     */
    fun pruneOldBackups(backupDir: File, maxKeep: Int = 5) {
        try {
            if (!backupDir.exists() || !backupDir.isDirectory) return
            val backupFiles = backupDir.listFiles { f ->
                f.isFile && (f.name.endsWith(".json") || f.name.endsWith(".enc.json") || f.name.startsWith("pos_backup_"))
            } ?: return
            if (backupFiles.size > maxKeep) {
                val sorted = backupFiles.sortedByDescending { it.lastModified() }
                for (i in maxKeep until sorted.size) {
                    val deleted = sorted[i].delete()
                    Log.i(TAG, "Pruned old backup: ${sorted[i].name}, deleted=$deleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pruning old backups in ${backupDir.absolutePath}", e)
        }
    }

    /**
     * Writes backup JSON to cache/files directory for sharing or saving into Google Drive.
     * Automatically enforces pruning so only the 5 most recent backups are kept on device.
     */
    fun saveBackupToLocalFile(context: Context, backupJson: String, fileName: String): File {
        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val file = File(backupDir, fileName)
        FileOutputStream(file).use { out ->
            out.write(backupJson.toByteArray(Charsets.UTF_8))
        }
        pruneOldBackups(backupDir, 5)

        val cacheBackupDir = File(context.cacheDir, "backups")
        if (cacheBackupDir.exists()) {
            pruneOldBackups(cacheBackupDir, 5)
        }
        return file
    }

    /**
     * Creates an Intent to share or save the backup directly to Google Drive app or local storage.
     */
    fun createDriveSaveIntent(context: Context, backupFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - ${backupFile.name}")
            putExtra(Intent.EXTRA_TEXT, "نسخة احتياطية كاملة لقاعدة بيانات السوبرماركت (Google Drive Backup)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val categoriesCount: Int = 0,
    val productsCount: Int = 0,
    val customersCount: Int = 0,
    val suppliersCount: Int = 0,
    val salesCount: Int = 0,
    val purchasesCount: Int = 0,
    val expensesCount: Int = 0,
    val usersCount: Int = 0,
    val linkedEmail: String? = null,
    val linkedPhone: String? = null,
    val lastInvoiceSequence: Long? = null
)
