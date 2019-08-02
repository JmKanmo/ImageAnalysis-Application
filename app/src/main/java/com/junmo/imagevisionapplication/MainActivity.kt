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
import android.widget.ImageView
import android.widget.TextView
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.vision.v1.Vision
import com.google.api.services.vision.v1.VisionRequest
import com.google.api.services.vision.v1.VisionRequestInitializer
import com.google.api.services.vision.v1.model.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.detection_chooser.*
import kotlinx.android.synthetic.main.main_analyze_view.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.lang.StringBuilder
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList

/*
    2019.08.02
    Developer: JM_Kanmo
    기능: MainActiviry (여러권한요청 및 메소드실행)
 */

class MainActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_REQUEST = 1000
    private val GALLERY_PERMISSION_REQUEST = 1001
    private val FILE_NAME = "picture.jpg"
    private var labelDetectionTask: LabelDetectionTask? = null
    val Label_Detection_Request = "Label_Detection_Request"
    val Landmark_Detection_Request = "Landmark_Detection_Request"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        labelDetectionTask = LabelDetectionTask(
            packageName = packageName,
            packageManager = packageManager,
            activity = this
        )
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
                uploadImage(photoUri, 0)
            }
            GALLERY_PERMISSION_REQUEST -> {
                data?.let {
                    uploadImage(it.data, 1)
                }
            }
        }
    }

    private fun uploadImage(imageUri: Uri, flag: Int) {
        var bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        if (flag == 0) bitmap = rotateImage(bitmap, -90f)
        DetectionChooser().apply {
            addDetectionChooserNotifierInterface(object : DetectionChooser.DetectionChooserNotifierInterface {
                override fun detectLabel() {
                    findViewById<ImageView>(R.id.uploaded_image).setImageBitmap(bitmap)
                    requestCloudVisionApi(bitmap, Label_Detection_Request)
                    findViewById<TextView>(R.id.result_of_uploaded_image).text = "이미지분석중"
                }

                override fun detectLandMark() {
                    findViewById<ImageView>(R.id.uploaded_image).setImageBitmap(bitmap)
                    requestCloudVisionApi(bitmap, Landmark_Detection_Request)
                    findViewById<TextView>(R.id.result_of_uploaded_image).text = "이미지분석중"
                }
            })
        }.show(supportFragmentManager, "")
    }

    private fun requestCloudVisionApi(bitmap: Bitmap, requestType: String) {
        labelDetectionTask?.requestCloudVisionApi(bitmap, object : LabelDetectionTask.LabelDetectionNotifierInterface {
            override fun notifyResult(result: String) {
                result_of_uploaded_image.text = result
            }
        }, requestType)
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
