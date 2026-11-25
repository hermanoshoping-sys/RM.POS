package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Escapes SQL LIKE wildcards (`\`, `%`, `_`) so user search input is matched literally
 * when paired with `ESCAPE '\'` in Room queries.
 */
fun escapeSqlLike(query: String): String {
    if (query.isEmpty()) return query
    return query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}

/**
 * Supermarket POS AppDao providing complete SQLite operations via Room.
 * Formatted for reactive UI updates using Kotlin Flow.
 */
@Dao
interface AppDao {
    @Query("SELECT * FROM sales WHERE timestamp > :lastSync")
    suspend fun getSalesAfterTimestamp(lastSync: Long): List<SaleEntity>

    // --- USERS ---
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(:username)) LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Long)

    @Query("UPDATE sales SET cashierName = :newName WHERE LOWER(TRIM(cashierName)) = LOWER(TRIM(:oldName))")
    suspend fun updateSalesCashierName(oldName: String, newName: String)

    @Query("UPDATE transactions SET cashierName = :newName WHERE LOWER(TRIM(cashierName)) = LOWER(TRIM(:oldName))")
    suspend fun updateTransactionsCashierName(oldName: String, newName: String)

    @Query("UPDATE customer_payments SET cashierName = :newName WHERE LOWER(TRIM(cashierName)) = LOWER(TRIM(:oldName))")
    suspend fun updateCustomerPaymentsCashierName(oldName: String, newName: String)

    @Query("UPDATE supplier_payments SET cashierName = :newName WHERE LOWER(TRIM(cashierName)) = LOWER(TRIM(:oldName))")
    suspend fun updateSupplierPaymentsCashierName(oldName: String, newName: String)


    // --- CATEGORIES ---
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("UPDATE categories SET imageUri = :imageUri WHERE id = :categoryId")
    suspend fun updateCategoryImage(categoryId: Long, imageUri: String?)

    @Query("UPDATE products SET categoryId = 0, categoryName = '' WHERE categoryId = :categoryId")
    suspend fun clearProductsCategory(categoryId: Long)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: Long)

    @Query("DELETE FROM categories")
    suspend fun clearAllCategories()

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?


    // --- PRODUCTS ---
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC LIMIT :limit OFFSET :offset")
    fun getProductsPaged(limit: Int, offset: Int): Flow<List<ProductEntity>>

    @Query("""
        SELECT * FROM products
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:escapedQuery = '' OR name LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR barcode LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR sku LIKE '%' || :escapedQuery || '%' ESCAPE '\')
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
    """)
    fun getProductsFilteredPagedRaw(escapedQuery: String, categoryId: Long?, limit: Int, offset: Int): Flow<List<ProductEntity>>

    fun getProductsFilteredPaged(query: String, categoryId: Long?, limit: Int, offset: Int): Flow<List<ProductEntity>> =
        getProductsFilteredPagedRaw(escapeSqlLike(query.trim()), categoryId, limit, offset)

    @Query("""
        SELECT COUNT(*) FROM products
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:escapedQuery = '' OR name LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR barcode LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR sku LIKE '%' || :escapedQuery || '%' ESCAPE '\')
    """)
    fun getProductsFilteredCountFlowRaw(escapedQuery: String, categoryId: Long?): Flow<Int>

    fun getProductsFilteredCountFlow(query: String, categoryId: Long?): Flow<Int> =
        getProductsFilteredCountFlowRaw(escapeSqlLike(query.trim()), categoryId)

    @Query("SELECT COUNT(*) FROM products")
    fun getProductsTotalCountFlow(): Flow<Int>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id IN (:productIds)")
    suspend fun getProductsByIds(productIds: List<Long>): List<ProductEntity>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :escapedQuery || '%' ESCAPE '\\' OR barcode LIKE '%' || :escapedQuery || '%' ESCAPE '\\' ORDER BY name ASC")
    fun searchProductsRaw(escapedQuery: String): Flow<List<ProductEntity>>

    fun searchProducts(query: String): Flow<List<ProductEntity>> =
        searchProductsRaw(escapeSqlLike(query.trim()))

    @Query("SELECT * FROM products WHERE categoryId = :catId ORDER BY name ASC")
    fun getProductsByCategory(catId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE currentStock <= minStock")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>): List<Long>

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET imageUri = :imageUri WHERE id = :productId")
    suspend fun updateProductImage(productId: Long, imageUri: String?)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProduct(productId: Long)

    @Query("DELETE FROM products WHERE id IN (:productIds)")
    suspend fun deleteProductsBatch(productIds: List<Long>)

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()

    @Query("UPDATE products SET currentStock = currentStock + :qty WHERE id = :productId")
    suspend fun adjustProductStock(productId: Long, qty: Double)


    // --- CUSTOMERS ---
    @Query("SELECT * FROM customers ORDER BY customerNumber ASC, id ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("""
        SELECT * FROM customers
        WHERE (:escapedQuery = '' OR name LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR phone LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR barcode LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR CAST(customerNumber AS TEXT) LIKE '%' || :escapedQuery || '%' ESCAPE '\')
        ORDER BY customerNumber ASC, id ASC
        LIMIT :limit OFFSET :offset
    """)
    fun getCustomersFilteredPagedRaw(escapedQuery: String, limit: Int, offset: Int): Flow<List<CustomerEntity>>

    fun getCustomersFilteredPaged(query: String, limit: Int, offset: Int): Flow<List<CustomerEntity>> =
        getCustomersFilteredPagedRaw(escapeSqlLike(query.trim()), limit, offset)

    @Query("""
        SELECT COUNT(*) FROM customers
        WHERE (:escapedQuery = '' OR name LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR phone LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR barcode LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR CAST(customerNumber AS TEXT) LIKE '%' || :escapedQuery || '%' ESCAPE '\')
    """)
    fun getCustomersFilteredCountFlowRaw(escapedQuery: String): Flow<Int>

    fun getCustomersFilteredCountFlow(query: String): Flow<Int> =
        getCustomersFilteredCountFlowRaw(escapeSqlLike(query.trim()))

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE barcode = :barcode LIMIT 1")
    suspend fun getCustomerByBarcode(barcode: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE customerNumber = :number LIMIT 1")
    suspend fun getCustomerByNumber(number: Long): CustomerEntity?

    @Query("SELECT MAX(customerNumber) FROM customers")
    suspend fun getMaxCustomerNumberFromCustomers(): Long?

    @Query("SELECT lastCustomerNumber FROM customer_sequences WHERE id = 1 LIMIT 1")
    suspend fun getLastCustomerNumber(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCustomerSequence(sequence: CustomerSequenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Query("UPDATE customers SET balance = balance + :amountCents WHERE id = :customerId")
    suspend fun adjustCustomerBalance(customerId: Long, amountCents: Long)

    suspend fun adjustCustomerBalance(customerId: Long, amount: Double) =
        adjustCustomerBalance(customerId, com.example.util.MoneyUtils.toCents(amount))

    @Query("UPDATE customers SET totalPurchases = totalPurchases + :amountCents WHERE id = :customerId")
    suspend fun addToCustomerTotalPurchases(customerId: Long, amountCents: Long)

    suspend fun addToCustomerTotalPurchases(customerId: Long, amount: Double) =
        addToCustomerTotalPurchases(customerId, com.example.util.MoneyUtils.toCents(amount))

    @Query("DELETE FROM customers WHERE id = :customerId")
    suspend fun deleteCustomer(customerId: Long)

    @Query("DELETE FROM customers WHERE id IN (:customerIds)")
    suspend fun deleteCustomersBatch(customerIds: List<Long>)

    @Query("DELETE FROM customers")
    suspend fun clearAllCustomers()

    @Query("SELECT COUNT(*) FROM sales WHERE customerId = :customerId AND isVoided = 0")
    suspend fun getCustomerActiveSalesCount(customerId: Long): Int

    @Query("SELECT COUNT(*) FROM customer_payments WHERE customerId = :customerId")
    suspend fun getCustomerPaymentsCount(customerId: Long): Int


    // --- SUPPLIERS ---
    @Query("SELECT * FROM suppliers")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: Long): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Query("UPDATE suppliers SET debtBalance = debtBalance + :amountCents WHERE id = :supplierId")
    suspend fun adjustSupplierDebt(supplierId: Long, amountCents: Long)

    suspend fun adjustSupplierDebt(supplierId: Long, amount: Double) =
        adjustSupplierDebt(supplierId, com.example.util.MoneyUtils.toCents(amount))

    @Query("DELETE FROM suppliers WHERE id = :supplierId")
    suspend fun deleteSupplier(supplierId: Long)

    @Query("DELETE FROM suppliers")
    suspend fun clearAllSuppliers()

    @Query("SELECT COUNT(*) FROM purchases WHERE supplierId = :supplierId AND isVoided = 0")
    suspend fun getSupplierActivePurchasesCount(supplierId: Long): Int

    @Query("SELECT COUNT(*) FROM supplier_payments WHERE supplierId = :supplierId")
    suspend fun getSupplierPaymentsCount(supplierId: Long): Int


    // --- PURCHASES ---
    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getPurchasesPaged(limit: Int, offset: Int): Flow<List<PurchaseEntity>>

    @Query("""
        SELECT * FROM purchases
        WHERE (:escapedQuery = '' OR supplierName LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR invoiceNumber LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR ('PUR-' || id) LIKE '%' || :escapedQuery || '%' ESCAPE '\')
          AND (:statusFilter = 'ALL'
               OR (:statusFilter = 'PAID' AND (totalAmount - paidAmount) <= 0)
               OR (:statusFilter = 'DEBT' AND (totalAmount - paidAmount) > 0))
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getPurchasesFilteredPagedRaw(escapedQuery: String, statusFilter: String, limit: Int, offset: Int): Flow<List<PurchaseEntity>>

    fun getPurchasesFilteredPaged(query: String, statusFilter: String, limit: Int, offset: Int): Flow<List<PurchaseEntity>> =
        getPurchasesFilteredPagedRaw(escapeSqlLike(query.trim()), statusFilter, limit, offset)

    @Query("""
        SELECT
            COUNT(*) AS count,
            COALESCE(SUM(totalAmount), 0) AS totalCents,
            COALESCE(SUM(CASE WHEN paidAmount > totalAmount THEN totalAmount ELSE paidAmount END), 0) AS paidCents,
            COALESCE(SUM(CASE WHEN totalAmount > paidAmount THEN totalAmount - paidAmount ELSE 0 END), 0) AS debtCents
        FROM purchases
        WHERE (:escapedQuery = '' OR supplierName LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR invoiceNumber LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR ('PUR-' || id) LIKE '%' || :escapedQuery || '%' ESCAPE '\')
          AND (:statusFilter = 'ALL'
               OR (:statusFilter = 'PAID' AND (totalAmount - paidAmount) <= 0)
               OR (:statusFilter = 'DEBT' AND (totalAmount - paidAmount) > 0))
    """)
    fun getPurchasesFilteredSummaryFlowRaw(escapedQuery: String, statusFilter: String): Flow<PurchaseLedgerSqlSummary>

    fun getPurchasesFilteredSummaryFlow(query: String, statusFilter: String): Flow<PurchaseLedgerSqlSummary> =
        getPurchasesFilteredSummaryFlowRaw(escapeSqlLike(query.trim()), statusFilter)

    @Query("SELECT * FROM purchases WHERE id = :purchaseId LIMIT 1")
    suspend fun getPurchaseById(purchaseId: Long): PurchaseEntity?

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun getPurchaseItems(purchaseId: Long): List<PurchaseItemEntity>

    @Query("SELECT * FROM purchase_items ORDER BY id DESC")
    fun getAllPurchaseItems(): Flow<List<PurchaseItemEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Update
    suspend fun updatePurchase(purchase: PurchaseEntity)

    @Query("UPDATE purchases SET isVoided = 1 WHERE id = :purchaseId")
    suspend fun voidPurchase(purchaseId: Long)

    @Query("UPDATE purchases SET timestamp = :newTimestamp WHERE id = :purchaseId")
    suspend fun updatePurchaseTimestamp(purchaseId: Long, newTimestamp: Long)

    @Query("UPDATE purchases SET invoiceNumber = :newInvoiceNumber WHERE id = :purchaseId")
    suspend fun updatePurchaseInvoiceNumber(purchaseId: Long, newInvoiceNumber: String)

    @Query("UPDATE purchases SET supplierId = :newSupplierId, supplierName = :newSupplierName WHERE id = :purchaseId")
    suspend fun updatePurchaseSupplier(purchaseId: Long, newSupplierId: Long, newSupplierName: String)

    @Query("DELETE FROM purchases WHERE id = :purchaseId")
    suspend fun deletePurchase(purchaseId: Long)

    @Query("DELETE FROM purchases")
    suspend fun clearAllPurchases()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseItem(item: PurchaseItemEntity): Long

    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun deletePurchaseItemsByPurchaseId(purchaseId: Long)

    @Query("DELETE FROM purchase_items")
    suspend fun clearAllPurchaseItems()


    // --- SALES ---
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getSalesPaged(limit: Int, offset: Int): Flow<List<SaleEntity>>

    @Query("""
        SELECT * FROM sales
        WHERE (:escapedQuery = '' OR invoiceNumber LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR customerName LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR cashierName LIKE '%' || :escapedQuery || '%' ESCAPE '\')
          AND (:statusFilter = 'ALL'
               OR (:statusFilter = 'PAID' AND (totalAmount - paidAmount) <= 0)
               OR (:statusFilter = 'DEBT' AND (totalAmount - paidAmount) > 0))
          AND (:seller = 'ALL'
               OR LOWER(TRIM(cashierName)) = LOWER(TRIM(:seller)))
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    fun getSalesFilteredPagedRaw(escapedQuery: String, statusFilter: String, seller: String, limit: Int, offset: Int): Flow<List<SaleEntity>>

    fun getSalesFilteredPaged(query: String, statusFilter: String, seller: String, limit: Int, offset: Int): Flow<List<SaleEntity>> =
        getSalesFilteredPagedRaw(escapeSqlLike(query.trim()), statusFilter, seller, limit, offset)

    @Query("""
        SELECT
            COUNT(*) AS count,
            COALESCE(SUM(totalAmount), 0) AS totalCents,
            COALESCE(SUM(CASE WHEN paidAmount > totalAmount THEN totalAmount ELSE paidAmount END), 0) AS paidCents,
            COALESCE(SUM(CASE WHEN totalAmount > paidAmount THEN totalAmount - paidAmount ELSE 0 END), 0) AS debtCents
        FROM sales
        WHERE (:escapedQuery = '' OR invoiceNumber LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR customerName LIKE '%' || :escapedQuery || '%' ESCAPE '\' OR cashierName LIKE '%' || :escapedQuery || '%' ESCAPE '\')
          AND (:statusFilter = 'ALL'
               OR (:statusFilter = 'PAID' AND (totalAmount - paidAmount) <= 0)
               OR (:statusFilter = 'DEBT' AND (totalAmount - paidAmount) > 0))
          AND (:seller = 'ALL'
               OR LOWER(TRIM(cashierName)) = LOWER(TRIM(:seller)))
    """)
    fun getSalesFilteredSummaryFlowRaw(escapedQuery: String, statusFilter: String, seller: String): Flow<SalesLedgerSqlSummary>

    fun getSalesFilteredSummaryFlow(query: String, statusFilter: String, seller: String): Flow<SalesLedgerSqlSummary> =
        getSalesFilteredSummaryFlowRaw(escapeSqlLike(query.trim()), statusFilter, seller)

    @Query("""
        SELECT TRIM(cashierName) AS seller,
               COUNT(*) AS invoiceCount
        FROM sales
        WHERE TRIM(cashierName) != ''
        GROUP BY TRIM(cashierName)
        ORDER BY seller ASC
    """)
    fun getSalesCountBySellerFlow(): Flow<List<SellerSalesCountRecord>>

    @Query("SELECT * FROM sales ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSales(limit: Int): Flow<List<SaleEntity>>

    @Query("SELECT COUNT(*) FROM sales WHERE isVoided = 0")
    fun getActiveSalesCountFlow(): Flow<Int>

    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM sales WHERE isVoided = 0")
    fun getTotalSalesSumFlow(): Flow<Double?>

    @Query("SELECT * FROM sales WHERE customerId = :customerId AND isVoided = 0 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getSalesForCustomerPaged(customerId: Long, limit: Int, offset: Int): Flow<List<SaleEntity>>

    @Query("SELECT COUNT(*) FROM sales WHERE customerId = :customerId AND isVoided = 0")
    fun getSalesCountForCustomerFlow(customerId: Long): Flow<Int>

    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM sales WHERE customerId = :customerId AND isVoided = 0")
    fun getSalesSumForCustomerFlow(customerId: Long): Flow<Double?>

    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleById(saleId: Long): SaleEntity?

    @Query("SELECT * FROM sales WHERE invoiceNumber = :invNum LIMIT 1")
    suspend fun getSaleByInvoiceNumber(invNum: String): SaleEntity?

    @Query("SELECT invoiceNumber FROM sales")
    suspend fun getAllInvoiceNumbers(): List<String>

    @Query("SELECT lastInvoiceNumber FROM invoice_sequences WHERE id = 1 LIMIT 1")
    suspend fun getLastInvoiceNumber(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setInvoiceSequence(sequence: InvoiceSequenceEntity)

    @Query("SELECT MAX(id) FROM sales")
    suspend fun getMaxSaleId(): Long?

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun getSalesCount(): Int

    @Query("UPDATE sales SET invoiceNumber = :newInvoiceNumber WHERE id = :saleId")
    suspend fun updateSaleInvoiceNumber(saleId: Long, newInvoiceNumber: String)

    @Query("UPDATE sales SET customerId = :newCustomerId, customerName = :newCustomerName WHERE id = :saleId")
    suspend fun updateSaleCustomer(saleId: Long, newCustomerId: Long, newCustomerName: String)

    @Query("SELECT COUNT(*) FROM sales WHERE invoiceNumber = :invoiceNumber AND id != :saleId")
    suspend fun isSaleInvoiceNumberTaken(invoiceNumber: String, saleId: Long): Int

    @Query("UPDATE sales SET timestamp = :newTimestamp WHERE id = :saleId")
    suspend fun updateSaleTimestamp(saleId: Long, newTimestamp: Long)

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity>

    @Query("""
        SELECT 
            s.id AS saleId,
            s.invoiceNumber AS invoiceNumber,
            s.timestamp AS timestamp,
            s.customerName AS customerName,
            si.productId AS productId,
            si.productName AS productName,
            si.quantity AS quantity,
            (CAST(si.unitPrice AS REAL) / 100.0) AS sellPrice,
            (CAST(COALESCE(CASE WHEN si.unitCost > 0 THEN si.unitCost ELSE p.purchasePrice END, 0) AS REAL) / 100.0) AS purchasePrice,
            (CAST(si.totalLinePrice AS REAL) / 100.0) AS totalSale,
            ((CAST((si.unitPrice - COALESCE(CASE WHEN si.unitCost > 0 THEN si.unitCost ELSE p.purchasePrice END, 0)) AS REAL) / 100.0) * si.quantity) AS profit
        FROM sale_items si
        INNER JOIN sales s ON si.saleId = s.id
        LEFT JOIN products p ON si.productId = p.id
        WHERE s.isVoided = 0
        ORDER BY s.timestamp DESC
    """)
    fun getAllProductSalesHistory(): Flow<List<ProductSaleHistoryRecord>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SaleEntity): Long

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItem(item: SaleItemEntity): Long

    @Query("UPDATE sales SET isVoided = 1 WHERE id = :saleId")
    suspend fun voidSale(saleId: Long)

    @Query("DELETE FROM sales WHERE id = :saleId")
    suspend fun deleteSale(saleId: Long)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteSaleItemsBySaleId(saleId: Long)

    @Query("DELETE FROM sales")
    suspend fun clearAllSales()

    @Query("DELETE FROM sale_items")
    suspend fun clearAllSaleItems()


    // --- STOCK MOVEMENTS ---
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC")
    fun getAllStockMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getStockMovementsPaged(limit: Int, offset: Int): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentStockMovements(limit: Int): Flow<List<StockMovementEntity>>

    @Query("SELECT COUNT(*) FROM stock_movements")
    fun getStockMovementsCountFlow(): Flow<Int>

    @Query("SELECT * FROM stock_movements WHERE referenceType = :refType ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getStockMovementsByReferenceTypePaged(refType: String, limit: Int, offset: Int): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockMovement(movement: StockMovementEntity): Long

    @Query("DELETE FROM stock_movements WHERE referenceType = :refType AND referenceId = :refId")
    suspend fun deleteStockMovementsByReference(refType: String, refId: Long)

    @Query("SELECT * FROM stock_movements WHERE referenceType = :refType AND referenceId = :refId")
    suspend fun getStockMovementsByReference(refType: String, refId: Long): List<StockMovementEntity>

    @Query("""
        UPDATE stock_movements
        SET referenceType = 'SALE_RETURN',
            referenceId = CAST(SUBSTR(notes, INSTR(notes, 'Facture #') + 9) AS INTEGER)
        WHERE type = 'CUSTOMER_RETURN'
          AND (referenceType = '' OR referenceId = 0)
          AND INSTR(notes, 'Facture #') > 0
          AND CAST(SUBSTR(notes, INSTR(notes, 'Facture #') + 9) AS INTEGER) > 0
    """)
    suspend fun backfillLegacyCustomerReturnReferences(): Int

    @Query("DELETE FROM stock_movements WHERE notes LIKE '%' || :escapedKeyword || '%' ESCAPE '\\'")
    suspend fun deleteStockMovementsByKeywordRaw(escapedKeyword: String)

    suspend fun deleteStockMovementsByKeyword(keyword: String) {
        deleteStockMovementsByKeywordRaw(escapeSqlLike(keyword))
    }

    @Query("DELETE FROM stock_movements")
    suspend fun clearAllStockMovements()


    // --- EXPENSES ---
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpense(expenseId: Long)

    @Query("DELETE FROM expenses")
    suspend fun clearAllExpenses()


    // --- APP SETTINGS ---
    @Query("SELECT * FROM global_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): GlobalSettingsEntity?

    @Query("SELECT * FROM global_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<GlobalSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: GlobalSettingsEntity)


    // --- DASHBOARD AND REPORT AGGREGATE QUERIES (Today's performance) ---
    // Total Sales Amount (non-voided) between timestamps
    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM sales WHERE timestamp >= :start AND timestamp <= :end AND isVoided = 0")
    fun getSalesSumBetween(start: Long, end: Long): Flow<Double?>

    @Query("SELECT SUM(totalAmount) FROM sales WHERE timestamp >= :start AND timestamp <= :end AND isVoided = 0")
    fun getSalesSumCentsBetween(start: Long, end: Long): Flow<Long?>

    // Today's Profit
    @Query("""
        SELECT (CAST(SUM(si.totalLinePrice - (si.quantity * CASE WHEN si.unitCost > 0 THEN si.unitCost ELSE p.purchasePrice END)) AS REAL) / 100.0)
        FROM sale_items si
        INNER JOIN sales s ON si.saleId = s.id
        LEFT JOIN products p ON si.productId = p.id
        WHERE s.timestamp >= :start AND s.timestamp <= :end AND s.isVoided = 0
    """)
    fun getProfitSumBetween(start: Long, end: Long): Flow<Double?>

    // Total Invoices count
    @Query("SELECT COUNT(id) FROM sales WHERE timestamp >= :start AND timestamp <= :end AND isVoided = 0")
    fun getSalesCountBetween(start: Long, end: Long): Flow<Int?>

    // Total Cash Sales Sum
    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM sales WHERE timestamp >= :start AND timestamp <= :end AND paymentMethod = 'CASH' AND isVoided = 0")
    fun getCashSalesSumBetween(start: Long, end: Long): Flow<Double?>

    @Query("SELECT SUM(totalAmount) FROM sales WHERE timestamp >= :start AND timestamp <= :end AND paymentMethod = 'CASH' AND isVoided = 0")
    fun getCashSalesSumCentsBetween(start: Long, end: Long): Flow<Long?>

    // Total Card Sales Sum
    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM sales WHERE timestamp >= :start AND timestamp <= :end AND paymentMethod = 'CARD' AND isVoided = 0")
    fun getCardSalesSumBetween(start: Long, end: Long): Flow<Double?>

    @Query("SELECT SUM(totalAmount) FROM sales WHERE timestamp >= :start AND timestamp <= :end AND paymentMethod = 'CARD' AND isVoided = 0")
    fun getCardSalesSumCentsBetween(start: Long, end: Long): Flow<Long?>

    // Total Expenses between timestamps
    @Query("SELECT (CAST(SUM(amount) AS REAL) / 100.0) FROM expenses WHERE timestamp >= :start AND timestamp <= :end")
    fun getExpensesSumBetween(start: Long, end: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :start AND timestamp <= :end")
    fun getExpensesSumCentsBetween(start: Long, end: Long): Flow<Long?>

    @Query("SELECT SUM(balance) FROM customers")
    fun getTotalCustomerDebtCents(): Flow<Long?>

    @Query("SELECT SUM(debtBalance) FROM suppliers")
    fun getTotalSupplierDebtCents(): Flow<Long?>

    // Count low stock
    @Query("SELECT COUNT(id) FROM products WHERE currentStock <= minStock")
    fun getLowStockCount(): Flow<Int?>
    @Query("SELECT * FROM products WHERE currentStock <= minStock")
    suspend fun getLowStockProductsWorker(): List<ProductEntity>

    // Top Selling Items (returns temporary structural records for reports)
    @Query("SELECT productId, productName, SUM(quantity) as totalQty, (CAST(SUM(totalLinePrice) AS REAL) / 100.0) as totalRevenue FROM sale_items GROUP BY productId ORDER BY totalQty DESC LIMIT 10")
    fun getTopSellingProducts(): Flow<List<TopProductRecord>>

    // --- COMPLETE BACKUP & RESTORE DAO METHODS ---
    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesList(): List<CategoryEntity>

    @Query("SELECT * FROM products")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersList(): List<CustomerEntity>

    @Query("SELECT * FROM suppliers")
    suspend fun getAllSuppliersList(): List<SupplierEntity>

    @Query("SELECT * FROM sales")
    suspend fun getAllSalesList(): List<SaleEntity>

    @Query("SELECT * FROM sale_items")
    suspend fun getAllSaleItemsList(): List<SaleItemEntity>

    @Query("SELECT * FROM purchases")
    suspend fun getAllPurchasesList(): List<PurchaseEntity>

    @Query("SELECT * FROM purchase_items")
    suspend fun getAllPurchaseItemsList(): List<PurchaseItemEntity>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesList(): List<ExpenseEntity>

    @Query("SELECT * FROM users")
    suspend fun getAllUsersList(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsersBatch(users: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoriesBatch(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductsBatch(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomersBatch(customers: List<CustomerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuppliersBatch(suppliers: List<SupplierEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesBatch(sales: List<SaleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItemsBatch(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchasesBatch(purchases: List<PurchaseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItemsBatch(items: List<PurchaseItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpensesBatch(expenses: List<ExpenseEntity>)

    // --- CUSTOMER PAYMENTS ---
    @Query("SELECT * FROM customer_payments ORDER BY timestamp DESC")
    fun getAllCustomerPayments(): Flow<List<CustomerPaymentEntity>>

    @Query("SELECT * FROM customer_payments ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getCustomerPaymentsPaged(limit: Int, offset: Int): Flow<List<CustomerPaymentEntity>>

    @Query("SELECT * FROM customer_payments WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getCustomerPaymentsByCustomerId(customerId: Long): Flow<List<CustomerPaymentEntity>>

    @Query("SELECT * FROM customer_payments WHERE id = :id LIMIT 1")
    suspend fun getCustomerPaymentById(id: Long): CustomerPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomerPayment(payment: CustomerPaymentEntity): Long

    @Update
    suspend fun updateCustomerPayment(payment: CustomerPaymentEntity)

    @Query("DELETE FROM customer_payments WHERE id = :id")
    suspend fun deleteCustomerPayment(id: Long)

    @Query("DELETE FROM customer_payments WHERE saleId = :saleId AND saleId > 0")
    suspend fun deleteCustomerPaymentsBySaleId(saleId: Long)

    @Query("SELECT * FROM customer_payments WHERE saleId = :saleId")
    suspend fun getCustomerPaymentsBySaleId(saleId: Long): List<CustomerPaymentEntity>

    @Query("DELETE FROM customer_payments")
    suspend fun clearAllCustomerPayments()

    @Query("SELECT * FROM customer_payments ORDER BY timestamp DESC")
    suspend fun getAllCustomerPaymentsList(): List<CustomerPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerPaymentsBatch(payments: List<CustomerPaymentEntity>)

    // --- SUPPLIER PAYMENTS ---
    @Query("SELECT * FROM supplier_payments ORDER BY timestamp DESC")
    fun getAllSupplierPayments(): Flow<List<SupplierPaymentEntity>>

    @Query("SELECT * FROM supplier_payments ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getSupplierPaymentsPaged(limit: Int, offset: Int): Flow<List<SupplierPaymentEntity>>

    @Query("SELECT * FROM supplier_payments WHERE supplierId = :supplierId ORDER BY timestamp DESC")
    fun getSupplierPaymentsBySupplierId(supplierId: Long): Flow<List<SupplierPaymentEntity>>

    @Query("SELECT * FROM supplier_payments WHERE id = :id LIMIT 1")
    suspend fun getSupplierPaymentById(id: Long): SupplierPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierPayment(payment: SupplierPaymentEntity): Long

    @Update
    suspend fun updateSupplierPayment(payment: SupplierPaymentEntity)

    @Query("DELETE FROM supplier_payments WHERE id = :id")
    suspend fun deleteSupplierPayment(id: Long)

    @Query("DELETE FROM supplier_payments WHERE purchaseId = :purchaseId AND purchaseId > 0")
    suspend fun deleteSupplierPaymentsByPurchaseId(purchaseId: Long)

    @Query("SELECT * FROM supplier_payments WHERE purchaseId = :purchaseId")
    suspend fun getSupplierPaymentsByPurchaseId(purchaseId: Long): List<SupplierPaymentEntity>

    @Query("DELETE FROM supplier_payments")
    suspend fun clearAllSupplierPayments()

    @Query("SELECT * FROM supplier_payments ORDER BY timestamp DESC")
    suspend fun getAllSupplierPaymentsList(): List<SupplierPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplierPaymentsBatch(payments: List<SupplierPaymentEntity>)

    // --- ORDERS & ORDER ITEMS (OFFLINE-FIRST POS SCHEMA) ---
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Transaction
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE orderNumber = :orderNumber LIMIT 1")
    suspend fun getOrderByNumber(orderNumber: String): OrderEntity?

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    fun getOrderWithItems(orderId: Long): Flow<OrderWithItems?>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItems(orderId: Long): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItemsBatch(items: List<OrderItemEntity>)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String)

    @Query("UPDATE orders SET isVoided = 1, status = 'CANCELLED' WHERE id = :orderId")
    suspend fun voidOrder(orderId: Long)

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItems(orderId: Long)

    @Query("SELECT * FROM orders WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getOrdersByCustomerId(customerId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE timestamp >= :startTime AND timestamp <= :endTime AND isVoided = 0 ORDER BY timestamp DESC")
    fun getOrdersBetween(startTime: Long, endTime: Long): Flow<List<OrderEntity>>

    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM orders WHERE timestamp >= :startTime AND timestamp <= :endTime AND isVoided = 0")
    fun getTotalOrdersRevenueBetween(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM orders WHERE isVoided = 0")
    fun getActiveOrdersCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrdersBatch(orders: List<OrderEntity>)

    // --- TRANSACTIONS (OFFLINE-FIRST SCHEMA) ---
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsWithItems(): Flow<List<TransactionWithItems>>

    @Query("SELECT * FROM transactions WHERE id = :txId LIMIT 1")
    suspend fun getTransactionById(txId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transactionNumber = :txNumber LIMIT 1")
    suspend fun getTransactionByNumber(txNumber: String): TransactionEntity?

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :txId LIMIT 1")
    fun getTransactionWithItems(txId: Long): Flow<TransactionWithItems?>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :txId")
    suspend fun getTransactionItems(txId: Long): List<TransactionItemEntity>

    @Query("SELECT * FROM transactions WHERE isSynced = 0 ORDER BY timestamp ASC")
    fun getUnsyncedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedTransactionsList(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItem(item: TransactionItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItemsBatch(items: List<TransactionItemEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isSynced = 1, status = 'SYNCED', updatedAt = :updatedAt WHERE id = :txId")
    suspend fun markTransactionAsSynced(txId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isVoided = 1, status = 'CANCELLED', updatedAt = :updatedAt WHERE id = :txId")
    suspend fun voidTransaction(txId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE id = :txId")
    suspend fun deleteTransaction(txId: Long)

    @Query("DELETE FROM transaction_items WHERE transactionId = :txId")
    suspend fun deleteTransactionItems(txId: Long)

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getTransactionsByCustomerId(customerId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime AND isVoided = 0 ORDER BY timestamp DESC")
    fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime AND isVoided = 0")
    fun getTotalTransactionsRevenueBetween(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM transactions WHERE isVoided = 0")
    fun getActiveTransactionsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsBatch(transactions: List<TransactionEntity>)

    // --- CART ITEMS (OFFLINE-FIRST SCHEMA) ---
    @Query("SELECT * FROM cart_items WHERE cartSessionId = :sessionId ORDER BY timestamp ASC")
    fun getCartItems(sessionId: String = "ACTIVE"): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items WHERE cartSessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getCartItemsList(sessionId: String = "ACTIVE"): List<CartItemEntity>

    @Query("SELECT * FROM cart_items WHERE id = :id LIMIT 1")
    suspend fun getCartItemById(id: Long): CartItemEntity?

    @Query("SELECT * FROM cart_items WHERE productId = :productId AND cartSessionId = :sessionId LIMIT 1")
    suspend fun getCartItemByProduct(productId: Long, sessionId: String = "ACTIVE"): CartItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItemsBatch(items: List<CartItemEntity>)

    @Update
    suspend fun updateCartItem(item: CartItemEntity)

    @Query("UPDATE cart_items SET quantity = :quantity, totalLinePrice = :totalLinePrice, timestamp = :timestamp WHERE id = :id")
    suspend fun updateCartItemQuantity(id: Long, quantity: Double, totalLinePrice: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun deleteCartItem(id: Long)

    @Query("DELETE FROM cart_items WHERE productId = :productId AND cartSessionId = :sessionId")
    suspend fun deleteCartItemByProduct(productId: Long, sessionId: String = "ACTIVE")

    @Query("DELETE FROM cart_items WHERE cartSessionId = :sessionId")
    suspend fun clearCart(sessionId: String = "ACTIVE")

    @Query("SELECT COUNT(*) FROM cart_items WHERE cartSessionId = :sessionId")
    fun getCartItemsCount(sessionId: String = "ACTIVE"): Flow<Int>

    @Query("SELECT (CAST(SUM(totalLinePrice) AS REAL) / 100.0) FROM cart_items WHERE cartSessionId = :sessionId")
    fun getCartTotal(sessionId: String = "ACTIVE"): Flow<Double?>

    @Query("SELECT DISTINCT cartSessionId FROM cart_items ORDER BY cartSessionId ASC")
    fun getAllCartSessions(): Flow<List<String>>

    @Query("UPDATE cart_items SET cartSessionId = :toSessionId WHERE cartSessionId = :fromSessionId")
    suspend fun transferCartSession(fromSessionId: String, toSessionId: String)

    // --- AUDIT LOGS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity): Long

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE actionType = :actionType ORDER BY timestamp DESC")
    fun getAuditLogsByAction(actionType: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp DESC")
    fun getAuditLogsBetween(start: Long, end: Long): Flow<List<AuditLogEntity>>

    @Query("DELETE FROM audit_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun purgeOldAuditLogs(cutoffTimestamp: Long)

    @Query("SELECT entityId FROM audit_logs WHERE entityType = 'SALE'")
    suspend fun getSaleInvoiceNumbersFromAuditLogs(): List<String>
}

/**
 * Dedicated DAO interface for Orders and Order Items offline-first schema.
 */
@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Transaction
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrdersWithItems(): Flow<List<OrderWithItems>>

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE orderNumber = :orderNumber LIMIT 1")
    suspend fun getOrderByNumber(orderNumber: String): OrderEntity?

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    fun getOrderWithItems(orderId: Long): Flow<OrderWithItems?>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItems(orderId: Long): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItemsBatch(items: List<OrderItemEntity>)

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String)

    @Query("UPDATE orders SET isVoided = 1, status = 'CANCELLED' WHERE id = :orderId")
    suspend fun voidOrder(orderId: Long)

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrder(orderId: Long)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItems(orderId: Long)

    @Query("SELECT * FROM orders WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getOrdersByCustomerId(customerId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE timestamp >= :startTime AND timestamp <= :endTime AND isVoided = 0 ORDER BY timestamp DESC")
    fun getOrdersBetween(startTime: Long, endTime: Long): Flow<List<OrderEntity>>

    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM orders WHERE timestamp >= :startTime AND timestamp <= :endTime AND isVoided = 0")
    fun getTotalOrdersRevenueBetween(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM orders WHERE isVoided = 0")
    fun getActiveOrdersCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrdersBatch(orders: List<OrderEntity>)
}

/**
 * Temporary Data class for top selling products report.
 */
data class TopProductRecord(
    val productId: Long,
    val productName: String,
    val totalQty: Double,
    val totalRevenue: Double
)

data class SalesLedgerSqlSummary(
    val count: Int,
    val totalCents: Long,
    val paidCents: Long,
    val debtCents: Long
) {
    val totalAmount: Double get() = com.example.util.MoneyUtils.fromCents(totalCents)
    val totalPaid: Double get() = com.example.util.MoneyUtils.fromCents(paidCents)
    val totalDebt: Double get() = com.example.util.MoneyUtils.fromCents(debtCents)
}

data class PurchaseLedgerSqlSummary(
    val count: Int,
    val totalCents: Long,
    val paidCents: Long,
    val debtCents: Long
) {
    val totalAmount: Double get() = com.example.util.MoneyUtils.fromCents(totalCents)
    val totalPaid: Double get() = com.example.util.MoneyUtils.fromCents(paidCents)
    val totalDebt: Double get() = com.example.util.MoneyUtils.fromCents(debtCents)
}

data class SellerSalesCountRecord(
    val seller: String,
    val invoiceCount: Int
)

/**
 * Dedicated DAO interface for Products offline-first operations.
 */
@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE name LIKE '%' || :escapedQuery || '%' ESCAPE '\\' OR barcode LIKE '%' || :escapedQuery || '%' ESCAPE '\\' OR sku LIKE '%' || :escapedQuery || '%' ESCAPE '\\' ORDER BY name ASC")
    fun searchProductsRaw(escapedQuery: String): Flow<List<ProductEntity>>

    fun searchProducts(query: String): Flow<List<ProductEntity>> =
        searchProductsRaw(escapeSqlLike(query.trim()))

    @Query("SELECT * FROM products WHERE categoryId = :catId ORDER BY name ASC")
    fun getProductsByCategory(catId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE currentStock <= minStock")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isSynced = 0")
    fun getUnsyncedProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductsBatch(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProduct(productId: Long)

    @Query("UPDATE products SET currentStock = currentStock + :qty, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun adjustProductStock(productId: Long, qty: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE products SET isSynced = 1, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun markProductAsSynced(productId: Long, updatedAt: Long = System.currentTimeMillis())
}

/**
 * Dedicated DAO interface for Transactions and Transaction Items offline-first schema.
 */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsWithItems(): Flow<List<TransactionWithItems>>

    @Query("SELECT * FROM transactions WHERE id = :txId LIMIT 1")
    suspend fun getTransactionById(txId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transactionNumber = :txNumber LIMIT 1")
    suspend fun getTransactionByNumber(txNumber: String): TransactionEntity?

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :txId LIMIT 1")
    fun getTransactionWithItems(txId: Long): Flow<TransactionWithItems?>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :txId")
    suspend fun getTransactionItems(txId: Long): List<TransactionItemEntity>

    @Query("SELECT * FROM transactions WHERE isSynced = 0 ORDER BY timestamp ASC")
    fun getUnsyncedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedTransactionsList(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItem(item: TransactionItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItemsBatch(items: List<TransactionItemEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isSynced = 1, status = 'SYNCED', updatedAt = :updatedAt WHERE id = :txId")
    suspend fun markTransactionAsSynced(txId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isVoided = 1, status = 'CANCELLED', updatedAt = :updatedAt WHERE id = :txId")
    suspend fun voidTransaction(txId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM transactions WHERE id = :txId")
    suspend fun deleteTransaction(txId: Long)

    @Query("DELETE FROM transaction_items WHERE transactionId = :txId")
    suspend fun deleteTransactionItems(txId: Long)

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getTransactionsByCustomerId(customerId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime AND isVoided = 0 ORDER BY timestamp DESC")
    fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT (CAST(SUM(totalAmount) AS REAL) / 100.0) FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime AND isVoided = 0")
    fun getTotalTransactionsRevenueBetween(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM transactions WHERE isVoided = 0")
    fun getActiveTransactionsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionsBatch(transactions: List<TransactionEntity>)
}

/**
 * Dedicated DAO interface for Cart Items offline-first schema.
 */
@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items WHERE cartSessionId = :sessionId ORDER BY timestamp ASC")
    fun getCartItems(sessionId: String = "ACTIVE"): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items WHERE cartSessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getCartItemsList(sessionId: String = "ACTIVE"): List<CartItemEntity>

    @Query("SELECT * FROM cart_items WHERE id = :id LIMIT 1")
    suspend fun getCartItemById(id: Long): CartItemEntity?

    @Query("SELECT * FROM cart_items WHERE productId = :productId AND cartSessionId = :sessionId LIMIT 1")
    suspend fun getCartItemByProduct(productId: Long, sessionId: String = "ACTIVE"): CartItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItemsBatch(items: List<CartItemEntity>)

    @Update
    suspend fun updateCartItem(item: CartItemEntity)

    @Query("UPDATE cart_items SET quantity = :quantity, totalLinePrice = :totalLinePrice, timestamp = :timestamp WHERE id = :id")
    suspend fun updateCartItemQuantity(id: Long, quantity: Double, totalLinePrice: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun deleteCartItem(id: Long)

    @Query("DELETE FROM cart_items WHERE productId = :productId AND cartSessionId = :sessionId")
    suspend fun deleteCartItemByProduct(productId: Long, sessionId: String = "ACTIVE")

    @Query("DELETE FROM cart_items WHERE cartSessionId = :sessionId")
    suspend fun clearCart(sessionId: String = "ACTIVE")

    @Query("SELECT COUNT(*) FROM cart_items WHERE cartSessionId = :sessionId")
    fun getCartItemsCount(sessionId: String = "ACTIVE"): Flow<Int>

    @Query("SELECT (CAST(SUM(totalLinePrice) AS REAL) / 100.0) FROM cart_items WHERE cartSessionId = :sessionId")
    fun getCartTotal(sessionId: String = "ACTIVE"): Flow<Double?>

    @Query("SELECT DISTINCT cartSessionId FROM cart_items ORDER BY cartSessionId ASC")
    fun getAllCartSessions(): Flow<List<String>>

    @Query("UPDATE cart_items SET cartSessionId = :toSessionId WHERE cartSessionId = :fromSessionId")
    suspend fun transferCartSession(fromSessionId: String, toSessionId: String)
}
