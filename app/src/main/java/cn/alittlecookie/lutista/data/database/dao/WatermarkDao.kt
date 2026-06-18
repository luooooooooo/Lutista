package cn.alittlecookie.lutista.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cn.alittlecookie.lutista.data.database.entity.WatermarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatermarkDao {

    @Query("SELECT * FROM watermarks ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllWatermarks(): Flow<List<WatermarkEntity>>

    @Query("SELECT * FROM watermarks WHERE isEnabled = 1 ORDER BY sortOrder ASC, createdAt DESC")
    fun getEnabledWatermarks(): Flow<List<WatermarkEntity>>

    @Query("SELECT * FROM watermarks WHERE id = :id")
    suspend fun getWatermarkById(id: Long): WatermarkEntity?

    @Query("SELECT * FROM watermarks WHERE name = :name LIMIT 1")
    suspend fun getWatermarkByName(name: String): WatermarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(watermark: WatermarkEntity): Long

    @Update
    suspend fun update(watermark: WatermarkEntity)

    @Delete
    suspend fun delete(watermark: WatermarkEntity)

    @Query("DELETE FROM watermarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
