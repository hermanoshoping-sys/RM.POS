package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.ExpenseEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ExpenseSortCol {
    DATE, TITLE, AMOUNT
}

@Composable
internal fun ExpensesTopHeaderBar(
    currentLanguage: String,
    username: String?,
    onToggleLanguage: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF3872E0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "i",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Column {
                Text(
                    text = if (currentLanguage == "AR") ",مرحباً بك" else "Bienvenue,",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Text(
                    text = username?.let { "$it (مدير)" } ?: "admin (مدير)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF1E293B), CircleShape)
                    .border(BorderStroke(1.dp, Color(0xFF334155)), CircleShape)
                    .clickable(onClick = onToggleLanguage),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentLanguage == "AR") "FR" else "AR",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF1E293B), CircleShape)
                    .border(BorderStroke(1.dp, Color(0xFF334155)), CircleShape)
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Exit",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun ExpensesActionAndSummarySection(
    currentLanguage: String,
    currency: String,
    filteredExpenses: List<ExpenseEntity>,
    totalExpensesAmount: Double,
    showSearchBar: Boolean,
    searchQuery: String,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFFC05621), RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleSearch),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .height(42.dp)
                    .clickable {
                        if (filteredExpenses.isNotEmpty()) {
                            shareDocument(
                                context = context,
                                docTitle = if (currentLanguage == "AR") "سجل المصاريف" else "Journal_Depenses",
                                subtitle = "سجل المصاريف - ${dateFormat.format(Date())}",
                                headers = if (currentLanguage == "AR") {
                                    listOf("التاريخ", "رقم السند", "نوع وبيان المصروف", "الفئة", "المبلغ")
                                } else {
                                    listOf("Date", "N° Pièce", "Désignation", "Catégorie", "Montant")
                                },
                                rows = filteredExpenses.map {
                                    listOf(
                                        dateFormat.format(Date(it.timestamp)),
                                        "DEP-${it.id}",
                                        it.title,
                                        it.category,
                                        String.format(Locale.US, "%.2f %s", it.amountDouble, currency)
                                    )
                                },
                                summaryLines = listOf(
                                    (if (currentLanguage == "AR") "إجمالي عدد السندات:" else "Nombre de pièces:") to "${filteredExpenses.size}",
                                    (if (currentLanguage == "AR") "مجموع المصاريف الإجمالي:" else "TOTAL DEPENSES:") to String.format(Locale.US, "%.2f %s", totalExpensesAmount, currency)
                                ),
                                asPdf = true
                            )
                        } else {
                            Toast.makeText(
                                context,
                                if (currentLanguage == "AR") "لا توجد مصاريف لتصديرها" else "Aucune dépense",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = "PDF",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "PDF",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                color = Color(0xFF16A34A),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color(0xFF15803D)),
                modifier = Modifier
                    .height(42.dp)
                    .clickable {
                        try {
                            val sb = StringBuilder()
                            sb.appendLine("رقم السند,التاريخ,نوع المصروف,الفئة,المبلغ")
                            filteredExpenses.forEach { exp ->
                                val d = dateFormat.format(Date(exp.timestamp))
                                sb.appendLine("\"DEP-${exp.id}\",\"$d\",\"${exp.title}\",\"${exp.category}\",${exp.amountDouble}")
                            }
                            val cacheDir = File(context.cacheDir, "shared_files")
                            if (!cacheDir.exists()) cacheDir.mkdirs()
                            val csvFile = File(cacheDir, "depenses_journal.csv")
                            csvFile.writeText(sb.toString(), Charsets.UTF_8)

                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "تصدير سجل المصاريف"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "خطأ في التصدير: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "XLS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .border(BorderStroke(1.5.dp, Color.White), RoundedCornerShape(2.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (currentLanguage == "AR") "إدارة وسجل المصاريف" else "Gestion des Dépenses",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
    }

    AnimatedVisibility(visible = showSearchBar) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            placeholder = {
                Text(
                    if (currentLanguage == "AR") "البحث في المصاريف أو المبلغ..." else "Recherche...",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFF00B0FF))
                .border(BorderStroke(1.5.dp, Color.Black))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .border(BorderStroke(0.75.dp, Color.Black)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentLanguage == "AR") "عدد المستندات" else "Nombre d'actes",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${filteredExpenses.size}",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFFF0000))
                .border(BorderStroke(1.5.dp, Color.Black))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .border(BorderStroke(0.75.dp, Color.Black)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (currentLanguage == "AR") "مجموع المصاريف" else "Total Dépenses",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format(Locale.US, "%.2f", totalExpensesAmount),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(Color(0xFFFF0000))
                .border(BorderStroke(1.5.dp, Color.Black))
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 32.sp
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .background(Color(0xFFFF0000))
                .border(BorderStroke(1.5.dp, Color.Black))
                .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (currentLanguage == "AR") "اضافة المصروف" else "Ajouter une Dépense",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        }
    }
}
