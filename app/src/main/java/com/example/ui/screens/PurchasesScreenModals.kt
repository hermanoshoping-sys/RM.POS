package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.ProductEntity
import com.example.data.SupplierEntity
import com.example.ui.screens.catalog.CatalogPassToSaleDialog
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import kotlin.math.abs

@Composable
internal fun PurchasesScreenModalsHost(
    context: Context,
    viewModel: PosViewModel,
    lang: String,
    currency: String,
    cyanPrimary: Color,
    suppliersList: List<SupplierEntity>,
    selectedSupplier: SupplierEntity?,
    onSelectSupplier: (SupplierEntity) -> Unit,
    purchaseCart: List<PurchaseCartItem>,
    purchaseByProductId: Map<Long, PurchaseCartItem>,
    onUpdateCart: (List<PurchaseCartItem>) -> Unit,
    totalAmountCents: Long,
    totalAmount: Double,
    paidAmountStr: String,
    showSupplierPicker: Boolean,
    onDismissSupplierPicker: () -> Unit,
    onOpenAddSupplierFromPicker: () -> Unit,
    editingItem: PurchaseCartItem?,
    onDismissEditingItem: () -> Unit,
    showCheckoutDialog: Boolean,
    onCheckoutSuccess: () -> Unit,
    onDismissCheckoutDialog: () -> Unit,
    showAddSupplierDialog: Boolean,
    onDismissAddSupplierDialog: () -> Unit,
    selectedProductForPurchase: ProductEntity?,
    onDismissSelectedProduct: () -> Unit
) {
    if (showSupplierPicker) {
        PurchaseSupplierPickerDialog(
            suppliersList = suppliersList,
            selectedSupplier = selectedSupplier,
            lang = lang,
            cyanPrimary = cyanPrimary,
            onSelectSupplier = onSelectSupplier,
            onOpenAddSupplier = onOpenAddSupplierFromPicker,
            onDismiss = onDismissSupplierPicker
        )
    }

    editingItem?.let { item ->
        PurchaseEditCartItemDialog(
            item = item,
            lang = lang,
            cyanPrimary = cyanPrimary,
            currency = currency,
            onDelete = {
                onUpdateCart(purchaseCart.filter { it.product.id != item.product.id })
                onDismissEditingItem()
            },
            onSave = { q, c ->
                onUpdateCart(
                    if (q == 0.0) {
                        purchaseCart.filter { it.product.id != item.product.id }
                    } else {
                        purchaseCart.map { if (it.product.id == item.product.id) it.copy(quantity = q, costPrice = c) else it }
                    }
                )
                onDismissEditingItem()
            },
            onDismiss = onDismissEditingItem
        )
    }

    if (showCheckoutDialog) {
        PurchaseCheckoutConfirmDialog(
            selectedSupplier = selectedSupplier,
            purchaseCart = purchaseCart,
            totalAmountCents = totalAmountCents,
            totalAmount = totalAmount,
            initialPaidStr = paidAmountStr,
            cyanPrimary = cyanPrimary,
            lang = lang,
            viewModel = viewModel,
            onSuccess = onCheckoutSuccess,
            onDismiss = onDismissCheckoutDialog
        )
    }

    if (showAddSupplierDialog) {
        PurchaseQuickAddSupplierDialog(
            lang = lang,
            cyanPrimary = cyanPrimary,
            onSave = { sup ->
                viewModel.saveSupplier(sup)
                onSelectSupplier(sup)
                onDismissAddSupplierDialog()
                Toast.makeText(
                    context,
                    Bilingual.tr(lang, "تمت إضافة المورد وتحديده", "Fournisseur ajouté", "Supplier added"),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = onDismissAddSupplierDialog
        )
    }

    selectedProductForPurchase?.let { prod ->
        val existingCartItem = purchaseByProductId[prod.id]
        CatalogPassToSaleDialog(
            product = prod,
            existingCartQuantity = existingCartItem?.quantity ?: 0.0,
            existingCartPrice = existingCartItem?.costPrice,
            canModifyPrice = true,
            currency = currency,
            lang = lang,
            isPurchaseMode = true,
            onDismiss = onDismissSelectedProduct,
            onConfirm = { qty, unitPrice, saveAsDefaultPrice ->
                val isCustomPrice = abs(unitPrice - prod.purchasePriceDouble) > 0.001
                val updatedProduct = if (saveAsDefaultPrice && isCustomPrice) {
                    viewModel.saveProduct(
                        id = prod.id,
                        barcode = prod.barcode,
                        name = prod.name,
                        description = prod.description,
                        categoryId = prod.categoryId,
                        categoryName = prod.categoryName,
                        unit = prod.unit,
                        purchasePrice = unitPrice,
                        sellPrice = prod.sellPriceDouble,
                        wholesalePrice = prod.wholesalePriceDouble,
                        specialPrice = prod.specialPriceDouble,
                        currentStock = prod.currentStock,
                        minStock = prod.minStock,
                        imageUri = prod.imageUri
                    )
                    prod.copy(purchasePrice = MoneyUtils.toCents(unitPrice))
                } else {
                    prod
                }

                onUpdateCart(
                    when {
                        abs(qty) < 0.000001 -> purchaseCart.filter { it.product.id != prod.id }
                        existingCartItem != null -> purchaseCart.map {
                            if (it.product.id == prod.id) {
                                it.copy(product = updatedProduct, quantity = qty, costPrice = unitPrice)
                            } else {
                                it
                            }
                        }
                        else -> purchaseCart + PurchaseCartItem(updatedProduct, qty, unitPrice)
                    }
                )
                onDismissSelectedProduct()
                Toast.makeText(
                    context,
                    when {
                        qty < 0.0 -> Bilingual.tr(
                            lang,
                            "تمت إضافة المنتج كمرتجع للمورد (-) في السلة",
                            "Produit ajouté en retour fournisseur (-)",
                            "Added as supplier return (-)"
                        )
                        lang == "AR" -> {
                            if (saveAsDefaultPrice && isCustomPrice) "تمت إضافة المنتج لسلة المشتريات وتحديث ثمن الشراء"
                            else "تمت إضافة المنتج لسلة المشتريات"
                        }
                        else -> {
                            if (saveAsDefaultPrice && isCustomPrice) "Ajouté et prix d'achat mis à jour"
                            else "Ajouté au panier d'achat"
                        }
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}
