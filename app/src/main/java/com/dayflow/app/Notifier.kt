package com.dayflow.app

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager

object Notifier {
    fun show(ctx: Context, e: Entry) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel("alarm_v2", "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, attrs); enableVibration(true); vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
        })
        nm.createNotificationChannel(NotificationChannel("quiet_v2", "Quiet reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, e.id,
            Intent(ctx, MainActivity::class.java).putExtra("id", e.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (e.remindBefore == 0) "Your scheduled activity starts now." else "Starts in ${e.remindBefore} minutes."
        val n = Notification.Builder(ctx, if (e.alarm) "alarm_v2" else "quiet_v2")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(e.title).setContentText(text)
            .setCategory(if (e.alarm) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setContentIntent(open).setAutoCancel(true).build()
        if (e.alarm) n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(e.id, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -1)
        val e = Store(ctx).load().firstOrNull { it.id == id } ?: return
        Notifier.show(ctx, e)
        Scheduler.schedule(ctx, e)
    }
}
