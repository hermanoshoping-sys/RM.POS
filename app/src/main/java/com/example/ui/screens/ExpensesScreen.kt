package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ExpenseEntity
import com.example.ui.viewmodels.PosViewModel
import com.example.util.ActiveSort
import com.example.util.SortDirection
import com.example.util.SortingComparators

@Composable
fun ExpensesScreen(
    viewModel: PosViewModel,
    lang: String,
    onNavigate: ((String) -> Unit)? = null
) {
    BackHandler {
        onNavigate?.invoke("DASHBOARD")
    }

    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var showNewExpenseDialog by remember { mutableStateOf(false) }
    var expenseForInvoice by remember { mutableStateOf<ExpenseEntity?>(null) }
    var currentLanguage by remember { mutableStateOf(lang) }
    var expenseSortState by remember { mutableStateOf(ActiveSort(ExpenseSortCol.DATE, SortDirection.DESCENDING)) }

    val filteredExpenses = remember(expenses, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) {
            expenses
        } else {
            expenses.filter { exp ->
                exp.title.lowercase().contains(q) ||
                    exp.category.lowercase().contains(q) ||
                    "dep-${exp.id}".contains(q) ||
                    "${exp.amountDouble}".contains(q)
            }
        }
    }

    val sortedExpenses = remember(filteredExpenses, expenseSortState) {
        filteredExpenses.sortedWith { a, b ->
            val cmp = when (expenseSortState.column) {
                ExpenseSortCol.DATE -> SortingComparators.compareTimestamps(a.timestamp, b.timestamp)
                ExpenseSortCol.TITLE -> SortingComparators.compareNames(a.title, b.title)
                ExpenseSortCol.AMOUNT -> a.amount.compareTo(b.amount)
            }
            if (expenseSortState.direction == SortDirection.ASCENDING) cmp else -cmp
        }
    }

    val totalExpensesAmount = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amountDouble }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070D18))
            .imePadding()
    ) {
        ExpensesTopHeaderBar(
            currentLanguage = currentLanguage,
            username = currentUser?.username,
            onToggleLanguage = {
                currentLanguage = if (currentLanguage == "AR") "FR" else "AR"
            },
            onExit = { onNavigate?.invoke("DASHBOARD") }
        )

        Spacer(modifier = Modifier.height(6.dp))

        ExpensesActionAndSummarySection(
            currentLanguage = currentLanguage,
            currency = currency,
            filteredExpenses = filteredExpenses,
            totalExpensesAmount = totalExpensesAmount,
            showSearchBar = showSearchBar,
            searchQuery = searchQuery,
            onToggleSearch = { showSearchBar = !showSearchBar },
            onSearchChange = { searchQuery = it },
            onAddClick = { showNewExpenseDialog = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (currentLanguage == "AR") "سجل المصاريف" else "Journal des Dépenses",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        ExpensesTableSection(
            currentLanguage = currentLanguage,
            sortedExpenses = sortedExpenses,
            expenseSortState = expenseSortState,
            onSortChange = { col -> expenseSortState = expenseSortState.toggleOrSwitch(col) },
            onSelectExpense = { expenseForInvoice = it }
        )
    }

    if (showNewExpenseDialog) {
        AddExpenseVoucherDialog(
            currentLanguage = currentLanguage,
            currency = currency,
            onDismiss = { showNewExpenseDialog = false },
            onSave = { title, amt, category ->
                viewModel.saveExpense(title, amt, category)
            }
        )
    }

    expenseForInvoice?.let { exp ->
        ExpenseInvoiceDialogImage2(
            expense = exp,
            currency = currency,
            lang = currentLanguage,
            shopName = settings.shopName,
            shopPhone = settings.shopPhone,
            taxNumber = settings.taxNumber,
            viewModel = viewModel,
            onDismiss = { expenseForInvoice = null },
            onDelete = {
                viewModel.deleteExpense(exp)
                Toast.makeText(
                    context,
                    if (currentLanguage == "AR") "تم حذف المصروف بنجاح" else "Dépense supprimée",
                    Toast.LENGTH_SHORT
                ).show()
                expenseForInvoice = null
            }
        )
    }
}
