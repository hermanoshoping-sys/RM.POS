package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.POSSuccess
import com.example.util.MoneyUtils
import java.util.Locale

@Composable
internal fun ReportsKpiSummaryRow(
    lang: String,
    currency: String,
    selectedPeriodTab: Int,
    salesToday: Double,
    profitToday: Double,
    expensesToday: Double,
    netProfitToday: Double,
    invoicesToday: Int,
    avgTicket: Double,
    marginPercent: Double,
    expensesCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "AR") "مبيعات اليوم" else "Ventes du jour",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(color = Color(0xFF3B82F6).copy(alpha = 0.15f), shape = CircleShape) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.padding(6.dp).size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(MoneyUtils.format(salesToday, currency), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF334155), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = if (lang == "AR") "$invoicesToday فاتورة" else "$invoicesToday factures",
                            fontSize = 11.sp,
                            color = Color(0xFF93C5FD),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = (if (lang == "AR") "متوسط: " else "Moy: ") + MoneyUtils.format(avgTicket, currency),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val periodTxt = when (selectedPeriodTab) {
                        1 -> if (lang == "AR") "7 أيام" else "7 jours"
                        2 -> if (lang == "AR") "هذا الشهر" else "Ce mois"
                        else -> if (lang == "AR") "اليوم" else "du jour"
                    }
                    Text(
                        text = if (lang == "AR") "أرباح $periodTxt (حقيقية)" else "Bénéfice réel ($periodTxt)",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(color = POSSuccess.copy(alpha = 0.15f), shape = CircleShape) {
                        Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = POSSuccess, modifier = Modifier.padding(6.dp).size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(MoneyUtils.format(profitToday, currency), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = POSSuccess)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(color = POSSuccess.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = (if (lang == "AR") "هامش: " else "Marge: ") + String.format(Locale.US, "%.1f%%", marginPercent),
                        fontSize = 11.sp,
                        color = POSSuccess,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (lang == "AR") "مصاريف اليوم" else "Dépenses du jour", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Surface(color = Color(0xFFEF4444).copy(alpha = 0.15f), shape = CircleShape) {
                        Icon(Icons.Filled.MoneyOff, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.padding(6.dp).size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(MoneyUtils.format(expensesToday, currency), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color(0xFFEF4444))
                Spacer(modifier = Modifier.height(6.dp))
                Surface(color = Color(0xFFEF4444).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = if (lang == "AR") "$expensesCount مصاريف" else "$expensesCount dépenses",
                        fontSize = 11.sp,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (netProfitToday >= 0) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFF7F1D1D).copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, if (netProfitToday >= 0) POSSuccess.copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "AR") "صافي الربح اليوم" else "Bénéfice Net",
                        color = if (netProfitToday >= 0) Color(0xFFA7F3D0) else Color(0xFFFECACA),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = if (netProfitToday >= 0) POSSuccess.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = CircleShape
                    ) {
                        Icon(
                            if (netProfitToday >= 0) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (netProfitToday >= 0) POSSuccess else Color(0xFFEF4444),
                            modifier = Modifier.padding(6.dp).size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = MoneyUtils.format(netProfitToday, currency),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = if (netProfitToday >= 0) POSSuccess else Color(0xFFEF4444)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(if (lang == "AR") "(الأرباح - المصاريف)" else "(Bénéfice - Dépenses)", fontSize = 11.sp, color = Color.LightGray)
            }
        }
    }
}
