package com.ssolstice.camera.manual.di

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.ssolstice.camera.manual.utils.RemoteConfigManager
import com.ssolstice.camera.manual.utils.SharedPrefManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideSharedPrefManager(
        sharedPreferences: SharedPreferences,
        gson: Gson
    ): SharedPrefManager {
        return SharedPrefManager(sharedPreferences, gson)
    }

    @Provides
    @Singleton
    fun provideFirebaseRemoteConfig(): FirebaseRemoteConfig {
        val config = FirebaseRemoteConfig.getInstance()
        val settings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // fetch mỗi 1h
        }
        config.setConfigSettingsAsync(settings)
        return config
    }

    @Provides
    @Singleton
    fun provideRemoteConfigManager(
        remoteConfig: FirebaseRemoteConfig
    ): RemoteConfigManager = RemoteConfigManager(remoteConfig)
}
