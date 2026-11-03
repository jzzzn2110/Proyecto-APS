package cl.fourpeople.aps

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Muestra las bajas y permite consultar el archivo que se conservó.
@Composable
fun BajasAdministrativas() {
    HorizontalDivider()
    Text("Bajas documentales")
    if (DatosAdministracion.estado.bajas.isEmpty()) Text("No hay bajas registradas.")
    DatosAdministracion.estado.bajas.asReversed().forEach { baja ->
        val documento = DatosFuncionario.documentos.firstOrNull { it.id == baja.documentoId }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(documento?.nombre ?: baja.documentoId)
                Text("${baja.responsable} · ${baja.fecha}")
                Text(baja.motivo)
                documento?.let {
                    Text("Archivo y ${it.historial.size + 1} versiones conservados")
                    VistaPreviaDocumento(it.archivoUri)
                }
            }
        }
    }
}
