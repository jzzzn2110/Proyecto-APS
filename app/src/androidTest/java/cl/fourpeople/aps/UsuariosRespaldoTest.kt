package cl.fourpeople.aps

import android.content.Context
import android.content.ContextWrapper
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class UsuariosRespaldoTest {
    private val base get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun contexto(carpeta: File, sufijo: String) = object : ContextWrapper(base) {
        override fun getFilesDir(): File = carpeta
        override fun getSharedPreferences(nombre: String, modo: Int) = base.getSharedPreferences("$sufijo-$nombre", modo)
    }
    private suspend fun cargar(contexto: Context) {
        RespaldoLocal.recuperarPendiente(contexto)
        DatosUsuarios.cargar(contexto)
        val docs = PersistenciaDocumentos.cargar(contexto, emptyList())
        DatosFuncionario.documentos.clear(); DatosFuncionario.documentos.addAll(docs)
        DatosSolicitudes.cargar(contexto); DatosSeguridad.cargar(contexto)
    }
    private fun pdf(archivo: File) {
        val doc = PdfDocument()
        try {
            val pagina = doc.startPage(PdfDocument.PageInfo.Builder(200, 200, 1).create())
            pagina.canvas.drawText("Documento ficticio", 10f, 30f, android.graphics.Paint())
            doc.finishPage(pagina); archivo.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
    }
    @Test fun usuariosPermisosSaldosDescargaYRespaldoCompleto() = runBlocking {
        val sufijo = UUID.randomUUID().toString()
        val carpeta = File(base.cacheDir, "usuarios-test-$sufijo").apply { mkdirs() }
        val ctx = contexto(carpeta, sufijo)
        val docsOriginales = DatosFuncionario.documentos.toList()
        val adminOriginal = DatosAdministracion.estado
        val eventosOriginales = AuditoriaLocal.eventosDocumentales
        var zip: File? = null
        try {
            DatosUsuarios.cargar(ctx); DatosSeguridad.cargar(ctx); DatosSolicitudes.cargar(ctx)
            DatosAdministracion.estado = EstadoAdministracion()
            AuditoriaLocal.eventosDocumentales = emptyList(); DatosFuncionario.documentos.clear()
            val adjuntos = File(carpeta, "documentos").apply { mkdirs() }
            val archivo = File(adjuntos, "actual.pdf"); pdf(archivo)
            val anterior = File(adjuntos, "anterior.pdf"); archivo.copyTo(anterior)
            val viejo = DocumentoFuncionario("DOC-RESPALDO", "Contrato anterior", "Contrato", "01/11/2026", archivoUri = Uri.fromFile(anterior).toString())
            val documento = viejo.copy(nombre = "Contrato vigente", archivoUri = Uri.fromFile(archivo).toString(), version = 2, historial = listOf(viejo))
            SesionLocal.guardar(ctx, "c.rojas", "Funcionario")
            PersistenciaDocumentos.guardar(ctx, listOf(documento)); DatosFuncionario.documentos.add(documento)
            val fecha = LocalDate.now().plusDays(1).toString()
            DatosSolicitudes.enviar(ctx, SolicitudFuncionario("SOL-RESPALDO", TipoSolicitud.OTRO, fecha, fecha,
                JornadaSolicitud.COMPLETA, "Solicitud ficticia para respaldo", java.time.LocalDateTime.now().toString(), archivoUri = documento.archivoUri))
            SesionLocal.guardar(ctx, "d.herrera", "TI / Admin")
            val admin = PerfilesCompartidos.perfiles.first { it.usuario == "administrativo" }
            DatosUsuarios.guardarUsuario(ctx, admin, admin.copy(permisos = admin.permisos - "documentos.revisar"))
            SesionLocal.guardar(ctx, "administrativo", "Administrativo")
            try { DatosAdministracion.revisar(ctx, "Administrativo", documento, true, ""); fail("Debe denegar el permiso revocado")
            } catch (_: IllegalArgumentException) { }
            SesionLocal.guardar(ctx, "d.herrera", "TI / Admin")
            var actual = PerfilesCompartidos.perfiles.first { it.usuario == "administrativo" }
            DatosUsuarios.guardarUsuario(ctx, actual, actual.copy(centro = "CESFAM Sur", permisos = admin.permisos + "documentos.descargar"))
            SesionLocal.guardar(ctx, "administrativo", "Administrativo")
            assertFalse(ControlAcceso.puede(ctx, "expedientes.leer", expediente = true))
            try { DatosAdministracion.darDeBaja(ctx, "Administrativo", documento, "Baja fuera de centro"); fail("Debe denegar otro centro")
            } catch (_: IllegalArgumentException) { }
            SesionLocal.guardar(ctx, "d.herrera", "TI / Admin")
            actual = PerfilesCompartidos.perfiles.first { it.usuario == "administrativo" }
            DatosUsuarios.guardarUsuario(ctx, actual, actual.copy(centro = "CESFAM Norte"))
            val nueva = PerfilUsuario("demo.admin", "Cuenta ficticia nueva", RolUsuario.ADMINISTRATIVO, "CESFAM Norte", permisos = admin.permisos)
            DatosUsuarios.guardarUsuario(ctx, null, nueva)
            assertNull(AccesoCuentas.errorIngreso(nueva.usuario, "Contraseña123#", "Administrativo", emptySet()))
            try { DatosUsuarios.guardarUsuario(ctx, null, nueva); fail("Debe rechazar usuarios duplicados")
            } catch (_: IllegalArgumentException) { }
            DatosUsuarios.guardarUsuario(ctx, nueva, nueva.copy(activa = false))
            assertNotNull(AccesoCuentas.errorIngreso(nueva.usuario, "Contraseña123#", "Administrativo", emptySet()))
            val propia = PerfilesCompartidos.perfiles.first { it.usuario == "d.herrera" }
            try { DatosUsuarios.guardarUsuario(ctx, propia, propia.copy(permisos = emptySet())); fail("No debe quitarse su propio acceso")
            } catch (_: IllegalArgumentException) { }
            val operador = PerfilUsuario("operador.ti", "Operador de incidentes", RolUsuario.TI, "Nivel central", permisos = setOf("incidentes.gestionar"))
            DatosUsuarios.guardarUsuario(ctx, null, operador)
            SesionLocal.guardar(ctx, operador.usuario, "TI / Admin")
            val casoTi = DatosSeguridad.crear(ctx, "Revisión de acceso TI", "Reporte ficticio de administración", "d.herrera", PrioridadIncidente.BAJA, emptySet())
            try { DatosSeguridad.cambiarCuenta(ctx, casoTi, true, "Intento de bloquear al último administrador"); fail("Debe conservar el último acceso de administración")
            } catch (_: IllegalArgumentException) { }
            SesionLocal.guardar(ctx, "d.herrera", "TI / Admin")
            val saldoPrevio = ReglasSolicitudes.saldo(DatosSolicitudes.estado.solicitudes, TipoSolicitud.ADMINISTRATIVO, 2026)
            DatosUsuarios.guardarSaldos(ctx, PoliticaSaldos(9, 20))
            assertEquals(saldoPrevio + 3, ReglasSolicitudes.saldo(DatosSolicitudes.estado.solicitudes, TipoSolicitud.ADMINISTRATIVO, 2026), 0.001)
            SesionLocal.guardar(ctx, "administrativo", "Administrativo")
            val descarga = File(carpeta, "exportado.pdf")
            DescargasDocumento.exportar(ctx, documento, Uri.fromFile(descarga))
            assertArrayEquals(archivo.readBytes(), descarga.readBytes())
            assertTrue(AuditoriaLocal.eventos.any { it.accion == "Descargó" })
            SesionLocal.guardar(ctx, "d.herrera", "TI / Admin")
            actual = PerfilesCompartidos.perfiles.first { it.usuario == "administrativo" }
            DatosUsuarios.guardarUsuario(ctx, actual, actual.copy(permisos = actual.permisos - "documentos.descargar"))
            SesionLocal.guardar(ctx, "administrativo", "Administrativo")
            try { DescargasDocumento.exportar(ctx, documento, Uri.fromFile(File(carpeta, "denegado.pdf"))); fail("Debe denegar la descarga")
            } catch (_: IllegalArgumentException) { }
            SesionLocal.guardar(ctx, "d.herrera", "TI / Admin")
            val incidente = DatosSeguridad.crear(ctx, "Incidente de respaldo", "Reporte ficticio conservado en respaldo", "c.rojas", PrioridadIncidente.BAJA, emptySet())
            val usuariosAntes = DatosUsuarios.estado; val seguridadAntes = DatosSeguridad.estado; val solicitudesAntes = DatosSolicitudes.estado
            val bytes = archivo.readBytes()
            val respaldoCompleto = RespaldoLocal.crear(ctx)
            zip = respaldoCompleto
            ZipFile(respaldoCompleto).use { respaldo -> assertNotNull(respaldo.getEntry("documentos/anterior.pdf")); assertNotNull(respaldo.getEntry("usuarios_ti.json")) }
            archivo.writeText("contenido alterado"); anterior.delete()
            DatosUsuarios.guardarSaldos(ctx, PoliticaSaldos(2, 3))
            DatosSeguridad.cambiarCuenta(ctx, incidente, true, "Bloqueo posterior al respaldo")
            val idsAuditoriaAntes = AuditoriaLocal.eventos.map { it.id }.toSet()
            RespaldoLocal.restaurar(ctx, respaldoCompleto)
            assertNull(ControlAcceso.perfil(ctx))
            cargar(ctx)
            assertEquals(usuariosAntes.usuarios, DatosUsuarios.estado.usuarios); assertEquals(usuariosAntes.saldos, DatosUsuarios.estado.saldos)
            assertEquals(seguridadAntes.incidentes, DatosSeguridad.estado.incidentes); assertEquals(seguridadAntes.bloqueadas, DatosSeguridad.estado.bloqueadas)
            assertEquals(solicitudesAntes, DatosSolicitudes.estado)
            assertTrue(AuditoriaLocal.eventos.map { it.id }.toSet().containsAll(idsAuditoriaAntes))
            assertEquals(2, DatosFuncionario.documentos.single().version); assertEquals(1, DatosFuncionario.documentos.single().historial.size)
            assertArrayEquals(bytes, archivo.readBytes()); assertArrayEquals(bytes, anterior.readBytes())
            assertTrue(AuditoriaLocal.eventos.any { it.accion == "Restauró respaldo" })
            SesionLocal.guardar(ctx, "d.herrera", "TI / Admin")
            val raizAntes = File(carpeta, "expediente_funcionario.json").readText()
            val malo = File(carpeta, "malo.zip")
            ZipOutputStream(malo.outputStream()).use { out -> out.putNextEntry(ZipEntry("../fuera.txt")); out.write("no".toByteArray()); out.closeEntry() }
            try { RespaldoLocal.restaurar(ctx, malo); fail("Debe rechazar rutas fuera del respaldo") } catch (_: java.io.IOException) { }
            assertEquals(raizAntes, File(carpeta, "expediente_funcionario.json").readText())
            val partes = ZipFile(respaldoCompleto).use { fuente -> fuente.entries().asSequence().associate { it.name to fuente.getInputStream(it).use { entrada -> entrada.readBytes() } } }
            fun escribirZip(datos: Map<String, ByteArray>) {
                ZipOutputStream(malo.outputStream()).use { salida -> datos.forEach { (nombre, bytesParte) ->
                    salida.putNextEntry(ZipEntry(nombre)); salida.write(bytesParte); salida.closeEntry()
                } }
            }
            escribirZip(partes + ("documentos/actual.pdf" to "alterado".toByteArray()))
            try { RespaldoLocal.restaurar(ctx, malo); fail("Debe rechazar un archivo alterado") } catch (_: java.io.IOException) { }
            assertEquals(raizAntes, File(carpeta, "expediente_funcionario.json").readText())
            val incompleto = partes.toMutableMap().apply { remove("documentos/anterior.pdf") }
            val manifiesto = JSONObject(String(incompleto.getValue("manifest.json"), Charsets.UTF_8))
            manifiesto.getJSONObject("archivos").remove("documentos/anterior.pdf")
            incompleto["manifest.json"] = manifiesto.toString().toByteArray()
            escribirZip(incompleto)
            try { RespaldoLocal.restaurar(ctx, malo); fail("Debe rechazar un adjunto faltante aunque el manifiesto coincida") } catch (_: java.io.IOException) { }
            assertEquals(raizAntes, File(carpeta, "expediente_funcionario.json").readText())
            malo.writeText("ZIP truncado")
            try { RespaldoLocal.restaurar(ctx, malo); fail("Debe rechazar un respaldo inválido") } catch (_: java.io.IOException) { }
            assertEquals(raizAntes, File(carpeta, "expediente_funcionario.json").readText())
        } finally {
            zip?.delete()
            DatosUsuarios.cargar(base); DatosSeguridad.cargar(base); DatosSolicitudes.cargar(base)
            DatosFuncionario.documentos.clear(); DatosFuncionario.documentos.addAll(docsOriginales)
            DatosAdministracion.estado = adminOriginal; AuditoriaLocal.eventosDocumentales = eventosOriginales
            ctx.getSharedPreferences("sesion_local", 0).edit().clear().commit(); carpeta.deleteRecursively()
        }
    }

    @Test fun recuperaUnaRestauracionInterrumpida() = runBlocking {
        val carpeta = File(base.cacheDir, "recuperacion-${UUID.randomUUID()}").apply { mkdirs() }
        val ctx = contexto(carpeta, carpeta.name)
        try {
            val pendiente = File(carpeta, "restauracion_pendiente").apply { mkdirs() }
            val nombres = listOf("expediente_funcionario.json", "solicitudes_funcionario.json", "seguridad_ti.json", "usuarios_ti.json")
            nombres.forEach { File(pendiente, it).writeText("anterior-$it"); File(carpeta, it).writeText("nuevo-$it") }
            File(pendiente, "documentos").mkdirs(); File(pendiente, "documentos/antes.pdf").writeText("anterior")
            File(carpeta, "documentos").mkdirs(); File(carpeta, "documentos/despues.pdf").writeText("nuevo")
            File(pendiente, "estado.json").writeText(JSONObject().put("aplicada", false).put("archivos", JSONArray(nombres)).put("documentos", true).toString())
            RespaldoLocal.recuperarPendiente(ctx)
            nombres.forEach { assertEquals("anterior-$it", File(carpeta, it).readText()) }
            assertTrue(File(carpeta, "documentos/antes.pdf").exists()); assertFalse(File(carpeta, "documentos/despues.pdf").exists()); assertFalse(pendiente.exists())
            RespaldoLocal.recuperarPendiente(ctx)
        } finally { carpeta.deleteRecursively() }
    }
}
