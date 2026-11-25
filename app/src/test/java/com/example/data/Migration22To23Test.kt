package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration22To23Test {

    private fun resolveSchemaJsonFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.example.data.AppDatabase/$version.json"),
            File("app/schemas/com.example.data.AppDatabase/$version.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Exported Room schema JSON for version $version not found")
    }

    private fun createV22DatabaseFromSchema(
        context: Context,
        dbName: String,
        seedData: (SupportSQLiteDatabase) -> Unit
    ) {
        context.deleteDatabase(dbName)
        val schemaJson = JSONObject(resolveSchemaJsonFile(22).readText(Charsets.UTF_8))
        val dbObj = schemaJson.getJSONObject("database")
        val entities = dbObj.getJSONArray("entities")
        val setupQueries = dbObj.optJSONArray("setupQueries")

        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(22) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("PRAGMA foreign_keys=OFF")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val tableName = entity.getString("tableName")
                        val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(createSql)
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) {
                            for (j in 0 until indices.length()) {
                                val indexSql = indices.getJSONObject(j)
                                    .getString("createSql")
                                    .replace("\${TABLE_NAME}", tableName)
                                db.execSQL(indexSql)
                            }
                        }
                    }
                    if (setupQueries != null) {
                        for (i in 0 until setupQueries.length()) {
                            db.execSQL(setupQueries.getString(i))
                        }
                    }
                    seedData(db)
                    db.execSQL("PRAGMA foreign_keys=ON")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        factory.create(config).writableDatabase.close()
    }

    @Test
    fun `migration 22 to 23 creates stocktaking tables enforces unique constraints and preserves existing data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema23File = resolveSchemaJsonFile(23)
        assertTrue("Exported schema 23.json must exist and be non-empty", schema23File.exists() && schema23File.length() > 0L)
        val schema23Json = JSONObject(schema23File.readText(Charsets.UTF_8))
        assertEquals(23, schema23Json.getJSONObject("database").getInt("version"))

        val dbName = "migration_dedicated_v22_to_v23.db"
        createV22DatabaseFromSchema(context, dbName) { db ->
            db.execSQL("INSERT INTO `categories` (`id`, `name`, `imageUri`) VALUES (1, 'بقالة', NULL)")
            db.execSQL(
                "INSERT INTO `products` (`id`, `barcode`, `name`, `description`, `categoryId`, `categoryName`, `unit`, `purchasePrice`, `sellPrice`, `wholesalePrice`, `specialPrice`, `currentStock`, `minStock`, `sku`, `taxRate`, `isTaxable`, `isActive`, `imageUri`, `isSynced`, `createdAt`, `updatedAt`) " +
                    "VALUES (10, '6112300', 'حليب كامل', '', 1, 'بقالة', 'لتر', 700, 950, 850, 900, 30.0, 5.0, 'SKU-23', 0.0, 0, 1, NULL, 0, 1750000000000, 1750000000000)"
            )
        }

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_22_23)
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                // Existing v22 product data preserved intact
                val existingProduct = migratedDb.appDao().getProductById(10L)
                assertNotNull(existingProduct)
                assertEquals("حليب كامل", existingProduct!!.name)
                assertEquals(30.0, existingProduct.currentStock, 1e-6)
                assertEquals(700L, existingProduct.purchasePrice)

                // Inventory sequence initialized
                assertEquals(0L, migratedDb.stocktakingDao().getLastInventoryNumber())

                // Insert inventory + item + adjustment and verify UNIQUE(inventoryId, productId) on adjustments
                val invId = migratedDb.stocktakingDao().insertInventory(
                    InventoryEntity(reference = "INV-20261123-0001", title = "Test")
                )
                migratedDb.stocktakingDao().insertInventoryItem(
                    InventoryItemEntity(inventoryId = invId, productId = 10L, productName = "حليب كامل")
                )
                migratedDb.stocktakingDao().insertAdjustment(
                    InventoryAdjustmentEntity(
                        inventoryId = invId,
                        productId = 10L,
                        productName = "حليب كامل",
                        difference = -2.0,
                        approvedBy = "admin"
                    )
                )

                var uniqueBlocked = false
                try {
                    migratedDb.stocktakingDao().insertAdjustment(
                        InventoryAdjustmentEntity(
                            inventoryId = invId,
                            productId = 10L,
                            productName = "حليب كامل",
                            difference = -2.0,
                            approvedBy = "admin"
                        )
                    )
                } catch (_: SQLiteConstraintException) {
                    uniqueBlocked = true
                }
                assertTrue("Duplicate (inventoryId, productId) adjustment must violate UNIQUE constraint", uniqueBlocked)

                // Idempotency check: re-running MIGRATION_22_23 must succeed cleanly
                AppDatabase.MIGRATION_22_23.migrate(migratedDb.openHelper.writableDatabase)
            }
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
