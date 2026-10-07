package com.example.proyectoaps.model

// Entidad Documento del modelo relacional (Análisis del contexto).
// Por ahora vive en memoria; más adelante podrá mapearse a una tabla con Room.

enum class CategoriaDocumento(val etiqueta: String) {
    CONTRATO("Contrato"),
    ANEXO("Anexo"),
    DECRETO("Decreto"),
    RESOLUCION("Resolución"),
    PERMISO("Permiso"),
    CAPACITACION("Capacitación"),
    OTRO("Otro")
}

enum class EstadoDocumento(val etiqueta: String) {
    PENDIENTE("Pendiente de revisión"),
    APROBADO("Aprobado"),
    RECHAZADO("Rechazado")
}

data class Documento(
    val id: Int,                        // ID_Documento (PK)
    val idUsuario: Int,                 // ID_Usuario (FK, dueño del expediente)
    val idUsuarioCarga: Int,            // ID_Usuario_Carga (FK)
    val categoria: CategoriaDocumento,  // ID_Categoria (FK)
    val nombre: String,                 // Nombre_Documento
    val nombreArchivo: String,          // Nombre_Archivo
    val fechaCarga: String,             // Fecha_Carga (dd/MM/yyyy HH:mm)
    val fechaVencimiento: String?,      // Fecha_Vencimiento (dd/MM/yyyy), opcional
    val estado: EstadoDocumento = EstadoDocumento.PENDIENTE,
    val observacion: String? = null     // Observacion (motivo de rechazo)
)
