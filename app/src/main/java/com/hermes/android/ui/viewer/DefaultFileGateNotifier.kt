package com.hermes.android.ui.viewer

import android.content.Context
import com.hermes.android.runtime.FileGateNotifier
import com.hermes.android.runtime.LinuxFileGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultFileGateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileGateNotifier {
    override fun notifyHeldBack(file: File, risk: LinuxFileGate.Risk) {
        FileGateActivity.notifyHeldBack(context, file, risk)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FileGateNotifierModule {
    @Binds
    @Singleton
    abstract fun bindFileGateNotifier(impl: DefaultFileGateNotifier): FileGateNotifier
}
