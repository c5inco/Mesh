package data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import model.SavedColor

@Dao
interface SavedColorDao {
    @Query("SELECT * FROM savedColor")
    fun getAll(): Flow<List<SavedColor>>

    @Query("SELECT * FROM savedColor WHERE preset = true")
    fun getAllPresets(): Flow<List<SavedColor>>

    @Insert
    suspend fun insertAll(vararg colors: SavedColor)
}