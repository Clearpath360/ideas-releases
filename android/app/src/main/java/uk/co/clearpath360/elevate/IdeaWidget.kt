package uk.co.clearpath360.elevate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class IdeaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun capture(mode: String, req: Int) = PendingIntent.getActivity(ctx, req,
            Intent(ctx, CaptureActivity::class.java).putExtra(CaptureActivity.EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK), flags)
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java), flags)
        for (id in ids) {
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            v.setOnClickPendingIntent(R.id.w_voice, capture("voice", 1))
            v.setOnClickPendingIntent(R.id.w_type, capture("type", 2))
            v.setOnClickPendingIntent(R.id.w_open, open)
            mgr.updateAppWidget(id, v)
        }
    }
}
