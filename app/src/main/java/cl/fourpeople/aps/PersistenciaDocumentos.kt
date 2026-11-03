package cl.fourpeople.aps

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// guardado: conserva documentos y expediente en el teléfono.
object PersistenciaDocumentos {
    // Una lectura o escritura a la vez.
    private val bloqueo = Mutex()
    // Archivo privado de la app. No cambia al editar los textos de pantalla.
    private fun archivo(c: Context) = AtomicFile(File(c.filesDir, "expediente_funcionario.json"))

    // Prepara un documento, su versión y sus versiones anteriores.
    private fun escribir(d: DocumentoFuncionario): JSONObject = JSONObject().apply {
        put("id", d.id); put("nombre", d.nombre); put("categoria", d.categoria)
        put("fecha", d.fecha); put("estado", d.estado.name); put("vencimiento", d.vencimiento)
        put("motivoRechazo", d.motivoRechazo); put("archivoUri", d.archivoUri)
        put("horas", d.horas ?: JSONObject.NULL); put("version", d.version)
        put("historial", JSONArray().apply {
            d.historial.forEach { put(escribir(it.copy(historial = emptyList()))) }
        })
    }

    // Recupera un documento. Los archivos antiguos empiezan en versión 1.
    private fun leer(d: JSONObject, conHistorial: Boolean = true): DocumentoFuncionario {
        val historia = d.optJSONArray("historial") ?: JSONArray()
        return DocumentoFuncionario(
            id = d.getString("id"), nombre = d.getString("nombre"),
            categoria = d.getString("categoria"), fecha = d.getString("fecha"),
            estado = EstadoDocumento.valueOf(d.getString("estado")),
            vencimiento = d.optString("vencimiento", ""), motivoRechazo = d.optString("motivoRechazo", ""),
            archivoUri = d.optString("archivoUri", ""),
            horas = if (d.isNull("horas")) null else d.getInt("horas"),
            version = d.optInt("version", 1),
            historial = if (!conHistorial) emptyList() else List(historia.length()) { leer(historia.getJSONObject(it), false) }
        ).also { require(it.id.isNotBlank() && it.version > 0) }
    }

    // Se llama al iniciar la app para recuperar los datos guardados.
    suspend fun cargar(contexto: Context, iniciales: List<DocumentoFuncionario>): List<DocumentoFuncionario> {
        // Lee en segundo plano para no bloquear la pantalla.
        val resultado = withContext(Dispatchers.IO) {
            bloqueo.withLock {
                val almacenamiento = archivo(contexto)
                val entrada = try { almacenamiento.openRead() } catch (e: FileNotFoundException) {
                    if (almacenamiento.baseFile.exists()) throw e
                    // Primera ejecución: usa los documentos de demostración.
                    return@withLock iniciales.toList() to EstadoAdministracion()
                }
                try {
                    val raiz = JSONObject(entrada.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    // Comprueba que el archivo tenga un formato conocido.
                    require(raiz.getInt("formato") == 1)
                    val datos = raiz.getJSONArray("documentos")
                    val lista = List(datos.length()) { leer(datos.getJSONObject(it)) }
                    require(lista.map { it.id }.distinct().size == lista.size)
                    lista to CodecAdministracion.leer(raiz.optJSONObject("administracion"))
                } catch (e: Exception) {
                    throw IOException("No se pudo leer el expediente. Los datos se conservaron.", e)
                }
            }
        }
        // Comparte con las pantallas los antecedentes recuperados.
        DatosAdministracion.estado = resultado.second
        return resultado.first
    }

    // Guarda todos los documentos y los antecedentes laborales juntos.
    suspend fun guardar(contexto: Context, documentos: List<DocumentoFuncionario>,
                        administracion: EstadoAdministracion = DatosAdministracion.estado) {
        val copia = documentos.toList()
        withContext(Dispatchers.IO) {
            bloqueo.withLock {
                val contenido = JSONObject().apply {
                    put("formato", 1)
                    put("documentos", JSONArray().apply { copia.forEach { put(escribir(it)) } })
                    put("administracion", CodecAdministracion.escribir(administracion))
                }.toString().toByteArray(Charsets.UTF_8)
                val almacenamiento = archivo(contexto)
                // AtomicFile conserva la copia anterior si falla la escritura.
                val salida = almacenamiento.startWrite()
                try {
                    salida.write(contenido)
                    // Confirma el archivo nuevo cuando terminó de escribirse.
                    almacenamiento.finishWrite(salida)
                } catch (e: Exception) {
                    // Si falla, recupera la copia anterior y muestra el error.
                    almacenamiento.failWrite(salida)
                    throw IOException("No se pudo guardar el expediente.", e)
                }
            }
        }
    }
}
