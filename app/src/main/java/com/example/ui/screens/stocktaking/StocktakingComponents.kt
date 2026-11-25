package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.stocktaking.DuplicateScanMode
import com.example.domain.stocktaking.InventoryItemStatus
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.ui.theme.Bilingual
import com.example.ui.viewmodels.StocktakingFeedback
import com.example.ui.viewmodels.StocktakingFeedbackType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun isStocktakingDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

internal object StocktakingPalette {
    val NavyDeep = Color(0xFF0F172A)
    val NavySurface = Color(0xFF1E293B)
    val NavyBorder = Color(0xFF334155)

    val PageBg: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF0B1120) else Color(0xFFF1F5F9)
    val CardBg: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val SurfaceSubtle: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val SelectedCardBg: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF172554) else Color(0xFFEFF6FF)
    val CardBorder: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF334155) else Color(0xFFE2E8F0)
    val TextPrimary: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val TextSecondary: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFFCBD5E1) else Color(0xFF475569)
    val TextMuted: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF94A3B8) else Color(0xFF64748B)
    val InputBg: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    val InputText: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color.White else Color.Black
    val InputBorder: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF475569) else Color(0xFFCBD5E1)

    val AccentBlue = Color(0xFF2563EB)
    val AccentSky = Color(0xFF0284C7)
    val Emerald = Color(0xFF059669)
    val EmeraldSoft: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF064E3B) else Color(0xFFD1FAE5)
    val Amber = Color(0xFFD97706)
    val AmberSoft: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF78350F) else Color(0xFFFEF3C7)
    val Rose = Color(0xFFDC2626)
    val RoseSoft: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF7F1D1D) else Color(0xFFFEE2E2)
    val Indigo = Color(0xFF4F46E5)
    val IndigoSoft: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF312E81) else Color(0xFFE0E7FF)
    val SlateSoft: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF334155) else Color(0xFFE2E8F0)
    val TableHeaderBg: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF1E293B) else Color(0xFF0F172A)
    val TableHeaderText: Color = Color(0xFFF8FAFC)
    val GridLine: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF334155) else Color(0xFFCBD5E1)
    val ZebraRowBg: Color
        @Composable get() = if (isStocktakingDarkTheme()) Color(0xFF162033) else Color(0xFFF8FAFC)
}

@Composable
internal fun stocktakingTextFieldColors(): TextFieldColors {
    val textColor = StocktakingPalette.InputText
    val containerColor = StocktakingPalette.InputBg
    val borderColor = StocktakingPalette.InputBorder
    val labelColor = StocktakingPalette.TextSecondary
    val placeholderColor = StocktakingPalette.TextMuted
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        disabledTextColor = textColor.copy(alpha = 0.6f),
        errorTextColor = textColor,
        cursorColor = StocktakingPalette.AccentBlue,
        focusedContainerColor = containerColor,
        unfocusedContainerColor = containerColor,
        disabledContainerColor = containerColor,
        errorContainerColor = containerColor,
        focusedBorderColor = StocktakingPalette.AccentBlue,
        unfocusedBorderColor = borderColor,
        focusedLabelColor = StocktakingPalette.AccentBlue,
        unfocusedLabelColor = labelColor,
        focusedPlaceholderColor = placeholderColor,
        unfocusedPlaceholderColor = placeholderColor,
        focusedLeadingIconColor = labelColor,
        unfocusedLeadingIconColor = labelColor,
        focusedTrailingIconColor = labelColor,
        unfocusedTrailingIconColor = labelColor
    )
}

internal fun formatStocktakingDate(timestamp: Long?, lang: String): String {
    if (timestamp == null || timestamp <= 0L) return Bilingual.tr(lang, "—", "—")
    val locale = if (lang == "AR") Locale("ar") else Locale.FRANCE
    return SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(timestamp))
}

internal fun formatStocktakingShortDate(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "—"
    return SimpleDateFormat("dd/MM/yy HH:mm", Locale.US).format(Date(timestamp))
}

internal fun inventoryStatusLabel(status: InventoryStatus, lang: String): String = when (status) {
    InventoryStatus.DRAFT -> Bilingual.tr(lang, "مسودة / متوقف", "Brouillon / En pause")
    InventoryStatus.IN_PROGRESS -> Bilingual.tr(lang, "قيد الجرد", "En cours")
    InventoryStatus.COMPLETED -> Bilingual.tr(lang, "مكتمل ومعتمد", "Clôturé & Validé")
    InventoryStatus.CANCELLED -> Bilingual.tr(lang, "ملغى", "Annulé")
}

@Composable
internal fun inventoryStatusColors(status: InventoryStatus): Pair<Color, Color> {
    val dark = isStocktakingDarkTheme()
    return when (status) {
        InventoryStatus.DRAFT -> StocktakingPalette.AmberSoft to if (dark) Color(0xFFFDE68A) else StocktakingPalette.Amber
        InventoryStatus.IN_PROGRESS -> StocktakingPalette.IndigoSoft to if (dark) Color(0xFFA5B4FC) else StocktakingPalette.Indigo
        InventoryStatus.COMPLETED -> StocktakingPalette.EmeraldSoft to if (dark) Color(0xFF6EE7B7) else StocktakingPalette.Emerald
        InventoryStatus.CANCELLED -> StocktakingPalette.RoseSoft to if (dark) Color(0xFFFCA5A5) else StocktakingPalette.Rose
    }
}

internal fun inventoryTypeLabel(type: InventoryType, lang: String): String = when (type) {
    InventoryType.GENERAL -> Bilingual.tr(lang, "جرد شامل", "Inventaire général")
    InventoryType.PARTIAL_CATEGORY -> Bilingual.tr(lang, "جزئي (فئات)", "Partiel (Catégories)")
    InventoryType.PARTIAL_PRODUCTS -> Bilingual.tr(lang, "جزئي (منتجات)", "Partiel (Produits)")
    InventoryType.PARTIAL_SUPPLIER -> Bilingual.tr(lang, "جزئي (موردون)", "Partiel (Fournisseurs)")
}

internal fun itemStatusLabel(status: InventoryItemStatus, lang: String): String = when (status) {
    InventoryItemStatus.UNCOUNTED -> Bilingual.tr(lang, "غير معدود", "Non compté")
    InventoryItemStatus.MATCHING -> Bilingual.tr(lang, "مطابق", "Conforme")
    InventoryItemStatus.SHORTAGE -> Bilingual.tr(lang, "عجز", "Manquant")
    InventoryItemStatus.SURPLUS -> Bilingual.tr(lang, "فائض", "Surplus")
}

@Composable
internal fun itemStatusColors(status: InventoryItemStatus): Pair<Color, Color> {
    val dark = isStocktakingDarkTheme()
    return when (status) {
        InventoryItemStatus.UNCOUNTED -> StocktakingPalette.SlateSoft to StocktakingPalette.TextSecondary
        InventoryItemStatus.MATCHING -> StocktakingPalette.IndigoSoft to if (dark) Color(0xFF7DD3FC) else StocktakingPalette.AccentSky
        InventoryItemStatus.SHORTAGE -> StocktakingPalette.RoseSoft to if (dark) Color(0xFFFCA5A5) else StocktakingPalette.Rose
        InventoryItemStatus.SURPLUS -> StocktakingPalette.EmeraldSoft to if (dark) Color(0xFF6EE7B7) else StocktakingPalette.Emerald
    }
}

internal fun duplicateScanModeLabel(mode: DuplicateScanMode, lang: String): String = when (mode) {
    DuplicateScanMode.INCREMENT -> Bilingual.tr(lang, "زيادة تلقائية (+1)", "Incrémenter (+1)")
    DuplicateScanMode.PROMPT -> Bilingual.tr(lang, "سؤال عند التكرار", "Demander confirmation")
    DuplicateScanMode.REPLACE -> Bilingual.tr(lang, "استبدال الكمية", "Remplacer la quantité")
}

internal fun duplicateScanModeShortLabel(mode: DuplicateScanMode, lang: String): String = when (mode) {
    DuplicateScanMode.INCREMENT -> "+1"
    DuplicateScanMode.PROMPT -> Bilingual.tr(lang, "سؤال", "Choix")
    DuplicateScanMode.REPLACE -> Bilingual.tr(lang, "استبدال", "Rempl.")
}

@Composable
internal fun StocktakingHeaderBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    backTestTag: String = "stocktaking_back_button",
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(StocktakingPalette.NavyDeep, Color(0xFF172554))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StocktakingPalette.NavySurface)
                    .testTag(backTestTag)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = trailingContent
            )
        }
    }
}

@Composable
internal fun StocktakingStatusBadge(
    status: InventoryStatus,
    lang: String,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = inventoryStatusColors(status)
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = inventoryStatusLabel(status, lang),
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun StocktakingItemStatusBadge(
    status: InventoryItemStatus,
    lang: String,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = itemStatusColors(status)
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = itemStatusLabel(status, lang),
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun StocktakingProductThumbnail(
    imageUri: String?,
    contentDescription: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(10.dp),
        color = StocktakingPalette.SurfaceSubtle,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder)
    ) {
        if (!imageUri.isNullOrBlank()) {
            AsyncImage(
                model = imageUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.Inventory2,
                    contentDescription = contentDescription,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}

@Composable
internal fun StocktakingKpiCard(
    label: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    color = StocktakingPalette.TextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                color = StocktakingPalette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = StocktakingPalette.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun StocktakingFeedbackBanner(
    feedback: StocktakingFeedback,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bg, icon) = when (feedback.type) {
        StocktakingFeedbackType.SUCCESS -> StocktakingPalette.Emerald to Icons.Filled.CheckCircle
        StocktakingFeedbackType.WARNING -> StocktakingPalette.Amber to Icons.Filled.Warning
        StocktakingFeedbackType.ERROR -> StocktakingPalette.Rose to Icons.Filled.Error
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("stocktaking_feedback_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(
                text = feedback.message,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
