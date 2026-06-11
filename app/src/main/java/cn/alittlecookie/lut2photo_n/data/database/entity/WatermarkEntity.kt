package cn.alittlecookie.lut2photo_n.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watermarks")
data class WatermarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val configJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)
