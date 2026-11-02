package cl.fourpeople.aps

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val FondoTi = Color(0xFFF5F7F4)
private val VerdeTi = Color(0xFF115D55)

@Composable
fun PantallaAuditoria(onSalir: () -> Unit) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        primary = VerdeTi, secondaryContainer = Color(0xFFDDEFEA), onSecondaryContainer = VerdeTi,
        outline = Color(0xFFCBD4CE), outlineVariant = Color(0xFFDEE5DF)
    )) { ContenidoAuditoria(onSalir) }
}

@Composable
private fun ContenidoAuditoria(onSalir: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val esTi = contexto.getSharedPreferences("sesion_local", 0).getString("rol", "") == "TI / Admin"
    if (!esTi) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text("Se requiere una sesión de TI para consultar la auditoría.")
            TextButton(onClick = onSalir) { Text("Volver") }
        }
        return
    }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var centro by rememberSaveable { mutableStateOf("Todos") }
    var categoria by rememberSaveable { mutableStateOf("Todos") }
    var accion by rememberSaveable { mutableStateOf("Todas") }
    var periodo by rememberSaveable { mutableStateOf("Últimos 7 días") }
    var documentoId by rememberSaveable { mutableStateOf<String?>(null) }
    var verBajas by rememberSaveable { mutableStateOf(false) }
    var csvPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var exportando by remember { mutableStateOf(false) }
    val eventos = AuditoriaLocal.eventos
    val filtrados = FiltroAuditoria(busqueda, centro, categoria, accion, periodo).aplicar(eventos)
    val historial = documentoId?.let { id -> eventos.filter { it.entidadId == id }.sortedBy { it.fecha } }
    val documento = DatosFuncionario.documentos.firstOrNull { it.id == documentoId }
    val seleccionHistorial = documentoId
    val seleccionBajas = verBajas

    val exportador = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val archivo = csvPendiente?.let { File(contexto.cacheDir, it) }
        csvPendiente = null
        if (uri != null && archivo != null) {
            exportando = true
            alcance.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val salida = contexto.contentResolver.openOutputStream(uri, "wt")
                            ?: error("No se pudo abrir el destino.")
                        salida.use { destino -> archivo.inputStream().use { it.copyTo(destino) } }
                    }
                    mensaje = "Reporte de auditoría exportado."
                } catch (e: CancellationException) { throw e
                } catch (_: Exception) { mensaje = "No se pudo exportar el reporte. Intenta nuevamente."
                } finally { archivo.delete(); exportando = false }
            }
        } else archivo?.delete()
    }
    fun exportar(lista: List<EventoAuditoria>) {
        exportando = true
        alcance.launch {
            val archivo = File(contexto.cacheDir, "auditoria-${UUID.randomUUID()}.csv")
            try {
                withContext(Dispatchers.IO) { archivo.writeText(CsvAuditoria.generar(lista), Charsets.UTF_8) }
                csvPendiente = archivo.name
                exportador.launch("auditoria-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv")
            } catch (e: CancellationException) { archivo.delete(); throw e
            } catch (_: Exception) {
                archivo.delete()
                csvPendiente = null
                mensaje = "No se pudo preparar la exportación. Intenta nuevamente."
            } finally { exportando = false }
        }
    }
    BackHandler(enabled = documentoId != null || verBajas) { documentoId = null; verBajas = false }
    Scaffold(containerColor = FondoTi,
        bottomBar = {
            if (seleccionHistorial != null) {
                Surface(color = Color.White) {
                    Button(onClick = { exportar(historial.orEmpty()) }, enabled = !exportando && csvPendiente == null,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 54.dp),
                        shape = RoundedCornerShape(18.dp)) { Text("Exportar reporte de auditoría") }
                }
            } else {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(selected = true, onClick = { verBajas = false },
                        icon = { IconoTi("auditoria") }, label = { Text("Auditoría") })
                    NavigationBarItem(selected = false, enabled = false, onClick = {},
                        icon = { IconoTi("incidentes") }, label = { Text("Incidentes") })
                    NavigationBarItem(selected = false, enabled = false, onClick = {},
                        icon = { IconoTi("usuarios") }, label = { Text("Usuarios") })
                }
            }
        }) { espacio ->
        Column(Modifier.fillMaxSize().padding(espacio).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (seleccionHistorial != null || seleccionBajas) {
                    OutlinedButton(onClick = { documentoId = null; verBajas = false }) { Text("‹") }
                    Spacer(Modifier.width(12.dp))
                } else {
                    Box(Modifier.size(48.dp).background(Color(0xFF17241F), CircleShape), contentAlignment = Alignment.Center) {
                        IconoTi("auditoria", Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(if (seleccionHistorial != null) "Historial del documento" else if (seleccionBajas) "Bajas documentales" else "Auditoría",
                        fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    if (seleccionHistorial == null && !seleccionBajas) Text("Diego Herrera · TI / Administrador", color = Color(0xFF566861), fontSize = 13.sp)
                }
                if (seleccionHistorial == null && !seleccionBajas) TextButton(onClick = onSalir) { Text("Salir") }
            }
            mensaje?.let { Text(it, color = VerdeTi) }
            if (exportando) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                seleccionHistorial != null -> {
                    TarjetaTi {
                        Text(documento?.nombre ?: historial?.firstOrNull()?.entidad ?: seleccionHistorial, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Titular: ${historial?.firstOrNull()?.titular ?: DatosAdministracion.estado.expediente.nombre}")
                        Text("ID documento: $seleccionHistorial")
                        Text("Estado: ${if (!DatosAdministracion.activo(seleccionHistorial)) "Baja documental" else documento?.estado?.texto ?: "Sin información"}")
                        Text("Ubicación: almacenamiento local del MVP")
                    }
                    if (historial.isNullOrEmpty()) Text("No hay eventos registrados para este documento.")
                    historial.orEmpty().forEach { evento ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(10.dp).background(VerdeTi, CircleShape))
                                Box(Modifier.width(2.dp).height(68.dp).background(Color(0xFFDEE5DF)))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${evento.accion} · ${fechaEvento(evento.fecha)}", fontWeight = FontWeight.Bold)
                                Text("${evento.usuario} · ${evento.rol} · Android", color = Color(0xFF566861))
                                if (evento.detalle.isNotBlank()) Text(evento.detalle, fontSize = 13.sp)
                                Text(evento.resultado, color = VerdeTi, fontSize = 13.sp)
                            }
                        }
                    }
                }
                seleccionBajas -> {
                    if (DatosAdministracion.estado.bajas.isEmpty()) Text("No hay bajas documentales registradas.")
                    DatosAdministracion.estado.bajas.asReversed().forEach { baja ->
                        val d = DatosFuncionario.documentos.firstOrNull { it.id == baja.documentoId }
                        TarjetaTi {
                            Text(d?.nombre ?: baja.documentoId, fontWeight = FontWeight.Bold)
                            Text("${baja.responsable} · ${fechaEvento(baja.fecha)}")
                            Text(baja.motivo)
                            Text("Archivo y versiones conservados")
                            TextButton(onClick = { documentoId = baja.documentoId }) { Text("Ver historial") }
                        }
                    }
                }
                else -> {
                    OutlinedTextField(busqueda, { busqueda = it }, label = { Text("Funcionario, RUN o documento") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectorTi("Centro", centro, listOf("Todos") + eventos.map { it.centro }.distinct().sorted(), Modifier.weight(1f)) { centro = it }
                        SelectorTi("Tipo", categoria, listOf("Todos") + eventos.map { it.categoria }.distinct().sorted(), Modifier.weight(1f)) { categoria = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectorTi("Acción", accion, listOf("Todas") + eventos.map { it.accion }.distinct().sorted(), Modifier.weight(1f)) { accion = it }
                        SelectorTi("Período", periodo, listOf("Hoy", "Últimos 7 días", "Últimos 30 días", "Todo el historial"), Modifier.weight(1f)) { periodo = it }
                    }
                    OutlinedButton(onClick = { verBajas = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Consultar bajas documentales (${DatosAdministracion.estado.bajas.size})")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Actividad reciente", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { exportar(filtrados) }, enabled = !exportando && csvPendiente == null) { Text("Exportar") }
                    }
                    Text("${filtrados.size} " + if (filtrados.size == 1) "evento" else "eventos", color = Color(0xFF566861))
                    if (filtrados.isEmpty()) Text("No hay eventos que coincidan con los filtros. Las nuevas acciones realizadas en la aplicación aparecerán aquí.")
                    filtrados.forEach { evento ->
                        val esDocumento = DatosFuncionario.documentos.any { it.id == evento.entidadId }
                        EventoTi(evento, esDocumento) { documentoId = evento.entidadId }
                    }
                }
            }
        }
    }
}

private fun fechaEvento(fecha: String) = LocalDateTime.parse(fecha).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

@Composable
private fun EventoTi(evento: EventoAuditoria, abrir: Boolean, onAbrir: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = abrir, onClick = onAbrir),
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${evento.accion} · ${fechaEvento(evento.fecha)}", color = VerdeTi, fontWeight = FontWeight.Bold)
            Text("${evento.usuario} · ${evento.entidad}", fontWeight = FontWeight.SemiBold)
            Text("${evento.rol} · Android · ${evento.resultado}", fontSize = 13.sp, color = Color(0xFF566861))
            if (evento.detalle.isNotBlank()) Text(evento.detalle, fontSize = 13.sp)
            if (abrir) Text("Ver historial del documento ›", color = VerdeTi, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TarjetaTi(contenido: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
    }
}

@Composable
private fun SelectorTi(titulo: String, valor: String, opciones: List<String>, modifier: Modifier, onElegir: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(titulo, fontWeight = FontWeight.SemiBold, color = Color(0xFF566861))
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(valor, modifier = Modifier.weight(1f)); Text("⌄")
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEach { opcion -> DropdownMenuItem(text = { Text(opcion) }, onClick = { onElegir(opcion); abierto = false }) }
            }
        }
    }
}

@Composable
private fun IconoTi(tipo: String, color: Color = LocalContentColor.current) {
    Canvas(Modifier.size(25.dp)) {
        val trazo = Stroke(width = 2.dp.toPx())
        val w = size.width
        val h = size.height
        when (tipo) {
            "auditoria" -> drawPath(Path().apply {
                moveTo(w * .5f, h * .08f); lineTo(w * .85f, h * .22f)
                lineTo(w * .85f, h * .55f); quadraticTo(w * .85f, h * .8f, w * .5f, h * .95f)
                quadraticTo(w * .15f, h * .8f, w * .15f, h * .55f)
                lineTo(w * .15f, h * .22f); close()
            }, color, style = trazo)
            "incidentes" -> {
                drawPath(Path().apply { moveTo(w / 2, h * .08f); lineTo(w * .95f, h * .9f); lineTo(w * .05f, h * .9f); close() }, color, style = trazo)
                drawLine(color, Offset(w / 2, h * .35f), Offset(w / 2, h * .6f), strokeWidth = 2.dp.toPx())
                drawCircle(color, 1.2f.dp.toPx(), Offset(w / 2, h * .74f))
            }
            else -> {
                drawCircle(color, w * .14f, Offset(w * .4f, h * .28f), style = trazo)
                drawCircle(color, w * .12f, Offset(w * .78f, h * .35f), style = trazo)
                drawOval(color, topLeft = Offset(w * .08f, h * .52f), size = androidx.compose.ui.geometry.Size(w * .62f, h * .43f), style = trazo)
                drawLine(color, Offset(w * .8f, h * .6f), Offset(w * .92f, h * .88f), strokeWidth = 2.dp.toPx())
            }
        }
    }
}
