package cl.fourpeople.aps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AvisoFuncionario(
    val id: String,
    val titulo: String,
    val detalle: String,
    val grupo: String,
    val destinoId: String,
    val color: Color,
    val simbolo: String
)

fun avisosFuncionario(hoy: LocalDate = LocalDate.now()): List<AvisoFuncionario> {
    val avisos = mutableListOf<AvisoFuncionario>()
    val formato = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    DatosFuncionario.documentos.forEach { documento ->
        val base = "documento:${documento.id}:${documento.version}"
        when (documento.estado) {
            EstadoDocumento.RECHAZADO -> avisos.add(AvisoFuncionario(
                "$base:rechazado", "Documento rechazado",
                "«${documento.nombre}» fue rechazado. Toca para ver el motivo.",
                "DOCUMENTOS", documento.id, Color(0xFFA52D26), "×"))
            EstadoDocumento.PENDIENTE -> avisos.add(AvisoFuncionario(
                "$base:pendiente", "Documento pendiente",
                "«${documento.nombre}» está pendiente de validación.",
                "DOCUMENTOS", documento.id, Color(0xFF24518A), "▤"))
            EstadoDocumento.APROBADO -> Unit
        }
        val vencimiento = runCatching { LocalDate.parse(documento.vencimiento, formato) }.getOrNull()
        if (vencimiento != null) {
            val dias = ChronoUnit.DAYS.between(hoy, vencimiento)
            if (dias <= 30) {
                avisos.add(AvisoFuncionario(
                    "$base:vencimiento:$vencimiento:${if (dias < 0) "vencido" else "proximo"}",
                    if (dias < 0) "Documento vencido" else "Documento por vencer",
                    when {
                        dias < 0 -> "«${documento.nombre}» venció el ${documento.vencimiento}."
                        dias == 0L -> "«${documento.nombre}» vence hoy."
                        else -> "«${documento.nombre}» vence en $dias días."
                    }, "DOCUMENTOS", documento.id, Color(0xFF8B5200), "◷"
                ))
            }
        }
    }
    DatosSolicitudes.estado.solicitudes.forEach { solicitud ->
        val (titulo, detalle, color, simbolo) = when (solicitud.estado) {
            EstadoSolicitud.EN_REVISION -> listOf(
                "Solicitud registrada", "Tu ${solicitud.tipo.texto.lowercase()} está en revisión.", "pendiente", "◷")
            EstadoSolicitud.APROBADA -> listOf(
                "Solicitud aprobada", "Tu ${solicitud.tipo.texto.lowercase()} fue aprobado.", "aprobada", "✓")
            EstadoSolicitud.RECHAZADA -> listOf(
                "Solicitud rechazada", "Tu ${solicitud.tipo.texto.lowercase()} fue rechazado. Toca para ver el motivo.", "rechazada", "×")
        }
        avisos.add(AvisoFuncionario(
            "solicitud:${solicitud.id}:${solicitud.estado.name}", titulo, detalle,
            "SOLICITUDES", solicitud.id,
            when (color) {
                "aprobada" -> Color(0xFF115D55)
                "rechazada" -> Color(0xFFA52D26)
                else -> Color(0xFF24518A)
            }, simbolo
        ))
    }
    return avisos
}

@Composable
fun PantallaAvisos(onNavegar: (String) -> Unit, onAbrir: (AvisoFuncionario) -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val avisos = avisosFuncionario()
    val leidos = DatosSolicitudes.estado.avisosLeidos
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun marcar(ids: Set<String>, despues: () -> Unit = {}) {
        if (ocupado) return
        ocupado = true
        error = null
        alcance.launch {
            try {
                withContext(NonCancellable) {
                    DatosSolicitudes.marcarLeidos(contexto.applicationContext, ids)
                }
                despues()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudo marcar el aviso como leído."
            } finally {
                ocupado = false
            }
        }
    }

    BackHandler { onNavegar("inicio") }
    Scaffold(containerColor = Color(0xFFF5F7F4),
        bottomBar = { NavegacionFuncionario("avisos", onNavegar) }) { espacio ->
        Column(Modifier.fillMaxSize().padding(espacio).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Avisos", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { marcar(avisos.map { it.id }.toSet()) },
                    enabled = !ocupado && avisos.any { it.id !in leidos }) { Text("Marcar como leídos", fontSize = 12.sp) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (avisos.isEmpty()) Text("No tienes avisos por ahora.", color = Color.Gray)
            listOf("DOCUMENTOS", "SOLICITUDES").forEach { grupo ->
                val elementos = avisos.filter { it.grupo == grupo }
                if (elementos.isNotEmpty()) Text(grupo, color = Color(0xFF566861), fontWeight = FontWeight.Bold)
                elementos.forEach { aviso ->
                    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !ocupado) {
                        marcar(setOf(aviso.id)) { onAbrir(aviso) }
                    }, colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(aviso.color.copy(alpha = 0.13f), CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text(aviso.simbolo, color = aviso.color, fontSize = 24.sp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(aviso.titulo, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(aviso.detalle, color = Color(0xFF566861), fontSize = 14.sp)
                            }
                            if (aviso.id !in leidos) {
                                Box(Modifier.padding(top = 6.dp).size(9.dp).background(Color(0xFFBF420C), CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}
