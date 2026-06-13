package cn.alittlecookie.lut2photo_n

import android.app.Application
import cn.alittlecookie.lut2photo_n.di.appModule
import cn.alittlecookie.lut2photo_n.manager.BuiltinFilterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get
import org.opencv.android.OpenCVLoader
import timber.log.Timber

class Lut2PhotoApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger()
            androidContext(this@Lut2PhotoApp)
            modules(appModule)
        }

        instance = this

        if (!OpenCVLoader.initLocal()) {
            Timber.e("OpenCV initialization failed")
        }

        initializeBuiltinFilters()
    }

    private fun initializeBuiltinFilters() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val builtinFilterManager = get<BuiltinFilterManager>(BuiltinFilterManager::class.java)
                builtinFilterManager.initializeBuiltinFilters()
            } catch (e: Exception) {
                Timber.e(e, "初始化内置滤镜失败")
            }
        }
    }

    companion object {
        lateinit var instance: Lut2PhotoApp
            private set
    }
}
