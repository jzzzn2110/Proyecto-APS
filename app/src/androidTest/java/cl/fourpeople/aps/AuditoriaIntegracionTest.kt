package cl.fourpeople.aps

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

class AuditoriaIntegracionTest {
    @Test fun documentoRevisionConsultaYBajaConservanAuditoriaAlRecargar() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val carpeta = File(base.cacheDir, "auditoria-test-${UUID.randomUUID()}").apply { mkdirs() }
        val contexto = object : ContextWrapper(base) {
            override fun getFilesDir(): File = carpeta
            override fun getSharedPreferences(nombre: String, modo: Int) = base.getSharedPreferences("test-$nombre", modo)
        }
        val documentosOriginales = DatosFuncionario.documentos.toList()
        val administracionOriginal = DatosAdministracion.estado
        val auditoriaOriginal = AuditoriaLocal.eventosDocumentales
        try {
            DatosFuncionario.documentos.clear()
            DatosAdministracion.estado = EstadoAdministracion()
            AuditoriaLocal.eventosDocumentales = emptyList()
            PersistenciaDocumentos.guardar(contexto, emptyList())
            SesionLocal.guardar(contexto, "c.rojas", "Funcionario")
            val documento = DocumentoFuncionario("DOC-PRUEBA", "Anexo de prueba", "Anexo", "02/11/2026")
            PersistenciaDocumentos.guardar(contexto, listOf(documento))
            DatosFuncionario.documentos.add(documento)
            assertEquals("c.rojas", AuditoriaLocal.eventosDocumentales.single().usuario)
            assertEquals("Cargó", AuditoriaLocal.eventosDocumentales.single().accion)

            SesionLocal.guardar(contexto, "administrativo", "Administrativo")
            DatosAdministracion.revisar(contexto, "Administrativo", documento, false, "Falta firma de prueba")
            val rechazado = DatosFuncionario.documentos.single()
            val corregido = rechazado.copy(estado = EstadoDocumento.PENDIENTE, motivoRechazo = "",
                version = 2, historial = listOf(rechazado))
            SesionLocal.guardar(contexto, "c.rojas", "Funcionario")
            PersistenciaDocumentos.guardar(contexto, listOf(corregido))
            DatosFuncionario.documentos.clear(); DatosFuncionario.documentos.add(corregido)
            SesionLocal.guardar(contexto, "administrativo", "Administrativo")
            DatosAdministracion.revisar(contexto, "Administrativo", corregido, true, "")
            val aprobado = DatosFuncionario.documentos.single()
            AuditoriaLocal.registrar(contexto, AuditoriaLocal.evento(contexto, aprobado.id, aprobado.nombre, "Vio", aprobado.categoria))
            DatosAdministracion.darDeBaja(contexto, "Administrativo", aprobado, "Baja de prueba documentada")
            val antes = AuditoriaLocal.eventosDocumentales
            val cargados = PersistenciaDocumentos.cargar(contexto, emptyList())
            assertEquals(antes, AuditoriaLocal.eventosDocumentales)
            assertEquals(listOf("Cargó", "Rechazó", "Modificó", "Validó", "Vio", "Dio de baja"), antes.map { it.accion })
            assertEquals(2, cargados.single().version)
            assertEquals(1, cargados.single().historial.size)
            assertFalse(DatosAdministracion.activo(documento.id))

            DatosSolicitudes.cargar(contexto)
            SesionLocal.guardar(contexto, "c.rojas", "Funcionario")
            val fecha = LocalDate.now().plusDays(1).toString()
            val solicitud = SolicitudFuncionario("SOL-PRUEBA", TipoSolicitud.OTRO, fecha, fecha,
                JornadaSolicitud.COMPLETA, "Solicitud de prueba aislada", java.time.LocalDateTime.now().toString())
            DatosSolicitudes.enviar(contexto, solicitud)
            SesionLocal.guardar(contexto, "jefatura", "Jefatura")
            DatosSolicitudes.resolver(contexto, "Jefatura", solicitud.id, true, "Autorización de prueba")
            DatosSolicitudes.cargar(contexto)
            assertEquals(listOf("Solicitó", "Aprobó solicitud"), DatosSolicitudes.estado.auditoria.map { it.accion })
            assertEquals("jefatura", DatosSolicitudes.estado.auditoria.last().usuario)
        } finally {
            DatosFuncionario.documentos.clear(); DatosFuncionario.documentos.addAll(documentosOriginales)
            DatosAdministracion.estado = administracionOriginal
            AuditoriaLocal.eventosDocumentales = auditoriaOriginal
            DatosSolicitudes.cargar(base)
            contexto.getSharedPreferences("sesion_local", 0).edit().clear().commit()
            carpeta.deleteRecursively()
        }
    }
}
