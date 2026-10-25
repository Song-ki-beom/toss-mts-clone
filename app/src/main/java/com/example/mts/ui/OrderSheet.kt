package com.example.mts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mts.data.OrderType
import com.example.mts.data.Quote
import com.example.mts.data.Side
import com.example.mts.data.Stock
import com.example.mts.data.tickSize
import com.example.mts.ui.theme.DownBlue
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
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("${stock.name} 주문", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Side.entries.forEachIndexed { i, s ->
                    val c = if (s == Side.BUY) UpRed else DownBlue
                    SegmentedButton(
                        selected = side == s,
                        onClick = { side = s },
                        shape = SegmentedButtonDefaults.itemShape(i, Side.entries.size),
                        colors = SegmentedButtonDefaults.colors(activeContainerColor = c.copy(alpha = 0.15f), activeContentColor = c),
                    ) { Text(s.label) }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                OrderType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { type = t },
                        shape = SegmentedButtonDefaults.itemShape(i, OrderType.entries.size),
                    ) { Text(t.label) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("가격", Modifier.weight(1f))
                OutlinedButton(
                    onClick = { price = (price - tickSize(price - 1)).coerceAtLeast(1) },
                    enabled = type == OrderType.LIMIT,
                ) { Text("−") }
                Text(
                    if (type == OrderType.MARKET) "시장가" else "${formatPrice(price)}원",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = { price += tickSize(price) },
                    enabled = type == OrderType.LIMIT,
                ) { Text("+") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { text -> quantityText = text.filter(Char::isDigit).take(7) },
                    label = { Text("수량 (주)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { quantityText = available.toString() }) { Text("최대") }
            }
            Text(
                "${if (side == Side.BUY) "주문가능" else "매도가능"} ${formatPrice(available)}주",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("예상 주문금액")
                Text("${formatPrice(effectivePrice * quantity)}원", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onSubmit(side, type, price, quantity) },
                enabled = quantity > 0,
                colors = ButtonDefaults.buttonColors(containerColor = sideColor),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("${side.label} 주문", fontSize = 17.sp) }
        }
    }
}
