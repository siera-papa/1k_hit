package rs.plan1k.regulacija

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Standalone widget entry: pick an event whose latest hit awaits its start of work. */
class WidgetGoalActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val foreground = Color.rgb(239,243,248)
    private val secondary = Color.rgb(181,195,206)
    private val accent = Color.rgb(173,137,232)
    private var delayed: Runnable? = null
    private var step = 0
    private var candidates: List<Pair<EventRecord, HitRecord>> = emptyList()
    private var selectedEvent: Long? = null
    private var type = GOALS.first()
    private var delay = ""
    private var score: Double? = null
    private var startedAt = System.currentTimeMillis()
    private var saving = false
    private lateinit var body: LinearLayout
    private lateinit var section: TextView
    private lateinit var counter: TextView
    private lateinit var previous: TextView
    private lateinit var next: TextView
    private lateinit var timeButton: TextView

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun label(value: String, size: Float = 15f, color: Int = foreground) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    private fun add(parent: LinearLayout, child: View, top: Int = 0) {
        (child.parent as? android.view.ViewGroup)?.removeView(child)
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun outline(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(25,39,51)); cornerRadius = dp(10).toFloat()
        setStroke(dp(1), Color.rgb(115,145,158))
    }
    private fun cancelAdvance() { delayed?.let { handler.removeCallbacks(it) }; delayed = null }
    private fun autoAdvance() {
        cancelAdvance()
        if (step == 4) return
        val current = step
        val action = Runnable {
            delayed = null
            if (!isFinishing && !saving && step == current) { step++; render() }
        }
        delayed = action
        handler.postDelayed(action, 400)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0.45f)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.rgb(35,49,62)); cornerRadius = dp(19).toFloat() }
            setPadding(dp(18),dp(14),dp(18),dp(15))
        }
        setContentView(root)
        window.setLayout((resources.displayMetrics.widthPixels * 0.94).toInt(), -2)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = label("Početak rada",14f,secondary)
        counter = label("1/5",13f,secondary).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
        header.addView(title, LinearLayout.LayoutParams(0,dp(31),1f))
        header.addView(counter, LinearLayout.LayoutParams(dp(52),dp(31)))
        add(root,header)
        section = label("",19f).apply { setTypeface(null,Typeface.BOLD) }
        add(root,section,7)
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(67,83,95)) },
            LinearLayout.LayoutParams(-1,dp(1)).apply { topMargin=dp(9);bottomMargin=dp(11) })
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(root,body)
        timeButton=label("").apply {
            minHeight=dp(58);background=outline();setPadding(dp(14),dp(8),dp(12),dp(8))
            setOnClickListener { pickTime() }
        }
        val controls = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        previous=label("Otkaži",15f,secondary).apply { gravity=Gravity.CENTER }
        next=label("Dalje",15f,accent).apply { gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD) }
        controls.addView(previous,LinearLayout.LayoutParams(0,dp(46),1f))
        controls.addView(next,LinearLayout.LayoutParams(0,dp(46),1f))
        add(root,controls,9)
        previous.setOnClickListener { cancelAdvance();if(step==0)finishAndRemoveTask() else {step--;render()} }
        next.setOnClickListener {
            if(saving)return@setOnClickListener
            cancelAdvance()
            if(step==0 && selectedEvent==null)return@setOnClickListener
            if(step==2 && delay.isBlank()){Toast.makeText(this,"Izaberi kašnjenje",Toast.LENGTH_SHORT).show();return@setOnClickListener}
            if(step==3 && score==null){Toast.makeText(this,"Izaberi funkcionalnost",Toast.LENGTH_SHORT).show();return@setOnClickListener}
            if(step<4){step++;render()}else save()
        }
        section.text="Događaj"
        add(body,label("Učitavanje…",15f,secondary))
        next.visibility=View.GONE
        io.execute {
            val data=try {
                Store(applicationContext).use { store ->
                    store.autoClose()
                    store.events().filter { !it.closed }.mapNotNull { event ->
                        val last=store.hits(event.id).lastOrNull()
                        if(last!=null && store.goals(last.id).isEmpty())event to last else null
                    }
                }
            } catch (_:Exception) { emptyList() }
            runOnUiThread {
                if(isFinishing)return@runOnUiThread
                candidates=data
                selectedEvent=data.firstOrNull()?.first?.id
                render()
            }
        }
    }
    private fun radio(options: List<Pair<String,String>>, current: String, onChoice: (String)->Unit): RadioGroup {
        val group=RadioGroup(this).apply { orientation=RadioGroup.VERTICAL }
        options.forEach { (value,title) ->
            val item=RadioButton(this).apply {
                id=View.generateViewId();text=title;textSize=14.5f;setTextColor(this@WidgetGoalActivity.foreground)
                buttonTintList=ColorStateList.valueOf(accent);minHeight=dp(43)
            }
            group.addView(item)
            if(value==current)group.check(item.id)
            item.setOnClickListener { group.check(item.id);onChoice(value);autoAdvance() }
        }
        return group
    }
    private fun timeLabel(): String {
        val chosen=Calendar.getInstance().apply {timeInMillis=startedAt}
        val now=Calendar.getInstance()
        val today=chosen.get(Calendar.YEAR)==now.get(Calendar.YEAR) && chosen.get(Calendar.DAY_OF_YEAR)==now.get(Calendar.DAY_OF_YEAR)
        val date=if(today)"danas" else SimpleDateFormat("dd.MM.yyyy.",Locale("sr")).format(Date(startedAt))
        return "◷  $date  ·  ${SimpleDateFormat("HH:mm",Locale("sr")).format(Date(startedAt))}    ›"
    }
    private fun pickTime() {
        val chosen=Calendar.getInstance().apply { timeInMillis=startedAt }
        DatePickerDialog(this,{_,year,month,day->
            chosen.set(Calendar.YEAR,year);chosen.set(Calendar.MONTH,month);chosen.set(Calendar.DAY_OF_MONTH,day)
            TimePickerDialog(this,{_,hour,minute->
                chosen.set(Calendar.HOUR_OF_DAY,hour);chosen.set(Calendar.MINUTE,minute)
                chosen.set(Calendar.SECOND,0);chosen.set(Calendar.MILLISECOND,0)
                startedAt=chosen.timeInMillis;timeButton.text=timeLabel()
            },chosen.get(Calendar.HOUR_OF_DAY),chosen.get(Calendar.MINUTE),true).show()
        },chosen.get(Calendar.YEAR),chosen.get(Calendar.MONTH),chosen.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun render() {
        cancelAdvance()
        body.removeAllViews()
        counter.text="${step+1}/5"
        previous.text=if(step==0)"Otkaži" else "Nazad"
        next.text=if(step==4)"Sačuvaj" else "Dalje"
        next.visibility=if(step==0 && candidates.isNotEmpty())View.GONE else View.VISIBLE
        section.text=listOf("Događaj","Vrsta","Kašnjenje u odnosu na plan","Procena funkcionalnosti","Vreme početka")[step]
        when(step) {
            0-> if(candidates.isEmpty()){
                add(body,label("Nema događaja koji čekaju početak rada.",15f,secondary))
                next.visibility=View.GONE
            } else add(body,radio(candidates.map { it.first.id.toString() to it.first.description },selectedEvent.toString()) {selectedEvent=it.toLong()})
            1->add(body,radio(GOALS.map {it to it},type){type=it})
            2->add(body,radio(DURATIONS.map {it to it},delay){delay=it})
            3->add(body,radio(listOf("0.5","1.0").map {it to it},score?.toString()?:""){score=it.toDouble()})
            4->{timeButton.text=timeLabel();add(body,timeButton)}
        }
    }
    private fun save() {
        val id=selectedEvent?:return
        val rating=score?:return
        if(delay.isBlank())return
        saving=true;next.isEnabled=false
        val found=candidates.firstOrNull {it.first.id==id}?:return
        val record=GoalRecord(hitId=found.second.id,type=type,duration=delay,score=rating,
            evidence="",startedAt=startedAt)
        io.execute {
            var error:String?=null
            try {Store(applicationContext).use {it.addGoal(record)}}
            catch(e:Exception){error=e.message?:"Greška pri čuvanju"}
            runOnUiThread {
                if(error==null)finishAndRemoveTask()
                else {saving=false;next.isEnabled=true;Toast.makeText(this,error,Toast.LENGTH_LONG).show()}
            }
        }
    }
    override fun onDestroy(){cancelAdvance();handler.removeCallbacksAndMessages(null);io.shutdown();super.onDestroy()}
}
