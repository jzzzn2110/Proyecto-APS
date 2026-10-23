package cl.fourpeople.aps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val VerdeSolicitudes = Color(0xFF115D55)
private val FormatoSolicitud = DateTimeFormatter.ofPattern("dd/MM/uuuu")

fun fechaSolicitud(fecha: String): String = LocalDate.parse(fecha).format(FormatoSolicitud)

@Composable
fun EtiquetaSolicitud(estado: EstadoSolicitud) {
    val (fondo, texto) = when (estado) {
        EstadoSolicitud.EN_REVISION -> Color(0xFFDCE8F7) to Color(0xFF24518A)
        EstadoSolicitud.APROBADA -> Color(0xFFDDEFE6) to VerdeSolicitudes
        EstadoSolicitud.RECHAZADA -> Color(0xFFF9E1DD) to Color(0xFFA52D26)
    }
    Surface(color = fondo, shape = RoundedCornerShape(20.dp)) {
        Text(estado.texto, color = texto, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp, 6.dp))
    }
}

@Composable
fun PantallaSolicitudes(
    onNavegar: (String) -> Unit,
    onNueva: () -> Unit,
    solicitudInicialId: String? = null
) {
    var filtro by rememberSaveable { mutableStateOf("Todas") }
    var seleccionadoId by rememberSaveable(solicitudInicialId) {
        mutableStateOf(solicitudInicialId)
    }
    val solicitudes = DatosSolicitudes.estado.solicitudes
    val visibles = solicitudes.filter {
        when (filtro) {
            "En curso" -> it.estado == EstadoSolicitud.EN_REVISION
            "Resueltas" -> it.estado != EstadoSolicitud.EN_REVISION
            else -> true
        }
    }
    BackHandler { onNavegar("inicio") }
    Scaffold(
        containerColor = Color(0xFFF5F7F4),
        bottomBar = { NavegacionFuncionario("solicitudes", onNavegar) }
    ) { espacio ->
        Column(
            modifier = Modifier.fillMaxSize().padding(espacio)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mis solicitudes", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Button(onClick = onNueva) { Text("+", fontSize = 24.sp) }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Días administrativos disponibles", color = Color.Gray)
                            val saldo = ReglasSolicitudes.saldo(solicitudes,
                                TipoSolicitud.ADMINISTRATIVO, LocalDate.now().year, false)
                            Text("${textoDias(saldo)} de 6", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Feriado legal disponible", color = Color.Gray)
                            val saldo = ReglasSolicitudes.saldo(solicitudes,
                                TipoSolicitud.FERIADO, LocalDate.now().year, false)
                            Text("${textoDias(saldo)} días", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Saldos demostrativos. Las solicitudes en revisión reservan días al enviar una nueva.",
                        fontSize = 12.sp, color = Color.Gray)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todas", "En curso", "Resueltas").forEach { opcion ->
                    FilterChip(selected = filtro == opcion, onClick = { filtro = opcion },
                        label = { Text(opcion) }, shape = RoundedCornerShape(24.dp))
                }
            }
            if (visibles.isEmpty()) Text("No hay solicitudes en esta categoría.", color = Color.Gray)
            visibles.forEach { solicitud ->
                Card(modifier = Modifier.fillMaxWidth().clickable { seleccionadoId = solicitud.id },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(solicitud.tipo.texto, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${fechaSolicitud(solicitud.desde)} al ${fechaSolicitud(solicitud.hasta)}",
                            fontSize = 13.sp, color = Color.Gray)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${textoDias(solicitud.dias)} días", fontSize = 13.sp)
                            EtiquetaSolicitud(solicitud.estado)
                        }
                    }
                }
            }
            Text("Revisión de un nivel: jefatura del centro. Datos y estados simulados.",
                fontSize = 12.sp, color = Color.Gray)
        }
    }
    solicitudes.firstOrNull { it.id == seleccionadoId }?.let { solicitud ->
        AlertDialog(
            onDismissRequest = { seleccionadoId = null },
            title = { Text(solicitud.tipo.texto) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    EtiquetaSolicitud(solicitud.estado)
                    Text("Folio: ${solicitud.id}", fontSize = 12.sp)
                    Text("Desde: ${fechaSolicitud(solicitud.desde)}")
                    Text("Hasta: ${fechaSolicitud(solicitud.hasta)}")
                    Text("Duración: ${textoDias(solicitud.dias)} días")
                    Text(solicitud.jornada.texto)
                    Text("Motivo", fontWeight = FontWeight.Bold)
                    Text(solicitud.motivo)
                    if (solicitud.respuesta.isNotBlank()) {
                        Text("Respuesta de jefatura", fontWeight = FontWeight.Bold)
                        Text(solicitud.respuesta)
                    }
                    if (solicitud.archivoUri.isNotBlank()) {
                        Text("Respaldo adjunto", fontWeight = FontWeight.Bold)
                        VistaPreviaDocumento(solicitud.archivoUri)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { seleccionadoId = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
fun PantallaSolicitudEnviada(id: String?, onVerSolicitudes: () -> Unit, onInicio: () -> Unit) {
    val solicitud = DatosSolicitudes.estado.solicitudes.firstOrNull { it.id == id }
    BackHandler(onBack = onVerSolicitudes)
    Scaffold(containerColor = Color(0xFFF5F7F4)) { espacio ->
        Column(
            Modifier.fillMaxSize().padding(espacio).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Surface(color = Color(0xFFDDEFE6), shape = RoundedCornerShape(60.dp)) {
                Text("✓", fontSize = 48.sp, color = VerdeSolicitudes, modifier = Modifier.padding(24.dp))
            }
            Text("Solicitud enviada", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Registrada en esta demostración y pendiente de revisión de jefatura.", color = Color.Gray)
            if (solicitud != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(solicitud.tipo.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        EtiquetaSolicitud(solicitud.estado)
                        Text("${fechaSolicitud(solicitud.desde)} al ${fechaSolicitud(solicitud.hasta)}")
                        Text("Duración: ${textoDias(solicitud.dias)} días")
                        val enviada = LocalDateTime.parse(solicitud.creadaEn)
                            .format(DateTimeFormatter.ofPattern("dd/MM/uuuu · HH:mm"))
                        Text("Enviada: $enviada")
                        Text("Revisa: Jefatura del centro")
                        Text("Folio: ${solicitud.id}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            Button(onClick = onVerSolicitudes, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Ver mis solicitudes")
            }
            OutlinedButton(onClick = onInicio, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Volver al inicio")
            }
        }
    }
}
