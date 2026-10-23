package cl.fourpeople.aps

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaNuevaSolicitud(onVolver: () -> Unit, onEnviada: (String) -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val formato = remember { DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT) }
    var tipoNombre by rememberSaveable { mutableStateOf(TipoSolicitud.ADMINISTRATIVO.name) }
    var jornadaNombre by rememberSaveable { mutableStateOf(JornadaSolicitud.COMPLETA.name) }
    var desde by rememberSaveable { mutableStateOf(LocalDate.now().format(formato)) }
    var hasta by rememberSaveable { mutableStateOf(LocalDate.now().format(formato)) }
    var motivo by rememberSaveable { mutableStateOf("") }
    var archivoUri by rememberSaveable { mutableStateOf("") }
    var nombreArchivo by rememberSaveable { mutableStateOf("") }
    var fotoPendiente by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    val bloqueado = ocupado || fotoPendiente.isNotBlank()

    suspend fun importar(origen: Uri) {
        val anterior = archivoUri
        val nuevo = ArchivosFuncionario.importar(contexto, origen)
        archivoUri = nuevo.uri
        nombreArchivo = nuevo.nombre
        ArchivosFuncionario.eliminarSinGuardar(contexto, anterior)
    }

    fun volver() {
        if (bloqueado) return
        ocupado = true
        alcance.launch {
            ArchivosFuncionario.eliminarSinGuardar(contexto, archivoUri)
            onVolver()
        }
    }

    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ocupado = true
            error = null
            alcance.launch {
                try {
                    importar(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo cargar el respaldo."
                } finally {
                    ocupado = false
                }
            }
        }
    }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tomada ->
        val temporal = fotoPendiente
        if (temporal.isNotBlank()) {
            ocupado = true
            alcance.launch {
                try {
                    if (tomada) importar(Uri.parse(temporal))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: "No se pudo procesar la fotografía."
                } finally {
                    withContext(NonCancellable) {
                        CamaraFuncionario.eliminarTemporal(contexto, temporal)
                        fotoPendiente = ""
                        ocupado = false
                    }
                }
            }
        }
    }

    BackHandler { volver() }
    Scaffold(containerColor = Color(0xFFF5F7F4)) { espacio ->
        Column(
            Modifier.fillMaxSize().padding(espacio).imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TextButton(onClick = { volver() }, enabled = !bloqueado) { Text("Volver") }
            Text("Nueva solicitud", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Tipo de solicitud", fontWeight = FontWeight.Bold)
            TipoSolicitud.entries.chunked(2).forEach { fila ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fila.forEach { tipo ->
                        FilterChip(selected = tipoNombre == tipo.name,
                            onClick = { tipoNombre = tipo.name }, enabled = !bloqueado,
                            label = { Text(tipo.texto, fontSize = 12.sp) },
                            shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = desde, onValueChange = { desde = it },
                    label = { Text("Desde") }, placeholder = { Text("DD/MM/AAAA") },
                    singleLine = true, enabled = !bloqueado, modifier = Modifier.weight(1f))
                OutlinedTextField(value = hasta, onValueChange = { hasta = it },
                    label = { Text("Hasta") }, placeholder = { Text("DD/MM/AAAA") },
                    singleLine = true, enabled = !bloqueado, modifier = Modifier.weight(1f))
            }
            Text("Jornada", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JornadaSolicitud.entries.forEach { jornada ->
                    FilterChip(selected = jornadaNombre == jornada.name,
                        onClick = { jornadaNombre = jornada.name }, enabled = !bloqueado,
                        label = { Text(jornada.texto) })
                }
            }
            OutlinedTextField(value = motivo, onValueChange = { motivo = it },
                label = { Text("Motivo") }, minLines = 3, enabled = !bloqueado,
                supportingText = { Text("${motivo.length}/500 caracteres") },
                modifier = Modifier.fillMaxWidth())
            Text("Adjuntar respaldo", fontWeight = FontWeight.Bold)
            Text("Opcional · foto o archivo PDF/imagen de hasta 20 MB.", fontSize = 12.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selector.launch(arrayOf("application/pdf", "image/*")) },
                    enabled = !bloqueado, modifier = Modifier.weight(1f)) { Text("Elegir archivo") }
                OutlinedButton(onClick = {
                    error = null
                    try {
                        val destino = CamaraFuncionario.crearDestino(contexto)
                        fotoPendiente = destino.toString()
                        camara.launch(destino)
                    } catch (e: Exception) {
                        val temporal = fotoPendiente
                        fotoPendiente = ""
                        alcance.launch { CamaraFuncionario.eliminarTemporal(contexto, temporal) }
                        error = "No se pudo abrir la cámara. Puedes adjuntar un archivo."
                    }
                }, enabled = !bloqueado, modifier = Modifier.weight(1f)) { Text("Tomar foto") }
            }
            if (archivoUri.isNotBlank()) {
                Text(nombreArchivo, fontSize = 13.sp)
                TextButton(onClick = {
                    ocupado = true
                    alcance.launch {
                        ArchivosFuncionario.eliminarSinGuardar(contexto, archivoUri)
                        archivoUri = ""
                        nombreArchivo = ""
                        ocupado = false
                    }
                }, enabled = !bloqueado) { Text("Quitar respaldo") }
            }
            Text("Cálculo demostrativo por días calendario. Media jornada corresponde a una sola fecha.",
                fontSize = 12.sp, color = Color.Gray)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (bloqueado) CircularProgressIndicator(Modifier.size(26.dp))
            Button(
                onClick = {
                    if (!bloqueado) {
                        ocupado = true
                        error = null
                        alcance.launch {
                            try {
                                val inicio = runCatching { LocalDate.parse(desde.trim(), formato) }
                                    .getOrElse { throw IllegalArgumentException("Desde inválido. Usa DD/MM/AAAA.") }
                                val fin = runCatching { LocalDate.parse(hasta.trim(), formato) }
                                    .getOrElse { throw IllegalArgumentException("Hasta inválido. Usa DD/MM/AAAA.") }
                                val nueva = SolicitudFuncionario(
                                    id = UUID.randomUUID().toString(),
                                    tipo = TipoSolicitud.valueOf(tipoNombre),
                                    desde = inicio.toString(), hasta = fin.toString(),
                                    jornada = JornadaSolicitud.valueOf(jornadaNombre),
                                    motivo = motivo.trim(), creadaEn = LocalDateTime.now().toString(),
                                    archivoUri = archivoUri
                                )
                                withContext(NonCancellable) {
                                    DatosSolicitudes.enviar(contexto.applicationContext, nueva)
                                    archivoUri = ""
                                }
                                onEnviada(nueva.id)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "No se pudo enviar la solicitud."
                            } finally {
                                ocupado = false
                            }
                        }
                    }
                },
                enabled = !bloqueado, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Enviar solicitud") }
        }
    }
}
