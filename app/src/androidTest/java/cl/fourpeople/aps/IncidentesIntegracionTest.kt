package cl.fourpeople.aps

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class IncidentesIntegracionTest {
    @Test fun incidentesBloqueosYEventosSeConservanYRequierenTi() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val sufijo = UUID.randomUUID().toString()
        val carpeta = File(base.cacheDir, "incidentes-test-$sufijo").apply { mkdirs() }
        val contexto = object : ContextWrapper(base) {
            override fun getFilesDir(): File = carpeta
            override fun getSharedPreferences(nombre: String, modo: Int) = base.getSharedPreferences("$sufijo-$nombre", modo)
        }
        val eventosAnteriores = AuditoriaLocal.eventosDocumentales
        try {
            DatosSeguridad.cargar(contexto)
            val evento = EventoAuditoria(id = "EV-PRUEBA", usuario = "c.rojas", rol = "Funcionario",
                entidadId = "DOC-1", entidad = "Anexo", accion = "Vio", centro = "CESFAM Norte",
                categoria = "Anexo", titular = "Camila Rojas", run = "DEMO-CAMILA")
            AuditoriaLocal.eventosDocumentales = listOf(evento)
            SesionLocal.guardar(contexto, "c.rojas", "Funcionario")
            try {
                DatosSeguridad.crear(contexto, "Acceso inusual", "Consulta bajo investigación", "c.rojas", PrioridadIncidente.ALTA, setOf(evento.id))
                fail("Funcionario no debe crear incidentes")
            } catch (_: IllegalArgumentException) { }
            SesionLocal.guardar(contexto, "c.rojas", "TI / Admin")
            try {
                DatosSeguridad.crear(contexto, "Acceso inusual", "Consulta bajo investigación", "c.rojas", PrioridadIncidente.ALTA, setOf(evento.id))
                fail("Cambiar solo el nombre del rol no debe autorizar")
            } catch (_: IllegalArgumentException) { }
            SesionLocal.guardar(contexto, "d.herrera", "TI / Admin")
            val id = DatosSeguridad.crear(contexto, "Acceso inusual", "Consulta bajo investigación", "c.rojas", PrioridadIncidente.ALTA, setOf(evento.id))
            val original = DatosSeguridad.estado.incidentes.single()
            DatosSeguridad.cambiarEstado(contexto, original, EstadoIncidente.EN_REVISION, "Revisión de eventos iniciada")
            try {
                DatosSeguridad.cambiarEstado(contexto, original, EstadoIncidente.EN_REVISION, "Operación duplicada de prueba")
                fail("Debe rechazar una versión desactualizada")
            } catch (_: IllegalArgumentException) { }
            DatosSeguridad.cambiarCuenta(contexto, id, true, "Bloqueo preventivo autorizado")
            assertNotNull(AccesoCuentas.errorIngreso("c.rojas", "Contraseña123#", "Funcionario", DatosSeguridad.estado.bloqueadas))
            val antes = DatosSeguridad.estado
            DatosSeguridad.cargar(contexto)
            assertEquals(antes, DatosSeguridad.estado)
            assertEquals(setOf(evento.id), DatosSeguridad.estado.incidentes.single().eventosIds)
            DatosSeguridad.cambiarCuenta(contexto, id, false, "Identidad y acceso verificados")
            assertNull(AccesoCuentas.errorIngreso("c.rojas", "Contraseña123#", "Funcionario", DatosSeguridad.estado.bloqueadas))
            DatosSeguridad.cambiarEstado(contexto, DatosSeguridad.estado.incidentes.single(), EstadoIncidente.CERRADO, "Investigación completada y resuelta")
            DatosSeguridad.cargar(contexto)
            assertEquals(EstadoIncidente.CERRADO, DatosSeguridad.estado.incidentes.single().estado)
            assertEquals(listOf("Registró incidente", "Revisó incidente", "Bloqueó cuenta", "Reactivó cuenta", "Cerró incidente"),
                DatosSeguridad.estado.auditoria.map { it.accion })
            assertTrue(AuditoriaLocal.eventos.any { it.entidadId == id })
            val propio = DatosSeguridad.crear(contexto, "Incidente propio", "Reporte de prueba sobre TI", "d.herrera", PrioridadIncidente.BAJA, emptySet())
            try {
                DatosSeguridad.cambiarCuenta(contexto, propio, true, "Bloqueo propio no permitido")
                fail("TI no debe bloquear su propia cuenta")
            } catch (_: IllegalArgumentException) { }
            val guardado = File(carpeta, "seguridad_ti.json").readText()
            File(carpeta, "seguridad_ti.json").writeText("archivo inválido")
            try { DatosSeguridad.cargar(contexto); fail("No debe ignorar datos corruptos")
            } catch (_: java.io.IOException) { }
            assertEquals("archivo inválido", File(carpeta, "seguridad_ti.json").readText())
            File(carpeta, "seguridad_ti.json").writeText(guardado)
        } finally {
            AuditoriaLocal.eventosDocumentales = eventosAnteriores
            DatosSeguridad.cargar(base)
            contexto.getSharedPreferences("sesion_local", 0).edit().clear().commit()
            carpeta.deleteRecursively()
        }
    }
}
