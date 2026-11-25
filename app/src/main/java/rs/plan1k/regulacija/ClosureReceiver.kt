package rs.plan1k.regulacija

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClosureReceiver : BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val token=goAsync()
        Thread {
            try {
                Store(context).use { store ->
                    store.autoClose()
                    val next=store.nextDeadline()
                    if(next!=null) {
                        val manager=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        val pi=PendingIntent.getBroadcast(context,9,Intent(context,ClosureReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.coerceAtLeast(System.currentTimeMillis()+1000),pi)
                    }
                }
            } finally {token.finish()}
        }.start()
    }
}
