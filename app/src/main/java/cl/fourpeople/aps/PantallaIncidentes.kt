package cl.fourpeople.aps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaIncidentes(onSalir: () -> Unit, onAuditoria: () -> Unit,
                       incidenteInicialId: String? = null, eventoInicialId: String? = null, onUsuarios: () -> Unit = {}) {
    val contexto = LocalContext.current.applicationContext
    val prefs = contexto.getSharedPreferences("sesion_local", 0)
    if (!DatosSeguridad.tieneAcceso(contexto)) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Text("Se requiere una cuenta activa de TI.")
            TextButton(onClick = onSalir) { Text("Volver") }
        }
        return
    }
    val alcance = rememberCoroutineScope()
    var filtro by rememberSaveable { mutableStateOf(EstadoIncidente.ABIERTO.name) }
    var seleccionado by rememberSaveable(incidenteInicialId) { mutableStateOf(incidenteInicialId) }
    var creando by rememberSaveable(eventoInicialId) { mutableStateOf(eventoInicialId != null) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val incidentes = DatosSeguridad.estado.incidentes
    val incidente = incidentes.firstOrNull { it.id == seleccionado }

    fun ejecutar(tarea: suspend () -> Unit) {
        if (ocupado) return
        ocupado = true; error = null; aviso = null
        alcance.launch {
            try {
                withContext(NonCancellable) { tarea() }
                aviso = "Cambios guardados."
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "No se pudo guardar."
            } finally { ocupado = false }
        }
    }
    BackHandler(enabled = creando || seleccionado != null) {
        if (!ocupado) { creando = false; seleccionado = null; error = null }
    }
    Scaffold(containerColor = FondoTi,
        bottomBar = { NavegacionTi("incidentes", onAuditoria, {}, !ocupado, onUsuarios) }) { espacio ->
        Column(Modifier.fillMaxSize().padding(espacio).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Incidentes", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onSalir, enabled = !ocupado) { Text("Salir") }
            }
            Button(onClick = { creando = true; error = null }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
                Text("Nuevo incidente")
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstadoIncidente.entries.forEach { estado ->
                    val texto = when (estado) {
                        EstadoIncidente.ABIERTO -> "Abiertos (${incidentes.count { it.estado == estado }})"
                        EstadoIncidente.EN_REVISION -> "En revisión"
                        EstadoIncidente.CERRADO -> "Cerrados"
                    }
                    FilterChip(selected = filtro == estado.name, onClick = { filtro = estado.name },
                        label = { Text(texto) }, shape = RoundedCornerShape(24.dp))
                }
            }
            aviso?.let { Text(it, color = VerdeTi) }
            if (!creando && incidente == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val visibles = incidentes.filter { it.estado.name == filtro }.sortedByDescending { it.fecha }
            if (visibles.isEmpty()) Text("No hay incidentes en este estado.", color = Color(0xFF566861))
            visibles.forEach { item ->
                Card(Modifier.fillMaxWidth().clickable(enabled = !ocupado) { seleccionado = item.id; error = null; aviso = null },
                    shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Color(0xFFDEE5DF)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            EtiquetaIncidente(item.prioridad.texto, item.prioridad == PrioridadIncidente.ALTA)
                            Text(fechaIncidente(item.fecha), fontSize = 12.sp, color = Color(0xFF566861))
                            EtiquetaIncidente(item.estado.texto)
                        }
                        Text(item.titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(item.descripcion, maxLines = 3)
                        Text("Cuenta: ${item.usuario}", color = Color(0xFF566861))
                        Text((if (item.eventosIds.size == 1) "1 evento vinculado" else "${item.eventosIds.size} eventos vinculados") + " · Ver detalle", fontSize = 13.sp, color = VerdeTi)
                    }
                }
            }
        }
    }
    if (creando) {
        FormularioIncidente(eventoInicialId, ocupado, error, onCancelar = { creando = false; error = null },
            onCrear = { titulo, descripcion, usuario, prioridad, ids -> ejecutar {
                seleccionado = DatosSeguridad.crear(contexto, titulo, descripcion, usuario, prioridad, ids)
                filtro = EstadoIncidente.ABIERTO.name
                creando = false
            } })
    } else if (incidente != null) {
        var observacion by rememberSaveable(incidente.id) { mutableStateOf("") }
        var bloqueoSolicitado by rememberSaveable(incidente.id) { mutableStateOf<Boolean?>(null) }
        val bloqueada = incidente.usuario in DatosSeguridad.estado.bloqueadas
        val cuentaActiva = PerfilesCompartidos.perfiles.firstOrNull { it.usuario == incidente.usuario }?.activa == true
        AlertDialog(onDismissRequest = { if (!ocupado) { seleccionado = null; error = null } },
            title = { Text(incidente.titulo) },
            text = {
                Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EtiquetaIncidente(incidente.estado.texto)
                    Text(incidente.descripcion)
                    Text("Cuenta: ${incidente.usuario} · ${if (bloqueada) "Bloqueada" else if (cuentaActiva) "Activa" else "Inactiva"}", fontWeight = FontWeight.SemiBold)
                    if (!cuentaActiva) Text("La cuenta está desactivada en Usuarios. Quitar el bloqueo del incidente no habilita su acceso.")
                    Text("Prioridad: ${incidente.prioridad.texto} · ${fechaIncidente(incidente.fecha)}")
                    Text("Eventos vinculados", fontWeight = FontWeight.Bold)
                    if (incidente.eventosIds.isEmpty()) Text("Sin eventos vinculados.")
                    incidente.eventosIds.forEach { id ->
                        val evento = AuditoriaLocal.eventos.firstOrNull { it.id == id }
                        if (evento == null) Text("Evento no disponible: $id") else {
                            Card(colors = CardDefaults.cardColors(containerColor = FondoTi)) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${evento.accion} · ${fechaIncidente(evento.fecha)}", fontWeight = FontWeight.SemiBold)
                                    Text("${evento.usuario} · ${evento.entidad}")
                                    Text("${evento.resultado} · ${evento.detalle}", fontSize = 12.sp)
                                    Text("ID: ${evento.id}", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Text("Seguimiento", fontWeight = FontWeight.Bold)
                    incidente.seguimiento.forEach { paso ->
                        Text("${paso.accion} · ${fechaIncidente(paso.fecha)}\n${paso.responsable}: ${paso.observacion}", fontSize = 13.sp)
                    }
                    OutlinedTextField(observacion, { observacion = it }, label = { Text("Observación o motivo") },
                        minLines = 2, enabled = !ocupado, modifier = Modifier.fillMaxWidth())
                    Text("Entre 10 y 1000 caracteres.", fontSize = 12.sp)
                    if (incidente.estado != EstadoIncidente.CERRADO) {
                        Button(onClick = { ejecutar {
                            val siguiente = if (incidente.estado == EstadoIncidente.ABIERTO) EstadoIncidente.EN_REVISION else EstadoIncidente.CERRADO
                            DatosSeguridad.cambiarEstado(contexto, incidente, siguiente, observacion)
                            filtro = siguiente.name
                            observacion = ""
                        } }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
                            Text(if (incidente.estado == EstadoIncidente.ABIERTO) "Marcar revisado" else "Cerrar incidente")
                        }
                    }
                    if (incidente.usuario != prefs.getString("usuario", "")) {
                        OutlinedButton(onClick = { bloqueoSolicitado = !bloqueada }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
                            Text(if (bloqueada) "Reactivar cuenta" else "Bloquear cuenta")
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { seleccionado = null; error = null }, enabled = !ocupado) { Text("Volver a incidentes") } })
        bloqueoSolicitado?.let { bloquear ->
            AlertDialog(onDismissRequest = { if (!ocupado) bloqueoSolicitado = null },
                title = { Text(if (bloquear) "Bloquear cuenta" else "Reactivar cuenta") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (bloquear) "Se impedirá iniciar sesión a ${incidente.usuario} hasta que TI reactive la cuenta." else "Se permitirá iniciar sesión a ${incidente.usuario}.")
                    Text("Motivo: $observacion")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                } },
                confirmButton = { TextButton(onClick = { ejecutar {
                    DatosSeguridad.cambiarCuenta(contexto, incidente.id, bloquear, observacion)
                    bloqueoSolicitado = null; observacion = ""
                } }, enabled = !ocupado) { Text("Confirmar") } },
                dismissButton = { TextButton(onClick = { bloqueoSolicitado = null }, enabled = !ocupado) { Text("Cancelar") } })
        }
    }
}

@Composable
private fun FormularioIncidente(eventoInicialId: String?, ocupado: Boolean, error: String?, onCancelar: () -> Unit,
                                onCrear: (String, String, String, PrioridadIncidente, Set<String>) -> Unit) {
    val inicial = AuditoriaLocal.eventos.firstOrNull { it.id == eventoInicialId }
    var titulo by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var usuario by rememberSaveable { mutableStateOf(inicial?.usuario ?: "c.rojas") }
    var prioridad by rememberSaveable { mutableStateOf(PrioridadIncidente.MEDIA.name) }
    var ids by rememberSaveable { mutableStateOf(inicial?.let { listOf(it.id) } ?: emptyList<String>()) }
    val eventos = AuditoriaLocal.eventos.filter { it.usuario == usuario }.sortedByDescending { it.fecha }
    AlertDialog(onDismissRequest = { if (!ocupado) onCancelar() }, title = { Text("Nuevo incidente") },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(titulo, { titulo = it }, label = { Text("Título") }, enabled = !ocupado)
                OutlinedTextField(descripcion, { descripcion = it }, label = { Text("Descripción") }, minLines = 3, enabled = !ocupado)
                SelectorTi("Cuenta asociada", usuario, PerfilesCompartidos.perfiles.map { it.usuario }, Modifier.fillMaxWidth(), !ocupado) {
                    usuario = it; ids = emptyList()
                }
                SelectorTi("Prioridad", PrioridadIncidente.valueOf(prioridad).texto, PrioridadIncidente.entries.map { it.texto }, Modifier.fillMaxWidth(), !ocupado) { valor ->
                    prioridad = PrioridadIncidente.entries.first { it.texto == valor }.name
                }
                Text("Vincular eventos de esta cuenta", fontWeight = FontWeight.Bold)
                Text("Puedes registrar un incidente sin eventos si todavía no existen registros.", fontSize = 12.sp)
                if (eventos.isEmpty()) Text("No hay eventos para esta cuenta.")
                eventos.forEach { evento ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = evento.id in ids, enabled = !ocupado, onCheckedChange = { marcado ->
                            ids = if (marcado) ids + evento.id else ids - evento.id
                        })
                        Column(Modifier.weight(1f)) {
                            Text("${evento.accion} · ${fechaIncidente(evento.fecha)}", fontWeight = FontWeight.SemiBold)
                            Text(evento.entidad, fontSize = 12.sp)
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onCrear(titulo, descripcion, usuario, PrioridadIncidente.valueOf(prioridad), ids.toSet()) }, enabled = !ocupado) { Text("Registrar incidente") } },
        dismissButton = { TextButton(onClick = onCancelar, enabled = !ocupado) { Text("Cancelar") } })
}

private fun fechaIncidente(fecha: String) = LocalDateTime.parse(fecha).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))

@Composable
private fun EtiquetaIncidente(texto: String, alta: Boolean = false) {
    val verde = texto == "Cerrado"
    Surface(shape = RoundedCornerShape(20.dp), color = if (alta) Color(0xFFF9DEDA) else if (verde) Color(0xFFDDEFE6) else Color(0xFFFFEED0)) {
        Text(texto, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, color = if (alta) Color(0xFFA52D26) else if (verde) VerdeTi else Color(0xFF8B5200))
    }
}
