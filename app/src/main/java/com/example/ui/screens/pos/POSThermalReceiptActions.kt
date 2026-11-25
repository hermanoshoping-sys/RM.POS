package com.example.ui.screens.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.printer.BluetoothPrinterService
import com.example.data.SaleEntity
import com.example.data.SaleItemEntity
import com.example.data.GlobalSettingsEntity
import com.example.printer.BtPrinterConnectionState
import com.example.ui.components.rememberBluetoothAwarePrintLauncher
import com.example.ui.screens.shareReceipt
import com.example.ui.theme.Bilingual
import com.example.ui.theme.POSPrimary
import com.example.ui.viewmodels.PosViewModel

@Composable
internal fun ThermalReceiptActionsSection(
    viewModel: PosViewModel,
    sale: SaleEntity,
    items: List<SaleItemEntity>,
    storeSettings: GlobalSettingsEntity,
    btState: BtPrinterConnectionState,
    lang: String,
    onOpenBtPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val launchThermalPrint = rememberBluetoothAwarePrintLauncher(lang = lang) {
        viewModel.printSaleViaService(
            context = context,
            sale = sale,
            items = items
        ) { result ->
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        onClick = onOpenBtPicker,
        color = Color(0xFFF1F5F9),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            when (btState) {
                is BtPrinterConnectionState.Connected -> Color(0xFF10B981)
                is BtPrinterConnectionState.Error -> Color(0xFFEF4444)
                else -> Color(0xFFCBD5E1)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("receipt_bt_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (btState is BtPrinterConnectionState.Connected) {
                        Icons.Filled.BluetoothConnected
                    } else {
                        Icons.Filled.Bluetooth
                    },
                    contentDescription = null,
                    tint = if (btState is BtPrinterConnectionState.Connected) Color(0xFF10B981) else POSPrimary,
                    modifier = Modifier.size(18.dp)
                )
                val label = when (btState) {
                    is BtPrinterConnectionState.Connected ->
                        "${btState.device.name} (${btState.device.address})"
                    is BtPrinterConnectionState.Error -> btState.message
                    else -> Bilingual.tr(
                        lang,
                        "تحديد / ربط طابعة البلوتوث",
                        "Sélectionner imprimante BT",
                        "Select / Connect BT Printer"
                    )
                }
                Text(
                    text = label,
                    color = Color(0xFF0F172A),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Filled.SettingsBluetooth,
                contentDescription = Bilingual.tr(lang, "إعدادات الطابعة", "Paramètres BT", "BT Settings"),
                tint = Color(0xFF475569),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { shareReceipt(context, sale, items, asPdf = false, settings = storeSettings) },
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF334155), RoundedCornerShape(8.dp))
        ) {
            Icon(Icons.Filled.Image, contentDescription = "Share Image", tint = Color.White)
        }
        IconButton(
            onClick = { shareReceipt(context, sale, items, asPdf = true, settings = storeSettings) },
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFFDC2626), RoundedCornerShape(8.dp))
        ) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Share PDF", tint = Color.White)
        }
        IconButton(
            onClick = {
                BluetoothPrinterService.printInvoiceSystem(context, sale, items, storeSettings)
            },
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF2563EB), RoundedCornerShape(8.dp))
                .testTag("receipt_system_print_button")
        ) {
            Icon(Icons.Filled.Print, contentDescription = "System Print", tint = Color.White)
        }
        Button(
            onClick = {
                launchThermalPrint(viewModel.printerConnectionType.value)
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("receipt_thermal_print_button"),
            colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                Bilingual.tr(lang, "طباعة حرارية", "Imprimer ESC/POS", "Thermal Print"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = POSPrimary),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(Bilingual.tr(lang, "مكتمل / إغلاق", "Terminer / OK", "Done / OK"), fontSize = 13.sp)
    }
}

@Composable
fun ReceiptFactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.DarkGray, fontSize = 10.sp)
        Text(value, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    }
}

@Composable
fun ReceiptAggregateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(value, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}
