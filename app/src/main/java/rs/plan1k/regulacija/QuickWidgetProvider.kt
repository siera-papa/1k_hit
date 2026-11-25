package rs.plan1k.regulacija

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class QuickWidgetProvider:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{WidgetUi.update(context,manager,it,"event","+ Događaj",false)}}
}
class WaveWidgetProvider:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{WidgetUi.update(context,manager,it,"hit","+ Udar",true)}}
}
class GoalWidgetProvider:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){ids.forEach{WidgetUi.update(context,manager,it,"goal","+ Početak rada",false)}}
}
private object WidgetUi {
    fun update(context:Context,manager:AppWidgetManager,id:Int,mode:String,label:String,second:Boolean){
        val intent=Intent(context,when(mode){"event"->WidgetEventActivity::class.java;"goal"->WidgetGoalActivity::class.java;else->WidgetHitActivity::class.java}).apply{
            action="rs.plan1k.regulacija.ADD_${mode.uppercase()}"
            data=Uri.parse("hit://widget/v30/$mode/$id")
            putExtra("mode",mode)
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        val pending=PendingIntent.getActivity(context,id*3+when(mode){"event"->0;"hit"->1;else->2},intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val views=RemoteViews(context.packageName,when(mode){"hit"->R.layout.quick_wave_widget;"goal"->R.layout.quick_goal_widget;else->R.layout.quick_widget})
        views.setTextViewText(R.id.widget_action,label)
        views.setOnClickPendingIntent(R.id.widget_action,pending)
        manager.updateAppWidget(id,views)
    }
}
