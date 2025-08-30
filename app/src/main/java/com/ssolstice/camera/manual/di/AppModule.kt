package com.ssolstice.camera.manual.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
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
}
