package cl.fourpeople.aps

import android.content.Context
import java.io.File
import java.io.IOException

/** Estado de sesión mínimo: nunca guarda la contraseña y se limpia al cerrar sesión. */
object SesionLocal {
    private const val PREFS = "sesion_local"
    fun guardar(contexto: Context, usuario: String, rol: String) = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString("usuario", usuario).putString("rol", rol).apply()
    fun limpiar(contexto: Context) = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

/** Copia de recuperación dentro del almacenamiento privado de la aplicación. */
object RespaldoLocal {
    private fun carpeta(contexto: Context) = File(contexto.filesDir, "backups").also { it.mkdirs() }
    suspend fun crear(contexto: Context): File {
        PersistenciaDocumentos.cargar(contexto, DatosFuncionario.documentos.toList())
        val origen = File(contexto.filesDir, "expediente_funcionario.json")
        if (!origen.exists()) throw IOException("No hay expediente para respaldar.")
        val destino = File(carpeta(contexto), "expediente_funcionario.bak")
        origen.copyTo(destino, overwrite = true)
        return destino
    }

    suspend fun restaurar(contexto: Context, respaldo: File) {
        require(respaldo.isFile && respaldo.length() > 0) { "El respaldo no es válido." }
        val destino = File(contexto.filesDir, "expediente_funcionario.json")
        val temporal = File(contexto.filesDir, "expediente_funcionario.restore.tmp")
        respaldo.copyTo(temporal, overwrite = true)
        try {
            // Valida el JSON y el historial antes de reemplazar el expediente actual.
            val contenido = temporal.readText(Charsets.UTF_8)
            require(contenido.contains("\"formato\"")) { "El respaldo no tiene un formato compatible." }
            temporal.copyTo(destino, overwrite = true)
            temporal.delete()
            PersistenciaDocumentos.cargar(contexto, DatosFuncionario.documentos.toList())
        } finally {
            if (temporal.exists()) temporal.delete()
        }
    }
}
