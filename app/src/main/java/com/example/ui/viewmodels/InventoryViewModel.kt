package com.example.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.ProductExcelManager
import com.example.util.cents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Modular ViewModel for Inventory, Products, Categories, and Paged Stock Movements.
 */
class InventoryViewModel(
    private val repository: PosRepository,
    externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    val products: StateFlow<List<ProductEntity>> = repository.products
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<CategoryEntity>> = repository.categories
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val lowStockProducts: StateFlow<List<ProductEntity>> = repository.lowStockProducts
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _productsPage = MutableStateFlow(0)
    val productsPage: StateFlow<Int> = _productsPage.asStateFlow()
    val productsPageSize: Int = 50

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedProducts: StateFlow<List<ProductEntity>> = _productsPage.flatMapLatest { page ->
        repository.getProductsPaged(limit = productsPageSize, offset = page * productsPageSize)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun nextProductsPage() { _productsPage.value += 1 }
    fun prevProductsPage() { if (_productsPage.value > 0) _productsPage.value -= 1 }
    fun resetProductsPage() { _productsPage.value = 0 }

    val productSearchQuery = MutableStateFlow("")
    val productCategoryFilter = MutableStateFlow<Long?>(null)
    private val _visibleProductLimit = MutableStateFlow(60)
    val visibleProductLimit: StateFlow<Int> = _visibleProductLimit.asStateFlow()

    val totalProductsCount: StateFlow<Int> = repository.getProductsTotalCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val debouncedProductCriteria: Flow<Pair<String, Long?>> = combine(
        productSearchQuery.map { it.trim() }.debounce(200L).distinctUntilChanged(),
        productCategoryFilter
    ) { q, catId -> q to catId }

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredProductsCount: StateFlow<Int> = debouncedProductCriteria
        .flatMapLatest { (q, catId) -> repository.getProductsFilteredCount(q, catId) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredPagedProducts: StateFlow<List<ProductEntity>> = combine(
        debouncedProductCriteria,
        _visibleProductLimit
    ) { (q, catId), limit -> Triple(q, catId, limit) }
        .flatMapLatest { (q, catId, limit) ->
            repository.getProductsFilteredPaged(q, catId, limit = limit, offset = 0)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasMoreFilteredProducts: StateFlow<Boolean> = combine(
        filteredPagedProducts,
        filteredProductsCount
    ) { loaded, total -> loaded.size < total }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

    fun setProductSearchQuery(query: String) {
        productSearchQuery.value = query
        _visibleProductLimit.value = 60
    }

    fun setProductCategoryFilter(categoryId: Long?) {
        productCategoryFilter.value = categoryId
        _visibleProductLimit.value = 60
    }

    fun loadMoreFilteredProducts(step: Int = 60) {
        _visibleProductLimit.value = _visibleProductLimit.value + step
    }

    fun resetFilteredProductsWindow() {
        productSearchQuery.value = ""
        productCategoryFilter.value = null
        _visibleProductLimit.value = 60
    }

    private val _pendingLowStockFilter = MutableStateFlow(false)
    val pendingLowStockFilter: StateFlow<Boolean> = _pendingLowStockFilter.asStateFlow()

    fun requestOpenLowStockFilter() {
        _pendingLowStockFilter.value = true
    }

    fun consumePendingLowStockFilter(): Boolean {
        val pending = _pendingLowStockFilter.value
        if (pending) {
            _pendingLowStockFilter.value = false
        }
        return pending
    }

    val stockMovements: StateFlow<List<StockMovementEntity>> = repository.stockMovements
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _stockMovementsPage = MutableStateFlow(0)
    val stockMovementsPage: StateFlow<Int> = _stockMovementsPage.asStateFlow()
    val stockMovementsPageSize: Int = 50

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedStockMovements: StateFlow<List<StockMovementEntity>> = _stockMovementsPage.flatMapLatest { page ->
        repository.getStockMovementsPaged(limit = stockMovementsPageSize, offset = page * stockMovementsPageSize)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentStockMovements: StateFlow<List<StockMovementEntity>> = repository.getRecentStockMovements(50)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val stockMovementsCount: StateFlow<Int> = repository.getStockMovementsCount()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    fun nextStockMovementsPage() { _stockMovementsPage.value += 1 }
    fun prevStockMovementsPage() { if (_stockMovementsPage.value > 0) _stockMovementsPage.value -= 1 }
    fun resetStockMovementsPage() { _stockMovementsPage.value = 0 }

    fun addNewCategory(name: String, imageUri: String? = null) {
        scope.launch {
            repository.saveCategory(CategoryEntity(name = name, imageUri = imageUri))
        }
    }

    fun updateCategory(id: Long, name: String, imageUri: String? = null) {
        scope.launch {
            val existing = categories.value.find { it.id == id }
            val finalImage = imageUri ?: existing?.imageUri
            repository.saveCategory(CategoryEntity(id = id, name = name, imageUri = finalImage))
            products.value.filter { it.categoryId == id }.forEach { p ->
                repository.saveProduct(p.copy(categoryName = name))
            }
        }
    }

    fun updateCategoryImage(id: Long, imageUri: String?) {
        scope.launch { repository.updateCategoryImage(id, imageUri) }
    }

    fun updateProductImage(id: Long, imageUri: String?) {
        scope.launch { repository.updateProductImage(id, imageUri) }
    }

    fun deleteCategory(category: CategoryEntity) {
        scope.launch { repository.deleteCategory(category) }
    }

    fun seedDefaultCategories() {
        scope.launch {
            val defaults = listOf(
                "المشروبات والعصائر / Boissons & Jus",
                "المواد الغذائية والمعلبات / Épicerie & Conserves",
                "الألبان والأجبان / Produits Laitiers",
                "المنظفات ومستلزمات النظافة / Détergents",
                "البسكويت والحلويات / Biscuits & Chocolats",
                "المخبوزات والحلويات / Boulangerie",
                "الخضار والفواكه / Fruits & Légumes",
                "اللحوم والدواجن / Boucherie & Volailles",
                "العناية الشخصية / Hygiène & Soins",
                "المجمدات والمثلجات / Surgelés"
            )
            defaults.forEach { name ->
                if (categories.value.none { it.name.trim().equals(name.trim(), ignoreCase = true) }) {
                    repository.saveCategory(CategoryEntity(name = name))
                }
            }
        }
    }

    fun saveProduct(
        id: Long = 0,
        barcode: String,
        name: String,
        description: String,
        categoryId: Long,
        categoryName: String,
        unit: String,
        purchasePrice: Double,
        sellPrice: Double,
        wholesalePrice: Double,
        specialPrice: Double,
        currentStock: Double,
        minStock: Double,
        imageUri: String? = null
    ) {
        scope.launch {
            var actualCatId = categoryId
            var actualCatName = categoryName.trim()
            if (actualCatName.isBlank()) {
                actualCatName = categories.value.find { it.id == categoryId }?.name ?: "عام / Général"
            }
            val existing = categories.value.find { it.name.trim().equals(actualCatName, ignoreCase = true) }
            if (existing != null) {
                actualCatId = existing.id
                actualCatName = existing.name
            } else if (actualCatName.isNotBlank()) {
                actualCatId = repository.saveCategory(CategoryEntity(name = actualCatName))
            }

            repository.saveProduct(
                ProductEntity(
                    id = id,
                    barcode = barcode,
                    name = name,
                    description = description,
                    categoryId = actualCatId,
                    categoryName = actualCatName,
                    unit = unit,
                    purchasePrice = purchasePrice.cents,
                    sellPrice = sellPrice.cents,
                    wholesalePrice = wholesalePrice.cents,
                    specialPrice = specialPrice.cents,
                    currentStock = currentStock,
                    minStock = minStock,
                    imageUri = imageUri
                )
            )
        }
    }

    fun saveProduct(product: ProductEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                repository.saveProduct(product)
                onResult(true, "تم حفظ المنتج بنجاح")
            } catch (e: Exception) {
                android.util.Log.e("InventoryViewModel", "Failed to save product", e)
                onResult(false, e.localizedMessage ?: "فشل حفظ المنتج")
            }
        }
    }

    fun deleteProduct(product: ProductEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                repository.deleteProduct(product)
                onResult(true, "تم حذف المنتج بنجاح")
            } catch (e: Exception) {
                android.util.Log.e("InventoryViewModel", "Failed to delete product", e)
                onResult(false, e.localizedMessage ?: "فشل حذف المنتج")
            }
        }
    }

    fun deleteProductsBatch(productIds: List<Long>, onComplete: (Int) -> Unit = {}) {
        scope.launch {
            onComplete(repository.deleteProductsBatch(productIds))
        }
    }

    fun importProductsFromExcel(
        rows: List<ProductExcelManager.ProductExcelRow>,
        updateExisting: Boolean,
        onResult: (PosRepository.ImportProductsResult) -> Unit
    ) {
        scope.launch {
            onResult(repository.importProductsFromExcel(rows, updateExisting))
        }
    }

    fun saveCategory(category: CategoryEntity, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                repository.saveCategory(category)
                onResult(true, "تم حفظ التصنيف بنجاح")
            } catch (e: Exception) {
                android.util.Log.e("InventoryViewModel", "Failed to save category", e)
                onResult(false, e.localizedMessage ?: "فشل حفظ التصنيف")
            }
        }
    }
}
