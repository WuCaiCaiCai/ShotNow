package com.shotnow.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

object ClipboardHelper {

    private const val TAG = "ShotNow"

    /**
     * Puts an image://content URI on the clipboard. The URI is served by
     * [ImageClipProvider], which is exported read-only because clipboard
     * grants do not travel with ClipData to the pasting app.
     */
    fun copyImage(context: Context, file: File): Boolean {
        return try {
            val uri = Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(context.packageName + ".imageclip")
                .appendPath(file.name)
                .build()
            val clip = ClipData.newUri(context.contentResolver, "screenshot.png", uri)
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(clip)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "copy image failed", t)
            false
        }
    }
}
