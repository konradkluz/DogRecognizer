package com.konradkluz.dogrecognizer.service

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import io.reactivex.Single
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class ImageClassifier constructor(application: Application) {

    companion object {
        private const val LOG_TAG = "ImageClassifier"
        private const val DIM_BATCH_SIZE = 1
        private const val DIM_PIXEL_SIZE = 3
    }

    private val imageSizeX: Int = 224

    private val imageSizeY: Int = 224

    private val intValues = IntArray(imageSizeX * imageSizeY)

    private var tflite: Interpreter? = null

    private val labelList: List<String>

    private var imgData: ByteBuffer

    private var labelProbArray: Array<ByteArray>

    private val modelPath: String = "breed_class_1_224_model.tflite"

    private val labelPath: String = "breed_class_1_224_labels.txt"

    private val numBytesPerChannel: Int = 1


    private val numLabels: Int
        get() = labelList.size

    init {
        tflite = Interpreter(loadModelFile(application))
        labelList = loadLabelList(application)
        imgData = ByteBuffer.allocateDirect(
                DIM_BATCH_SIZE
                        * imageSizeX
                        * imageSizeY
                        * DIM_PIXEL_SIZE
                        * numBytesPerChannel)
        imgData!!.order(ByteOrder.nativeOrder())
        labelProbArray = Array(1){ByteArray(numLabels)}
        Log.d(LOG_TAG, "Created a Tensorflow Lite Image Classifier.")
    }

    fun recognizeImage(bitmap: Bitmap): Single<List<Result>> {
        return Single.just(bitmap).flatMap {
            convertBitmapToByteBuffer(Bitmap.createScaledBitmap(it, imageSizeX, imageSizeY, false))
            tflite!!.run(imgData, labelProbArray)
            val pq = PriorityQueue<Result>(3,
                    Comparator<Result> { lhs, rhs ->
                        java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
                    })
            for (i in labelList.indices) {
                pq.add(Result("" + i, if (labelList.size > i) labelList[i] else "unknown", labelProbArray[0][i].toFloat(), null))
            }
            val recognitions = ArrayList<Result>()
            val recognitionsSize = Math.min(pq.size, 3)
            for (i in 0 until recognitionsSize) recognitions.add(pq.poll())
            return@flatMap Single.just(recognitions)
        }
    }

    fun setUseNNAPI(nnapi: Boolean?) {
        if (tflite != null)
            tflite!!.setUseNNAPI(nnapi!!)
    }

    fun close() {
        tflite!!.close()
        tflite = null
    }

    @Throws(IOException::class)
    private fun loadLabelList(application: Application): List<String> {
        val labelList = ArrayList<String>()
        val reader = BufferedReader(InputStreamReader(application.assets.open(labelPath)))
        reader.lines().forEach { labelList.add(it) }
        reader.close()
        return labelList
    }

    @Throws(IOException::class)
    private fun loadModelFile(application: Application): MappedByteBuffer {
        val fileDescriptor = application.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        if (imgData == null) {
            return
        }
        imgData!!.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until imageSizeX) {
            for (j in 0 until imageSizeY) {
                val value = intValues[pixel++]
                addPixelValue(value)
            }
        }
    }

    private fun addPixelValue(pixelValue: Int) {
        imgData?.put((pixelValue shr 16 and 0xFF).toByte())
        imgData?.put((pixelValue shr 8 and 0xFF).toByte())
        imgData?.put((pixelValue and 0xFF).toByte())
    }
}
