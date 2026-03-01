package com.museovirtualnacional.strogoff.data.repository

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.museovirtualnacional.strogoff.R
import android.content.Intent
import android.app.PendingIntent
import com.museovirtualnacional.strogoff.MainActivity

class NotificationHelper(private val context: Context) {
    @SuppressLint("MissingPermission")
    fun sendNotification(title: String, articleUrl: String) {
        val channelId = "news_channel"
        val notificationId = title.hashCode()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "News Updates"
            val descriptionText = "Notifications for interested topics"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("article_url", articleUrl)
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 
                notificationId, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                try {
                    notify(notificationId, builder.build())
                } catch (e: SecurityException) {
                    // Permission not granted
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasPermission(): Boolean {
        // On Android < 13 (TIRAMISU) the POST_NOTIFICATIONS permission is not required
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
