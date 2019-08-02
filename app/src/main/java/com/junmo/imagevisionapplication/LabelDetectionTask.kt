package com.junmo.imagevisionapplication

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.AsyncTask
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.vision.v1.Vision
import com.google.api.services.vision.v1.VisionRequest
import com.google.api.services.vision.v1.VisionRequestInitializer
import com.google.api.services.vision.v1.model.*
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.lang.StringBuilder
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList

/*
    2019.08.02
    Developer: JM_Kanmo
    기능: image Bitmap을 받은 뒤 , CloudServer에서 보낸 요청을 받아 화면에 표시
 */

class LabelDetectionTask(
    private val packageName: String,
    private val packageManager: PackageManager,
    private val activity: MainActivity
) {
    private val CLOUD_API_KEY = "AIzaSyAPe83VKDQLfw_5kVvTYSvM0j0Z2HX8ff0"
    private val ANDROID_PACKAGE_HEADER = "X-Android-Package"
    private val ANDROID_CERT_HEADER = "X-Android-Cert"
    private val MAX_LABEL_RESULTS = 10
    private var labelDetectionNotifierInterface: LabelDetectionNotifierInterface? = null
    private var requestType: String? = null

    interface LabelDetectionNotifierInterface {
        fun notifyResult(result: String)
    }

    inner class ImageRequestTask constructor(
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
                return findProperResponseType(response)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return "분석실패"
        }

        override fun onPostExecute(result: String?) {
            val activity = weakReference.get()
            if (activity != null && !activity.isFinishing()) {
                result?.let {
                    labelDetectionNotifierInterface?.notifyResult(it)
                }
            }
        }
    }

    private fun findProperResponseType(response: BatchAnnotateImagesResponse): String {
        when (requestType) {
            activity.Label_Detection_Request -> {
                return convertResponseToString(response.responses[0].labelAnnotations)
            }
            activity.Landmark_Detection_Request -> {
                return convertResponseToString(response.responses[0].landmarkAnnotations)
            }
        }
        return "분석실패"
    }

    private fun convertResponseToString(labels: List<EntityAnnotation>): String {
        val message = StringBuilder("분석결과")
        labels.forEach {
            message.append(String.format(Locale.KOREAN, "%.3f: %s", it.score, it.description))
            message.append("\n")
        }
        return message.toString()
    }

    fun requestCloudVisionApi(
        bitmap: Bitmap,
        labelDetectionNotifierInterface: LabelDetectionNotifierInterface,
        requestType: String
    ) {
        this.requestType = requestType
        this.labelDetectionNotifierInterface = labelDetectionNotifierInterface
        val visionTask = ImageRequestTask(prepareImageRequest(bitmap))
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
                        when (requestType) {
                            activity.Label_Detection_Request -> {
                                labelDetection.type = "LABEL_DETECTION"
                            }
                            activity.Landmark_Detection_Request -> {
                                labelDetection.type = "LANDMARK_DETECTION"
                            }
                        }
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
}