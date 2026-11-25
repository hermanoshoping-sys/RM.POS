package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ExpenseEntity
import com.example.util.ActiveSort
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun ColumnScope.ExpensesTableSection(
    currentLanguage: String,
    sortedExpenses: List<ExpenseEntity>,
    expenseSortState: ActiveSort<ExpenseSortCol>,
    onSortChange: (ExpenseSortCol) -> Unit,
    onSelectExpense: (ExpenseEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 14.dp)
            .background(Color.White)
            .border(BorderStroke(1.5.dp, Color.Black))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val minTableWidth = 440.dp
            val tableWidth = if (maxWidth > minTableWidth) maxWidth else minTableWidth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier
                        .width(tableWidth)
                        .fillMaxHeight()
                ) {
                    val amountTitle = if (currentLanguage == "AR") "ثمن" else "Montant"
                    val titleTitle = if (currentLanguage == "AR") "نوع المصروف" else "Type de Dépense"
                    val dateTitle = if (currentLanguage == "AR") "التاريخ" else "Date"

                    val dispAmount = if (expenseSortState.column == ExpenseSortCol.AMOUNT) "$amountTitle ${expenseSortState.direction.symbol}" else amountTitle
                    val dispTitle = if (expenseSortState.column == ExpenseSortCol.TITLE) "$titleTitle ${expenseSortState.direction.symbol}" else titleTitle
                    val dispDate = if (expenseSortState.column == ExpenseSortCol.DATE) "$dateTitle ${expenseSortState.direction.symbol}" else dateTitle

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(Color(0xFFFF0000)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpenseTableCell(
                            text = if (currentLanguage == "AR") "طباعة" else "Imprimer",
                            modifier = Modifier.width(62.dp),
                            isHeader = true
                        )
                        ExpenseTableCell(
                            text = dispAmount,
                            modifier = Modifier.width(90.dp),
                            isHeader = true,
                            onClick = { onSortChange(ExpenseSortCol.AMOUNT) }
                        )
                        ExpenseTableCell(
                            text = dispTitle,
                            modifier = Modifier.weight(1f),
                            isHeader = true,
                            onClick = { onSortChange(ExpenseSortCol.TITLE) }
                        )
                        ExpenseTableCell(
                            text = dispDate,
                            modifier = Modifier.width(95.dp),
                            isHeader = true,
                            onClick = { onSortChange(ExpenseSortCol.DATE) }
                        )
                    }

                    HorizontalDivider(color = Color.Black, thickness = 1.dp)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = sortedExpenses,
                            key = { it.id },
                            contentType = { "expense_row" }
                        ) { exp ->
                            val dateFormatted = dateFormat.format(Date(exp.timestamp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(Color.White)
                                    .clickable { onSelectExpense(exp) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(62.dp)
                                        .fillMaxHeight()
                                        .border(BorderStroke(0.5.dp, Color.Black)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Print,
                                        contentDescription = "Print",
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                ExpenseTableCell(
                                    text = String.format(Locale.US, "%.2f", exp.amountDouble),
                                    modifier = Modifier.width(90.dp),
                                    isBold = true
                                )
                                ExpenseTableCell(
                                    text = exp.title,
                                    modifier = Modifier.weight(1f),
                                    align = TextAlign.Start
                                )
                                ExpenseTableCell(
                                    text = dateFormatted,
                                    modifier = Modifier.width(95.dp)
                                )
                            }
                            HorizontalDivider(color = Color.Black, thickness = 0.5.dp)
                        }

                        val emptyRowsCount = (14 - sortedExpenses.size).coerceAtLeast(0)
                        items(emptyRowsCount) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(Color.White)
                            ) {
                                Box(modifier = Modifier.width(62.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                Box(modifier = Modifier.width(90.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                                Box(modifier = Modifier.width(95.dp).fillMaxHeight().border(BorderStroke(0.5.dp, Color.Black)))
                            }
                            HorizontalDivider(color = Color.Black, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseTableCell(
    text: String,
    modifier: Modifier = Modifier,
    isHeader: Boolean = false,
    isBold: Boolean = false,
    align: TextAlign = TextAlign.Center,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .border(BorderStroke(0.5.dp, Color.Black))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp),
        contentAlignment = when (align) {
            TextAlign.Start -> Alignment.CenterStart
            TextAlign.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = if (isHeader) 13.sp else 12.sp,
            fontWeight = if (isHeader || isBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
