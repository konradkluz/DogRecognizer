package com.konradkluz.dogrecognizer.ui

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import com.konradkluz.dogrecognizer.R
import com.konradkluz.dogrecognizer.viewmodel.CameraViewModel
import java.util.*


class CameraFragment : Fragment() {

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1
        private const val LOG_TAG = "CAMERA FRAGMENT"
    }

    // region fields

    private lateinit var textureView: TextureView
    private lateinit var recogniseButton: ImageButton

    private var cameraId: String = ""
    private var imageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var mBackgroundThread: HandlerThread? = null

    private lateinit var imageDimension: Size
    private lateinit var captureRequest: CaptureRequest
    private lateinit var mBackgroundHandler: Handler
    private lateinit var cameraViewModel: CameraViewModel

    // endregion

    // region overrides

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraViewModel = ViewModelProviders.of(this).get(CameraViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture)
        textureView.let {
            it.surfaceTextureListener = textureListener
        }
        recogniseButton = view.findViewById(R.id.recognise_button)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = textureListener
        }
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> if (grantResults[0] == PackageManager.PERMISSION_DENIED) activity?.finish()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        val matrix = Matrix()
        recogniseButton.scaleType = ImageView.ScaleType.MATRIX
        when (newConfig?.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                Log.d(LOG_TAG, "Orientation landscape")
                matrix.postRotate(90f)
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                Log.d(LOG_TAG, "Orientation portrait")
                matrix.postRotate(-90f)
            }
        }
        recogniseButton.imageMatrix = matrix
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
        if (ActivityCompat.checkSelfPermission(context!!, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), PERMISSIONS_REQUEST_CODE)
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
        imageReader?.let {
            it.close()
            imageReader = null
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

    private val captureCallbackListener = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession?, request: CaptureRequest?, result: TotalCaptureResult?) {
            super.onCaptureCompleted(session, request, result)
            createCameraPreview()
        }
    }

    // endregion
}
