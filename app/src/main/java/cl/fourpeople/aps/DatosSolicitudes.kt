package cl.fourpeople.aps

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class EstadoTramites(
    val solicitudes: List<SolicitudFuncionario>,
    val avisosLeidos: Set<String> = emptySet(),
    val auditoria: List<EventoAuditoria> = emptyList()
)

object DatosSolicitudes {
    // Casos ficticios equivalentes a los estados del mockup.
    private val inicial = EstadoTramites(listOf(
        SolicitudFuncionario("SOL-001", TipoSolicitud.ADMINISTRATIVO,
            "2026-09-26", "2026-09-26", JornadaSolicitud.COMPLETA,
            "Trámite personal.", "2026-09-24T11:20:00"),
        SolicitudFuncionario("SOL-002", TipoSolicitud.FERIADO,
            "2026-10-13", "2026-10-17", JornadaSolicitud.COMPLETA,
            "Descanso programado.", "2026-09-20T10:00:00",
            EstadoSolicitud.APROBADA, "Feriado autorizado por la jefatura de demostración."),
        SolicitudFuncionario("SOL-003", TipoSolicitud.ADMINISTRATIVO,
            "2026-09-12", "2026-09-13", JornadaSolicitud.COMPLETA,
            "Asuntos personales.", "2026-09-10T09:00:00",
            EstadoSolicitud.APROBADA, "Permiso autorizado."),
        SolicitudFuncionario("SOL-004", TipoSolicitud.COMPENSATORIO,
            "2026-09-02", "2026-09-02", JornadaSolicitud.COMPLETA,
            "Compensación de jornada.", "2026-09-01T09:00:00",
            EstadoSolicitud.RECHAZADA, "Falta acreditar las horas compensatorias.")
    ))

    // Las pantallas consultan este estado compartido y se actualizan cuando cambia.
    var estado by mutableStateOf(inicial)
        private set
    private val bloqueo get() = AlmacenamientoAps.bloqueo

    private fun archivo(contexto: Context) =
        AtomicFile(File(contexto.filesDir, "solicitudes_funcionario.json"))

    // Recupera las solicitudes del teléfono; usa los ejemplos solo si todavía no existe el archivo.
    suspend fun cargar(contexto: Context) {
        bloqueo.withLock {
            val cargado = withContext(Dispatchers.IO) {
                val almacenamiento = archivo(contexto)
                val entrada = try {
                    almacenamiento.openRead()
                } catch (e: FileNotFoundException) {
                    if (almacenamiento.baseFile.exists()) throw e
                    return@withContext inicial
                }
                try {
                    val raiz = JSONObject(entrada.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    leer(raiz)
                } catch (e: Exception) {
                    throw IOException("No se pudieron leer las solicitudes guardadas. Los datos se conservaron.", e)
                }
            }
            estado = cargado
        }
    }

    internal fun leer(raiz: JSONObject): EstadoTramites {
        require(raiz.getInt("formato") == 1)
        val lista = raiz.getJSONArray("solicitudes")
        val solicitudes = List(lista.length()) { indice ->
            val dato = lista.getJSONObject(indice)
            SolicitudFuncionario(
                id = dato.getString("id"),
                tipo = TipoSolicitud.valueOf(dato.getString("tipo")),
                desde = dato.getString("desde"),
                hasta = dato.getString("hasta"),
                jornada = JornadaSolicitud.valueOf(dato.getString("jornada")),
                motivo = dato.getString("motivo"),
                creadaEn = dato.getString("creadaEn"),
                estado = EstadoSolicitud.valueOf(dato.getString("estado")),
                respuesta = dato.optString("respuesta", ""),

                // Recupera el archivo adjunto de la solicitud.
                archivoUri = dato.optString("archivoUri", ""),

                // Recupera quién revisó la solicitud y cuándo.
                // Las solicitudes antiguas pueden tener estos datos vacíos.
                revisadaPor = dato.optString("revisadaPor", ""),
                revisadaEn = dato.optString("revisadaEn", ""),


            ).also {
                require(it.id.isNotBlank())
                LocalDateTime.parse(it.creadaEn)
                if (it.revisadaEn.isNotBlank()) LocalDateTime.parse(it.revisadaEn)
                val desde = LocalDate.parse(it.desde)
                val hasta = LocalDate.parse(it.hasta)
                require(!hasta.isBefore(desde) && it.dias in 0.5..365.0)
            }
        }
        require(solicitudes.map { it.id }.distinct().size == solicitudes.size)
        val leidos = raiz.optJSONArray("leidos") ?: JSONArray()
        return EstadoTramites(solicitudes, List(leidos.length()) { leidos.getString(it) }.toSet(),
            AuditoriaLocal.leer(raiz.optJSONArray("auditoria")))
    }

    suspend fun asegurarPersistencia(contexto: Context) = bloqueo.withLock {
        if (!File(contexto.filesDir, "solicitudes_funcionario.json").exists()) guardar(contexto, estado)
    }

    suspend fun enviar(contexto: Context, solicitud: SolicitudFuncionario) {
        bloqueo.withLock {
            ControlAcceso.exigir(contexto, "solicitudes.propias", propio = true)
            // Una solicitud nueva debe estar en revisión, tener un ID único y cumplir las reglas de fechas y saldo.
            require(solicitud.estado == EstadoSolicitud.EN_REVISION)
            require(estado.solicitudes.none { it.id == solicitud.id })
            ReglasSolicitudes.validar(solicitud, estado.solicitudes)
            val evento = AuditoriaLocal.evento(contexto, solicitud.id, solicitud.tipo.texto, "Solicitó")
            val siguiente = estado.copy(solicitudes = listOf(solicitud) + estado.solicitudes,
                auditoria = estado.auditoria + evento)
            // Actualiza el estado visible después de guardar correctamente en el teléfono.
            guardar(contexto, siguiente)
            estado = siguiente
        }
    }

    // Conserva los identificadores leídos para que no vuelvan a aparecer como avisos nuevos al reiniciar.
    suspend fun marcarLeidos(contexto: Context, ids: Set<String>) {
        bloqueo.withLock {
            val siguiente = estado.copy(avisosLeidos = estado.avisosLeidos + ids)
            guardar(contexto, siguiente)
            estado = siguiente
        }
    }

    // Permite que Jefatura apruebe o rechace una solicitud pendiente.
    suspend fun resolver(
        contexto: Context,
        perfil: String,
        id: String,
        aprobar: Boolean,
        respuesta: String
    ) {
        // Comprueba el permiso antes de modificar los datos.
        ControlAcceso.exigir(contexto, "solicitudes.resolver", expediente = true)
        require(perfil == "Jefatura") {
            "Solo Jefatura puede resolver solicitudes."
        }

        val respuestaLimpia = respuesta.trim()

        // Tanto la aprobación como el rechazo necesitan una respuesta.
        require(respuestaLimpia.length in 10..500) {
            "La respuesta debe tener entre 10 y 500 caracteres."
        }

        // Evita que dos operaciones cambien las solicitudes al mismo tiempo.
        bloqueo.withLock {
            val original = estado.solicitudes.firstOrNull {
                it.id == id
            } ?: throw IllegalArgumentException("La solicitud no existe.")

            // Una solicitud resuelta no se puede resolver otra vez.
            require(original.estado == EstadoSolicitud.EN_REVISION) {
                "La solicitud ya fue resuelta."
            }

            // Cambia únicamente la solicitud seleccionada.
            val actualizadas = estado.solicitudes.map { solicitud ->
                if (solicitud.id != id) {
                    solicitud
                } else {
                    solicitud.copy(
                        estado = if (aprobar) {
                            EstadoSolicitud.APROBADA
                        } else {
                            EstadoSolicitud.RECHAZADA
                        },
                        respuesta = respuestaLimpia,

                        revisadaPor = requireNotNull(ControlAcceso.perfil(contexto)).usuario,
                        revisadaEn = java.time.LocalDateTime.now().toString()
                    )
                }
            }

            val evento = AuditoriaLocal.evento(contexto, original.id, original.tipo.texto,
                if (aprobar) "Aprobó solicitud" else "Rechazó solicitud", detalle = respuestaLimpia)
            val siguiente = estado.copy(solicitudes = actualizadas, auditoria = estado.auditoria + evento)

            // Primero guarda en el teléfono y luego actualiza la pantalla.
            guardar(contexto, siguiente)
            estado = siguiente

            // El saldo se calcula con ReglasSolicitudes.
            // Aprobar mantiene los días reservados; rechazar los libera.
        }
    }
    private suspend fun guardar(contexto: Context, datos: EstadoTramites) {
        withContext(Dispatchers.IO) {
            val solicitudes = JSONArray()
            datos.solicitudes.forEach { solicitud ->
                solicitudes.put(JSONObject().apply {
                    put("id", solicitud.id)
                    put("tipo", solicitud.tipo.name)
                    put("desde", solicitud.desde)
                    put("hasta", solicitud.hasta)
                    put("jornada", solicitud.jornada.name)
                    put("motivo", solicitud.motivo)
                    put("creadaEn", solicitud.creadaEn)
                    put("estado", solicitud.estado.name)
                    put("respuesta", solicitud.respuesta)
                    put("archivoUri", solicitud.archivoUri)

                    // Conserva la revisión al cerrar y abrir la aplicación.
                    put("revisadaPor", solicitud.revisadaPor)
                    put("revisadaEn", solicitud.revisadaEn)
                })
            }
            val contenido = JSONObject().apply {
                put("formato", 1)
                put("auditoria", AuditoriaLocal.escribir(datos.auditoria))
                put("solicitudes", solicitudes)
                put("leidos", JSONArray(datos.avisosLeidos.toList()))
            }.toString().toByteArray(Charsets.UTF_8)
            val almacenamiento = archivo(contexto)
            // La escritura atómica permite recuperar la copia anterior si falla el guardado.
            val salida = almacenamiento.startWrite()
            try {
                salida.write(contenido)
                almacenamiento.finishWrite(salida)
            } catch (e: Exception) {
                almacenamiento.failWrite(salida)
                throw IOException("No se pudo guardar la solicitud o el estado de los avisos.", e)
            }
        }
    }
}
