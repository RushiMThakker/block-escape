package com.rushi.blockescape

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

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
