package com.example.ui.screens.clients

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.CustomerSortCol
import com.example.ui.screens.CustomerSortOption
import com.example.util.ActiveSort
import com.example.util.SortDirection
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner

@Composable
internal fun ClientsTopControlsAndFilters(
    lang: String,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    searchVal: String,
    onSearchChange: (String) -> Unit,
    scanner: GmsBarcodeScanner,
    customerSortState: ActiveSort<CustomerSortCol>,
    onSortChange: (ActiveSort<CustomerSortCol>) -> Unit,
    isMultiSelectMode: Boolean,
    onToggleMultiSelect: () -> Unit,
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onBulkDeleteClick: () -> Unit,
    onPrintClick: () -> Unit,
    onAddClientClick: () -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clickable(onClick = onToggleSearch),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(28.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(48.dp).background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort", tint = Color(0xFFF9CF58), modifier = Modifier.size(24.dp))
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))
                ) {
                    CustomerSortOption.entries.forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(if (lang == "AR") opt.titleAr else opt.titleFr, color = Color.White) },
                            onClick = {
                                onSortChange(
                                    when (opt) {
                                        CustomerSortOption.NUMBER_ASC -> ActiveSort(CustomerSortCol.NUMBER, SortDirection.ASCENDING)
                                        CustomerSortOption.NUMBER_DESC -> ActiveSort(CustomerSortCol.NUMBER, SortDirection.DESCENDING)
                                        CustomerSortOption.NAME_ASC -> ActiveSort(CustomerSortCol.NAME, SortDirection.ASCENDING)
                                        CustomerSortOption.NAME_DESC -> ActiveSort(CustomerSortCol.NAME, SortDirection.DESCENDING)
                                        CustomerSortOption.BALANCE_ASC -> ActiveSort(CustomerSortCol.BALANCE, SortDirection.ASCENDING)
                                        CustomerSortOption.BALANCE_DESC -> ActiveSort(CustomerSortCol.BALANCE, SortDirection.DESCENDING)
                                        CustomerSortOption.BARCODE_ASC -> ActiveSort(CustomerSortCol.BARCODE, SortDirection.ASCENDING)
                                    }
                                )
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleMultiSelect,
                modifier = Modifier
                    .size(48.dp)
                    .background(if (isMultiSelectMode) Color(0xFF2563EB) else Color(0xFF1E293B), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Checklist,
                    contentDescription = "Multi Select",
                    tint = if (isMultiSelectMode) Color.White else Color(0xFF34D399),
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    .clickable(onClick = onPrintClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Print, contentDescription = "Print List", tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
            }

            Button(
                onClick = onAddClientClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp).testTag("add_client_button"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (lang == "AR") "إضافة عميل" else "Ajouter Client", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    AnimatedVisibility(
        visible = isSearchOpen,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchVal,
                onValueChange = onSearchChange,
                placeholder = { Text(if (lang == "AR") "ابحث بالاسم أو الهاتف أو الباركود..." else "Recherche...", color = Color.Gray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, tint = Color.Gray, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchVal.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, tint = Color.Gray, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(
                            onClick = { scanner.startScan().addOnSuccessListener { b -> b.rawValue?.let(onSearchChange) } },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, tint = Color(0xFF38BDF8), contentDescription = "Scan", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.weight(1f).height(50.dp).testTag("clients_search_input")
            )
        }
    }

    if (isMultiSelectMode) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF3B82F6)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (lang == "AR") "المحدد: $selectedCount" else "Sélectionnés : $selectedCount",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onToggleSelectAll) {
                        Text(
                            text = if (allSelected) (if (lang == "AR") "إلغاء الكل" else "Désélectionner") else (if (lang == "AR") "تحديد الكل" else "Tout sélectionner"),
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onBulkDeleteClick,
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), disabledContainerColor = Color(0xFF475569)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "AR") "حذف ($selectedCount)" else "Supprimer ($selectedCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onToggleMultiSelect, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
