package com.konradkluz.dogrecognizer.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.graphics.Bitmap
import android.os.Environment
import android.text.SpannableStringBuilder
import android.util.Log
import com.konradkluz.dogrecognizer.service.ImageClassifier
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraViewModel(application: Application) : AndroidViewModel(application), LifecycleObserver {

    companion object {
        private const val LOG_TAG = "CameraViewModel"
        private const val DIRECTORY_NAME = "DogRecogniser"
    }


    private var imageClassifier: ImageClassifier? = null
    private var pictureSingle: Single<Bitmap>? = null
    private var breedTextSubject: BehaviorSubject<String>? = BehaviorSubject.create()

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun dddd() {
        imageClassifier = ImageClassifier(getApplication<Application>()).apply {
            this.setUseNNAPI(true)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun destroy() {
        imageClassifier?.close()
    }

    fun classifyFrame(picture: Bitmap) {
        imageClassifier?.recognizeImage(picture)?.let {
            it.subscribe{result -> breedTextSubject?.onNext(result[0].title!!)}
        }
    }

    fun takePicture(bitmap: Bitmap): Single<Boolean> {
        pictureSingle = Single.just(bitmap)
        val galleryFolder = createImageGallery()
        var outputFile: FileOutputStream? = null
        return Single.create { emitter ->
            try {
                outputFile = FileOutputStream(createImageFile(galleryFolder))
                emitter.onSuccess(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputFile))
            } catch (e: Exception) {
                emitter.onError(e)
                Log.e(LOG_TAG, "takePicture: error while saving picture", e)
            } finally {
                outputFile?.close()
            }
        }
    }

    fun pictureSingle(): Single<Bitmap>? {
        return pictureSingle
    }

    fun breedTextSubject(): BehaviorSubject<String>?{
        return breedTextSubject
    }

    private fun createImageGallery(): File {
        val storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val galleryFolder = File(storageDirectory, DIRECTORY_NAME)

        if (!galleryFolder.exists()) {
            galleryFolder.mkdirs()
        }
        return galleryFolder
    }

    private fun createImageFile(galleryFile: File?): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "image_" + timeStamp + "_"
        return File.createTempFile(imageFileName, ".jpg", galleryFile)
    }

}
