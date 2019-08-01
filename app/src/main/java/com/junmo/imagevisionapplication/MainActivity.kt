package com.junmo.imagevisionapplication

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.util.Log
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.vision.v1.Vision
import com.google.api.services.vision.v1.VisionRequest
import com.google.api.services.vision.v1.VisionRequestInitializer
import com.google.api.services.vision.v1.model.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.main_analyze_view.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.lang.StringBuilder
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_REQUEST = 1000
    private val GALLERY_PERMISSION_REQUEST = 1001
    private val FILE_NAME = "picture.jpg"
    private val CLOUD_API_KEY = "AIzaSyAPe83VKDQLfw_5kVvTYSvM0j0Z2HX8ff0"
    private val ANDROID_PACKAGE_HEADER = "X-Android-Package"
    private val ANDROID_CERT_HEADER = "X-Android-Cert"
    private val MAX_LABEL_RESULTS = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupListener()
    }

    private fun setupListener() {
        upload_image.setOnClickListener {
            UploadChooser().apply {
                addNotifier(object : UploadChooser.UploadChooserNotifierInterface {
                    override fun cameraOnClick() {
                        //카메라권한
                        checkCameraPermission()
                    }

                    override fun galleryOnClick() {
                        //저장장치권한
                        checkGalleryPermission()
                    }
                })
            }.show(supportFragmentManager, "")
            //UploadChooser().show(supportFragmentManager,"")
        }
    }

    private fun checkCameraPermission() {
        if (PermissionUtility().requestPermission(
                this, CAMERA_PERMISSION_REQUEST, Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            openCamera()
        }
    }

    private fun checkGalleryPermission() {
        if (PermissionUtility().requestPermission(
                this, GALLERY_PERMISSION_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE
            )
        ) {
            openGallery()
        }
    }

    private fun openGallery() {
        val intent = Intent().apply {
            setType("image/*")
            setAction(Intent.ACTION_GET_CONTENT)
        }
        startActivityForResult(Intent.createChooser(intent, "Choose a pic"), GALLERY_PERMISSION_REQUEST)
    }

    private fun openCamera() {
        val photoUri =
            FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", createCameraFile())
        startActivityForResult(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }
                val photoUri =
                    FileProvider.getUriForFile(this, applicationContext.packageName + ".provider", createCameraFile())
                uploadImage(photoUri)
            }
            GALLERY_PERMISSION_REQUEST -> {
                data?.let {
                    uploadImage(it.data)
                }
            }
        }
    }

    private fun uploadImage(imageUri: Uri) {
        var bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        if (bitmap.width > bitmap.height) bitmap = rotateImage(bitmap, -90f)
        uploaded_image.setImageBitmap(bitmap)
        requestCloudVisionApi(bitmap)
    }

    private fun requestCloudVisionApi(bitmap: Bitmap) {
        val visionTask = ImageRequestTask(this, prepareImageRequest(bitmap))
        visionTask.execute()
    }

    private fun prepareImageRequest(bitmap: Bitmap): Vision.Images.Annotate {
        val httpTransPort = AndroidHttp.newCompatibleTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val requestInitializer = object : VisionRequestInitializer(CLOUD_API_KEY) {
            override fun initializeVisionRequest(request: VisionRequest<*>?) {
                super.initializeVisionRequest(request)
                val packageName = packageName
                request?.requestHeaders?.set(ANDROID_PACKAGE_HEADER, packageName)
                val sign = PackageManageUtil().getSignature(packageManager, packageName)
                request?.requestHeaders?.set(ANDROID_CERT_HEADER, sign)
            }
        }
        val builder = Vision.Builder(httpTransPort, jsonFactory, null)
        builder.setVisionRequestInitializer(requestInitializer)
        val vision = builder.build()
        val batchAnnotateImageRequest = BatchAnnotateImagesRequest()
        batchAnnotateImageRequest.requests = object : ArrayList<AnnotateImageRequest>() {
            init {
                val annotateImageRequest = AnnotateImageRequest()
                val base64EncodedImage = Image()
                val byteArrayOutputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
                val imageBytes = byteArrayOutputStream.toByteArray()
                base64EncodedImage.encodeContent(imageBytes)
                annotateImageRequest.image = base64EncodedImage
                annotateImageRequest.features = object : ArrayList<Feature>() {
                    init {
                        val labelDetection = Feature()
                        labelDetection.type = "LABEL_DETECTION"
                        labelDetection.maxResults = MAX_LABEL_RESULTS
                        add(labelDetection)
                    }
                }
                add(annotateImageRequest)
            }
        }

        val annotateRequest = vision.images().annotate(batchAnnotateImageRequest)
        annotateRequest.setDisableGZipContent(true)
        return annotateRequest
    }

    inner class ImageRequestTask constructor(
        activity: MainActivity,
        val request: Vision.Images.Annotate
    ) : AsyncTask<Any, Void, String>() {
        private val weakReference: WeakReference<MainActivity>

        init {
            weakReference = WeakReference(activity)
        }

        override fun doInBackground(vararg params: Any?): String {
            //백그라운드 작업수행
            try {
                val response = request.execute()
                return convertResponseToString(response)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "분석실패"
        }

        override fun onPostExecute(result: String?) {
            val activity = weakReference.get()
            if (activity != null && !activity.isFinishing()) {
                result_of_uploaded_image.text = result
            }
        }
    }

    private fun convertResponseToString(response: BatchAnnotateImagesResponse): String {
        val message = StringBuilder("분석결과")
        val labels = response.responses[0].labelAnnotations

        labels?.let {
            it.forEach {
                message.append(String.format(Locale.KOREAN, "%.3f: %s", it.score, it.description))
                message.append("\n")
            }
            return message.toString()
        }
        return "분석실패"
    }

    private fun rotateImage(bitmap: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    private fun createCameraFile(): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(dir, FILE_NAME)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            GALLERY_PERMISSION_REQUEST -> {
                if (PermissionUtility().permissionGranted(requestCode, GALLERY_PERMISSION_REQUEST, grantResults)) {
                    openGallery()
                }
            }
            CAMERA_PERMISSION_REQUEST -> {
                if (PermissionUtility().permissionGranted(requestCode, CAMERA_PERMISSION_REQUEST, grantResults)) {
                    openCamera()
                }
            }
        }
    }
}
