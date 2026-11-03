package cl.fourpeople.aps

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class PantallaAuditoriaTest {
    @get:Rule val regla = createAndroidComposeRule<MainActivity>()

    @Test fun consultaDelFuncionarioApareceEnTiYAbreHistorial() {
        regla.waitUntil(15000) { regla.onAllNodesWithText("Ingresar").fetchSemanticsNodes().isNotEmpty() }
        regla.onNodeWithText("Usuario").performTextInput("c.rojas")
        regla.onNodeWithText("Contraseña").performTextInput("Contraseña123#")
        regla.onNodeWithText("Ingresar").performScrollTo().performClick()
        regla.waitUntil(10000) { regla.onAllNodesWithText("Hola, Camila").fetchSemanticsNodes().isNotEmpty() }
        regla.onNodeWithText("Documentos").performClick()
        regla.onNodeWithText("Anexo de contrato 2026").performScrollTo().performClick()
        regla.waitUntil(10000) { AuditoriaLocal.eventos.any { it.usuario == "c.rojas" && it.accion == "Vio" } }
        regla.onNodeWithText("Cerrar").performClick()
        regla.onNodeWithText("Inicio").performClick()
        regla.onNodeWithContentDescription("Cerrar sesión").performClick()
        regla.onNodeWithText("Usuario").performTextInput("d.herrera")
        regla.onNodeWithText("Contraseña").performTextInput("Contraseña123#")
        regla.onNodeWithText("TI / Admin").performScrollTo().performClick()
        regla.onNodeWithText("Ingresar").performScrollTo().performClick()
        regla.onNodeWithText("Actividad reciente").performScrollTo().assertExists()
        val carpeta = File(regla.activity.getExternalFilesDir(null), "verificacion-ti").apply { mkdirs() }
        File(carpeta, "auditoria.png").outputStream().use { flujo ->
            regla.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, flujo)
        }
        regla.onAllNodesWithText("Ver historial del documento ›").onFirst().performScrollTo().performClick()
        regla.onNodeWithText("Historial del documento").assertExists()
        regla.onNodeWithText("Exportar reporte de auditoría").assertExists()
        File(carpeta, "historial.png").outputStream().use { flujo ->
            regla.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, flujo)
        }
    }
}
