package cl.fourpeople.aps

import android.content.Context
import androidx.compose.runtime.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// datos iniciales: se usan si no hay un expediente guardado.
// Durante la demostración, se cambiann estos datos desde editar expediente.
data class ExpedienteLaboral(
    val nombre: String = "Camila Rojas Soto",
    val run: String = "DEMO-CAMILA",
    val centro: String = "CESFAM Norte",
    val cargo: String = "Técnico de enfermería",
    val contrato: String = "Plazo fijo",
    val ingreso: String = "15/03/2021"
)

// Reúne los datos administrativos que se guardan juntos.
data class CambioAdministrativo(
    val id: String, val responsable: String, val fecha: String,
    val entidad: String, val accion: String, val motivo: String,
    val antes: String, val despues: String
)
// Guarda qué documento se dio de baja, quién lo hizo y el motivo.
data class BajaDocumental(
    val documentoId: String,
    val responsable: String,
    val fecha: String,
    val motivo: String
)

data class EstadoAdministracion(
    val expediente: ExpedienteLaboral = ExpedienteLaboral(),
    val cambios: List<CambioAdministrativo> = emptyList(),
    // Conserva el registro de los documentos dados de baja.
    val bajas: List<BajaDocumental> = emptyList()
) {
    fun registrar(entidad: String, accion: String, motivo: String, antes: String, despues: String) =
        copy(cambios = cambios + CambioAdministrativo(UUID.randomUUID().toString(),
            "administrativo", LocalDateTime.now().toString(), entidad, accion, motivo, antes, despues))
}

// Comparte el mismo expediente entre las pantallas.
object DatosAdministracion {
    // Si cambia este valor, Compose actualiza los textos que lo muestran.
    var estado by mutableStateOf(EstadoAdministracion())
        internal set
    // Evita que dos operaciones de este módulo se ejecuten al mismo tiempo.
    private val bloqueo = Mutex()
    // Formato de las fechas: día/mes/año. Rechaza fechas que no existen.
    val formato: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT)

    // Devuelve true si el documento no está dado de baja.
    fun activo(id: String): Boolean {
        return estado.bajas.none { it.documentoId == id }
    }

    // PERMISO: solo Administrativo puede editar o revisar aquí.
    private fun autorizar(perfil: String) {
        require(perfil == "Administrativo") { "Se requiere el perfil Administrativo." }
    }

    // Guarda nombre, cargo, contrato y fecha de ingreso.
    suspend fun guardarExpediente(contexto: Context, perfil: String, nuevo: ExpedienteLaboral) {
        autorizar(perfil)
        // reglas: comprueba los campos antes de guardar.
        require(nuevo.nombre.trim().length in 3..120) { "Nombre: entre 3 y 120 caracteres." }
        require(nuevo.cargo.trim().length in 3..120) { "Cargo: entre 3 y 120 caracteres." }
        require(nuevo.contrato.trim().length in 3..120) { "Contrato: entre 3 y 120 caracteres." }
        LocalDate.parse(nuevo.ingreso.trim(), formato)
        bloqueo.withLock {
            // RUN y centro no se pueden cambiar con este formulario.
            require(nuevo.run == estado.expediente.run && nuevo.centro == estado.expediente.centro) {
                "Este perfil no puede cambiar RUN ni centro."
            }
            // copy crea la nueva versión y conserva los demás datos.
            val siguiente = estado.copy(expediente = nuevo.copy(
                nombre = nuevo.nombre.trim(), cargo = nuevo.cargo.trim(),
                contrato = nuevo.contrato.trim(), ingreso = nuevo.ingreso.trim()))
                .registrar(nuevo.run, "EDITAR_EXPEDIENTE", "Actualización de antecedentes laborales",
                    estado.expediente.toString(), nuevo.toString())
            PersistenciaDocumentos.guardar(contexto, DatosFuncionario.documentos.toList(), siguiente)
            // Actualiza la pantalla después de guardar correctamente.
            estado = siguiente
        }
    }

    // Aprobar o rechazar un documento que todavía está pendiente.
    suspend fun revisar(contexto: Context, perfil: String, original: DocumentoFuncionario,
                        aprobar: Boolean, motivo: String) {
        autorizar(perfil)
        // El rechazo necesita una explicación de 10 a 500 caracteres.
        require(aprobar || motivo.trim().length in 10..500) { "Motivo: entre 10 y 500 caracteres." }
        bloqueo.withLock {
            // Los documentos dados de baja no se pueden modificar.
            require(activo(original.id)) { "El documento está dado de baja." }
            // Comprueba que nadie haya cambiado el documento abierto.
            val actual = DatosFuncionario.documentos.firstOrNull { it.id == original.id }
            require(actual == original && actual?.estado == EstadoDocumento.PENDIENTE) {
                "El documento cambió o ya fue revisado. Vuelve a abrirlo."
            }
            // Cambia el estado y conserva el archivo y su historial.
            val nuevo = original.copy(
                estado = if (aprobar) EstadoDocumento.APROBADO else EstadoDocumento.RECHAZADO,
                motivoRechazo = if (aprobar) "" else motivo.trim())
            // Reemplaza solo el documento con el mismo identificador.
            val lista = DatosFuncionario.documentos.map { if (it.id == original.id) nuevo else it }
            // Primero guarda en el teléfono y después actualiza la lista visible.
            val siguiente = estado.registrar(original.id,
                if (aprobar) "APROBAR_DOCUMENTO" else "RECHAZAR_DOCUMENTO",
                if (aprobar) "Documento validado" else motivo.trim(), original.toString(), nuevo.toString())
            PersistenciaDocumentos.guardar(contexto, lista, siguiente)
            estado = siguiente
            DatosFuncionario.documentos.clear()
            DatosFuncionario.documentos.addAll(lista)
        }
    }
    suspend fun corregirMetadatos(contexto: Context, perfil: String, original: DocumentoFuncionario,
                                  propuesta: DocumentoFuncionario, motivo: String) {
        autorizar(perfil)
        require(motivo.trim().length in 10..500) { "Motivo: entre 10 y 500 caracteres." }
        require(propuesta.nombre.trim().length in 3..120) { "Nombre: entre 3 y 120 caracteres." }
        require(propuesta.categoria in listOf("Contrato", "Anexo", "Capacitación", "Resolución", "Decreto", "Otro")) {
            "Selecciona una categoría válida."
        }
        val fecha = LocalDate.parse(propuesta.fecha.trim(), formato)
        if (propuesta.vencimiento.isNotBlank()) {
            require(!LocalDate.parse(propuesta.vencimiento.trim(), formato).isBefore(fecha)) {
                "El vencimiento no puede ser anterior al documento."
            }
        }
        require(propuesta.categoria != "Capacitación" || (propuesta.horas ?: 0) in 1..10000) {
            "Las horas de capacitación deben estar entre 1 y 10000."
        }
        bloqueo.withLock {
            // Los documentos dados de baja no se pueden modificar.
            require(activo(original.id)) { "El documento está dado de baja." }
            require(DatosFuncionario.documentos.firstOrNull { it.id == original.id } == original) {
                "El documento cambió. Vuelve a abrirlo."
            }
            val nuevo = original.copy(nombre = propuesta.nombre.trim(), categoria = propuesta.categoria,
                fecha = fecha.format(formato), vencimiento = propuesta.vencimiento.trim(),
                horas = if (propuesta.categoria == "Capacitación") propuesta.horas else null,
                estado = EstadoDocumento.PENDIENTE, motivoRechazo = "", version = original.version + 1,
                historial = original.historial + original.copy(historial = emptyList()))
            val lista = DatosFuncionario.documentos.map { if (it.id == original.id) nuevo else it }
            val siguiente = estado.registrar(original.id, "CORREGIR_METADATOS", motivo.trim(),
                original.copy(historial = emptyList()).toString(), nuevo.copy(historial = emptyList()).toString())
            PersistenciaDocumentos.guardar(contexto, lista, siguiente)
            estado = siguiente
            DatosFuncionario.documentos.clear()
            DatosFuncionario.documentos.addAll(lista)
        }
    }

    // Da de baja el documento y conserva su archivo e historial.
    suspend fun darDeBaja(
        contexto: Context,
        perfil: String,
        original: DocumentoFuncionario,
        motivo: String
    ) {
        // Solo el perfil Administrativo puede dar documentos de baja.
        autorizar(perfil)
        val motivoLimpio = motivo.trim()
        require(motivoLimpio.length in 10..500) {
            "Motivo: entre 10 y 500 caracteres."
        }

        bloqueo.withLock {
            // Evita dar de baja el mismo documento dos veces.
            require(activo(original.id)) { "El documento ya está dado de baja." }

            // Comprueba que el documento siga siendo el que se abrió.
            val actual = DatosFuncionario.documentos.firstOrNull { it.id == original.id }
            require(actual == original) { "El documento cambió. Vuelve a abrirlo." }

            // El usuario es fijo porque esta es una sesión de prueba.
            val baja = BajaDocumental(
                documentoId = original.id,
                responsable = "administrativo",
                fecha = LocalDateTime.now().toString(),
                motivo = motivoLimpio
            )

            // Agrega la baja y deja constancia en el historial.
            val siguiente = estado.copy(bajas = estado.bajas + baja).registrar(
                entidad = original.id,
                accion = "BAJA_DOCUMENTAL",
                motivo = motivoLimpio,
                antes = "Activo · v${original.version}",
                despues = "Baja lógica · archivo conservado"
            )

            // Conserva todos los documentos sin eliminar sus archivos.
            PersistenciaDocumentos.guardar(
                contexto,
                DatosFuncionario.documentos.toList(),
                siguiente
            )
            estado = siguiente
        }
    }
}
