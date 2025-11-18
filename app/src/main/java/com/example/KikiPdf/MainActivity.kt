package com.kikipdf

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.media.MediaScannerConnection
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
    private lateinit var btnMenu: ImageButton
    private lateinit var btnClearCache: Button
    private lateinit var sharedPreferences: SharedPreferences

    private var currentPdfUri: Uri? = null
    private var currentPdfFile: File? = null
    private val RECENT_FILES_KEY = "recent_files"
    private val MAX_RECENT_FILES = 10
    private val CACHE_CLEANUP_THRESHOLD = TimeUnit.DAYS.toMillis(1)

    private var wasViewingPdf = false
    private var pdfUriBeforeRecreate: Uri? = null

    private var backPressedTime: Long = 0

    private val pdfCacheDir: File by lazy {
        File(cacheDir, "pdf_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                openPdfFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)

        applySystemTheme()

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
        setupMenuButton()
        setupClearCacheButton()
        setupBackPressedHandler()
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
        btnMenu = findViewById(R.id.btn_menu)
        btnClearCache = findViewById(R.id.btn_clear_cache)
    }

    private fun setupMaterialIcons() {
        setupMenuIcon()
    }

    private fun setupMenuIcon() {
        btnMenu.setImageResource(R.drawable.ic_more_vert)
        btnMenu.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
    }

    private fun setupMenuButton() {
        btnMenu.setOnClickListener {
            showPdfOptionsMenu()
        }
    }

    private fun showPdfOptionsMenu() {
        val popup = PopupMenu(this, btnMenu)
        popup.menuInflater.inflate(R.menu.pdf_options_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_share -> {
                    sharePdf()
                    true
                }
                R.id.menu_download -> {
                    downloadPdf()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupClearCacheButton() {
        btnClearCache.setOnClickListener {
            showClearCacheConfirmation()
        }
    }

    private fun downloadPdf() {
        try {
            val sourceFile = currentPdfFile ?: getCachedFileFromUri(currentPdfUri)

            if (sourceFile != null && sourceFile.exists()) {
                val fileName = getFileName(currentPdfUri)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val outputFile = File(downloadsDir, fileName)

                sourceFile.copyTo(outputFile, overwrite = true)

                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(outputFile.absolutePath),
                    arrayOf("application/pdf"),
                    null
                )

                Toast.makeText(this, "PDF guardado en: Descargas/$fileName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Error: No se pudo acceder al archivo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Error: Permisos insuficientes", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al descargar: ${e.message}", Toast.LENGTH_SHORT).show()
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
            var deletedFiles = 0
            var totalSize = 0L

            if (pdfCacheDir.exists() && pdfCacheDir.isDirectory) {
                pdfCacheDir.listFiles()?.forEach { file ->
                    totalSize += file.length()
                    if (file.delete()) {
                        deletedFiles++
                    }
                }
            }

            val cacheDir = cacheDir
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
            val now = System.currentTimeMillis()

            if (pdfCacheDir.exists() && pdfCacheDir.isDirectory) {
                pdfCacheDir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > CACHE_CLEANUP_THRESHOLD) {
                        file.delete()
                    }
                }
            }

            val cacheDir = cacheDir
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
        try {
            val fileToShare = currentPdfFile ?: getCachedFileFromUri(currentPdfUri)

            if (fileToShare != null && fileToShare.exists()) {
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    fileToShare
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_SUBJECT, "Compartiendo PDF: ${getFileName(currentPdfUri)}")
                    putExtra(Intent.EXTRA_TEXT, "Te comparto este archivo PDF")
                }

                val chooserIntent = Intent.createChooser(shareIntent, "Compartir PDF via")
                val resInfoList = packageManager.queryIntentActivities(chooserIntent, 0)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(chooserIntent)
            } else {
                Toast.makeText(this, "No se pudo acceder al archivo para compartir", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCachedFileFromUri(uri: Uri?): File? {
        if (uri == null) return null

        return try {
            if (uri.scheme == "file") {
                File(uri.path!!)
            } else {
                val fileName = getFileName(uri)
                File(pdfCacheDir, fileName)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun applySystemTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun setupHomeScreen() {
        findViewById<View>(R.id.cardOpenPdf).setOnClickListener {
            openFilePicker()
        }
        setupMenuButton()
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
                setMargins(0, 0, 0, 12)
            }
            radius = 10f
            cardElevation = 2f
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card_light))
        }

        val linearLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 18, 20, 18)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.menuitem_background)
            isClickable = true
            isFocusable = true
            minimumHeight = 64
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val docIndicator = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            text = "📄"
            textSize = 16f
            setPadding(0, 0, 20, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        }

        val textView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
            text = fileName
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            maxLines = 1
            gravity = android.view.Gravity.CENTER_VERTICAL
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val menuIcon = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            text = "⋮"
            textSize = 20f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(48, 0, 20, 0)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        linearLayout.addView(docIndicator)
        linearLayout.addView(textView)
        linearLayout.addView(menuIcon)
        cardView.addView(linearLayout)

        linearLayout.setOnClickListener {
            openPdfFromUri(uri)
        }

        linearLayout.setOnLongClickListener {
            showFileContextMenu(linearLayout, uri, fileName)
            true
        }

        menuIcon.setOnClickListener {
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
        btnMenu.visibility = View.VISIBLE
    }

    private fun showHomeScreen() {
        runOnUiThread {
            homeScreen.visibility = View.VISIBLE
            pdfView.visibility = View.GONE
            btnMenu.visibility = View.GONE
            currentPdfUri = null
            currentPdfFile = null
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
        filePickerLauncher.launch(intent)
    }

    private fun openPdfFromUri(uri: Uri) {
        try {
            val cachedFile = copyToCache(uri)
            currentPdfFile = cachedFile
            currentPdfUri = Uri.fromFile(cachedFile)

            loadPdfFromUri(currentPdfUri!!)
            showPdfView()
            addToRecentFiles(currentPdfUri!!, getFileName(uri))

        } catch (e: Exception) {
            showPdfErrorDialog("No se pudo abrir el archivo: ${e.message ?: "Error desconocido"}")
        }
    }

    private fun copyToCache(uri: Uri): File {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                throw Exception("No se pudo abrir el stream del archivo")
            }

            val fileName = getFileName(uri)
            val outputFile = File(pdfCacheDir, fileName)

            if (outputFile.exists()) {
                return outputFile
            }

            outputStream = FileOutputStream(outputFile)
            inputStream.copyTo(outputStream)
            return outputFile

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

    private fun getFileName(uri: Uri?): String {
        if (uri == null) return "documento.pdf"

        var result = "documento.pdf"

        if (uri.scheme == "file") {
            val file = File(uri.path!!)
            result = file.name
        } else {
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
        }

        if (!result.endsWith(".pdf", ignoreCase = true)) {
            result += ".pdf"
        }

        if (result.length > 30) {
            val nameWithoutExt = result.substringBeforeLast(".")
            val extension = result.substringAfterLast(".")
            result = nameWithoutExt.take(27) + "...$extension"
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

    private fun setupBackPressedHandler() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pdfView.visibility == View.VISIBLE) {
                    showHomeScreen()
                } else {
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        finish()
                    } else {
                        Toast.makeText(this@MainActivity, "Presiona de nuevo para salir", Toast.LENGTH_SHORT).show()
                        backPressedTime = System.currentTimeMillis()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
