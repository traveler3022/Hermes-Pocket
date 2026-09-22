package com.hermes.android.runtime.linux

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale

/**
 * Makes the built-in Alpine filesystem available in Android's Files app and document picker —
 * Aether's `LinuxDocumentsProvider`. Only the system DocumentsUI (MANAGE_DOCUMENTS) binds to it.
 */
class LinuxDocumentsProvider : DocumentsProvider() {
    private lateinit var store: LinuxDocumentStore
    private lateinit var authority: String
    private val observers = mutableMapOf<File, DirectoryObserver>()
    private val closeHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun onCreate(): Boolean {
        val context = context ?: return false
        store = LinuxDocumentStore(File(context.filesDir, "linux/rootfs"))
        authority = authority(context)
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: RootProjection).apply {
            setNotificationUri(context!!.contentResolver, DocumentsContract.buildRootsUri(authority))
            if (store.isAvailable) {
                val application = context!!.applicationInfo
                val root = store.directory(LinuxDocumentStore.RootDocumentId)
                addProjectedRow(mapOf(
                    Root.COLUMN_ROOT_ID to LinuxDocumentStore.RootId,
                    Root.COLUMN_DOCUMENT_ID to root.documentId,
                    Root.COLUMN_TITLE to application.loadLabel(context!!.packageManager).toString(),
                    Root.COLUMN_SUMMARY to "Linux",
                    Root.COLUMN_ICON to application.icon,
                    Root.COLUMN_FLAGS to (Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD),
                    Root.COLUMN_MIME_TYPES to "*/*",
                    Root.COLUMN_AVAILABLE_BYTES to root.target!!.usableSpace,
                ))
            }
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = documentOperation {
        MatrixCursor(projection ?: DocumentProjection).apply {
            includeDocument(store.document(documentId))
            setNotificationUri(context!!.contentResolver, DocumentsContract.buildDocumentUri(authority, documentId))
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = documentOperation {
        val parent = store.directory(parentDocumentId)
        val cursor = DirectoryCursor(projection ?: DocumentProjection, parentDocumentId, parent.target!!)
        try {
            store.children(parentDocumentId)
                .sortedWith(documentComparator(sortOrder))
                .forEach { cursor.includeDocument(it) }
            cursor
        } catch (error: Throwable) {
            cursor.close()
            throw error
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        documentOperation {
            signal?.throwIfCanceled()
            val file = store.contentFile(documentId)
            // File editors commonly use "w" to replace the document. Some Android versions
            // parse it without MODE_TRUNCATE, which would leave bytes from the old contents.
            val accessMode = ParcelFileDescriptor.parseMode(mode).let {
                if (mode == "w") it or ParcelFileDescriptor.MODE_TRUNCATE else it
            }
            val descriptor = if (accessMode and ParcelFileDescriptor.MODE_WRITE_ONLY != 0) {
                ParcelFileDescriptor.open(file, accessMode, closeHandler) { notifyDocumentChanged(documentId) }
            } else {
                ParcelFileDescriptor.open(file, accessMode)
            }
            try {
                signal?.throwIfCanceled()
                descriptor
            } catch (error: Throwable) {
                descriptor.close()
                throw error
            }
        }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String =
        documentOperation {
            store.create(parentDocumentId, displayName, directory = mimeType == Document.MIME_TYPE_DIR)
                .also(::notifyDocumentChanged)
        }

    override fun renameDocument(documentId: String, displayName: String): String? = documentOperation {
        val renamed = store.rename(documentId, displayName)
        // Returning the same ID would make DocumentsProvider revoke the existing URI grant.
        if (renamed == documentId) return@documentOperation null
        notifyDocumentChanged(documentId)
        notifyDocumentChanged(renamed)
        renamed
    }

    override fun deleteDocument(documentId: String) = documentOperation {
        store.delete(documentId)
        notifyDocumentChanged(documentId)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        store.isChild(parentDocumentId, documentId)

    override fun findDocumentPath(parentDocumentId: String?, childDocumentId: String): DocumentsContract.Path =
        documentOperation {
            DocumentsContract.Path(
                if (parentDocumentId == null) LinuxDocumentStore.RootId else null,
                store.documentPath(parentDocumentId, childDocumentId),
            )
        }

    private fun MatrixCursor.includeDocument(document: LinuxDocument) {
        var flags = 0
        if (document.canModify) flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        if (document.target?.canWrite() == true) {
            if (document.isDirectory) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
            if (document.isRegularFile) flags = flags or Document.FLAG_SUPPORTS_WRITE
        }
        val mimeType = if (document.isDirectory) Document.MIME_TYPE_DIR else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                document.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT),
            ) ?: "application/octet-stream"
        }
        addProjectedRow(mapOf(
            Document.COLUMN_DOCUMENT_ID to document.documentId,
            Document.COLUMN_DISPLAY_NAME to document.displayName,
            Document.COLUMN_MIME_TYPE to mimeType,
            Document.COLUMN_FLAGS to flags,
            Document.COLUMN_SIZE to if (document.isDirectory) null else document.size,
            Document.COLUMN_LAST_MODIFIED to document.lastModified,
        ))
    }

    private fun MatrixCursor.addProjectedRow(values: Map<String, Any?>) {
        addRow(columnNames.map(values::get))
    }

    private fun notifyDocumentChanged(documentId: String) {
        val resolver = context!!.contentResolver
        resolver.notifyChange(DocumentsContract.buildDocumentUri(authority, documentId), null)
        LinuxDocumentStore.parentId(documentId)?.let { parent ->
            resolver.notifyChange(DocumentsContract.buildChildDocumentsUri(authority, parent), null)
        }
    }

    private inner class DirectoryCursor(
        projection: Array<out String>,
        documentId: String,
        private val directory: File,
    ) : MatrixCursor(projection) {
        private val uri = DocumentsContract.buildChildDocumentsUri(authority, documentId)

        init {
            setNotificationUri(context!!.contentResolver, uri)
            synchronized(observers) {
                val observer = observers.getOrPut(directory) { DirectoryObserver(directory).apply { startWatching() } }
                observer.cursors.add(this)
            }
        }

        fun notifyChanged() {
            if (!isClosed) {
                onChange(false)
                context!!.contentResolver.notifyChange(uri, null)
            }
        }

        override fun close() {
            synchronized(observers) {
                observers[directory]?.let { observer ->
                    observer.cursors.remove(this)
                    if (observer.cursors.isEmpty()) {
                        observer.stopWatching()
                        observers.remove(directory)
                    }
                }
            }
            super.close()
        }
    }

    @Suppress("DEPRECATION") // The File constructor is only available from API 29.
    private inner class DirectoryObserver(private val directory: File) : FileObserver(directory.path, WatchEvents) {
        val cursors = mutableSetOf<DirectoryCursor>()

        override fun onEvent(event: Int, path: String?) {
            if (event and WatchEvents == 0) return
            val active = synchronized(observers) {
                val active = cursors.toList()
                if (event and (DELETE_SELF or MOVE_SELF) != 0) {
                    // A recreated directory needs a new inotify watch, even while an old
                    // cursor is still open (for example, across an Alpine reset).
                    if (observers[directory] === this) observers.remove(directory)
                    stopWatching()
                    cursors.clear()
                }
                active
            }
            active.forEach(DirectoryCursor::notifyChanged)
        }
    }

    override fun shutdown() {
        synchronized(observers) {
            observers.values.forEach { it.stopWatching() }
            observers.clear()
        }
        super.shutdown()
    }

    private inline fun <T> documentOperation(block: () -> T): T = try {
        block()
    } catch (error: FileNotFoundException) {
        throw error
    } catch (error: IOException) {
        throw FileNotFoundException("Unable to access the Linux document.").apply { initCause(error) }
    }

    companion object {
        fun authority(context: Context): String = "${context.packageName}.linux.documents"

        fun notifyRootsChanged(context: Context) {
            val authority = authority(context)
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority), null)
            context.contentResolver.notifyChange(
                DocumentsContract.buildChildDocumentsUri(authority, LinuxDocumentStore.RootDocumentId), null,
            )
        }

        private fun documentComparator(sortOrder: String?): Comparator<LinuxDocument> {
            val comparator = when (sortOrder?.substringBefore(' ')) {
                Document.COLUMN_SIZE -> compareBy<LinuxDocument> { it.size }
                Document.COLUMN_LAST_MODIFIED -> compareBy { it.lastModified }
                else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            }
            return if (sortOrder?.endsWith("DESC", ignoreCase = true) == true) comparator.reversed() else comparator
        }

        private const val WatchEvents = FileObserver.ATTRIB or FileObserver.CLOSE_WRITE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or FileObserver.CREATE or
            FileObserver.DELETE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF

        private val RootProjection = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_MIME_TYPES, Root.COLUMN_AVAILABLE_BYTES,
        )
        private val DocumentProjection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
    }
}
