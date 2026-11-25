package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun AddExpenseVoucherDialog(
    currentLanguage: String,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, category: String) -> Unit
) {
    val context = LocalContext.current
    var newTitle by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("مصاريف عامة / Divers") }
    var beneficiaryName by remember { mutableStateOf("") }

    val commonCategories = listOf(
        "كراء المحل التجاري / Loyer",
        "فاتورة الكهرباء والماء / Electricité",
        "رواتب وأجور العمال / Salaires",
        "صيانة ومعدات / Maintenance",
        "مصاريف النقل والشحن / Transport",
        "مواد التعبئة والنظافة / Fournitures",
        "مصاريف عامة / Divers"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, Color(0xFFFF0000)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .imePadding()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentLanguage == "AR") "اضافة سند مصروف جديد" else "Nouvelle Dépense",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFFFF0000)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text(if (currentLanguage == "AR") "نوع وبيان المصروف *" else "Intitulé dépense *", color = Color.Gray) },
                    placeholder = { Text(if (currentLanguage == "AR") "مثال: فاتورة استهلاك الكهرباء" else "Ex: Facture électricité", color = Color.DarkGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF0000),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newAmount,
                    onValueChange = { newAmount = it },
                    label = { Text(if (currentLanguage == "AR") "ثمن المصروف ($currency) *" else "Montant ($currency) *", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF0000),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = beneficiaryName,
                    onValueChange = { beneficiaryName = it },
                    label = { Text(if (currentLanguage == "AR") "المستفيد / الشخص المستلم (اختياري)" else "Bénéficiaire (optionnel)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF0000),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (currentLanguage == "AR") "فئة المصروف الشائعة:" else "Catégorie:",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonCategories.forEach { cat ->
                        val isSel = newCategory == cat
                        Surface(
                            color = if (isSel) Color(0xFFFF0000) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isSel) Color.White else Color(0xFF334155)),
                            modifier = Modifier.clickable {
                                newCategory = cat
                                if (newTitle.isBlank()) {
                                    newTitle = cat.split("/").first().trim()
                                }
                            }
                        ) {
                            Text(
                                text = if (currentLanguage == "AR") cat.split("/").first().trim() else cat.split("/").last().trim(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (currentLanguage == "AR") "إلغاء" else "Annuler", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = newAmount.toDoubleOrNull() ?: 0.0
                            if (newTitle.isNotBlank() && amt > 0.0) {
                                val fullTitle = if (beneficiaryName.isNotBlank()) {
                                    "$newTitle - المستلم: $beneficiaryName"
                                } else {
                                    newTitle
                                }
                                onSave(fullTitle, amt, newCategory)
                                Toast.makeText(
                                    context,
                                    if (currentLanguage == "AR") "تم تسجيل المصروف بنجاح!" else "Dépense enregistrée!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            } else {
                                Toast.makeText(
                                    context,
                                    if (currentLanguage == "AR") "يرجى إدخال بيان ومبلغ صحيح!" else "Veuillez remplir les champs obligatoires!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            if (currentLanguage == "AR") "حفظ المصروف" else "Enregistrer",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
