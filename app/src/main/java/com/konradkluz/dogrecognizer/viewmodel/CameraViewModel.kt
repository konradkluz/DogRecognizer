package com.konradkluz.dogrecognizer.viewmodel

import android.arch.lifecycle.ViewModel
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraViewModel : ViewModel() {

    companion object {
        private const val LOG_TAG = "CameraViewModel"
        private const val DIRECTORY_NAME = "DogRecogniser"
    }

    private var galleryFolder: File? = null

    fun takePicture(bitmap: Bitmap) {
        createImageGallery()
        var outputFile: FileOutputStream? = null
        try {
            outputFile = FileOutputStream(createImageFile(galleryFolder))
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputFile)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "takePicture: error while saving picture", e)
        } finally {
            outputFile?.close()
        }
    }


    private fun createImageGallery() {
        val storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        galleryFolder = File(storageDirectory, DIRECTORY_NAME)
        var created = false

        if (!galleryFolder?.exists()!!) {
            created = galleryFolder?.mkdirs()!!
        }
        if (created) {
            println(created)
        }
    }

    private fun createImageFile(galleryFile: File?): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "image_" + timeStamp + "_"
        return File.createTempFile(imageFileName, ".jpg", galleryFile)
    }

}
