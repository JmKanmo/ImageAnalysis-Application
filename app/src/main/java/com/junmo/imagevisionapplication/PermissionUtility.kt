package com.junmo.imagevisionapplication

import android.app.Activity
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import java.security.Permissions

/*
    2019.08.02
    Developer: JM_Kanmo
    기능: 권한요청 유틸리티
 */

class PermissionUtility {
    fun requestPermission(
        activity: Activity, requestCode: Int, vararg permissions: String
    ): Boolean {
        var granted = true
        val permissionNeeded = ArrayList<String>()
        permissions.forEach {
            val permissionCheck = ContextCompat.checkSelfPermission(activity, it)
            val hasPermission = permissionCheck == PackageManager.PERMISSION_GRANTED
            granted = granted and hasPermission
            if (!hasPermission) {
                permissionNeeded.add(it)
            }
        }
        if (granted) {
            return true
        } else {
            ActivityCompat.requestPermissions(
                activity, permissionNeeded.toTypedArray(), requestCode
            )
            return false
        }
    }

    fun permissionGranted(
        requestCode: Int, permissionCode: Int, grantResult: IntArray
    ): Boolean {
        return requestCode == permissionCode && grantResult.size > 0 && grantResult[0] == PackageManager.PERMISSION_GRANTED
    }
}