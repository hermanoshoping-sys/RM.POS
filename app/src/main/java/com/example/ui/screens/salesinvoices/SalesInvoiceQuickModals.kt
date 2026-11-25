package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CustomerEntity
import com.example.data.SaleEntity
import com.example.domain.PosOperationResult
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
internal fun EditSaleInvoiceNumberDialog(
    target: SaleEntity,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var invNumText by remember { mutableStateOf(target.invoiceNumber) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lang == "AR") "تعديل رقم الفاتورة" else "Modifier le numéro de facture",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    if (lang == "AR") "أدخل رقم الفاتورة الجديد:" else "Entrez le nouveau numéro de facture:",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = invNumText,
                    onValueChange = {
                        invNumText = it
                        errorMsg = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    isError = errorMsg.isNotEmpty()
                )
                if (errorMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = invNumText.trim()
                    if (trimmed.isEmpty()) {
                        errorMsg = if (lang == "AR") "رقم الفاتورة لا يمكن أن يكون فارغاً" else "Le numéro ne peut pas être vide"
                        return@Button
                    }
                    viewModel.updateSaleInvoiceNumber(target.id, trimmed) { success ->
                        if (success) {
                            onDismiss()
                            Toast.makeText(
                                context,
                                if (lang == "AR") "تم تعديل رقم الفاتورة بنجاح" else "Numéro de facture modifié avec succès",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            errorMsg = if (lang == "AR") "رقم الفاتورة مستخدم بالفعل في فاتورة أخرى!" else "Ce numéro est déjà utilisé!"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text(if (lang == "AR") "حفظ" else "Enregistrer", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "AR") "إلغاء" else "Annuler")
            }
        }
    )
}

@Composable
internal fun SwitchSaleCustomerDialog(
    target: SaleEntity,
    customers: List<CustomerEntity>,
    viewModel: PosViewModel,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    var selectedCustId by remember { mutableStateOf(target.customerId) }
    var selectedCustName by remember { mutableStateOf(target.customerName) }
    var customNameInput by remember { mutableStateOf("") }
    var custSearch by remember { mutableStateOf("") }

    val filteredCusts = remember(customers, custSearch) {
        if (custSearch.isBlank()) customers
        else customers.filter { it.name.contains(custSearch, ignoreCase = true) || it.phone.contains(custSearch) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (lang == "AR") "تبديل اسم الزبون للفاتورة (#${target.invoiceNumber})" else "Changer le client de la facture",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Text(
                    text = "${if (lang == "AR") "الزبون الحالي:" else "Client actuel:"} ${if (target.customerName.isNotBlank()) target.customerName else (if (lang == "AR") "زبون عادي" else "Client de passage")}",
                    fontSize = 13.sp,
                    color = Color(0xFF0284C7),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = custSearch,
                    onValueChange = { custSearch = it },
                    placeholder = { Text(if (lang == "AR") "بحث في قائمة الزبائن..." else "Rechercher client...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    item(key = "walk_in_customer", contentType = "walk_in_customer") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    selectedCustId = 0L
                                    selectedCustName = if (lang == "AR") "زبون عادي" else "Client de passage"
                                    customNameInput = ""
                                },
                            color = if (selectedCustId == 0L && customNameInput.isBlank()) Color(0xFFE0F2FE) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (selectedCustId == 0L && customNameInput.isBlank()) Color(0xFF0284C7) else Color.LightGray)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (lang == "AR") "زبون عادي (بدون حساب)" else "Client de passage",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    items(
                        items = filteredCusts,
                        key = { it.id },
                        contentType = { "customer_option" }
                    ) { c ->
                        val isSelected = selectedCustId == c.id && customNameInput.isBlank()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    selectedCustId = c.id
                                    selectedCustName = c.name
                                    customNameInput = ""
                                },
                            color = if (isSelected) Color(0xFFE0F2FE) else Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0284C7) else Color.LightGray)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(c.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    if (c.phone.isNotBlank()) {
                                        Text(c.phone, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                if (c.balance > 0L) {
                                    Text(
                                        MoneyUtils.format(c.balanceDouble, currency),
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customNameInput,
                    onValueChange = {
                        customNameInput = it
                        if (it.isNotBlank()) {
                            selectedCustId = 0L
                            selectedCustName = it
                        }
                    },
                    placeholder = { Text(if (lang == "AR") "أو أدخل اسم زبون آخر يدوياً..." else "Ou nom personnalisé...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (customNameInput.isNotBlank()) customNameInput.trim() else selectedCustName
                    viewModel.updateSaleCustomer(target.id, selectedCustId, finalName) { res ->
                        when (res) {
                            is PosOperationResult.Success -> {
                                onDismiss()
                                Toast.makeText(
                                    context,
                                    res.message.ifBlank { if (lang == "AR") "تم تبديل اسم الزبون بنجاح" else "Client mis à jour avec succès" },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            is PosOperationResult.Failure -> {
                                Toast.makeText(context, res.error.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text(if (lang == "AR") "تأكيد التبديل" else "Confirmer", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "AR") "إلغاء" else "Annuler")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SalesLedgerTableRow(
    col1: String,
    col2: String,
    col3: String,
    col4: String,
    col5: String,
    isHeader: Boolean = false,
    isDebt: Boolean = false,
    isVoided: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    rowTestTag: String? = null,
    onSortCol1: (() -> Unit)? = null,
    onSortCol2: (() -> Unit)? = null,
    onSortCol3: (() -> Unit)? = null,
    onSortCol4: (() -> Unit)? = null,
    onSortCol5: (() -> Unit)? = null,
    activeSortCol: SalesSortCol? = null,
    sortArrow: String = ""
) {
    val bgColor = when {
        isHeader -> Color(0xFFF9CF58)
        selectionMode && isSelected -> Color(0xFFDBEAFE)
        isDebt -> Color(0xFFFFF1F2)
        else -> Color.White
    }
    val textColor = if (isHeader) Color.Black else if (isDebt) Color(0xFFDC2626) else Color.Black
    val fontWeight = if (isHeader || isDebt || (selectionMode && isSelected)) FontWeight.Bold else FontWeight.Medium
    val fontSize = if (isHeader) 11.5.sp else 10.5.sp

    val rowClickModifier = when {
        selectionMode && !isHeader && onToggleSelect != null -> Modifier.clickable(onClick = onToggleSelect)
        !isHeader && (onClick != null || onLongClick != null) -> Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick
        )
        else -> Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (selectionMode && !isHeader) 38.dp else 32.dp)
            .background(bgColor)
            .then(if (rowTestTag != null) Modifier.testTag(rowTestTag) else Modifier)
            .then(rowClickModifier)
            .drawBehind {
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        drawLine(
                            color = Color.Black,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .then(if (onToggleSelect != null) Modifier.clickable(onClick = onToggleSelect) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (onToggleSelect != null) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) Color(0xFF0284C7) else Color(0xFF475569),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        SalesLedgerCell(col1, 1.05f, isHeader && activeSortCol == SalesSortCol.NUMBER, sortArrow, fontSize, fontWeight, textColor, Alignment.Center, TextAlign.Center, true, onSortCol1)
        SalesLedgerCell(col2, 0.95f, isHeader && activeSortCol == SalesSortCol.DATE, sortArrow, fontSize, fontWeight, textColor, Alignment.Center, TextAlign.Center, true, onSortCol2)
        SalesLedgerCell(col3, 1.35f, isHeader && activeSortCol == SalesSortCol.CLIENT, sortArrow, fontSize, fontWeight, textColor, Alignment.CenterStart, TextAlign.Start, true, onSortCol3)
        SalesLedgerCell(
            text = col4,
            weight = 1.1f,
            isSorted = isHeader && activeSortCol == SalesSortCol.SELLER,
            sortArrow = sortArrow,
            fontSize = fontSize,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.SemiBold,
            textColor = if (isHeader) textColor else Color(0xFF0369A1),
            alignment = Alignment.Center,
            textAlign = TextAlign.Center,
            drawRightBorder = true,
            onSort = onSortCol4
        )
        SalesLedgerCell(col5, 1.05f, isHeader && activeSortCol == SalesSortCol.TOTAL, sortArrow, fontSize, fontWeight, textColor, Alignment.Center, TextAlign.Center, false, onSortCol5)
    }
}

@Composable
private fun RowScope.SalesLedgerCell(
    text: String,
    weight: Float,
    isSorted: Boolean,
    sortArrow: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    textColor: Color,
    alignment: Alignment,
    textAlign: TextAlign,
    drawRightBorder: Boolean,
    onSort: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .then(
                if (drawRightBorder) {
                    Modifier.drawBehind {
                        drawLine(
                            color = Color.Black,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                } else Modifier
            )
            .then(if (onSort != null) Modifier.clickable(onClick = onSort) else Modifier)
            .padding(horizontal = 2.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = if (isSorted) "$text $sortArrow" else text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
}
