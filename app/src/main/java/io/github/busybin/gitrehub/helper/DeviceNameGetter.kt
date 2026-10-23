package io.github.busybin.gitrehub.helper

import android.os.Build
import io.github.busybin.gitrehub.App
import com.jaredrummler.android.device.DeviceDatabase
import com.jaredrummler.android.device.DeviceName.DeviceInfo

/**
 * Created by Front Desk on 8/7/2017.
 */
class DeviceNameGetter private constructor() {
    private var deviceInfo: DeviceInfo? = null
    val deviceName: String
        get() = this.deviceInfo?.marketName ?: blockingDeviceName()

    fun loadDevice() {
        queryToDevice()
    }

    private fun queryToDevice() {
        val codename = Build.DEVICE
        val model = Build.MODEL
        val db = DeviceDatabase(App.getInstance())
        deviceInfo = db.queryToDevice(codename, model)
    }

    private fun blockingDeviceName(): String {
        queryToDevice()
        return deviceInfo?.marketName ?: Build.MODEL
    }

    companion object {
        @JvmField
        val instance = DeviceNameGetter()
    }
}