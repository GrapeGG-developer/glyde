package gpr.greentune.unblock.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import gpr.greentune.unblock.R
import gpr.greentune.unblock.core.Engine
import gpr.greentune.unblock.core.EngineStatus

class OrbWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    companion object {
        fun updateAll(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, OrbWidget::class.java))
                if (ids.isNotEmpty()) render(context, manager, ids)
            } catch (_: Exception) {
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val st = Engine.status.value
            val on = st == EngineStatus.On
            val text = context.getString(
                when (st) {
                    EngineStatus.On -> R.string.tile_on
                    EngineStatus.Starting -> R.string.status_connecting
                    else -> R.string.tile_off
                }
            )
            val pi = PendingIntent.getActivity(
                context, 11,
                Intent(context, ToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_orb)
                views.setImageViewResource(R.id.widget_orb, if (on) R.drawable.orb_on else R.drawable.orb_off)
                views.setTextViewText(R.id.widget_label, text)
                views.setOnClickPendingIntent(R.id.widget_root, pi)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
