package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CategoryEntity
import com.example.data.ProductEntity
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ProductExcelManager
import com.example.util.ProductExcelManager.ProductExcelRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelImportExportDialog(
    viewModel: PosViewModel,
    productsList: List<ProductEntity>,
    filteredList: List<ProductEntity>,
    categoriesList: List<CategoryEntity>,
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val storeCurrency by viewModel.currency.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Export, 1: Import
    val categoriesMap = remember(categoriesList) { categoriesList.associate { it.id to it.name } }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var parsedRows by remember { mutableStateOf<List<ProductExcelRow>>(emptyList()) }
    var isParsingFile by remember { mutableStateOf(false) }
    var importResultSummary by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            isParsingFile = true
            coroutineScope.launch {
                val rows = withContext(Dispatchers.IO) {
                    try {
                        ProductExcelManager.parseProductsFromUri(context, uri)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                parsedRows = rows
                isParsingFile = false
                if (rows.isEmpty()) {
                    Toast.makeText(
                        context,
                        if (lang == "AR") "تعذر قراءة أي منتجات صالحة من الملف المختار" else "Aucun produit valide trouvé dans le fichier",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFF107C41)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF107C41)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("X", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, fontFamily = FontFamily.SansSerif)
                        }
                        Column {
                            Text(
                                text = if (lang == "AR") "إدارة المنتجات عبر إكسيل (Excel / CSV)" else "Gestion Produits via Excel",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = if (lang == "AR") "استيراد وتصدير سريع بـ 6 أعمدة مطابقة" else "Import & Export structuré en 6 colonnes",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 0) Color(0xFF107C41) else Color.Transparent)
                            .clickable { selectedTab = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, tint = if (selectedTab == 0) Color.White else Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            Text(
                                text = if (lang == "AR") "تصدير إلى إكسيل (Export)" else "Exporter vers Excel",
                                color = if (selectedTab == 0) Color.White else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == 1) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { selectedTab = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null, tint = if (selectedTab == 1) Color.White else Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            Text(
                                text = if (lang == "AR") "استيراد من إكسيل (Import)" else "Importer depuis Excel",
                                color = if (selectedTab == 1) Color.White else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.ViewColumn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Text(
                                text = if (lang == "AR") "ترتيب الأعمدة المعتمد في الملف:" else "Ordre officiel des colonnes :",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val headers = ProductExcelManager.getHeaders(lang)
                            val colLetters = listOf("A", "B", "C", "D", "E", "F")
                            headers.forEachIndexed { i, h ->
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF475569))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(text = colLetters[i], color = Color(0xFF107C41), fontWeight = FontWeight.Black, fontSize = 11.sp)
                                        Text(text = h, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (selectedTab == 0) {
                        ExcelExportTabContent(
                            context = context,
                            coroutineScope = coroutineScope,
                            productsList = productsList,
                            filteredList = filteredList,
                            categoriesMap = categoriesMap,
                            lang = lang
                        )
                    } else {
                        ExcelImportTabContent(
                            viewModel = viewModel,
                            lang = lang,
                            currency = storeCurrency,
                            selectedFileName = selectedFileName,
                            parsedRows = parsedRows,
                            isParsingFile = isParsingFile,
                            onPickFileClick = {
                                filePickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            onImportCompleted = { summaryMsg -> importResultSummary = summaryMsg }
                        )
                    }
                }
            }
        }
    }

    importResultSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = {
                importResultSummary = null
                onDismiss()
            },
            icon = {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(44.dp))
            },
            title = {
                Text(
                    text = if (lang == "AR") "تقرير استيراد الإكسيل" else "Rapport d'importation",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = summary,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        importResultSummary = null
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41))
                ) {
                    Text(text = if (lang == "AR") "تم، حسناً" else "Terminé", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
