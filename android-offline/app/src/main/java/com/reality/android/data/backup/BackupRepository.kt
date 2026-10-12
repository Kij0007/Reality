package com.reality.android.data.backup

import android.content.Context
import android.net.Uri
import com.reality.android.core.network.ApiResult
import com.reality.android.core.network.AppError
import com.reality.android.core.network.MutationGate
import com.reality.android.data.local.LocalRealityStore
import com.reality.android.data.local.RealityCalculations
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LocalRealityStore,
    private val mutations: MutationGate,
) {
    suspend fun export(uri: Uri): ApiResult<Unit> = operation {
        val (snapshot, zone) = store.snapshotWithZone()
        val exportedAt = Instant.now()
        val today = LocalDate.ofInstant(exportedAt, ZoneId.of(zone))
        val backup = RealityBackup(
            exportedAt = exportedAt.toString(), timeZone = zone,
            activities = snapshot.activities, sessions = snapshot.sessions, breaks = snapshot.breaks,
            streaks = snapshot.activities.filter { it.active == true }.map {
                RealityCalculations.streak(snapshot, it.id, today)
            },
        )
        val bytes = BackupCodec.encode(backup)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "The selected file cannot be written." }
            .use { it.write(bytes); it.flush() }
    }

    suspend fun inspect(uri: Uri): ApiResult<RealityBackup> = operation {
        requireNotNull(context.contentResolver.openInputStream(uri)) { "The selected file cannot be opened." }
            .use(BackupCodec::read)
    }

    suspend fun restore(backup: RealityBackup): ApiResult<Unit> = mutations.run {
        operation {
            BackupCodec.validate(backup)
            // Room commits history, IDs and the source timezone together. Cancellation cannot
            // interrupt the confirmed replacement and leave UI uncertain about the committed data.
            withContext(NonCancellable) { store.replaceAll(backup.snapshot(), backup.timeZone) }
        }
    }

    private suspend fun <T> operation(action: suspend () -> T): ApiResult<T> = withContext(Dispatchers.IO) {
        try { ApiResult.Success(action()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: IllegalArgumentException) { ApiResult.Failure(AppError(error.message ?: "The backup is invalid.")) }
        catch (_: SecurityException) { ApiResult.Failure(AppError("Reality cannot access this file. Select it again using the file picker.")) }
        catch (_: Exception) { ApiResult.Failure(AppError("The backup could not be read or saved. Check available storage and try again.")) }
    }
}
