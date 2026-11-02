package cl.fourpeople.aps

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

object AuditoriaLocal {
    var eventosDocumentales by mutableStateOf<List<EventoAuditoria>>(emptyList())
        internal set
    val eventos: List<EventoAuditoria> get() = eventosDocumentales + DatosSolicitudes.estado.auditoria + DatosSeguridad.estado.auditoria

    fun evento(contexto: Context, id: String, nombre: String, accion: String,
               categoria: String = "Solicitud", detalle: String = ""): EventoAuditoria {
        val prefs = contexto.getSharedPreferences("sesion_local", Context.MODE_PRIVATE)
        val expediente = DatosAdministracion.estado.expediente
        return EventoAuditoria(usuario = prefs.getString("usuario", null) ?: "sistema",
            rol = prefs.getString("rol", null) ?: "Sistema", entidadId = id, entidad = nombre,
            accion = accion, centro = expediente.centro, categoria = categoria,
            titular = expediente.nombre, run = expediente.run, detalle = detalle)
    }

    fun cambios(contexto: Context, documentos: List<DocumentoFuncionario>, administracion: EstadoAdministracion): List<EventoAuditoria> {
        val anteriores = DatosFuncionario.documentos.associateBy { it.id }
        val nuevos = documentos.mapNotNull { d ->
            val previo = anteriores[d.id]
            val accion = when {
                previo == null -> "Cargó"
                previo == d -> return@mapNotNull null
                previo.estado != d.estado && d.estado == EstadoDocumento.APROBADO -> "Validó"
                previo.estado != d.estado && d.estado == EstadoDocumento.RECHAZADO -> "Rechazó"
                else -> "Modificó"
            }
            evento(contexto, d.id, d.nombre, accion, d.categoria,
                "Versión ${d.version} · ${d.estado.texto}" + if (d.motivoRechazo.isBlank()) "" else " · ${d.motivoRechazo}")
        }
        val bajas = administracion.bajas.filter { baja -> DatosAdministracion.estado.bajas.none { it.documentoId == baja.documentoId } }.map { baja ->
            val d = documentos.first { it.id == baja.documentoId }
            evento(contexto, d.id, d.nombre, "Dio de baja", d.categoria, baja.motivo)
        }
        val expediente = if (administracion.expediente != DatosAdministracion.estado.expediente)
            listOf(evento(contexto, administracion.expediente.run, administracion.expediente.nombre, "Editó expediente", "Expediente")) else emptyList()
        return nuevos + bajas + expediente
    }

    suspend fun registrar(contexto: Context, evento: EventoAuditoria) {
        PersistenciaDocumentos.registrarAuditoria(contexto, evento)
    }

    fun escribir(eventos: List<EventoAuditoria>) = JSONArray().apply {
        eventos.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("fecha", e.fecha); put("usuario", e.usuario); put("rol", e.rol)
            put("entidadId", e.entidadId); put("entidad", e.entidad); put("accion", e.accion)
            put("resultado", e.resultado); put("centro", e.centro); put("categoria", e.categoria)
            put("titular", e.titular); put("run", e.run); put("detalle", e.detalle)
        }) }
    }

    fun leer(datos: JSONArray?): List<EventoAuditoria> = if (datos == null) emptyList() else
        List(datos.length()) { i -> datos.getJSONObject(i).let { e ->
            EventoAuditoria(id = e.getString("id"), fecha = e.getString("fecha"),
                usuario = e.getString("usuario"), rol = e.getString("rol"), entidadId = e.getString("entidadId"),
                entidad = e.getString("entidad"), accion = e.getString("accion"), resultado = e.getString("resultado"),
                centro = e.getString("centro"), categoria = e.getString("categoria"), titular = e.getString("titular"),
                run = e.getString("run"), detalle = e.getString("detalle")).also {
                java.time.LocalDateTime.parse(it.fecha)
                require(it.id.isNotBlank())
            }
        } }
}

@Composable
fun RegistrarConsulta(documento: DocumentoFuncionario) {
    val contexto = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var error by remember(documento.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(documento.id, documento.version) {
        try {
            AuditoriaLocal.registrar(contexto, AuditoriaLocal.evento(contexto, documento.id,
                documento.nombre, "Vio", documento.categoria, "Versión ${documento.version}"))
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { error = "No se pudo registrar la consulta en auditoría." }
    }
    error?.let { androidx.compose.material3.Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
}
