package cn.alittlecookie.lut2photo_n.di

import androidx.room.Room
import cn.alittlecookie.lut2photo_n.data.database.AppDatabase
import cn.alittlecookie.lut2photo_n.data.database.dao.FilterDao
import cn.alittlecookie.lut2photo_n.data.database.dao.TaskDao
import cn.alittlecookie.lut2photo_n.data.database.dao.WatermarkDao
import cn.alittlecookie.lut2photo_n.data.repository.FilterRepository
import cn.alittlecookie.lut2photo_n.data.repository.TaskRepository
import cn.alittlecookie.lut2photo_n.domain.engine.BrightnessProcessor
import cn.alittlecookie.lut2photo_n.domain.engine.ColorProcessor
import cn.alittlecookie.lut2photo_n.domain.engine.CurveProcessor
import cn.alittlecookie.lut2photo_n.domain.engine.GrainProcessor
import cn.alittlecookie.lut2photo_n.domain.engine.ImageProcessingEngine
import cn.alittlecookie.lut2photo_n.domain.engine.LchProcessor
import cn.alittlecookie.lut2photo_n.domain.engine.LutProcessor
import cn.alittlecookie.lut2photo_n.domain.engine.VignetteProcessor
import cn.alittlecookie.lut2photo_n.domain.usecase.ApplyFilterUseCase
import cn.alittlecookie.lut2photo_n.domain.usecase.ExportFilterUseCase
import cn.alittlecookie.lut2photo_n.domain.usecase.ImportLutUseCase
import cn.alittlecookie.lut2photo_n.domain.usecase.ProcessImageUseCase
import cn.alittlecookie.lut2photo_n.manager.LogManager
import cn.alittlecookie.lut2photo_n.manager.PermissionManager
import cn.alittlecookie.lut2photo_n.manager.SettingsManager
import cn.alittlecookie.lut2photo_n.service.ImageProcessingService
import cn.alittlecookie.lut2photo_n.service.TaskSubmissionManager
import cn.alittlecookie.lut2photo_n.util.FileUtils
import cn.alittlecookie.lut2photo_n.util.ImageUtils
import cn.alittlecookie.lut2photo_n.util.LutParser
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
        ).build()
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

    // Utilities
    singleOf(::FileUtils)
    singleOf(::ImageUtils)
    singleOf(::LutParser)

    // Image Processing Engine
    singleOf(::LutProcessor)
    singleOf(::BrightnessProcessor)
    singleOf(::ColorProcessor)
    singleOf(::CurveProcessor)
    singleOf(::LchProcessor)
    singleOf(::VignetteProcessor)
    singleOf(::GrainProcessor)
    singleOf(::ImageProcessingEngine)

    // Use Cases
    singleOf(::ApplyFilterUseCase)
    singleOf(::ImportLutUseCase)
    singleOf(::ExportFilterUseCase)
    singleOf(::ProcessImageUseCase)

    // Services
    singleOf(::ImageProcessingService)
    singleOf(::TaskSubmissionManager)
}
