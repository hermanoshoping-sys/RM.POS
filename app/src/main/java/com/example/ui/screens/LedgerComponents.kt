package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LedgerScreenTopHeader(
    title: String,
    onPrintList: () -> Unit,
    leadingAction: (@Composable BoxScope.() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        leadingAction?.invoke(this)

        Surface(
            color = Color.Black,
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(2.dp, Color.White),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 5.dp)
            )
        }

        IconButton(
            onClick = onPrintList,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Print,
                contentDescription = "Print Invoices List",
                tint = Color(0xFFF97316),
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
fun LedgerKpiBlock(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    backgroundColor: Color
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = backgroundColor,
        border = BorderStroke(1.dp, Color.Black),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = Color.Black, thickness = 1.dp)
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LedgerFilterButton(
    text: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(2.dp, Color.White) else BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun LedgerTableRow(
    col1: String,
    col2: String,
    col3: String,
    col4: String,
    isHeader: Boolean = false,
    isDebt: Boolean = false,
    isVoided: Boolean = false,
    activeSortCol: String? = null,
    sortArrow: String = "",
    onSortCol1: (() -> Unit)? = null,
    onSortCol2: (() -> Unit)? = null,
    onSortCol3: (() -> Unit)? = null,
    onSortCol4: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val bgColor = if (isHeader) Color(0xFFF9CF58) else if (isDebt) Color(0xFFFFF1F2) else Color.White
    val textColor = if (isHeader) Color.Black else if (isDebt) Color(0xFFDC2626) else Color.Black
    val fontWeight = if (isHeader) FontWeight.Bold else if (isDebt) FontWeight.Bold else FontWeight.Medium
    val fontSize = if (isHeader) 12.sp else 11.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(bgColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
        LedgerTableColumnCell(
            text = if (isHeader && activeSortCol == "NUMBER") "$col1$sortArrow" else col1,
            isSortedHeader = isHeader && activeSortCol == "NUMBER",
            isHeader = isHeader,
            textColor = textColor,
            fontWeight = fontWeight,
            fontSize = fontSize,
            alignment = Alignment.Center,
            textAlign = TextAlign.Center,
            hasRightDivider = true,
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .then(if (isHeader && onSortCol1 != null) Modifier.clickable(onClick = onSortCol1) else Modifier)
        )

        LedgerTableColumnCell(
            text = if (isHeader && activeSortCol == "DATE") "$col2$sortArrow" else col2,
            isSortedHeader = isHeader && activeSortCol == "DATE",
            isHeader = isHeader,
            textColor = textColor,
            fontWeight = fontWeight,
            fontSize = fontSize,
            alignment = Alignment.Center,
            textAlign = TextAlign.Center,
            hasRightDivider = true,
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .then(if (isHeader && onSortCol2 != null) Modifier.clickable(onClick = onSortCol2) else Modifier)
        )

        LedgerTableColumnCell(
            text = if (isHeader && activeSortCol == "PARTY") "$col3$sortArrow" else col3,
            isSortedHeader = isHeader && activeSortCol == "PARTY",
            isHeader = isHeader,
            textColor = textColor,
            fontWeight = fontWeight,
            fontSize = fontSize,
            alignment = Alignment.CenterStart,
            textAlign = TextAlign.Start,
            hasRightDivider = true,
            modifier = Modifier
                .weight(1.7f)
                .fillMaxHeight()
                .then(if (isHeader && onSortCol3 != null) Modifier.clickable(onClick = onSortCol3) else Modifier)
        )

        LedgerTableColumnCell(
            text = if (isHeader && activeSortCol == "TOTAL") "$col4$sortArrow" else col4,
            isSortedHeader = isHeader && activeSortCol == "TOTAL",
            isHeader = isHeader,
            textColor = textColor,
            fontWeight = fontWeight,
            fontSize = fontSize,
            alignment = Alignment.Center,
            textAlign = TextAlign.Center,
            hasRightDivider = false,
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
                .then(if (isHeader && onSortCol4 != null) Modifier.clickable(onClick = onSortCol4) else Modifier)
        )
    }
}

@Composable
private fun LedgerTableColumnCell(
    text: String,
    isSortedHeader: Boolean,
    isHeader: Boolean,
    textColor: Color,
    fontWeight: FontWeight,
    fontSize: androidx.compose.ui.unit.TextUnit,
    alignment: Alignment,
    textAlign: TextAlign,
    hasRightDivider: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .then(
                if (hasRightDivider) {
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
            .padding(horizontal = if (alignment == Alignment.CenterStart) 5.dp else 3.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = if (isSortedHeader) FontWeight.Black else fontWeight,
            color = if (isSortedHeader) Color(0xFF1D4ED8) else textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
}
