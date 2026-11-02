package cl.fourpeople.aps

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object DatosSeguridad {
    var estado by mutableStateOf(EstadoSeguridad())
        private set
    private val bloqueo = Mutex()
    private fun archivo(contexto: Context) = AtomicFile(File(contexto.filesDir, "seguridad_ti.json"))

    suspend fun cargar(contexto: Context) {
        bloqueo.withLock {
            val recuperado = withContext(Dispatchers.IO) {
                val almacenamiento = archivo(contexto)
                val entrada = try { almacenamiento.openRead() } catch (e: FileNotFoundException) {
                    if (almacenamiento.baseFile.exists()) throw e
                    return@withContext EstadoSeguridad()
                }
                try {
                    val raiz = entrada.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                    require(raiz.getInt("formato") == 1)
                    val datos = raiz.getJSONArray("incidentes")
                    val incidentes = List(datos.length()) { i ->
                        val d = datos.getJSONObject(i)
                        val ids = d.getJSONArray("eventos")
                        val historia = d.getJSONArray("seguimiento")
                        IncidenteSeguridad(id = d.getString("id"), titulo = d.getString("titulo"),
                            descripcion = d.getString("descripcion"), usuario = d.getString("usuario"),
                            prioridad = PrioridadIncidente.valueOf(d.getString("prioridad")),
                            estado = EstadoIncidente.valueOf(d.getString("estado")), fecha = d.getString("fecha"),
                            eventosIds = List(ids.length()) { ids.getString(it) }.toSet(),
                            seguimiento = List(historia.length()) { j -> historia.getJSONObject(j).let {
                                SeguimientoIncidente(it.getString("fecha"), it.getString("responsable"),
                                    it.getString("accion"), it.getString("observacion"))
                            } }).also {
                            require(it.id.isNotBlank() && it.titulo.isNotBlank() && it.usuario.isNotBlank())
                            LocalDateTime.parse(it.fecha)
                            it.seguimiento.forEach { paso -> LocalDateTime.parse(paso.fecha) }
                        }
                    }
                    require(incidentes.map { it.id }.distinct().size == incidentes.size)
                    val cuentas = raiz.getJSONArray("bloqueadas")
                    EstadoSeguridad(incidentes, List(cuentas.length()) { cuentas.getString(it) }.toSet(),
                        AuditoriaLocal.leer(raiz.getJSONArray("auditoria")))
                } catch (e: Exception) {
                    throw IOException("No se pudieron leer los incidentes y bloqueos. Los datos se conservaron.", e)
                }
            }
            estado = recuperado
        }
    }

    fun tieneAcceso(contexto: Context): Boolean {
        val preferencias = contexto.getSharedPreferences("sesion_local", Context.MODE_PRIVATE)
        val usuario = preferencias.getString("usuario", "") ?: ""
        return preferencias.getString("rol", "") == "TI / Admin" && usuario !in estado.bloqueadas &&
            PerfilesCompartidos.perfiles.any { it.usuario == usuario && it.rol == RolUsuario.TI }
    }

    private fun autorizar(contexto: Context): String {
        require(tieneAcceso(contexto)) { "Se requiere una cuenta activa de TI." }
        return contexto.getSharedPreferences("sesion_local", Context.MODE_PRIVATE).getString("usuario", "")!!
    }

    private fun evento(responsable: String, incidente: IncidenteSeguridad, accion: String, detalle: String): EventoAuditoria {
        val cuenta = PerfilesCompartidos.perfiles.first { it.usuario == incidente.usuario }
        return EventoAuditoria(usuario = responsable, rol = "TI / Admin", entidadId = incidente.id,
            entidad = incidente.titulo, accion = accion, centro = cuenta.centro, categoria = "Incidente",
            titular = cuenta.nombre, run = cuenta.run.orEmpty(), detalle = "Cuenta: ${cuenta.usuario} · $detalle")
    }

    suspend fun crear(contexto: Context, titulo: String, descripcion: String, usuario: String,
                      prioridad: PrioridadIncidente, eventosIds: Set<String>): String = bloqueo.withLock {
        val responsable = autorizar(contexto)
        ReglasIncidentes.validar(titulo, descripcion, usuario, eventosIds, AuditoriaLocal.eventos)
        val incidente = IncidenteSeguridad(titulo = titulo.trim(), descripcion = descripcion.trim(),
            usuario = usuario, prioridad = prioridad, eventosIds = eventosIds.toSet())
        val nuevo = incidente.copy(seguimiento = listOf(SeguimientoIncidente(incidente.fecha, responsable,
            "Registró", incidente.descripcion)))
        guardar(contexto, estado.copy(incidentes = listOf(nuevo) + estado.incidentes,
            auditoria = estado.auditoria + evento(responsable, nuevo, "Registró incidente", nuevo.descripcion)))
        nuevo.id
    }

    suspend fun cambiarEstado(contexto: Context, original: IncidenteSeguridad,
                              siguiente: EstadoIncidente, observacion: String) = bloqueo.withLock {
        val responsable = autorizar(contexto)
        val actual = estado.incidentes.firstOrNull { it.id == original.id }
        require(actual == original) { "El incidente cambió. Vuelve a abrirlo." }
        ReglasIncidentes.validarTransicion(original.estado, siguiente, observacion)
        val nuevo = original.copy(estado = siguiente, seguimiento = original.seguimiento +
            SeguimientoIncidente(LocalDateTime.now().toString(), responsable, siguiente.texto, observacion.trim()))
        guardar(contexto, estado.copy(incidentes = estado.incidentes.map { if (it.id == nuevo.id) nuevo else it },
            auditoria = estado.auditoria + evento(responsable, nuevo,
                if (siguiente == EstadoIncidente.CERRADO) "Cerró incidente" else "Revisó incidente", observacion.trim())))
    }

    suspend fun cambiarCuenta(contexto: Context, incidenteId: String, bloquearCuenta: Boolean, motivo: String) = bloqueo.withLock {
        val responsable = autorizar(contexto)
        val original = estado.incidentes.firstOrNull { it.id == incidenteId }
            ?: throw IllegalArgumentException("El incidente no existe.")
        require(original.usuario != responsable) { "No puedes bloquear o reactivar tu propia cuenta desde un incidente." }
        require(motivo.trim().length in 10..1000) { "El motivo debe tener entre 10 y 1000 caracteres." }
        require((original.usuario in estado.bloqueadas) != bloquearCuenta) { "El estado de la cuenta ya cambió. Vuelve a abrir el incidente." }
        val accion = if (bloquearCuenta) "Bloqueó cuenta" else "Reactivó cuenta"
        val nuevo = original.copy(seguimiento = original.seguimiento +
            SeguimientoIncidente(LocalDateTime.now().toString(), responsable, accion, motivo.trim()))
        val cuentas = if (bloquearCuenta) estado.bloqueadas + original.usuario else estado.bloqueadas - original.usuario
        guardar(contexto, estado.copy(incidentes = estado.incidentes.map { if (it.id == nuevo.id) nuevo else it },
            bloqueadas = cuentas, auditoria = estado.auditoria + evento(responsable, nuevo, accion, motivo.trim())))
    }

    private suspend fun guardar(contexto: Context, siguiente: EstadoSeguridad) {
        withContext(Dispatchers.IO) {
            val raiz = JSONObject().apply {
                put("formato", 1)
                put("bloqueadas", JSONArray(siguiente.bloqueadas.toList()))
                put("auditoria", AuditoriaLocal.escribir(siguiente.auditoria))
                put("incidentes", JSONArray().apply { siguiente.incidentes.forEach { i ->
                    put(JSONObject().apply {
                        put("id", i.id); put("titulo", i.titulo); put("descripcion", i.descripcion)
                        put("usuario", i.usuario); put("prioridad", i.prioridad.name); put("estado", i.estado.name)
                        put("fecha", i.fecha); put("eventos", JSONArray(i.eventosIds.toList()))
                        put("seguimiento", JSONArray().apply { i.seguimiento.forEach { paso ->
                            put(JSONObject().apply { put("fecha", paso.fecha); put("responsable", paso.responsable)
                                put("accion", paso.accion); put("observacion", paso.observacion) })
                        } })
                    })
                } })
            }
            val almacenamiento = archivo(contexto)
            val salida = almacenamiento.startWrite()
            try {
                salida.write(raiz.toString().toByteArray(Charsets.UTF_8))
                almacenamiento.finishWrite(salida)
            } catch (e: Exception) {
                almacenamiento.failWrite(salida)
                throw IOException("No se pudo guardar el incidente o el estado de la cuenta.", e)
            }
        }
        estado = siguiente
    }
}
