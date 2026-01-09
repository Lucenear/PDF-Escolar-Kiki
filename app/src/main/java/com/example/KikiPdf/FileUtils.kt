package com.kikipdf

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.TimeUnit

object FileUtils {
    private val CACHE_CLEANUP_THRESHOLD = TimeUnit.DAYS.toMillis(30)

    fun getFileName(context: Context, uri: Uri?): String {
        if (uri == null) return context.getString(R.string.default_filename)

        var result = "documento.pdf"

        if (uri.scheme == "file") {
            val file = File(uri.path!!)
            result = file.name
        } else {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.let {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex >= 0) {
                            result = it.getString(displayNameIndex) ?: "documento.pdf"
                        }
                    }
                }
            } catch (e: Exception) {
                result = uri.lastPathSegment ?: "documento.pdf"
            } finally {
                cursor?.close()
            }
        }
        return result
    }

    fun getCachedFileFromUri(context: Context, uri: Uri?, pdfStorageDir: File, pdfCacheDir: File): File? {
        if (uri == null) return null

        return try {
            if (uri.scheme == "file") {
                File(uri.path!!)
            } else {
                val fileName = getFileName(context, uri)
                val storedFile = File(pdfStorageDir, fileName)
                if (storedFile.exists()) {
                    storedFile
                } else {
                    File(pdfCacheDir, fileName)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun cleanOldCache(context: Context, pdfStorageDir: File, pdfCacheDir: File) {
        try {
            val now = System.currentTimeMillis()

            if (pdfStorageDir.exists() && pdfStorageDir.isDirectory) {
                pdfStorageDir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > CACHE_CLEANUP_THRESHOLD) {
                        file.delete()
                    }
                }
            }

            if (pdfCacheDir.exists() && pdfCacheDir.isDirectory) {
                pdfCacheDir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > CACHE_CLEANUP_THRESHOLD) {
                        file.delete()
                    }
                }
            }

            val cacheDir = context.cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("pdf_cache_") && file.extension == "pdf") {
                        if (now - file.lastModified() > CACHE_CLEANUP_THRESHOLD) {
                            file.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }
}
