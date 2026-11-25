package com.example.ui.screens

import com.example.data.ProductEntity
import com.example.data.ProductSaleHistoryRecord
import com.example.data.PurchaseEntity
import com.example.data.PurchaseItemEntity
import com.example.data.StockMovementEntity
import com.example.data.SupplierEntity
import com.example.util.MoneyUtils
import com.example.util.cents
import com.example.util.moneyDouble
import kotlin.math.abs

enum class MovementType {
    SALE,
    CUSTOMER_RETURN,
    SUPPLIER_RETURN,
    PURCHASE
}

enum class SalesJournalSortCol {
    DATE, PRODUCT, ACCOUNT, INVOICE, QTY, PURCHASE_PRICE, SELL_PRICE, PROFIT
}

data class SalesJournalEntry(
    val id: String,
    val timestamp: Long,
    val productName: String,
    val productId: Long,
    val accountName: String,
    val invoiceNumber: String,
    val rawQuantity: Double,
    val displayQuantity: Double,
    val purchasePrice: Double,
    val sellPrice: Double,
    val profit: Double,
    val type: MovementType
)

internal data class SingleProductJournalStats(
    val soldQty: Double,
    val returnCustQty: Double,
    val returnSuppQty: Double,
    val purchasedQty: Double,
    val netQuantity: Double,
    val profitSum: Double,
    val currentStock: Double
)

internal data class SalesJournalTotals(
    val totalCalculatedQty: Double,
    val totalPurchaseCost: Double,
    val totalSalesAmount: Double,
    val totalProfitAmount: Double
)

internal fun buildSalesJournalEntries(
    salesHistory: List<ProductSaleHistoryRecord>,
    stockMovements: List<StockMovementEntity>,
    productsList: List<ProductEntity>,
    suppliersList: List<SupplierEntity>,
    purchasesList: List<PurchaseEntity>,
    purchaseItemsList: List<PurchaseItemEntity>
): List<SalesJournalEntry> {
    val entries = ArrayList<SalesJournalEntry>(salesHistory.size + purchaseItemsList.size + 32)
    val productsById = productsList.associateBy { it.id }
    val purchasesById = purchasesList.associateBy { it.id }
    val suppliersById = suppliersList.associateBy { it.id }
    val saleKeys = HashSet<String>(salesHistory.size * 2)
    val purchaseKeys = HashSet<String>(purchaseItemsList.size * 2)

    salesHistory.forEachIndexed { idx, record ->
        val absQty = abs(record.quantity)
        val isCustomerReturn = record.quantity < 0 ||
            record.invoiceNumber.contains("RET", ignoreCase = true) ||
            record.totalSale < 0
        val sellCents = MoneyUtils.multiplyCents(record.sellPrice.cents, absQty)
        val buyCents = MoneyUtils.multiplyCents(record.purchasePrice.cents, absQty)
        val baseProfit = (sellCents - buyCents).moneyDouble

        saleKeys.add("${record.productId}_${record.timestamp / 3000L}")

        if (isCustomerReturn) {
            entries.add(
                SalesJournalEntry(
                    id = "sale_ret_${record.saleId}_$idx",
                    timestamp = record.timestamp,
                    productName = record.productName,
                    productId = record.productId,
                    accountName = record.customerName.ifBlank { "عميل عام" },
                    invoiceNumber = record.invoiceNumber.ifBlank { "RET-#${record.saleId}" },
                    rawQuantity = absQty,
                    displayQuantity = absQty,
                    purchasePrice = record.purchasePrice,
                    sellPrice = record.sellPrice,
                    profit = -abs(baseProfit),
                    type = MovementType.CUSTOMER_RETURN
                )
            )
        } else {
            entries.add(
                SalesJournalEntry(
                    id = "sale_${record.saleId}_$idx",
                    timestamp = record.timestamp,
                    productName = record.productName,
                    productId = record.productId,
                    accountName = record.customerName.ifBlank { "عميل عام" },
                    invoiceNumber = record.invoiceNumber,
                    rawQuantity = absQty,
                    displayQuantity = -absQty,
                    purchasePrice = record.purchasePrice,
                    sellPrice = record.sellPrice,
                    profit = baseProfit,
                    type = MovementType.SALE
                )
            )
        }
    }

    stockMovements.forEachIndexed { idx, movement ->
        val absQty = abs(movement.quantity)
        val product = productsById[movement.productId]
        val buyP = product?.purchasePriceDouble ?: 0.0
        val sellP = product?.sellPriceDouble ?: 0.0

        if (movement.type == "CUSTOMER_RETURN") {
            val key = "${movement.productId}_${movement.timestamp / 3000L}"
            val isDuplicate = saleKeys.contains(key) || entries.any {
                it.productId == movement.productId && abs(it.timestamp - movement.timestamp) < 3000
            }
            if (!isDuplicate) {
                val baseProfit = (MoneyUtils.multiplyCents(sellP.cents, absQty) - MoneyUtils.multiplyCents(buyP.cents, absQty)).moneyDouble
                entries.add(
                    SalesJournalEntry(
                        id = "cust_ret_${movement.id}_$idx",
                        timestamp = movement.timestamp,
                        productName = movement.productName,
                        productId = movement.productId,
                        accountName = "زبون (استرجاع)",
                        invoiceNumber = extractInvoiceFromNotes(movement.notes) ?: "RET-CUST",
                        rawQuantity = absQty,
                        displayQuantity = absQty,
                        purchasePrice = buyP,
                        sellPrice = sellP,
                        profit = -abs(baseProfit),
                        type = MovementType.CUSTOMER_RETURN
                    )
                )
            }
        } else if (movement.type == "SUPPLIER_RETURN") {
            val purId = extractPurchaseIdFromNotes(movement.notes)
            val matchedPurchase = if (purId != null) purchasesById[purId] else null
            if (purId != null && (matchedPurchase == null || matchedPurchase.isVoided)) return@forEachIndexed

            val resolvedSupplierName = matchedPurchase?.supplierName?.takeIf { it.isNotBlank() }
                ?: suppliersList.find { movement.notes.contains(it.name, ignoreCase = true) }?.name
                ?: "مورد عام"

            entries.add(
                SalesJournalEntry(
                    id = "supp_ret_${movement.id}_$idx",
                    timestamp = movement.timestamp,
                    productName = movement.productName,
                    productId = movement.productId,
                    accountName = "مورد: $resolvedSupplierName",
                    invoiceNumber = if (purId != null) "RET-PUR-$purId" else (extractInvoiceFromNotes(movement.notes) ?: "RET-SUP"),
                    rawQuantity = absQty,
                    displayQuantity = -absQty,
                    purchasePrice = buyP,
                    sellPrice = buyP,
                    profit = 0.0,
                    type = MovementType.SUPPLIER_RETURN
                )
            )
        }
    }

    purchaseItemsList.forEachIndexed { idx, pItem ->
        val parentPurchase = purchasesById[pItem.purchaseId]
        if (parentPurchase == null || parentPurchase.isVoided) return@forEachIndexed

        val absQty = abs(pItem.quantity)
        val product = productsById[pItem.productId]
        val buyPrice = if (pItem.unitPrice > 0L) pItem.unitPriceDouble else (product?.purchasePriceDouble ?: 0.0)
        val sellPrice = product?.sellPriceDouble ?: buyPrice
        val supName = parentPurchase.supplierName.takeIf { it.isNotBlank() }
            ?: suppliersById[parentPurchase.supplierId]?.name
            ?: "مورد عام"

        purchaseKeys.add("${pItem.productId}_${parentPurchase.timestamp / 3000L}")

        entries.add(
            SalesJournalEntry(
                id = "pur_${pItem.id}_$idx",
                timestamp = parentPurchase.timestamp,
                productName = pItem.productName.ifBlank { product?.name ?: "منتج مشتريات" },
                productId = pItem.productId,
                accountName = "مورد: $supName",
                invoiceNumber = parentPurchase.displayInvoiceNumber,
                rawQuantity = absQty,
                displayQuantity = absQty,
                purchasePrice = buyPrice,
                sellPrice = sellPrice,
                profit = 0.0,
                type = MovementType.PURCHASE
            )
        )
    }

    stockMovements.forEachIndexed { idx, movement ->
        if (movement.type != "PURCHASE") return@forEachIndexed
        val purId = extractPurchaseIdFromNotes(movement.notes)
        if (purId != null) {
            val matchedPurchase = purchasesById[purId]
            if (matchedPurchase == null || matchedPurchase.isVoided) return@forEachIndexed
        }
        val key = "${movement.productId}_${movement.timestamp / 3000L}"
        val isDuplicate = purchaseKeys.contains(key) || entries.any {
            it.productId == movement.productId && it.type == MovementType.PURCHASE && abs(it.timestamp - movement.timestamp) < 3000
        }
        if (!isDuplicate) {
            val absQty = abs(movement.quantity)
            val product = productsById[movement.productId]
            val buyP = product?.purchasePriceDouble ?: 0.0
            val sellP = product?.sellPriceDouble ?: 0.0
            val matchedPurchase = if (purId != null) purchasesById[purId] else null
            val resolvedSupplierName = matchedPurchase?.supplierName?.takeIf { it.isNotBlank() }
                ?: suppliersList.find { movement.notes.contains(it.name, ignoreCase = true) }?.name
                ?: "مورد عام"

            entries.add(
                SalesJournalEntry(
                    id = "stock_pur_${movement.id}_$idx",
                    timestamp = movement.timestamp,
                    productName = movement.productName,
                    productId = movement.productId,
                    accountName = "مورد: $resolvedSupplierName",
                    invoiceNumber = if (purId != null) "PUR-#$purId" else (extractInvoiceFromNotes(movement.notes) ?: "PUR"),
                    rawQuantity = absQty,
                    displayQuantity = absQty,
                    purchasePrice = buyP,
                    sellPrice = sellP,
                    profit = 0.0,
                    type = MovementType.PURCHASE
                )
            )
        }
    }

    return entries.sortedByDescending { it.timestamp }
}

internal fun computeSalesJournalTotals(filteredEntries: List<SalesJournalEntry>): SalesJournalTotals {
    var totalQty = 0.0
    var buyCents = 0L
    var sellCents = 0L
    var profitCents = 0L
    for (e in filteredEntries) {
        totalQty += e.displayQuantity
        buyCents += MoneyUtils.multiplyCents(e.purchasePrice.cents, e.rawQuantity)
        sellCents += MoneyUtils.multiplyCents(e.sellPrice.cents, e.rawQuantity)
        profitCents += e.profit.cents
    }
    return SalesJournalTotals(
        totalCalculatedQty = totalQty,
        totalPurchaseCost = buyCents.moneyDouble,
        totalSalesAmount = sellCents.moneyDouble,
        totalProfitAmount = profitCents.moneyDouble
    )
}

internal fun computeSingleProductStats(
    filteredEntries: List<SalesJournalEntry>,
    selectedProduct: ProductEntity?
): SingleProductJournalStats? {
    if (selectedProduct == null) return null
    var sold = 0.0
    var returnCust = 0.0
    var returnSupp = 0.0
    var purchased = 0.0
    var netQty = 0.0
    var profitCents = 0L
    for (e in filteredEntries) {
        when (e.type) {
            MovementType.SALE -> sold += e.displayQuantity
            MovementType.CUSTOMER_RETURN -> returnCust += e.displayQuantity
            MovementType.SUPPLIER_RETURN -> returnSupp += e.displayQuantity
            MovementType.PURCHASE -> purchased += e.displayQuantity
        }
        netQty += e.displayQuantity
        profitCents += e.profit.cents
    }
    return SingleProductJournalStats(
        soldQty = sold,
        returnCustQty = returnCust,
        returnSuppQty = returnSupp,
        purchasedQty = purchased,
        netQuantity = netQty,
        profitSum = profitCents.moneyDouble,
        currentStock = selectedProduct.currentStock
    )
}

private val invoiceRegex = Regex("""(?:Facture\s*#?|Invoice\s*|PUR-|ID-)(\S+)""", RegexOption.IGNORE_CASE)
private val purchaseIdRegex = Regex("""(?:ID-|ID\s+|PUR-|PUR-#|RET-PUR-)(\d+)""", RegexOption.IGNORE_CASE)

internal fun extractInvoiceFromNotes(notes: String): String? {
    if (notes.isBlank()) return null
    return invoiceRegex.find(notes)?.value
}

internal fun extractPurchaseIdFromNotes(notes: String): Long? {
    if (notes.isBlank()) return null
    return purchaseIdRegex.find(notes)?.groupValues?.getOrNull(1)?.toLongOrNull()
}
