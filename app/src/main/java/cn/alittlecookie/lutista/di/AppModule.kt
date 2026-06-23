package cn.alittlecookie.lutista.di

import androidx.room.Room
import cn.alittlecookie.lutista.data.database.AppDatabase
import cn.alittlecookie.lutista.data.database.dao.FilterDao
import cn.alittlecookie.lutista.data.database.dao.TaskDao
import cn.alittlecookie.lutista.data.database.dao.WatermarkDao
import cn.alittlecookie.lutista.data.repository.FilterRepository
import cn.alittlecookie.lutista.data.repository.TaskRepository
import cn.alittlecookie.lutista.data.repository.WatermarkRepository
import cn.alittlecookie.lutista.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lutista.domain.engine.LutProcessor
import cn.alittlecookie.lutista.domain.usecase.ApplyFilterUseCase
import cn.alittlecookie.lutista.domain.usecase.ExportFilterUseCase
import cn.alittlecookie.lutista.domain.usecase.ImportLutUseCase
import cn.alittlecookie.lutista.domain.usecase.ProcessImageUseCase
import cn.alittlecookie.lutista.gpu.VulkanGpuEngine
import cn.alittlecookie.lutista.manager.BuiltinFilterManager
import cn.alittlecookie.lutista.manager.LogManager
import cn.alittlecookie.lutista.manager.PermissionManager
import cn.alittlecookie.lutista.manager.SettingsManager
import cn.alittlecookie.lutista.service.ImageProcessingService
import cn.alittlecookie.lutista.service.TaskSubmissionManager
import cn.alittlecookie.lutista.util.FileUtils
import cn.alittlecookie.lutista.util.ImageUtils
import cn.alittlecookie.lutista.util.LutParser
import cn.alittlecookie.lutista.util.MediaStoreScanner
import cn.alittlecookie.lutista.watermark.cache.BitmapCache
import cn.alittlecookie.lutista.watermark.manager.HistoryManager
import cn.alittlecookie.lutista.watermark.manager.ResourceManager
import cn.alittlecookie.lutista.watermark.renderer.OutputRenderer
import cn.alittlecookie.lutista.watermark.renderer.WatermarkProcessor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    // Database
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "Lutista.db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()
    }

    // DAOs
    single<FilterDao> { get<AppDatabase>().filterDao() }
    single<TaskDao> { get<AppDatabase>().taskDao() }
    single<WatermarkDao> { get<AppDatabase>().watermarkDao() }

    // Repositories
    singleOf(::FilterRepository)
    singleOf(::TaskRepository)
    singleOf(::WatermarkRepository)

    // Managers
    singleOf(::LogManager)
    singleOf(::PermissionManager)
    singleOf(::SettingsManager)
    singleOf(::BuiltinFilterManager)

    // Utilities
    singleOf(::FileUtils)
    singleOf(::ImageUtils)
    singleOf(::LutParser)
    singleOf(::MediaStoreScanner)

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
    single { ImageProcessingService(androidContext(), get(), get(), get(), get(), get(), get()) }
    singleOf(::TaskSubmissionManager)

    // Watermark System
    single { BitmapCache() }
    singleOf(::OutputRenderer)
    singleOf(::WatermarkProcessor)
    singleOf(::ResourceManager)
    singleOf(::HistoryManager)
}
