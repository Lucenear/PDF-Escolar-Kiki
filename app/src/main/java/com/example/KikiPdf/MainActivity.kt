package com.kikipdf

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
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
import com.github.chrisbanes.photoview.PhotoView
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONException
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

import android.content.Context 




class MainActivity : AppCompatActivity() {

    companion object {
        private const val MAX_FILE_SIZE_MB = 100
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024L


    }

    private lateinit var pdfRecyclerView: RecyclerView
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private lateinit var homeScreen: View
    private lateinit var recentFilesContainer: LinearLayout
    private lateinit var txtNoRecentFiles: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnToggleNightMode: ImageButton
    private lateinit var btnSharePdf: ImageButton
    private lateinit var btnClearCache: ImageButton


    private var currentPdfUri: Uri? = null
    private var currentPdfFile: File? = null
    private var currentOriginalUri: Uri? = null
    private val repository by lazy { RecentFilesRepository(this) }
    private var allRecentFiles: List<RecentFile> = emptyList()


    private var wasViewingPdf = false
    private var pdfUriBeforeRecreate: Uri? = null
    private var isNightMode = false
    

    

    



    private lateinit var fastScrollerContainer: android.widget.RelativeLayout
    private lateinit var fastScrollerKnob: android.widget.LinearLayout
    private lateinit var scrollerHandle: CardView
    private lateinit var pageIndicatorBubble: CardView
    private lateinit var pdfPageIndicator: TextView
    
    private val thumbnailsDir: File by lazy {
        File(filesDir, "thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    private var backPressedTime: Long = 0

    private val hideControlsHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideControlsRunnable = Runnable {
        if (::fastScrollerContainer.isInitialized) {
            fastScrollerContainer.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction { fastScrollerContainer.visibility = View.GONE }
                .start()
        }
    }

    private val pdfStorageDir: File by lazy {
        File(filesDir, "pdf_storage").apply {
            if (!exists()) mkdirs()
        }
    }

    private val pdfCacheDir: File by lazy {
        File(cacheDir, "pdf_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            openFilePicker()
        } else {
            showPermissionDeniedDialog()
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

        FileUtils.cleanOldCache(this, pdfStorageDir, pdfCacheDir)
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
        if (::pdfRecyclerView.isInitialized && pdfRecyclerView.visibility == View.VISIBLE && currentPdfUri != null) {
            outState.putString("saved_pdf_uri", currentPdfUri.toString())
            outState.putBoolean("was_viewing_pdf", true)
        }
    }

    private fun initViews() {
        pdfRecyclerView = findViewById(R.id.pdfRecyclerView)
        pdfRecyclerView.layoutManager = LinearLayoutManager(this)
        homeScreen = findViewById(R.id.homeScreen)
        recentFilesContainer = findViewById(R.id.recentFilesContainer)
        txtNoRecentFiles = findViewById(R.id.txtNoRecentFiles)
        btnMenu = findViewById(R.id.btn_menu)
        btnClearCache = findViewById(R.id.btn_clear_cache)
        
        btnToggleNightMode = findViewById(R.id.btnToggleNightMode)
        btnToggleNightMode.setOnClickListener { toggleNightMode() }
        
        btnSharePdf = findViewById(R.id.btnSharePdf)
        btnSharePdf.setOnClickListener { sharePdf() }
        
        fastScrollerContainer = findViewById(R.id.fastScrollerContainer)
        fastScrollerKnob = findViewById(R.id.fastScrollerKnob)
        scrollerHandle = findViewById(R.id.scrollerHandle)
        pageIndicatorBubble = findViewById(R.id.pageIndicatorBubble)
        pdfPageIndicator = findViewById(R.id.pdfPageIndicator)
        
        setupNavigationListeners()
    }

    private fun setupMaterialIcons() {
        setupMenuIcon()
    }
    
    private fun setupNavigationListeners() {
        scrollerHandle.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    hideControlsHandler.removeCallbacks(hideControlsRunnable)
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val containerHeight = fastScrollerContainer.height
                    val knobHeight = fastScrollerKnob.height
                    
                    val location = IntArray(2)
                    fastScrollerContainer.getLocationOnScreen(location)
                    val handleCenterOffset = view.height / 2
                    
                    var newY = event.rawY - location[1] - handleCenterOffset

                    newY = newY.coerceIn(0f, (containerHeight - knobHeight).toFloat())
                    fastScrollerKnob.y = newY
                    

                    val scrollRange = pdfRecyclerView.computeVerticalScrollRange() - pdfRecyclerView.height
                    val handleRange = containerHeight - knobHeight
                    
                    if (handleRange > 0 && scrollRange > 0) {
                        val percentage = newY / handleRange
                        val targetScroll = (percentage * scrollRange).toInt()
                         
                         val adapter = pdfRecyclerView.adapter
                         if (adapter != null && adapter.itemCount > 0) {
                             val targetPos = (percentage * (adapter.itemCount - 1)).toInt()
                             (pdfRecyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(targetPos, 0)
                             updatePageIndicator(targetPos, adapter.itemCount)
                         }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    hideControlsHandler.postDelayed(hideControlsRunnable, 2000)
                    view.parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        pdfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (scrollerHandle.isPressed) return
                
                showControls()
                

                val offset = recyclerView.computeVerticalScrollOffset()
                val range = recyclerView.computeVerticalScrollRange() - recyclerView.height
                val containerHeight = fastScrollerContainer.height
                val knobHeight = fastScrollerKnob.height
                
                if (range > 0 && (containerHeight - knobHeight) > 0) {
                    val percentage = offset.toFloat() / range
                    val newY = percentage * (containerHeight - knobHeight)
                    fastScrollerKnob.y = newY
                }

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                var firstPos = layoutManager.findFirstVisibleItemPosition()
                val lastPos = layoutManager.findLastVisibleItemPosition()
                
                if (firstPos != RecyclerView.NO_POSITION) {
                    val firstView = layoutManager.findViewByPosition(firstPos)
                    if (firstView != null) {
                        val visibleHeight = firstView.bottom - recyclerView.paddingTop
                        if (visibleHeight < recyclerView.height * 0.5) {
                            firstPos++
                        }
                    }

                    val pageCount = pdfRenderer?.pageCount ?: 0
                    if (pageCount > 0) updatePageIndicatorRange(firstPos, lastPos, pageCount)
                }
            }
            
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (scrollerHandle.isPressed) {
                    hideControlsHandler.removeCallbacks(hideControlsRunnable)
                    return
                }
                
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    hideControlsHandler.postDelayed(hideControlsRunnable, 2000)
                } else {
                    hideControlsHandler.removeCallbacks(hideControlsRunnable)
                }
            }
        })
    }
    
    private fun showControls() {
        if (fastScrollerContainer.visibility != View.VISIBLE) {
            fastScrollerContainer.visibility = View.VISIBLE
            fastScrollerContainer.alpha = 0f
            fastScrollerContainer.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        hideControlsHandler.postDelayed(hideControlsRunnable, 3000)
    }

    private fun updatePageIndicator(position: Int, total: Int) {
        updatePageIndicatorRange(position, position, total)
    }
    
    private fun updatePageIndicatorRange(first: Int, last: Int, total: Int) {
        if (first == last) {
             pdfPageIndicator.text = "${first + 1} de $total"
        } else {
             pdfPageIndicator.text = "${first + 1} a ${last + 1} de $total"
        }
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
                R.id.menu_download -> {
                    downloadPdf()
                    true
                }
                R.id.menu_jump_to_page -> {
                    showJumpToPageDialog()
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
            val sourceFile = currentPdfFile ?: FileUtils.getCachedFileFromUri(this, currentPdfUri, pdfStorageDir, pdfCacheDir)

            if (sourceFile != null && sourceFile.exists()) {
                val fileName = FileUtils.getFileName(this, currentPdfUri)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            java.io.FileInputStream(sourceFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Toast.makeText(this, getString(R.string.pdf_saved_in_downloads, fileName), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, R.string.error_downloading, Toast.LENGTH_SHORT).show()
                    }
                } else {
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
                    Toast.makeText(this, getString(R.string.pdf_saved_in_downloads, fileName), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, R.string.error_file_access, Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.error_insufficient_permissions, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_downloading, e.message ?: "Unknown"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearCacheConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_cache_title)
            .setMessage(R.string.clear_cache_message)
            .setPositiveButton(R.string.accept) { _, _ ->
                clearCacheAndRecentFiles()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearCacheAndRecentFiles() {
        try {
            var deletedFiles = 0
            var totalSize = 0L

            if (pdfStorageDir.exists() && pdfStorageDir.isDirectory) {
                pdfStorageDir.listFiles()?.forEach { file ->
                    totalSize += file.length()
                    if (file.delete()) {
                        deletedFiles++
                    }
                }
            }

            if (pdfCacheDir.exists() && pdfCacheDir.isDirectory) {
                pdfCacheDir.listFiles()?.forEach { file ->
                    totalSize += file.length()
                    if (file.delete()) {
                        deletedFiles++
                    }
                }
            }

            if (thumbnailsDir.exists() && thumbnailsDir.isDirectory) {
                thumbnailsDir.listFiles()?.forEach { file ->
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

            repository.clearAllData()

            val sizeMB = String.format("%.2f", totalSize / (1024.0 * 1024.0))
            loadRecentFiles()
        } catch (e: Exception) {

        }
    }



    private fun sharePdf() {
        try {
            val fileToShare = currentPdfFile ?: FileUtils.getCachedFileFromUri(this, currentPdfUri, pdfStorageDir, pdfCacheDir)

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
                    putExtra(Intent.EXTRA_SUBJECT, "Compartiendo PDF: ${FileUtils.getFileName(this@MainActivity, currentPdfUri)}")
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
                Toast.makeText(this, R.string.could_not_access_file, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_sharing, e.message ?: "Unknown"), Toast.LENGTH_SHORT).show()
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
        

        
        val searchView = findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterRecentFiles(newText ?: "")
                return true
            }
        })
        
        loadRecentFiles()
    }

    private fun loadRecentFiles() {
        allRecentFiles = repository.getRecentFiles()
        filterRecentFiles("")
    }
    
    private fun filterRecentFiles(query: String) {
        val filteredList = if (query.isEmpty()) {
            allRecentFiles
        } else {
            allRecentFiles.filter { it.name.contains(query, ignoreCase = true) }
        }
        
        val sortedList = filteredList.sortedWith(compareByDescending<RecentFile> { it.isFavorite }.thenByDescending { it.timestamp })
        
        recentFilesContainer.removeAllViews()

        if (sortedList.isEmpty()) {
            txtNoRecentFiles.visibility = View.VISIBLE
            txtNoRecentFiles.text = if(query.isEmpty()) getString(R.string.no_recent_files) else "No se encontraron resultados"
        } else {
            txtNoRecentFiles.visibility = View.GONE
            for (file in sortedList) {
                addRecentFileToView(file)
            }
        }
    }

    private fun addRecentFileToView(file: RecentFile) {
        val uri = Uri.parse(file.uri)

        
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            radius = 24f
            cardElevation = 4f
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.card_light))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 16)
            background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.menuitem_background)
            isClickable = true
            isFocusable = true
            
            setOnClickListener { 
                openPdfFromRecentFile(uri, file.name) 
            }
            

        }

        val thumbnail = android.widget.ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(120, 160)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.ic_pdf_file)
            
            CoroutineScope(Dispatchers.Main).launch {
                var thumb = getThumbnail(uri)
                if (thumb == null) {

                    withContext(Dispatchers.IO) {
                        generateThumbnailSynchronous(uri)
                    }
                    thumb = getThumbnail(uri)
                }
                
                if (thumb != null) setImageBitmap(thumb)
            }
        }
        
        val infoLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 16, 0)
        }
        
        val titleView = TextView(this).apply {
            text = file.name
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        infoLayout.addView(titleView)

        val starBtn = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(96, 96)
            background = null
            setImageResource(if (file.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            setOnClickListener { toggleFavorite(file) }


        }

        container.addView(thumbnail)
        container.addView(infoLayout)
        container.addView(starBtn)
        
        cardView.addView(container)
        recentFilesContainer.addView(cardView)
    }


    

    

    

    




        



    
    private fun toggleFavorite(file: RecentFile) {
        repository.toggleFavorite(file)
        loadRecentFiles()
    }

    private fun addToRecentFiles(storedUri: Uri, originalUri: Uri, fileName: String? = null) {
        val displayName = fileName ?: FileUtils.getFileName(this, originalUri)
        repository.addRecentFile(storedUri, displayName, originalUri)
        generateThumbnail(storedUri)
        loadRecentFiles() // Refresh UI
    }

    
    private fun removeFromRecentFiles(uri: Uri) {
        repository.removeRecentFile(uri.toString())
        loadRecentFiles()
        
        Toast.makeText(this, R.string.file_removed_from_recent, Toast.LENGTH_SHORT).show()
    }



    private fun showPdfView() {
        homeScreen.visibility = View.GONE
        pdfRecyclerView.visibility = View.VISIBLE
        btnToggleNightMode.visibility = View.VISIBLE
        btnSharePdf.visibility = View.VISIBLE
        btnMenu.visibility = View.VISIBLE
        fastScrollerContainer.visibility = View.VISIBLE
    }

    private fun showHomeScreen() {
        runOnUiThread {
            homeScreen.visibility = View.VISIBLE
            pdfRecyclerView.visibility = View.GONE
            btnMenu.visibility = View.GONE
            btnToggleNightMode.visibility = View.GONE
            btnSharePdf.visibility = View.GONE
            fastScrollerContainer.visibility = View.GONE

            closeRenderer()

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }
                filePickerLauncher.launch(intent)
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED) {
                    launchFilePicker()
                } else {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ))
                }
            }
        } else {
            launchFilePicker()
        }
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        filePickerLauncher.launch(intent)
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.storage_permission_required)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openPdfFromRecentFile(uri: Uri, fileName: String) {
        try {
            val storedFile = File(pdfStorageDir, fileName)
            
            if (storedFile.exists()) {
                currentPdfFile = storedFile
                currentPdfUri = Uri.fromFile(storedFile)
                currentOriginalUri = getOriginalUri(uri)
                
                if (currentOriginalUri != null) {
                    addToRecentFiles(currentPdfUri!!, currentOriginalUri!!, fileName)
                }

                loadPdfFromUri(currentPdfUri!!)
                showPdfView()
            } else {
                val originalUri = getOriginalUri(uri)
                if (originalUri != null) {
                    Toast.makeText(this, "Reabriendo archivo...", Toast.LENGTH_SHORT).show()
                    openPdfFromUri(originalUri, false)
                } else {
                    showFileNotAvailableDialog(uri, fileName)
                }
            }
        } catch (e: Exception) {
            showFileNotAvailableDialog(uri, fileName)
        }
    }

    private fun showFileNotAvailableDialog(uri: Uri, fileName: String) {
        AlertDialog.Builder(this)
            .setTitle("Archivo no disponible")
            .setMessage("El archivo \"$fileName\" ya no está disponible.\n\n¿Deseas eliminarlo de archivos recientes?")
            .setPositiveButton("Eliminar") { _, _ ->
                removeFromRecentFiles(uri)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openPdfFromUri(uri: Uri, isReopen: Boolean = false) {
        try {
            val fileSize = getFileSize(uri)
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                val sizeMB = fileSize / (1024 * 1024)
                showPdfErrorDialog("El archivo es demasiado grande ($sizeMB MB). El tamaño máximo soportado es $MAX_FILE_SIZE_MB MB.")
                return
            }

            val persistentFile = copyToPersistentStorage(uri)
            currentPdfFile = persistentFile
            currentPdfUri = Uri.fromFile(persistentFile)
            currentOriginalUri = uri

            loadPdfFromUri(currentPdfUri!!)
            showPdfView()
            
            if (!isReopen) {
                addToRecentFiles(currentPdfUri!!, uri, FileUtils.getFileName(this, uri))
            }

        } catch (e: Exception) {
            showPdfErrorDialog(getString(R.string.could_not_open_file) + ": ${e.message ?: "Error desconocido"}")
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun copyToPersistentStorage(uri: Uri): File {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                throw Exception("No se pudo abrir el stream del archivo")
            }

            val fileName = FileUtils.getFileName(this@MainActivity, uri)
            val outputFile = File(pdfStorageDir, fileName)

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

    private fun copyToCache(uri: Uri): File {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                throw Exception("No se pudo abrir el stream del archivo")
            }

            val fileName = FileUtils.getFileName(this@MainActivity, uri)
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
                .setTitle(R.string.error_opening_pdf)
                .setMessage(getString(R.string.error_message_template, message))
                .setPositiveButton(R.string.accept, null)
                .show()

            Toast.makeText(this, R.string.could_not_open_file, Toast.LENGTH_LONG).show()
        }
    }



    private fun getOriginalUri(storedUri: Uri): Uri? {
        return repository.getOriginalUri(storedUri)
    }

    private fun generateThumbnail(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = FileUtils.getCachedFileFromUri(this@MainActivity, uri, pdfStorageDir, pdfCacheDir) ?: return@launch
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val width = 300
                    val height = (width * 1.414).toInt()
                    
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    

                    val thumbName = "thumb_${file.name}.png"
                    val thumbFile = File(thumbnailsDir, thumbName)
                    FileOutputStream(thumbFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {

            }
        }
    }
    private fun generateThumbnailSynchronous(uri: Uri) {
        try {
            val file = FileUtils.getCachedFileFromUri(this, uri, pdfStorageDir, pdfCacheDir) ?: return
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val width = 300
                val height = (width * 1.414).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                

                val thumbName = "thumb_${file.name}.png"
                val thumbFile = File(thumbnailsDir, thumbName)
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {

        }
    }
    private suspend fun getThumbnail(uri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = FileUtils.getFileName(this@MainActivity, uri)
                val thumbName = "thumb_$fileName.png"
                val thumbFile = File(thumbnailsDir, thumbName)
                
                if (thumbFile.exists()) {
                     android.graphics.BitmapFactory.decodeFile(thumbFile.absolutePath)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun loadPdfFromUri(uri: Uri) {
        try {
            closeRenderer()

            val file = FileUtils.getCachedFileFromUri(this, uri, pdfStorageDir, pdfCacheDir) ?: return
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)

            pdfRecyclerView.adapter = PdfAdapter(pdfRenderer!!, isNightMode)

            currentPdfUri = uri
            showPdfView()
            
            updatePageIndicator(0, pdfRenderer!!.pageCount)
        } catch (e: Exception) {
            showPdfErrorDialog("Error al mostrar PDF: ${e.message}")
        }
    }

    private fun closeRenderer() {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {}
    }






    private fun toggleNightMode() {
        val layoutManager = pdfRecyclerView.layoutManager as LinearLayoutManager
        val currentPosition = layoutManager.findFirstVisibleItemPosition()

        isNightMode = !isNightMode
        btnToggleNightMode.setImageResource(if (isNightMode) R.drawable.ic_day else R.drawable.ic_night)
        
        if (currentPdfUri != null) {

             if (pdfRenderer != null) {
                pdfRecyclerView.adapter = PdfAdapter(pdfRenderer!!, isNightMode)
                
                if (currentPosition != RecyclerView.NO_POSITION) {
                    pdfRecyclerView.scrollToPosition(currentPosition)
                    updatePageIndicator(currentPosition, pdfRenderer!!.pageCount)
                }
             }
        }
    }

    private fun showJumpToPageDialog() {
        val pageCount = pdfRenderer?.pageCount ?: 0
        if (pageCount == 0) return

        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1 - $pageCount"
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.jump_to_page_title)
            .setView(input)
            .setPositiveButton(R.string.go_button) { _, _ ->
                val pageStr = input.text.toString()
                if (pageStr.isNotEmpty()) {
                    val page = pageStr.toInt() - 1
                    if (page in 0 until pageCount) {
                        pdfRecyclerView.scrollToPosition(page)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupBackPressedHandler() {
        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::pdfRecyclerView.isInitialized && pdfRecyclerView.visibility == View.VISIBLE) {
                    showHomeScreen()
                } else {
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        finish()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.press_again_to_exit, Toast.LENGTH_SHORT).show()
                        backPressedTime = System.currentTimeMillis()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        closeRenderer()
    }
}
