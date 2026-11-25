package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProductEntity
import com.example.util.ProductExcelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExcelExportTabContent(
    context: Context,
    coroutineScope: CoroutineScope,
    productsList: List<ProductEntity>,
    filteredList: List<ProductEntity>,
    categoriesMap: Map<Long, String>,
    lang: String
) {
    var exportOnlyFiltered by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val targetList = if (exportOnlyFiltered && filteredList.isNotEmpty()) filteredList else productsList

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (lang == "AR") "نطاق التصدير:" else "Portée de l'exportation :",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !exportOnlyFiltered,
                        onClick = { exportOnlyFiltered = false },
                        label = { Text(if (lang == "AR") "جميع المنتجات (${productsList.size})" else "Tous les produits (${productsList.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF107C41),
                            selectedLabelColor = Color.White
                        )
                    )
                    if (filteredList.size != productsList.size) {
                        FilterChip(
                            selected = exportOnlyFiltered,
                            onClick = { exportOnlyFiltered = true },
                            label = { Text(if (lang == "AR") "نتائج البحث الحالية (${filteredList.size})" else "Filtrés (${filteredList.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF107C41),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF107C41))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Description, contentDescription = null, tint = Color(0xFF107C41), modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "AR") "تصدير ملف Microsoft Excel (.xlsx)" else "Exporter vers Microsoft Excel (.xlsx)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (lang == "AR") "ملف إكسيل رسمي ملون وجاهز للفتح المباشر على الكمبيوتر والهاتف" else "Format XLSX officiel avec couleurs et en-tête vert",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        isExporting = true
                        coroutineScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    ProductExcelManager.exportToXlsx(context, targetList, categoriesMap, lang, isTemplate = false)
                                }
                                ProductExcelManager.shareFile(
                                    context,
                                    file,
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    if (lang == "AR") "مشاركة ملف إكسيل المنتجات" else "Partager l'inventaire Excel"
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "تصدير ومشاركة (.xlsx)" else "Exporter & Partager (.xlsx)", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.TableChart, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "AR") "تصدير ملف جدول (.csv مع دعم العربية)" else "Exporter au format CSV (UTF-8 avec BOM)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (lang == "AR") "متوافق مع جداول البيانات و Google Sheets بدون تشويه الحروف" else "Compatible avec Excel et Google Sheets",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        isExporting = true
                        coroutineScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    ProductExcelManager.exportToCsv(context, targetList, categoriesMap, lang, isTemplate = false)
                                }
                                ProductExcelManager.shareFile(
                                    context,
                                    file,
                                    "text/csv",
                                    if (lang == "AR") "مشاركة ملف CSV" else "Partager le fichier CSV"
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "تصدير ومشاركة (.csv)" else "Exporter & Partager (.csv)", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "AR") "تحميل نموذج إكسيل فارغ لتعبئته" else "Télécharger un modèle Excel vierge",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (lang == "AR") "يحتوي على الأعمدة الستة مع أمثلة توضيحية لتسهيل إدخال بياناتك" else "Modèle pré-formaté avec les 6 colonnes et exemples",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    ProductExcelManager.exportToXlsx(context, emptyList(), emptyMap(), lang, isTemplate = true)
                                }
                                ProductExcelManager.shareFile(
                                    context,
                                    file,
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    if (lang == "AR") "نموذج إكسيل لإدخال المنتجات" else "Modèle Excel Produits"
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Template error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "AR") "تحميل النموذج الفارغ (.xlsx)" else "Télécharger le modèle (.xlsx)",
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

