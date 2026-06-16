package cn.alittlecookie.lut2photo_n.di

import androidx.room.Room
import cn.alittlecookie.lut2photo_n.data.database.AppDatabase
import cn.alittlecookie.lut2photo_n.data.database.dao.FilterDao
import cn.alittlecookie.lut2photo_n.data.database.dao.TaskDao
import cn.alittlecookie.lut2photo_n.data.database.dao.WatermarkDao
import cn.alittlecookie.lut2photo_n.data.repository.FilterRepository
import cn.alittlecookie.lut2photo_n.data.repository.TaskRepository
import cn.alittlecookie.lut2photo_n.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lut2photo_n.domain.engine.LutProcessor
import cn.alittlecookie.lut2photo_n.domain.usecase.ApplyFilterUseCase
import cn.alittlecookie.lut2photo_n.domain.usecase.ExportFilterUseCase
import cn.alittlecookie.lut2photo_n.domain.usecase.ImportLutUseCase
import cn.alittlecookie.lut2photo_n.domain.usecase.ProcessImageUseCase
import cn.alittlecookie.lut2photo_n.gpu.VulkanGpuEngine
import cn.alittlecookie.lut2photo_n.manager.BuiltinFilterManager
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.manager.PermissionManager
import cn.alittlecookie.lut2photo_n.manager.SettingsManager
import cn.alittlecookie.lut2photo_n.service.ImageProcessingService
import cn.alittlecookie.lut2photo_n.service.TaskSubmissionManager
import cn.alittlecookie.lut2photo_n.util.FileUtils
import cn.alittlecookie.lut2photo_n.util.ImageUtils
import cn.alittlecookie.lut2photo_n.util.LutParser
import cn.alittlecookie.lut2photo_n.watermark.cache.BitmapCache
import cn.alittlecookie.lut2photo_n.watermark.manager.HistoryManager
import cn.alittlecookie.lut2photo_n.watermark.manager.ResourceManager
import cn.alittlecookie.lut2photo_n.watermark.renderer.OutputRenderer
import cn.alittlecookie.lut2photo_n.watermark.renderer.WatermarkProcessor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    // Database
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "lut2photo.db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()
    }

    // DAOs
    single<FilterDao> { get<AppDatabase>().filterDao() }
    single<TaskDao> { get<AppDatabase>().taskDao() }
    single<WatermarkDao> { get<AppDatabase>().watermarkDao() }

    // Repositories
    singleOf(::FilterRepository)
    singleOf(::TaskRepository)

    // Managers
    singleOf(::LogManager)
    singleOf(::PermissionManager)
    singleOf(::SettingsManager)
    singleOf(::BuiltinFilterManager)

    // Utilities
    singleOf(::FileUtils)
    singleOf(::ImageUtils)
    singleOf(::LutParser)

    // Image Processing Engine
    singleOf(::LutProcessor)
    singleOf(::VulkanGpuEngine)
    singleOf(::ImageProcessingEngine)

    // Use Cases
    singleOf(::ApplyFilterUseCase)
    singleOf(::ImportLutUseCase)
    singleOf(::ExportFilterUseCase)
    singleOf(::ProcessImageUseCase)

    // Services
    singleOf(::ImageProcessingService)
    singleOf(::TaskSubmissionManager)

    // Watermark System
    single { BitmapCache() }
    singleOf(::OutputRenderer)
    singleOf(::WatermarkProcessor)
    singleOf(::ResourceManager)
    singleOf(::HistoryManager)
}
