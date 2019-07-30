package com.junmo.imagevisionapplication

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupListener()
    }

    private fun setupListener(){
        upload_image.setOnClickListener {
            UploadChooser().apply {
                addNotifier(object:UploadChooser.UploadChooserNotifierInterface{
                    override fun cameraOnClick() {
                        Log.d("TAG","카메라클릭작동")
                    }
                    override fun galleryOnClick() {
                        Log.d("TAG","갤러리클릭작동")
                    }
                })
            }.show(supportFragmentManager,"")
            //UploadChooser().show(supportFragmentManager,"")
        }
    }

}
