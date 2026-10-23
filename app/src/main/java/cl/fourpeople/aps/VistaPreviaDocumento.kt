package cl.fourpeople.aps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ResultadoVista(
    val imagen: Bitmap? = null,
    val paginas: Int = 1,
    val error: String = ""
)

@Composable
fun VistaPreviaDocumento(uri: String) {
    if (uri.isBlank()) {
        Text(
            "Este antecedente de ejemplo no tiene archivo adjunto.",
            color = Color.Gray
        )
        return
    }

    val contexto = LocalContext.current

    var pagina by rememberSaveable(uri) {
        mutableIntStateOf(0)
    }

    var totalPaginas by rememberSaveable(uri) {
        mutableIntStateOf(1)
    }

    var ampliado by rememberSaveable(uri) {
        mutableStateOf(false)
    }

    val vista by produceState<ResultadoVista?>(
        initialValue = null,
        uri,
        pagina
    ) {
        value = null

        try {
            val resultado = withContext(Dispatchers.IO) {
                renderizarArchivo(
                    contexto,
                    uri,
                    pagina
                )
            }

            totalPaginas = resultado.paginas
            value = resultado
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            value = ResultadoVista(
                error = e.message
                    ?: "No se pudo mostrar el archivo."
            )
        }
    }

    @Composable
    fun Contenido(modifier: Modifier) {
        Box(
            modifier = modifier.background(
                Color(0xFFE5E9E5)
            ),
            contentAlignment = Alignment.Center
        ) {
            val resultado = vista
            val imagen = resultado?.imagen

            when {
                resultado == null -> {
                    CircularProgressIndicator()
                }

                imagen != null -> {
                    Image(
                        bitmap = imagen.asImageBitmap(),
                        contentDescription =
                            "Vista previa del documento, página ${pagina + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                else -> {
                    Text(
                        resultado.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun Controles() {
        if (totalPaginas > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        pagina--
                    },
                    enabled = pagina > 0 &&
                            vista?.imagen != null
                ) {
                    Text("Anterior")
                }

                Text("${pagina + 1} / $totalPaginas")

                TextButton(
                    onClick = {
                        pagina++
                    },
                    enabled = pagina < totalPaginas - 1 &&
                            vista?.imagen != null
                ) {
                    Text("Siguiente")
                }
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Contenido(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clickable(
                    enabled = vista?.imagen != null
                ) {
                    ampliado = true
                }
        )

        Controles()

        TextButton(
            onClick = {
                ampliado = true
            },
            enabled = vista?.imagen != null
        ) {
            Text("Ampliar documento")
        }
    }

    if (ampliado) {
        Dialog(
            onDismissRequest = {
                ampliado = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            ampliado = false
                        }
                    ) {
                        Text("Cerrar vista previa")
                    }

                    Contenido(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Controles()
                }
            }
        }
    }
}

private fun renderizarArchivo(
    contexto: Context,
    uri: String,
    pagina: Int
): ResultadoVista {
    val direccion = Uri.parse(uri)

    require(direccion.scheme == "file") {
        "La dirección del archivo no es válida."
    }

    val ruta = direccion.path
        ?: throw IllegalArgumentException(
            "El archivo no tiene dirección."
        )

    val archivo = File(ruta).canonicalFile

    val carpeta = File(
        contexto.filesDir,
        "documentos"
    ).canonicalFile

    require(
        archivo.parentFile == carpeta &&
                archivo.isFile
    ) {
        "No encontramos el archivo adjunto."
    }

    if (
        archivo.extension.equals(
            "pdf",
            ignoreCase = true
        )
    ) {
        return ParcelFileDescriptor.open(
            archivo,
            ParcelFileDescriptor.MODE_READ_ONLY
        ).use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                require(pdf.pageCount > 0) {
                    "El PDF no contiene páginas."
                }

                pdf.openPage(
                    pagina.coerceIn(
                        0,
                        pdf.pageCount - 1
                    )
                ).use { hoja ->
                    val escala = 1600f / maxOf(
                        hoja.width,
                        hoja.height
                    )

                    val ancho = (hoja.width * escala)
                        .toInt()
                        .coerceAtLeast(1)

                    val alto = (hoja.height * escala)
                        .toInt()
                        .coerceAtLeast(1)

                    val imagen = Bitmap.createBitmap(
                        ancho,
                        alto,
                        Bitmap.Config.ARGB_8888
                    )

                    imagen.eraseColor(
                        android.graphics.Color.WHITE
                    )

                    hoja.render(
                        imagen,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    ResultadoVista(
                        imagen = imagen,
                        paginas = pdf.pageCount
                    )
                }
            }
        }
    }

    val opciones = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    BitmapFactory.decodeFile(
        archivo.path,
        opciones
    )

    require(
        opciones.outWidth > 0 &&
                opciones.outHeight > 0
    ) {
        "La imagen no se puede leer."
    }

    var muestra = 1

    while (
        maxOf(
            opciones.outWidth,
            opciones.outHeight
        ) / muestra > 1600
    ) {
        muestra *= 2
    }

    opciones.inJustDecodeBounds = false
    opciones.inSampleSize = muestra

    val original = BitmapFactory.decodeFile(
        archivo.path,
        opciones
    ) ?: throw IllegalArgumentException(
        "La imagen no se puede abrir."
    )

    val orientacion = runCatching {
        ExifInterface(archivo.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            1
        )
    }.getOrDefault(1)

    val matriz = Matrix()

    when (orientacion) {
        2 -> matriz.postScale(-1f, 1f)
        3 -> matriz.postRotate(180f)
        4 -> matriz.postScale(1f, -1f)

        5 -> {
            matriz.postRotate(90f)
            matriz.postScale(-1f, 1f)
        }

        6 -> matriz.postRotate(90f)

        7 -> {
            matriz.postRotate(270f)
            matriz.postScale(-1f, 1f)
        }

        8 -> matriz.postRotate(270f)
    }

    val imagen = if (matriz.isIdentity) {
        original
    } else {
        Bitmap.createBitmap(
            original,
            0,
            0,
            original.width,
            original.height,
            matriz,
            true
        )
    }

    if (imagen !== original) {
        original.recycle()
    }

    return ResultadoVista(
        imagen = imagen
    )
}