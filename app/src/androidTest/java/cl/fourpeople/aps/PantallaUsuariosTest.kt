package cl.fourpeople.aps

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class PantallaUsuariosTest {
    @get:Rule val regla = createAndroidComposeRule<MainActivity>()
    private fun captura(nombre: String) {
        val carpeta = File(regla.activity.getExternalFilesDir(null), "verificacion-usuarios").apply { mkdirs() }
        File(carpeta, nombre).outputStream().use { regla.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun crearEditarPermisosYDesactivarUsuario() {
        regla.waitUntil(20000) { regla.onAllNodesWithText("Ingresar").fetchSemanticsNodes().isNotEmpty() }
        regla.onNodeWithText("Usuario").performTextInput("d.herrera")
        regla.onNodeWithText("Contraseña").performTextInput("Contraseña123#")
        regla.onNodeWithText("TI / Admin").performScrollTo().performClick()
        regla.onNodeWithText("Ingresar").performScrollTo().performClick()
        regla.onNodeWithText("Usuarios").performClick()
        captura("usuarios.png")
        regla.onNodeWithText("+ Nuevo usuario").performClick()
        val usuario = "demo." + UUID.randomUUID().toString().take(6)
        regla.onNodeWithText("Usuario de acceso").performTextInput(usuario)
        regla.onNodeWithText("Nombre completo").performTextInput("Administrativo de prueba")
        regla.onNodeWithText("Correo electrónico").performTextInput("$usuario@demo.invalid")
        regla.onNodeWithText("Funcionario").performScrollTo().performClick()
        regla.onNodeWithText("Administrativo").performClick()
        regla.onNodeWithContentDescription("Dar de baja documentos").performScrollTo().performClick()
        regla.onNodeWithContentDescription("Descargar documentos").performScrollTo().performClick()
        regla.onNodeWithText("Guardar cambios").performClick()
        regla.waitUntil(10000) { PerfilesCompartidos.perfiles.any { it.usuario == usuario } }
        regla.onNodeWithText("Buscar usuario").performTextInput(usuario)
        regla.onNodeWithText("Administrativo de prueba").performClick()
        regla.onNodeWithContentDescription("Dar de baja documentos").performScrollTo().assertIsOff()
        regla.onNodeWithContentDescription("Descargar documentos").performScrollTo().assertIsOn()
        captura("editar-usuario.png")
        regla.onNodeWithContentDescription("Cuenta activa").performScrollTo().performClick()
        regla.onNodeWithText("Guardar cambios").performClick()
        regla.waitUntil(10000) { PerfilesCompartidos.perfiles.first { it.usuario == usuario }.activa == false }
        regla.onNodeWithText("Inactiva").assertExists()
        regla.onNodeWithText("Configuración").performClick()
        regla.onNodeWithText("Crear y exportar respaldo").performScrollTo().assertExists()
        regla.onNodeWithText("Seleccionar respaldo para restaurar").assertExists()
        captura("configuracion.png")
    }
}
