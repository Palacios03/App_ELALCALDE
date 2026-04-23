package com.mediascroll.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mediascroll.app.model.MediaItem

class FolderPickerActivity : AppCompatActivity() {

    private val folderMap = linkedMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_picker)
        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val denied = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (denied.isEmpty()) {
            loadFolders()
        } else {
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), REQ_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadFolders()
            } else {
                // Permiso denegado: mostrar diálogo explicativo en lugar de cerrar
                showPermissionDeniedDialog()
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios")
            .setMessage("La app necesita acceder a tus fotos y vídeos para mostrarlos.\n\nVe a Ajustes → Permisos → Almacenamiento y actívalo.")
            .setPositiveButton("Ir a Ajustes") { _, _ ->
                // Abre los ajustes de la app directamente
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivityForResult(intent, REQ_SETTINGS)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SETTINGS) {
            // Vuelve de ajustes — volver a comprobar permisos
            checkPermissionsAndLoad()
        }
    }

    private fun loadFolders() {
        folderMap.clear()
        val tempMap = sortedMapOf<String, String>(compareBy { it.lowercase() })

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
                    val name = cursor.getString(nameCol)?.trim() ?: "Sin nombre"
                    val id   = cursor.getString(idCol) ?: continue
                    if (name.isNotBlank()) tempMap[name] = id
                }
            }
        }

        folderMap.putAll(tempMap)

        if (folderMap.isEmpty()) {
            Toast.makeText(this, "No se encontraron carpetas con fotos o vídeos", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val listView = findViewById<ListView>(R.id.listViewFolders)
        val folderNames = folderMap.keys.toList()
        val adapter = ArrayAdapter(this, R.layout.item_folder, R.id.tvFolderName, folderNames)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val name = folderNames[position]
            val bucketId = folderMap[name] ?: return@setOnItemClickListener
            loadMediaFromBucket(bucketId)
        }
    }

    private fun loadMediaFromBucket(bucketId: String) {
        val items = mutableListOf<MediaItem>()

        val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            "${MediaStore.Images.Media.BUCKET_ID} = ?",
            arrayOf(bucketId), null
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id   = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "image_$id"
                val uri  = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                items.add(MediaItem(uri, name, isVideo = false))
            }
        }

        val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            "${MediaStore.Video.Media.BUCKET_ID} = ?",
            arrayOf(bucketId), null
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id   = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "video_$id"
                val uri  = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                items.add(MediaItem(uri, name, isVideo = true))
            }
        }

        if (items.isEmpty()) {
            Toast.makeText(this, "La carpeta seleccionada está vacía", Toast.LENGTH_SHORT).show()
            return
        }

        items.sortWith(compareBy { it.name.lowercase() })

        val result = Intent().apply {
            putParcelableArrayListExtra(EXTRA_MEDIA_ITEMS, ArrayList(items))
        }
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_MEDIA_ITEMS = "extra_media_items"
        const val REQ_PERMISSION    = 100
        const val REQ_SETTINGS      = 101
    }
}
