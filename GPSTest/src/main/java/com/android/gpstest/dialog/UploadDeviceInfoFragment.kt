package com.android.gpstest.dialog

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.android.gpstest.Application
import com.android.gpstest.BuildConfig
import com.android.gpstest.R
import com.android.gpstest.io.UploadDevicePropertiesWorker
import com.android.gpstest.util.IOUtils
import com.android.gpstest.util.PreferenceUtils
import com.android.gpstest.util.SatelliteUtils
import com.google.android.material.button.MaterialButton

class UploadDeviceInfoFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setRetainInstance(true)
        return inflater.inflate(R.layout.share_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val uploadNoLocationTextView: TextView = view.findViewById(R.id.upload_no_location)
        val uploadDetails: TextView = view.findViewById(R.id.upload_details)
        val upload: MaterialButton = view.findViewById(R.id.upload)

        val location = arguments?.getParcelable<Location>(ShareDialogFragment.KEY_LOCATION)

        if (location == null) {
            // No location
            uploadDetails.visibility = View.GONE
            upload.visibility = View.GONE
            uploadNoLocationTextView.visibility = View.VISIBLE
        } else {
            // We have a location
            uploadDetails.visibility = View.VISIBLE
            upload.visibility = View.VISIBLE
            uploadNoLocationTextView.visibility = View.GONE
        }

        upload.setOnClickListener { v: View? ->
            var versionName = ""
            var versionCode = ""
            try {
                val info: PackageInfo = Application.get().packageManager.getPackageInfo(Application.get().packageName, 0)
                versionName = info.versionName
                versionCode = info.versionCode.toString()
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
            val locationManager = Application.get().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            PreferenceUtils.saveInt(Application.get().getString(R.string.capability_key_nmea), PreferenceUtils.CAPABILITY_SUPPORTED)


            // Inject PSDS capability
            val capabilityInjectPsdsInt = Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_inject_psds), PreferenceUtils.CAPABILITY_UNKNOWN)
            val psdsSuccessBoolean: Boolean
            val psdsSuccessString: String
            if (capabilityInjectPsdsInt == PreferenceUtils.CAPABILITY_UNKNOWN) {
                psdsSuccessBoolean = IOUtils.forcePsdsInjection(locationManager)
                psdsSuccessString = PreferenceUtils.getCapabilityDescription(psdsSuccessBoolean)
            } else {
                psdsSuccessString = PreferenceUtils.getCapabilityDescription(capabilityInjectPsdsInt)
            }

            // Inject time
            val capabilityInjectTimeInt = Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_inject_time), PreferenceUtils.CAPABILITY_UNKNOWN)
            val timeSuccessBoolean: Boolean
            val timeSuccessString: String
            if (capabilityInjectTimeInt == PreferenceUtils.CAPABILITY_UNKNOWN) {
                timeSuccessBoolean = IOUtils.forceTimeInjection(locationManager)
                timeSuccessString = PreferenceUtils.getCapabilityDescription(timeSuccessBoolean)
            } else {
                timeSuccessString = PreferenceUtils.getCapabilityDescription(capabilityInjectTimeInt)
            }

            // Upload device info to database
            val myData = Data.Builder()
                    .putString(UploadDevicePropertiesWorker.MANUFACTURER, Build.MANUFACTURER)
                    .putString(UploadDevicePropertiesWorker.MODEL, Build.MODEL)
                    .putString(UploadDevicePropertiesWorker.ANDROID_VERSION, Build.VERSION.RELEASE)
                    .putString(UploadDevicePropertiesWorker.API_LEVEL, Build.VERSION.SDK_INT.toString())
                    .putString(UploadDevicePropertiesWorker.GNSS_HARDWARE_YEAR, IOUtils.getGnssHardwareYear())
                    .putString(UploadDevicePropertiesWorker.GNSS_HARDWARE_MODEL_NAME, IOUtils.getGnssHardwareModelName())

//                    .putString(UploadDevicePropertiesWorker.DUAL_FREQUENCY)
//                    .putString(UploadDevicePropertiesWorker.SUPPORTED_GNSS)
//                    .putString(UploadDevicePropertiesWorker.SUPPORTED_SBAS)
//                    .putString(UploadDevicePropertiesWorker.RAW_MEASUREMENTS)
//                    .putString(UploadDevicePropertiesWorker.NAVIGATION_MESSAGES)
                    .putString(UploadDevicePropertiesWorker.NMEA, PreferenceUtils.getCapabilityDescription(Application.getPrefs().getInt(Application.get().getString(R.string.capability_key_nmea), PreferenceUtils.CAPABILITY_UNKNOWN)))
                    .putString(UploadDevicePropertiesWorker.INJECT_PSDS, psdsSuccessString)
                    .putString(UploadDevicePropertiesWorker.INJECT_TIME, timeSuccessString)
//                    .putString(UploadDevicePropertiesWorker.ACCUMULATED_DELTA_RANGE)

                    .putString(UploadDevicePropertiesWorker.GNSS_ANTENNA_INFO, PreferenceUtils.getCapabilityDescription(SatelliteUtils.isGnssAntennaInfoSupported(locationManager)))
                    .putString(UploadDevicePropertiesWorker.APP_VERSION_NAME, versionName)
                    .putString(UploadDevicePropertiesWorker.APP_VERSION_CODE, versionCode)
                    .putString(UploadDevicePropertiesWorker.APP_BUILD_FLAVOR, BuildConfig.FLAVOR)

                    .build()
            val workRequest = OneTimeWorkRequest.Builder(
                    UploadDevicePropertiesWorker::class.java)
                    .setInputData(myData)
                    .build()
            WorkManager.getInstance(Application.get()).enqueue(workRequest)
        }
    }
}