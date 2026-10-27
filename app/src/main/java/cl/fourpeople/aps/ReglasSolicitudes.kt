package cl.fourpeople.aps

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class TipoSolicitud(val texto: String) {
    ADMINISTRATIVO("Permiso administrativo"),
    FERIADO("Feriado legal"),
    COMPENSATORIO("Día compensatorio"),
    OTRO("Otro")
}

enum class JornadaSolicitud(val texto: String, val factor: Double) {
    COMPLETA("Jornada completa", 1.0),
    MANANA("Media jornada AM", 0.5),
    TARDE("Media jornada PM", 0.5)
}

enum class EstadoSolicitud(val texto: String) {
    EN_REVISION("En revisión"), APROBADA("Aprobada"), RECHAZADA("Rechazada")
}

data class SolicitudFuncionario(
    val id: String,
    val tipo: TipoSolicitud,
    val desde: String,
    val hasta: String,
    val jornada: JornadaSolicitud,
    val motivo: String,
    val creadaEn: String,
    val estado: EstadoSolicitud = EstadoSolicitud.EN_REVISION,
    // Respuesta que escribe Jefatura al aprobar o rechazar.
    val respuesta: String = "",

    // Dirección del archivo adjunto de la solicitud.
    val archivoUri: String = "",

    // Usuario que revisó la solicitud.
    val revisadaPor: String = "",

    // Fecha y hora en que se aprobó o rechazó.
    val revisadaEn: String = ""
) {
    val dias: Double get() = (
            ChronoUnit.DAYS.between(LocalDate.parse(desde), LocalDate.parse(hasta)) + 1
            ) * jornada.factor
}

fun textoDias(dias: Double): String =
    if (dias == dias.toInt().toDouble()) dias.toInt().toString()
    else dias.toString().replace('.', ',')

object ReglasSolicitudes {
    fun saldo(
        solicitudes: List<SolicitudFuncionario>,
        tipo: TipoSolicitud,
        ano: Int,
        reservarPendientes: Boolean = true
    ): Double {
        val total = when (tipo) {
            TipoSolicitud.ADMINISTRATIVO -> 6.0
            TipoSolicitud.FERIADO -> 15.0
            else -> return Double.POSITIVE_INFINITY
        }
        val usados = solicitudes.filter {
            it.tipo == tipo && LocalDate.parse(it.desde).year == ano &&
                    (it.estado == EstadoSolicitud.APROBADA ||
                            (reservarPendientes && it.estado == EstadoSolicitud.EN_REVISION))
        }.sumOf { it.dias }
        return (total - usados).coerceAtLeast(0.0)
    }

    fun validar(
        nueva: SolicitudFuncionario,
        existentes: List<SolicitudFuncionario>,
        hoy: LocalDate = LocalDate.now()
    ) {
        val desde = LocalDate.parse(nueva.desde)
        val hasta = LocalDate.parse(nueva.hasta)
        require(!desde.isBefore(hoy)) { "La fecha inicial no puede estar en el pasado." }
        require(!hasta.isBefore(desde)) { "Hasta debe ser igual o posterior a Desde." }
        require(desde.year == hasta.year) { "Selecciona fechas del mismo año." }
        require(ChronoUnit.DAYS.between(desde, hasta) < 365) {
            "El intervalo no puede superar 365 días."
        }
        require(nueva.jornada == JornadaSolicitud.COMPLETA || desde == hasta) {
            "Para media jornada, Desde y Hasta deben ser el mismo día."
        }
        require(nueva.motivo.trim().length in 10..500) {
            "El motivo debe tener entre 10 y 500 caracteres."
        }
        require(existentes.none {
            it.estado != EstadoSolicitud.RECHAZADA &&
                    !hasta.isBefore(LocalDate.parse(it.desde)) &&
                    !desde.isAfter(LocalDate.parse(it.hasta)) &&
                    !(desde == hasta && it.desde == it.hasta &&
                            nueva.jornada != JornadaSolicitud.COMPLETA &&
                            it.jornada != JornadaSolicitud.COMPLETA && nueva.jornada != it.jornada)
        }) { "Ya tienes una solicitud aprobada o en revisión en ese período y jornada." }
        require(nueva.dias <= saldo(existentes, nueva.tipo, desde.year)) {
            "El período supera tu saldo disponible, considerando las solicitudes en revisión."
        }
    }
}
