package gpr.greentune.unblock.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import gpr.greentune.unblock.R
import gpr.greentune.unblock.ui.MainActivity

object Notifications {
    const val CHANNEL_ENGINE = "engine"
    const val CHANNEL_INFO = "info"
    const val ID_ENGINE = 1
    const val ID_UPDATE = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ENGINE, context.getString(R.string.channel_engine), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, context.getString(R.string.channel_info), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun engine(context: Context, service: Class<out Service>, text: String): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            context, 1, Intent(context, service).setAction(GtVpnService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ENGINE)
            .setSmallIcon(R.drawable.ic_stat_glyde)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColor(0xFF19A7CE.toInt())
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.action_disconnect), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun startForeground(service: Service, notification: Notification, vpn: Boolean) {
        if (Build.VERSION.SDK_INT >= 34) {
            val type = if (vpn) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            service.startForeground(ID_ENGINE, notification, type)
        } else {
            service.startForeground(ID_ENGINE, notification)
        }
    }

    fun update(context: Context, notification: Notification) {
        context.getSystemService(NotificationManager::class.java)?.notify(ID_ENGINE, notification)
    }
}
