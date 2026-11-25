package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.util.BarcodeGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomerBarcodeAndSequenceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: PosRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(db.appDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testCustomerSequenceUniquenessAndNeverReusedAfterDeletion() = runBlocking {
        // Set store name
        db.appDao().insertSettings(
            GlobalSettingsEntity(
                id = 1,
                shopName = "rioual shop"
            )
        )

        // 1. Create first customer -> should be customerNumber 1
        val c1 = CustomerEntity(name = "زبون 1", phone = "0600000001")
        val id1 = repository.saveCustomer(c1)
        val saved1 = repository.getCustomerById(id1)
        assertNotNull(saved1)
        assertEquals(1L, saved1!!.customerNumber)
        assertEquals("rioualshop-1", saved1.barcode)

        // 2. Create second customer -> should be customerNumber 2
        val c2 = CustomerEntity(name = "زبون 2", phone = "0600000002")
        val id2 = repository.saveCustomer(c2)
        val saved2 = repository.getCustomerById(id2)
        assertNotNull(saved2)
        assertEquals(2L, saved2!!.customerNumber)
        assertEquals("rioualshop-2", saved2.barcode)

        // 3. Create third customer -> should be customerNumber 3
        val c3 = CustomerEntity(name = "زبون 3", phone = "0600000003")
        val id3 = repository.saveCustomer(c3)
        val saved3 = repository.getCustomerById(id3)
        assertNotNull(saved3)
        assertEquals(3L, saved3!!.customerNumber)
        assertEquals("rioualshop-3", saved3.barcode)

        // 4. Delete customer 3
        val deleteResult = repository.deleteCustomer(id3)
        assertTrue(deleteResult.first)
        val deletedCheck = repository.getCustomerById(id3)
        assertEquals(null, deletedCheck)

        // 5. Add next customer -> MUST NOT reuse #3, MUST allocate #4!
        val c4 = CustomerEntity(name = "زبون 4", phone = "0600000004")
        val id4 = repository.saveCustomer(c4)
        val saved4 = repository.getCustomerById(id4)
        assertNotNull(saved4)
        assertEquals(4L, saved4!!.customerNumber)
        assertEquals("rioualshop-4", saved4.barcode)

        // 6. Delete customer 4, 2, 1 (all customers deleted)
        repository.deleteCustomer(id4)
        repository.deleteCustomer(id2)
        repository.deleteCustomer(id1)

        // 7. Add new customer when table is empty -> MUST continue from 5!
        val c5 = CustomerEntity(name = "زبون 5 بعد الحذف الكامل", phone = "0600000005")
        val id5 = repository.saveCustomer(c5)
        val saved5 = repository.getCustomerById(id5)
        assertNotNull(saved5)
        assertEquals(5L, saved5!!.customerNumber)
        assertEquals("rioualshop-5", saved5.barcode)
    }

    @Test
    fun testExistingCustomerBarcodePreservedWhenStoreNameChanges() = runBlocking {
        // Initial store name
        db.appDao().insertSettings(
            GlobalSettingsEntity(
                id = 1,
                shopName = "rioual shop"
            )
        )

        // Create customer 1
        val c1 = CustomerEntity(name = "كريم", phone = "0611223344")
        val id1 = repository.saveCustomer(c1)
        val saved1 = repository.getCustomerById(id1)
        assertNotNull(saved1)
        assertEquals("rioualshop-1", saved1!!.barcode)

        // Change store name in Settings
        db.appDao().insertSettings(
            GlobalSettingsEntity(
                id = 1,
                shopName = "Al Baraka Market"
            )
        )

        // Update customer 1 (e.g. changing phone/name)
        repository.saveCustomer(saved1.copy(phone = "0699887766"))
        val updated1 = repository.getCustomerById(id1)
        assertNotNull(updated1)
        // CRITICAL REQUIREMENT: Barcode MUST remain "rioualshop-1" and NOT change!
        assertEquals("rioualshop-1", updated1!!.barcode)
        assertEquals(1L, updated1.customerNumber)
        assertEquals("0699887766", updated1.phone)

        // Newly added customer 2 should use the new store name
        val c2 = CustomerEntity(name = "سعيد", phone = "0622334455")
        val id2 = repository.saveCustomer(c2)
        val saved2 = repository.getCustomerById(id2)
        assertNotNull(saved2)
        assertEquals(2L, saved2!!.customerNumber)
        assertEquals("albarakamarket-2", saved2.barcode)
    }

    @Test
    fun testMigration14To15UpgradesDatabaseSafely() {
        val testDbFile = File(context.filesDir, "test_v14_to_v15.db")
        if (testDbFile.exists()) testDbFile.delete()

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(sqLiteDb: SupportSQLiteDatabase) {
                    sqLiteDb.execSQL("""
                        CREATE TABLE IF NOT EXISTS global_settings (
                            id INTEGER PRIMARY KEY NOT NULL,
                            shopName TEXT NOT NULL DEFAULT ''
                        )
                    """.trimIndent())
                    sqLiteDb.execSQL("""
                        INSERT INTO global_settings (id, shopName) VALUES (1, 'rioual shop')
                    """.trimIndent())

                    sqLiteDb.execSQL("""
                        CREATE TABLE IF NOT EXISTS customers (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            phone TEXT NOT NULL,
                            address TEXT NOT NULL DEFAULT '',
                            balance REAL NOT NULL DEFAULT 0.0,
                            totalPurchases REAL NOT NULL DEFAULT 0.0,
                            latitude REAL DEFAULT NULL,
                            longitude REAL DEFAULT NULL,
                            geoRadiusMeters REAL NOT NULL DEFAULT 150.0,
                            isGeoLocationRequired INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())

                    sqLiteDb.execSQL("""
                        CREATE TABLE IF NOT EXISTS audit_logs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            actionType TEXT NOT NULL,
                            entityType TEXT NOT NULL,
                            entityId TEXT NOT NULL,
                            details TEXT NOT NULL,
                            username TEXT NOT NULL,
                            userRole TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            isCritical INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())

                    sqLiteDb.execSQL("INSERT INTO customers (name, phone) VALUES ('زبون أول', '06001')")
                    sqLiteDb.execSQL("INSERT INTO customers (name, phone) VALUES ('زبون ثان', '06002')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = factory.create(config)
        val sqLiteDb = helper.writableDatabase

        // Execute MIGRATION_14_15
        AppDatabase.MIGRATION_14_15.migrate(sqLiteDb)

        // 1. Check customer_sequences table exists
        val seqCursor = sqLiteDb.query("SELECT lastCustomerNumber FROM customer_sequences WHERE id = 1")
        assertTrue(seqCursor.moveToFirst())
        assertEquals(2L, seqCursor.getLong(0))
        seqCursor.close()

        // 2. Check customer 1 backfilled with customerNumber 1 and barcode rioualshop-1
        val c1Cursor = sqLiteDb.query("SELECT customerNumber, barcode FROM customers WHERE id = 1")
        assertTrue(c1Cursor.moveToFirst())
        assertEquals(1L, c1Cursor.getLong(0))
        assertEquals("rioualshop-1", c1Cursor.getString(1))
        c1Cursor.close()

        // 3. Check customer 2 backfilled with customerNumber 2 and barcode rioualshop-2
        val c2Cursor = sqLiteDb.query("SELECT customerNumber, barcode FROM customers WHERE id = 2")
        assertTrue(c2Cursor.moveToFirst())
        assertEquals(2L, c2Cursor.getLong(0))
        assertEquals("rioualshop-2", c2Cursor.getString(1))
        c2Cursor.close()

        sqLiteDb.close()
        testDbFile.delete()
    }

    @Test
    fun testCode128BarcodeGeneratorProducesValidModulesAndBitmap() {
        val content = "rioualshop-1"
        val modules = BarcodeGenerator.encodeToModules(content)
        assertNotNull(modules)
        assertTrue(modules!!.isNotEmpty())

        val bitmap = BarcodeGenerator.generateCode128Bitmap(content, width = 400, height = 120, showText = true)
        assertNotNull(bitmap)
        assertEquals(400, bitmap.width)
        assertEquals(120, bitmap.height)
    }

    @Test
    fun testBatchCustomerDeletionProtectsDebtorsAndDeletesEligible() = runBlocking {
        // Customer A: eligible for deletion (0 balance, no sales)
        val idA = repository.saveCustomer(CustomerEntity(name = "زبون A", phone = "0601"))
        // Customer B: has debt -> should be protected from deletion
        val idB = repository.saveCustomer(CustomerEntity(name = "زبون B", phone = "0602", balance = 150.0))
        // Customer C: eligible for deletion
        val idC = repository.saveCustomer(CustomerEntity(name = "زبون C", phone = "0603"))

        val (deletedCount, warnings) = repository.deleteCustomersBatch(listOf(idA, idB, idC))
        assertEquals(2, deletedCount)
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("زبون B"))

        // Verify Customer A & C deleted, Customer B remains intact
        assertEquals(null, repository.getCustomerById(idA))
        assertNotNull(repository.getCustomerById(idB))
        assertEquals(null, repository.getCustomerById(idC))
    }

    @Test
    fun testBatchProductDeletion() = runBlocking {
        val p1 = ProductEntity(barcode = "PROD-1", name = "منتج 1", categoryId = 1, unit = "قطعة", purchasePrice = 10.0, sellPrice = 15.0, currentStock = 5.0, minStock = 1.0)
        val p2 = ProductEntity(barcode = "PROD-2", name = "منتج 2", categoryId = 1, unit = "قطعة", purchasePrice = 20.0, sellPrice = 25.0, currentStock = 8.0, minStock = 1.0)
        val p3 = ProductEntity(barcode = "PROD-3", name = "منتج 3", categoryId = 1, unit = "قطعة", purchasePrice = 30.0, sellPrice = 35.0, currentStock = 2.0, minStock = 1.0)

        val id1 = db.appDao().insertProduct(p1)
        val id2 = db.appDao().insertProduct(p2)
        val id3 = db.appDao().insertProduct(p3)

        val deletedCount = repository.deleteProductsBatch(listOf(id1, id3))
        assertEquals(2, deletedCount)

        assertEquals(null, repository.getProductById(id1))
        assertNotNull(repository.getProductById(id2))
        assertEquals(null, repository.getProductById(id3))
    }

    @Test
    fun testCustomerBarcodeBadgeDataPreparation() {
        val customer = CustomerEntity(
            id = 10,
            customerNumber = 7,
            name = "محمد أمين",
            phone = "0612345678",
            address = "شارع الحسن الثاني",
            barcode = "rioualshop-7"
        )
        val barcodeBitmap = com.example.util.BarcodeGenerator.generateCode128Bitmap(
            content = customer.barcode,
            width = 500,
            height = 140,
            showText = true
        )
        assertNotNull(barcodeBitmap)
        assertEquals(500, barcodeBitmap.width)
        assertEquals(140, barcodeBitmap.height)
        assertEquals("rioualshop-7", customer.barcode)
    }
}
