package com.example.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SortDirection {
    ASCENDING,
    DESCENDING;

    fun toggle(): SortDirection = if (this == ASCENDING) DESCENDING else ASCENDING
    val symbol: String get() = if (this == ASCENDING) " ▲" else " ▼"
}

data class ActiveSort<T>(
    val column: T,
    val direction: SortDirection = SortDirection.ASCENDING
) {
    fun toggleOrSwitch(targetColumn: T, defaultDirection: SortDirection = SortDirection.ASCENDING): ActiveSort<T> {
        return if (column == targetColumn) {
            copy(direction = direction.toggle())
        } else {
            ActiveSort(targetColumn, defaultDirection)
        }
    }

    fun isCurrent(targetColumn: T): Boolean = column == targetColumn

    fun indicator(targetColumn: T): String = if (isCurrent(targetColumn)) direction.symbol else ""
}

object SortingComparators {
    /**
     * Alphabetical sorting for names (Arabic and Latin).
     */
    fun compareNames(n1: String, n2: String): Int {
        return n1.trim().compareTo(n2.trim(), ignoreCase = true)
    }

    /**
     * Barcode comparison that strictly preserves leading zeros.
     * Standard alphanumeric comparison ensures "001" < "01" < "1" and "ABC-1" < "ABC-2".
     */
    fun compareBarcodes(b1: String, b2: String): Int {
        val s1 = b1.trim()
        val s2 = b2.trim()
        // If both strings consist of numbers with prefix, handle intelligently
        return s1.compareTo(s2, ignoreCase = true)
    }

    /**
     * Numeric comparison for integers and sequential numbers.
     */
    fun compareLongs(l1: Long, l2: Long): Int {
        return l1.compareTo(l2)
    }

    /**
     * Numeric comparison for prices, totals, balances and quantities.
     */
    fun compareDoubles(d1: Double, d2: Double): Int {
        return d1.compareTo(d2)
    }

    /**
     * Chronological comparison for timestamps.
     */
    fun compareTimestamps(t1: Long, t2: Long): Int {
        return t1.compareTo(t2)
    }
}

/**
 * Clickable header text component displaying column title with dynamic sort arrow indicator.
 */
@Composable
fun <T> SortableHeaderTitle(
    text: String,
    columnKey: T,
    activeSort: ActiveSort<T>?,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    fontSize: TextUnit = 11.5.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = 1,
    onSortChange: (ActiveSort<T>) -> Unit
) {
    val isCurrent = activeSort?.isCurrent(columnKey) == true
    val arrow = if (isCurrent) activeSort?.direction?.symbol ?: "" else ""

    Row(
        modifier = modifier
            .clickable {
                val next = activeSort?.toggleOrSwitch(columnKey) ?: ActiveSort(columnKey, SortDirection.ASCENDING)
                onSortChange(next)
            }
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$text$arrow",
            color = if (isCurrent) Color(0xFF1D4ED8).takeIf { color != Color.White } ?: Color(0xFFFDE047) else color,
            fontSize = fontSize,
            fontWeight = if (isCurrent) FontWeight.Black else fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
