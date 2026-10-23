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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class EstadoTramites(
    val solicitudes: List<SolicitudFuncionario>,
    val avisosLeidos: Set<String> = emptySet()
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

    var estado by mutableStateOf(inicial)
        private set
    private val bloqueo = Mutex()

    private fun archivo(contexto: Context) =
        AtomicFile(File(contexto.filesDir, "solicitudes_funcionario.json"))

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
                            archivoUri = dato.optString("archivoUri", "")
                        ).also {
                            require(it.id.isNotBlank())
                            val desde = LocalDate.parse(it.desde)
                            val hasta = LocalDate.parse(it.hasta)
                            require(!hasta.isBefore(desde) && it.dias in 0.5..365.0)
                        }
                    }
                    require(solicitudes.map { it.id }.distinct().size == solicitudes.size)
                    val leidos = raiz.optJSONArray("leidos") ?: JSONArray()
                    EstadoTramites(solicitudes, List(leidos.length()) { leidos.getString(it) }.toSet())
                } catch (e: Exception) {
                    throw IOException("No se pudieron leer las solicitudes guardadas. Los datos se conservaron.", e)
                }
            }
            estado = cargado
        }
    }

    suspend fun enviar(contexto: Context, solicitud: SolicitudFuncionario) {
        bloqueo.withLock {
            require(solicitud.estado == EstadoSolicitud.EN_REVISION)
            require(estado.solicitudes.none { it.id == solicitud.id })
            ReglasSolicitudes.validar(solicitud, estado.solicitudes)
            val siguiente = estado.copy(solicitudes = listOf(solicitud) + estado.solicitudes)
            guardar(contexto, siguiente)
            estado = siguiente
        }
    }

    suspend fun marcarLeidos(contexto: Context, ids: Set<String>) {
        bloqueo.withLock {
            val siguiente = estado.copy(avisosLeidos = estado.avisosLeidos + ids)
            guardar(contexto, siguiente)
            estado = siguiente
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
                })
            }
            val contenido = JSONObject().apply {
                put("formato", 1)
                put("solicitudes", solicitudes)
                put("leidos", JSONArray(datos.avisosLeidos.toList()))
            }.toString().toByteArray(Charsets.UTF_8)
            val almacenamiento = archivo(contexto)
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
