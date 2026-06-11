package cn.alittlecookie.lut2photo_n.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.alittlecookie.lut2photo_n.data.database.dao.FilterDao
import cn.alittlecookie.lut2photo_n.data.database.dao.TaskDao
import cn.alittlecookie.lut2photo_n.data.database.dao.WatermarkDao
import cn.alittlecookie.lut2photo_n.data.database.entity.FilterEntity
import cn.alittlecookie.lut2photo_n.data.database.entity.TaskEntity
import cn.alittlecookie.lut2photo_n.data.database.entity.WatermarkEntity

@Database(
    entities = [
        FilterEntity::class,
        WatermarkEntity::class,
        TaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao
    abstract fun watermarkDao(): WatermarkDao
    abstract fun taskDao(): TaskDao
}
