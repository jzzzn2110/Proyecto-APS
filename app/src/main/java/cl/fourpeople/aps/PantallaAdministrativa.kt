package cl.fourpeople.aps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// pantalla: aquí se cambian títulos, botones y distribución visual.
@Composable
fun PantallaAdministrativa(perfil: String, onSalir: () -> Unit) {

    // Contexto de Android: permite acceder al almacenamiento de la app.
    val contexto = LocalContext.current.applicationContext

    // Permite iniciar tareas de guardado desde los botones.
    val alcance = rememberCoroutineScope()

    // Lo que el usuario escribió en el buscador.
    var busqueda by rememberSaveable { mutableStateOf("") }

    // Documento abierto. null significa que no hay ninguno seleccionado.
    var documentoId by rememberSaveable { mutableStateOf<String?>(null) }

    // Abre o cierra el formulario de antecedentes laborales.
    var editandoExpediente by rememberSaveable { mutableStateOf(false) }

    // Explicación que se guarda cuando se rechaza un documento.
    var editandoMetadatos by rememberSaveable { mutableStateOf(false) }
    var motivo by rememberSaveable { mutableStateOf("") }

    // Mientras guarda, deshabilita los botones principales.
    var ocupado by remember { mutableStateOf(false) }

    // Mensaje para explicar por qué no se pudo guardar.
    var error by remember { mutableStateOf<String?>(null) }

    // Mensaje de confirmación después del guardado.
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Usa los datos compartidos; no crea otra lista de documentos.
    val expediente = DatosAdministracion.estado.expediente
    val documento = DatosFuncionario.documentos.firstOrNull { it.id == documentoId }

    // Los botones usan esta función para guardar y mostrar errores.
    fun ejecutar(tarea: suspend () -> Unit) {
        if (ocupado) return
        ocupado = true; error = null; mensaje = null
        alcance.launch {
            try {
                // Completa el guardado aunque se recree la pantalla.
                withContext(NonCancellable) { tarea() }
                documentoId = null; editandoExpediente = false; editandoMetadatos = false
                mensaje = "Cambios guardados en este dispositivo."
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "No se pudo guardar."
            } finally { ocupado = false }
        }
    }

    // inicio de la vista: títulos y contenido de la pantalla principal.
    Scaffold { espacio ->
        Column(Modifier.fillMaxSize().padding(espacio).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Gestión · $perfil", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Demostración local · un expediente ficticio")
            OutlinedButton(onClick = onSalir, enabled = !ocupado) { Text("Cerrar sesión") }
            // En este primer avance, Jefatura tiene una vista provisional.
            if (perfil == "Jefatura") {
                Text("Solicitudes recibidas: ${DatosSolicitudes.estado.solicitudes.size}")
                Text("La resolución de solicitudes se incorpora en el cuarto avance.")
            } else {
                OutlinedTextField(value = busqueda, onValueChange = { busqueda = it },
                    label = { Text("Buscar expediente por nombre, RUN o centro") }, modifier = Modifier.fillMaxWidth())
                // Busca por nombre, RUN ficticio o centro.
                val coincide = listOf(expediente.nombre, expediente.run, expediente.centro)
                    .any { it.contains(busqueda.trim(), ignoreCase = true) }
                if (!coincide) Text("No hay expedientes que coincidan.") else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(expediente.nombre, fontWeight = FontWeight.Bold)
                            Text("${expediente.run} · ${expediente.centro}")
                            Text("${expediente.cargo} · ${expediente.contrato}")
                            Text("Ingreso: ${expediente.ingreso}")
                            Button(onClick = { editandoExpediente = true; error = null }) { Text("Editar expediente") }
                        }
                    }
                    // Dibuja una tarjeta por cada documento del funcionario.
                    DatosFuncionario.documentos.forEach { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.nombre, fontWeight = FontWeight.Bold)
                                Text("${item.categoria} · ${item.estado.texto} · v${item.version}")
                                Button(onClick = { documentoId = item.id; motivo = ""; error = null; editandoMetadatos = false }) { Text("Abrir documento") }
                            }
                        }
                    }
                }
            }
            if (perfil == "Administrativo") HistorialAdministrativo()
            mensaje?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (!editandoExpediente && documento == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    // formulario: permite editar los campos laborales autorizados.
    if (editandoExpediente) {
        // Estos valores son un borrador: se guardan al pulsar Guardar expediente.
        var nombre by rememberSaveable { mutableStateOf(expediente.nombre) }
        var cargo by rememberSaveable { mutableStateOf(expediente.cargo) }
        var contrato by rememberSaveable { mutableStateOf(expediente.contrato) }
        var ingreso by rememberSaveable { mutableStateOf(expediente.ingreso) }
        AlertDialog(onDismissRequest = { if (!ocupado) editandoExpediente = false },
            title = { Text("Editar antecedentes laborales") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RUN y centro se conservan: ${expediente.run} · ${expediente.centro}")
                    OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre del funcionario") }, enabled = !ocupado)
                    OutlinedTextField(cargo, { cargo = it }, label = { Text("Cargo") }, enabled = !ocupado)
                    OutlinedTextField(contrato, { contrato = it }, label = { Text("Contrato") }, enabled = !ocupado)
                    OutlinedTextField(ingreso, { ingreso = it }, label = { Text("Ingreso DD/MM/AAAA") }, enabled = !ocupado)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { ejecutar {
                    DatosAdministracion.guardarExpediente(contexto, perfil,
                        expediente.copy(nombre = nombre, cargo = cargo, contrato = contrato, ingreso = ingreso))
                } }, enabled = !ocupado) { Text("Guardar expediente") }
            },
            dismissButton = { TextButton(onClick = { editandoExpediente = false }, enabled = !ocupado) { Text("Cancelar") } })
    }

    // Detalle: muestra el archivo y permite aprobarlo o rechazarlo.
    documento?.let { original ->
        if (editandoMetadatos) EditorMetadatos(original, ocupado, error,
            onGuardar = { propuesta, razon -> ejecutar {
                DatosAdministracion.corregirMetadatos(contexto, perfil, original, propuesta, razon)
            } }, onVolver = { editandoMetadatos = false })
        else AlertDialog(onDismissRequest = { if (!ocupado) documentoId = null },
            title = { Text(original.nombre) },
            text = {
                Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${original.categoria} · ${original.fecha} · ${original.estado.texto}")
                    original.horas?.let { Text("Horas: $it") }
                    VistaPreviaDocumento(original.archivoUri)
                    // Los botones de revisión solo aparecen para documentos pendientes.
                    OutlinedButton(onClick = { editandoMetadatos = true; error = null }, enabled = !ocupado) {
                        Text("Corregir metadatos")
                    }
                    if (original.estado == EstadoDocumento.PENDIENTE) {
                        OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo del rechazo") }, enabled = !ocupado)
                        Text("Para rechazar: motivo de 10 a 500 caracteres.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { ejecutar { DatosAdministracion.revisar(contexto, perfil, original, true, "") } },
                                enabled = !ocupado) { Text("Aprobar") }
                            OutlinedButton(onClick = { ejecutar { DatosAdministracion.revisar(contexto, perfil, original, false, motivo) } },
                                enabled = !ocupado) { Text("Rechazar") }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { documentoId = null }, enabled = !ocupado) { Text("Cerrar") } })
    }
}
