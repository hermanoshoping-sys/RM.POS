package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.util.Money
import com.example.util.MoneyUtils
import com.example.util.cents
import com.example.util.money
import com.example.util.moneyDouble

/**
 * Supermarket POS Entities representing database tables for offline-first store operations.
 * Customized for High Performance, Arabic-French bilingual readiness, and Material 3 layouts.
 */

/**
 * Strongly typed Enum representing store user roles with role checks.
 */
enum class UserRole {
    MANAGER,
    CASHIER;

    val isManager: Boolean get() = this == MANAGER
    val isCashier: Boolean get() = this == CASHIER

    companion object {
        fun fromString(value: String?): UserRole {
            return when (value?.trim()?.uppercase()) {
                "MANAGER", "ADMIN" -> MANAGER
                "CASHIER" -> CASHIER
                else -> CASHIER
            }
        }
    }
}

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val role: UserRole = UserRole.CASHIER,
    // Detailed User Permissions & Feature Visibility
    val canSell: Boolean = true,
    val canPurchase: Boolean = false,
    val canReports: Boolean = false,
    val canModifyPrice: Boolean = false,
    val canDeleteInvoice: Boolean = false,
    val canManageUsers: Boolean = false,
    val canEditInvoice: Boolean = false,
    val canManageInventory: Boolean = true,
    val canManageClients: Boolean = true,
    val canEditCustomers: Boolean = true,
    val canManageSuppliers: Boolean = false,
    val canManageExpenses: Boolean = false,
    val canManagePayments: Boolean = false,
    val canManageSettings: Boolean = false,
    val enforceCustomerGeoLocation: Boolean = false
) {
    @Ignore
    constructor(
        id: Long = 0,
        username: String,
        passwordHash: String,
        role: String,
        canSell: Boolean = true,
        canPurchase: Boolean = false,
        canReports: Boolean = false,
        canModifyPrice: Boolean = false,
        canDeleteInvoice: Boolean = false,
        canManageUsers: Boolean = false,
        canEditInvoice: Boolean = false,
        canManageInventory: Boolean = true,
        canManageClients: Boolean = true,
        canEditCustomers: Boolean = true,
        canManageSuppliers: Boolean = false,
        canManageExpenses: Boolean = false,
        canManagePayments: Boolean = false,
        canManageSettings: Boolean = false,
        enforceCustomerGeoLocation: Boolean = false
    ) : this(
        id = id,
        username = username,
        passwordHash = passwordHash,
        role = UserRole.fromString(role),
        canSell = canSell,
        canPurchase = canPurchase,
        canReports = canReports,
        canModifyPrice = canModifyPrice,
        canDeleteInvoice = canDeleteInvoice,
        canManageUsers = canManageUsers,
        canEditInvoice = canEditInvoice,
        canManageInventory = canManageInventory,
        canManageClients = canManageClients,
        canEditCustomers = canEditCustomers,
        canManageSuppliers = canManageSuppliers,
        canManageExpenses = canManageExpenses,
        canManagePayments = canManagePayments,
        canManageSettings = canManageSettings,
        enforceCustomerGeoLocation = enforceCustomerGeoLocation
    )

    val roleName: String get() = role.name
    val isManager: Boolean get() = role == UserRole.MANAGER

    fun hasReportsAccess(): Boolean = isManager || canReports
    fun hasSettingsAccess(): Boolean = isManager || canManageSettings
    fun hasUsersAccess(): Boolean = isManager || canManageUsers
    fun hasEditInvoiceAccess(): Boolean = isManager || canEditInvoice
    fun hasDeleteInvoiceAccess(): Boolean = isManager || canDeleteInvoice
    fun hasSellAccess(): Boolean = isManager || canSell
    fun hasPurchaseAccess(): Boolean = isManager || canPurchase
    fun hasInventoryAccess(): Boolean = isManager || canManageInventory
    fun hasClientsAccess(): Boolean = isManager || canManageClients
    fun hasEditCustomersAccess(): Boolean = isManager || (canManageClients && canEditCustomers)
    fun hasSuppliersAccess(): Boolean = isManager || canManageSuppliers
    fun hasExpensesAccess(): Boolean = isManager || canManageExpenses
    fun hasPaymentsAccess(): Boolean = isManager || canManagePayments
    fun hasModifyPriceAccess(): Boolean = isManager || canModifyPrice
    fun hasEnforceGeoLocation(): Boolean = enforceCustomerGeoLocation
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val imageUri: String? = null
)

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["name"]),
        Index(value = ["sku"]),
        Index(value = ["isSynced"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val name: String,
    val description: String = "",
    val categoryId: Long,
    val categoryName: String = "",
    val unit: String, // "قطعة / Unité", "علبة / Boîte", "كرتونة / Carton", "كيلو / Kg", "لتر / Litre"
    val purchasePrice: Long, // In integer cents (Long)
    val sellPrice: Long, // In integer cents (Long)
    val wholesalePrice: Long = 0L, // In integer cents (Long)
    val specialPrice: Long = 0L, // In integer cents (Long)
    val currentStock: Double,
    val minStock: Double,
    val sku: String = "",
    val taxRate: Double = 0.0,
    val isTaxable: Boolean = false,
    val isActive: Boolean = true,
    val imageUri: String? = null,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    @Ignore
    constructor(
        id: Long = 0,
        barcode: String,
        name: String,
        description: String = "",
        categoryId: Long,
        categoryName: String = "",
        unit: String,
        purchasePrice: Double,
        sellPrice: Double,
        wholesalePrice: Double = 0.0,
        specialPrice: Double = 0.0,
        currentStock: Double,
        minStock: Double,
        sku: String = "",
        taxRate: Double = 0.0,
        isTaxable: Boolean = false,
        isActive: Boolean = true,
        imageUri: String? = null,
        isSynced: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ) : this(
        id = id,
        barcode = barcode,
        name = name,
        description = description,
        categoryId = categoryId,
        categoryName = categoryName,
        unit = unit,
        purchasePrice = purchasePrice.cents,
        sellPrice = sellPrice.cents,
        wholesalePrice = wholesalePrice.cents,
        specialPrice = specialPrice.cents,
        currentStock = currentStock,
        minStock = minStock,
        sku = sku,
        taxRate = taxRate,
        isTaxable = isTaxable,
        isActive = isActive,
        imageUri = imageUri,
        isSynced = isSynced,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    val purchasePriceDouble: Double get() = purchasePrice.moneyDouble
    val sellPriceDouble: Double get() = sellPrice.moneyDouble
    val wholesalePriceDouble: Double get() = wholesalePrice.moneyDouble
    val specialPriceDouble: Double get() = specialPrice.moneyDouble

    val purchasePriceCents: Long get() = purchasePrice
    val sellPriceCents: Long get() = sellPrice
    val wholesalePriceCents: Long get() = wholesalePrice
    val specialPriceCents: Long get() = specialPrice

    val purchasePriceMoney: Money get() = Money(purchasePrice)
    val sellPriceMoney: Money get() = Money(sellPrice)
    val wholesalePriceMoney: Money get() = Money(wholesalePrice)
    val specialPriceMoney: Money get() = Money(specialPrice)

    val imageList: List<String> get() {
        val raw = imageUri?.trim() ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return if (raw.contains("||")) {
            raw.split("||").map { it.trim() }.filter { it.isNotEmpty() }
        } else if (raw.contains(",")) {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf(raw)
        }
    }
}

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["customerNumber"]),
        Index(value = ["barcode"])
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val address: String = "",
    val balance: Long = 0L, // For debit/credit tracking in integer cents (Long)
    val totalPurchases: Long = 0L, // In integer cents (Long)
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geoRadiusMeters: Double = 150.0,
    val isGeoLocationRequired: Boolean = false,
    val customerNumber: Long = 0L,
    val barcode: String = ""
) {
    @Ignore
    constructor(
        id: Long = 0,
        name: String,
        phone: String,
        address: String = "",
        balance: Double,
        totalPurchases: Double = 0.0,
        latitude: Double? = null,
        longitude: Double? = null,
        geoRadiusMeters: Double = 150.0,
        isGeoLocationRequired: Boolean = false,
        customerNumber: Long = 0L,
        barcode: String = ""
    ) : this(
        id = id,
        name = name,
        phone = phone,
        address = address,
        balance = balance.cents,
        totalPurchases = totalPurchases.cents,
        latitude = latitude,
        longitude = longitude,
        geoRadiusMeters = geoRadiusMeters,
        isGeoLocationRequired = isGeoLocationRequired,
        customerNumber = customerNumber,
        barcode = barcode
    )

    val balanceDouble: Double get() = balance.moneyDouble
    val totalPurchasesDouble: Double get() = totalPurchases.moneyDouble
    val balanceCents: Long get() = balance
    val totalPurchasesCents: Long get() = totalPurchases
    val balanceMoney: Money get() = Money(balance)
    val totalPurchasesMoney: Money get() = Money(totalPurchases)
    val hasGeoLocation: Boolean get() = latitude != null && longitude != null
    val displayCustomerNumber: String get() = if (customerNumber > 0) "#$customerNumber" else "#$id"
    val displayBarcode: String get() = barcode.ifBlank { "CUST-$id" }
}

@Entity(tableName = "customer_sequences")
data class CustomerSequenceEntity(
    @PrimaryKey val id: Int = 1,
    val lastCustomerNumber: Long = 0L
)

@Entity(tableName = "invoice_sequences")
data class InvoiceSequenceEntity(
    @PrimaryKey val id: Int = 1,
    val lastInvoiceNumber: Long = 0L
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val email: String = "",
    val address: String = "",
    val debtBalance: Long = 0L // In integer cents (Long)
) {
    @Ignore
    constructor(
        id: Long = 0,
        name: String,
        phone: String,
        email: String = "",
        address: String = "",
        debtBalance: Double
    ) : this(
        id = id,
        name = name,
        phone = phone,
        email = email,
        address = address,
        debtBalance = debtBalance.cents
    )

    val debtBalanceDouble: Double get() = debtBalance.moneyDouble
    val debtBalanceCents: Long get() = debtBalance
    val debtBalanceMoney: Money get() = Money(debtBalance)
}

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val supplierId: Long,
    val supplierName: String,
    val totalAmount: Long, // In integer cents (Long)
    val paidAmount: Long, // In integer cents (Long)
    val isVoided: Boolean = false,
    val invoiceNumber: String = ""
) {
    @Ignore
    constructor(
        id: Long = 0,
        timestamp: Long,
        supplierId: Long,
        supplierName: String,
        totalAmount: Double,
        paidAmount: Double,
        isVoided: Boolean = false,
        invoiceNumber: String = ""
    ) : this(
        id = id,
        timestamp = timestamp,
        supplierId = supplierId,
        supplierName = supplierName,
        totalAmount = totalAmount.cents,
        paidAmount = paidAmount.cents,
        isVoided = isVoided,
        invoiceNumber = invoiceNumber
    )

    val totalAmountDouble: Double get() = totalAmount.moneyDouble
    val paidAmountDouble: Double get() = paidAmount.moneyDouble
    val totalAmountCents: Long get() = totalAmount
    val paidAmountCents: Long get() = paidAmount
    val remainingDebtCents: Long get() = (totalAmount - paidAmount).coerceAtLeast(0L)
    val remainingDebtDouble: Double get() = remainingDebtCents.moneyDouble
    val totalAmountMoney: Money get() = Money(totalAmount)
    val paidAmountMoney: Money get() = Money(paidAmount)
    val remainingDebtMoney: Money get() = Money(remainingDebtCents)
    val displayInvoiceNumber: String get() = invoiceNumber.ifBlank { "PUR-$id" }
}

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("purchaseId"),
        Index("productId")
    ]
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPrice: Long // In integer cents (Long)
) {
    @Ignore
    constructor(
        id: Long = 0,
        purchaseId: Long,
        productId: Long,
        productName: String,
        quantity: Double,
        unitPrice: Double
    ) : this(
        id = id,
        purchaseId = purchaseId,
        productId = productId,
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice.cents
    )

    val unitPriceDouble: Double get() = unitPrice.moneyDouble
    val totalPrice: Double get() = totalLinePrice
    val unitPriceCents: Long get() = unitPrice
    val unitPriceMoney: Money get() = Money(unitPrice)
    val totalLinePriceCents: Long get() = MoneyUtils.multiplyCents(unitPrice, quantity)
    val totalLinePrice: Double get() = totalLinePriceCents.moneyDouble
    val totalLinePriceMoney: Money get() = Money(totalLinePriceCents)
}

@Entity(
    tableName = "sales",
    indices = [Index(value = ["invoiceNumber"], unique = true)]
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val timestamp: Long,
    val cashierName: String,
    val customerId: Long = 0L,
    val customerName: String = "",
    val totalAmount: Long, // In integer cents (Long)
    val discountAmount: Long, // In integer cents (Long)
    val discountType: String, // "PERCENT" or "VALUE"
    val paidAmount: Long, // In integer cents (Long)
    val changeAmount: Long, // In integer cents (Long)
    val paymentMethod: String, // "CASH", "CARD", "BANK", "MIXED"
    val isVoided: Boolean = false
) {
    @Ignore
    constructor(
        id: Long = 0,
        invoiceNumber: String,
        timestamp: Long,
        cashierName: String,
        customerId: Long = 0L,
        customerName: String = "",
        totalAmount: Double,
        discountAmount: Double,
        discountType: String,
        paidAmount: Double,
        changeAmount: Double,
        paymentMethod: String,
        isVoided: Boolean = false
    ) : this(
        id = id,
        invoiceNumber = invoiceNumber,
        timestamp = timestamp,
        cashierName = cashierName,
        customerId = customerId,
        customerName = customerName,
        totalAmount = totalAmount.cents,
        discountAmount = discountAmount.cents,
        discountType = discountType,
        paidAmount = paidAmount.cents,
        changeAmount = changeAmount.cents,
        paymentMethod = paymentMethod,
        isVoided = isVoided
    )

    val totalAmountDouble: Double get() = totalAmount.moneyDouble
    val discountAmountDouble: Double get() = discountAmount.moneyDouble
    val paidAmountDouble: Double get() = paidAmount.moneyDouble
    val changeAmountDouble: Double get() = changeAmount.moneyDouble
    val totalAmountCents: Long get() = totalAmount
    val discountAmountCents: Long get() = discountAmount
    val paidAmountCents: Long get() = paidAmount
    val changeAmountCents: Long get() = changeAmount
    val remainingDueCents: Long get() = (totalAmount - paidAmount).coerceAtLeast(0L)
    val remainingDueDouble: Double get() = remainingDueCents.moneyDouble
    val totalAmountMoney: Money get() = Money(totalAmount)
    val discountAmountMoney: Money get() = Money(discountAmount)
    val paidAmountMoney: Money get() = Money(paidAmount)
    val changeAmountMoney: Money get() = Money(changeAmount)
    val remainingDueMoney: Money get() = Money(remainingDueCents)
}

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("saleId"),
        Index("productId")
    ]
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPrice: Long, // In integer cents (Long)
    val discountValue: Long = 0L, // In integer cents (Long)
    val totalLinePrice: Long = 0L, // In integer cents (Long)
    val unitCost: Long = 0L // Historical purchase cost per unit in cents (Long)
) {
    @Ignore
    constructor(
        id: Long = 0,
        saleId: Long,
        productId: Long,
        productName: String,
        quantity: Double,
        unitPrice: Double,
        discountValue: Double = 0.0,
        totalLinePrice: Double = 0.0,
        unitCost: Double = 0.0
    ) : this(
        id = id,
        saleId = saleId,
        productId = productId,
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice.cents,
        discountValue = discountValue.cents,
        totalLinePrice = totalLinePrice.cents,
        unitCost = unitCost.cents
    )

    val unitPriceDouble: Double get() = unitPrice.moneyDouble
    val discountValueDouble: Double get() = discountValue.moneyDouble
    val totalLinePriceDouble: Double get() = totalLinePrice.moneyDouble
    val unitCostDouble: Double get() = unitCost.moneyDouble
    val totalPrice: Double get() = totalLinePriceDouble
    val unitPriceCents: Long get() = unitPrice
    val unitCostCents: Long get() = unitCost
    val discountValueCents: Long get() = discountValue
    val totalLinePriceCents: Long get() = totalLinePrice
    val unitPriceMoney: Money get() = Money(unitPrice)
    val unitCostMoney: Money get() = Money(unitCost)
    val discountValueMoney: Money get() = Money(discountValue)
    val totalLinePriceMoney: Money get() = Money(totalLinePrice)

    val profitCents: Long get() {
        val costCents = if (unitCost > 0L) unitCost else 0L
        val totalCost = MoneyUtils.multiplyCents(costCents, quantity)
        return totalLinePrice - totalCost
    }
    val profit: Double get() = profitCents.moneyDouble
}

/**
 * Data class representing a product sale history record with selling price, purchase price,
 * date/time, customer, invoice number, quantity, and profit margin.
 */
data class ProductSaleHistoryRecord(
    val saleId: Long,
    val invoiceNumber: String,
    val timestamp: Long,
    val customerName: String,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val sellPrice: Double,
    val purchasePrice: Double,
    val totalSale: Double,
    val profit: Double
)

@Entity(
    tableName = "stock_movements",
    indices = [
        Index("productId"),
        Index(value = ["referenceType", "referenceId"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val type: String, // "SALE", "PURCHASE", "RETURN", "VOID", "CORRECTION"
    val quantity: Double, // Negative for outflow, positive for inflow
    val timestamp: Long,
    val notes: String,
    val referenceType: String = "", // "SALE", "PURCHASE", "RETURN_SALE", "RETURN_PURCHASE", "ADJUSTMENT"
    val referenceId: Long = 0L // saleId, purchaseId, etc.
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long, // In integer cents (Long)
    val timestamp: Long,
    val category: String
) {
    @Ignore
    constructor(
        id: Long = 0,
        title: String,
        amount: Double,
        timestamp: Long,
        category: String
    ) : this(
        id = id,
        title = title,
        amount = amount.cents,
        timestamp = timestamp,
        category = category
    )

    val amountDouble: Double get() = amount.moneyDouble
    val amountCents: Long get() = amount
    val amountMoney: Money get() = Money(amount)
}

@Entity(tableName = "global_settings")
data class GlobalSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "",
    val shopAddress: String = "",
    val shopPhone: String = "",
    val taxNumber: String = "",
    val receiptHeader: String = "فاتورة بيع مبسطة / Ticket de Caisse",
    val receiptFooter: String = "شكراً لتعاملكم معنا\nMerci pour votre fidélité !",
    val language: String = "AR", // "AR", "FR" or "EN"
    val showShopName: Boolean = true,
    val showAddress: Boolean = true,
    val showPhone: Boolean = true,
    val showTaxNumber: Boolean = true,
    val showLogo: Boolean = true,
    val showCashierName: Boolean = true,
    val showCustomerInfo: Boolean = true,
    val showDateTime: Boolean = true,
    val showBarcode: Boolean = true,
    val showPaymentMethod: Boolean = true,
    val showReceiptFooter: Boolean = true,
    val printerConnectionType: String = "BLUETOOTH", // "BLUETOOTH", "WIFI_NETWORK", "USB"
    val printerName: String = "",
    val printerMac: String = "",
    val printerIp: String = "192.168.1.100",
    val printerPort: Int = 9100,
    val printerPaperWidth: Int = 58,
    val autoPrintReceipt: Boolean = true,
    val logoWidth: Int = 110,
    val logoHeight: Int = 70,
    val logoQuality: Int = 90,
    val headerHeight: Int = 30,
    val headerFontSize: Int = 13,
    val headerContentType: String = "TEXT",
    val headerWidth: Int = 520,
    val footerContentType: String = "TEXT",
    val footerWidth: Int = 520,
    val footerHeight: Int = 50,
    val documentFontSize: Int = 12,
    val allowNegativeStock: Boolean = false,
    val currency: String = "MAD"
)

/**
 * Room Entity representing Orders for offline-first POS data persistence.
 */
@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["orderNumber"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["customerId"]),
        Index(value = ["status"])
    ]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val customerId: Long? = null,
    val customerName: String = "عميل عام / Walk-In",
    val cashierName: String = "",
    val subtotalAmount: Long = 0L, // In integer cents (Long)
    val discountAmount: Long = 0L, // In integer cents (Long)
    val discountType: String = "VALUE", // "PERCENT" or "VALUE"
    val taxAmount: Long = 0L, // In integer cents (Long)
    val totalAmount: Long = 0L, // In integer cents (Long)
    val paidAmount: Long = 0L, // In integer cents (Long)
    val changeAmount: Long = 0L, // In integer cents (Long)
    val paymentMethod: String = "CASH", // "CASH", "CARD", "BANK", "MIXED"
    val status: String = "COMPLETED", // "COMPLETED", "PENDING", "CANCELLED", "REFUNDED"
    val notes: String = "",
    val isVoided: Boolean = false,
    val isSynced: Boolean = false
) {
    val subtotalAmountDouble: Double get() = subtotalAmount.moneyDouble
    val discountAmountDouble: Double get() = discountAmount.moneyDouble
    val taxAmountDouble: Double get() = taxAmount.moneyDouble
    val totalAmountDouble: Double get() = totalAmount.moneyDouble
    val paidAmountDouble: Double get() = paidAmount.moneyDouble
    val changeAmountDouble: Double get() = changeAmount.moneyDouble
}

/**
 * Room Entity representing Order Items linked to an Order and Product.
 */
@Entity(
    tableName = "order_items",
    indices = [
        Index(value = ["orderId"]),
        Index(value = ["productId"]),
        Index(value = ["barcode"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val barcode: String = "",
    val productName: String,
    val quantity: Double,
    val unitPrice: Long, // In integer cents (Long)
    val purchasePrice: Long = 0L, // In integer cents (Long)
    val discountValue: Long = 0L, // In integer cents (Long)
    val totalLinePrice: Long // In integer cents (Long)
) {
    val unitPriceDouble: Double get() = unitPrice.moneyDouble
    val purchasePriceDouble: Double get() = purchasePrice.moneyDouble
    val discountValueDouble: Double get() = discountValue.moneyDouble
    val totalLinePriceDouble: Double get() = totalLinePrice.moneyDouble
}

/**
 * Relation linking an Order to all its constituent Order Items.
 */
data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "orderId"
    )
    val items: List<OrderItemEntity>
)

/**
 * Room Entity representing POS Financial Transactions (Sales, Returns, Refunds)
 * for offline-first store operations.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["transactionNumber"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["customerId"]),
        Index(value = ["type"]),
        Index(value = ["status"]),
        Index(value = ["isSynced"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "",
    val customerId: Long? = null,
    val customerName: String = "عميل عام / Walk-In",
    val subtotal: Long = 0L, // In integer cents (Long)
    val discountAmount: Long = 0L, // In integer cents (Long)
    val discountType: String = "VALUE", // "PERCENT" or "VALUE"
    val taxAmount: Long = 0L, // In integer cents (Long)
    val totalAmount: Long = 0L, // In integer cents (Long)
    val paidAmount: Long = 0L, // In integer cents (Long)
    val changeAmount: Long = 0L, // In integer cents (Long)
    val paymentMethod: String = "CASH", // "CASH", "CARD", "BANK", "MIXED", "CREDIT"
    val type: String = "SALE", // "SALE", "RETURN", "REFUND", "VOID"
    val status: String = "COMPLETED", // "COMPLETED", "PENDING", "CANCELLED", "SYNCED"
    val notes: String = "",
    val isVoided: Boolean = false,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val subtotalDouble: Double get() = subtotal.moneyDouble
    val discountAmountDouble: Double get() = discountAmount.moneyDouble
    val taxAmountDouble: Double get() = taxAmount.moneyDouble
    val totalAmountDouble: Double get() = totalAmount.moneyDouble
    val paidAmountDouble: Double get() = paidAmount.moneyDouble
    val changeAmountDouble: Double get() = changeAmount.moneyDouble
}

/**
 * Room Entity representing Transaction Items linked to a Transaction and Product.
 */
@Entity(
    tableName = "transaction_items",
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["productId"]),
        Index(value = ["barcode"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productId: Long,
    val barcode: String = "",
    val productName: String,
    val quantity: Double,
    val unitPrice: Long, // In integer cents (Long)
    val purchasePrice: Long = 0L, // In integer cents (Long)
    val discountAmount: Long = 0L, // In integer cents (Long)
    val taxRate: Double = 0.0,
    val totalPrice: Long // In integer cents (Long)
) {
    val unitPriceDouble: Double get() = unitPrice.moneyDouble
    val purchasePriceDouble: Double get() = purchasePrice.moneyDouble
    val discountAmountDouble: Double get() = discountAmount.moneyDouble
    val totalPriceDouble: Double get() = totalPrice.moneyDouble
}

/**
 * Relation linking a Transaction to all its constituent Transaction Items.
 */
data class TransactionWithItems(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val items: List<TransactionItemEntity>
)

/**
 * Room Entity representing Persistent Cart Items for offline-first POS operations.
 * Supports active cart persistence across app restarts and multiple suspended/held cart sessions.
 */
@Entity(
    tableName = "cart_items",
    indices = [
        Index(value = ["cartSessionId"]),
        Index(value = ["productId"]),
        Index(value = ["barcode"])
    ]
)
data class CartItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cartSessionId: String = "ACTIVE", // "ACTIVE" or parked cart ticket number (e.g. "PARK_1")
    val productId: Long,
    val barcode: String = "",
    val productName: String,
    val categoryName: String = "",
    val unit: String = "قطعة / Unité",
    val quantity: Double = 1.0,
    val unitPrice: Long, // In integer cents (Long)
    val purchasePrice: Long = 0L, // In integer cents (Long)
    val customPrice: Long? = null, // In integer cents (Long?)
    val lineDiscount: Long = 0L, // In integer cents (Long)
    val totalLinePrice: Long, // In integer cents (Long)
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val unitPriceDouble: Double get() = unitPrice.moneyDouble
    val purchasePriceDouble: Double get() = purchasePrice.moneyDouble
    val customPriceDouble: Double? get() = customPrice?.moneyDouble
    val lineDiscountDouble: Double get() = lineDiscount.moneyDouble
    val totalLinePriceDouble: Double get() = totalLinePrice.moneyDouble
}

/**
 * Room Entity representing Customer Debt Payments.
 */
@Entity(
    tableName = "customer_payments",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["timestamp"]),
        Index(value = ["saleId"])
    ]
)
data class CustomerPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val amount: Long, // In integer cents (Long)
    val previousBalance: Long = 0L, // In integer cents (Long)
    val newBalance: Long = 0L, // In integer cents (Long)
    val paymentMethod: String = "CASH", // "CASH", "CHECK", "TRANSFER", "OTHER"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "",
    val saleId: Long = 0L
) {
    @Ignore
    constructor(
        id: Long = 0,
        customerId: Long,
        customerName: String,
        amount: Double,
        previousBalance: Double = 0.0,
        newBalance: Double = 0.0,
        paymentMethod: String = "CASH",
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        cashierName: String = "",
        saleId: Long = 0L
    ) : this(
        id = id,
        customerId = customerId,
        customerName = customerName,
        amount = amount.cents,
        previousBalance = previousBalance.cents,
        newBalance = newBalance.cents,
        paymentMethod = paymentMethod,
        notes = notes,
        timestamp = timestamp,
        cashierName = cashierName,
        saleId = saleId
    )

    val amountDouble: Double get() = amount.moneyDouble
    val previousBalanceDouble: Double get() = previousBalance.moneyDouble
    val newBalanceDouble: Double get() = newBalance.moneyDouble
    val amountCents: Long get() = amount
    val previousBalanceCents: Long get() = previousBalance
    val newBalanceCents: Long get() = newBalance
    val amountMoney: Money get() = Money(amount)
}

/**
 * Room Entity representing Supplier Debt Payments.
 */
@Entity(
    tableName = "supplier_payments",
    indices = [
        Index(value = ["supplierId"]),
        Index(value = ["timestamp"]),
        Index(value = ["purchaseId"])
    ]
)
data class SupplierPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val supplierName: String,
    val amount: Long, // In integer cents (Long)
    val previousDebt: Long = 0L, // In integer cents (Long)
    val newDebt: Long = 0L, // In integer cents (Long)
    val paymentMethod: String = "CASH", // "CASH", "CHECK", "TRANSFER", "TRAITE", "OTHER"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "",
    val purchaseId: Long = 0L
) {
    @Ignore
    constructor(
        id: Long = 0,
        supplierId: Long,
        supplierName: String,
        amount: Double,
        previousDebt: Double = 0.0,
        newDebt: Double = 0.0,
        paymentMethod: String = "CASH",
        notes: String = "",
        timestamp: Long = System.currentTimeMillis(),
        cashierName: String = "",
        purchaseId: Long = 0L
    ) : this(
        id = id,
        supplierId = supplierId,
        supplierName = supplierName,
        amount = amount.cents,
        previousDebt = previousDebt.cents,
        newDebt = newDebt.cents,
        paymentMethod = paymentMethod,
        notes = notes,
        timestamp = timestamp,
        cashierName = cashierName,
        purchaseId = purchaseId
    )

    val amountDouble: Double get() = amount.moneyDouble
    val previousDebtDouble: Double get() = previousDebt.moneyDouble
    val newDebtDouble: Double get() = newDebt.moneyDouble
    val amountCents: Long get() = amount
    val previousDebtCents: Long get() = previousDebt
    val newDebtCents: Long get() = newDebt
    val amountMoney: Money get() = Money(amount)
}

/**
 * Room Entity representing Audit Log for sensitive business and security operations:
 * - حذف / تعديل فاتورة (Invoice Delete/Edit)
 * - تغيير سعر (Price Modification)
 * - استرجاع مبيعات أو مشتريات (Returns / Refunds)
 * - تغيير صلاحيات المستخدمين (Role / Permissions Changes)
 * - محاولات الدخول الفاشلة (Failed Login Attempts)
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["actionType"]),
        Index(value = ["username"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String, // "INVOICE_DELETE", "INVOICE_EDIT", "PRICE_CHANGE", "RETURN_REFUND", "PERMISSION_CHANGE", "FAILED_LOGIN", "USER_MANAGEMENT"
    val entityType: String, // "SALE", "PURCHASE", "PRODUCT", "USER", "AUTH", "CUSTOMER", "SUPPLIER"
    val entityId: String,   // Reference ID (Invoice number, product ID, username, etc.)
    val details: String,    // Detailed description of the operation or before/after changes
    val username: String,   // Actor username
    val userRole: String = "CASHIER",
    val timestamp: Long = System.currentTimeMillis(),
    val isCritical: Boolean = false
)


