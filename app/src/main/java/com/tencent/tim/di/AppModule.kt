package com.tencent.tim.di

import androidx.room.Room
import com.tencent.tim.data.local.AppDatabase
import com.tencent.tim.data.migration.LegacyAccountMigration
import com.tencent.tim.data.remote.TencentGameApi
import com.tencent.tim.data.repository.AccountFileDataSource
import com.tencent.tim.data.repository.AccountRepository
import com.tencent.tim.data.repository.AccountTransferFileDataSource
import com.tencent.tim.data.system.RootManager
import com.tencent.tim.domain.AccountInteractor
import com.tencent.tim.manager.ModeManager
import com.tencent.tim.manager.QQControlManager
import com.tencent.tim.manager.QQControlManagerImpl
import com.tencent.tim.ui.main.MainViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

val appModule = module {
    // Local Data
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "accountswitcher_db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration(false)
            .build()
    }
    single { get<AppDatabase>().accountDao() }
    single { LegacyAccountMigration(androidContext(), get()) }

    // System
    single { RootManager() }
    single { ModeManager(androidContext(), get()) }
    single<QQControlManager> { QQControlManagerImpl(get(), get()) }

    // Network
    single {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }
    single {
        Retrofit.Builder()
            .baseUrl("https://comm.aci.game.qq.com/")
            .client(get())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(TencentGameApi::class.java)
    }

    // Repository
    single { AccountFileDataSource(get()) }
    single { AccountTransferFileDataSource(androidContext()) }
    single { AccountRepository(get(), get(), get(), get(), get()) }

    // Domain
    single { AccountInteractor(get()) }

    // ViewModels
    viewModel { MainViewModel(get(), get(), get()) }
}

