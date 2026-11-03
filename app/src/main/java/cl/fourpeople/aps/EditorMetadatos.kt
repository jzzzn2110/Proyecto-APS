package cl.fourpeople.aps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Formulario para corregir nombre, categoría, fechas y horas del documento.
@Composable
fun EditorMetadatos(original: DocumentoFuncionario, ocupado: Boolean, error: String?,
                    onGuardar: (DocumentoFuncionario, String) -> Unit, onVolver: () -> Unit) {
    // Borrador del formulario: no modifica el documento hasta pulsar Guardar.
    var nombre by rememberSaveable(original.id) { mutableStateOf(original.nombre) }
    var categoria by rememberSaveable(original.id) { mutableStateOf(original.categoria) }
    var fecha by rememberSaveable(original.id) { mutableStateOf(original.fecha) }
    var vencimiento by rememberSaveable(original.id) { mutableStateOf(original.vencimiento) }
    var horas by rememberSaveable(original.id) { mutableStateOf(original.horas?.toString() ?: "") }
    var motivo by rememberSaveable(original.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!ocupado) onVolver() },
        title = { Text("Corregir metadatos · v${original.version}") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se conserva el archivo. El cambio crea otra versión pendiente de revisión.")
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre del documento") }, enabled = !ocupado)
                // Categorías que el usuario puede seleccionar.
                listOf("Contrato", "Anexo", "Capacitación", "Resolución", "Decreto", "Otro").forEach { opcion ->
                    FilterChip(selected = categoria == opcion, onClick = { categoria = opcion },
                        label = { Text(opcion) }, enabled = !ocupado)
                }
                OutlinedTextField(fecha, { fecha = it }, label = { Text("Fecha DD/MM/AAAA") }, enabled = !ocupado)
                OutlinedTextField(vencimiento, { vencimiento = it }, label = { Text("Vencimiento opcional") }, enabled = !ocupado)
                if (categoria == "Capacitación") OutlinedTextField(horas, { horas = it }, label = { Text("Horas") }, enabled = !ocupado)
                OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo del cambio") }, enabled = !ocupado)
                Text("Motivo obligatorio: entre 10 y 500 caracteres.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        // Envía los campos y el motivo a la pantalla administrativa para guardarlos.
        confirmButton = {
            TextButton(onClick = { onGuardar(original.copy(nombre = nombre, categoria = categoria,
                fecha = fecha, vencimiento = vencimiento, horas = horas.toIntOrNull()), motivo) },
                enabled = !ocupado) { Text("Guardar corrección") }
        },
        dismissButton = { TextButton(onClick = onVolver, enabled = !ocupado) { Text("Volver") } })
}

@Composable
// Muestra quién cambió los datos, el motivo y los valores anteriores y nuevos.
fun HistorialAdministrativo() {
    HorizontalDivider()
    Text("Historial administrativo")
    if (DatosAdministracion.estado.cambios.isEmpty()) Text("Todavía no hay cambios registrados.")
    DatosAdministracion.estado.cambios.asReversed().forEach { cambio ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${cambio.accion} · ${cambio.entidad}")
                Text("${cambio.responsable} · ${cambio.fecha}")
                Text(cambio.motivo)
                Text("Antes: ${cambio.antes}")
                Text("Después: ${cambio.despues}")
            }
        }
    }
}
