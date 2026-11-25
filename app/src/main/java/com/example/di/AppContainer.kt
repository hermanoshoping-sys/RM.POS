package com.example.di

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.DatabaseInitResult
import com.example.data.PosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dependency Injection Container interface for managing singleton services and repositories.
 */
interface AppContainer {
    val databaseInitState: StateFlow<DatabaseInitResult>
    val database: AppDatabase
    val posRepository: PosRepository
    fun retryDatabaseInitialization(): DatabaseInitResult
}

/**
 * Default implementation of [AppContainer] creating application-scoped singletons
 * with non-crashing database initialization and retry support.
 */
class DefaultAppContainer(private val application: Application) : AppContainer {

    private val _databaseInitState = MutableStateFlow(AppDatabase.initializeDatabase(application))
    override val databaseInitState: StateFlow<DatabaseInitResult> = _databaseInitState.asStateFlow()

    @Volatile
    private var cachedRepository: PosRepository? = null

    override fun retryDatabaseInitialization(): DatabaseInitResult {
        val result = AppDatabase.initializeDatabase(application)
        _databaseInitState.value = result
        if (result is DatabaseInitResult.Ready) {
            cachedRepository = PosRepository(result.database.appDao(), result.database)
        }
        return result
    }

    override val database: AppDatabase
        get() = when (val state = _databaseInitState.value) {
            is DatabaseInitResult.Ready -> state.database
            is DatabaseInitResult.Failed -> when (val retried = retryDatabaseInitialization()) {
                is DatabaseInitResult.Ready -> retried.database
                is DatabaseInitResult.Failed -> throw SecurityException(
                    "${retried.reasonCode}: ${retried.messageAr}"
                )
            }
        }

    override val posRepository: PosRepository
        get() {
            cachedRepository?.let { return it }
            return synchronized(this) {
                cachedRepository ?: PosRepository(database.appDao(), database).also {
                    cachedRepository = it
                }
            }
        }
}
