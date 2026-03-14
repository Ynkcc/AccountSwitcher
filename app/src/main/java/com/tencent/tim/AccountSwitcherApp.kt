package com.tencent.tim

import android.app.Application
import android.util.Log
import com.tencent.tim.data.migration.LegacyAccountMigration
import com.tencent.tim.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

class AccountSwitcherApp : Application() {
    companion object {
        private const val TAG = "AccountSwitcherApp"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Start Koin
        startKoin {
            androidLogger()
            androidContext(this@AccountSwitcherApp)
            modules(appModule)
        }

        appScope.launch {
            runCatching {
                getKoin().get<LegacyAccountMigration>().migrateIfNeeded()
            }.onFailure { e ->
                Log.e(TAG, "旧版账号迁移失败", e)
            }
        }
    }
}
