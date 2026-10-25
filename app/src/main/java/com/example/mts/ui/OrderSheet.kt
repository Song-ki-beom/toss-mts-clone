package com.example.mts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mts.data.OrderType
import com.example.mts.data.Quote
import com.example.mts.data.Side
import com.example.mts.data.Stock
import com.example.mts.data.tickSize
import com.example.mts.ui.theme.DownBlue
import com.example.mts.ui.theme.Mts
import com.example.mts.ui.theme.UpRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderSheet(
    stock: Stock,
    quote: Quote,
    draft: OrderDraft,
    maxBuyQuantity: (Long) -> Long,
    sellableQuantity: () -> Long,
    onDismiss: () -> Unit,
    onSubmit: (Side, OrderType, Long, Long) -> Unit,
) {
    var side by remember { mutableStateOf(draft.side) }
    var type by remember { mutableStateOf(OrderType.LIMIT) }
    var price by remember { mutableLongStateOf(draft.price) }
    var quantityText by remember { mutableStateOf("1") }

    val quantity = quantityText.toLongOrNull() ?: 0L
    val effectivePrice = if (type == OrderType.MARKET) quote.price else price
    val available = if (side == Side.BUY) maxBuyQuantity(effectivePrice) else sellableQuantity()
    val sideColor = if (side == Side.BUY) UpRed else DownBlue

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Mts.colors.card,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("${stock.name} ${side.label}하기", style = MaterialTheme.typography.titleLarge, color = Mts.colors.textPrimary)

            PillToggle(
                options = Side.entries,
                selected = side,
                label = { it.label },
                onSelect = { side = it },
                activeColor = { if (it == Side.BUY) UpRed else DownBlue },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("가격", style = MaterialTheme.typography.labelLarge, color = Mts.colors.textSecondary, modifier = Modifier.weight(1f))
                    PillToggle(
                        options = OrderType.entries,
                        selected = type,
                        label = { it.label },
                        onSelect = { type = it },
                        modifier = Modifier.size(width = 150.dp, height = 40.dp),
                    )
                }
                Stepper(
                    value = if (type == OrderType.MARKET) "시장가로 바로" else formatWon(price),
                    enabled = type == OrderType.LIMIT,
                    onMinus = { price = (price - tickSize(price - 1)).coerceAtLeast(1) },
                    onPlus = { price += tickSize(price) },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("수량", style = MaterialTheme.typography.labelLarge, color = Mts.colors.textSecondary, modifier = Modifier.weight(1f))
                    Text(
                        "${if (side == Side.BUY) "구매" else "판매"} 가능 ${formatPrice(available)}주",
                        style = MaterialTheme.typography.labelMedium,
                        color = Mts.colors.textTertiary,
                    )
                }
                StepperBox(
                    onMinus = { quantityText = (quantity - 1).coerceAtLeast(0).toString() },
                    onPlus = { quantityText = (quantity + 1).toString() },
                ) {
                    BasicTextField(
                        value = quantityText,
                        onValueChange = { text -> quantityText = text.filter(Char::isDigit).take(7) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = Mts.colors.textPrimary, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(sideColor),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10 to "10%", 25 to "25%", 50 to "50%", 100 to "최대").forEach { (pct, label) ->
                        QuickChip(label, Modifier.weight(1f)) { quantityText = (available * pct / 100).toString() }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Mts.colors.chip)
                    .padding(vertical = 10.dp),
            ) {
                InfoRow("주문 방식", type.label)
                InfoRow("총 주문 금액", formatWon(effectivePrice * quantity), bold = true)
            }

            Button(
                onClick = { onSubmit(side, type, price, quantity) },
                enabled = quantity > 0,
                colors = ButtonDefaults.buttonColors(containerColor = sideColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("${formatPrice(quantity)}주 ${side.label}하기", style = MaterialTheme.typography.titleSmall, color = Color.White) }
        }
    }
}

@Composable
private fun Stepper(value: String, enabled: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    StepperBox(onMinus = onMinus, onPlus = onPlus, enabled = enabled) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) Mts.colors.textPrimary else Mts.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StepperBox(onMinus: () -> Unit, onPlus: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Mts.colors.chip)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton("−", enabled, onMinus)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
        RoundButton("+", enabled, onPlus)
    }
}

@Composable
private fun RoundButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (enabled) Mts.colors.card else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = if (enabled) Mts.colors.textPrimary else Mts.colors.textTertiary.copy(alpha = 0.5f))
    }
}

@Composable
private fun QuickChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Mts.colors.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Mts.colors.textSecondary)
    }
}
