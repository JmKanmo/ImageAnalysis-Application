package com.junmo.imagevisionapplication

import android.Manifest
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val CAMERA_PERMISSION_REQUEST = 1000
    private val GALLERY_PERMISSION_REQUEST = 1001

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
                        Log.d("TAG", "카메라클릭작동")
                        //카메라권한
                        checkCameraPermission()
                    }

                    override fun galleryOnClick() {
                        Log.d("TAG", "갤러리클릭작동")
                        //저장장치권한
                        checkGalleryPermission()
                    }
                })
            }.show(supportFragmentManager, "")
            //UploadChooser().show(supportFragmentManager,"")
        }
    }

    private fun checkCameraPermission() {
        PermissionUtility().requestPermission(
            this, CAMERA_PERMISSION_REQUEST, Manifest.permission.CAMERA
        )
    }

    private fun checkGalleryPermission() {
        PermissionUtility().requestPermission(
            this, GALLERY_PERMISSION_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA
        )
    }
}
