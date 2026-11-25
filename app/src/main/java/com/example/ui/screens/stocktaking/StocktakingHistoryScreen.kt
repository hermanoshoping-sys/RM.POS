package com.example.ui.screens.stocktaking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InventoryEntity
import com.example.domain.stocktaking.InventoryStatus
import com.example.domain.stocktaking.InventoryType
import com.example.domain.stocktaking.StocktakingReportBuilder
import com.example.ui.theme.Bilingual
import com.example.util.MoneyUtils
import java.util.Locale
import kotlin.math.abs

private enum class HistoryStatusFilter { ALL, COMPLETED, ACTIVE, CANCELLED }
private enum class HistoryTypeFilter { ALL, GENERAL, PARTIAL }
private enum class HistoryDateWindow(val days: Int?) {
    ALL(null),
    DAYS_30(30),
    DAYS_90(90),
    DAYS_365(365)
}

@Composable
internal fun StocktakingHistoryScreen(
    allInventories: List<InventoryEntity>,
    isManager: Boolean = true,
    currency: String,
    lang: String,
    onBack: () -> Unit,
    onOpenReport: (InventoryEntity) -> Unit,
    onOpenSession: (InventoryEntity) -> Unit,
    onEditSession: (InventoryEntity) -> Unit = {},
    onDeleteSession: (InventoryEntity) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(HistoryStatusFilter.ALL) }
    var typeFilter by remember { mutableStateOf(HistoryTypeFilter.ALL) }
    var dateWindow by remember { mutableStateOf(HistoryDateWindow.ALL) }

    val analytics = remember(allInventories) {
        StocktakingReportBuilder.buildHistoryAnalytics(allInventories)
    }

    val filteredSessions = remember(allInventories, searchQuery, statusFilter, typeFilter, dateWindow) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        val cutoffMillis = dateWindow.days?.let { days ->
            System.currentTimeMillis() - (days.toLong() * 24L * 60L * 60L * 1000L)
        }

        allInventories.filter { session ->
            val st = InventoryStatus.fromCode(session.status)
            val tp = InventoryType.fromCode(session.type)

            val matchStatus = when (statusFilter) {
                HistoryStatusFilter.ALL -> true
                HistoryStatusFilter.COMPLETED -> st == InventoryStatus.COMPLETED
                HistoryStatusFilter.ACTIVE -> st == InventoryStatus.DRAFT || st == InventoryStatus.IN_PROGRESS
                HistoryStatusFilter.CANCELLED -> st == InventoryStatus.CANCELLED
            }
            val matchType = when (typeFilter) {
                HistoryTypeFilter.ALL -> true
                HistoryTypeFilter.GENERAL -> tp == InventoryType.GENERAL
                HistoryTypeFilter.PARTIAL -> tp != InventoryType.GENERAL
            }
            val matchDate = cutoffMillis == null || session.createdAt >= cutoffMillis
            val matchQuery = q.isEmpty() ||
                session.reference.lowercase(Locale.ROOT).contains(q) ||
                session.title.lowercase(Locale.ROOT).contains(q) ||
                session.createdBy.lowercase(Locale.ROOT).contains(q) ||
                (session.approvedBy?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                session.scopeSummary.lowercase(Locale.ROOT).contains(q) ||
                session.notes.lowercase(Locale.ROOT).contains(q)

            matchStatus && matchType && matchDate && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StocktakingPalette.PageBg)
    ) {
        StocktakingHeaderBar(
            title = Bilingual.tr(lang, "التقارير وأرشيف الجرد", "Rapports & Historique"),
            subtitle = Bilingual.tr(
                lang,
                "${analytics.totalSessions} جلسة إجمالاً • ${analytics.completedSessions} مكتملة",
                "${analytics.totalSessions} sessions au total • ${analytics.completedSessions} clôturées"
            ),
            onBack = onBack,
            backTestTag = "stocktaking_history_back_btn"
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .testTag("stocktaking_history_list"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Cumulative Historical Analytics Grid
            item(key = "history_analytics") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StocktakingKpiCard(
                            label = Bilingual.tr(lang, "متوسط دقة المخزون", "Précision Moyenne"),
                            value = String.format(Locale.US, "%.1f%%", analytics.averageAccuracyPercent),
                            subtitle = Bilingual.tr(
                                lang,
                                "${analytics.completedSessions} جرد معتمد • ${analytics.totalItemsAudited} صنف",
                                "${analytics.completedSessions} clôturé(s) • ${analytics.totalItemsAudited} art."
                            ),
                            icon = Icons.Filled.Verified,
                            accentColor = StocktakingPalette.AccentBlue,
                            modifier = Modifier.weight(1f)
                        )
                        val netColor = when {
                            analytics.cumulativeNetDiscrepancyCents < 0L -> StocktakingPalette.Rose
                            analytics.cumulativeNetDiscrepancyCents > 0L -> StocktakingPalette.Emerald
                            else -> StocktakingPalette.AccentSky
                        }
                        val netSign = when {
                            analytics.cumulativeNetDiscrepancyCents > 0L -> "+"
                            analytics.cumulativeNetDiscrepancyCents < 0L -> "-"
                            else -> ""
                        }
                        StocktakingKpiCard(
                            label = Bilingual.tr(lang, "صافي الفوارق التراكمي", "Écart Net Cumulé"),
                            value = "$netSign${MoneyUtils.format(abs(analytics.cumulativeNetDiscrepancyCents), currency)}",
                            subtitle = Bilingual.tr(
                                lang,
                                "عجز: ${MoneyUtils.format(analytics.cumulativeShortageValueCents, currency)}",
                                "Manq. : ${MoneyUtils.format(analytics.cumulativeShortageValueCents, currency)}"
                            ),
                            icon = Icons.Filled.AccountBalance,
                            accentColor = netColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 2. Search & Filter Controls
            item(key = "history_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                Bilingual.tr(
                                    lang,
                                    "ابحث بالمرجع، العنوان، المسؤول، أو الملاحظات...",
                                    "Rechercher référence, titre, responsable, notes..."
                                ),
                                fontSize = 13.sp
                            )
                        },
                        colors = stocktakingTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stocktaking_history_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val statusChips = listOf(
                            HistoryStatusFilter.ALL to Bilingual.tr(lang, "الكل (${analytics.totalSessions})", "Tous (${analytics.totalSessions})"),
                            HistoryStatusFilter.COMPLETED to Bilingual.tr(lang, "مكتمل (${analytics.completedSessions})", "Clôturés (${analytics.completedSessions})"),
                            HistoryStatusFilter.ACTIVE to Bilingual.tr(lang, "نشط (${analytics.activeSessions})", "Actifs (${analytics.activeSessions})"),
                            HistoryStatusFilter.CANCELLED to Bilingual.tr(lang, "ملغى (${analytics.cancelledSessions})", "Annulés (${analytics.cancelledSessions})")
                        )
                        statusChips.forEach { (flt, label) ->
                            FilterChip(
                                selected = statusFilter == flt,
                                onClick = { statusFilter = flt },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("history_status_${flt.name.lowercase(Locale.ROOT)}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val typeChips = listOf(
                            HistoryTypeFilter.ALL to Bilingual.tr(lang, "كل الأنواع", "Tous types"),
                            HistoryTypeFilter.GENERAL to Bilingual.tr(lang, "شامل", "Général"),
                            HistoryTypeFilter.PARTIAL to Bilingual.tr(lang, "جزئي", "Partiel")
                        )
                        typeChips.forEach { (flt, label) ->
                            FilterChip(
                                selected = typeFilter == flt,
                                onClick = { typeFilter = flt },
                                label = { Text(label, fontSize = 11.5.sp) },
                                modifier = Modifier.testTag("history_type_${flt.name.lowercase(Locale.ROOT)}")
                            )
                        }

                        val dateChips = listOf(
                            HistoryDateWindow.ALL to Bilingual.tr(lang, "كل الفترات", "Toute date"),
                            HistoryDateWindow.DAYS_30 to Bilingual.tr(lang, "30 يوم", "30 j"),
                            HistoryDateWindow.DAYS_90 to Bilingual.tr(lang, "90 يوم", "90 j"),
                            HistoryDateWindow.DAYS_365 to Bilingual.tr(lang, "سنة", "1 an")
                        )
                        dateChips.forEach { (win, label) ->
                            FilterChip(
                                selected = dateWindow == win,
                                onClick = { dateWindow = win },
                                label = { Text(label, fontSize = 11.5.sp) },
                                modifier = Modifier.testTag("history_date_${win.name.lowercase(Locale.ROOT)}")
                            )
                        }
                    }
                }
            }

            // 3. Archive Session Cards
            if (filteredSessions.isEmpty()) {
                item(key = "history_empty") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = StocktakingPalette.CardBg,
                        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Assessment,
                                contentDescription = null,
                                tint = StocktakingPalette.TextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = Bilingual.tr(lang, "لا توجد جلسات جرد مطابقة", "Aucune session correspondante"),
                                color = StocktakingPalette.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredSessions, key = { "hist_${it.id}" }) { session ->
                    HistoryArchiveSessionCard(
                        session = session,
                        isManager = isManager,
                        currency = currency,
                        lang = lang,
                        onOpenReport = { onOpenReport(session) },
                        onOpenSession = { onOpenSession(session) },
                        onEdit = { onEditSession(session) },
                        onDelete = { onDeleteSession(session) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HistoryArchiveSessionCard(
    session: InventoryEntity,
    isManager: Boolean,
    currency: String,
    lang: String,
    onOpenReport: () -> Unit,
    onOpenSession: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val status = InventoryStatus.fromCode(session.status)
    val type = InventoryType.fromCode(session.type)
    val accuracy = StocktakingReportBuilder.computeSessionAccuracyPercent(session)
    val netColor = when {
        session.netDiscrepancyValueCents < 0L -> StocktakingPalette.Rose
        session.netDiscrepancyValueCents > 0L -> StocktakingPalette.Emerald
        else -> StocktakingPalette.TextSecondary
    }
    val signedNet = when {
        session.netDiscrepancyValueCents > 0L -> "+${MoneyUtils.format(session.netDiscrepancyValueCents, currency)}"
        session.netDiscrepancyValueCents < 0L -> "-${MoneyUtils.format(abs(session.netDiscrepancyValueCents), currency)}"
        else -> MoneyUtils.format(0L, currency)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StocktakingPalette.CardBg,
        border = BorderStroke(1.dp, StocktakingPalette.CardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_session_card_${session.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title.ifBlank { session.reference },
                        color = StocktakingPalette.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${session.reference} • ${inventoryTypeLabel(type, lang)} • ${formatStocktakingShortDate(session.createdAt)}",
                        color = StocktakingPalette.TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                StocktakingStatusBadge(status = status, lang = lang)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "معدود: ${session.countedItemsCount}/${session.totalItemsCount} • دقة: ${String.format(Locale.US, "%.0f%%", accuracy)} • فوارق: ${session.discrepancyItemsCount}",
                        "Comptés : ${session.countedItemsCount}/${session.totalItemsCount} • Préc. : ${String.format(Locale.US, "%.0f%%", accuracy)} • Écarts : ${session.discrepancyItemsCount}"
                    ),
                    color = StocktakingPalette.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = signedNet,
                    color = netColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            if (session.createdBy.isNotBlank() || !session.approvedBy.isNullOrBlank()) {
                Text(
                    text = Bilingual.tr(
                        lang,
                        "المسؤول: ${session.createdBy.ifBlank { "—" }} • المعتمد: ${session.approvedBy?.ifBlank { "—" } ?: "—"}",
                        "Responsable : ${session.createdBy.ifBlank { "—" }} • Validé par : ${session.approvedBy?.ifBlank { "—" } ?: "—"}"
                    ),
                    color = StocktakingPalette.TextMuted,
                    fontSize = 11.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenReport,
                    colors = ButtonDefaults.buttonColors(containerColor = StocktakingPalette.AccentBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("history_open_report_${session.id}")
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Bilingual.tr(lang, "التقرير الكامل", "Rapport complet"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onOpenSession,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("history_open_session_${session.id}")
                ) {
                    Icon(
                        imageVector = if (status.isEditable) Icons.Filled.Edit else Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (status.isEditable) {
                            Bilingual.tr(lang, "متابعة العدّ", "Continuer")
                        } else {
                            Bilingual.tr(lang, "فتح الجلسة", "Ouvrir")
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isManager || status.isEditable) {
                    OutlinedIconButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("history_edit_session_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = Bilingual.tr(lang, "تعديل الجرد", "Modifier"),
                            tint = StocktakingPalette.AccentBlue
                        )
                    }
                }

                if (isManager) {
                    OutlinedIconButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StocktakingPalette.Rose.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("history_delete_session_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = Bilingual.tr(lang, "حذف الجرد", "Supprimer"),
                            tint = StocktakingPalette.Rose
                        )
                    }
                }
            }
        }
    }
}
