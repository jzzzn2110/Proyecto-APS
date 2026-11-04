package cl.fourpeople.aps

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CamaraFuncionario {
    // Prepara una foto temporal y entrega a la cámara una URI del FileProvider.
    fun crearDestino(contexto: Context): Uri {
        val carpeta = File(contexto.cacheDir, "camara")
        check(carpeta.isDirectory || carpeta.mkdirs()) {
            "No se pudo preparar la cámara."
        }
        val foto = File(carpeta, "foto_${UUID.randomUUID()}.jpg")
        check(foto.createNewFile()) { "No se pudo crear la fotografía." }
        return try {
            FileProvider.getUriForFile(
                contexto,
                "${contexto.packageName}.archivos",
                foto
            )
        } catch (e: Exception) {
            // Si no se puede compartir la URI, elimina el archivo vacío que se había creado.
            foto.delete()
            throw e
        }
    }

    // Elimina el temporal de la cámara solo si pertenece al proveedor de esta aplicación.
    suspend fun eliminarTemporal(contexto: Context, direccion: String) {
        withContext(Dispatchers.IO) {
            if (direccion.isNotBlank()) {
                val uri = Uri.parse(direccion)
                if (uri.scheme == "content" &&
                    uri.authority == "${contexto.packageName}.archivos"
                ) {
                    runCatching { contexto.contentResolver.delete(uri, null, null) }
                }
            }
        }
    }
}
