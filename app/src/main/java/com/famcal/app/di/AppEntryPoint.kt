package com.famcal.app.di

import android.content.Context
import com.famcal.app.data.ActiveFamilyStore
import com.famcal.app.data.auth.AuthRepository
import com.famcal.app.data.family.FamilyRepository
import com.famcal.app.data.task.TaskRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Singletons for classes Hilt can't inject directly: broadcast receivers created by
 * notification actions and the Glance home-screen widget.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun taskRepository(): TaskRepository
    fun authRepository(): AuthRepository
    fun familyRepository(): FamilyRepository
    fun activeFamilyStore(): ActiveFamilyStore

    companion object {
        fun get(context: Context): AppEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
    }
}
