package com.mediascroll.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.mediascroll.app.model.MediaItem

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var tvHint: TextView

    private val exitHandler = Handler(Looper.getMainLooper())
    private var volumeDownStartTime = 0L
    private var mediaLoaded = false

    private var threeFingerStartY = 0f
    private var threeFingerActive = false

    companion object {
        private const val REQ_PERMISSIONS = 200
    }

    // ─── onCreate ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()
        setContentView(R.layout.activity_main)

        tvHint = findViewById(R.id.tvHint)

        exoPlayer = ExoPlayer.Builder(this).build()

        recyclerView = findViewById(R.id.recyclerView)
        layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)

        mediaAdapter = MediaAdapter(this, exoPlayer)
        recyclerView.adapter = mediaAdapter
        PagerSnapHelper().attachToRecyclerView(recyclerView)

        setupScrollListener()
        setupTouchListener()

        showHint("Toca la pantalla para\nseleccionar una carpeta")
        // Pedir permisos directamente aquí, sin abrir otra Activity
        requestStoragePermissions()
    }

    // ─── Permisos ─────────────────────────────────────────────────────────────

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        if (hasPermissions()) {
            showFolderPicker()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions(), REQ_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showFolderPicker()
            } else {
                showHint("Permiso denegado.\n\nVe a Ajustes → Apps → MediaScroll → Permisos\ny activa Almacenamiento.\n\nLuego toca aquí para continuar.")
            }
        }
    }

    // ─── Selector de carpetas (AlertDialog dentro de MainActivity) ────────────

    private fun showFolderPicker() {
        showHint("Cargando carpetas…")

        // Cargar carpetas en hilo de fondo para no bloquear la UI
        Thread {
            val folderMap = linkedMapOf<String, String>()
            val projection = arrayOf(
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.BUCKET_ID
            )
            val collections = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
            for (uri in collections) {
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val idCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol)?.trim() ?: continue
                        val id   = cursor.getString(idCol) ?: continue
                        if (name.isNotBlank()) folderMap[name] = id
                    }
                }
            }

            // Ordenar alfabéticamente
            val sorted = folderMap.entries.sortedBy { it.key.lowercase() }
            val names  = sorted.map { it.key }
            val ids    = sorted.map { it.value }

            runOnUiThread {
                if (names.isEmpty()) {
                    showHint("No se encontraron carpetas con fotos o vídeos.\n\nToca para intentarlo de nuevo.")
                    return@runOnUiThread
                }

                hideHint()

                // Mostrar diálogo de selección de carpeta
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
                AlertDialog.Builder(this)
                    .setTitle("Selecciona una carpeta")
                    .setAdapter(adapter) { dialog, position ->
                        dialog.dismiss()
                        loadMediaFromBucket(ids[position])
                    }
                    .setOnCancelListener {
                        showHint("Toca la pantalla para\nseleccionar una carpeta")
                    }
                    .show()
            }
        }.start()
    }

    private fun loadMediaFromBucket(bucketId: String) {
        showHint("Cargando…")

        Thread {
            val items = mutableListOf<MediaItem>()

            // Imágenes
            val imgProj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgProj,
                "${MediaStore.Images.Media.BUCKET_ID} = ?", arrayOf(bucketId), null
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id  = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    items.add(MediaItem(uri, cursor.getString(nameCol) ?: "img_$id", false))
                }
            }

            // Vídeos
            val vidProj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vidProj,
                "${MediaStore.Video.Media.BUCKET_ID} = ?", arrayOf(bucketId), null
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id  = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                    items.add(MediaItem(uri, cursor.getString(nameCol) ?: "vid_$id", true))
                }
            }

            items.sortWith(compareBy { it.name.lowercase() })

            runOnUiThread {
                if (items.isEmpty()) {
                    Toast.makeText(this, "Carpeta vacía", Toast.LENGTH_SHORT).show()
                    showHint("Carpeta vacía.\nToca para elegir otra.")
                    return@runOnUiThread
                }
                mediaLoaded = true
                hideHint()
                mediaAdapter.submitList(items) { mediaAdapter.onItemVisible(0) }
            }
        }.start()
    }

    // ─── UI helpers ──────────────────────────────────────────────────────────

    private fun showHint(msg: String) {
        tvHint.text = msg
        tvHint.visibility = View.VISIBLE
    }

    private fun hideHint() {
        tvHint.visibility = View.GONE
    }

    // ─── Fullscreen ───────────────────────────────────────────────────────────

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars() or WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    // ─── Teclas de volumen ────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (volumeDownStartTime == 0L) {
                            volumeDownStartTime = System.currentTimeMillis()
                            exitHandler.postDelayed({
                                if (volumeDownStartTime > 0L) finishAndRemoveTask()
                            }, 3000L)
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        volumeDownStartTime = 0L
                        exitHandler.removeCallbacksAndMessages(null)
                    }
                }
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    // ─── Scroll ───────────────────────────────────────────────────────────────

    private fun setupScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val pos = layoutManager.findFirstCompletelyVisibleItemPosition()
                    if (pos != RecyclerView.NO_POSITION) mediaAdapter.onItemVisible(pos)
                }
            }
        })
    }

    // ─── Touch ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!mediaLoaded) {
                    requestStoragePermissions()
                    return true
                }
                navigateNext()
                return true
            }
        })

        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> threeFingerActive = false
                    MotionEvent.ACTION_POINTER_DOWN -> if (e.pointerCount == 3) {
                        threeFingerStartY = e.getY(0); threeFingerActive = true
                    }
                    MotionEvent.ACTION_MOVE -> if (threeFingerActive && e.pointerCount >= 3 &&
                        e.getY(0) - threeFingerStartY > 120f) {
                        navigateToFirst(); threeFingerActive = false; return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> threeFingerActive = false
                }
                if (e.pointerCount == 1) gestureDetector.onTouchEvent(e)
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(d: Boolean) {}
        })
    }

    private fun navigateNext() {
        val current = layoutManager.findFirstCompletelyVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION } ?: layoutManager.findFirstVisibleItemPosition()
        val next = (current + 1).coerceAtMost(mediaAdapter.itemCount - 1)
        if (next != current) recyclerView.smoothScrollToPosition(next)
    }

    private fun navigateToFirst() {
        recyclerView.scrollToPosition(0)
        mediaAdapter.onItemVisible(0)
    }

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        mediaAdapter.pausePlayback()
        exitHandler.removeCallbacksAndMessages(null)
        volumeDownStartTime = 0L
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (mediaLoaded) mediaAdapter.resumePlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!mediaLoaded) { @Suppress("DEPRECATION") super.onBackPressed() }
    }
}
