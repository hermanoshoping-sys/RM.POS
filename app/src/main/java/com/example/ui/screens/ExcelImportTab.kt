package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import com.example.util.ProductExcelManager.ProductExcelRow

@Composable
internal fun ExcelImportTabContent(
    viewModel: PosViewModel,
    lang: String,
    currency: String,
    selectedFileName: String?,
    parsedRows: List<ProductExcelRow>,
    isParsingFile: Boolean,
    onPickFileClick: () -> Unit,
    onImportCompleted: (String) -> Unit
) {
    var updateExistingProducts by remember { mutableStateOf(true) }
    var isImportingToDb by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(26.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "AR") "اختر ملف Excel (.xlsx) أو (.csv)" else "Choisir un fichier Excel (.xlsx / .csv)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = selectedFileName ?: if (lang == "AR") "اضغط للبحث عن الملف في هاتفك" else "Cliquez pour sélectionner un fichier",
                            color = if (selectedFileName != null) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontWeight = if (selectedFileName != null) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onPickFileClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (lang == "AR") "استعراض ملفات الهاتف..." else "Parcourir le téléphone...", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isParsingFile) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(36.dp))
                    Text(
                        text = if (lang == "AR") "جارٍ قراءة وفحص بيانات الملف..." else "Lecture du fichier en cours...",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (parsedRows.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == "AR") "عدد المنتجات المقروءة:" else "Produits détectés :",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Surface(color = Color(0xFF107C41), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "${parsedRows.size} " + if (lang == "AR") "منتج" else "prod.",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (lang == "AR") "تحديث المنتجات الحالية" else "Mettre à jour les existants",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (lang == "AR") "تحديث السعر والتصنيف إذا تطابق كود البار" else "Actualiser prix si le code-barres existe déjà",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = updateExistingProducts,
                            onCheckedChange = { updateExistingProducts = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF107C41))
                        )
                    }
                }
            }

            Text(
                text = if (lang == "AR") "معاينة أولية للمنتجات المقروءة:" else "Aperçu des produits :",
                color = Color(0xFF38BDF8),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(parsedRows.take(15), key = { idx, row -> "${row.barcode}_${row.name}_$idx" }) { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color(0xFF0F172A) else Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.6f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(color = Color(0xFF2563EB).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = if (lang == "AR") "المنتج:" else "Nom:",
                                        color = Color(0xFF60A5FA),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = row.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${if (lang == "AR") "باركود: " else "Code: "}${row.barcode.ifBlank { if (lang == "AR") "تلقائي" else "Auto" }}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp
                                )
                                Text(text = "•", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = "${if (lang == "AR") "العائلة: " else "Famille: "}${row.familyName.ifBlank { if (lang == "AR") "عام" else "Général" }}",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 11.sp
                                )
                                Text(text = "•", color = Color.Gray, fontSize = 11.sp)
                                Text(text = row.unit, color = Color(0xFFF59E0B), fontSize = 11.sp)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MoneyUtils.format(row.sellPrice, currency),
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${if (lang == "AR") "شراء: " else "Achat: "}${MoneyUtils.format(row.purchasePrice, currency)}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    isImportingToDb = true
                    viewModel.importProductsFromExcel(rows = parsedRows, updateExisting = updateExistingProducts) { res ->
                        isImportingToDb = false
                        val summaryMsg = if (lang == "AR") {
                            "تم الاستيراد بنجاح! \n✓ مضافة جديدة: ${res.insertedCount}\n✓ محدثة: ${res.updatedCount}\n✓ متخطاة: ${res.skippedCount}"
                        } else {
                            "Importation réussie !\n✓ Ajoutés : ${res.insertedCount}\n✓ Mis à jour : ${res.updatedCount}\n✓ Ignorés : ${res.skippedCount}"
                        }
                        onImportCompleted(summaryMsg)
                    }
                },
                enabled = !isImportingToDb,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isImportingToDb) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "AR") "جارٍ الحفظ في قاعدة البيانات..." else "Enregistrement en cours...")
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "AR") "تأكيد واستيراد (${parsedRows.size}) منتج الآن" else "Confirmer l'importation (${parsedRows.size} produits)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
