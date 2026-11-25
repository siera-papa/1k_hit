package rs.plan1k.regulacija

import android.app.Activity
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
import java.util.concurrent.Executors

/** Independent widget dialog. Step 1 picks an eligible event, steps 2–5 record only the hit. */
class WidgetHitActivity : Activity() {
    private val main=Handler(Looper.getMainLooper())
    private val io=Executors.newSingleThreadExecutor()
    private val fg=Color.rgb(239,243,248)
    private val muted=Color.rgb(181,195,206)
    private val accent=Color.rgb(173,137,232)
    private var advance:Runnable?=null
    private var step=0
    private var candidates:List<EventRecord> = emptyList()
    private var eventId:Long?=null
    private val emotions=linkedSetOf<String>()
    private val strategies=linkedSetOf<String>()
    private var intensity=4
    private var saving=false
    private lateinit var root:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var heading:TextView
    private lateinit var section:TextView
    private lateinit var counter:TextView
    private lateinit var previous:TextView
    private lateinit var next:TextView
    private lateinit var note:EditText

    private fun dp(n:Int)=(n*resources.displayMetrics.density+0.5f).toInt()
    private fun tv(value:String,size:Float=15f,color:Int=fg)=TextView(this).apply {
        text=value; textSize=size; setTextColor(color); gravity=Gravity.CENTER_VERTICAL
    }
    private fun add(parent:LinearLayout,child:View,top:Int=0){
        (child.parent as? android.view.ViewGroup)?.removeView(child)
        parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)})
    }
    private fun stopAdvance(){advance?.let{main.removeCallbacks(it)};advance=null}
    private fun delayAdvance(){
        stopAdvance()
        if(step>=4)return
        val was=step
        val task=Runnable {advance=null;if(!isFinishing && step==was && !saving){step++;render()}}
        advance=task
        main.postDelayed(task,400)
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0.45f)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            background=GradientDrawable().apply{setColor(Color.rgb(35,49,62));cornerRadius=dp(19).toFloat()}
            setPadding(dp(18),dp(14),dp(18),dp(15))
        }
        setContentView(root)
        window.setLayout((resources.displayMetrics.widthPixels*0.94).toInt(),-2)
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        heading=tv("Udar",14f,muted)
        counter=tv("1/5",13f,muted).apply{gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL}
        bar.addView(heading,LinearLayout.LayoutParams(0,dp(31),1f))
        bar.addView(counter,LinearLayout.LayoutParams(dp(50),dp(31)))
        add(root,bar)
        section=tv("",19f).apply{setTypeface(null,Typeface.BOLD)}
        add(root,section,7)
        val line=View(this).apply{setBackgroundColor(Color.rgb(67,83,95))}
        root.addView(line,LinearLayout.LayoutParams(-1,dp(1)).apply{topMargin=dp(9);bottomMargin=dp(11)})
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        add(root,body)
        val actions=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        previous=tv("Otkaži",15f,muted).apply{gravity=Gravity.CENTER}
        next=tv("Dalje",15f,accent).apply{gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)}
        actions.addView(previous,LinearLayout.LayoutParams(0,dp(46),1f))
        actions.addView(next,LinearLayout.LayoutParams(0,dp(46),1f))
        add(root,actions,9)
        previous.setOnClickListener {if(step==0)finishAndRemoveTask() else {step--;render()}}
        next.setOnClickListener {if(saving)return@setOnClickListener;if(step<4){step++;render()}else save()}
        note=EditText(this).apply {
            hint="Kako si primenio strategije?";setHintTextColor(muted);setTextColor(fg);textSize=15f
            minLines=2;maxLines=2;gravity=Gravity.CENTER_VERTICAL or Gravity.START
            background=GradientDrawable().apply {
                setColor(Color.rgb(25,39,51));cornerRadius=dp(10).toFloat();setStroke(dp(1),Color.rgb(115,145,158))
            }
            setPadding(dp(12),dp(8),dp(12),dp(8))
        }
        section.text="Događaj"
        body.addView(tv("Učitavanje…",15f,muted))
        next.visibility=View.GONE
        io.execute {
            val found=try {
                Store(applicationContext).use { store ->
                    store.autoClose()
                    store.events().filter { e ->
                        !e.closed && store.hits(e.id).lastOrNull()?.let { last -> store.goals(last.id).isNotEmpty() } != false
                    }
                }
            } catch (_:Exception) {emptyList()}
            runOnUiThread {
                if(isFinishing)return@runOnUiThread
                candidates=found
                eventId=found.firstOrNull()?.id
                render()
            }
        }
    }
    private fun radio(items:List<Pair<String,String>>,chosen:String,onSelect:(String)->Unit):RadioGroup {
        val group=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL}
        items.forEach {(key,label)->
            val option=RadioButton(this).apply {
                id=View.generateViewId();text=label;textSize=14.5f;setTextColor(fg)
                buttonTintList=ColorStateList.valueOf(accent)
                minHeight=dp(43)
            }
            group.addView(option)
            if(chosen==key)group.check(option.id)
            option.setOnClickListener {group.check(option.id);onSelect(key);delayAdvance()}
        }
        return group
    }
    private fun checkGrid(items:List<String>,chosen:MutableSet<String>,defs:Map<String,String> = emptyMap()):View {
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.TOP}
        val left=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val right=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        row.addView(left,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(right,LinearLayout.LayoutParams(0,-2,1f))
        items.forEachIndexed {i,item->
            val check=CheckBox(this).apply {
                text=item;textSize=12.2f;setTextColor(fg);buttonTintList=ColorStateList.valueOf(accent)
                minHeight=dp(44);isChecked=item in chosen
                setOnCheckedChangeListener {_,yes->if(yes)chosen.add(item)else chosen.remove(item)}
                defs[item]?.let{definition->setOnLongClickListener {Toast.makeText(this@WidgetHitActivity,definition,Toast.LENGTH_LONG).show();true}}
            }
            (if(i<(items.size+1)/2)left else right).addView(check)
        }
        return row
    }
    private fun render(){
        stopAdvance()
        body.removeAllViews()
        counter.text="${step+1}/5"
        previous.text=if(step==0)"Otkaži" else "Nazad"
        next.text=if(step==4)"Sačuvaj" else "Dalje"
        // Only the event's single-choice step advances on tap. Checkbox steps need Dalje.
        next.visibility=if(step==0 && candidates.isNotEmpty())View.GONE else View.VISIBLE
        section.text=listOf("Događaj","Emocije","Intenzitet","Adaptivne strategije","Maladaptivne strategije")[step]
        when(step){
            0->{
                if(candidates.isEmpty()){
                    add(body,tv("Nema događaja za novi udar.",15f,muted))
                    next.visibility=View.GONE
                }else{
                    val choices=candidates.map {it.id.toString() to it.description}
                    add(body,radio(choices,eventId.toString()){eventId=it.toLong()})
                }
            }
            1->add(body,checkGrid(EMOTIONS,emotions))
            2->{
                val label=tv("$intensity / 7",25f,accent).apply{gravity=Gravity.CENTER}
                add(body,label,24)
                val seek=SeekBar(this).apply{max=6;progress=intensity-1}
                seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(v:SeekBar?,p:Int,fromUser:Boolean){intensity=p+1;label.text="$intensity / 7"}
                    override fun onStartTrackingTouch(v:SeekBar?){}
                    override fun onStopTrackingTouch(v:SeekBar?){}
                })
                add(body,seek,18)
            }
            3->add(body,checkGrid(ADAPTIVE_STRATEGIES.map {it.name},strategies,ADAPTIVE_STRATEGIES.associate{it.name to it.definition}))
            4->{
                add(body,checkGrid(MALADAPTIVE_STRATEGIES.map {it.name},strategies,MALADAPTIVE_STRATEGIES.associate{it.name to it.definition}))
                add(body,note,9)
            }
        }
    }
    private fun save(){
        val chosen=eventId ?: return
        saving=true
        next.isEnabled=false
        val record=HitRecord(eventId=chosen,emotions=emotions.toList(),intensity=intensity,
            strategies=strategies.toList(),note=note.text.toString())
        io.execute {
            var problem:String?=null
            try {Store(applicationContext).use{it.addHit(record)}}
            catch(e:Exception){problem=e.message?:"Greška pri čuvanju"}
            runOnUiThread {
                if(problem==null)finishAndRemoveTask()
                else {saving=false;next.isEnabled=true;Toast.makeText(this,problem,Toast.LENGTH_LONG).show()}
            }
        }
    }
    override fun onDestroy(){stopAdvance();main.removeCallbacksAndMessages(null);io.shutdown();super.onDestroy()}
}
