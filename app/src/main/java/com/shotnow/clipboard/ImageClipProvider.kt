package com.shotnow.clipboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Read-only provider for screenshots placed on the clipboard.
 * Only exposes cache/shots/<32 hex chars>.png, nothing else.
 */
class ImageClipProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (!mode.startsWith("r")) throw FileNotFoundException("read-only provider")
        val file = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = fileFor(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(columns, 1)
        val values = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> "screenshot.png"
                OpenableColumns.SIZE -> file?.length() ?: 0L
                else -> null
            }
        }
        cursor.addRow(values.toTypedArray())
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("read-only provider")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("read-only provider")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        throw UnsupportedOperationException("read-only provider")
    }

    private fun fileFor(uri: Uri): File? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        if (!NAME_PATTERN.matches(name)) return null
        val directory = File(ctx.cacheDir, "shots").canonicalFile
        val file = File(directory, name).canonicalFile
        return if (file.parentFile == directory && file.isFile) file else null
    }

    companion object {
        private val NAME_PATTERN = Regex("[a-f0-9]{32}\\.png")
    }
}
