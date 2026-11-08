package data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import model.MeshPoint

/** Reads the points of the single mesh Mesh saved before it had documents. */
@Dao
interface MeshPointDao {
    @Query("SELECT * FROM meshPoint")
    fun getAll(): Flow<List<MeshPoint>>
}
