package com.konradkluz.dogrecognizer.ui

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.SensorManager
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.util.Size
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.navigation.fragment.NavHostFragment
import com.konradkluz.dogrecognizer.R
import com.konradkluz.dogrecognizer.viewmodel.CameraViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*


class CameraFragment : Fragment() {

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1
        private const val LOG_TAG = "CameraFragment"
    }

    // region fields

    private lateinit var textureView: TextureView
    private lateinit var takePictureButton: ImageButton
    private lateinit var progressBar: ProgressBar

    private var cameraId: String = ""
    private var cameraDevice: CameraDevice? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var mBackgroundThread: HandlerThread? = null

    private lateinit var imageDimension: Size
    private lateinit var mBackgroundHandler: Handler
    private lateinit var cameraViewModel: CameraViewModel

    private lateinit var orientationEventListener: OrientationEventListener

    // endregion

    // region overrides

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraViewModel = ViewModelProviders.of(activity!!).get(CameraViewModel::class.java)

        orientationEventListener =
                MyOrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) { rotation: Float -> rotateButton(rotation) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture)
        textureView.let {
            it.surfaceTextureListener = textureListener
        }

        progressBar = view.findViewById(R.id.progressBar)

        takePictureButton = view.findViewById(R.id.take_picture_button)
        takePictureButton.setOnClickListener {
            takePicture()
        }
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener.enable()
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        orientationEventListener.disable()
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                if (grantResults.any(predicate =
                        { permission -> permission == PackageManager.PERMISSION_DENIED })) activity?.finish()
            }
        }
    }

    // endregion

    // region private methods

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("Camera Background")
        mBackgroundThread?.let {
            it.start()
            mBackgroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.let {
            it.quitSafely()
            it.join()
            mBackgroundThread = null
        }
    }

    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(context!!, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context!!, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context!!, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_CODE)
            return
        }

        val cameraManager: CameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        streamConfigurationMap?.let {
            imageDimension = streamConfigurationMap.getOutputSizes(SurfaceTexture::class.java)[0]
            cameraManager.openCamera(cameraId, stateCallback, null)
        }
    }

    private fun closeCamera() {
        cameraDevice?.let {
            it.close()
            cameraDevice = null
        }
    }

    private fun createCameraPreview() {
        val texture = textureView.surfaceTexture
        texture?.let {
            texture.setDefaultBufferSize(imageDimension.width, imageDimension.height)
            val surface = Surface(texture)
            captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)
            cameraDevice?.createCaptureSession(Arrays.asList(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession?) {
                    cameraDevice?.let {
                        cameraCaptureSession = session
                        updatePreview()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession?) {
                }
            }, null)
        }
    }

    private fun updatePreview() {
        cameraDevice?.let {
            captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder?.build(), null, mBackgroundHandler)
        }
    }

    private fun takePicture() {
        progressBar.visibility = VISIBLE
        lock()
        cameraViewModel.takePicture(textureView.bitmap).let {
            it.observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe { saved ->
                        progressBar.visibility = GONE
                        if (saved) {
                            NavHostFragment
                                    .findNavController(this)
                                    .navigate(R.id.action_cameraFragment_to_previewFragment)
                        }
                    }
        }
        unlock()
    }

    private fun lock() {
        try {
            cameraCaptureSession?.capture(captureRequestBuilder?.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun unlock() {
        try {
            cameraCaptureSession?.setRepeatingRequest(captureRequestBuilder?.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun rotateButton(deg: Float) {
        takePictureButton.animate().rotation(deg).interpolator = AccelerateDecelerateInterpolator()
    }

// endregion

// region anonymous classes

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return false
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera()
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice?) {
            cameraDevice = camera
            createCameraPreview()
        }

        override fun onDisconnected(camera: CameraDevice?) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice?, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
        }
    }

// endregion

// inner class

    class MyOrientationEventListener(context: Context?, rate: Int,
                                     val action: (rotation: Float) -> Unit) : OrientationEventListener(context, rate) {

        private var rotation = -1

        override fun onOrientationChanged(orientation: Int) {
            if (rotation != 1 && (orientation >= 315 || orientation <= 45)) {
                action(0f)
                rotation = 1
            } else if (rotation != 2 && orientation in 46..134) {
                action(-90f)
                rotation = 2
            } else if (rotation != 3 && orientation in 225..314) {
                action(90f)
                rotation = 3
            } else if (rotation != 4 && orientation in 135..224) {
                action(180f)
                rotation = 4
            }
        }
    }

// endregion
}
