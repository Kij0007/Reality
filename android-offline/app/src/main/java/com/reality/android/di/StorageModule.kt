package com.reality.android.di

import android.content.Context
import androidx.room.Room
import com.reality.android.data.local.RealityDatabase
import com.reality.android.data.local.RealityDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): RealityDatabase =
        Room.databaseBuilder(context, RealityDatabase::class.java, "reality-offline.db").build()
    @Provides fun dao(database: RealityDatabase): RealityDao = database.realityDao()
    @Provides @Singleton fun json(): Json = Json { encodeDefaults = true; explicitNulls = true }
}
