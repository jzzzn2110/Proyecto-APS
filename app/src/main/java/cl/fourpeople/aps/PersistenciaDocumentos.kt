package cl.fourpeople.aps

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object PersistenciaDocumentos {

    private val bloqueo = Mutex()

    private fun archivo(contexto: Context): AtomicFile {
        return AtomicFile(
            File(
                contexto.filesDir,
                "expediente_funcionario.json"
            )
        )
    }

    suspend fun cargar(
        contexto: Context,
        iniciales: List<DocumentoFuncionario>
    ): List<DocumentoFuncionario> = withContext(Dispatchers.IO) {
        bloqueo.withLock {
            val almacenamiento = archivo(contexto)

            // Recupera la copia anterior si una escritura se interrumpió.
            val entrada = try {
                almacenamiento.openRead()
            } catch (e: java.io.FileNotFoundException) {
                if (almacenamiento.baseFile.exists()) {
                    throw e
                }

                return@withLock iniciales.toList()
            }

            try {
                val texto = entrada
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val raiz = JSONObject(texto)

                require(raiz.getInt("formato") == 1) {
                    "Formato de expediente incompatible."
                }

                val registros = raiz.getJSONArray("documentos")

                val documentos = List(registros.length()) { indice ->
                    val dato = registros.getJSONObject(indice)

                    DocumentoFuncionario(
                        id = dato.getString("id"),
                        nombre = dato.getString("nombre"),
                        categoria = dato.getString("categoria"),
                        fecha = dato.getString("fecha"),
                        estado = EstadoDocumento.valueOf(
                            dato.getString("estado")
                        ),
                        vencimiento = dato.optString(
                            "vencimiento",
                            ""
                        ),
                        motivoRechazo = dato.optString(
                            "motivoRechazo",
                            ""
                        ),
                        archivoUri = dato.optString(
                            "archivoUri",
                            ""
                        ),
                        horas = if (dato.isNull("horas")) {
                            null
                        } else {
                            dato.getInt("horas")
                        }
                    )
                }

                require(documentos.all { it.id.isNotBlank() })

                require(
                    documentos.map { it.id }.distinct().size ==
                            documentos.size
                )

                documentos
            } catch (e: Exception) {
                throw IOException(
                    "No se pudo leer el expediente guardado. " +
                            "Los datos se conservaron.",
                    e
                )
            }
        }
    }

    suspend fun guardar(
        contexto: Context,
        documentos: List<DocumentoFuncionario>
    ) {
        val copia = documentos.toList()

        withContext(Dispatchers.IO) {
            bloqueo.withLock {
                val registros = JSONArray()

                copia.forEach { documento ->
                    val registro = JSONObject().apply {
                        put("id", documento.id)
                        put("nombre", documento.nombre)
                        put("categoria", documento.categoria)
                        put("fecha", documento.fecha)
                        put("estado", documento.estado.name)
                        put("vencimiento", documento.vencimiento)
                        put("motivoRechazo", documento.motivoRechazo)
                        put("archivoUri", documento.archivoUri)
                        put(
                            "horas",
                            documento.horas ?: JSONObject.NULL
                        )
                    }

                    registros.put(registro)
                }

                val contenido = JSONObject().apply {
                    put("formato", 1)
                    put("documentos", registros)
                }.toString().toByteArray(Charsets.UTF_8)

                val almacenamiento = archivo(contexto)
                val salida = almacenamiento.startWrite()

                try {
                    salida.write(contenido)
                    almacenamiento.finishWrite(salida)
                } catch (e: Exception) {
                    almacenamiento.failWrite(salida)

                    throw IOException(
                        "No se pudo guardar el expediente.",
                        e
                    )
                }
            }
        }
    }
}