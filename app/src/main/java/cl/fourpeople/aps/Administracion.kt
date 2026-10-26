package cl.fourpeople.aps

import android.content.Context
import androidx.compose.runtime.*
import java.time.LocalDate
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
data class EstadoAdministracion(val expediente: ExpedienteLaboral = ExpedienteLaboral())

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
            PersistenciaDocumentos.guardar(contexto, lista)
            DatosFuncionario.documentos.clear()
            DatosFuncionario.documentos.addAll(lista)
        }
    }
}
