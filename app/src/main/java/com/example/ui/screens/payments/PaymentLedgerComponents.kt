package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PaymentSortCol { NUMBER, DATE, PARTY, AMOUNT }

@Composable
fun PaymentLedgerTableRow(
    indexNumber: String,
    dateString: String,
    partyName: String,
    amountString: String,
    hasActions: Boolean,
    onPrint: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .border(0.5.dp, Color.Black)
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(75.dp)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasActions) {
                Icon(
                    imageVector = Icons.Filled.Print,
                    contentDescription = "Print",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(15.dp).clickable(onClick = onPrint)
                )
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(15.dp).clickable(onClick = onEdit)
                )
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(15.dp).clickable(onClick = onDelete)
                )
            }
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))

        Text(
            text = amountString,
            color = Color.Black,
            fontWeight = if (amountString.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(80.dp).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically)
        )
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))

        Text(
            text = partyName,
            color = Color.Black,
            fontSize = 11.sp,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp).wrapContentHeight(Alignment.CenterVertically)
        )
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))

        Text(
            text = dateString,
            color = Color.Black,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(90.dp).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically)
        )
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.Black))

        Text(
            text = indexNumber,
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(42.dp).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically)
        )
    }
}

@Composable
fun PaymentFilterChipButton(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) activeColor else Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (isSelected) activeColor else Color(0xFF475569)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .height(34.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp
            )
        }
    }
}
