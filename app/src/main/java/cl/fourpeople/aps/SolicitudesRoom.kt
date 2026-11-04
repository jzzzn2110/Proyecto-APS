package cl.fourpeople.aps

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fila SQLite que representa una solicitud y sus datos de revisión. */
@Entity(tableName = "solicitudes")
data class SolicitudEntity(
    @androidx.room.PrimaryKey val id: String,
    val tipo: String,
    val desde: String,
    val hasta: String,
    val jornada: String,
    val motivo: String,
    val creadaEn: String,
    val estado: String,
    val respuesta: String,
    val archivoUri: String,
    val revisadaPor: String,
    val revisadaEn: String
)

/** CRUD requerido por Room para solicitudes. */
@Dao
interface SolicitudDao {
    @Query("SELECT * FROM solicitudes ORDER BY creadaEn DESC")
    fun observarTodas(): Flow<List<SolicitudEntity>>

    @Query("SELECT * FROM solicitudes WHERE id = :id LIMIT 1")
    suspend fun buscar(id: String): SolicitudEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(solicitud: SolicitudEntity)

    @Update
    suspend fun actualizar(solicitud: SolicitudEntity)

    @Query("DELETE FROM solicitudes WHERE id = :id")
    suspend fun eliminar(id: String)

    @Query("DELETE FROM solicitudes")
    suspend fun eliminarTodas()
}

@Database(entities = [SolicitudEntity::class], version = 1, exportSchema = false)
abstract class ApsDatabase : RoomDatabase() {
    abstract fun solicitudDao(): SolicitudDao

    companion object {
        @Volatile private var instancia: ApsDatabase? = null

        fun obtener(contexto: Context): ApsDatabase = instancia ?: synchronized(this) {
            instancia ?: Room.databaseBuilder(
                contexto.applicationContext,
                ApsDatabase::class.java,
                "aps.sqlite"
            ).build().also { instancia = it }
        }
    }
}

/** Traduce entre el modelo de pantalla y la entidad persistida en SQLite. */
private fun SolicitudEntity.aModelo() = SolicitudFuncionario(
    id, TipoSolicitud.valueOf(tipo), desde, hasta, JornadaSolicitud.valueOf(jornada),
    motivo, creadaEn, EstadoSolicitud.valueOf(estado), respuesta, archivoUri, revisadaPor, revisadaEn
)

private fun SolicitudFuncionario.aEntidad() = SolicitudEntity(
    id, tipo.name, desde, hasta, jornada.name, motivo, creadaEn, estado.name,
    respuesta, archivoUri, revisadaPor, revisadaEn
)

/** Repository: la interfaz no accede directamente al DAO. */
class SolicitudesRepository(private val dao: SolicitudDao) {
    fun observar(): Flow<List<SolicitudEntity>> = dao.observarTodas()
    suspend fun listar(): List<SolicitudFuncionario> = dao.observarTodas().first().map { it.aModelo() }
    suspend fun insertar(solicitud: SolicitudFuncionario) = dao.insertar(solicitud.aEntidad())
    suspend fun actualizar(solicitud: SolicitudFuncionario) = dao.actualizar(solicitud.aEntidad())
    suspend fun eliminar(id: String) = dao.eliminar(id)
    suspend fun reemplazar(solicitudes: List<SolicitudFuncionario>) {
        dao.eliminarTodas()
        solicitudes.forEach { dao.insertar(it.aEntidad()) }
    }
}

/** ViewModel disponible para las pantallas que necesiten observar solicitudes desde Compose. */
class SolicitudesViewModel(private val repository: SolicitudesRepository) : ViewModel() {
    fun guardar(solicitud: SolicitudFuncionario, existente: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (existente) repository.actualizar(solicitud) else repository.insertar(solicitud)
        }
    }
    fun eliminar(id: String) { viewModelScope.launch(Dispatchers.IO) { repository.eliminar(id) } }
}
