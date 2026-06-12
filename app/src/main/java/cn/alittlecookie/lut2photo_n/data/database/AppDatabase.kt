package cn.alittlecookie.lut2photo_n.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filterDao(): FilterDao
    abstract fun watermarkDao(): WatermarkDao
    abstract fun taskDao(): TaskDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE filters ADD COLUMN isBuiltIn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE filters ADD COLUMN filterDirPath TEXT DEFAULT NULL")
            }
        }
    }
}
