package com.example.data

import com.example.domain.FinancialCalculator
import com.example.util.MoneyUtils
import com.example.util.cents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal class RepositorySalesOperations(
    private val appDao: AppDao,
    private val runInTransaction: suspend (suspend () -> Any?) -> Any?,
    private val recordAuditLog: suspend (String, String, String, String, String, String, Boolean) -> Long,
    private val logError: (String, Throwable?) -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> tx(block: suspend () -> T): T = runInTransaction { block() } as T

    suspend fun recordSaleInvoice(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        currentUser: UserEntity? = null
    ): SaleEntity = tx {
        if (currentUser == null || !currentUser.hasSellAccess()) {
            throw SecurityException("صلاحية إجراء عمليات البيع غير ممنوحة لهذا المستخدم (${currentUser?.username ?: "غير مصادق"})")
        }
        val finalInvoice = if (sale.invoiceNumber.isEmpty()) {
            val currentSeq = appDao.getLastInvoiceNumber() ?: run {
                val maxFromSales = appDao.getAllInvoiceNumbers().mapNotNull { inv ->
                    val clean = inv.trim()
                    if (clean.matches(Regex("^\\d{1,8}$"))) clean.toLongOrNull() else null
                }.maxOrNull() ?: 0L
                val maxFromAudit = appDao.getSaleInvoiceNumbersFromAuditLogs().mapNotNull { idStr ->
                    val clean = idStr.trim()
                    if (clean.matches(Regex("^\\d{1,8}$"))) clean.toLongOrNull() else null
                }.maxOrNull() ?: 0L
                maxOf(maxFromSales, maxFromAudit)
            }
            var nextNum = currentSeq + 1L
            var formatted = String.format(Locale.US, "%04d", nextNum)
            while (appDao.getSaleByInvoiceNumber(formatted) != null) {
                nextNum++
                formatted = String.format(Locale.US, "%04d", nextNum)
            }
            appDao.setInvoiceSequence(InvoiceSequenceEntity(id = 1, lastInvoiceNumber = nextNum))
            formatted
        } else {
            val manualNum = sale.invoiceNumber.trim()
            val numericManual = if (manualNum.matches(Regex("^\\d{1,8}$"))) manualNum.toLongOrNull() else null
            if (numericManual != null) {
                val existingSeq = appDao.getLastInvoiceNumber() ?: 0L
                if (numericManual > existingSeq) {
                    appDao.setInvoiceSequence(InvoiceSequenceEntity(id = 1, lastInvoiceNumber = numericManual))
                }
            }
            sale.invoiceNumber
        }
        val validatedSale = sale.copy(invoiceNumber = finalInvoice)

        val settings = appDao.getSettings() ?: GlobalSettingsEntity()
        val storeCurrency = settings.currency.trim().ifBlank { "MAD" }
        val distinctProductIds = items.map { it.productId }.distinct()
        val productsMap = if (distinctProductIds.isNotEmpty()) {
            appDao.getProductsByIds(distinctProductIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        if (!settings.allowNegativeStock) {
            val totalRequiredPerProduct = items.groupBy { it.productId }
                .mapValues { entry -> entry.value.sumOf { it.quantity } }

            for ((productId, totalRequired) in totalRequiredPerProduct) {
                if (totalRequired > 0.0) {
                    val product = productsMap[productId]
                    if (product != null && totalRequired > product.currentStock) {
                        throw IllegalStateException(
                            "الكمية المطلوبة من المنتج (${product.name}) هي $totalRequired بينما المتوفر في المخزون هو ${product.currentStock} فقط. يمكنك تفعيل خيار «البيع عند نفاد المخزون (المخزون = 0)» من الإعدادات."
                        )
                    }
                }
            }
        }

        val saleId = appDao.insertSale(validatedSale)

        for (item in items) {
            val product = productsMap[item.productId] ?: appDao.getProductById(item.productId)
            val cost = if (item.unitCost > 0L) item.unitCost else (product?.purchasePrice ?: 0L)
            appDao.insertSaleItem(item.copy(saleId = saleId, unitCost = cost))
            appDao.adjustProductStock(item.productId, -item.quantity)
            val mType = if (item.quantity < 0) "CUSTOMER_RETURN" else "SALE"
            val mNote = if (item.quantity < 0) "استرجاع زبون فاتورة رقم / Return: $finalInvoice" else "فاتورة بيع رقم / Invoice: $finalInvoice"
            appDao.insertStockMovement(
                StockMovementEntity(
                    productId = item.productId,
                    productName = item.productName,
                    type = mType,
                    quantity = -item.quantity,
                    timestamp = System.currentTimeMillis(),
                    notes = mNote,
                    referenceType = "SALE",
                    referenceId = saleId
                )
            )
        }

        recordAuditLog(
            "SALE_CREATE",
            "SALE",
            finalInvoice,
            "إنشاء فاتورة بيع #$finalInvoice للزبون ${sale.customerName} بإجمالي ${MoneyUtils.format(sale.totalAmount, storeCurrency)}",
            currentUser?.username ?: sale.cashierName,
            currentUser?.roleName ?: "CASHIER",
            false
        )

        if (sale.customerId > 0) {
            val customerBefore = appDao.getCustomerById(sale.customerId)
            val prevCustomerBalance = customerBefore?.balance ?: 0L
            appDao.addToCustomerTotalPurchases(sale.customerId, sale.totalAmount)
            val signedBalanceDelta = if (sale.totalAmount >= 0L) {
                (sale.totalAmount - sale.paidAmount).coerceAtLeast(0L)
            } else {
                (sale.totalAmount - sale.paidAmount).coerceAtMost(0L)
            }
            if (signedBalanceDelta != 0L) {
                appDao.adjustCustomerBalance(sale.customerId, signedBalanceDelta)
            }
            val gap = sale.remainingDueCents
            if (validatedSale.paidAmount > 0L && validatedSale.paidAmount < validatedSale.totalAmount) {
                val normalizedMethod = when (validatedSale.paymentMethod.uppercase(Locale.ROOT)) {
                    "BANK", "TRANSFER", "VIREMENT", "CARD" -> "TRANSFER"
                    "CHECK", "CHEQUE" -> "CHECK"
                    else -> "CASH"
                }
                appDao.insertCustomerPayment(
                    CustomerPaymentEntity(
                        saleId = saleId,
                        customerId = validatedSale.customerId,
                        customerName = validatedSale.customerName.ifBlank { customerBefore?.name ?: "زبون" },
                        amount = validatedSale.paidAmount,
                        previousBalance = prevCustomerBalance + validatedSale.totalAmount,
                        newBalance = prevCustomerBalance + gap,
                        paymentMethod = normalizedMethod,
                        notes = "دفعة جزئية لفاتورة بيع #$finalInvoice",
                        timestamp = validatedSale.timestamp + 1L,
                        cashierName = currentUser?.username ?: validatedSale.cashierName
                    )
                )
            }
        }

        try {
            val orderEntity = OrderEntity(
                orderNumber = finalInvoice,
                timestamp = validatedSale.timestamp,
                customerId = if (sale.customerId > 0) sale.customerId else null,
                customerName = sale.customerName,
                cashierName = sale.cashierName,
                subtotalAmount = sale.totalAmount + sale.discountAmount,
                discountAmount = sale.discountAmount,
                discountType = sale.discountType,
                taxAmount = 0L,
                totalAmount = sale.totalAmount,
                paidAmount = sale.paidAmount,
                changeAmount = sale.changeAmount,
                paymentMethod = sale.paymentMethod,
                status = "COMPLETED",
                isVoided = false,
                isSynced = false
            )
            val orderId = appDao.insertOrder(orderEntity)
            val orderItems = items.map { item ->
                OrderItemEntity(
                    orderId = orderId,
                    productId = item.productId,
                    barcode = "",
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    purchasePrice = if (item.unitCost > 0L) item.unitCost else 0L,
                    discountValue = item.discountValue,
                    totalLinePrice = item.totalLinePrice
                )
            }
            appDao.insertOrderItemsBatch(orderItems)
        } catch (e: Exception) {
            logError("Order items batch insertion non-blocking fallback", e)
        }

        validatedSale.copy(id = saleId)
    }

    suspend fun createOrder(order: OrderEntity, items: List<OrderItemEntity>): OrderEntity = tx {
        val finalOrderNumber = if (order.orderNumber.isEmpty()) {
            "ORD-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + "-" + (100..999).random()
        } else {
            order.orderNumber
        }
        val validatedOrder = order.copy(orderNumber = finalOrderNumber)
        val orderId = appDao.insertOrder(validatedOrder)
        val itemsWithId = items.map { it.copy(orderId = orderId) }
        appDao.insertOrderItemsBatch(itemsWithId)

        for (item in itemsWithId) {
            appDao.adjustProductStock(item.productId, -item.quantity)
            appDao.insertStockMovement(
                StockMovementEntity(
                    productId = item.productId,
                    productName = item.productName,
                    type = "ORDER",
                    quantity = -item.quantity,
                    timestamp = System.currentTimeMillis(),
                    notes = "طلب رقم / Order: $finalOrderNumber"
                )
            )
        }
        validatedOrder.copy(id = orderId)
    }

    suspend fun voidOrder(orderId: Long) = tx {
        appDao.voidOrder(orderId)
        val items = appDao.getOrderItems(orderId)
        for (item in items) {
            appDao.adjustProductStock(item.productId, item.quantity)
            appDao.insertStockMovement(
                StockMovementEntity(
                    productId = item.productId,
                    productName = item.productName,
                    type = "ORDER_VOID",
                    quantity = item.quantity,
                    timestamp = System.currentTimeMillis(),
                    notes = "إلغاء طلب رقم / Order Void: ID $orderId"
                )
            )
        }
    }

    suspend fun recordTransaction(
        transaction: TransactionEntity,
        items: List<TransactionItemEntity>
    ): Long = tx {
        val txId = appDao.insertTransaction(transaction)
        val linkedItems = items.map { it.copy(transactionId = txId) }
        appDao.insertTransactionItemsBatch(linkedItems)
        txId
    }

    suspend fun addToPersistentCart(
        product: ProductEntity,
        quantity: Double = 1.0,
        customPrice: Double? = null,
        lineDiscount: Double = 0.0,
        sessionId: String = "ACTIVE"
    ) {
        if (abs(quantity) < 1e-6) return
        val existing = appDao.getCartItemByProduct(product.id, sessionId)
        val unitPriceCents = customPrice?.cents ?: product.sellPrice
        val lineDiscountCents = lineDiscount.cents
        if (existing != null) {
            val newQty = existing.quantity + quantity
            if (abs(newQty) < 1e-6) {
                appDao.deleteCartItem(existing.id)
            } else {
                val totalCents = FinancialCalculator.computeLineTotalCents(unitPriceCents, newQty, existing.lineDiscount)
                appDao.updateCartItemQuantity(existing.id, newQty, totalCents)
            }
        } else {
            val totalCents = FinancialCalculator.computeLineTotalCents(unitPriceCents, quantity, lineDiscountCents)
            appDao.insertCartItem(
                CartItemEntity(
                    cartSessionId = sessionId,
                    productId = product.id,
                    barcode = product.barcode,
                    productName = product.name,
                    categoryName = product.categoryName,
                    unit = product.unit,
                    quantity = quantity,
                    unitPrice = unitPriceCents,
                    purchasePrice = product.purchasePrice,
                    customPrice = customPrice?.cents,
                    lineDiscount = lineDiscountCents,
                    totalLinePrice = totalCents
                )
            )
        }
    }

    suspend fun updatePersistentCartQuantity(
        cartItemId: Long,
        quantity: Double,
        unitPrice: Double,
        lineDiscount: Double = 0.0
    ) {
        if (abs(quantity) < 1e-6) {
            appDao.deleteCartItem(cartItemId)
        } else {
            val totalCents = FinancialCalculator.computeLineTotalCents(unitPrice.cents, quantity, lineDiscount.cents)
            appDao.updateCartItemQuantity(cartItemId, quantity, totalCents)
        }
    }
}
