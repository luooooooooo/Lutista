package cn.alittlecookie.lut2photo_n

import android.app.Application
import cn.alittlecookie.lut2photo_n.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class Lut2PhotoApp : Application() {

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
    }

    companion object {
        lateinit var instance: Lut2PhotoApp
            private set
    }
}
