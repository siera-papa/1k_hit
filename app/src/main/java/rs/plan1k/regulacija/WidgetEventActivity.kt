package rs.plan1k.regulacija

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.concurrent.Executors

/** Dialog-styled activity: a home-screen widget never opens MainActivity's tabs or list. */
class WidgetEventActivity : Activity() {
    private val bg=Color.rgb(35,49,62)
    private val fg=Color.rgb(239,243,248)
    private val muted=Color.rgb(181,195,206)
    private val accent=Color.rgb(173,137,232)
    private val handler=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private var pending:Runnable?=null
    private var step=0
    private var domain=DOMAINS.first()
    private var trigger=TRIGGERS.first()
    private lateinit var body:LinearLayout
    private lateinit var title:TextView
    private lateinit var stepTitle:TextView
    private lateinit var counter:TextView
    private lateinit var back:TextView
    private lateinit var next:TextView
    private lateinit var name:EditText

    private fun dp(x:Int)=(x*resources.displayMetrics.density+0.5f).toInt()
    private fun text(s:String,size:Int=16,color:Int=fg)=TextView(this).apply {
        text=s;textSize=size.toFloat();setTextColor(color);gravity=Gravity.CENTER_VERTICAL
    }
    private fun bg(color:Int,radius:Int)=GradientDrawable().apply {
        setColor(color);cornerRadius=dp(radius).toFloat()
    }
    private fun add(p:LinearLayout,v:View,top:Int=0){p.addView(v,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)})}

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0.45f)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val frame=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            background=bg(bg,19)
            setPadding(dp(19),dp(14),dp(19),dp(16))
        }
        setContentView(frame)
        window.setLayout((resources.displayMetrics.widthPixels*0.91).toInt(),-2)
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        title=text("Novi događaj",14,muted)
        counter=text("1/3",13,muted).apply{gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL}
        head.addView(title,LinearLayout.LayoutParams(0,dp(30),1f))
        head.addView(counter,LinearLayout.LayoutParams(dp(52),dp(30)))
        add(frame,head)
        stepTitle=text("",19).apply{setTypeface(null,Typeface.BOLD)}
        add(frame,stepTitle,7)
        val line=View(this).apply{setBackgroundColor(Color.rgb(67,83,95))}
        frame.addView(line,LinearLayout.LayoutParams(-1,dp(1)).apply{topMargin=dp(9);bottomMargin=dp(12)})
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        add(frame,body)
        name=EditText(this).apply{
            hint="Naziv";setHintTextColor(muted);setTextColor(fg);textSize=16f
            setSingleLine(false);minLines=2;maxLines=3
            gravity=Gravity.CENTER_VERTICAL or Gravity.START
            background=GradientDrawable().apply{
                setColor(Color.rgb(25,39,51));cornerRadius=dp(10).toFloat()
                setStroke(dp(1),Color.rgb(115,145,158))
            }
            setPadding(dp(13),dp(10),dp(13),dp(10))
        }
        val controls=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        back=text("Otkaži",15,muted).apply{gravity=Gravity.CENTER}
        next=text("Dalje",15,accent).apply{gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)}
        controls.addView(back,LinearLayout.LayoutParams(0,dp(46),1f))
        controls.addView(next,LinearLayout.LayoutParams(0,dp(46),1f))
        add(frame,controls,12)
        back.setOnClickListener { if(step==0)finishAndRemoveTask() else {step--;showStep()} }
        next.setOnClickListener {
            if(step==0 && name.text.toString().trim().isEmpty()){
                name.error="Obavezno";return@setOnClickListener
            }
            if(step<2){step++;showStep()} else save()
        }
        showStep()
        name.requestFocus()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        handler.postDelayed({
            if(!isFinishing && step==0){name.requestFocus();(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(name,InputMethodManager.SHOW_IMPLICIT)}
        },150)
    }
    private fun cancelAdvance(){pending?.let{handler.removeCallbacks(it)};pending=null}
    private fun showStep(){
        cancelAdvance()
        counter.text="${step+1}/3"
        body.removeAllViews()
        back.text=if(step==0)"Otkaži" else "Nazad"
        next.text=if(step==2)"Sačuvaj" else "Dalje"
        stepTitle.visibility=if(step==0)View.GONE else View.VISIBLE
        stepTitle.text=if(step==1)"Domen" else "Okidač"
        when(step){
            0->{(name.parent as? android.view.ViewGroup)?.removeView(name);add(body,name)}
            1,2->{
                val options=if(step==1)DOMAINS else TRIGGERS
                val group=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL}
                options.forEach { opt ->
                    val radio=RadioButton(this).apply {
                        id=View.generateViewId()
                        text=opt;textSize=15f;setTextColor(fg)
                        buttonTintList=android.content.res.ColorStateList.valueOf(accent)
                        minHeight=dp(43)
                        setOnClickListener {
                            group.check(id)
                            if(step==1)domain=opt else trigger=opt
                            if(step<2){
                                cancelAdvance();val at=step
                                val action=Runnable {pending=null;if(!isFinishing && step==at){step++;showStep()}}
                                pending=action;handler.postDelayed(action,400)
                            }
                        }
                    }
                    group.addView(radio)
                    if (opt == if (step==1) domain else trigger) group.check(radio.id)
                }
                add(body,group)
            }
        }
        // Choosing a single option advances; final step always needs Save.
        next.visibility=if(step==1)View.GONE else View.VISIBLE
        if(step!=0){(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(name.windowToken,0)}
    }
    private fun save(){
        cancelAdvance()
        next.isEnabled=false
        val description=name.text.toString().trim()
        if(description.isEmpty()){step=0;showStep();name.error="Obavezno";next.isEnabled=true;return}
        io.execute {
            var error:String?=null
            try {Store(applicationContext).use { it.createEvent(description,domain,trigger) }}
            catch(e:Exception){error=e.message?:"Greška pri čuvanju"}
            runOnUiThread {
                if(error==null) finishAndRemoveTask() else {Toast.makeText(this,error,Toast.LENGTH_LONG).show();next.isEnabled=true}
            }
        }
    }
    override fun onDestroy(){cancelAdvance();handler.removeCallbacksAndMessages(null);io.shutdown();super.onDestroy()}
}
