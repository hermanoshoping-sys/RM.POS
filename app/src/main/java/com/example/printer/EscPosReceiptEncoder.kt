package com.example.printer

import android.util.Log
import com.example.ui.theme.Bilingual
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encodes structured ThermalReceiptData into raw ESC/POS byte streams and plain-text previews.
 * Supports 58mm (32 chars) & 80mm (48 chars), Arabic RTL / French & English LTR, font scaling,
 * custom header/footer lines, QR code printing, and cash drawer kick pulses.
 */
internal object EscPosReceiptEncoder {
    private const val TAG = "EscPosReceiptEncoder"

    private val CMD_INIT = byteArrayOf(0x1B, 0x40)
    private val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val CMD_FONT_A = byteArrayOf(0x1B, 0x4D, 0x00)
    private val CMD_FONT_B = byteArrayOf(0x1B, 0x4D, 0x01)
    private val CMD_TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val CMD_TEXT_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01)
    private val CMD_TEXT_DOUBLE_WIDTH_HEIGHT = byteArrayOf(0x1D, 0x21, 0x11)
    private val CMD_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x03)
    private val CMD_CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x42, 0x10)

    fun generateThermalReceiptBytes(receipt: ThermalReceiptData, config: ThermalPrinterConfig): ByteArray {
        val width = ThermalPaperWidth.fromMm(config.paperWidthMm).charsPerLine
        val lang = config.language.ifBlank { "AR" }.uppercase(Locale.ROOT)
        val isRtl = config.textDirection.resolveRtl(lang)
        val bodyAlign = if (isRtl) CMD_ALIGN_RIGHT else CMD_ALIGN_LEFT
        val stream = ByteArrayOutputStream()

        fun write(bytes: ByteArray) = stream.write(bytes)
        fun writeLine(text: String = "") = stream.write((text + "\n").toByteArray(Charsets.UTF_8))
        fun applyBodyFont() {
            when (config.fontSize) {
                ThermalFontSize.SMALL -> {
                    write(CMD_FONT_B)
                    write(CMD_TEXT_NORMAL)
                }
                ThermalFontSize.NORMAL -> {
                    write(CMD_FONT_A)
                    write(CMD_TEXT_NORMAL)
                }
                ThermalFontSize.LARGE -> {
                    write(CMD_FONT_A)
                    write(CMD_TEXT_DOUBLE_HEIGHT)
                }
            }
        }

        write(CMD_INIT)
        if (config.openCashDrawer || receipt.openCashDrawer) {
            write(CashDrawerManager.generateUniversalDrawerKick(config.drawerPin))
        }

        write(CMD_ALIGN_CENTER)
        if (!receipt.documentTitle.isNullOrBlank()) {
            write(CMD_BOLD_ON)
            writeLine("=== ${receipt.documentTitle} ===")
            write(CMD_BOLD_OFF)
        }
        if (receipt.storeName.isNotBlank()) {
            write(CMD_BOLD_ON)
            write(CMD_TEXT_DOUBLE_WIDTH_HEIGHT)
            writeLine(receipt.storeName)
            write(CMD_TEXT_NORMAL)
            write(CMD_BOLD_OFF)
        }

        applyBodyFont()
        if (receipt.storeAddress.isNotBlank()) writeLine(receipt.storeAddress)
        if (receipt.storePhone.isNotBlank()) {
            val tel = Bilingual.tr(lang, "الهاتف:", "Tél:", "Tel:")
            writeLine("$tel ${receipt.storePhone}")
        }
        if (receipt.taxId.isNotBlank()) writeLine("ICE/IF: ${receipt.taxId}")
        if (config.headerText.isNotBlank()) {
            config.headerText.trim().lines().filter { it.isNotBlank() }.forEach { writeLine(it.trim()) }
        }
        writeLine("-".repeat(width))

        write(bodyAlign)
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(receipt.timestamp))
        writeLine("${Bilingual.tr(lang, "فاتورة رقم:", "Facture N°:", "Invoice #:")} ${receipt.invoiceNumber}")
        writeLine("${Bilingual.tr(lang, "التاريخ:", "Date:", "Date:")} $dateStr")
        if (receipt.cashierName.isNotBlank()) {
            writeLine("${Bilingual.tr(lang, "البائع:", "Vendeur:", "Cashier:")} ${receipt.cashierName}")
        }
        if (!receipt.customerName.isNullOrBlank()) {
            writeLine("${Bilingual.tr(lang, "الزبون:", "Client:", "Customer:")} ${receipt.customerName}")
        }
        if (receipt.paymentMethod.isNotBlank()) {
            writeLine(
                "${Bilingual.tr(lang, "طريقة الدفع:", "Paiement:", "Payment:")} ${
                    formatPaymentMethodLocalized(receipt.paymentMethod, lang)
                }"
            )
        }
        writeLine("-".repeat(width))

        val lblItem = Bilingual.tr(lang, "المنتج", "Article", "Item")
        val lblQty = Bilingual.tr(lang, "كمية", "Qté", "Qty")
        val lblPrice = Bilingual.tr(lang, "السعر", "P.U", "Price")
        val lblTotal = Bilingual.tr(lang, "المجموع", "Total", "Total")

        write(CMD_BOLD_ON)
        writeLine(formatTableHeaderLine(width, isRtl, lblItem, lblQty, lblPrice, lblTotal))
        write(CMD_BOLD_OFF)
        writeLine("-".repeat(width))

        for (item in receipt.items) {
            val qtyStr = if (item.quantity % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", item.quantity)
            } else {
                String.format(Locale.US, "%.2f", item.quantity)
            }
            val priceStr = String.format(Locale.US, "%.2f", item.unitPrice)
            val totalStr = String.format(Locale.US, "%.2f", item.total)
            writeLine(formatItemRowLine(width, isRtl, item.name, qtyStr, priceStr, totalStr))
        }
        writeLine("-".repeat(width))

        write(bodyAlign)
        if (receipt.discountAmount > 0.0) {
            writeLine(
                formatKeyValueLine(
                    label = Bilingual.tr(lang, "المجموع الفرعي:", "Sous-total:", "Subtotal:"),
                    value = String.format(Locale.US, "%.2f", receipt.subtotal),
                    width = width,
                    isRtl = isRtl
                )
            )
            writeLine(
                formatKeyValueLine(
                    label = Bilingual.tr(lang, "الخصم:", "Remise:", "Discount:"),
                    value = String.format(Locale.US, "-%.2f", receipt.discountAmount),
                    width = width,
                    isRtl = isRtl
                )
            )
        }
        if (receipt.taxAmount > 0.0) {
            writeLine(
                formatKeyValueLine(
                    label = Bilingual.tr(lang, "الضريبة (TVA):", "TVA:", "Tax:"),
                    value = String.format(Locale.US, "%.2f", receipt.taxAmount),
                    width = width,
                    isRtl = isRtl
                )
            )
        }

        write(CMD_BOLD_ON)
        write(CMD_TEXT_DOUBLE_HEIGHT)
        writeLine(
            formatKeyValueLine(
                label = Bilingual.tr(lang, "الإجمالي النهائي:", "TOTAL NET:", "GRAND TOTAL:"),
                value = String.format(Locale.US, "%.2f", receipt.grandTotal),
                width = width,
                isRtl = isRtl
            )
        )
        applyBodyFont()
        write(CMD_BOLD_OFF)
        writeLine("-".repeat(width))

        writeLine(
            formatKeyValueLine(
                label = Bilingual.tr(lang, "المدفوع:", "Payé:", "Paid:"),
                value = String.format(Locale.US, "%.2f", receipt.paidAmount),
                width = width,
                isRtl = isRtl
            )
        )
        if (receipt.changeAmount > 0.0) {
            writeLine(
                formatKeyValueLine(
                    label = Bilingual.tr(lang, "الباقي:", "Monnaie:", "Change:"),
                    value = String.format(Locale.US, "%.2f", receipt.changeAmount),
                    width = width,
                    isRtl = isRtl
                )
            )
        }
        writeLine("-".repeat(width))

        val qrPayload = receipt.qrCodeData ?: receipt.invoiceNumber
        if (config.printQrCode && qrPayload.isNotBlank()) {
            write(CMD_ALIGN_CENTER)
            appendQrToStream(stream, qrPayload)
            writeLine()
        }

        write(CMD_ALIGN_CENTER)
        if (receipt.cashierName.isNotBlank()) {
            write(CMD_BOLD_ON)
            writeLine("${Bilingual.tr(lang, "البائع:", "Vendeur:", "Cashier:")} ${receipt.cashierName}")
            write(CMD_BOLD_OFF)
        }
        val effectiveFooter = config.footerText.trim().ifBlank { receipt.footerMessage.trim() }
        if (effectiveFooter.isNotBlank()) {
            effectiveFooter.lines().filter { it.isNotBlank() }.forEach { writeLine(it.trim()) }
        }
        writeLine(Bilingual.tr(lang, "*** شكراً لزيارتكم ***", "*** Merci de votre visite ***", "*** Thank you ***"))

        write(CMD_FEED_LINES)
        write("\n\n\n".toByteArray(Charsets.UTF_8))
        if (config.cutPaper) write(CMD_CUT_PAPER)
        return stream.toByteArray()
    }

    fun generatePlainTextReceipt(receipt: ThermalReceiptData, config: ThermalPrinterConfig): String {
        val width = ThermalPaperWidth.fromMm(config.paperWidthMm).charsPerLine
        val lang = config.language.ifBlank { "AR" }.uppercase(Locale.ROOT)
        val isRtl = config.textDirection.resolveRtl(lang)
        val sb = StringBuilder()
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(receipt.timestamp))

        if (!receipt.documentTitle.isNullOrBlank()) sb.appendLine(padCenter("=== ${receipt.documentTitle} ===", width))
        if (receipt.storeName.isNotBlank()) sb.appendLine(padCenter(receipt.storeName, width))
        if (receipt.storeAddress.isNotBlank()) sb.appendLine(padCenter(receipt.storeAddress, width))
        if (receipt.storePhone.isNotBlank()) {
            sb.appendLine(padCenter("${Bilingual.tr(lang, "الهاتف:", "Tél:", "Tel:")} ${receipt.storePhone}", width))
        }
        if (config.headerText.isNotBlank()) {
            config.headerText.trim().lines().filter { it.isNotBlank() }.forEach {
                sb.appendLine(padCenter(it.trim(), width))
            }
        }
        sb.appendLine("-".repeat(width))

        sb.appendLine("${Bilingual.tr(lang, "فاتورة رقم:", "Facture N°:", "INV:")} ${receipt.invoiceNumber}")
        sb.appendLine("${Bilingual.tr(lang, "التاريخ:", "Date:", "DATE:")} $dateStr")
        if (receipt.cashierName.isNotBlank()) {
            sb.appendLine("${Bilingual.tr(lang, "البائع:", "Vendeur:", "CASHIER:")} ${receipt.cashierName}")
        }
        if (!receipt.customerName.isNullOrBlank()) {
            sb.appendLine("${Bilingual.tr(lang, "الزبون:", "Client:", "CLIENT:")} ${receipt.customerName}")
        }
        if (receipt.paymentMethod.isNotBlank()) {
            sb.appendLine(
                "${Bilingual.tr(lang, "الدفع:", "Paiement:", "PAYMENT:")} ${
                    formatPaymentMethodLocalized(receipt.paymentMethod, lang)
                }"
            )
        }
        sb.appendLine("-".repeat(width))

        val lblItem = Bilingual.tr(lang, "المنتج", "Article", "ITEM")
        val lblQty = Bilingual.tr(lang, "كمية", "Qté", "QTY")
        val lblPrice = Bilingual.tr(lang, "السعر", "P.U", "PRICE")
        val lblTotal = Bilingual.tr(lang, "المجموع", "Total", "TOTAL")
        sb.appendLine(formatTableHeaderLine(width, isRtl, lblItem, lblQty, lblPrice, lblTotal))
        sb.appendLine("-".repeat(width))

        for (item in receipt.items) {
            val qty = if (item.quantity % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", item.quantity)
            } else {
                String.format(Locale.US, "%.2f", item.quantity)
            }
            val price = String.format(Locale.US, "%.2f", item.unitPrice)
            val tot = String.format(Locale.US, "%.2f", item.total)
            sb.appendLine(formatItemRowLine(width, isRtl, item.name, qty, price, tot))
        }

        sb.appendLine("-".repeat(width))
        if (receipt.discountAmount > 0.0) {
            sb.appendLine(
                formatKeyValueLine(
                    Bilingual.tr(lang, "المجموع الفرعي:", "Sous-total:", "SUBTOTAL:"),
                    String.format(Locale.US, "%.2f", receipt.subtotal),
                    width,
                    isRtl
                )
            )
            sb.appendLine(
                formatKeyValueLine(
                    Bilingual.tr(lang, "الخصم:", "Remise:", "DISCOUNT:"),
                    String.format(Locale.US, "-%.2f", receipt.discountAmount),
                    width,
                    isRtl
                )
            )
        }
        if (receipt.taxAmount > 0.0) {
            sb.appendLine(
                formatKeyValueLine(
                    Bilingual.tr(lang, "الضريبة (TVA):", "TVA:", "TAX:"),
                    String.format(Locale.US, "%.2f", receipt.taxAmount),
                    width,
                    isRtl
                )
            )
        }
        sb.appendLine(
            formatKeyValueLine(
                Bilingual.tr(lang, "الإجمالي:", "TOTAL:", "TOTAL:"),
                String.format(Locale.US, "%.2f", receipt.grandTotal),
                width,
                isRtl
            )
        )
        sb.appendLine(
            formatKeyValueLine(
                Bilingual.tr(lang, "المدفوع:", "Payé:", "PAID:"),
                String.format(Locale.US, "%.2f", receipt.paidAmount),
                width,
                isRtl
            )
        )
        sb.appendLine(
            formatKeyValueLine(
                Bilingual.tr(lang, "الباقي:", "Monnaie:", "CHANGE:"),
                String.format(Locale.US, "%.2f", receipt.changeAmount),
                width,
                isRtl
            )
        )
        sb.appendLine("-".repeat(width))
        if (receipt.cashierName.isNotBlank()) {
            sb.appendLine(padCenter("${Bilingual.tr(lang, "البائع:", "Vendeur:", "Cashier:")} ${receipt.cashierName}", width))
        }
        val effectiveFooter = config.footerText.trim().ifBlank { receipt.footerMessage.trim() }
        if (effectiveFooter.isNotBlank()) {
            effectiveFooter.lines().filter { it.isNotBlank() }.forEach {
                sb.appendLine(padCenter(it.trim(), width))
            }
        }
        sb.appendLine()
        sb.appendLine()
        return sb.toString()
    }

    internal fun appendQrToStream(stream: ByteArrayOutputStream, text: String) {
        try {
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val len = textBytes.size + 3
            val pL = (len and 0xFF).toByte()
            val pH = ((len shr 8) and 0xFF).toByte()
            stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
            stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x05))
            stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31))
            stream.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
            stream.write(textBytes)
            stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode ESC/POS QR code", e)
        }
    }

    internal fun formatPaymentMethodLocalized(method: String, lang: String): String =
        when (method.trim().uppercase(Locale.ROOT)) {
            "CASH" -> Bilingual.tr(lang, "نقداً", "Espèces", "Cash")
            "CARD" -> Bilingual.tr(lang, "بطاقة بنكية", "Carte bancaire", "Card")
            "BANK" -> Bilingual.tr(lang, "تحويل بنكي", "Virement", "Bank transfer")
            "MIXED" -> Bilingual.tr(lang, "مختلط", "Mixte", "Mixed")
            "CREDIT" -> Bilingual.tr(lang, "آجل", "Crédit", "Credit")
            else -> method.ifBlank { Bilingual.tr(lang, "نقداً", "Espèces", "Cash") }
        }

    private fun formatTableHeaderLine(
        width: Int,
        isRtl: Boolean,
        item: String,
        qty: String,
        price: String,
        total: String
    ): String {
        return if (width >= 48) {
            if (isRtl) {
                padLeft(total, 10) + padLeft(price, 8) + padLeft(qty, 6) + padRight(item, 24)
            } else {
                padRight(item, 24) + padLeft(qty, 6) + padLeft(price, 8) + padLeft(total, 10)
            }
        } else {
            if (isRtl) {
                padLeft(total, 10) + padLeft(qty, 6) + padRight(item, 16)
            } else {
                padRight(item, 16) + padLeft(qty, 6) + padLeft(total, 10)
            }
        }
    }

    private fun formatItemRowLine(
        width: Int,
        isRtl: Boolean,
        name: String,
        qty: String,
        price: String,
        total: String
    ): String {
        return if (width >= 48) {
            val truncated = truncate(name, 23)
            if (isRtl) {
                padLeft(total, 10) + padLeft(price, 8) + padLeft(qty, 6) + padRight(truncated, 24)
            } else {
                padRight(truncated, 24) + padLeft(qty, 6) + padLeft(price, 8) + padLeft(total, 10)
            }
        } else {
            val truncated = truncate(name, 15)
            if (isRtl) {
                padLeft(total, 10) + padLeft(qty, 6) + padRight(truncated, 16)
            } else {
                padRight(truncated, 16) + padLeft(qty, 6) + padLeft(total, 10)
            }
        }
    }

    private fun formatKeyValueLine(label: String, value: String, width: Int, isRtl: Boolean): String {
        val half = width / 2
        val rest = width - half
        return if (isRtl) {
            padRight(value, rest) + padLeft(label, half)
        } else {
            padRight(label, half) + padLeft(value, rest)
        }
    }

    private fun padCenter(text: String, width: Int): String =
        if (text.length >= width) text else " ".repeat((width - text.length) / 2) + text

    private fun padRight(text: String, width: Int): String =
        if (text.length >= width) text.substring(0, width) else text + " ".repeat(width - text.length)

    private fun padLeft(text: String, width: Int): String =
        if (text.length >= width) text.substring(0, width) else " ".repeat(width - text.length) + text

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length > maxLen) text.substring(0, maxLen) else text
}
