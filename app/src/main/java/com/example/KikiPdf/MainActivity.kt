package com.kikipdf

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.github.barteksc.pdfviewer.PDFView
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var homeScreen: View
    private lateinit var recentFilesContainer: LinearLayout
    private lateinit var txtNoRecentFiles: TextView
    private lateinit var btnTheme: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnDownload: ImageButton // Nuevo botón
    private lateinit var btnClearCache: Button
    private lateinit var sharedPreferences: SharedPreferences

    private val PDF_REQUEST_CODE = 1001
    private var currentPdfUri: Uri? = null
    private val RECENT_FILES_KEY = "recent_files"
    private val THEME_PREF_KEY = "app_theme"
    private val MAX_RECENT_FILES = 10
    private val CACHE_CLEANUP_THRESHOLD = TimeUnit.DAYS.toMillis(1)

    private var wasViewingPdf = false
    private var pdfUriBeforeRecreate: Uri? = null

    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            val savedUri = savedInstanceState.getString("saved_pdf_uri")
            val wasViewing = savedInstanceState.getBoolean("was_viewing_pdf", false)
            if (wasViewing && savedUri != null) {
                pdfUriBeforeRecreate = Uri.parse(savedUri)
                wasViewingPdf = true
            }
        }

        cleanOldCache()
        initViews()
        setupMaterialIcons()
        setupHomeScreen()
        setupThemeButton()
        setupShareButton()
        setupDownloadButton() // Configurar el nuevo botón
        setupClearCacheButton()
        handleIntent(intent)

        if (wasViewingPdf && pdfUriBeforeRecreate != null) {
            openPdfFromUri(pdfUriBeforeRecreate!!)
            wasViewingPdf = false
            pdfUriBeforeRecreate = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pdfView.visibility == View.VISIBLE && currentPdfUri != null) {
            outState.putString("saved_pdf_uri", currentPdfUri.toString())
            outState.putBoolean("was_viewing_pdf", true)
        }
    }

    private fun initViews() {
        pdfView = findViewById(R.id.pdfView)
        homeScreen = findViewById(R.id.homeScreen)
        recentFilesContainer = findViewById(R.id.recentFilesContainer)
        txtNoRecentFiles = findViewById(R.id.txtNoRecentFiles)
        btnTheme = findViewById(R.id.btn_theme)
        btnShare = findViewById(R.id.btn_share)
        btnDownload = findViewById(R.id.btn_download) // Inicializar el nuevo botón
        btnClearCache = findViewById(R.id.btn_clear_cache)
    }

    private fun setupMaterialIcons() {
        updateThemeIcon()
        setupShareIcon()
        setupDownloadIcon() // Configurar icono de descarga
    }

    private fun updateThemeIcon() {
        val currentTheme = sharedPreferences.getInt(THEME_PREF_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val isDarkTheme = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        val themeIconRes = if (isDarkTheme) {
            R.drawable.ic_day
        } else {
            R.drawable.ic_night
        }

        btnTheme.setImageResource(themeIconRes)
        btnTheme.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun setupShareIcon() {
        btnShare.setImageResource(R.drawable.ic_share)
        btnShare.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun setupDownloadIcon() {
        btnDownload.setImageResource(R.drawable.ic_download)
        btnDownload.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun setupShareButton() {
        btnShare.setOnClickListener {
            sharePdf()
        }
    }

    private fun setupDownloadButton() {
        btnDownload.setOnClickListener {
            downloadPdf()
        }
    }

    private fun setupClearCacheButton() {
        btnClearCache.setOnClickListener {
            showClearCacheConfirmation()
        }
    }

    private fun downloadPdf() {
        currentPdfUri?.let { uri ->
            try {
                val fileName = getFileName(uri)
                val inputStream = contentResolver.openInputStream(uri)

                if (inputStream != null) {
                    // Crear directorio de descargas si no existe
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }

                    // Crear archivo de destino
                    val outputFile = File(downloadsDir, fileName)
                    val outputStream = FileOutputStream(outputFile)

                    // Copiar el archivo
                    inputStream.copyTo(outputStream)

                    // Cerrar streams
                    inputStream.close()
                    outputStream.close()

                    // Mostrar mensaje de éxito
                    Toast.makeText(this, "PDF guardado en: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()

                    // Opcional: escanear el archivo para que aparezca en la galería
                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    mediaScanIntent.data = Uri.fromFile(outputFile)
                    sendBroadcast(mediaScanIntent)

                } else {
                    Toast.makeText(this, "Error: No se pudo leer el archivo", Toast.LENGTH_SHORT).show()
                }

            } catch (e: SecurityException) {
                Toast.makeText(this, "Error: Permisos insuficientes para guardar el archivo", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Error al descargar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No hay PDF abierto para descargar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearCacheConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Limpiar Historial")
            .setMessage("¿Estás seguro de que deseas eliminar todos los archivos temporales y el historial de archivos recientes?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("Aceptar") { _, _ ->
                clearCacheAndRecentFiles()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun clearCacheAndRecentFiles() {
        try {
            val cacheDir = cacheDir
            var deletedFiles = 0
            var totalSize = 0L

            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("pdf_cache_") && file.extension == "pdf") {
                        totalSize += file.length()
                        if (file.delete()) {
                            deletedFiles++
                        }
                    }
                }
            }

            sharedPreferences.edit().remove(RECENT_FILES_KEY).apply()

            val sizeMB = String.format("%.2f", totalSize / (1024.0 * 1024.0))
            val message = if (deletedFiles > 0) {
                "Historial limpiado: $deletedFiles archivos ($sizeMB MB)"
            } else {
                "Historial limpiado correctamente"
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            loadRecentFiles()

        } catch (e: Exception) {
            Toast.makeText(this, "Error al limpiar el historial", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanOldCache() {
        try {
            val cacheDir = cacheDir
            val now = System.currentTimeMillis()

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

    private fun sharePdf() {
        currentPdfUri?.let { uri ->
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_SUBJECT, "Compartiendo PDF: ${getFileName(uri)}")
                    putExtra(Intent.EXTRA_TEXT, "Te comparto este archivo PDF")
                }

                val chooserIntent = Intent.createChooser(shareIntent, "Compartir PDF via")

                if (shareIntent.resolveActivity(packageManager) != null) {
                    startActivity(chooserIntent)
                } else {
                    Toast.makeText(this, "No hay aplicaciones disponibles para compartir", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error al compartir el archivo", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No hay PDF abierto para compartir", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme() {
        val theme = sharedPreferences.getInt(THEME_PREF_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(theme)
    }

    private fun setupThemeButton() {
        updateThemeIcon()
        btnTheme.setOnClickListener {
            toggleTheme()
        }
    }

    private fun toggleTheme() {
        val wasInPdfView = pdfView.visibility == View.VISIBLE
        val currentUri = currentPdfUri

        val currentTheme = sharedPreferences.getInt(THEME_PREF_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val newTheme = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
            AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }

        sharedPreferences.edit().putInt(THEME_PREF_KEY, newTheme).apply()
        AppCompatDelegate.setDefaultNightMode(newTheme)

        if (wasInPdfView && currentUri != null) {
            wasViewingPdf = true
            pdfUriBeforeRecreate = currentUri
        }

        recreate()
    }

    private fun setupHomeScreen() {
        findViewById<View>(R.id.cardOpenPdf).setOnClickListener {
            openFilePicker()
        }
        loadRecentFiles()
    }

    private fun loadRecentFiles() {
        val recentFiles = getRecentFiles()
        recentFilesContainer.removeAllViews()

        if (recentFiles.isEmpty()) {
            txtNoRecentFiles.visibility = View.VISIBLE
        } else {
            txtNoRecentFiles.visibility = View.GONE
            for (fileData in recentFiles) {
                val uri = Uri.parse(fileData.first)
                val fileName = fileData.second
                addRecentFileToView(uri, fileName)
            }
        }
    }

    private fun addRecentFileToView(uri: Uri, fileName: String) {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
            radius = 10f
            cardElevation = 1.5f
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card_light))
        }

        val linearLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 0, 24, 28)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.menuitem_background)
            isClickable = true
            isFocusable = true
            minimumHeight = 72
        }

        val textView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
            text = fileName
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            maxLines = 1
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val iconView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "⋮"
            textSize = 28f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(16, 0, 16, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        linearLayout.addView(textView)
        linearLayout.addView(iconView)
        cardView.addView(linearLayout)

        linearLayout.setOnClickListener {
            openPdfFromUri(uri)
        }

        linearLayout.setOnLongClickListener {
            showFileContextMenu(linearLayout, uri, fileName)
            true
        }

        iconView.setOnClickListener {
            showFileContextMenu(linearLayout, uri, fileName)
        }

        recentFilesContainer.addView(cardView)
    }

    private fun showFileContextMenu(view: View, uri: Uri, fileName: String) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.file_context_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_delete -> {
                    removeFromRecentFiles(uri)
                    true
                }
                R.id.menu_share -> {
                    shareSpecificPdf(uri, fileName)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun removeFromRecentFiles(uri: Uri) {
        val recentFiles = getRecentFiles().toMutableList()
        val removed = recentFiles.removeAll { it.first == uri.toString() }

        if (removed) {
            saveRecentFiles(recentFiles)
            loadRecentFiles()
            Toast.makeText(this, "Archivo eliminado de recientes", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSpecificPdf(uri: Uri, fileName: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "Compartiendo PDF: $fileName")
                putExtra(Intent.EXTRA_TEXT, "Te comparto este archivo PDF: $fileName")
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Compartir PDF via")
            if (shareIntent.resolveActivity(packageManager) != null) {
                startActivity(chooserIntent)
            } else {
                Toast.makeText(this, "No hay aplicaciones disponibles para compartir", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPdfView() {
        homeScreen.visibility = View.GONE
        pdfView.visibility = View.VISIBLE
        btnShare.visibility = View.VISIBLE
        btnDownload.visibility = View.VISIBLE // Mostrar botón de descarga
        btnTheme.visibility = View.GONE
    }

    private fun showHomeScreen() {
        runOnUiThread {
            homeScreen.visibility = View.VISIBLE
            pdfView.visibility = View.GONE
            btnShare.visibility = View.GONE
            btnDownload.visibility = View.GONE // Ocultar botón de descarga
            btnTheme.visibility = View.VISIBLE
            currentPdfUri = null
            loadRecentFiles()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.type != null) {
            when {
                intent.type!!.startsWith("application/pdf") -> {
                    intent.data?.let { uri ->
                        openPdfFromUri(uri)
                    }
                }
                intent.dataString?.endsWith(".pdf") == true -> {
                    intent.data?.let { uri ->
                        openPdfFromUri(uri)
                    }
                }
                intent.data != null -> {
                    intent.data?.let { uri ->
                        openPdfFromUri(uri)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        startActivityForResult(intent, PDF_REQUEST_CODE)
    }

    private fun openPdfFromUri(uri: Uri) {
        try {
            if (uri.toString().contains("whatsapp")) {
                val cachedUri = copyToCache(uri)
                val fileName = getFileName(uri)
                loadPdfFromUri(cachedUri)
                showPdfView()
                addToRecentFiles(cachedUri, fileName)
                return
            }

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
            }

            loadPdfFromUri(uri)
            showPdfView()
            addToRecentFiles(uri)

        } catch (e: SecurityException) {
            try {
                val cachedUri = copyToCache(uri)
                val fileName = getFileName(uri)
                loadPdfFromUri(cachedUri)
                showPdfView()
                addToRecentFiles(cachedUri, fileName)
            } catch (e2: Exception) {
                showPdfErrorDialog("No se pudo abrir el archivo: Error de permisos")
            }
        } catch (e: Exception) {
            showPdfErrorDialog("No se pudo abrir el archivo: ${e.message ?: "Error desconocido"}")
        }
    }

    private fun copyToCache(uri: Uri): Uri {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                throw Exception("No se pudo abrir el stream del archivo")
            }

            val tempFile = File.createTempFile("pdf_cache_${System.currentTimeMillis()}", ".pdf", cacheDir)
            outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            return Uri.fromFile(tempFile)

        } catch (e: Exception) {
            throw e
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private fun showPdfErrorDialog(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Error al abrir PDF")
                .setMessage("$message\n\nAsegúrate de que:\n• El archivo no esté corrupto\n• Tengas permisos para acceder al archivo")
                .setPositiveButton("Aceptar", null)
                .show()

            Toast.makeText(this, "No se pudo abrir el archivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun addToRecentFiles(uri: Uri, fileName: String? = null) {
        val recentFiles = getRecentFiles().toMutableList()
        recentFiles.removeAll { it.first == uri.toString() }
        val displayName = fileName ?: getFileName(uri)
        recentFiles.add(0, Pair(uri.toString(), displayName))

        if (recentFiles.size > MAX_RECENT_FILES) {
            recentFiles.removeAt(recentFiles.size - 1)
        }
        saveRecentFiles(recentFiles)
    }

    private fun getFileName(uri: Uri): String {
        var result = "documento.pdf"
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, null, null, null, null)
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

        if (result.length > 30) {
            result = result.take(27) + "..."
        }
        return result
    }

    private fun getRecentFiles(): List<Pair<String, String>> {
        val jsonString = sharedPreferences.getString(RECENT_FILES_KEY, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val files = mutableListOf<Pair<String, String>>()
            for (i in 0 until jsonArray.length()) {
                val fileArray = jsonArray.getJSONArray(i)
                files.add(Pair(fileArray.getString(0), fileArray.getString(1)))
            }
            files
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun saveRecentFiles(files: List<Pair<String, String>>) {
        try {
            val jsonArray = JSONArray()
            for (file in files) {
                val fileArray = JSONArray()
                fileArray.put(file.first)
                fileArray.put(file.second)
                jsonArray.put(fileArray)
            }
            sharedPreferences.edit().putString(RECENT_FILES_KEY, jsonArray.toString()).apply()
        } catch (e: Exception) {
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PDF_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                openPdfFromUri(uri)
            }
        }
    }

    private fun loadPdfFromUri(uri: Uri) {
        try {
            currentPdfUri = uri
            pdfView.fromUri(uri)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .onError { t ->
                    throw Exception("Error en PDFView: ${t?.message}")
                }
                .load()
        } catch (e: Exception) {
            throw e
        }
    }

    override fun onBackPressed() {
        if (pdfView.visibility == View.VISIBLE) {
            showHomeScreen()
        } else {
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                super.onBackPressed()
                finish()
            } else {
                Toast.makeText(this, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show()
            }
            backPressedTime = System.currentTimeMillis()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}