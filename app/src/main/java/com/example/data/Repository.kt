package com.example.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.domain.DomainDatabaseConstraintException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Supermarket POS Repository implementing clean MVVM single-source of truth.
 * Delegates multi-step transactional workflows to domain-focused operation helpers
 * while preserving atomic database transactions and a stable public API.
 */
class PosRepository(
    private val appDao: AppDao,
    private val database: AppDatabase? = null,
    private val stocktakingDao: StocktakingDao? = database?.stocktakingDao()
) {

    suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return try {
            val db = database
            if (db != null) {
                db.withTransaction { block() }
            } else {
                block()
            }
        } catch (e: SQLiteConstraintException) {
            throw DomainDatabaseConstraintException(
                message = e.message ?: "Database constraint prevented saving changes",
                cause = e
            )
        }
    }

    private suspend fun getStoreCurrency(): String {
        return appDao.getSettings()?.currency?.trim()?.takeIf { it.isNotBlank() } ?: "MAD"
    }

    private val txRunner: suspend (suspend () -> Any?) -> Any? = { block -> runInTransaction { block() } }
    private val auditLogger: suspend (String, String, String, String, String, String, Boolean) -> Long =
        { actionType, entityType, entityId, details, username, userRole, isCritical ->
            recordAuditLog(actionType, entityType, entityId, details, username, userRole, isCritical)
        }

    private val catalogOps = RepositoryCatalogOperations(appDao, txRunner, ::getStoreCurrency, auditLogger, ::logError, stocktakingDao)
    private val partyFinanceOps = RepositoryPartyAndFinanceOperations(appDao, txRunner, ::getStoreCurrency, auditLogger)
    private val purchaseOps = RepositoryPurchaseOperations(appDao, txRunner, ::getStoreCurrency, auditLogger)
    private val salesOps = RepositorySalesOperations(appDao, txRunner, auditLogger, ::logError)
    private val salesSettlementOps = RepositorySalesSettlementOperations(appDao, txRunner, ::getStoreCurrency, auditLogger)
    private val stocktakingOps: RepositoryStocktakingOperations? = stocktakingDao?.let { dao ->
        RepositoryStocktakingOperations(appDao, dao, txRunner, ::getStoreCurrency, auditLogger)
    }

    private fun requireStocktakingOps(): RepositoryStocktakingOperations =
        stocktakingOps ?: throw IllegalStateException("StocktakingDao is not initialized for PosRepository")

    // Streams
    val categories: Flow<List<CategoryEntity>> = appDao.getAllCategories()
    val products: Flow<List<ProductEntity>> = appDao.getAllProducts()
    val lowStockProducts: Flow<List<ProductEntity>> = appDao.getLowStockProducts()
    val customers: Flow<List<CustomerEntity>> = appDao.getAllCustomers()
    val suppliers: Flow<List<SupplierEntity>> = appDao.getAllSuppliers()
    val sales: Flow<List<SaleEntity>> = appDao.getAllSales()
    val productSalesHistory: Flow<List<ProductSaleHistoryRecord>> = appDao.getAllProductSalesHistory()
    val purchases: Flow<List<PurchaseEntity>> = appDao.getAllPurchases()
    val purchaseItems: Flow<List<PurchaseItemEntity>> = appDao.getAllPurchaseItems()
    val expenses: Flow<List<ExpenseEntity>> = appDao.getAllExpenses()
    val stockMovements: Flow<List<StockMovementEntity>> = appDao.getAllStockMovements()
    val orders: Flow<List<OrderEntity>> = appDao.getAllOrders()
    val ordersWithItems: Flow<List<OrderWithItems>> = appDao.getAllOrdersWithItems()
    val transactions: Flow<List<TransactionEntity>> = appDao.getAllTransactions()
    val transactionsWithItems: Flow<List<TransactionWithItems>> = appDao.getAllTransactionsWithItems()
    val unsyncedTransactions: Flow<List<TransactionEntity>> = appDao.getUnsyncedTransactions()
    val activeCartItems: Flow<List<CartItemEntity>> = appDao.getCartItems("ACTIVE")
    val activeCartTotal: Flow<Double?> = appDao.getCartTotal("ACTIVE")
    val activeCartCount: Flow<Int> = appDao.getCartItemsCount("ACTIVE")
    val cartSessions: Flow<List<String>> = appDao.getAllCartSessions()
    val customerPayments: Flow<List<CustomerPaymentEntity>> = appDao.getAllCustomerPayments()
    val supplierPayments: Flow<List<SupplierPaymentEntity>> = appDao.getAllSupplierPayments()
    val auditLogs: Flow<List<AuditLogEntity>> = appDao.getAllAuditLogs()

    // Paging & Aggregated Streams
    fun getProductsPaged(limit: Int, offset: Int): Flow<List<ProductEntity>> = appDao.getProductsPaged(limit, offset)
    fun getProductsFilteredPaged(query: String, categoryId: Long?, limit: Int, offset: Int): Flow<List<ProductEntity>> =
        appDao.getProductsFilteredPaged(query.trim(), categoryId, limit, offset)
    fun getProductsFilteredCount(query: String, categoryId: Long?): Flow<Int> =
        appDao.getProductsFilteredCountFlow(query.trim(), categoryId)
    fun getProductsTotalCount(): Flow<Int> = appDao.getProductsTotalCountFlow()

    fun getCustomersFilteredPaged(query: String, limit: Int, offset: Int): Flow<List<CustomerEntity>> =
        appDao.getCustomersFilteredPaged(query.trim(), limit, offset)
    fun getCustomersFilteredCount(query: String): Flow<Int> =
        appDao.getCustomersFilteredCountFlow(query.trim())

    fun getSalesPaged(limit: Int, offset: Int): Flow<List<SaleEntity>> = appDao.getSalesPaged(limit, offset)
    fun getSalesFilteredPaged(query: String, statusFilter: String, seller: String, limit: Int, offset: Int): Flow<List<SaleEntity>> =
        appDao.getSalesFilteredPaged(query.trim(), statusFilter, seller, limit, offset)
    fun getSalesFilteredSummary(query: String, statusFilter: String, seller: String): Flow<SalesLedgerSqlSummary> =
        appDao.getSalesFilteredSummaryFlow(query.trim(), statusFilter, seller)
    fun getSalesCountBySeller(): Flow<List<SellerSalesCountRecord>> = appDao.getSalesCountBySellerFlow()

    fun getPurchasesPaged(limit: Int, offset: Int): Flow<List<PurchaseEntity>> = appDao.getPurchasesPaged(limit, offset)
    fun getPurchasesFilteredPaged(query: String, statusFilter: String, limit: Int, offset: Int): Flow<List<PurchaseEntity>> =
        appDao.getPurchasesFilteredPaged(query.trim(), statusFilter, limit, offset)
    fun getPurchasesFilteredSummary(query: String, statusFilter: String): Flow<PurchaseLedgerSqlSummary> =
        appDao.getPurchasesFilteredSummaryFlow(query.trim(), statusFilter)

    fun getCustomerPaymentsPaged(limit: Int, offset: Int): Flow<List<CustomerPaymentEntity>> =
        appDao.getCustomerPaymentsPaged(limit, offset)
    fun getSupplierPaymentsPaged(limit: Int, offset: Int): Flow<List<SupplierPaymentEntity>> =
        appDao.getSupplierPaymentsPaged(limit, offset)

    fun getRecentSales(limit: Int = 10): Flow<List<SaleEntity>> = appDao.getRecentSales(limit)
    fun getActiveSalesCount(): Flow<Int> = appDao.getActiveSalesCountFlow()
    fun getTotalSalesSum(): Flow<Double?> = appDao.getTotalSalesSumFlow()
    fun getSalesForCustomerPaged(customerId: Long, limit: Int = 20, offset: Int = 0): Flow<List<SaleEntity>> =
        appDao.getSalesForCustomerPaged(customerId, limit, offset)
    fun getSalesCountForCustomer(customerId: Long): Flow<Int> = appDao.getSalesCountForCustomerFlow(customerId)
    fun getSalesSumForCustomer(customerId: Long): Flow<Double?> = appDao.getSalesSumForCustomerFlow(customerId)

    fun getStockMovementsPaged(limit: Int, offset: Int): Flow<List<StockMovementEntity>> =
        appDao.getStockMovementsPaged(limit, offset)
    fun getRecentStockMovements(limit: Int = 50): Flow<List<StockMovementEntity>> =
        appDao.getRecentStockMovements(limit)
    fun getStockMovementsCount(): Flow<Int> = appDao.getStockMovementsCountFlow()
    fun getStockMovementsByReferenceTypePaged(refType: String, limit: Int, offset: Int): Flow<List<StockMovementEntity>> =
        appDao.getStockMovementsByReferenceTypePaged(refType, limit, offset)

    companion object {
        private const val TAG = "PosRepository"

        private fun logError(message: String, throwable: Throwable? = null) {
            if (com.example.BuildConfig.DEBUG) {
                android.util.Log.e(TAG, message, throwable)
            }
        }
    }

    data class ImportProductsResult(
        val totalRead: Int,
        val insertedCount: Int,
        val updatedCount: Int,
        val skippedCount: Int,
        val errors: List<String>
    )

    // --- SEEDING, RESET & USERS ---
    suspend fun verifyAndSeedDatabase() = catalogOps.verifyAndSeedDatabase()
    suspend fun wipeAllEntitiesData(currentUser: UserEntity? = null) = catalogOps.wipeAllEntitiesData(currentUser)

    val allUsers: Flow<List<UserEntity>> = appDao.getAllUsers()
    suspend fun getAllUsersList(): List<UserEntity> = appDao.getAllUsersList()
    suspend fun authenticate(username: String, pin: String, ioDispatcher: CoroutineDispatcher = Dispatchers.IO): UserEntity? =
        catalogOps.authenticate(username, pin, ioDispatcher)
    suspend fun getUserByUsername(username: String): UserEntity? = appDao.getUserByUsername(username)
    suspend fun saveUser(user: UserEntity, ioDispatcher: CoroutineDispatcher = Dispatchers.IO): Long =
        catalogOps.saveUser(user, ioDispatcher)
    suspend fun renameSellerReferences(oldUsername: String, newUsername: String) =
        catalogOps.renameSellerReferences(oldUsername, newUsername)
    suspend fun deleteUser(user: UserEntity) = appDao.deleteUser(user.id)

    // --- CATEGORIES & PRODUCTS ---
    suspend fun saveCategory(category: CategoryEntity) = appDao.insertCategory(category)
    suspend fun updateCategoryImage(categoryId: Long, imageUri: String?) = appDao.updateCategoryImage(categoryId, imageUri)
    suspend fun deleteCategory(category: CategoryEntity) = runInTransaction {
        appDao.clearProductsCategory(category.id)
        appDao.deleteCategory(category.id)
    }

    suspend fun getProductByBarcode(barcode: String): ProductEntity? = appDao.getProductByBarcode(barcode)
    suspend fun getProductById(id: Long): ProductEntity? = appDao.getProductById(id)
    suspend fun getProductsByIds(productIds: List<Long>): List<ProductEntity> = appDao.getProductsByIds(productIds)
    suspend fun updateProductImage(productId: Long, imageUri: String?) = appDao.updateProductImage(productId, imageUri)
    suspend fun saveProduct(product: ProductEntity) = catalogOps.saveProduct(product)
    suspend fun deleteProduct(product: ProductEntity) = appDao.deleteProduct(product.id)
    suspend fun deleteProductsBatch(productIds: List<Long>): Int = catalogOps.deleteProductsBatch(productIds)
    suspend fun importProductsFromExcel(
        rows: List<com.example.util.ProductExcelManager.ProductExcelRow>,
        updateExisting: Boolean
    ): ImportProductsResult = catalogOps.importProductsFromExcel(rows, updateExisting)

    // --- CUSTOMERS, SUPPLIERS & PAYMENTS ---
    suspend fun getCustomerById(id: Long): CustomerEntity? = appDao.getCustomerById(id)
    suspend fun getSupplierById(id: Long): SupplierEntity? = appDao.getSupplierById(id)
    suspend fun getCustomerByBarcode(barcode: String): CustomerEntity? = appDao.getCustomerByBarcode(barcode.trim())
    suspend fun getCustomerByNumber(number: Long): CustomerEntity? = appDao.getCustomerByNumber(number)
    suspend fun insertCustomerPayment(payment: CustomerPaymentEntity): Long = appDao.insertCustomerPayment(payment)
    suspend fun insertSupplierPayment(payment: SupplierPaymentEntity): Long = appDao.insertSupplierPayment(payment)
    suspend fun getNextCustomerNumber(): Long = partyFinanceOps.getNextCustomerNumber()
    fun generateCustomerBarcode(shopName: String, customerNumber: Long): String =
        partyFinanceOps.generateCustomerBarcode(shopName, customerNumber)
    suspend fun saveCustomer(customer: CustomerEntity): Long = partyFinanceOps.saveCustomer(customer)
    suspend fun deleteCustomer(customerId: Long): Pair<Boolean, String> = partyFinanceOps.deleteCustomer(customerId)
    suspend fun deleteCustomersBatch(customerIds: List<Long>): Pair<Int, List<String>> =
        partyFinanceOps.deleteCustomersBatch(customerIds)
    suspend fun adjustCustomerBalance(customerId: Long, amount: Double) = appDao.adjustCustomerBalance(customerId, amount)
    suspend fun saveSupplier(supplier: SupplierEntity) = appDao.insertSupplier(supplier)
    suspend fun deleteSupplier(supplierId: Long): Pair<Boolean, String> = partyFinanceOps.deleteSupplier(supplierId)

    suspend fun recordCustomerPayment(customerId: Long, amount: Double, paymentMethod: String, notes: String, cashierName: String): CustomerPaymentEntity? =
        partyFinanceOps.recordCustomerPayment(customerId, amount, paymentMethod, notes, cashierName)
    suspend fun deleteCustomerPayment(payment: CustomerPaymentEntity) = partyFinanceOps.deleteCustomerPayment(payment)
    suspend fun updateCustomerPayment(paymentId: Long, newAmount: Double, newPaymentMethod: String, newNotes: String) =
        partyFinanceOps.updateCustomerPayment(paymentId, newAmount, newPaymentMethod, newNotes)
    suspend fun recordSupplierPayment(supplierId: Long, amount: Double, paymentMethod: String, notes: String, cashierName: String): SupplierPaymentEntity? =
        partyFinanceOps.recordSupplierPayment(supplierId, amount, paymentMethod, notes, cashierName)
    suspend fun deleteSupplierPayment(payment: SupplierPaymentEntity) = partyFinanceOps.deleteSupplierPayment(payment)
    suspend fun updateSupplierPayment(paymentId: Long, newAmount: Double, newPaymentMethod: String, newNotes: String) =
        partyFinanceOps.updateSupplierPayment(paymentId, newAmount, newPaymentMethod, newNotes)

    // --- PURCHASES ---
    suspend fun getPurchaseById(id: Long): PurchaseEntity? = appDao.getPurchaseById(id)
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity> = appDao.getPurchaseItems(purchaseId)
    suspend fun recordPurchaseInvoice(purchase: PurchaseEntity, items: List<PurchaseItemEntity>, currentUser: UserEntity? = null) =
        purchaseOps.recordPurchaseInvoice(purchase, items, currentUser)
    suspend fun voidPurchaseInvoice(purchaseId: Long) = purchaseOps.voidPurchaseInvoice(purchaseId)
    suspend fun updatePurchaseInvoice(updatedPurchase: PurchaseEntity, updatedItems: List<PurchaseItemEntity>, recordPaymentDelta: Boolean = false) =
        purchaseOps.updatePurchaseInvoice(updatedPurchase, updatedItems, recordPaymentDelta)
    suspend fun settlePurchaseInvoice(purchaseId: Long, amountCents: Long? = null, paymentMethod: String = "CASH", notes: String = "", cashierName: String = ""): SupplierPaymentEntity =
        purchaseOps.settlePurchaseInvoice(purchaseId, amountCents, paymentMethod, notes, cashierName)
    suspend fun deletePurchaseInvoice(purchaseId: Long, currentUser: UserEntity? = null) =
        purchaseOps.deletePurchaseInvoice(purchaseId, currentUser)
    suspend fun recordSupplierReturn(purchaseId: Long, productId: Long, productName: String, quantity: Double, returnAmount: Double, supplierId: Long) =
        purchaseOps.recordSupplierReturn(purchaseId, productId, productName, quantity, returnAmount, supplierId)
    suspend fun updatePurchaseTimestamp(purchaseId: Long, newTimestamp: Long) = purchaseOps.updatePurchaseTimestamp(purchaseId, newTimestamp)
    suspend fun updatePurchaseInvoiceNumber(purchaseId: Long, newInvoiceNumber: String): Boolean =
        purchaseOps.updatePurchaseInvoiceNumber(purchaseId, newInvoiceNumber)
    suspend fun updatePurchaseSupplier(purchaseId: Long, newSupplierId: Long, newSupplierName: String) =
        purchaseOps.updatePurchaseSupplier(purchaseId, newSupplierId, newSupplierName)

    // --- SALES, ORDERS & SETTLEMENTS ---
    suspend fun getSaleById(saleId: Long): SaleEntity? = appDao.getSaleById(saleId)
    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity> = appDao.getSaleItems(saleId)
    suspend fun recordSaleInvoice(sale: SaleEntity, items: List<SaleItemEntity>, currentUser: UserEntity? = null): SaleEntity =
        salesOps.recordSaleInvoice(sale, items, currentUser)
    suspend fun createOrder(order: OrderEntity, items: List<OrderItemEntity>): OrderEntity = salesOps.createOrder(order, items)
    suspend fun getOrderById(orderId: Long): OrderEntity? = appDao.getOrderById(orderId)
    suspend fun getOrderByNumber(orderNumber: String): OrderEntity? = appDao.getOrderByNumber(orderNumber)
    fun getOrderWithItems(orderId: Long): Flow<OrderWithItems?> = appDao.getOrderWithItems(orderId)
    suspend fun getOrderItems(orderId: Long): List<OrderItemEntity> = appDao.getOrderItems(orderId)
    suspend fun voidOrder(orderId: Long) = salesOps.voidOrder(orderId)
    fun searchProducts(query: String): Flow<List<ProductEntity>> = appDao.searchProducts(query)
    fun getProductsByCategory(catId: Long): Flow<List<ProductEntity>> = appDao.getProductsByCategory(catId)

    suspend fun voidSaleInvoice(saleId: Long) = salesSettlementOps.voidSaleInvoice(saleId)
    suspend fun updateSaleInvoice(updatedSale: SaleEntity, updatedItems: List<SaleItemEntity>, recordPaymentDelta: Boolean = false) =
        salesSettlementOps.updateSaleInvoice(updatedSale, updatedItems, recordPaymentDelta)
    suspend fun settleSaleInvoice(saleId: Long, amountCents: Long? = null, paymentMethod: String = "CASH", notes: String = "", cashierName: String = ""): CustomerPaymentEntity =
        salesSettlementOps.settleSaleInvoice(saleId, amountCents, paymentMethod, notes, cashierName)
    suspend fun updateSaleTimestamp(saleId: Long, newTimestamp: Long) = salesSettlementOps.updateSaleTimestamp(saleId, newTimestamp)
    suspend fun updateSaleInvoiceNumber(saleId: Long, newInvoiceNumber: String): Boolean =
        salesSettlementOps.updateSaleInvoiceNumber(saleId, newInvoiceNumber)
    suspend fun updateSaleCustomer(saleId: Long, newCustomerId: Long, newCustomerName: String) =
        salesSettlementOps.updateSaleCustomer(saleId, newCustomerId, newCustomerName)
    suspend fun deleteSaleInvoice(saleId: Long, currentUser: UserEntity? = null) =
        salesSettlementOps.deleteSaleInvoice(saleId, currentUser)
    suspend fun getReturnedQuantitiesForSale(saleId: Long): Map<Long, Double> =
        salesSettlementOps.getReturnedQuantitiesForSale(saleId)
    suspend fun recordCustomerReturn(saleId: Long, productId: Long, productName: String, quantity: Double, refundAmount: Double? = null, isCreditRefund: Boolean, customerId: Long) =
        salesSettlementOps.recordCustomerReturn(saleId, productId, productName, quantity, refundAmount, isCreditRefund, customerId)

    // --- EXPENSES, SETTINGS & METRICS ---
    suspend fun saveExpense(expense: ExpenseEntity) = appDao.insertExpense(expense)
    suspend fun deleteExpense(expense: ExpenseEntity) = appDao.deleteExpense(expense.id)
    suspend fun getSettings(): GlobalSettingsEntity = appDao.getSettings() ?: GlobalSettingsEntity()
    fun getSettingsFlow(): Flow<GlobalSettingsEntity> = appDao.getSettingsFlow().map { it ?: GlobalSettingsEntity() }
    suspend fun saveSettings(settings: GlobalSettingsEntity) = appDao.insertSettings(settings)
    suspend fun updateSettings(settings: GlobalSettingsEntity) = appDao.insertSettings(settings)

    fun getProfitSumForDay(start: Long, end: Long): Flow<Double?> = appDao.getProfitSumBetween(start, end)
    fun getSalesSumForDay(start: Long, end: Long): Flow<Double?> = appDao.getSalesSumBetween(start, end)
    fun getSalesCountForDay(start: Long, end: Long): Flow<Int?> = appDao.getSalesCountBetween(start, end)
    fun getCashSalesSumForDay(start: Long, end: Long): Flow<Double?> = appDao.getCashSalesSumBetween(start, end)
    fun getCardSalesSumForDay(start: Long, end: Long): Flow<Double?> = appDao.getCardSalesSumBetween(start, end)
    fun getExpensesSumForDay(start: Long, end: Long): Flow<Double?> = appDao.getExpensesSumBetween(start, end)
    fun getLowStockCountFlow(): Flow<Int?> = appDao.getLowStockCount()
    fun getTopProductsFlow(): Flow<List<TopProductRecord>> = appDao.getTopSellingProducts()

    // --- TRANSACTIONS & PERSISTENT CART ---
    suspend fun recordTransaction(transaction: TransactionEntity, items: List<TransactionItemEntity>): Long =
        salesOps.recordTransaction(transaction, items)
    suspend fun getTransactionById(txId: Long): TransactionEntity? = appDao.getTransactionById(txId)
    suspend fun getTransactionByNumber(txNumber: String): TransactionEntity? = appDao.getTransactionByNumber(txNumber)
    fun getTransactionWithItems(txId: Long): Flow<TransactionWithItems?> = appDao.getTransactionWithItems(txId)
    suspend fun getTransactionItems(txId: Long): List<TransactionItemEntity> = appDao.getTransactionItems(txId)
    suspend fun getUnsyncedTransactionsList(): List<TransactionEntity> = appDao.getUnsyncedTransactionsList()
    suspend fun markTransactionSynced(txId: Long) = appDao.markTransactionAsSynced(txId)
    suspend fun voidTransaction(txId: Long) = appDao.voidTransaction(txId)
    suspend fun deleteTransaction(txId: Long) = runInTransaction {
        appDao.deleteTransactionItems(txId)
        appDao.deleteTransaction(txId)
    }

    fun getCartItems(sessionId: String = "ACTIVE"): Flow<List<CartItemEntity>> = appDao.getCartItems(sessionId)
    suspend fun getCartItemsList(sessionId: String = "ACTIVE"): List<CartItemEntity> = appDao.getCartItemsList(sessionId)
    suspend fun addToPersistentCart(product: ProductEntity, quantity: Double = 1.0, customPrice: Double? = null, lineDiscount: Double = 0.0, sessionId: String = "ACTIVE") =
        salesOps.addToPersistentCart(product, quantity, customPrice, lineDiscount, sessionId)
    suspend fun updatePersistentCartQuantity(cartItemId: Long, quantity: Double, unitPrice: Double, lineDiscount: Double = 0.0) =
        salesOps.updatePersistentCartQuantity(cartItemId, quantity, unitPrice, lineDiscount)
    suspend fun removePersistentCartItem(cartItemId: Long) = appDao.deleteCartItem(cartItemId)
    suspend fun deleteCartItemByProduct(productId: Long, sessionId: String = "ACTIVE") = appDao.deleteCartItemByProduct(productId, sessionId)
    suspend fun getCartItemByProduct(productId: Long, sessionId: String = "ACTIVE"): CartItemEntity? = appDao.getCartItemByProduct(productId, sessionId)
    suspend fun clearPersistentCart(sessionId: String = "ACTIVE") = appDao.clearCart(sessionId)
    suspend fun holdActiveCart(heldSessionId: String) = appDao.transferCartSession(fromSessionId = "ACTIVE", toSessionId = heldSessionId)
    suspend fun restoreHeldCart(heldSessionId: String) {
        appDao.clearCart("ACTIVE")
        appDao.transferCartSession(fromSessionId = heldSessionId, toSessionId = "ACTIVE")
    }

    // --- AUDIT LOGGING ---
    suspend fun recordAuditLog(
        actionType: String,
        entityType: String,
        entityId: String,
        details: String,
        username: String = "Admin",
        userRole: String = "CASHIER",
        isCritical: Boolean = false
    ): Long = appDao.insertAuditLog(
        AuditLogEntity(
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            details = details,
            username = username,
            userRole = userRole,
            timestamp = System.currentTimeMillis(),
            isCritical = isCritical
        )
    )

    fun getAllAuditLogs(): Flow<List<AuditLogEntity>> = appDao.getAllAuditLogs()
    fun getAuditLogsByAction(actionType: String): Flow<List<AuditLogEntity>> = appDao.getAuditLogsByAction(actionType)
    fun getAuditLogsBetween(start: Long, end: Long): Flow<List<AuditLogEntity>> = appDao.getAuditLogsBetween(start, end)

    // --- PHYSICAL INVENTORY / STOCKTAKING (INVENTAIRE) ---
    val allInventories: Flow<List<InventoryEntity>>
        get() = requireStocktakingOps().allInventories
    val activeInventories: Flow<List<InventoryEntity>>
        get() = requireStocktakingOps().activeInventories
    val completedInventories: Flow<List<InventoryEntity>>
        get() = requireStocktakingOps().completedInventories
    val allInventoryAdjustments: Flow<List<InventoryAdjustmentEntity>>
        get() = requireStocktakingOps().allAdjustments

    fun getInventoryByIdFlow(inventoryId: Long): Flow<InventoryEntity?> =
        requireStocktakingOps().getInventoryByIdFlow(inventoryId)

    fun getInventoryItemsFlow(inventoryId: Long): Flow<List<InventoryItemEntity>> =
        requireStocktakingOps().getItemsForInventoryFlow(inventoryId)

    fun getInventoryAdjustmentsFlow(inventoryId: Long): Flow<List<InventoryAdjustmentEntity>> =
        requireStocktakingOps().getAdjustmentsForInventoryFlow(inventoryId)

    fun getProductInventoryAdjustmentsFlow(productId: Long): Flow<List<InventoryAdjustmentEntity>> =
        requireStocktakingOps().getAdjustmentsForProductFlow(productId)

    fun getInventoryDashboardStatsFlow(): Flow<com.example.domain.stocktaking.InventoryDashboardStats> =
        requireStocktakingOps().getDashboardStatsFlow()

    fun getCategoryValuationsFlow(): Flow<List<com.example.domain.stocktaking.CategoryValuationRow>> =
        requireStocktakingOps().getCategoryValuationsFlow()

    fun getSupplierValuationsFlow(): Flow<List<com.example.domain.stocktaking.SupplierValuationRow>> =
        requireStocktakingOps().getSupplierValuationsFlow()

    fun getStockMonitoringFlow(
        slowMovingDays: Int = 30,
        nowMillis: Long = System.currentTimeMillis()
    ): Flow<List<com.example.domain.stocktaking.StockMonitoringItem>> =
        requireStocktakingOps().getStockMonitoringFlow(slowMovingDays, nowMillis)

    suspend fun createInventorySession(
        type: com.example.domain.stocktaking.InventoryType,
        title: String = "",
        notes: String = "",
        categoryIds: List<Long> = emptyList(),
        productIds: List<Long> = emptyList(),
        supplierIds: List<Long> = emptyList(),
        duplicateScanMode: com.example.domain.stocktaking.DuplicateScanMode = com.example.domain.stocktaking.DuplicateScanMode.INCREMENT,
        startImmediately: Boolean = true,
        currentUser: UserEntity? = null
    ): InventoryEntity = requireStocktakingOps().createInventorySession(
        type = type,
        title = title,
        notes = notes,
        categoryIds = categoryIds,
        productIds = productIds,
        supplierIds = supplierIds,
        duplicateScanMode = duplicateScanMode,
        startImmediately = startImmediately,
        currentUser = currentUser
    )

    suspend fun addProductToActiveInventory(
        inventoryId: Long,
        productId: Long,
        currentUser: UserEntity? = null
    ): InventoryItemEntity = requireStocktakingOps().addProductToActiveInventory(inventoryId, productId, currentUser)

    suspend fun updateInventorySessionStatus(
        inventoryId: Long,
        newStatus: com.example.domain.stocktaking.InventoryStatus,
        currentUser: UserEntity? = null
    ): InventoryEntity = requireStocktakingOps().updateSessionStatus(inventoryId, newStatus, currentUser)

    suspend fun updateInventoryDuplicateScanMode(
        inventoryId: Long,
        mode: com.example.domain.stocktaking.DuplicateScanMode,
        currentUser: UserEntity? = null
    ) = requireStocktakingOps().updateDuplicateScanMode(inventoryId, mode, currentUser)

    suspend fun updateInventoryNotes(
        inventoryId: Long,
        notes: String,
        currentUser: UserEntity? = null
    ) = requireStocktakingOps().updateInventoryNotes(inventoryId, notes, currentUser)

    suspend fun recordInventoryItemCount(
        inventoryId: Long,
        productId: Long,
        countedQuantity: Double,
        notes: String? = null,
        currentUser: UserEntity? = null
    ): InventoryItemEntity = requireStocktakingOps().recordItemCount(inventoryId, productId, countedQuantity, notes, currentUser)

    suspend fun clearInventoryItemCount(
        inventoryId: Long,
        productId: Long,
        currentUser: UserEntity? = null
    ): InventoryItemEntity = requireStocktakingOps().clearItemCount(inventoryId, productId, currentUser)

    suspend fun handleInventoryBarcodeScan(
        inventoryId: Long,
        rawBarcode: String,
        incrementBy: Double = 1.0,
        overrideMode: com.example.domain.stocktaking.DuplicateScanMode? = null,
        currentUser: UserEntity? = null
    ): com.example.domain.stocktaking.BarcodeScanOutcome =
        requireStocktakingOps().handleBarcodeScan(inventoryId, rawBarcode, incrementBy, overrideMode, currentUser)

    suspend fun buildInventoryApprovalPreview(inventoryId: Long): com.example.domain.stocktaking.InventoryApprovalPreview =
        requireStocktakingOps().buildApprovalPreview(inventoryId)

    suspend fun approveAndApplyInventory(
        inventoryId: Long,
        reason: String = "",
        currentUser: UserEntity?
    ): InventoryEntity = requireStocktakingOps().approveAndApplyInventory(inventoryId, reason, currentUser)

    suspend fun cancelInventorySession(
        inventoryId: Long,
        reason: String = "",
        currentUser: UserEntity?
    ): InventoryEntity = requireStocktakingOps().cancelInventorySession(inventoryId, reason, currentUser)

    suspend fun updateInventorySession(
        inventoryId: Long,
        title: String,
        notes: String,
        duplicateScanMode: com.example.domain.stocktaking.DuplicateScanMode? = null,
        currentUser: UserEntity? = null
    ): InventoryEntity = requireStocktakingOps().updateInventorySession(
        inventoryId = inventoryId,
        title = title,
        notes = notes,
        duplicateScanMode = duplicateScanMode,
        currentUser = currentUser
    )

    suspend fun deleteInventorySession(
        inventoryId: Long,
        revertStockAdjustments: Boolean = false,
        reason: String = "",
        currentUser: UserEntity?
    ): Int = requireStocktakingOps().deleteInventorySession(
        inventoryId = inventoryId,
        revertStockAdjustments = revertStockAdjustments,
        reason = reason,
        currentUser = currentUser
    )

    suspend fun getInventoryHistoricalComparison(inventoryId: Long): com.example.domain.stocktaking.InventoryComparisonSummary? =
        requireStocktakingOps().getHistoricalComparison(inventoryId)
}
