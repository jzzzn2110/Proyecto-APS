package cl.fourpeople.aps

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CircuitoCompletoTest {
    @get:Rule val regla = createAndroidComposeRule<MainActivity>()
    private val instrumento get() = InstrumentationRegistry.getInstrumentation()
    private val contexto get() = instrumento.targetContext
    private val codigo = UUID.randomUUID().toString().take(6)
    private val nombre = "Contrato integración $codigo"
    private val motivo = "Solicitud integrada de demostración $codigo"
    private val respuesta = "Solicitud autorizada en el recorrido integrado $codigo"
    private val pasos = mutableListOf<String>()
    private val carpeta get() = File(contexto.getExternalFilesDir(null), "entrega-aps").apply { mkdirs() }

    private fun entrar(usuario: String, rol: String) {
        regla.waitUntil(30000) { regla.onAllNodesWithText("Ingresar").fetchSemanticsNodes().isNotEmpty() }
        regla.onNodeWithText("Usuario").performTextReplacement(usuario)
        regla.onNodeWithText("Contraseña").performTextReplacement("Contraseña123#")
        regla.onNodeWithText(rol).performScrollTo().performClick()
        regla.onNodeWithText("Ingresar").performScrollTo().performClick()
    }
    private fun salirFuncionario() {
        regla.onNodeWithText("Inicio").performClick()
        regla.onNodeWithContentDescription("Cerrar sesión").performClick()
    }
    private fun captura(nombre: String) {
        regla.waitForIdle()
        File(carpeta, nombre).outputStream().use {
            regla.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun pdf(version: Int): File {
        val destino = File(contexto.cacheDir, "camara/entrega-$codigo-v$version.pdf")
        destino.parentFile!!.mkdirs()
        val documento = PdfDocument()
        try {
            val pagina = documento.startPage(PdfDocument.PageInfo.Builder(420, 595, 1).create())
            val pintura = android.graphics.Paint().apply { textSize = 18f }
            pagina.canvas.drawText("PROYECTO APS", 32f, 60f, pintura)
            pagina.canvas.drawText("Documento ficticio de prueba", 32f, 95f, pintura)
            pagina.canvas.drawText("Versión $version · $codigo", 32f, 130f, pintura)
            documento.finishPage(pagina)
            destino.outputStream().use { documento.writeTo(it) }
        } finally { documento.close() }
        return destino
    }
    private fun adjuntar(archivo: File, boton: String) {
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
        val filtro = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*")
        }
        // Devuelve un PDF ficticio al selector; la importación de la aplicación se ejecuta completa.
        val monitor = instrumento.addMonitor(filtro, Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)), true)
        try {
            regla.onNodeWithText(boton).performScrollTo().performClick()
            regla.waitUntil(20000) { monitor.hits > 0 }
            regla.waitUntil(20000) { regla.onAllNodesWithText(archivo.name).fetchSemanticsNodes().isNotEmpty() }
        } finally { instrumento.removeMonitor(monitor) }
    }
    private fun abrirAdministrativo(id: String) {
        val tag = "documento-admin-$id"
        regla.onNodeWithTag(tag).performScrollTo()
        regla.onNode(hasText("Abrir documento") and hasAnyAncestor(hasTestTag(tag))).performClick()
    }

    @Test fun cargarRechazarCorregirAprobarSolicitarResolverYConsultarAuditoria() {
        val v1 = pdf(1); val v2 = pdf(2)
        try {
            entrar("c.rojas", "Funcionario")
            regla.onNodeWithText("Cargar documento").performScrollTo().performClick()
            adjuntar(v1, "Seleccionar PDF o imagen")
            regla.onNodeWithText("Nombre del documento").performTextReplacement(nombre)
            regla.onNodeWithText("Vencimiento opcional").performScrollTo().performTextReplacement(
                LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")))
            regla.onNodeWithText("Guardar documento").performScrollTo().performClick()
            regla.waitUntil(15000) { DatosFuncionario.documentos.any { it.nombre == nombre && it.archivoUri.isNotBlank() } }
            val id = DatosFuncionario.documentos.first { it.nombre == nombre }.id
            assertEquals(EstadoDocumento.PENDIENTE, DatosFuncionario.documentos.first { it.id == id }.estado)
            pasos += "Funcionario cargó PDF real en estado Pendiente"
            regla.onNodeWithText(nombre).performScrollTo().assertExists()
            captura("01-documento-cargado.png")
            salirFuncionario()

            entrar("administrativo", "Administrativo")
            abrirAdministrativo(id)
            regla.onNodeWithText("Motivo del rechazo").performScrollTo().performTextInput("Falta la firma ficticia de la versión inicial")
            regla.onNodeWithText("Rechazar", substring = false).performScrollTo().performClick()
            regla.waitUntil(10000) { DatosFuncionario.documentos.first { it.id == id }.estado == EstadoDocumento.RECHAZADO }
            pasos += "Administrativo rechazó con observación"
            regla.onNodeWithText("Cerrar sesión").performScrollTo().performClick()

            entrar("c.rojas", "Funcionario")
            regla.onNodeWithText("Documentos").performClick()
            regla.onNodeWithText(nombre).performScrollTo().performClick()
            regla.onNodeWithText("Falta la firma ficticia de la versión inicial").assertExists()
            regla.onNodeWithText("Corregir documento", substring = false).performClick()
            adjuntar(v2, "Seleccionar PDF o imagen")
            regla.onNodeWithText("Enviar corrección").performScrollTo().performClick()
            regla.waitUntil(15000) { DatosFuncionario.documentos.first { it.id == id }.version == 2 }
            val corregido = DatosFuncionario.documentos.first { it.id == id }
            assertEquals(EstadoDocumento.PENDIENTE, corregido.estado)
            assertEquals(1, corregido.historial.single().version)
            assertNotEquals(corregido.archivoUri, corregido.historial.single().archivoUri)
            assertTrue(File(requireNotNull(android.net.Uri.parse(corregido.historial.single().archivoUri).path)).isFile)
            regla.onNodeWithText("Historial de versiones").assertExists()
            regla.onNodeWithText("Cerrar", substring = false).performClick()
            pasos += "Funcionario corrigió conservando ID, PDF anterior e historial v1/v2"
            captura("02-documento-corregido.png")
            salirFuncionario()

            entrar("administrativo", "Administrativo")
            abrirAdministrativo(id)
            regla.onNodeWithText("Aprobar", substring = false).performScrollTo().performClick()
            regla.waitUntil(10000) { DatosFuncionario.documentos.first { it.id == id }.estado == EstadoDocumento.APROBADO }
            pasos += "Administrativo aprobó la versión corregida"
            regla.onNodeWithTag("documento-admin-$id").performScrollTo()
            captura("03-documento-aprobado.png")
            regla.onNodeWithText("Cerrar sesión").performScrollTo().performClick()

            entrar("c.rojas", "Funcionario")
            regla.onNodeWithText("Nueva solicitud").performScrollTo().performClick()
            regla.onNodeWithText("Otro", substring = false).performScrollTo().performClick()
            val fecha = (1L..90L).map { LocalDate.now().plusDays(it) }.first { dia ->
                DatosSolicitudes.estado.solicitudes.none {
                    it.estado != EstadoSolicitud.RECHAZADA && !dia.isBefore(LocalDate.parse(it.desde)) && !dia.isAfter(LocalDate.parse(it.hasta))
                }
            }.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
            regla.onNodeWithText("Desde", substring = false).performTextReplacement(fecha)
            regla.onNodeWithText("Hasta", substring = false).performTextReplacement(fecha)
            regla.onNodeWithText("Motivo", substring = false).performScrollTo().performTextInput(motivo)
            adjuntar(v2, "Elegir archivo")
            regla.onNodeWithText("Enviar solicitud", substring = false).performScrollTo().performClick()
            regla.waitUntil(15000) { DatosSolicitudes.estado.solicitudes.any { it.motivo == motivo } }
            val solicitudId = DatosSolicitudes.estado.solicitudes.first { it.motivo == motivo }.id
            assertEquals(EstadoSolicitud.EN_REVISION, DatosSolicitudes.estado.solicitudes.first { it.id == solicitudId }.estado)
            pasos += "Funcionario envió solicitud con PDF de respaldo"
            captura("04-solicitud-enviada.png")
            regla.onNodeWithText("Volver al inicio").performScrollTo().performClick()
            regla.onNodeWithContentDescription("Cerrar sesión").performClick()

            entrar("jefatura", "Jefatura")
            val tag = "solicitud-jefatura-$solicitudId"
            regla.onNodeWithTag(tag).performScrollTo()
            regla.onNode(hasText("Abrir solicitud") and hasAnyAncestor(hasTestTag(tag))).performClick()
            regla.onNodeWithText("Respuesta de Jefatura").performScrollTo().performTextInput(respuesta)
            regla.onNodeWithText("Aprobar", substring = false).performScrollTo().performClick()
            regla.waitUntil(10000) { DatosSolicitudes.estado.solicitudes.first { it.id == solicitudId }.estado == EstadoSolicitud.APROBADA }
            pasos += "Jefatura aprobó con respuesta y quedó registrada la resolución"
            regla.onNodeWithText("Cerrar sesión").performScrollTo().performClick()

            entrar("c.rojas", "Funcionario")
            regla.onNodeWithText("Solicitudes", substring = false).performClick()
            regla.onNodeWithTag("solicitud-funcionario-$solicitudId").performScrollTo().performClick()
            regla.onNodeWithText(respuesta).assertExists()
            regla.onNodeWithText("Cerrar", substring = false).performClick()
            regla.onNodeWithTag("solicitud-funcionario-$solicitudId").performScrollTo()
            captura("05-solicitud-aprobada.png")
            pasos += "Funcionario consultó la respuesta aprobada"
            salirFuncionario()

            entrar("d.herrera", "TI / Admin")
            regla.onNodeWithText("Funcionario, RUN o documento").performTextInput(nombre)
            val eventos = FiltroAuditoria(busqueda = nombre, periodo = "Todo el historial").aplicar(AuditoriaLocal.eventos)
            assertTrue(eventos.map { it.accion }.containsAll(listOf("Cargó", "Rechazó", "Modificó", "Validó", "Vio")))
            assertTrue(eventos.all { it.entidadId == id })
            assertTrue(CsvAuditoria.generar(eventos).contains(nombre))
            regla.onNodeWithText("Actividad reciente").performScrollTo()
            captura("06-auditoria-filtrada.png")
            regla.onAllNodesWithText("Ver historial del documento ›").onFirst().performScrollTo().performClick()
            regla.onNodeWithText("Historial del documento").assertExists()
            pasos += "TI consultó la trazabilidad completa y se verificó el contenido CSV filtrado"
            captura("07-historial-documento.png")
            File(carpeta, "resultado-circuito.json").writeText(JSONObject().apply {
                put("resultado", "APROBADO"); put("fechaDispositivo", LocalDateTime.now().toString())
                put("documentoId", id); put("nombreDocumento", nombre); put("version", 2); put("estadoDocumento", "APROBADO")
                put("solicitudId", solicitudId); put("estadoSolicitud", "APROBADA"); put("pasos", JSONArray(pasos))
                put("accionesDocumento", JSONArray(eventos.map { it.accion }.distinct()))
                put("sdk", android.os.Build.VERSION.SDK_INT); put("dispositivo", android.os.Build.MODEL)
                put("selector", "ACTION_OPEN_DOCUMENT interceptado con PDF ficticio; importación y persistencia reales")
            }.toString(2), Charsets.UTF_8)
        } finally { v1.delete(); v2.delete() }
    }
}
