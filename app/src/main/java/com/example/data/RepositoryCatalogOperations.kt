package com.example.data

import com.example.util.MoneyUtils
import com.example.util.cents
import java.util.Locale

internal class RepositoryCatalogOperations(
    private val appDao: AppDao,
    private val runInTransaction: suspend (suspend () -> Any?) -> Any?,
    private val getStoreCurrency: suspend () -> String,
    private val recordAuditLog: suspend (String, String, String, String, String, String, Boolean) -> Long,
    private val logError: (String, Throwable?) -> Unit,
    private val stocktakingDao: StocktakingDao? = null
) {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> tx(block: suspend () -> T): T = runInTransaction { block() } as T

    suspend fun verifyAndSeedDatabase() {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val existingUsers = appDao.getAllUsersList()
                for (u in existingUsers) {
                    if (!com.example.security.PasswordHasher.isHashed(u.passwordHash)) {
                        val hashed = com.example.security.PasswordHasher.hashAsync(
                            password = u.passwordHash,
                            dispatcher = kotlinx.coroutines.Dispatchers.IO
                        )
                        appDao.updateUser(u.copy(passwordHash = hashed))
                    }
                }
            }
        } catch (e: Exception) {
            logError("Automatic password hash migration error", e)
        }

        val existingSet = appDao.getSettings()
        if (existingSet == null) {
            appDao.insertSettings(
                GlobalSettingsEntity(
                    shopName = "",
                    shopAddress = "",
                    shopPhone = "",
                    taxNumber = "",
                    receiptHeader = "فاتورة بيع مبسطة / Ticket de Caisse",
                    receiptFooter = "شكراً لتعاملكم معنا\nMerci pour votre fidélité !",
                    language = "AR"
                )
            )
        }
    }

    suspend fun wipeAllEntitiesData(currentUser: UserEntity? = null) = tx {
        if (currentUser == null || !currentUser.isManager) {
            throw SecurityException("عملية المسح الشامل مقتصرة حصرياً على المدير")
        }
        appDao.clearCart("ACTIVE")
        appDao.clearAllSaleItems()
        appDao.clearAllSales()
        appDao.clearAllStockMovements()
        appDao.clearAllCustomerPayments()
        appDao.clearAllSupplierPayments()
        appDao.clearAllPurchaseItems()
        appDao.clearAllPurchases()
        appDao.clearAllExpenses()
        appDao.clearAllProducts()
        appDao.clearAllCategories()
        appDao.clearAllCustomers()
        appDao.clearAllSuppliers()
        stocktakingDao?.clearAllAdjustments()
        stocktakingDao?.clearAllInventoryItems()
        stocktakingDao?.clearAllInventories()
        stocktakingDao?.setInventorySequence(InventorySequenceEntity(id = 1, lastInventoryNumber = 0L))
        appDao.setCustomerSequence(CustomerSequenceEntity(id = 1, lastCustomerNumber = 0L))
        recordAuditLog(
            "DATABASE_RESET",
            "ALL",
            "0",
            "مسح كافة المنتجات والزبناء والموردين وعائلات المنتجات المتواجدة في البرنامج",
            currentUser.username,
            currentUser.roleName,
            true
        )
    }

    suspend fun authenticate(
        username: String,
        pin: String,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
    ): UserEntity? = kotlinx.coroutines.withContext(ioDispatcher) {
        val user = appDao.getUserByUsername(username.trim()) ?: return@withContext null
        if (com.example.security.PasswordHasher.verifyAsync(pin.trim(), user.passwordHash, dispatcher = ioDispatcher)) {
            if (com.example.security.PasswordHasher.needsRehash(user.passwordHash)) {
                val upgraded = user.copy(
                    passwordHash = com.example.security.PasswordHasher.hashAsync(pin.trim(), dispatcher = ioDispatcher)
                )
                appDao.updateUser(upgraded)
                return@withContext upgraded
            }
            return@withContext user
        }
        null
    }

    suspend fun saveUser(
        user: UserEntity,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
    ): Long = kotlinx.coroutines.withContext(ioDispatcher) {
        val securePassword = if (com.example.security.PasswordHasher.isHashed(user.passwordHash)) {
            user.passwordHash
        } else {
            com.example.security.PasswordHasher.hashAsync(user.passwordHash, dispatcher = ioDispatcher)
        }
        appDao.insertUser(user.copy(username = user.username.trim(), passwordHash = securePassword))
    }

    suspend fun renameSellerReferences(oldUsername: String, newUsername: String) {
        val oldTrimmed = oldUsername.trim()
        val newTrimmed = newUsername.trim()
        if (oldTrimmed.isBlank() || newTrimmed.isBlank() || oldTrimmed == newTrimmed) return
        tx {
            appDao.updateSalesCashierName(oldTrimmed, newTrimmed)
            appDao.updateTransactionsCashierName(oldTrimmed, newTrimmed)
            appDao.updateCustomerPaymentsCashierName(oldTrimmed, newTrimmed)
            appDao.updateSupplierPaymentsCashierName(oldTrimmed, newTrimmed)
        }
    }

    suspend fun saveProduct(product: ProductEntity) {
        val existing = appDao.getProductById(product.id)
        if (existing == null) {
            val insertedId = appDao.insertProduct(product)
            appDao.insertStockMovement(
                StockMovementEntity(
                    productId = insertedId,
                    productName = product.name,
                    type = "CORRECTION",
                    quantity = product.currentStock,
                    timestamp = System.currentTimeMillis(),
                    notes = "افتتاح المخزون / Stock initial"
                )
            )
        } else {
            val stockDiff = product.currentStock - existing.currentStock
            appDao.updateProduct(product)
            if (stockDiff != 0.0) {
                appDao.insertStockMovement(
                    StockMovementEntity(
                        productId = product.id,
                        productName = product.name,
                        type = "CORRECTION",
                        quantity = stockDiff,
                        timestamp = System.currentTimeMillis(),
                        notes = "تعديل يدوي / Ajustement manuel"
                    )
                )
            }
            if (product.sellPrice != existing.sellPrice || product.purchasePrice != existing.purchasePrice) {
                val currency = getStoreCurrency()
                recordAuditLog(
                    "PRICE_CHANGE",
                    "PRODUCT",
                    product.id.toString(),
                    "تعديل أسعار ${product.name}: البيع السابق=${MoneyUtils.format(existing.sellPrice, currency)}، الجديد=${MoneyUtils.format(product.sellPrice, currency)} | الشراء السابق=${MoneyUtils.format(existing.purchasePrice, currency)}، الجديد=${MoneyUtils.format(product.purchasePrice, currency)}",
                    "Admin",
                    "MANAGER",
                    false
                )
            }
        }
    }

    suspend fun deleteProductsBatch(productIds: List<Long>): Int = tx {
        if (productIds.isEmpty()) return@tx 0
        appDao.deleteProductsBatch(productIds)
        recordAuditLog(
            "BATCH_PRODUCT_DELETE",
            "PRODUCT",
            productIds.joinToString(","),
            "حذف جماعي لـ ${productIds.size} منتج",
            "Admin",
            "MANAGER",
            false
        )
        productIds.size
    }

    suspend fun importProductsFromExcel(
        rows: List<com.example.util.ProductExcelManager.ProductExcelRow>,
        updateExisting: Boolean
    ): PosRepository.ImportProductsResult = tx {
        var inserted = 0
        var updated = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        val existingCategories = appDao.getAllCategoriesList().associateBy { it.name.trim().lowercase(Locale.ROOT) }.toMutableMap()
        val existingProductsByBarcode = appDao.getAllProductsList().associateBy { it.barcode.trim() }.toMutableMap()

        for ((index, row) in rows.withIndex()) {
            val rowNum = index + 2
            if (row.name.isBlank()) {
                errors.add("السطر $rowNum: اسم المنتج فارغ")
                skipped++
                continue
            }

            var cleanBarcode = row.barcode.trim()
            if (cleanBarcode.isBlank()) {
                cleanBarcode = "AUTO${System.currentTimeMillis().toString().takeLast(8)}${(10..99).random()}"
            }

            val cleanFamily = row.familyName.trim().ifBlank { "عام / Général" }
            val catKey = cleanFamily.lowercase(Locale.ROOT)
            var cat = existingCategories[catKey]
            if (cat == null) {
                val newCat = CategoryEntity(name = cleanFamily)
                val catId = appDao.insertCategory(newCat)
                cat = newCat.copy(id = catId)
                existingCategories[catKey] = cat
            }

            val cleanUnit = row.unit.trim().ifBlank { "قطعة / Unité" }
            val existingProduct = existingProductsByBarcode[cleanBarcode]

            if (existingProduct != null) {
                if (updateExisting) {
                    val updatedProduct = existingProduct.copy(
                        name = row.name.trim(),
                        categoryId = cat.id,
                        categoryName = cat.name,
                        unit = cleanUnit,
                        purchasePrice = row.purchasePrice.cents,
                        sellPrice = row.sellPrice.cents,
                        updatedAt = System.currentTimeMillis()
                    )
                    appDao.updateProduct(updatedProduct)
                    existingProductsByBarcode[cleanBarcode] = updatedProduct
                    updated++
                } else {
                    skipped++
                }
            } else {
                val newProduct = ProductEntity(
                    barcode = cleanBarcode,
                    name = row.name.trim(),
                    categoryId = cat.id,
                    categoryName = cat.name,
                    unit = cleanUnit,
                    purchasePrice = row.purchasePrice.cents,
                    sellPrice = row.sellPrice.cents,
                    currentStock = 0.0,
                    minStock = 2.0,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val newId = appDao.insertProduct(newProduct)
                existingProductsByBarcode[cleanBarcode] = newProduct.copy(id = newId)
                inserted++
            }
        }

        recordAuditLog(
            "EXCEL_IMPORT",
            "PRODUCT",
            "BATCH",
            "استيراد ملف إكسيل: تمت إضافة $inserted منتج جديد، وتحديث $updated منتج، وتخطي $skipped",
            "Admin",
            "MANAGER",
            false
        )

        PosRepository.ImportProductsResult(
            totalRead = rows.size,
            insertedCount = inserted,
            updatedCount = updated,
            skippedCount = skipped,
            errors = errors
        )
    }
}
