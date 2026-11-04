package cl.fourpeople.aps

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

// Devuelve la dirección de la copia privada y el nombre que verá el funcionario.
data class ArchivoSeleccionado(
    val uri: String,
    val nombre: String
)

object ArchivosFuncionario {

    private const val MAXIMO_BYTES = 20 * 1024 * 1024

    // Importa el adjunto en segundo plano para no bloquear la pantalla.
    suspend fun importar(
        context: Context,
        origen: Uri
    ): ArchivoSeleccionado = withContext(Dispatchers.IO) {

        val carpeta = File(
            context.filesDir,
            "documentos"
        ).apply {
            mkdirs()
        }

        // Usa un archivo temporal hasta comprobar que el contenido sea válido.
        var destino = File(
            carpeta,
            "${UUID.randomUUID()}.tmp"
        )

        try {
            val entrada = context.contentResolver
                .openInputStream(origen)
                ?: throw IllegalArgumentException(
                    "No se pudo abrir el archivo."
                )

            entrada.use { input ->
                destino.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L

                    // Copia por bloques, permite cancelar y rechaza archivos que superen 20 MB.
                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val leidos = input.read(buffer)
                        if (leidos == -1) break

                        total += leidos

                        require(total <= MAXIMO_BYTES) {
                            "El archivo supera los 20 MB."
                        }

                        output.write(buffer, 0, leidos)
                    }

                    require(total > 0) {
                        "El archivo está vacío."
                    }
                }
            }

            // Comprueba el contenido real; el nombre o la extensión no bastan para aceptar un PDF.
            val cabecera = ByteArray(5)

            destino.inputStream().use {
                it.read(cabecera)
            }

            val esPdf = cabecera.contentEquals(
                "%PDF-".toByteArray(Charsets.US_ASCII)
            )

            val extension = if (esPdf) {
                try {
                    ParcelFileDescriptor.open(
                        destino,
                        ParcelFileDescriptor.MODE_READ_ONLY
                    ).use { descriptor ->
                        PdfRenderer(descriptor).use { pdf ->
                            require(pdf.pageCount > 0)
                        }
                    }
                } catch (_: Exception) {
                    throw IllegalArgumentException(
                        "El PDF está dañado o protegido."
                    )
                }

                "pdf"
            } else {
                val medidas = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                BitmapFactory.decodeFile(destino.path, medidas)

                require(
                    medidas.outWidth > 0 &&
                            medidas.outHeight > 0
                ) {
                    "Selecciona un PDF o una imagen válida."
                }

                // Reduce la imagen de comprobación para no cargar una fotografía grande a tamaño completo.
                var muestra = 1

                while (
                    maxOf(
                        medidas.outWidth,
                        medidas.outHeight
                    ) / muestra > 1600
                ) {
                    muestra *= 2
                }

                val opciones = BitmapFactory.Options().apply {
                    inSampleSize = muestra
                }

                val imagen = BitmapFactory.decodeFile(
                    destino.path,
                    opciones
                ) ?: throw IllegalArgumentException(
                    "La imagen está dañada."
                )

                imagen.recycle()

                MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(medidas.outMimeType)
                    ?: "img"
            }

            currentCoroutineContext().ensureActive()

            // Asigna un nombre único a la copia definitiva para evitar reemplazar otros adjuntos.
            val definitivo = File(
                carpeta,
                "${UUID.randomUUID()}.$extension"
            )

            check(destino.renameTo(definitivo)) {
                "No se pudo guardar el archivo."
            }

            destino = definitivo

            // Recupera el nombre visible del selector y elimina caracteres de control.
            val nombre = runCatching {
                context.contentResolver.query(
                    origen,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                ).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        "Documento"
                    }
                }
            }.getOrNull()
                .orEmpty()
                .filterNot { it.isISOControl() }
                .ifBlank { "Documento" }

            ArchivoSeleccionado(
                uri = Uri.fromFile(destino).toString(),
                nombre = nombre
            )
        } catch (e: Exception) {
            // Borra la copia incompleta si la importación falla o se cancela.
            destino.delete()
            throw e
        }
    }

    // Limpia adjuntos descartados antes de incorporarlos al expediente.
    suspend fun eliminarSinGuardar(
        context: Context,
        uri: String
    ) {
        withContext(Dispatchers.IO) {
            if (uri.isNotBlank()) {
                runCatching {
                    val carpeta = File(
                        context.filesDir,
                        "documentos"
                    ).canonicalFile

                    val direccion = Uri.parse(uri)
                    val ruta = direccion.path

                    if (direccion.scheme == "file" && ruta != null) {
                        val archivo = File(ruta).canonicalFile

                        // Solo permite borrar archivos de la carpeta privada de documentos de esta app.
                        if (archivo.parentFile == carpeta) {
                            archivo.delete()
                        }
                    }
                }
            }
        }
    }
}