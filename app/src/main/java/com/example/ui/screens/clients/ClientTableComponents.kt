package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.POSPrimary
import com.example.ui.theme.POSSecondary

@Composable
fun FilterChipBadge(
    text: String,
    count: Int,
    isSelected: Boolean,
    badgeColor: Color,
    onClick: () -> Unit
) {
    val animatedColor by animateColorAsState(targetValue = if (isSelected) POSPrimary else Color(0xFF1E293B))
    val animatedBadgeColor by animateColorAsState(targetValue = if (isSelected) POSSecondary else badgeColor)

    Surface(
        color = animatedColor,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (isSelected) POSPrimary else Color(0xFF334155)),
        modifier = Modifier
            .clickable { onClick() }
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = animatedBadgeColor,
                shape = CircleShape
            ) {
                AnimatedContent(targetState = count, label = "badge_count") { targetCount ->
                    Text(
                        text = targetCount.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

enum class CustomerSortOption(val titleAr: String, val titleFr: String) {
    NUMBER_ASC("الرقم (تصاعدي)", "Numéro (Croissant)"),
    NUMBER_DESC("الرقم (تنازلي)", "Numéro (Décroissant)"),
    NAME_ASC("الاسم (أ - ي)", "Nom (A - Z)"),
    NAME_DESC("الاسم (ي - أ)", "Nom (Z - A)"),
    BALANCE_DESC("الرصيد / الدين (الأعلى)", "Solde / Dette (Plus grand)"),
    BALANCE_ASC("الرصيد / الدين (الأقل)", "Solde / Dette (Plus petit)"),
    BARCODE_ASC("الكود بار", "Code-barres")
}

enum class CustomerSortCol {
    NUMBER,
    NAME,
    BALANCE,
    BARCODE
}

@Composable
fun PartnerLedgerTableRow(
    colPos: String = "",
    colStatement: String = "",
    colPayDebt: String = "",
    colName: String = "",
    hasGeoLocation: Boolean = false,
    isHeader: Boolean = false,
    statementHeaderColor: Color = Color(0xFFF9CF58),
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onPosClick: (() -> Unit)? = null,
    onStatementClick: (() -> Unit)? = null,
    onPayDebtClick: (() -> Unit)? = null,
    onRowClick: (() -> Unit)? = null,
    onNameHeaderClick: (() -> Unit)? = null,
    onNumberHeaderClick: (() -> Unit)? = null
) {
    val rowHeight = if (isHeader) 34.dp else 30.dp
    val gridLineColor = Color.Black

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (isMultiSelectMode && isSelected && !isHeader) Color(0xFFEFF6FF) else Color.White)
            .then(if (onRowClick != null) Modifier.clickable(onClick = onRowClick) else Modifier)
            .drawBehind {
                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .background(if (isHeader) Color(0xFFE2E8F0) else if (isSelected) Color(0xFFDBEAFE) else Color.White)
                    .then(if (onToggleSelect != null) Modifier.clickable { onToggleSelect() } else Modifier)
                    .drawBehind {
                        drawLine(
                            color = gridLineColor,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = if (isHeader) "Select All" else "Select",
                    tint = if (isSelected) Color(0xFF2563EB) else if (isHeader) Color(0xFF64748B) else Color(0xFF94A3B8),
                    modifier = Modifier.size(if (isHeader) 18.dp else 17.dp)
                )
            }
        }

        // Col 1: POS
        val posBg = if (isHeader) Color(0xFFF9CF58) else Color.White
        val posModifier = when {
            isHeader && onNumberHeaderClick != null -> Modifier.clickable(onClick = onNumberHeaderClick)
            onPosClick != null -> Modifier.clickable(onClick = onPosClick)
            else -> Modifier
        }
        Box(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .background(posBg)
                .then(posModifier)
                .drawBehind {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isHeader) {
                Text(
                    text = colPos,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
            } else if (colPos.isNotBlank()) {
                Text(
                    text = colPos,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB45309),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Col 2: Statement
        val statementBg = if (isHeader) statementHeaderColor else Color.White
        Box(
            modifier = Modifier
                .weight(1.4f)
                .fillMaxHeight()
                .background(statementBg)
                .then(if (onStatementClick != null) Modifier.clickable(onClick = onStatementClick) else Modifier)
                .drawBehind {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isHeader) {
                Text(
                    text = colStatement,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            } else if (colStatement.isNotBlank()) {
                Text(
                    text = colStatement,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D4ED8),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Col 3: Pay Debt
        val payBg = if (isHeader) Color(0xFFE50012) else Color.White
        Box(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .background(payBg)
                .then(if (onPayDebtClick != null) Modifier.clickable(onClick = onPayDebtClick) else Modifier)
                .drawBehind {
                    drawLine(
                        color = gridLineColor,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isHeader) {
                Text(
                    text = colPayDebt,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            } else if (colPayDebt.isNotBlank()) {
                Text(
                    text = colPayDebt,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Col 4: Partner Name
        val nameModifier = if (isHeader && onNameHeaderClick != null) Modifier.clickable(onClick = onNameHeaderClick) else Modifier
        Box(
            modifier = Modifier
                .weight(2.6f)
                .fillMaxHeight()
                .background(Color.White)
                .then(nameModifier)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (hasGeoLocation && !isHeader) {
                    Text("📍", fontSize = 10.sp, modifier = Modifier.padding(end = 2.dp))
                }
                Text(
                    text = colName,
                    fontSize = 11.sp,
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
