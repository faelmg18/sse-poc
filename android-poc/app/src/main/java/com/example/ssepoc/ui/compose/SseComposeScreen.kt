package com.example.ssepoc.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ssepoc.data.CheckoutEvent
import com.example.ssepoc.data.PaymentInfo
import com.example.ssepoc.data.SseEvent
import com.example.ssepoc.viewmodel.SseViewModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

private val gson = Gson()

@Composable
fun SseComposeScreen(modifier: Modifier = Modifier, vm: SseViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.events.size) {
        if (state.events.isNotEmpty()) {
            listState.animateScrollToItem(state.events.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "SSE POC — Compose", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        StatusBadge(isConnected = state.isConnected)
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.connect() }, enabled = !state.isConnected) {
                Text("Conectar")
            }
            OutlinedButton(onClick = { vm.disconnect() }, enabled = state.isConnected) {
                Text("Desconectar")
            }
            TextButton(onClick = { vm.clearEvents() }) {
                Text("Limpar")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        state.error?.let {
            Text(
                text = "Erro: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.events.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum evento recebido ainda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(state.events) { event ->
                CheckoutEventCard(event)
            }
        }
    }
}

@Composable
private fun CheckoutEventCard(event: SseEvent) {
    // Tenta parsear como CheckoutEvent; só usa o objeto se tiver campos significativos
    val checkout = event.data?.let {
        try {
            val parsed = gson.fromJson(it, CheckoutEvent::class.java)
            // considera válido se tiver pelo menos id ou status (campos do servidor real)
            if (parsed?.id != null || parsed?.status != null) parsed else null
        } catch (e: JsonSyntaxException) { null }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (checkout == null) {
                // Payload simples (servidor local) — mostra event type + data como texto
                event.event?.let {
                    Text(text = it, fontSize = 10.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                }
                Text(text = event.data ?: "", fontSize = 12.sp)
                return@Column
            }

            // Header com status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = checkout.type ?: "EVENT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                StatusChip(checkout.status)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Dados principais
            EventRow("ID", checkout.id)
            EventRow("Reference", checkout.referenceId)
            EventRow("Owner", checkout.ownerName)
            EventRow("Transaction", checkout.transactionId)
            EventRow("Prev. Status", checkout.previousStatus)
            EventRow("Amount", checkout.amount?.let { "R$ $it" })
            EventRow("Total", checkout.totalAmount?.let { "R$ $it" })
            EventRow("Items", checkout.itemsQuantity?.toString())
            EventRow("Created", checkout.createdAt)
            EventRow("Updated", checkout.updatedAt)

            // Payment info
            checkout.paymentInfo?.let { p ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Payment Info",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                EventRow("Method", p.method)
                EventRow("Amount", p.amount?.let { "R$ $it" })
                EventRow("Amount to Pay", p.amountToPay?.let { "R$ $it" })
                EventRow("Installments", p.installments?.toString())
                EventRow("Player", p.player)
                EventRow("PSP Ref", p.pspReference)
                EventRow("Card Brand", p.cardBrand)
                EventRow("Pix EMV", p.pixEmv)
                EventRow("Pix Exp (s)", p.pixExpirationTimeInSeconds?.toString())
            }
        }
    }
}

@Composable
private fun EventRow(label: String, value: String?) {
    if (value == null) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray,
            modifier = Modifier.width(110.dp),
        )
        Text(text = value, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusChip(status: String?) {
    val color = when (status) {
        "PAID" -> Color(0xFF4CAF50)
        "PROCESSING_PAYMENT" -> Color(0xFFFF9800)
        "CANCELLED", "FAILED" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text = status ?: "-",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun StatusBadge(isConnected: Boolean) {
    val color = if (isConnected) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
    val label = if (isConnected) "Conectado" else "Desconectado"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
