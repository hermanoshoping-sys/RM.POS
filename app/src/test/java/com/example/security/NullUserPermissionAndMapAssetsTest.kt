package com.example.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDao
import com.example.data.AppDatabase
import com.example.data.CategoryEntity
import com.example.data.CustomerEntity
import com.example.data.GlobalSettingsEntity
import com.example.data.PosRepository
import com.example.data.ProductEntity
import com.example.data.PurchaseEntity
import com.example.data.PurchaseItemEntity
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.data.SupplierEntity
import com.example.data.UserEntity
import com.example.data.UserRole
import com.example.domain.OperationErrorCode
import com.example.domain.stocktaking.InventoryType
import com.example.ui.screens.DEFAULT_OPENFREEMAP_STYLE_URL
import com.example.ui.screens.MapTileEngine
import com.example.ui.screens.resolveActiveMapProvider
import com.example.ui.screens.resolveMapTileProvider
import com.example.ui.viewmodels.CartViewModel
import com.example.ui.viewmodels.DebtLedgerViewModel
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies:
 * 1. All 6 repository permission guards (and ViewModel delegates) fail CLOSED when currentUser == null.
 * 2. Bundled Leaflet assets exist in app/src/main/assets/leaflet/ so map screens never open blank.
 * 3. Map tile provider resolution rejects empty, non-HTTPS, and public tile.openstreetmap.org URLs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NullUserPermissionAndMapAssetsTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var repository: PosRepository
    private lateinit var vmScope: CoroutineScope

    private val managerUser = UserEntity(
        id = 1L,
        username = "manager",
        passwordHash = "hash",
        role = UserRole.MANAGER
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appDao = database.appDao()
        repository = PosRepository(appDao, database)
        vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        vmScope.cancel()
        database.close()
    }

    @Test
    fun allSixRepositoryPermissionGuardsRejectNullUser() = runBlocking {
        val catId = appDao.insertCategory(CategoryEntity(name = "مشروبات"))
        val prodId = appDao.insertProduct(
            ProductEntity(
                barcode = "SEC-001",
                name = "منتج محمي",
                categoryId = catId,
                unit = "قطعة",
                purchasePrice = 500L,
                sellPrice = 800L,
                currentStock = 20.0,
                minStock = 2.0
            )
        )
        val supId = appDao.insertSupplier(SupplierEntity(name = "مورد محمي", phone = "0600000001"))

        val sale = SaleEntity(
            invoiceNumber = "INV-NULL-1",
            timestamp = System.currentTimeMillis(),
            cashierName = "anon",
            totalAmount = 800L,
            discountAmount = 0L,
            discountType = "VALUE",
            paidAmount = 800L,
            changeAmount = 0L,
            paymentMethod = "CASH"
        )
        val saleItems = listOf(
            SaleItemEntity(
                saleId = 0L,
                productId = prodId,
                productName = "منتج محمي",
                quantity = 1.0,
                unitPrice = 800L,
                totalLinePrice = 800L
            )
        )
        val purchase = PurchaseEntity(
            timestamp = System.currentTimeMillis(),
            supplierId = supId,
            supplierName = "مورد محمي",
            totalAmount = 500L,
            paidAmount = 500L
        )
        val purchaseItems = listOf(
            PurchaseItemEntity(
                purchaseId = 0L,
                productId = prodId,
                productName = "منتج محمي",
                quantity = 1.0,
                unitPrice = 500L
            )
        )

        // 1. wipeAllEntitiesData(null) must throw SecurityException and leave data intact
        assertThrowsSecurityException("wipeAllEntitiesData(null)") {
            repository.wipeAllEntitiesData(currentUser = null)
        }
        assertEquals(1, appDao.getAllProductsList().size)

        // 2. recordSaleInvoice(..., null) must throw SecurityException
        assertThrowsSecurityException("recordSaleInvoice(null)") {
            repository.recordSaleInvoice(sale, saleItems, currentUser = null)
        }
        assertEquals(20.0, appDao.getProductById(prodId)!!.currentStock, 0.001)

        // 3. deleteSaleInvoice(..., null) must throw SecurityException (seed a real sale first as manager)
        val createdSale = repository.recordSaleInvoice(sale, saleItems, currentUser = managerUser)
        assertThrowsSecurityException("deleteSaleInvoice(null)") {
            repository.deleteSaleInvoice(createdSale.id, currentUser = null)
        }
        assertNotNull("Sale must remain intact after null-user delete attempt", appDao.getSaleById(createdSale.id))

        // 4. recordPurchaseInvoice(..., null) must throw SecurityException
        assertThrowsSecurityException("recordPurchaseInvoice(null)") {
            repository.recordPurchaseInvoice(purchase, purchaseItems, currentUser = null)
        }

        // 5. deletePurchaseInvoice(..., null) must throw SecurityException (seed a real purchase first as manager)
        val createdPurchaseId = repository.recordPurchaseInvoice(purchase, purchaseItems, currentUser = managerUser)
        assertThrowsSecurityException("deletePurchaseInvoice(null)") {
            repository.deletePurchaseInvoice(createdPurchaseId, currentUser = null)
        }
        assertNotNull("Purchase must remain intact after null-user delete attempt", appDao.getPurchaseById(createdPurchaseId))

        // 6. Stocktaking requireInventoryAccess(null) must throw SecurityException
        assertThrowsSecurityException("createInventorySession(null)") {
            repository.createInventorySession(type = InventoryType.GENERAL, currentUser = null)
        }
        val session = repository.createInventorySession(type = InventoryType.GENERAL, currentUser = managerUser)
        assertThrowsSecurityException("recordInventoryItemCount(null)") {
            repository.recordInventoryItemCount(session.id, prodId, 15.0, currentUser = null)
        }
        assertThrowsSecurityException("updateInventorySession(null)") {
            repository.updateInventorySession(session.id, title = "Unauthorized Edit", notes = "", currentUser = null)
        }
        assertThrowsSecurityException("cancelInventorySession(null)") {
            repository.cancelInventorySession(session.id, reason = "Unauthorized", currentUser = null)
        }
        assertThrowsSecurityException("deleteInventorySession(null)") {
            repository.deleteInventorySession(session.id, currentUser = null)
        }
    }

    @Test
    fun checkoutAndCustomerEditViewModelsRejectNullUser() = runBlocking {
        val prod = ProductEntity(
            id = 50L,
            barcode = "VM-SEC-1",
            name = "منتج اختبار",
            categoryId = 1L,
            unit = "قطعة",
            purchasePrice = 100L,
            sellPrice = 200L,
            currentStock = 10.0,
            minStock = 1.0
        )
        appDao.insertProduct(prod)

        // CheckoutExecutionDelegate must deny null user with PERMISSION_DENIED
        val cartVm = CartViewModel(repository = repository, externalScope = vmScope)
        cartVm.addToCart(prod, 1.0)
        val checkoutError = CompletableDeferred<String>()
        cartVm.executeCheckout(
            appContext = context,
            currentUser = null,
            language = "AR",
            settings = GlobalSettingsEntity(),
            printerConnectionType = "BLUETOOTH",
            printerName = "",
            printerMac = "",
            printerIp = "",
            printerPort = 9100,
            printerPaperWidth = 80,
            autoPrintReceipt = false,
            onError = { checkoutError.complete(it) },
            onComplete = { fail("Checkout with currentUser = null must never succeed") }
        )
        withTimeout(5_000L) { checkoutError.await() }
        assertEquals(OperationErrorCode.PERMISSION_DENIED, cartVm.latestCheckoutError.value?.code)

        // DebtLedgerViewModel.saveCustomer edit path must reject null user
        val customerId = appDao.insertCustomer(CustomerEntity(name = "زبون أصلي", phone = "0611111111"))
        val debtVm = DebtLedgerViewModel(repository = repository, externalScope = vmScope)
        val editResult = CompletableDeferred<Pair<Boolean, String>>()
        debtVm.saveCustomer(
            id = customerId,
            name = "زبون معدل بدون مستخدم",
            phone = "0611111111",
            address = "",
            balance = 0.0,
            latitude = null,
            longitude = null,
            isGeoLocationRequired = false,
            customerNumber = 0L,
            barcode = "",
            currentUser = null
        ) { success, msg -> editResult.complete(success to msg) }
        val (success, _) = withTimeout(5_000L) { editResult.await() }
        assertEquals(false, success)
        assertEquals("زبون أصلي", appDao.getCustomerById(customerId)?.name)
    }

    @Test
    fun bundledLeafletAndMapLibreAssetsExistAndAreValid() {
        val leafletDir = listOf(
            File("src/main/assets/leaflet"),
            File("app/src/main/assets/leaflet")
        ).firstOrNull { it.isDirectory }
        assertNotNull("app/src/main/assets/leaflet directory must exist", leafletDir)

        val jsFile = File(leafletDir!!, "leaflet.js")
        val cssFile = File(leafletDir, "leaflet.css")
        assertTrue("leaflet.js must exist and be non-empty", jsFile.isFile && jsFile.length() > 50_000L)
        assertTrue("leaflet.css must exist and be non-empty", cssFile.isFile && cssFile.length() > 5_000L)
        assertTrue("leaflet.js must contain Leaflet library banner", jsFile.readText(Charsets.UTF_8).take(256).contains("Leaflet", ignoreCase = true))
        assertTrue("leaflet.css must contain leaflet-container styles", cssFile.readText(Charsets.UTF_8).contains(".leaflet-container"))

        val mapLibreDir = listOf(
            File("src/main/assets/maplibre"),
            File("app/src/main/assets/maplibre")
        ).firstOrNull { it.isDirectory }
        assertNotNull("app/src/main/assets/maplibre directory must exist", mapLibreDir)

        val mlJs = File(mapLibreDir!!, "maplibre-gl.js")
        val mlCss = File(mapLibreDir, "maplibre-gl.css")
        assertTrue("maplibre-gl.js must exist and be non-empty", mlJs.isFile && mlJs.length() > 200_000L)
        assertTrue("maplibre-gl.css must exist and be non-empty", mlCss.isFile && mlCss.length() > 10_000L)
        assertTrue("maplibre-gl.css must contain maplibregl styles", mlCss.readText(Charsets.UTF_8).contains(".maplibregl-map"))
    }

    @Test
    fun resolveMapTileProviderEnforcesLicensedHttpsProviderAndDefaultsToOpenFreeMap() {
        assertNull(resolveMapTileProvider("", ""))
        assertNull(resolveMapTileProvider("   ", ""))
        assertNull(resolveMapTileProvider("http://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=k", ""))
        assertNull(resolveMapTileProvider("https://tile.openstreetmap.org/{z}/{x}/{y}.png", ""))
        assertNull(resolveMapTileProvider("https://a.tile.openstreetmap.org/{z}/{x}/{y}.png", ""))
        assertNull(resolveMapTileProvider("https://b.tile.openstreetmap.org/{z}/{x}/{y}.png", ""))
        assertNull(resolveMapTileProvider("https://c.tile.openstreetmap.org/{z}/{x}/{y}.png", ""))

        val defaultActive = resolveActiveMapProvider("", "")
        assertEquals(MapTileEngine.OPENFREEMAP_VECTOR, defaultActive.engine)
        assertEquals(DEFAULT_OPENFREEMAP_STYLE_URL, defaultActive.url)
        assertTrue(defaultActive.attribution.contains("OpenFreeMap"))

        val osmBlockedActive = resolveActiveMapProvider("https://tile.openstreetmap.org/{z}/{x}/{y}.png", "")
        assertEquals(MapTileEngine.OPENFREEMAP_VECTOR, osmBlockedActive.engine)
        assertEquals(DEFAULT_OPENFREEMAP_STYLE_URL, osmBlockedActive.url)

        val maptiler = resolveActiveMapProvider(
            configuredUrl = "https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=DEMO",
            configuredAttribution = "&copy; MapTiler &copy; OpenStreetMap contributors"
        )
        assertEquals(MapTileEngine.CUSTOM_RASTER_LEAFLET, maptiler.engine)
        assertEquals("https://api.maptiler.com/maps/streets-v2/{z}/{x}/{y}.png?key=DEMO", maptiler.url)
        assertTrue(maptiler.attribution.contains("MapTiler"))
    }

    private suspend fun assertThrowsSecurityException(label: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected SecurityException for $label when currentUser == null")
        } catch (_: SecurityException) {
            // Expected fail-closed behavior
        }
    }
}
