package com.cuny.exchange.data.repository

import android.content.Context
import android.net.Uri
import com.cuny.exchange.ui.util.ImageCompressor
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

interface StorageRepository {
    suspend fun uploadPhotos(uris: List<Uri>): List<String>
    suspend fun deletePhotos(urls: List<String>): Result<Unit>
}

class FirebaseStorageRepository(
    private val context: Context? = null,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : StorageRepository {

    override suspend fun uploadPhotos(uris: List<Uri>): List<String> {
        if (uris.isEmpty()) return emptyList()
        val uploadedRefs = mutableListOf<StorageReference>()
        val uploadedUrls = mutableListOf<String>()
        val uploadBatchId = UUID.randomUUID().toString()
        var currentUploadTask: UploadTask? = null

        return try {
            withContext(Dispatchers.IO) {
                for ((index, uri) in uris.withIndex()) {
                    val bytes = context?.let { ctx ->
                        ImageCompressor.compressImage(ctx, uri)
                    } ?: run {
                        context?.contentResolver?.openInputStream(uri)?.use { it.readBytes() }
                    } ?: throw IllegalArgumentException("Could not read image from URI: $uri")

                    val photoRef = storage.reference.child("listings/$uploadBatchId/$index.jpg")
                    uploadedRefs.add(photoRef)

                    val uploadTask = photoRef.putBytes(bytes)
                    currentUploadTask = uploadTask
                    uploadTask.await()

                    val downloadUrl = photoRef.downloadUrl.await().toString()
                    uploadedUrls.add(downloadUrl)
                }
                uploadedUrls
            }
        } catch (e: Exception) {
            currentUploadTask?.cancel()
            withContext(NonCancellable) {
                // Atomic cleanup: Remove all uploaded storage references in this batch
                for (ref in uploadedRefs) {
                    runCatching { ref.delete().await() }
                }
            }
            throw e
        }
    }

    override suspend fun deletePhotos(urls: List<String>): Result<Unit> {
        var firstError: Throwable? = null
        for (url in urls) {
            try {
                storage.getReferenceFromUrl(url).delete().await()
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        return if (firstError != null) Result.failure(firstError) else Result.success(Unit)
    }
}
