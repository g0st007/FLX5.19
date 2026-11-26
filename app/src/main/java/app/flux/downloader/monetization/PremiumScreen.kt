package app.flux.downloader.monetization

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flux.downloader.ui.FluxOrange
import kotlinx.coroutines.launch
import java.util.Locale

private fun usd(v: Double): String = "\$" + String.format(Locale.US, "%.2f", v)

/** Pantalla de Flux Premium (planes mensual y anual). Referencia de diseño: la hoja de planes de Aloha. */
@Composable
fun PremiumScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val premium by PremiumState.isPremium.collectAsStateWithLifecycle()
    var annual by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf(false) }
    val debuggable = remember { (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }

    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFF6A00), Color(0xFF4A1F00), Color(0xFF0A0A0A)))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.clip(CircleShape).background(Color(0x66000000)),
            ) { Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White) }

            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(12.dp))
            Text(
                "Flux Premium",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (premium) "Ya eres Premium. ¡Gracias por apoyar a Flux!" else "Sin anuncios y sin límites",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFFFE0CC),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99000000))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Benefit(Icons.Default.Download, "Descargas sin anuncios en todas las calidades")
                Benefit(Icons.Default.Block, "Sin anuncios en toda la app")
                Benefit(Icons.Default.Lock, "Casillas ilimitadas en la carpeta privada")
                Benefit(Icons.Default.Palette, "Ícono blanco de Flux para tu pantalla de inicio")
                if (more) {
                    Benefit(Icons.Default.Star, "Apoyas el desarrollo de Flux")
                }
                TextButton(onClick = { more = !more }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (more) "Menos beneficios" else "Más beneficios", color = FluxOrange)
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanCard(
                    title = "Anual",
                    price = usd(PremiumPlans.ANNUAL_USD) + "/año",
                    sub = usd(PremiumPlans.annualPerMonthUsd) + "/mes",
                    badge = "Ahorra ${PremiumPlans.annualDiscountPercent}%",
                    selected = annual,
                    modifier = Modifier.weight(1f),
                    onClick = { annual = true },
                )
                PlanCard(
                    title = "Mensual",
                    price = usd(PremiumPlans.MONTHLY_USD) + "/mes",
                    sub = null,
                    badge = null,
                    selected = !annual,
                    modifier = Modifier.weight(1f),
                    onClick = { annual = false },
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val product = if (annual) PremiumPlans.PRODUCT_ANNUAL else PremiumPlans.PRODUCT_MONTHLY
                        val msg = when (val r = Billing.provider.purchase(Ads.activity, product)) {
                            PurchaseResult.Success -> { PremiumState.setPremium(true); "¡Ya eres Premium!" }
                            PurchaseResult.Cancelled -> null
                            PurchaseResult.NotAvailable -> "Las suscripciones estarán disponibles muy pronto."
                            is PurchaseResult.Failed -> r.message
                        }
                        if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !premium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FluxOrange, contentColor = Color.White),
            ) { Text(if (premium) "Premium activo" else "Comprar", style = MaterialTheme.typography.titleMedium) }

            Text(
                "Suscripción con renovación automática. Puedes cancelarla en cualquier momento desde Google Play.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBBBBBB),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )

            // Solo en versiones de desarrollo: permite probar la app como Premium sin comprar nada
            if (debuggable) {
                TextButton(
                    onClick = { PremiumState.setPremium(!premium) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text(if (premium) "Desactivar Premium (solo pruebas)" else "Activar Premium (solo pruebas)", color = Color.White) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Benefit(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = FluxOrange, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    sub: String?,
    badge: String?,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x99000000))
            .border(2.dp, if (selected) FluxOrange else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFFDDDDDD), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FluxOrange, modifier = Modifier.size(20.dp))
        }
        Text(price, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = Color(0xFFBBBBBB), style = MaterialTheme.typography.bodySmall)
        if (badge != null) {
            Text(
                badge,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(FluxOrange)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
