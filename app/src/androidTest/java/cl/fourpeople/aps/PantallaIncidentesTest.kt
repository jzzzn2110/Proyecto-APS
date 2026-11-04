package cl.fourpeople.aps

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class PantallaIncidentesTest {
    private val titulo = "Consulta inusual " + java.util.UUID.randomUUID().toString().take(6)
    @get:Rule val regla = createAndroidComposeRule<MainActivity>()
    private fun entrar(usuario: String, rol: String) {
        regla.onNodeWithText("Usuario").performTextReplacement(usuario)
        regla.onNodeWithText("Contraseña").performTextReplacement("Contraseña123#")
        regla.onNodeWithText(rol).performScrollTo().performClick()
        regla.onNodeWithText("Ingresar").performScrollTo().performClick()
    }
    private fun captura(nombre: String) {
        val carpeta = File(regla.activity.getExternalFilesDir(null), "verificacion-incidentes").apply { mkdirs() }
        File(carpeta, nombre).outputStream().use {
            regla.waitForIdle()
            val imagen = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            imagen.compress(Bitmap.CompressFormat.PNG, 100, it)
            imagen.recycle()
        }
    }
    private fun motivo(texto: String) {
        regla.onNodeWithText("Observación o motivo").performScrollTo().performTextReplacement(texto)
    }
    @Test fun crearDesdeAuditoriaBloquearReactivarRevisarYCerrar() {
        regla.waitUntil(15000) { regla.onAllNodesWithText("Ingresar").fetchSemanticsNodes().isNotEmpty() }
        entrar("c.rojas", "Funcionario")
        regla.onNodeWithText("Documentos").performClick()
        val cantidadAntes = AuditoriaLocal.eventos.count { it.usuario == "c.rojas" && it.accion == "Vio" }
        regla.onNodeWithText("Anexo de contrato 2026").performScrollTo().performClick()
        regla.waitUntil(10000) { AuditoriaLocal.eventos.count { it.usuario == "c.rojas" && it.accion == "Vio" } > cantidadAntes }
        regla.onNodeWithText("Cerrar").performClick()
        regla.onNodeWithText("Inicio").performClick()
        regla.onNodeWithContentDescription("Cerrar sesión").performClick()
        entrar("d.herrera", "TI / Admin")
        regla.onAllNodesWithText("Vincular a incidente").onFirst().performScrollTo().performClick()
        regla.onNodeWithText("Título").performTextInput(titulo)
        regla.onNodeWithText("Descripción").performTextInput("Se revisará la consulta de este documento ficticio.")
        regla.onNodeWithText("Registrar incidente").performClick()
        regla.waitUntil(10000) { DatosSeguridad.estado.incidentes.any { it.titulo == titulo } }
        regla.onNodeWithText("Eventos vinculados").assertExists()
        captura("detalle.png")
        motivo("Bloqueo preventivo para revisión de acceso")
        regla.onNodeWithText("Bloquear cuenta").performScrollTo().performClick()
        regla.onNodeWithText("Confirmar").performClick()
        regla.waitUntil(10000) { "c.rojas" in DatosSeguridad.estado.bloqueadas }
        regla.onNodeWithText("Volver a incidentes").performClick()
        captura("incidentes.png")
        regla.onNodeWithText("Salir").performClick()
        entrar("c.rojas", "Funcionario")
        regla.onNodeWithText("Cuenta bloqueada. Contacta al administrador de TI.").performScrollTo().assertExists()
        captura("cuenta-bloqueada.png")
        entrar("d.herrera", "TI / Admin")
        regla.onNodeWithText("Incidentes", substring = false).performClick()
        regla.onNodeWithText(titulo).performClick()
        motivo("Identidad y actividad de prueba verificadas")
        regla.onNodeWithText("Reactivar cuenta").performScrollTo().performClick()
        regla.onNodeWithText("Confirmar").performClick()
        regla.waitUntil(10000) { "c.rojas" !in DatosSeguridad.estado.bloqueadas }
        motivo("Se revisaron los eventos vinculados")
        regla.onNodeWithText("Marcar revisado").performScrollTo().performClick()
        regla.waitUntil(10000) { DatosSeguridad.estado.incidentes.first().estado == EstadoIncidente.EN_REVISION }
        motivo("Incidente resuelto sin exposición de datos")
        regla.onNodeWithText("Cerrar incidente").performScrollTo().performClick()
        regla.waitUntil(10000) { DatosSeguridad.estado.incidentes.first().estado == EstadoIncidente.CERRADO }
        captura("incidente-cerrado.png")
        regla.onNodeWithText("Volver a incidentes").performClick()
        regla.onNodeWithText("Salir").performClick()
        entrar("c.rojas", "Funcionario")
        regla.onNodeWithText("Hola, Camila").assertExists()
    }
}
