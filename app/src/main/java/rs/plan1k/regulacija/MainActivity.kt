package rs.plan1k.regulacija

import android.app.Activity
import android.app.Dialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.text.InputType
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private val BG = Color.rgb(15,23,32)
private val SURFACE = Color.rgb(35,49,62)
private val TEXT = Color.rgb(239,243,248)
private val MUTED = Color.rgb(181,195,206)
private val ACCENT = Color.rgb(173,137,232)
private val ERROR = Color.rgb(255,143,150)
private const val REQUEST_IMPORT = 11
private const val REQUEST_EXPORT = 12

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private var activeDialog: Dialog? = null
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private var page = "list"
    private var eventId = 0L
    private var hitId = 0L
    private var goalId = 0L
    private var tab = 0
    private var search = ""
    private var openedFromWidget = false
    private var expandedEventId: Long? = null
    private var editingEventId: Long? = null
    private var creatingEvent = false
    private var pickEventForHit = false
    private var refreshHome: (() -> Unit)? = null
    private var reloadHome: (() -> Unit)? = null
    private val dateFormat = SimpleDateFormat("dd.MM. HH:mm", Locale("sr"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        DailyBackup.schedule(this)
        io.execute { try { DailyBackup.backupIfNeeded(applicationContext) } catch (_: Exception) {} }
        window.statusBarColor=BG; window.navigationBarColor=BG
        val mode=intent.getStringExtra("mode")
        openedFromWidget = mode != null
        page="list"
        render()
        if(mode=="event") window.decorView.post { showCreateEventModal() }
        if(mode=="hit") window.decorView.post { pickEventForHit=true; render() }
    }
    override fun onResume(){ super.onResume(); io.execute { try { store.autoClose(); scheduleClose(); DailyBackup.backupIfNeeded(applicationContext) } catch (_:Exception){} } }
    override fun onDestroy(){ io.shutdown(); store.close(); super.onDestroy() }
    private fun <T> work(task:()->T, done:(T)->Unit) {
        io.execute { try { val x=task(); scheduleClose(); runOnUiThread { if(!isFinishing) done(x) } }
            catch(e:Exception) { runOnUiThread { if(!isFinishing) Toast.makeText(this,e.message ?: "Greška",Toast.LENGTH_LONG).show() } } }
    }
    private fun scheduleClose() {
        val alarm=getSystemService(ALARM_SERVICE) as AlarmManager
        val intent=Intent(this,ClosureReceiver::class.java)
        val pending=PendingIntent.getBroadcast(this,9,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val next=store.nextDeadline()
        if(next==null) alarm.cancel(pending) else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.coerceAtLeast(System.currentTimeMillis()+1000),pending)
    }
    private fun dp(v:Int)= (v*resources.displayMetrics.density+0.5f).toInt()
    private fun rounded(color:Int, radius:Int=15):GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun outlinedInputBackground(radius:Int=10):GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(25, 39, 51))
        cornerRadius=dp(radius).toFloat()
        setStroke(dp(1), Color.rgb(115, 145, 158))
    }
    private fun text(value:String,size:Int=16,bold:Boolean=false,color:Int=TEXT):TextView = TextView(this).apply {
        this.text=value; textSize=size.toFloat(); setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD
        gravity=Gravity.CENTER_VERTICAL
    }
    private fun pad(view:View,left:Int=0,top:Int=0,right:Int=0,bottom:Int=0):View = view.apply { setPadding(dp(left),dp(top),dp(right),dp(bottom)) }
    private fun column():LinearLayout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row():LinearLayout=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun add(parent:LinearLayout,child:View,space:Int=0){parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(space)})}
    private fun label(parent:LinearLayout,s:String,space:Int=18){ add(parent,text(s,14,false,MUTED),space) }
    private fun input(value:String="",hint:String="",lines:Int=1):EditText=EditText(this).apply {
        setText(value); setHint(hint); setHintTextColor(MUTED); setTextColor(TEXT); textSize=16f
        background=outlinedInputBackground(10); setPadding(dp(13),dp(10),dp(13),dp(10)); minHeight=dp(48)
        if(lines>1){ minLines=lines; maxLines=6; gravity=Gravity.START or Gravity.CENTER_VERTICAL; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        else { gravity=Gravity.START or Gravity.CENTER_VERTICAL; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
    }
    private fun button(label:String,accent:Boolean=true,action:()->Unit):TextView=text(label,16,true,if(accent)BG else ACCENT).apply {
        gravity=Gravity.CENTER; background=rounded(if(accent)ACCENT else SURFACE,20); minHeight=dp(48)
        setOnClickListener { action() }
    }
    private fun optionRow(options:List<String>,selected:String,onChange:(String)->Unit):Spinner = Spinner(this).apply {
        adapter=object: ArrayAdapter<String>(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options) {
            override fun getView(pos:Int,conv:View?,parent:ViewGroup):View = text(getItem(pos) ?: "",16).apply {setPadding(dp(10),dp(12),dp(10),dp(12));background=rounded(SURFACE,8)}
            override fun getDropDownView(pos:Int,conv:View?,parent:ViewGroup):View=text(getItem(pos) ?: "",16).apply {setPadding(dp(12),dp(13),dp(12),dp(13));setBackgroundColor(SURFACE)}
        }
        setSelection(options.indexOf(selected).coerceAtLeast(0))
        onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){onChange(options[position])}
        }
    }
    private fun checkChoice(parent:LinearLayout,name:String,selected:MutableSet<String>,definition:String?=null):CheckBox=CheckBox(this).apply {
        text=name; textSize=15f; setTextColor(TEXT); buttonTintList=android.content.res.ColorStateList.valueOf(ACCENT)
        isChecked=name in selected; setOnCheckedChangeListener {_,checked ->if(checked) selected.add(name) else selected.remove(name)}
        if(definition!=null) setOnLongClickListener {Toast.makeText(this@MainActivity,definition,Toast.LENGTH_LONG).show();true}
        add(parent,this)
    }
    private fun header(title:String,back:Boolean=true):LinearLayout=row().apply {
        if(back){ val b=text("‹",32,true,ACCENT);b.gravity=Gravity.CENTER;b.layoutParams=LinearLayout.LayoutParams(dp(48),dp(56));b.setOnClickListener{onBackPressed()};addView(b) }
        val t=text(title,22,true);addView(t,LinearLayout.LayoutParams(0,-2,1f))
        val menu=text("⋮",29,false,TEXT);menu.gravity=Gravity.CENTER;menu.layoutParams=LinearLayout.LayoutParams(dp(44),dp(52))
        menu.setOnClickListener { anchor ->
            PopupMenu(this@MainActivity,anchor).apply {
                this.menu.add("Backup").setOnMenuItemClickListener {
                    val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"1k-hit-backup.json")}
                    startActivityForResult(i,REQUEST_EXPORT);true
                }
                this.menu.add("Restore").setOnMenuItemClickListener {
                    val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type="application/json"}
                    startActivityForResult(i,REQUEST_IMPORT);true
                }
                show()
            }
        };addView(menu)
    }
    private fun reset(){root=column().apply {setBackgroundColor(BG)};setContentView(root)}
    private fun navIcon(kind:Int, color:Int):Drawable = object:Drawable() {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; strokeWidth=dp(2).toFloat(); style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
        override fun draw(canvas:Canvas) {
            val w=bounds.width().toFloat();val h=bounds.height().toFloat()
            canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(w/24f,h/24f)
            if(kind==0){
                for(y in listOf(5f,12f,19f)) {canvas.drawCircle(4f,y,0.8f,paint);canvas.drawLine(9f,y,21f,y,paint)}
            } else {
                canvas.drawLine(3f,21f,21f,21f,paint)
                canvas.drawRect(5f,13f,8f,21f,paint)
                canvas.drawRect(11f,8f,14f,21f,paint)
                canvas.drawRect(17f,3f,20f,21f,paint)
            }
            canvas.restore()
        }
        override fun setAlpha(alpha:Int){paint.alpha=alpha}
        override fun setColorFilter(colorFilter:android.graphics.ColorFilter?){paint.colorFilter=colorFilter}
        @Deprecated("Deprecated in Java") override fun getOpacity()=android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth()=dp(22)
        override fun getIntrinsicHeight()=dp(22)
    }
    // Original v26 pencil / Android trash icons (before custom drawing attempts).
    private fun recordActionIcon(trash:Boolean, action:()->Unit): View = if(trash){
        ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(MUTED)
            setPadding(dp(8),dp(8),dp(8),dp(8))
            contentDescription="Obriši"
            setOnClickListener { action() }
        }
    } else {
        text("✎",21,false,MUTED).apply {
            gravity=Gravity.CENTER
            contentDescription="Izmeni"
            setOnClickListener { action() }
        }
    }
    private fun actionParams()=LinearLayout.LayoutParams(dp(40),dp(36)).apply { gravity=Gravity.CENTER_VERTICAL }
    private fun dateHeading(t:Long):String {
        val date=Calendar.getInstance().apply {timeInMillis=t}
        val today=Calendar.getInstance()
        if(date.get(Calendar.ERA)==today.get(Calendar.ERA) &&
           date.get(Calendar.YEAR)==today.get(Calendar.YEAR) &&
           date.get(Calendar.DAY_OF_YEAR)==today.get(Calendar.DAY_OF_YEAR))return "danas"
        today.add(Calendar.DAY_OF_YEAR,-1)
        if(date.get(Calendar.ERA)==today.get(Calendar.ERA) &&
           date.get(Calendar.YEAR)==today.get(Calendar.YEAR) &&
           date.get(Calendar.DAY_OF_YEAR)==today.get(Calendar.DAY_OF_YEAR))return "juče"
        return SimpleDateFormat("dd.MM.yyyy.",Locale("sr")).format(Date(t))
    }
    private fun dayKey(t:Long):Long = Calendar.getInstance().apply {
        timeInMillis=t
        set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0)
        set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)
    }.timeInMillis
    private fun eventClock(t:Long):String = SimpleDateFormat("HH:mm",Locale("sr")).format(Date(t))
    private fun bottomTabs(){
        val bar=row().apply {setPadding(dp(12),dp(5),dp(12),dp(8));setBackgroundColor(BG)}
        listOf("Udari","Statistika").forEachIndexed { i,name ->
            val selected=tab==i
            val tint=if(selected) ACCENT else MUTED
            val item=column().apply {
                gravity=Gravity.CENTER
                val icon=android.widget.ImageView(this@MainActivity).apply {
                    setImageDrawable(navIcon(i,tint));scaleType=android.widget.ImageView.ScaleType.CENTER_INSIDE
                }
                addView(icon,LinearLayout.LayoutParams(dp(24),dp(24)))
                val title=text(name,12,selected,tint).apply {gravity=Gravity.CENTER}
                addView(title,LinearLayout.LayoutParams(-2,dp(24)))
                setOnClickListener { if(tab!=i){tab=i;render()} }
            }
            bar.addView(item,LinearLayout.LayoutParams(0,dp(56),1f))
        }
        add(root,bar)
    }
    private fun scrollBody(title:String,back:Boolean=true):LinearLayout {
        reset();add(root,pad(header(title,back),10,8,10,8))
        val scroll=ScrollView(this).apply {isFillViewport=true;clipToPadding=false}
        val content=column().apply {setPadding(dp(16),dp(10),dp(16),dp(40))}
        scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        if(!back)bottomTabs()
        return content
    }
    private fun render(){if(tab==1){refreshHome=null;reloadHome=null;showStats()} else showList()}
    private fun refreshAfterChange() { if(tab==0) (reloadHome?.invoke() ?: render()) else render() }
    private fun showList(){
        tab=0
        reset();add(root,pad(header("Udari",false),10,8,10,6))
        val searchRow=row().apply {background=outlinedInputBackground(13);setPadding(dp(14),0,dp(6),0)}
        val magnifier=ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_search);setColorFilter(MUTED)
            setPadding(dp(2),dp(13),dp(2),dp(13))
        }
        searchRow.addView(magnifier,LinearLayout.LayoutParams(dp(27),dp(50)))
        val searchField=EditText(this).apply {
            setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(search);setSelection(text.length);hint="Pretraži događaje"
            setHintTextColor(MUTED);setTextColor(TEXT);textSize=16f;background=null
            setPadding(dp(9),0,dp(5),0)
        }
        searchRow.addView(searchField,LinearLayout.LayoutParams(0,dp(50),1f))
        val clear=text("×",26,false,MUTED).apply {gravity=Gravity.CENTER}
        clear.visibility=if(search.isEmpty())View.GONE else View.VISIBLE
        clear.setOnClickListener {searchField.setText("")}
        searchRow.addView(clear,LinearLayout.LayoutParams(dp(40),dp(48)))
        add(root,column().apply {setPadding(dp(16),dp(2),dp(16),dp(5));add(this,searchRow)})
        val scroll=ScrollView(this).apply {isFillViewport=false}
        val list=column().apply {setPadding(dp(16),dp(8),dp(16),dp(48))}
        scroll.addView(list)
        // Overlay the create button on the scrolling list: never reserve its own row.
        val listArea=android.widget.FrameLayout(this)
        listArea.addView(scroll,android.widget.FrameLayout.LayoutParams(-1,-1))
        val fab=button("+",true){showCreateEventModal()}.apply {textSize=28f;contentDescription="Novi događaj"}
        val fabParams=android.widget.FrameLayout.LayoutParams(dp(58),dp(56),Gravity.BOTTOM or Gravity.RIGHT).apply {
            rightMargin=dp(18);bottomMargin=dp(16)
        }
        listArea.addView(fab,fabParams)
        root.addView(listArea,LinearLayout.LayoutParams(-1,0,1f))
        bottomTabs()
        val emptyMessage=text("Nema rezultata",14,false,MUTED).apply {visibility=View.GONE}
        var closeExpanded:(()->Unit)?=null
        var cards=emptyList<Pair<EventRecord,View>>()
        var dateHeaders=emptyList<Pair<View,List<Pair<EventRecord,View>>>>()
        fun applySearch() {
            var visible=0
            cards.forEach { (event,view) ->
                val matches=event.description.contains(search,true)
                view.visibility=if(matches)View.VISIBLE else View.GONE
                if(matches)visible++
            }
            dateHeaders.forEach { (heading, entries) ->
                heading.visibility=if(entries.any { (event,_) -> event.description.contains(search,true) }) View.VISIBLE else View.GONE
            }
            emptyMessage.visibility=if(visible==0 && search.isNotBlank())View.VISIBLE else View.GONE
        }
        fun buildCards(events:List<EventRecord>){
            closeExpanded=null
            list.removeAllViews()
            if(pickEventForHit)add(list,text("Izaberi događaj",15,true,ACCENT),8)
            add(list,emptyMessage,16)
            val made=mutableListOf<Pair<EventRecord,View>>()
            val headings=mutableListOf<Pair<View,List<Pair<EventRecord,View>>>>()
            // Events are grouped by creation date, newest day first. Within the
            // day, the backend's last-modified order is preserved.
            events.groupBy { dayKey(it.created) }.toSortedMap(compareByDescending { it }).forEach { (_,dayEvents) ->
                val dateTitle=text(dateHeading(dayEvents.first().created),13,true,MUTED).apply {
                    setPadding(dp(3),dp(10),0,dp(3))
                }
                add(list,dateTitle,8)
                val groupEntries=mutableListOf<Pair<EventRecord,View>>()
                dayEvents.forEach { e ->
                val card=column().apply {
                    background=rounded(if(e.closed)Color.rgb(28,38,47) else SURFACE)
                    alpha=if(e.closed)0.62f else 1f
                    setPadding(dp(15),dp(12),dp(15),dp(13))
                }
                // Title occupies its own line. Timestamp and actions share the same
                // footer row, so icon alignment never depends on the title height.
                val top=column()
                add(top,text(e.description,17,false))
                val eventFooter=row().apply { gravity=Gravity.BOTTOM }
                val eventTime=text(eventClock(e.created),12,false,MUTED).apply { gravity=Gravity.BOTTOM }
                eventFooter.addView(eventTime,LinearLayout.LayoutParams(0,dp(32),1f))
                if(!e.closed){
                    eventFooter.addView(recordActionIcon(false) {showCreateEventModal(e)},actionParams())
                    eventFooter.addView(recordActionIcon(true) {confirmDelete("događaj") {store.deleteEvent(e.id)}},actionParams())
                }
                add(top,eventFooter,2)
                add(card,top)
                // The detail view is retained in the same card; never recreate the event list on expand.
                val detail=column()
                add(card,detail)
                var generation=0
                fun expand() {
                    generation++
                    val request=generation
                    detail.removeAllViews()
                    if(expandedEventId!=e.id){ detail.visibility=View.GONE;return }
                    detail.visibility=View.VISIBLE
                    add(detail,text("${e.domain} · ${e.trigger}",13,false,MUTED),6)
                    val records=column()
                    add(detail,records,10)
                    work({ val hits=store.hits(e.id);Pair(hits,hits.associate{it.id to store.goals(it.id)}) }) { (hits,goals) ->
                        if(request!=generation || expandedEventId!=e.id || detail.parent==null)return@work
                        hits.forEachIndexed { idx,hit ->
                            // Each hit is a visually separate unit, with its own start-of-work entry.
                            val hitPanel=column().apply {
                                background=rounded(Color.rgb(43,59,72),12)
                                setPadding(dp(12),dp(11),dp(12),dp(12))
                            }
                            add(hitPanel,inlineRecord("Udar ${idx+1}","${hit.intensity}/7 · ${hit.emotions.joinToString()}",!e.closed,{editHit(e,hit)},{confirmDelete("udar") {store.deleteHit(hit.id)}}))
                            val strategies=hit.strategies.joinToString()
                            if(strategies.isNotBlank())add(hitPanel,text(strategies,13,false,MUTED),7)
                            if(hit.note.isNotBlank())add(hitPanel,text(hit.note,13,false,MUTED),5)
                            goals[hit.id].orEmpty().forEach {goal ->
                                val startPanel=column().apply {
                                    background=rounded(Color.rgb(30,44,56),10)
                                    setPadding(dp(11),dp(10),dp(11),dp(10))
                                }
                                add(startPanel,inlineRecord("Početak rada · ${goal.type}","${goal.duration} · ${goal.score} · ${fmt(goal.startedAt)}",!e.closed,{editGoal(e,hit,goal)},{confirmDelete("početak rada") {store.deleteGoal(goal.id)}}))
                                if(goal.evidence.isNotBlank())add(startPanel,text(goal.evidence,13,false,MUTED),5)
                                add(hitPanel,startPanel,12)
                            }
                            add(records,hitPanel,12)
                        }
                        if(!e.closed){
                            val needsHit=hits.isEmpty() || goals[hits.last().id].orEmpty().isNotEmpty()
                            if(!needsHit)add(records,text("Nedostaje početak rada",14,true,ERROR),12)
                        }
                    }
                }
                if(pickEventForHit) {
                    top.setOnClickListener {
                        pickEventForHit=false
                        work({store.hits(e.id).let {it.isEmpty() || store.goals(it.last().id).isNotEmpty()}}) {eligible->
                            if(eligible && !e.closed)editHit(e,null)
                            else Toast.makeText(this,"Prvo dodaj početak rada",Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    top.setOnClickListener {
                        val wasOpen=expandedEventId==e.id
                        // Collapse only this card. The other card (if any) is closed using its retained callback.
                        expandedEventId=if(wasOpen)null else e.id
                        closeExpanded?.invoke()
                        closeExpanded=if(wasOpen)null else ({expand()})
                        expand()
                    }
                }
                if(expandedEventId==e.id)expand() else detail.visibility=View.GONE
                if(!e.closed){
                    val actionSlot=column()
                    add(card,actionSlot,11)
                    work({val hs=store.hits(e.id);Pair(hs.lastOrNull(),hs.isEmpty() || store.goals(hs.last().id).isNotEmpty())}) { (lastHit,needsHit) ->
                        if(actionSlot.parent==null)return@work
                        val action=button(if(needsHit)"+ Udar" else "+ Početak rada") {
                            if(needsHit)editHit(e,null) else if(lastHit!=null)editGoal(e,lastHit,null)
                        }
                        action.typeface=Typeface.DEFAULT
                        action.setPadding(dp(11),dp(5),dp(11),dp(5))
                        action.minHeight=0
                        actionSlot.addView(action,LinearLayout.LayoutParams(-2,dp(36)))
                    }
                }
                add(list,card,9)
                made.add(e to card)
                groupEntries.add(e to card)
                }
                headings.add(dateTitle to groupEntries)
            }
            cards=made
            dateHeaders=headings
            applySearch()
        }
        refreshHome={ /* Keep existing cards and scroll position; no full-screen redraw. */ }
        reloadHome={work({store.autoClose();store.events()}) {buildCards(it)}}
        reloadHome?.invoke()
        searchField.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                search=s?.toString().orEmpty()
                clear.visibility=if(search.isEmpty())View.GONE else View.VISIBLE
                applySearch()
            }
            override fun afterTextChanged(e:Editable?){}
        })
    }
    private fun inlineRecord(title:String,summary:String,editable:Boolean,onEdit:()->Unit,onDelete:()->Unit):View=column().apply {
        add(this,text(title,14,true))
        // Summary and both actions are aligned in one footer row.
        // No bottom alignment against a variable-height text column.
        val footer=row().apply { gravity=Gravity.BOTTOM }
        val summaryText=text(summary,13,false,MUTED).apply {gravity=Gravity.BOTTOM; minHeight=dp(32)}
        footer.addView(summaryText,LinearLayout.LayoutParams(0,-2,1f))
        if(editable){
            footer.addView(recordActionIcon(false,onEdit),actionParams())
            footer.addView(recordActionIcon(true,onDelete),actionParams())
        }
        if(summary.isNotBlank() || editable)add(this,footer,2)
    }
    private fun confirmDelete(kind:String, deletion:()->Unit){
        android.app.AlertDialog.Builder(this)
            .setMessage("Obrisati $kind?")
            .setNegativeButton("Otkaži",null)
            .setPositiveButton("Obriši") { _,_ ->
                work({deletion()}) {editingEventId=null;refreshAfterChange()}
            }.show()
    }
    // Event creation and editing share the same three-step modal.
    private fun showCreateEventModal(existing: EventRecord? = null) {
        val description=input(existing?.description.orEmpty(),"Naziv",2)
        var domain=existing?.domain ?: DOMAINS.first()
        var trigger=existing?.trigger ?: TRIGGERS.first()
        stepModal(if(existing==null) "Novi događaj" else "Izmeni događaj", listOf(
            "" to description,
            "Domen" to oneChoice(DOMAINS,domain){domain=it},
            "Okidač" to oneChoice(TRIGGERS,trigger){trigger=it}
        ),editing=existing!=null,focusFirstInput=existing==null,canNext={ index ->
            if(index==0 && description.text.toString().trim().isEmpty()) {
                description.error="Obavezno"
                false
            } else true
        },onSave={
            val value=description.text.toString().trim()
            if(value.isNotEmpty()) {
                work({
                    if(existing==null)store.createEvent(value,domain,trigger)
                    else {store.updateEvent(existing.copy(description=value,domain=domain,trigger=trigger));existing.id}
                }) { id ->
                    if(existing==null)expandedEventId=id
                    refreshAfterChange()
                }
            }
        })
    }
    // A single compact, non-scrollable modal host for every input and inspection flow.
    // The underlying home tab remains mounted; only the modal changes steps.
    private fun stepModal(title:String, steps:List<Pair<String,View>>, onSave:()->Unit,
                          canNext:(Int)->Boolean={true}, saveText:String="Sačuvaj", editing:Boolean=false,
                          focusFirstInput:Boolean=false) {
        activeDialog?.dismiss()
        val dialog=Dialog(this)
        activeDialog=dialog
        val frame=column().apply { background=rounded(SURFACE,22);setPadding(dp(18),dp(13),dp(18),dp(14)) }

        // Persistent entity context: visually quiet and paired with its step counter.
        val toolbar=row()
        val titleView=text(title,14,false,MUTED)
        toolbar.addView(titleView,LinearLayout.LayoutParams(0,dp(34),1f))
        val counter=text("",13,true,MUTED).apply{gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL}
        toolbar.addView(counter,LinearLayout.LayoutParams(dp(53),dp(34)))
        add(frame,toolbar)

        // Step title belongs above the separator; text inputs carry their own hint.
        val section=text("",21,true,TEXT)
        add(frame,section,6)
        val divider=View(this).apply {setBackgroundColor(Color.rgb(69,87,99))}
        frame.addView(divider,LinearLayout.LayoutParams(-1,dp(1)).apply {topMargin=dp(12);bottomMargin=dp(12)})
        val holder=android.widget.FrameLayout(this)
        frame.addView(holder,LinearLayout.LayoutParams(-1,0,1f))
        val actions=row()
        val prev=button("Nazad",false){}
        val next=button("Dalje"){}
        actions.addView(prev,LinearLayout.LayoutParams(0,dp(48),1f))
        actions.addView(View(this),LinearLayout.LayoutParams(dp(12),1))
        actions.addView(next,LinearLayout.LayoutParams(0,dp(48),1f))
        add(frame,actions,10)
        var index=0
        // A short visual confirmation keeps fast taps from landing on the next step.
        val advanceHandler=android.os.Handler(android.os.Looper.getMainLooper())
        var pendingAdvance:Runnable?=null
        fun cancelPendingAdvance(){
            pendingAdvance?.let { advanceHandler.removeCallbacks(it) }
            pendingAdvance=null
        }
        dialog.setOnDismissListener {
            cancelPendingAdvance()
            if(activeDialog===dialog) activeDialog=null
        }
        fun showStep(){
            cancelPendingAdvance()
            counter.text=if(steps.size==1) "" else "${index+1}/${steps.size}"
            val name=steps[index].first
            section.text=name
            section.visibility=if(name.isBlank()) View.GONE else View.VISIBLE
            val view=steps[index].second
            (view.parent as? ViewGroup)?.removeView(view)
            holder.removeAllViews()
            holder.addView(view,android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
            prev.text=if(index==0)"Otkaži" else "Nazad"
            next.text=if(index==steps.lastIndex)saveText else "Dalje"
            // Radio controls advance immediately when newly entering a record, including
            // taps on the already checked/default item. They never autosave the final step.
            // During editing, both manual navigation buttons remain available.
            if(view is RadioGroup){
                for(i in 0 until view.childCount){
                    (view.getChildAt(i) as? RadioButton)?.setOnClickListener {
                        if(!editing && index<steps.lastIndex && canNext(index)) {
                            // Every tap (including the currently selected option) restarts
                            // the confirmation window. The next step cannot catch this tap.
                            cancelPendingAdvance()
                            val expectedIndex=index
                            val action=Runnable {
                                pendingAdvance=null
                                if(dialog.isShowing && index==expectedIndex) {
                                    index++
                                    showStep()
                                }
                            }
                            pendingAdvance=action
                            advanceHandler.postDelayed(action,400L)
                        }
                    }
                }
            }
            // Navigation is retained for edits, multiselect, free text and last step.
            val autoRadio=view is RadioGroup && !editing && index<steps.lastIndex
            actions.visibility=View.VISIBLE
            next.visibility=if(autoRadio) View.GONE else View.VISIBLE
        }
        prev.setOnClickListener { if(index==0)dialog.dismiss() else {index--;showStep()} }
        next.setOnClickListener {
            if(!canNext(index))return@setOnClickListener
            if(index<steps.lastIndex){index++;showStep()} else {dialog.dismiss();onSave()}
        }
        showStep()
        val contentWidth=resources.displayMetrics.widthPixels-dp(66)
        val widthSpec=View.MeasureSpec.makeMeasureSpec(contentWidth,View.MeasureSpec.AT_MOST)
        val heightSpec=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)
        val tallest=steps.maxOf { (_,content) ->
            content.measure(widthSpec,heightSpec)
            content.measuredHeight
        }
        val height=(tallest+dp(165)).coerceAtLeast(dp(245))
            .coerceAtMost((resources.displayMetrics.heightPixels-dp(140)).coerceAtMost(dp(710)))
        dialog.setContentView(frame)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if(focusFirstInput && steps.firstOrNull()?.second is EditText){
            val first=steps.first().second as EditText
            first.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
        dialog.window?.setLayout(resources.displayMetrics.widthPixels-dp(22),height)
        if(focusFirstInput && steps.firstOrNull()?.second is EditText){
            val first=steps.first().second as EditText
            first.post {
                if(dialog.isShowing){
                    first.requestFocus()
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(first,InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
    private fun choices(items:List<String>,selected:MutableSet<String>,definitions:Map<String,String> = emptyMap()):View {
        val outer=row().apply { gravity=Gravity.TOP }
        val left=column();val right=column()
        outer.addView(left,LinearLayout.LayoutParams(0,-2,1f))
        outer.addView(right,LinearLayout.LayoutParams(0,-2,1f))
        items.forEachIndexed { idx,name ->
            val item=CheckBox(this).apply {
                text=name;textSize=12.3f;setTextColor(TEXT)
                minHeight=dp(47);buttonTintList=android.content.res.ColorStateList.valueOf(ACCENT)
                isChecked=name in selected
                setOnCheckedChangeListener { _,v -> if(v)selected.add(name) else selected.remove(name) }
                definitions[name]?.let{def -> setOnLongClickListener {Toast.makeText(this@MainActivity,def,Toast.LENGTH_LONG).show();true} }
            }
            val col=if(idx<((items.size+1)/2))left else right
            add(col,item)
        }
        return outer
    }
    private fun oneChoice(items:List<String>,current:String,onPick:(String)->Unit):View {
        val group=RadioGroup(this).apply {orientation=RadioGroup.VERTICAL}
        var selectedId=View.NO_ID
        items.forEach { value ->
            val b=RadioButton(this).apply {
                id=View.generateViewId()
                text=value; textSize=15f; setTextColor(TEXT)
                buttonTintList=android.content.res.ColorStateList.valueOf(ACCENT)
                minHeight=dp(47)
            }
            group.addView(b)
            if(value==current) selectedId=b.id
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            if(checkedId!=View.NO_ID) {
                val selected=g.findViewById<RadioButton>(checkedId)
                onPick(selected.text.toString())
            }
        }
        if(selectedId!=View.NO_ID)group.check(selectedId)
        return group
    }
    private fun editEvent(old:EventRecord?){
        if(old?.closed==true)return
        creatingEvent=old==null
        editingEventId=old?.id
        refreshHome?.invoke()
    }
    private fun editHit(event:EventRecord,old:HitRecord?){
        if(event.closed)return
        val emotions=(old?.emotions?:emptyList()).toMutableSet()
        val strategies=(old?.strategies?:emptyList()).toMutableSet()
        var intensity=old?.intensity?:4
        val intensityView=column().apply {
            val value=text("$intensity / 7",27,true,ACCENT).apply{gravity=Gravity.CENTER}
            add(this,value,45)
            val seek=SeekBar(this@MainActivity).apply{max=6;progress=intensity-1}
            seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(v:SeekBar?,p:Int,fromUser:Boolean){intensity=p+1;value.text="$intensity / 7"}
                override fun onStartTrackingTouch(v:SeekBar?){}
                override fun onStopTrackingTouch(v:SeekBar?){}
            })
            add(this,seek,22)
        }
        val note=input(old?.note?:"","Kako si primenio strategije?",2)
        val defs=(ADAPTIVE_STRATEGIES+MALADAPTIVE_STRATEGIES).associate{it.name to it.definition}
        stepModal("Udar", listOf(
            "Emocije" to choices(EMOTIONS,emotions),
            "Intenzitet" to intensityView,
            "Adaptivne strategije" to choices(ADAPTIVE_STRATEGIES.map{it.name},strategies,defs),
            "Maladaptivne strategije" to column().apply{
                add(this,choices(MALADAPTIVE_STRATEGIES.map{it.name},strategies,defs))
                add(this,text("Primena (opciono)",12,false,MUTED),12)
                add(this,note,6)
            }
        ),editing=old!=null,onSave={
            val h=HitRecord(id=old?.id?:0,eventId=event.id,created=old?.created?:System.currentTimeMillis(),
                emotions=emotions.toList(),intensity=intensity,strategies=strategies.toList(),note=note.text.toString())
            work({if(old==null)store.addHit(h) else {store.updateHit(h);h.id}}){expandedEventId=event.id;refreshAfterChange()}
        })
    }
    private fun editGoal(event:EventRecord,hit:HitRecord,old:GoalRecord?){
        if(event.closed)return
        var type=old?.type?:GOALS.first()
        var duration=old?.duration?.takeIf { it in DURATIONS } ?: ""
        var score:Double?=old?.score
        var startedAt=old?.startedAt ?: System.currentTimeMillis()
        val timeButton=text("",16,false,TEXT).apply {
            gravity=Gravity.CENTER_VERTICAL
            minHeight=dp(58)
            setPadding(dp(16),dp(8),dp(14),dp(8))
            background=outlinedInputBackground(12)
            contentDescription="Promeni vreme početka"
        }
        fun updateTimeLabel(){
            // Keep the value left-aligned and legible, like an editable date/time field.
            val selected=Calendar.getInstance().apply { timeInMillis=startedAt }
            val today=Calendar.getInstance()
            val sameDay=selected.get(Calendar.YEAR)==today.get(Calendar.YEAR) &&
                selected.get(Calendar.DAY_OF_YEAR)==today.get(Calendar.DAY_OF_YEAR)
            val dateLabel=if(sameDay) "danas" else SimpleDateFormat("dd.MM.yyyy.",Locale("sr")).format(Date(startedAt))
            val timeLabel=SimpleDateFormat("HH:mm",Locale("sr")).format(Date(startedAt))
            timeButton.text="◷  $dateLabel  ·  $timeLabel    ›"
        }
        updateTimeLabel()
        timeButton.setOnClickListener {
            val current=Calendar.getInstance().apply {timeInMillis=startedAt}
            DatePickerDialog(this,{ _,year,month,day ->
                val chosen=Calendar.getInstance().apply {timeInMillis=startedAt;set(Calendar.YEAR,year);set(Calendar.MONTH,month);set(Calendar.DAY_OF_MONTH,day)}
                TimePickerDialog(this,{ _,hour,minute ->
                    chosen.set(Calendar.HOUR_OF_DAY,hour);chosen.set(Calendar.MINUTE,minute)
                    chosen.set(Calendar.SECOND,0);chosen.set(Calendar.MILLISECOND,0)
                    startedAt=chosen.timeInMillis
                    updateTimeLabel()
                },chosen.get(Calendar.HOUR_OF_DAY),chosen.get(Calendar.MINUTE),true).show()
            },current.get(Calendar.YEAR),current.get(Calendar.MONTH),current.get(Calendar.DAY_OF_MONTH)).show()
        }
        stepModal("Početak rada", listOf(
            "Vrsta" to oneChoice(GOALS,type){type=it},
            "Kašnjenje u odnosu na plan" to oneChoice(DURATIONS,duration){duration=it},
            "Procena funkcionalnosti" to oneChoice(listOf("0.5","1.0"),score?.toString() ?: ""){score=it.toDouble()},
            "Vreme početka" to timeButton
        ),editing=old!=null,canNext={ index ->
            if(index==1 && duration.isEmpty()) {
                Toast.makeText(this,"Izaberi kašnjenje",Toast.LENGTH_SHORT).show()
                false
            } else if(index==2 && score==null) {
                Toast.makeText(this,"Izaberi funkcionalnost",Toast.LENGTH_SHORT).show()
                false
            } else true
        },onSave={
            val g=GoalRecord(id=old?.id?:0,hitId=hit.id,created=old?.created?:System.currentTimeMillis(),
                type=type,duration=duration,score=score!!,evidence=old?.evidence ?: "",startedAt=startedAt)
            work({if(old==null)store.addGoal(g) else {store.updateGoal(g);g.id}}){expandedEventId=event.id;refreshAfterChange()}
        })
    }
    private fun chooseEventForHit(){pickEventForHit=true;refreshHome?.invoke()}
    private fun showStats(){
        tab=1
        val body=scrollBody("Statistika",false)
        val filters=column().apply {
            background=rounded(Color.rgb(26,39,50),14)
            setPadding(dp(12),dp(12),dp(12),dp(14))
        }
        // Compact, expandable filter summary. Default is collapsed so statistics stay visible.
        val summaryLine=row().apply {
            background=rounded(Color.rgb(26,39,50),14)
            setPadding(dp(14),dp(13),dp(14),dp(13))
            isClickable=true
            isFocusable=true
        }
        val summaryText=text("Filteri",14,true,TEXT).apply {
            // Let active filter labels wrap to a second line instead of truncating.
            maxLines=Int.MAX_VALUE
            ellipsize=null
        }
        val summaryArrow=text("▾",18,false,MUTED).apply { gravity=Gravity.CENTER }
        summaryLine.addView(summaryText,LinearLayout.LayoutParams(0,-2,1f))
        summaryLine.addView(summaryArrow,LinearLayout.LayoutParams(dp(28),-2))
        filters.visibility=View.GONE
        summaryLine.setOnClickListener {
            val expanding=filters.visibility!=View.VISIBLE
            filters.visibility=if(expanding) View.VISIBLE else View.GONE
            summaryArrow.text=if(expanding) "▴" else "▾"
        }
        add(body,summaryLine,10)
        add(body,filters,3)
        val results=column()
        add(body,results,16)

        // Filter on the associated event, hit and start-of-work record.
        work({
            val closed=store.events().filter { it.closed }
            val hits=closed.flatMap { event -> store.hits(event.id).map { event to it } }
            val starts=hits.flatMap { (event,hit) -> store.goals(hit.id).map { Triple(event,hit,it) } }
            Triple(closed,hits,starts)
        }) { (_, hits, starts) ->
            // null means all-time; otherwise use a rolling window in days.
            var rangeDays: Int?=null
            val rangeButtons=mutableMapOf<Int?,TextView>()
            var intensityFilter: Set<Int>?=null
            var intensityGroup: String?=null
            var emotionFilter: String?=null
            var strategyFilter: String?=null
            var typeFilter: String?=null
            var domainFilter: String?=null
            var triggerFilter: String?=null

            val domainButtons=mutableListOf<TextView>()
            val intensityButtons=mutableListOf<TextView>()
            val intensityGroupButtons=mutableMapOf<String,TextView>()
            val domainRow=row()
            val intensityRow=row()

            fun durationLabel(milliseconds:Long):String {
                val minutes=(milliseconds/60000.0).toLong()
                val hours=minutes/60
                val rest=minutes%60
                return if(hours>0) "${hours} h ${rest} min" else "${minutes} min"
            }
            fun redraw(){
                // Only non-default selections appear, in the same order as the filters.
                val active=mutableListOf<String>()
                rangeDays?.let { active.add("$it dana") }
                domainFilter?.let { active.add(it) }
                triggerFilter?.let { active.add(it) }
                emotionFilter?.let { active.add(it) }
                if(intensityGroup!=null) active.add(intensityGroup!!)
                else intensityFilter?.let { active.add(it.sorted().joinToString(", ")) }
                strategyFilter?.let { active.add(it) }
                typeFilter?.let { active.add(it) }
                summaryText.text=if(active.isEmpty()) "Filteri" else active.joinToString(" · ")
                summaryLine.contentDescription=if(active.isEmpty()) "Filteri: sve" else "Filteri: "+active.joinToString(", ")
                rangeButtons.forEach { (days,view) ->
                    val chosen=rangeDays==days
                    view.background=rounded(if(chosen) ACCENT else SURFACE,9)
                    view.setTextColor(if(chosen) BG else TEXT)
                }
                domainButtons.forEachIndexed { idx, view ->
                    val chosen=domainFilter==(if(idx==0)null else DOMAINS[idx-1])
                    view.background=rounded(if(chosen)ACCENT else SURFACE,9)
                    view.setTextColor(if(chosen)BG else TEXT)
                }
                intensityButtons.forEachIndexed { idx, view ->
                    val chosen=if(idx==0) intensityFilter==null else intensityFilter?.contains(idx)==true
                    view.background=rounded(if(chosen)ACCENT else SURFACE,9)
                    view.setTextColor(if(chosen)BG else TEXT)
                }
                intensityGroupButtons.forEach { (key,view) ->
                    val chosen=intensityGroup==key
                    view.background=rounded(if(chosen)ACCENT else SURFACE,9)
                    view.setTextColor(if(chosen)BG else TEXT)
                }
                val cutoff=rangeDays?.let { System.currentTimeMillis() - it * 24L * 60L * 60L * 1000L }
                val baseHits=hits.filter { (event,hit) ->
                    (cutoff==null || event.created>=cutoff) &&
                    (intensityFilter==null || hit.intensity in intensityFilter!!) &&
                    (emotionFilter==null || emotionFilter in hit.emotions) &&
                    (strategyFilter==null || strategyFilter in hit.strategies) &&
                    (domainFilter==null || event.domain==domainFilter) &&
                    (triggerFilter==null || event.trigger==triggerFilter)
                }
                val allowedHitIds=baseHits.map { it.second.id }.toSet()
                val matchingStarts=starts.filter { (_,hit,start) ->
                    hit.id in allowedHitIds && (typeFilter==null || start.type==typeFilter)
                }
                val startHitIds=matchingStarts.map { it.second.id }.toSet()
                val matchingHits=if(typeFilter==null)baseHits else baseHits.filter { it.second.id in startHitIds }
                val eventCount=matchingHits.map { it.first.id }.distinct().size
                results.removeAllViews()
                add(results,statCard("Udari",matchingHits.size.toString()),9)
                if(matchingStarts.isNotEmpty()) {
                    add(results,statCard("Prosečna funkcionalnost","%.2f".format(Locale.US,matchingStarts.map { it.third.score }.average())),9)
                } else add(results,statCard("Prosečna funkcionalnost","—"),9)
                // The first hit inherits the event timestamp, because its detailed entry
                // can be delayed. Subsequent hits use their own creation timestamps.
                // Determine the first hit among ALL hits of its event, not only filtered hits.
                val firstHitIdByEvent=hits.groupBy { it.first.id }.mapValues { (_, entries) ->
                    entries.minOf { it.second.id }
                }
                val elapsed=matchingStarts.mapNotNull { (event,hit,start) ->
                    val startTime=if(hit.id==firstHitIdByEvent[event.id]) event.created else hit.created
                    (start.startedAt-startTime).takeIf { it>=0 }
                }
                val orderedElapsed=elapsed.sorted()
                val median=if(orderedElapsed.isEmpty()) "—" else {
                    val middle=orderedElapsed.size/2
                    val medianMs=if(orderedElapsed.size%2==1) orderedElapsed[middle]
                        else (orderedElapsed[middle-1]/2 + orderedElapsed[middle]/2 +
                            (orderedElapsed[middle-1]%2 + orderedElapsed[middle]%2)/2)
                    durationLabel(medianMs)
                }
                val known=matchingStarts.map { it.third }.filter { it.duration in DURATIONS }
                val distributions=column().apply {
                    background=rounded(SURFACE)
                    setPadding(dp(18),dp(15),dp(18),dp(17))
                }
                add(distributions,text("Početak rada",15,true))
                DURATIONS.forEach { category ->
                    val amount=known.count { it.duration==category }
                    val percent=if(known.isEmpty())"—" else "%.1f%%".format(Locale.US,100.0*amount/known.size)
                    val line=row()
                    line.addView(text(category,14,false,MUTED),LinearLayout.LayoutParams(0,-2,1f))
                    line.addView(text(percent,17,true,ACCENT))
                    add(distributions,line,12)
                }
                val legacy=matchingStarts.size-known.size
                if(legacy>0)add(distributions,text("$legacy starijih unosa bez ove kategorije",12,false,MUTED),12)
                add(results,distributions,9)
                add(results,statCard("Medijalno vreme od udara do početka rada",median),9)
                if(matchingHits.isNotEmpty()) {
                    add(results,statCard("Prosečan intenzitet","%.2f / 7".format(Locale.US,matchingHits.map { it.second.intensity }.average())),9)
                } else add(results,statCard("Prosečan intenzitet","—"),9)
                add(results,statCard("Zatvoreni događaji",eventCount.toString()),9)
            }
            val rangeLine=column()
            add(rangeLine,text("Opseg",13,true,MUTED))
            val rangeRow=row()
            listOf<Pair<Int?,String>>(null to "Sve", 90 to "90 dana", 30 to "30 dana", 7 to "7 dana").forEachIndexed { idx,(days,label) ->
                val item=text(label,13,true).apply {
                    gravity=Gravity.CENTER
                    setOnClickListener { rangeDays=days; redraw() }
                }
                rangeButtons[days]=item
                rangeRow.addView(item,LinearLayout.LayoutParams(0,dp(42),1f).apply {
                    if(idx>0)leftMargin=dp(6)
                })
            }
            add(rangeLine,rangeRow,6)
            add(filters,rangeLine)
            val domainLine=column()
            add(domainLine,text("Domen",13,true,MUTED))
            (listOf("Sve")+DOMAINS).forEachIndexed { idx,name ->
                val item=text(name,12,true).apply {
                    gravity=Gravity.CENTER
                    maxLines=2
                    setOnClickListener {domainFilter=if(idx==0)null else DOMAINS[idx-1];redraw()}
                }
                domainButtons.add(item)
                domainRow.addView(item,LinearLayout.LayoutParams(0,dp(48),1f).apply {if(idx>0)leftMargin=dp(5)})
            }
            add(domainLine,domainRow,6)
            add(filters,domainLine,14)
            fun picker(anchor:View,choices:List<String>,current:String?,onPick:(String?)->Unit){
                PopupMenu(this,anchor).apply {
                    (listOf("Sve")+choices).forEachIndexed { idx,choice ->
                        val value=if(idx==0)null else choice
                        menu.add((if(value==current)"✓ " else "")+choice).setOnMenuItemClickListener {
                            onPick(value);true
                        }
                    }
                    show()
                }
            }
            fun filterCell(title:String,choices:List<String>,current:()->String?,onPick:(String?)->Unit):LinearLayout {
                val cell=column()
                add(cell,text(title,13,true,MUTED))
                val action=button("Sve  ▾",false){}.apply {
                    textSize=13f; maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
                    setPadding(dp(8),0,dp(8),0)
                }
                action.setOnClickListener {
                    picker(action,choices,current()) { value ->
                        onPick(value)
                        action.text="${value ?: "Sve"}  ▾"
                        redraw()
                    }
                }
                add(cell,action,6)
                return cell
            }
            fun filtersRow(left:LinearLayout,right:LinearLayout,space:Int=14){
                val row=row()
                row.addView(left,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(View(this),LinearLayout.LayoutParams(dp(12),1))
                row.addView(right,LinearLayout.LayoutParams(0,-2,1f))
                add(filters,row,space)
            }
            filtersRow(
                filterCell("Okidač",TRIGGERS,{triggerFilter}){triggerFilter=it},
                filterCell("Emocija",EMOTIONS,{emotionFilter}){emotionFilter=it}
            )
            val intensityLine=column()
            add(intensityLine,text("Intenzitet",13,true,MUTED))
            (listOf("Sve")+(1..7).map { it.toString() }).forEachIndexed { idx,name ->
                val item=text(name,14,true).apply {
                    gravity=Gravity.CENTER
                    setOnClickListener {
                        intensityFilter=if(idx==0)null else setOf(idx)
                        intensityGroup=null
                        redraw()
                    }
                }
                intensityButtons.add(item)
                intensityRow.addView(item,LinearLayout.LayoutParams(0,dp(39),1f).apply {if(idx>0)leftMargin=dp(4)})
            }
            add(intensityLine,intensityRow,6)
            val intensityGroupsRow=row()
            listOf("1–4" to (1..4).toSet(), "5–7" to (5..7).toSet()).forEachIndexed { idx,(label,values) ->
                val item=text(label,13,true).apply {
                    gravity=Gravity.CENTER
                    setOnClickListener {
                        if(intensityGroup==label) {
                            // A second tap on the active group returns to the default All.
                            intensityGroup=null
                            intensityFilter=null
                        } else {
                            intensityGroup=label
                            intensityFilter=values
                        }
                        redraw()
                    }
                }
                intensityGroupButtons[label]=item
                intensityGroupsRow.addView(item,LinearLayout.LayoutParams(0,dp(36),1f).apply {
                    if(idx>0)leftMargin=dp(8)
                })
            }
            add(intensityLine,intensityGroupsRow,8)
            add(filters,intensityLine,14)

            filtersRow(
                filterCell("Strategija",(ADAPTIVE_STRATEGIES+MALADAPTIVE_STRATEGIES).map { it.name },{strategyFilter}){strategyFilter=it},
                filterCell("Vrsta rada",GOALS,{typeFilter}){typeFilter=it}
            )
            redraw()
        }
    }
    private fun statCard(title:String,value:String):View=column().apply{background=rounded(SURFACE);setPadding(dp(18),dp(16),dp(18),dp(16));add(this,text(title,14,false,MUTED));add(this,text(value,24,true,ACCENT),6)}
    private fun fmt(t:Long):String {
        val now=Calendar.getInstance()
        val recorded=Calendar.getInstance().apply { timeInMillis=t }
        val sameDate=now.get(Calendar.ERA)==recorded.get(Calendar.ERA) &&
            now.get(Calendar.YEAR)==recorded.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR)==recorded.get(Calendar.DAY_OF_YEAR)
        val yesterday=Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR,-1) }
        val isYesterday=yesterday.get(Calendar.ERA)==recorded.get(Calendar.ERA) &&
            yesterday.get(Calendar.YEAR)==recorded.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR)==recorded.get(Calendar.DAY_OF_YEAR)
        val time=SimpleDateFormat("HH:mm",Locale("sr")).format(Date(t))
        return when {
            sameDate -> "danas · $time"
            isYesterday -> "juče · $time"
            else -> dateFormat.format(Date(t))
        }
    }
    override fun onBackPressed(){if(tab==1){tab=0;render()}else super.onBackPressed()}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        val uri=data?.data?:return
        if(resultCode!=RESULT_OK)return
        when(requestCode){
            REQUEST_EXPORT->work({val data=store.exportJson();contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(data)};true}){Toast.makeText(this,"Backup sačuvan",Toast.LENGTH_SHORT).show()}
            REQUEST_IMPORT->work({val json=contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("Fajl nije dostupan");store.importJson(json);true}){page="list";render();Toast.makeText(this,"Backup učitan",Toast.LENGTH_SHORT).show()}
        }
    }
}
