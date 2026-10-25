package com.example.mts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mts.ui.theme.Brand
import com.example.mts.ui.theme.Mts

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    HOME("홈", "홈", Icons.Filled.Home),
    WATCHLIST("관심", "관심 종목", Icons.Filled.Favorite),
    ACCOUNT("내 계좌", "내 계좌", Icons.Filled.AccountCircle),
}

@Composable
fun MtsApp(vm: MtsViewModel = viewModel()) {
    val market by vm.marketState.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val marketStatus by vm.marketStatus.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
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
            candleLabel = vm.candleLabel,
            orderBook = market.orderBooks[selected.code],
            holding = account.holdings[selected.code],
            snackbarHostState = snackbar,
            onBack = { selectedCode = null },
            onOrder = vm::placeOrder,
            maxBuyQuantity = vm::maxBuyQuantity,
            sellableQuantity = { vm.sellableQuantity(selected.code) },
        )
        return
    }

    Scaffold(
        containerColor = Mts.colors.background,
        topBar = { TopBar(tab.title, marketStatus) },
        bottomBar = {
            NavigationBar(containerColor = Mts.colors.card, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Mts.colors.textPrimary,
                            selectedTextColor = Mts.colors.textPrimary,
                            unselectedIconColor = Mts.colors.textTertiary.copy(alpha = 0.6f),
                            unselectedTextColor = Mts.colors.textTertiary,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> HomeScreen(
                stocks = vm.stocks,
                quotes = market.quotes,
                account = account,
                status = marketStatus,
                onSelect = { selectedCode = it },
                onOpenAccount = { tab = Tab.ACCOUNT },
                modifier = contentModifier,
            )
            Tab.WATCHLIST -> WatchlistScreen(
                stocks = vm.stocks,
                quotes = market.quotes,
                status = marketStatus,
                onSelect = { selectedCode = it },
                modifier = contentModifier,
            )
            Tab.ACCOUNT -> AccountScreen(
                account = account,
                quotes = market.quotes,
                onSelect = { selectedCode = it },
                onCancel = vm::cancel,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun TopBar(title: String, status: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Mts.colors.background)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Mts.colors.textPrimary, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .widthIn(max = 220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Mts.colors.card)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Brand))
            Spacer(Modifier.width(6.dp))
            Text(status, style = MaterialTheme.typography.labelMedium, color = Mts.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
