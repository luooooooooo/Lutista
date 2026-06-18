package cn.alittlecookie.lutista.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watermarks")
data class WatermarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val configJson: String = "{}",
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
