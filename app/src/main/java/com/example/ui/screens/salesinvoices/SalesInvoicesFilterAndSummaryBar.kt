package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.util.MoneyUtils

@Composable
internal fun SalesInvoicesKpiRow(
    lang: String,
    currency: String,
    selectedSeller: String,
    totalAmount: Double,
    totalPaid: Double,
    totalDebt: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .height(56.dp)
    ) {
        LedgerKpiBlock(
            modifier = Modifier.weight(1f),
            title = if (selectedSeller == "ALL") (if (lang == "AR") "مجموع المبيعات" else "Total Ventes") else (if (lang == "AR") "مبيعات $selectedSeller" else "Ventes $selectedSeller"),
            value = MoneyUtils.format(totalAmount, currency),
            backgroundColor = Color(0xFF00A0E9)
        )
        LedgerKpiBlock(
            modifier = Modifier.weight(1f),
            title = if (selectedSeller == "ALL") (if (lang == "AR") "الفواتير المسددة" else "Factures Réglées") else (if (lang == "AR") "المسدد لـ $selectedSeller" else "Réglé ($selectedSeller)"),
            value = MoneyUtils.format(totalPaid, currency),
            backgroundColor = Color(0xFF8CC63F)
        )
        LedgerKpiBlock(
            modifier = Modifier.weight(1f),
            title = if (selectedSeller == "ALL") (if (lang == "AR") "الفواتير المدينة" else "Factures à Crédit") else (if (lang == "AR") "ديون $selectedSeller" else "Dettes ($selectedSeller)"),
            value = MoneyUtils.format(totalDebt, currency),
            backgroundColor = Color(0xFFE50012)
        )
    }
}

@Composable
internal fun SalesInvoicesFiltersSection(
    lang: String,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearAndCloseSearch: () -> Unit,
    filterStatus: String,
    onFilterStatusChange: (String) -> Unit,
    selectedSeller: String,
    onSelectedSellerChange: (String) -> Unit,
    allSellers: List<String>,
    sellerCounts: Map<String, Int>,
    totalAllSalesCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleSearch,
            modifier = Modifier.size(48.dp).testTag("sales_search_toggle_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = if (isSearchOpen || searchQuery.isNotEmpty()) Color(0xFF38BDF8) else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerFilterButton(stringResource(R.string.sales_all), Color(0xFF5A6B82), filterStatus == "ALL") {
                onFilterStatusChange("ALL")
            }
            LedgerFilterButton(stringResource(R.string.sales_paid), Color(0xFF7CB342), filterStatus == "PAID") {
                onFilterStatusChange("PAID")
            }
            LedgerFilterButton(stringResource(R.string.sales_debt), Color(0xFFE53935), filterStatus == "DEBT") {
                onFilterStatusChange("DEBT")
            }
        }
    }

    if (isSearchOpen || searchQuery.isNotEmpty()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.sales_search_placeholder), fontSize = 12.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                IconButton(onClick = onClearAndCloseSearch) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .height(48.dp)
                .testTag("sales_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true
        )
    }

    Spacer(modifier = Modifier.height(2.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.padding(end = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                Text(stringResource(R.string.sales_seller_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedSeller == "ALL",
                onClick = { onSelectedSellerChange("ALL") },
                label = { Text("${stringResource(R.string.sales_all_sellers)} ($totalAllSalesCount)", fontSize = 10.5.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF0284C7), selectedLabelColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            )
            allSellers.forEach { seller ->
                val count = sellerCounts[seller] ?: 0
                FilterChip(
                    selected = selectedSeller.equals(seller, true),
                    onClick = { onSelectedSellerChange(if (selectedSeller.equals(seller, true)) "ALL" else seller) },
                    label = { Text("$seller ($count)", fontSize = 10.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF10B981), selectedLabelColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(34.dp)
                )
            }
        }
    }
}
