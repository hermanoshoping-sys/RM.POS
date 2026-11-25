package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.printer.HtmlInvoiceAndReceiptFormatter
import com.example.ui.screens.generateThermalPrinterStringForPurchase
import com.example.util.cents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PartialInvoicePaymentAndPrintTest {

    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var repository: PosRepository
    private val testManager = UserEntity(id = 1L, username = "admin", passwordHash = "hash", role = UserRole.MANAGER)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appDao = database.appDao()
        repository = PosRepository(appDao, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun testProduct(barcode: String, name: String, purchase: Long, sell: Long, stock: Double = 50.0) =
        ProductEntity(
            barcode = barcode,
            name = name,
            categoryId = 1L,
            categoryName = "عام",
            unit = "قطعة",
            purchasePrice = purchase,
            sellPrice = sell,
            currentStock = stock,
            minStock = 5.0
        )

    private fun testSale(
        id: Long = 0L,
        invoiceNumber: String,
        customerId: Long,
        customerName: String,
        totalAmount: Long,
        paidAmount: Long,
        paymentMethod: String = "DEBT",
        cashierName: String = "Admin"
    ) = SaleEntity(
        id = id,
        invoiceNumber = invoiceNumber,
        timestamp = System.currentTimeMillis(),
        cashierName = cashierName,
        customerId = customerId,
        customerName = customerName,
        totalAmount = totalAmount,
        discountAmount = 0L,
        discountType = "FIXED",
        paidAmount = paidAmount,
        changeAmount = 0L,
        paymentMethod = paymentMethod
    )

    private fun testPurchase(
        id: Long = 0L,
        invoiceNumber: String,
        supplierId: Long,
        supplierName: String,
        totalAmount: Long,
        paidAmount: Long
    ) = PurchaseEntity(
        id = id,
        timestamp = System.currentTimeMillis(),
        supplierId = supplierId,
        supplierName = supplierName,
        totalAmount = totalAmount,
        paidAmount = paidAmount,
        invoiceNumber = invoiceNumber
    )

    @Test
    fun partialPaymentAtSaleCreation_recordsInCustomerStatement() = runBlocking {
        val productId = appDao.insertProduct(testProduct("P-100", "زيت نباتي", 15.0.cents, 20.0.cents))
        val customerId = appDao.insertCustomer(
            CustomerEntity(
                name = "أحمد الزبون",
                phone = "0600000001",
                balance = 100.0.cents
            )
        )

        val sale = testSale(
            invoiceNumber = "1001",
            customerId = customerId,
            customerName = "أحمد الزبون",
            totalAmount = 500.0.cents,
            paidAmount = 200.0.cents,
            paymentMethod = "DEBT",
            cashierName = "كاشير"
        )
        val items = listOf(
            SaleItemEntity(
                saleId = 0L,
                productId = productId,
                productName = "زيت نباتي",
                quantity = 25.0,
                unitPrice = 20.0.cents,
                totalLinePrice = 500.0.cents
            )
        )

        val recorded = repository.recordSaleInvoice(sale, items, testManager)

        val updatedCustomer = repository.getCustomerById(customerId)!!
        // Initial 100 + remaining 300 = 400
        assertEquals(400.0.cents, updatedCustomer.balance)

        val customerPayments = repository.customerPayments.first().filter { it.customerId == customerId }
        assertEquals(1, customerPayments.size)
        val pmt = customerPayments.first()
        assertEquals(recorded.id, pmt.saleId)
        assertEquals(200.0.cents, pmt.amount)
        assertEquals(600.0.cents, pmt.previousBalance) // 100 + 500 invoice
        assertEquals(400.0.cents, pmt.newBalance)      // 600 - 200 paid
    }

    @Test
    fun partialPaymentAtPurchaseCreation_recordsInSupplierStatement() = runBlocking {
        val productId = appDao.insertProduct(testProduct("P-200", "دقيق ممتاز", 40.0.cents, 50.0.cents, stock = 10.0))
        val supplierId = appDao.insertSupplier(
            SupplierEntity(
                name = "شركة المطاحن",
                phone = "0600000002",
                debtBalance = 250.0.cents
            )
        )

        val purchase = testPurchase(
            invoiceNumber = "PUR-50",
            supplierId = supplierId,
            supplierName = "شركة المطاحن",
            totalAmount = 800.0.cents,
            paidAmount = 300.0.cents
        )
        val items = listOf(
            PurchaseItemEntity(
                purchaseId = 0L,
                productId = productId,
                productName = "دقيق ممتاز",
                quantity = 20.0,
                unitPrice = 40.0.cents
            )
        )

        repository.recordPurchaseInvoice(purchase, items, testManager)

        val updatedSupplier = repository.getSupplierById(supplierId)!!
        // Initial 250 + remaining 500 = 750
        assertEquals(750.0.cents, updatedSupplier.debtBalance)

        val supplierPayments = repository.supplierPayments.first().filter { it.supplierId == supplierId }
        assertEquals(1, supplierPayments.size)
        val pmt = supplierPayments.first()
        assertTrue(pmt.purchaseId > 0L)
        assertEquals(300.0.cents, pmt.amount)
        assertEquals(1050.0.cents, pmt.previousDebt) // 250 + 800 invoice
        assertEquals(750.0.cents, pmt.newDebt)       // 1050 - 300 paid
    }

    @Test
    fun settleSaleInvoicePartially_updatesInvoiceBalanceAndCustomerStatement() = runBlocking {
        val productId = appDao.insertProduct(testProduct("P-300", "سكر", 8.0.cents, 10.0.cents, stock = 100.0))
        val customerId = appDao.insertCustomer(
            CustomerEntity(
                name = "كريم",
                phone = "0600000003",
                balance = 0L
            )
        )

        val sale = repository.recordSaleInvoice(
            testSale(
                invoiceNumber = "2001",
                customerId = customerId,
                customerName = "كريم",
                totalAmount = 1000.0.cents,
                paidAmount = 0L,
                paymentMethod = "DEBT"
            ),
            listOf(
                SaleItemEntity(
                    saleId = 0L,
                    productId = productId,
                    productName = "سكر",
                    quantity = 100.0,
                    unitPrice = 10.0.cents,
                    totalLinePrice = 1000.0.cents
                )
            ),
            currentUser = testManager
        )

        // First partial payment of 350
        val payment1 = repository.settleSaleInvoice(
            saleId = sale.id,
            amountCents = 350.0.cents,
            paymentMethod = "TRANSFER",
            notes = "دفعة أولى",
            cashierName = "Admin"
        )

        assertEquals(sale.id, payment1.saleId)
        assertEquals(350.0.cents, payment1.amount)
        assertEquals(1000.0.cents, payment1.previousBalance)
        assertEquals(650.0.cents, payment1.newBalance)
        assertEquals("TRANSFER", payment1.paymentMethod)
        assertEquals("دفعة أولى", payment1.notes)

        val saleAfter1 = repository.getSaleById(sale.id)!!
        assertEquals(350.0.cents, saleAfter1.paidAmount)
        assertEquals(650.0.cents, saleAfter1.remainingDueCents)
        assertEquals(650.0.cents, repository.getCustomerById(customerId)!!.balance)

        // Second partial payment of 250
        val payment2 = repository.settleSaleInvoice(
            saleId = sale.id,
            amountCents = 250.0.cents,
            paymentMethod = "CASH",
            cashierName = "Admin"
        )
        assertEquals(650.0.cents, payment2.previousBalance)
        assertEquals(400.0.cents, payment2.newBalance)

        val allCustomerPayments = repository.customerPayments.first().filter { it.customerId == customerId }
        assertEquals(2, allCustomerPayments.size)
        assertEquals(600.0.cents, allCustomerPayments.sumOf { it.amount })
        assertEquals(400.0.cents, repository.getCustomerById(customerId)!!.balance)
    }

    @Test
    fun settlePurchaseInvoicePartially_updatesInvoiceDebtAndSupplierStatement() = runBlocking {
        val productId = appDao.insertProduct(testProduct("P-400", "شاي", 20.0.cents, 25.0.cents, stock = 10.0))
        val supplierId = appDao.insertSupplier(
            SupplierEntity(
                name = "مورد الشاي",
                phone = "0600000004",
                debtBalance = 0L
            )
        )

        repository.recordPurchaseInvoice(
            testPurchase(
                invoiceNumber = "SUP-99",
                supplierId = supplierId,
                supplierName = "مورد الشاي",
                totalAmount = 1200.0.cents,
                paidAmount = 0L
            ),
            listOf(
                PurchaseItemEntity(
                    purchaseId = 0L,
                    productId = productId,
                    productName = "شاي",
                    quantity = 60.0,
                    unitPrice = 20.0.cents
                )
            ),
            currentUser = testManager
        )
        val purchase = repository.purchases.first().first { it.invoiceNumber == "SUP-99" }
        assertEquals(1200.0.cents, repository.getSupplierById(supplierId)!!.debtBalance)

        val payment = repository.settlePurchaseInvoice(
            purchaseId = purchase.id,
            amountCents = 450.0.cents,
            paymentMethod = "CHECK",
            notes = "دفعة شيك جزئية",
            cashierName = "Manager"
        )

        assertEquals(purchase.id, payment.purchaseId)
        assertEquals(450.0.cents, payment.amount)
        assertEquals(1200.0.cents, payment.previousDebt)
        assertEquals(750.0.cents, payment.newDebt)
        assertEquals("CHECK", payment.paymentMethod)

        val purchaseAfter = repository.getPurchaseById(purchase.id)!!
        assertEquals(450.0.cents, purchaseAfter.paidAmount)
        assertEquals(750.0.cents, purchaseAfter.remainingDebtCents)
        assertEquals(750.0.cents, repository.getSupplierById(supplierId)!!.debtBalance)

        val supplierPayments = repository.supplierPayments.first().filter { it.supplierId == supplierId }
        assertEquals(1, supplierPayments.size)
        assertEquals(450.0.cents, supplierPayments.first().amount)
    }

    @Test
    fun editInvoicePaidAmountUpwards_recordsDeltaInStatement() = runBlocking {
        val productId = appDao.insertProduct(testProduct("P-500", "قهوة", 30.0.cents, 40.0.cents))
        val customerId = appDao.insertCustomer(
            CustomerEntity(
                name = "سعيد",
                phone = "0600000005",
                balance = 0L
            )
        )
        val sale = repository.recordSaleInvoice(
            testSale(
                invoiceNumber = "3001",
                customerId = customerId,
                customerName = "سعيد",
                totalAmount = 400.0.cents,
                paidAmount = 0L,
                paymentMethod = "DEBT"
            ),
            listOf(
                SaleItemEntity(
                    saleId = 0L,
                    productId = productId,
                    productName = "قهوة",
                    quantity = 10.0,
                    unitPrice = 40.0.cents,
                    totalLinePrice = 400.0.cents
                )
            ),
            currentUser = testManager
        )

        val items = repository.getSaleItems(sale.id)
        repository.updateSaleInvoice(
            updatedSale = sale.copy(paidAmount = 150.0.cents),
            updatedItems = items,
            recordPaymentDelta = true
        )

        val payments = repository.customerPayments.first().filter { it.customerId == customerId }
        assertEquals(1, payments.size)
        assertEquals(150.0.cents, payments.first().amount)
        assertEquals(400.0.cents, payments.first().previousBalance)
        assertEquals(250.0.cents, payments.first().newBalance)
    }

    @Test
    fun printedSaleAndPurchaseInvoices_doNotIncludeRemainingDebtLine() {
        val creditSale = testSale(
            id = 1L,
            invoiceNumber = "INV-77",
            customerId = 1L,
            customerName = "زبون آجل",
            totalAmount = 500.0.cents,
            paidAmount = 200.0.cents,
            paymentMethod = "CREDIT"
        )
        val saleItems = listOf(
            SaleItemEntity(
                saleId = 1L,
                productId = 1L,
                productName = "منتج تجريبي",
                quantity = 5.0,
                unitPrice = 100.0.cents,
                totalLinePrice = 500.0.cents
            )
        )
        val htmlInvoice = HtmlInvoiceAndReceiptFormatter.buildHtmlInvoice(
            sale = creditSale,
            items = saleItems,
            settings = GlobalSettingsEntity(shopName = "متجر النور", currency = "DH")
        )
        assertFalse("Printed HTML sale invoice must not contain المتبقي في الذمة", htmlInvoice.contains("المتبقي في الذمة"))
        assertFalse("Printed HTML sale invoice must not contain Reste", htmlInvoice.contains("Reste", ignoreCase = true))

        val creditPurchase = testPurchase(
            id = 2L,
            invoiceNumber = "PUR-77",
            supplierId = 1L,
            supplierName = "مورد آجل",
            totalAmount = 900.0.cents,
            paidAmount = 300.0.cents
        )
        val purchaseItems = listOf(
            PurchaseItemEntity(
                purchaseId = 2L,
                productId = 1L,
                productName = "بضاعة",
                quantity = 9.0,
                unitPrice = 100.0.cents
            )
        )
        val thermalPurchase = generateThermalPrinterStringForPurchase(
            purchase = creditPurchase,
            items = purchaseItems,
            shopName = "متجر النور"
        )
        assertFalse("Printed thermal purchase invoice must not contain RESTE DU", thermalPurchase.contains("RESTE DU", ignoreCase = true))
        assertFalse("Printed thermal purchase invoice must not contain المتبقي في الذمة", thermalPurchase.contains("المتبقي في الذمة"))
    }
}
