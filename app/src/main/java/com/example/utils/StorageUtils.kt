package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object StorageUtils {

    /**
     * Saves a meme Bitmap directly to device external storage (Pictures/KandalaHub)
     * using MediaStore for Android 10+ and legacy storage fallback for older APIs.
     */
    suspend fun saveMemeToDeviceStorage(context: Context, bitmap: Bitmap, title: String = "Kandala_Meme"): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val filename = "${title}_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KandalaHub")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

                resolver.openOutputStream(imageUri)?.use { outputStream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                } ?: return@withContext Result.failure(Exception("Failed to open output stream"))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }

                Result.success(imageUri)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Saves a meme Bitmap to internal app storage so it can be loaded locally inside the app.
     */
    suspend fun saveMemeLocally(context: Context, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val memesDir = File(context.filesDir, "memes").apply { if (!exists()) mkdirs() }
            val file = File(memesDir, "meme_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            file.absolutePath
        }
}
