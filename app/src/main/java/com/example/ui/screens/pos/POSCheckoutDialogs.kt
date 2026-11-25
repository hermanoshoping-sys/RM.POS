package com.example.ui.screens.pos

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.printer.BluetoothPrinterService
import com.example.domain.FinancialCalculator
import com.example.printer.BluetoothPrinterConnectionManager
import com.example.printer.BtPrinterConnectionState
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.PosViewModel
import com.example.util.MoneyUtils
import com.example.util.cents
import java.util.Locale

@Composable
fun CheckoutPayDialog(viewModel: PosViewModel, grandTotal: Double, lang: String, onDismiss: () -> Unit) {
    val subTotal by viewModel.cartTotalBeforeGlobalDiscount.collectAsStateWithLifecycle()
    val discountVal by viewModel.globalDiscountValue.collectAsStateWithLifecycle()
    val discountType by viewModel.globalDiscountType.collectAsStateWithLifecycle()

    val baseSummary = remember(subTotal, grandTotal, discountType, discountVal) {
        val baseCents = if (subTotal != 0.0) subTotal.cents else {
            val netCents = grandTotal.cents
            if (discountType.equals("PERCENT", ignoreCase = true) && discountVal in 0.0..99.9) {
                kotlin.math.round(netCents / (1.0 - discountVal / 100.0)).toLong()
            } else {
                netCents + discountVal.cents
            }
        }
        FinancialCalculator.computeCartSummary(
            subtotalCents = baseCents,
            discountType = discountType,
            discountValue = discountVal,
            paymentMethod = viewModel.selectedPaymentMethod.value.ifBlank { "CASH" },
            receivedCash = 0.0
        )
    }
    val discountAmount = baseSummary.discountAmount
    val netTotal = if (baseSummary.netTotalCents != 0L || baseSummary.subtotalCents == 0L) baseSummary.netTotal else grandTotal
    val actualSubTotal = baseSummary.subtotal
    val isReturnInvoice = netTotal < 0.0

    var chosenPaymentMethod by remember { mutableStateOf(viewModel.selectedPaymentMethod.value.ifBlank { "CASH" }) }
    var receivedAmountInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", netTotal)) }
    var showDiscountDialogInCheckout by remember { mutableStateOf(false) }
    var showBluetoothPickerInCheckout by remember { mutableStateOf(false) }
    var isPrintingSummary by remember { mutableStateOf(false) }
    var printStatusMessage by remember { mutableStateOf<String?>(null) }
    var printStatusIsError by remember { mutableStateOf(false) }
    var checkoutErrorMessage by remember { mutableStateOf<String?>(null) }

    val btState by viewModel.btConnectionState.collectAsStateWithLifecycle()
    val savedPrinterName by viewModel.printerName.collectAsStateWithLifecycle()
    val savedPrinterMac by viewModel.printerMac.collectAsStateWithLifecycle()
    val printerConnectionType by viewModel.printerConnectionType.collectAsStateWithLifecycle()
    val autoPrintReceipt by viewModel.autoPrintReceipt.collectAsStateWithLifecycle()
    val storeCurrency by viewModel.currency.collectAsStateWithLifecycle()

    LaunchedEffect(netTotal) {
        receivedAmountInput = String.format(Locale.US, "%.2f", netTotal)
    }

    val parsedReceived = receivedAmountInput.replace(',', '.').trim().toDoubleOrNull() ?: netTotal
    val receivedAmount = if (isReturnInvoice && parsedReceived > 0.0) -parsedReceived else parsedReceived
    val liveSummary = remember(actualSubTotal, discountType, discountVal, chosenPaymentMethod, receivedAmount) {
        FinancialCalculator.computeCartSummary(
            subtotalCents = actualSubTotal.cents,
            discountType = discountType,
            discountValue = discountVal,
            paymentMethod = chosenPaymentMethod,
            receivedCash = receivedAmount
        )
    }
    val changeDue = liveSummary.changeDue
    val context = LocalContext.current

    fun triggerPrintSummary() {
        isPrintingSummary = true
        printStatusMessage = null
        printStatusIsError = false
        viewModel.printCheckoutTransactionSummary(
            context = context,
            subtotal = actualSubTotal,
            discountAmount = discountAmount,
            netTotal = netTotal,
            paymentMethod = chosenPaymentMethod,
            receivedAmount = receivedAmount,
            changeDue = changeDue,
            isDraftSummary = true
        ) { ok, msg ->
            isPrintingSummary = false
            printStatusMessage = msg
            printStatusIsError = !ok
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            val paired = BluetoothPrinterConnectionManager.getPairedDevicesSafe(context)
            if (btState is BtPrinterConnectionState.Connected ||
                savedPrinterMac.isNotBlank() ||
                savedPrinterName.isNotBlank() ||
                paired.isNotEmpty()
            ) {
                triggerPrintSummary()
            } else {
                showBluetoothPickerInCheckout = true
            }
        } else {
            printStatusMessage = Bilingual.tr(
                lang,
                "إذن البلوتوث مطلوب لطباعة ملخص المعاملة",
                "Autorisation Bluetooth requise pour imprimer le résumé",
                "Bluetooth permission is required to print the summary"
            )
            printStatusIsError = true
        }
    }

    fun handleDirectPrintSummaryClick() {
        val usingBt = printerConnectionType.equals("BLUETOOTH", ignoreCase = true) ||
            btState is BtPrinterConnectionState.Connected
        if (usingBt && !BluetoothPrinterConnectionManager.hasConnectPermission(context)) {
            val perms = BluetoothPrinterConnectionManager.requiredConnectPermissions()
            if (perms.isNotEmpty()) {
                btPermissionLauncher.launch(perms.toTypedArray())
                return
            }
        }
        if (usingBt && !BluetoothPrinterService.isBluetoothEnabled(context)) {
            showBluetoothPickerInCheckout = true
            return
        }
        val hasTarget = btState is BtPrinterConnectionState.Connected ||
            savedPrinterMac.isNotBlank() ||
            savedPrinterName.isNotBlank()
        if (usingBt && !hasTarget && BluetoothPrinterConnectionManager.getPairedDevicesSafe(context).isEmpty()) {
            showBluetoothPickerInCheckout = true
            return
        }
        triggerPrintSummary()
    }

    fun formatMoney(amount: Double): String {
        return MoneyUtils.format(amount, storeCurrency)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = Bilingual.tr(lang, "رجوع", "Retour", "Back"),
                        tint = Color.White
                    )
                }

                Text(
                    text = if (isReturnInvoice) {
                        Bilingual.tr(lang, "استرجاع للزبون", "Retour Client", "Customer Refund")
                    } else {
                        Bilingual.tr(lang, "الدفع", "Paiement", "Checkout")
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = if (isReturnInvoice) Color(0xFFFCA5A5) else Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CheckoutAmountBreakdownRows(
                    actualSubTotal = actualSubTotal,
                    discountAmount = discountAmount,
                    netTotal = netTotal,
                    formatMoney = ::formatMoney,
                    lang = lang,
                    isReturnInvoice = isReturnInvoice,
                    onEditDiscount = { showDiscountDialogInCheckout = true }
                )

                Spacer(modifier = Modifier.height(18.dp))

                CheckoutPaymentAndReceivedSection(
                    chosenPaymentMethod = chosenPaymentMethod,
                    receivedAmountInput = receivedAmountInput,
                    receivedAmount = receivedAmount,
                    changeDue = changeDue,
                    formatMoney = ::formatMoney,
                    lang = lang,
                    isReturnInvoice = isReturnInvoice,
                    onSelectMethod = { method ->
                        chosenPaymentMethod = method
                        viewModel.selectedPaymentMethod.value = method
                        val targetCash = if (method == "CREDIT") 0.0 else netTotal
                        receivedAmountInput = String.format(Locale.US, "%.2f", targetCash)
                        viewModel.receivedCash.value = targetCash
                    },
                    onReceivedInputChanged = { filtered ->
                        receivedAmountInput = filtered
                        val parsed = filtered.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
                        viewModel.receivedCash.value = if (isReturnInvoice && parsed > 0.0) -parsed else parsed
                    }
                )

                // Live Partial-Payment -> DEBT Notice
                if (liveSummary.remainingDebtCents > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_partial_debt_notice")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "دفعة جزئية — سيتم تسجيل الباقي كدين (DEBT): ${MoneyUtils.format(liveSummary.remainingDebtCents, storeCurrency)}",
                                    "Paiement partiel : le reste (${MoneyUtils.format(liveSummary.remainingDebtCents, storeCurrency)}) sera enregistré en DEBT",
                                    "Partial payment: remaining ${MoneyUtils.format(liveSummary.remainingDebtCents, storeCurrency)} will be recorded as DEBT"
                                ),
                                color = Color(0xFFFDE68A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    chosenPaymentMethod = "CASH"
                                    viewModel.selectedPaymentMethod.value = "CASH"
                                    receivedAmountInput = String.format(Locale.US, "%.2f", netTotal)
                                    viewModel.receivedCash.value = netTotal
                                },
                                modifier = Modifier.testTag("checkout_pay_full_button")
                            ) {
                                Text(
                                    text = Bilingual.tr(lang, "دفع الكل", "Tout payer", "Pay Full"),
                                    color = Color(0xFF4ADE80),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Live Customer Credit / Debt Deduction Notice (Negative Return)
                if (liveSummary.customerCreditCents > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("checkout_customer_credit_notice")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = Bilingual.tr(
                                    lang,
                                    "سيتم خصم ${MoneyUtils.format(liveSummary.customerCreditCents, storeCurrency)} من دين الزبون (رصيد دائن)",
                                    "${MoneyUtils.format(liveSummary.customerCreditCents, storeCurrency)} seront déduits de la dette client",
                                    "${MoneyUtils.format(liveSummary.customerCreditCents, storeCurrency)} will be deducted from customer debt"
                                ),
                                color = Color(0xFFA7F3D0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    chosenPaymentMethod = "CASH"
                                    viewModel.selectedPaymentMethod.value = "CASH"
                                    receivedAmountInput = String.format(Locale.US, "%.2f", netTotal)
                                    viewModel.receivedCash.value = netTotal
                                },
                                modifier = Modifier.testTag("checkout_refund_cash_full_button")
                            ) {
                                Text(
                                    text = Bilingual.tr(lang, "إرجاع نقدي", "Remb. Espèces", "Cash Refund"),
                                    color = Color(0xFF6EE7B7),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                CheckoutPrinterStatusCard(
                    viewModel = viewModel,
                    lang = lang,
                    isPrintingSummary = isPrintingSummary,
                    printStatusMessage = printStatusMessage,
                    printStatusIsError = printStatusIsError,
                    onOpenPrinterPicker = { showBluetoothPickerInCheckout = true },
                    onPrintSummaryClick = { handleDirectPrintSummaryClick() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                checkoutErrorMessage?.let { errMsg ->
                    Surface(
                        color = Color(0xFF7F1D1D).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF87171)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("checkout_error_banner")
                    ) {
                        Text(
                            text = errMsg,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        checkoutErrorMessage = null
                        viewModel.receivedCash.value = receivedAmount
                        viewModel.executeCheckout(
                            context = context,
                            onError = { err ->
                                checkoutErrorMessage = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            },
                            onComplete = {
                                onDismiss()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isReturnInvoice) Color(0xFFDC2626) else Color(0xFF22C55E)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("checkout_confirm_sale_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            isReturnInvoice && autoPrintReceipt -> Bilingual.tr(
                                lang,
                                "تأكيد الاسترجاع وطباعة الوصل",
                                "Valider Retour & Imprimer",
                                "Confirm Return & Print"
                            )
                            isReturnInvoice -> Bilingual.tr(
                                lang,
                                "تأكيد الاسترجاع",
                                "Confirmer le retour",
                                "Confirm Return"
                            )
                            autoPrintReceipt -> Bilingual.tr(
                                lang,
                                "تأكيد البيع وطباعة الوصل",
                                "Valider & Imprimer",
                                "Confirm & Print"
                            )
                            else -> Bilingual.tr(
                                lang,
                                "تأكيد البيع",
                                "Valider la vente",
                                "Confirm Sale"
                            )
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                CheckoutFooterBrand()

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showDiscountDialogInCheckout) {
            DiscountDialog(
                viewModel = viewModel,
                lang = lang,
                onDismiss = { showDiscountDialogInCheckout = false }
            )
        }

        if (showBluetoothPickerInCheckout) {
            CheckoutBluetoothPrinterPickerDialog(
                viewModel = viewModel,
                lang = lang,
                onDismiss = { showBluetoothPickerInCheckout = false },
                onConnectedAndReady = { showBluetoothPickerInCheckout = false }
            )
        }
    }
}
