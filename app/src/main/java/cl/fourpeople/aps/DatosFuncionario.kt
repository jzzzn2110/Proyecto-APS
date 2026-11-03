package cl.fourpeople.aps

import androidx.compose.runtime.mutableStateListOf

enum class EstadoDocumento(val texto: String) {
    PENDIENTE("Pendiente"),
    APROBADO("Aprobado"),
    RECHAZADO("Rechazado")
}

data class DocumentoFuncionario(
    val id: String,
    val nombre: String,
    val categoria: String,
    val fecha: String,
    val estado: EstadoDocumento = EstadoDocumento.PENDIENTE,
    val vencimiento: String = "",
    val motivoRechazo: String = "",
    val archivoUri: String = "",
    val horas: Int? = null,
    val version: Int = 1,
    val historial: List<DocumentoFuncionario> = emptyList()
)

object DatosFuncionario {

    // Antecedentes ficticios para la demostración.
    // Oculta las bajas de las listas sin borrar los documentos guardados.
    val documentosActivos: List<DocumentoFuncionario>
        get() = documentos.filter { DatosAdministracion.activo(it.id) }
    val documentos = mutableStateListOf(
        DocumentoFuncionario(
            id = "DOC-001",
            nombre = "Anexo de contrato 2026",
            categoria = "Anexo",
            fecha = "24/09/2026"
        ),

        DocumentoFuncionario(
            id = "DOC-002",
            nombre = "Certificado curso IAAS",
            categoria = "Capacitación",
            fecha = "20/09/2026",
            estado = EstadoDocumento.RECHAZADO,
            motivoRechazo = "El certificado no indica las horas cronológicas. " +
                    "Adjunta una versión con las horas y la firma de la institución.",
            horas = 20
        ),

        DocumentoFuncionario(
            id = "DOC-003",
            nombre = "Curso RCP básico",
            categoria = "Capacitación",
            fecha = "10/06/2026",
            estado = EstadoDocumento.APROBADO,
            horas = 16
        ),

        DocumentoFuncionario(
            id = "DOC-004",
            nombre = "Contrato plazo fijo 2026",
            categoria = "Contrato",
            fecha = "02/01/2026",
            estado = EstadoDocumento.APROBADO
        ),

        DocumentoFuncionario(
            id = "DOC-005",
            nombre = "Resolución de funciones",
            categoria = "Resolución",
            fecha = "12/05/2025",
            estado = EstadoDocumento.APROBADO
        ),

        DocumentoFuncionario(
            id = "DOC-006",
            nombre = "Decreto de nombramiento",
            categoria = "Decreto",
            fecha = "15/03/2021",
            estado = EstadoDocumento.APROBADO
        )
    )
}
