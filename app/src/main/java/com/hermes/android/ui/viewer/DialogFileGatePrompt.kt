package com.hermes.android.ui.viewer

import android.content.Context
import com.hermes.android.runtime.FileGatePrompt
import com.hermes.android.runtime.LinuxFileGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Shows [FileGateActivity]'s dialog for a Download and waits for the user's answer. */
@Singleton
class DialogFileGatePrompt @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileGatePrompt {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    override suspend fun allowed(file: File, name: String, risk: LinuxFileGate.Risk): Boolean {
        val id = UUID.randomUUID().toString()
        val answer = CompletableDeferred<Boolean>()
        pending[id] = answer
        try {
            context.startActivity(FileGateActivity.downloadIntent(context, file, name, risk, id))
            return answer.await()
        } finally {
            pending.remove(id)
        }
    }

    /** The dialog for request [id] closed; [allowed] when the user chose to save. */
    fun answer(id: String, allowed: Boolean) {
        pending[id]?.complete(allowed)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FileGatePromptModule {
    @Binds
    @Singleton
    abstract fun bindFileGatePrompt(impl: DialogFileGatePrompt): FileGatePrompt
}
