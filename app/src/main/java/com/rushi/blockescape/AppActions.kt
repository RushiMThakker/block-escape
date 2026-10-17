package com.rushi.blockescape

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.google.android.play.core.review.ReviewManagerFactory

private const val PACKAGE_NAME = "com.rushi.blockescape"
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$PACKAGE_NAME"

fun shareApp(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Block Escape")
        putExtra(
            Intent.EXTRA_TEXT,
            "Try Block Escape - a sliding-block puzzle game. Slide the blocks to clear the way for the red block!\n$PLAY_STORE_URL"
        )
    }
    context.startActivity(Intent.createChooser(send, "Share Block Escape").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun rateApp(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE_NAME"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Below this, a completed review flow is assumed to have shown no dialog. Google's In-App
 * Review API gives no signal for that case (its quota can silently suppress the dialog, and
 * it also completes instantly outside a Play install), so a user who tapped "Rate" and saw
 * nothing is sent to the Play Store page instead.
 */
private const val MIN_DIALOG_MILLIS = 600L

/**
 * Shows Google's in-app rating dialog. With [fallbackToStore] (the explicit "Rate" button)
 * the Play Store page is opened when the dialog can't be shown; without it (the one-time
 * automatic prompt) a suppressed dialog is simply skipped.
 */
fun requestInAppReview(activity: Activity, fallbackToStore: Boolean) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { request ->
        if (!request.isSuccessful) {
            if (fallbackToStore) rateApp(activity)
            return@addOnCompleteListener
        }
        val startedAt = SystemClock.elapsedRealtime()
        manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
            val tooFast = SystemClock.elapsedRealtime() - startedAt < MIN_DIALOG_MILLIS
            if (fallbackToStore && tooFast) rateApp(activity)
        }
    }
}
