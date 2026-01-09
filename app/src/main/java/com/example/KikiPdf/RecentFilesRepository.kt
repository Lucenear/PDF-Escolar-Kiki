package com.kikipdf

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class RecentFilesRepository(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val RECENT_FILES_KEY = "recent_files"
        private const val ORIGINAL_URIS_KEY = "original_uris"
        private const val MAX_RECENT_FILES = 10
    }

    private var allRecentFiles: List<RecentFile> = emptyList()

    fun getRecentFiles(): List<RecentFile> {
        val jsonString = sharedPreferences.getString(RECENT_FILES_KEY, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val files = mutableListOf<RecentFile>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.get(i)
                if (item is JSONArray) {

                    files.add(RecentFile(item.getString(0), item.getString(1)))
                } else if (item is JSONObject) {
                    files.add(RecentFile(
                        uri = item.getString("uri"),
                        name = item.getString("name"),
                        isFavorite = item.optBoolean("fav", false),
                        timestamp = item.optLong("time", System.currentTimeMillis())
                    ))
                }
            }
            files.sortByDescending { it.isFavorite }
            allRecentFiles = files
            files
        } catch (e: JSONException) {
            emptyList()
        }
    }

    fun saveRecentFiles(files: List<RecentFile>) {
        try {
            val jsonArray = JSONArray()
            for (file in files) {
                val jsonObj = JSONObject().apply {
                    put("uri", file.uri)
                    put("name", file.name)
                    put("fav", file.isFavorite)
                    put("time", file.timestamp)
                }
                jsonArray.put(jsonObj)
            }
            sharedPreferences.edit().putString(RECENT_FILES_KEY, jsonArray.toString()).apply()
            allRecentFiles = files
        } catch (e: Exception) {
        }
    }

    fun addRecentFile(uri: Uri, fileName: String, originalUri: Uri?) {
        val currentFiles = getRecentFiles().toMutableList()
        val storedUriString = uri.toString()
        val existingIndex = currentFiles.indexOfFirst { it.uri == storedUriString }



        val newFile = RecentFile(
            uri = storedUriString,
            name = fileName,
            isFavorite = if(existingIndex >= 0) currentFiles[existingIndex].isFavorite else false,
            timestamp = System.currentTimeMillis()
        )

        if (existingIndex >= 0) {
            currentFiles.removeAt(existingIndex)
        }
        
        currentFiles.add(0, newFile)

        if (currentFiles.size > MAX_RECENT_FILES) {
             val lastNonFavorite = currentFiles.findLast { !it.isFavorite }
             if (lastNonFavorite != null) {
                 currentFiles.remove(lastNonFavorite)
             } else {
                 currentFiles.removeAt(currentFiles.size - 1)
             }
        }
        
        saveRecentFiles(currentFiles)
        if (originalUri != null) {
            saveOriginalUri(uri, originalUri)
        }
    }

    fun toggleFavorite(file: RecentFile): List<RecentFile> {
        val currentFiles = getRecentFiles().toMutableList()
        val index = currentFiles.indexOfFirst { it.uri == file.uri }
        if (index >= 0) {
            val updatedFile = file.copy(isFavorite = !file.isFavorite)
            currentFiles[index] = updatedFile
            saveRecentFiles(currentFiles)
            return currentFiles
        }
        return currentFiles
    }
    
    fun removeRecentFile(uri: String) {
        val currentFiles = getRecentFiles().toMutableList()
        val index = currentFiles.indexOfFirst { it.uri == uri }
        if (index >= 0) {
            currentFiles.removeAt(index)
            saveRecentFiles(currentFiles)
        }
    }

    fun clearAllData() {
        sharedPreferences.edit()
            .remove(RECENT_FILES_KEY)
            .remove(ORIGINAL_URIS_KEY)
            .apply()
        allRecentFiles = emptyList()
    }


    fun saveOriginalUri(storedUri: Uri, originalUri: Uri) {
        try {
            val originalUris = getOriginalUrisMap().toMutableMap()
            originalUris[storedUri.toString()] = originalUri.toString()
            
            val jsonObject = JSONArray()
            originalUris.forEach { (stored, original) ->
                val pair = JSONArray()
                pair.put(stored)
                pair.put(original)
                jsonObject.put(pair)
            }
            
            sharedPreferences.edit()
                .putString(ORIGINAL_URIS_KEY, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
        }
    }

    fun getOriginalUri(storedUri: Uri): Uri? {
        return try {
            val originalUris = getOriginalUrisMap()
            val originalUriString = originalUris[storedUri.toString()]
            if (originalUriString != null) Uri.parse(originalUriString) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getOriginalUrisMap(): Map<String, String> {
        val jsonString = sharedPreferences.getString(ORIGINAL_URIS_KEY, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val map = mutableMapOf<String, String>()
            for (i in 0 until jsonArray.length()) {
                val pairArray = jsonArray.getJSONArray(i)
                map[pairArray.getString(0)] = pairArray.getString(1)
            }
            map
        } catch (e: JSONException) {
            emptyMap()
        }
    }
}
