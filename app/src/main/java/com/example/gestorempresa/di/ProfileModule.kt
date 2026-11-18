package com.example.gestorempresa.di

import com.example.gestorempresa.data.profile.RoomProfileRepository
import com.example.gestorempresa.data.settings.RoomSettingsRepository
import com.example.gestorempresa.domain.profile.ProfileRepository
import com.example.gestorempresa.domain.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Le dice a Hilt qué implementación usar cuando alguien pide una interfaz.
 * Ejemplo: si un ViewModel pide un ProfileRepository, Hilt le da un RoomProfileRepository.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileBindingModule {
    @Binds @Singleton abstract fun profileRepository(impl: RoomProfileRepository): ProfileRepository
    @Binds @Singleton abstract fun settingsRepository(impl: RoomSettingsRepository): SettingsRepository
}
