package com.rushi.blockescape.ads

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

/**
 * One [AdsController] per process, so a rotation (which recreates the Activity) does not start the
 * consent flow or the ad SDK a second time. UMP needs an Activity to show a form: the launch
 * Activity registers itself here and clears the reference when destroyed.
 */
object AdsRuntime {
    private var activityRef: WeakReference<Activity>? = null
    private var forceEea = false
    private var controllerOrNull: AdsController? = null

    /** The owner's phones: they get test ads and the UMP debug settings (see MainActivity). */
    fun controller(appContext: Context, testDeviceIds: List<String>): AdsController =
        controllerOrNull ?: AdsController(
            AdMobConsentAdapter(
                appContext = appContext.applicationContext,
                activity = { activityRef?.get() },
                forceEeaGeography = { forceEea },
                testDeviceIds = testDeviceIds,
            ),
        ).also { controllerOrNull = it }

    fun attach(activity: Activity, forceEeaForDebug: Boolean) {
        activityRef = WeakReference(activity)
        // Only ever true for debuggable builds (the caller checks); release always uses real geography.
        forceEea = forceEeaForDebug
    }

    fun detach(activity: Activity) {
        if (activityRef?.get() === activity) activityRef = null
    }
}
