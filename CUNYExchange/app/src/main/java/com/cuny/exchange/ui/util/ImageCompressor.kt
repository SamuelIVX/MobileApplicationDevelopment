package com.cuny.exchange.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCompressor {
    /**
     * Compresses and scales an image from a URI:
     * - Uses inJustDecodeBounds to prevent OOM on high-resolution photos
     * - Applies EXIF orientation (rotates/mirrors) so photos are right-side-up
     * - Scales down so max(width, height) <= maxDimension (default 1200px)
     * - Compresses to JPEG at 80% quality, stepping down if needed to ensure file size <= 1MB
     * - Rejects/returns null if final size cannot be brought under maxByteSize
     */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1200,
        maxByteSize: Int = 1_000_000
    ): ByteArray? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // 1. Decode bounds without allocating pixel memory
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val streamOpened = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
            true
        } ?: false
        if (!streamOpened) return@withContext null

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return@withContext null

        // 2. Read EXIF orientation to correct rotation
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            try {
                val exif = ExifInterface(stream)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
        } ?: ExifInterface.ORIENTATION_NORMAL

        // 3. Compute inSampleSize to downsample during decode
        var sampleSize = 1
        val maxOrig = maxOf(origWidth, origHeight)
        while (maxOrig / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        // 4. Decode scaled-down bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampledBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return@withContext null

        // 5. Apply EXIF rotation if needed
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        val rotatedBitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(
                sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true
            ).also {
                if (it != sampledBitmap) sampledBitmap.recycle()
            }
        } else {
            sampledBitmap
        }

        // 6. Scale to exact max dimension if needed
        val currentWidth = rotatedBitmap.width
        val currentHeight = rotatedBitmap.height
        val maxCurrent = maxOf(currentWidth, currentHeight)
        val finalBitmap = if (maxCurrent > maxDimension) {
            val scale = maxDimension.toFloat() / maxCurrent.toFloat()
            val targetW = (currentWidth * scale).toInt().coerceAtLeast(1)
            val targetH = (currentHeight * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true).also {
                if (it != rotatedBitmap) rotatedBitmap.recycle()
            }
        } else {
            rotatedBitmap
        }

        // 7. Compress to JPEG <= maxByteSize
        var quality = 80
        val outputStream = ByteArrayOutputStream()
        var compressed = finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        if (!compressed) {
            finalBitmap.recycle()
            return@withContext null
        }

        while (outputStream.size() > maxByteSize && quality > 30) {
            outputStream.reset()
            quality -= 15
            compressed = finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            if (!compressed) {
                finalBitmap.recycle()
                return@withContext null
            }
        }

        finalBitmap.recycle()

        // 8. Reject if output still exceeds maxByteSize
        if (outputStream.size() <= maxByteSize) {
            outputStream.toByteArray()
        } else {
            null
        }
    }
}
