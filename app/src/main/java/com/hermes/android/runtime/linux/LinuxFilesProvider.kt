package com.hermes.android.runtime.linux

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.hermes.android.ui.viewer.FileGateActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException

/** Shows the built-in Linux in Android's Files app and in every file picker. */
class LinuxFilesProvider : DocumentsProvider() {
    /** A provider can't be injected into; it reaches the app's [FileGate] through this. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FileGateEntryPoint {
        fun fileGate(): FileGate
    }

    private lateinit var files: GuestFiles
    private lateinit var authority: String
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val fileGate by lazy {
        EntryPointAccessors.fromApplication(checkNotNull(context), FileGateEntryPoint::class.java).fileGate()
    }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        files = GuestFiles(ProotEnvironment.rootfsDir(context))
        authority = authority(context)
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: RootColumns)
        val context = context ?: return cursor
        cursor.setNotificationUri(context.contentResolver, DocumentsContract.buildRootsUri(authority))
        if (!files.isReady) return cursor
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, ROOT_ID)
            add(Root.COLUMN_DOCUMENT_ID, GuestFiles.ROOT)
            add(Root.COLUMN_TITLE, context.applicationInfo.loadLabel(context.packageManager).toString())
            add(Root.COLUMN_SUMMARY, "Linux")
            add(Root.COLUMN_ICON, context.applicationInfo.icon)
            add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD)
            add(Root.COLUMN_MIME_TYPES, "*/*")
            add(Root.COLUMN_AVAILABLE_BYTES, files.freeBytes)
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DocumentColumns)
        addRow(cursor, GuestFiles.normalize(documentId))
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val parent = GuestFiles.normalize(parentDocumentId)
        val cursor = MatrixCursor(projection ?: DocumentColumns)
        for (name in files.list(parent)) {
            // The agent may delete a file while the list is being built; just skip it.
            runCatching { addRow(cursor, GuestFiles.child(parent, name)) }
        }
        context?.let { cursor.setNotificationUri(it.contentResolver, childrenUri(parent)) }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val path = GuestFiles.normalize(documentId)
        val file = files.hostFile(path)
        if (!file.isFile) throw FileNotFoundException("Not a file: $path")
        if ('r' in mode && Binder.getCallingUid() != Process.myUid()) holdBackProgram(file)
        val flags = ParcelFileDescriptor.parseMode(mode)
        if (mode == "r") return ParcelFileDescriptor.open(file, flags)
        return ParcelFileDescriptor.open(file, flags, mainHandler) { error ->
            if (error != null) Timber.w(error, "[Files] write to %s ended with an error", path)
            notifyChildren(GuestFiles.parentOf(path))
        }
    }

    /**
     * Another app (a copy in the Files app, an installer) reads a program only after the user
     * confirmed it in Hermes; the notification leads there. See [FileGate].
     */
    private fun holdBackProgram(file: File) {
        val risk = fileGate.riskOf(file)
        if (risk == FileGate.Risk.SAFE || (risk == FileGate.Risk.PROGRAM && fileGate.isApproved(file))) return
        context?.let { FileGateActivity.notifyHeldBack(it, file, risk) }
        throw FileNotFoundException("Held back until confirmed in Hermes: ${file.name}")
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = GuestFiles.normalize(parentDocumentId)
        val created = files.create(parent, displayName, directory = mimeType == Document.MIME_TYPE_DIR)
        notifyChildren(parent)
        return created
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val path = GuestFiles.normalize(documentId)
        val renamed = files.rename(path, displayName)
        notifyChildren(GuestFiles.parentOf(path))
        return renamed
    }

    override fun deleteDocument(documentId: String) {
        val path = GuestFiles.normalize(documentId)
        files.delete(path)
        notifyChildren(GuestFiles.parentOf(path))
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = runCatching {
        val parent = GuestFiles.normalize(parentDocumentId)
        val child = GuestFiles.normalize(documentId)
        child != parent && (parent == GuestFiles.ROOT || child.startsWith("$parent/"))
    }.getOrDefault(false)

    override fun findDocumentPath(parentDocumentId: String?, childDocumentId: String): DocumentsContract.Path {
        val child = GuestFiles.normalize(childDocumentId)
        val from = parentDocumentId?.let(GuestFiles::normalize) ?: GuestFiles.ROOT
        val chain = generateSequence(child) { if (it == from || it == GuestFiles.ROOT) null else GuestFiles.parentOf(it) }
            .toList()
            .asReversed()
        if (chain.first() != from) throw FileNotFoundException("$child is not under $from")
        return DocumentsContract.Path(if (parentDocumentId == null) ROOT_ID else null, chain)
    }

    private fun addRow(cursor: MatrixCursor, path: String) {
        val file = files.hostFile(path)
        if (!file.exists()) throw FileNotFoundException("Missing: $path")
        val directory = file.isDirectory
        val writableParent = path != GuestFiles.ROOT && files.hostFile(GuestFiles.parentOf(path)).canWrite()
        var flags = 0
        if (writableParent) flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        if (directory && file.canWrite()) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        if (!directory && file.canWrite()) flags = flags or Document.FLAG_SUPPORTS_WRITE
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, path)
            add(Document.COLUMN_DISPLAY_NAME, if (path == GuestFiles.ROOT) "Linux" else GuestFiles.nameOf(path))
            add(Document.COLUMN_MIME_TYPE, if (directory) Document.MIME_TYPE_DIR else mimeOf(file.name))
            add(Document.COLUMN_SIZE, if (directory) null else file.length())
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
        }
    }

    private fun childrenUri(parent: String): Uri = DocumentsContract.buildChildDocumentsUri(authority, parent)

    private fun notifyChildren(parent: String) {
        context?.contentResolver?.notifyChange(childrenUri(parent), null, false)
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    companion object {
        const val ROOT_ID = "linux"

        private val RootColumns = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY,
            Root.COLUMN_ICON, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES, Root.COLUMN_AVAILABLE_BYTES,
        )
        private val DocumentColumns = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
        )

        fun authority(context: Context): String = "${context.packageName}.linux.documents"

        /** Tells an open Files window that [guestDir] changed (e.g. a download landed in it). */
        fun notifyChildrenChanged(context: Context, guestDir: String) {
            context.contentResolver.notifyChange(
                DocumentsContract.buildChildDocumentsUri(authority(context), guestDir), null, false,
            )
        }

        fun notifyRootsChanged(context: Context) {
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority(context)), null, false)
        }
    }
}
