package com.example.mts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Tab(val label: String, val symbol: String) {
    WATCHLIST("관심종목", "★"),
    ACCOUNT("잔고", "₩"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MtsApp(vm: MtsViewModel = viewModel()) {
    val market by vm.marketState.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.WATCHLIST) }
    var selectedCode by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val selected = selectedCode?.let { code -> vm.stocks.find { it.code == code } }
    val selectedQuote = selected?.let { market.quotes[it.code] }

    if (selected != null && selectedQuote != null) {
        BackHandler { selectedCode = null }
        StockDetailScreen(
            stock = selected,
            quote = selectedQuote,
            candles = market.candles[selected.code].orEmpty(),
            orderBook = market.orderBooks[selected.code],
            holding = account.holdings[selected.code],
            snackbarHostState = snackbar,
            onBack = { selectedCode = null },
            onOrder = vm::placeOrder,
            maxBuyQuantity = vm::maxBuyQuantity,
            sellableQuantity = { vm.sellableQuantity(selected.code) },
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(tab.label) },
                    actions = {
                        AssistChip(onClick = {}, label = { Text("모의투자") }, modifier = Modifier.padding(end = 12.dp))
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Text(t.symbol, fontSize = 20.sp) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            when (tab) {
                Tab.WATCHLIST -> WatchlistScreen(
                    stocks = vm.stocks,
                    quotes = market.quotes,
                    onSelect = { selectedCode = it },
                    modifier = Modifier.padding(padding),
                )
                Tab.ACCOUNT -> AccountScreen(
                    account = account,
                    quotes = market.quotes,
                    onSelect = { selectedCode = it },
                    onCancel = vm::cancel,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
