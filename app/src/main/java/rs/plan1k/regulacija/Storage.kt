package rs.plan1k.regulacija

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

val DOMAINS = listOf("Žene", "Prijatelji", "Novac", "Ostalo")
val TRIGGERS = listOf("Odbijanje", "Negativna komparacija", "Neuspeh", "Konflikt", "Ostalo")
val EMOTIONS = listOf("Tuga", "Stid / inferiornost", "Zavist", "Kajanje", "Bes", "Frustracija", "Strah / anksioznost", "Potištenost / beznadežnost", "Drugo")
val GOALS = listOf("San", "Dubok rad", "Rutina", "Socijalna interakcija", "Odmor", "Fizička aktivnost", "Ostalo")
val DURATIONS = listOf("Na vreme", "Kasnije istog dana", "Sutra ili kasnije")

data class RegulationStrategy(val name: String, val definition: String)
val ADAPTIVE_STRATEGIES = listOf(
    RegulationStrategy("Reappraisal (reinterpretacija)", "Promena tumačenja događaja kako bi se promenila emocionalna reakcija."),
    RegulationStrategy("Distrakcija", "Privremeno preusmeravanje pažnje na drugu aktivnost ili sadržaj."),
    RegulationStrategy("Prihvatanje emocije", "Dopuštanje emociji da bude prisutna bez stalne borbe protiv nje."),
    RegulationStrategy("Rešavanje problema", "Izbor konkretnog koraka kojim se menja situacija."),
    RegulationStrategy("Bihevioralna aktivacija", "Započinjanje smislene aktivnosti uprkos negativnom raspoloženju."),
    RegulationStrategy("Mindfulness / usmeravanje pažnje", "Primećivanje misli bez automatskog poistovećivanja sa njima."),
    RegulationStrategy("Relaksacija / disanje", "Smanjivanje telesne pobuđenosti disanjem ili opuštanjem."),
    RegulationStrategy("Traženje socijalne podrške", "Kontakt sa osobom radi podrške ili praktične pomoći.")
)
val MALADAPTIVE_STRATEGIES = listOf(
    RegulationStrategy("Ruminacija", "Ponavljano vraćanje na događaj bez napretka ka rešenju."),
    RegulationStrategy("Suicidalno razmišljanje", "Misli o prekidu života, spontane ili doživljene kao zamišljeni izlaz iz patnje."),
    RegulationStrategy("Potiskivanje misli", "Pokušaj da se neželjena misao aktivno ukloni ili zabrani."),
    RegulationStrategy("Izbegavanje problema", "Izbegavanje važnog problema radi trenutnog olakšanja."),
    RegulationStrategy("Katastrofizacija", "Preuveličavanje najgorih mogućih posledica."),
    RegulationStrategy("Samookrivljavanje", "Uporno i široko pripisivanje krivice sebi."),
    RegulationStrategy("Alkohol / supstance", "Korišćenje supstanci radi promene emocionalnog stanja.")
)

data class EventRecord(val id: Long = 0, val description: String, val domain: String = "Žene",
                       val trigger: String = "Odbijanje", val created: Long = System.currentTimeMillis(),
                       val updated: Long = System.currentTimeMillis(), val closed: Boolean = false)
data class HitRecord(val id: Long = 0, val eventId: Long, val created: Long = System.currentTimeMillis(),
                     val emotions: List<String> = emptyList(), val intensity: Int = 4,
                     val strategies: List<String> = emptyList(), val note: String = "")
data class GoalRecord(val id: Long = 0, val hitId: Long, val created: Long = System.currentTimeMillis(),
                      val type: String = "Rutina", val duration: String = DURATIONS[0],
                      val score: Double = 1.0, val evidence: String = "",
                      val startedAt: Long = created)

class Store(context: Context): SQLiteOpenHelper(context.applicationContext, "1k_hit_rebuild.db", null, 3) {
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, description TEXT NOT NULL, domain TEXT NOT NULL, trigger TEXT NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL, closed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE hits(id INTEGER PRIMARY KEY AUTOINCREMENT, event_id INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE, created INTEGER NOT NULL, emotions TEXT NOT NULL, intensity INTEGER NOT NULL, strategies TEXT NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE goals(id INTEGER PRIMARY KEY AUTOINCREMENT, hit_id INTEGER NOT NULL REFERENCES hits(id) ON DELETE CASCADE, created INTEGER NOT NULL, type TEXT NOT NULL, duration TEXT NOT NULL, score REAL NOT NULL, evidence TEXT NOT NULL, started_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_hits_event ON hits(event_id)")
        db.execSQL("CREATE INDEX idx_goals_hit ON goals(hit_id)")
        db.execSQL("CREATE INDEX idx_events_updated ON events(updated DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion<2) db.execSQL("ALTER TABLE goals ADD COLUMN started_at INTEGER NOT NULL DEFAULT 0")
        if(oldVersion<2) db.execSQL("UPDATE goals SET started_at=created WHERE started_at=0")
        // One-time cleanup of already closed test events in the previous database.
        // Foreign keys cascade the deletion to their hits and starts of work.
        if(oldVersion<3) db.execSQL("DELETE FROM events WHERE closed=1")
    }
    private fun values(e: EventRecord) = ContentValues().apply {
        put("description",e.description); put("domain",e.domain); put("trigger",e.trigger)
        put("created",e.created); put("updated",e.updated); put("closed",if(e.closed)1 else 0)
    }
    private fun values(h: HitRecord) = ContentValues().apply {
        put("event_id",h.eventId); put("created",h.created); put("emotions",JSONArray(h.emotions).toString())
        put("intensity",h.intensity); put("strategies",JSONArray(h.strategies).toString()); put("note",h.note)
    }
    private fun values(g: GoalRecord) = ContentValues().apply {
        put("hit_id",g.hitId); put("created",g.created); put("type",g.type); put("duration",g.duration)
        put("score",g.score); put("evidence",g.evidence); put("started_at",g.startedAt)
    }
    fun events():List<EventRecord> = buildList {
        readableDatabase.rawQuery("SELECT id,description,domain,trigger,created,updated,closed FROM events ORDER BY updated DESC,id DESC",null).use { c ->
            while(c.moveToNext()) add(EventRecord(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getInt(6)==1))
        }
    }
    fun hits(eventId: Long):List<HitRecord> = buildList {
        readableDatabase.rawQuery("SELECT id,event_id,created,emotions,intensity,strategies,note FROM hits WHERE event_id=? ORDER BY id ASC",arrayOf(eventId.toString())).use { c ->
            while(c.moveToNext()) add(HitRecord(c.getLong(0),c.getLong(1),c.getLong(2),strings(c.getString(3)),c.getInt(4),strings(c.getString(5)),c.getString(6)))
        }
    }
    fun goals(hitId: Long):List<GoalRecord> = buildList {
        readableDatabase.rawQuery("SELECT id,hit_id,created,type,duration,score,evidence,started_at FROM goals WHERE hit_id=? ORDER BY id ASC",arrayOf(hitId.toString())).use { c ->
            while(c.moveToNext()) add(GoalRecord(c.getLong(0),c.getLong(1),c.getLong(2),c.getString(3),c.getString(4),c.getDouble(5),c.getString(6),c.getLong(7)))
        }
    }
    private fun strings(json:String):List<String> = JSONArray(json).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
    private fun assertOpen(db:SQLiteDatabase,id:Long) {
        db.rawQuery("SELECT closed FROM events WHERE id=?",arrayOf(id.toString())).use { c ->
            require(c.moveToFirst() && c.getInt(0)==0) { "Događaj je zatvoren" }
        }
    }
    fun createEvent(description:String,domain:String,trigger:String):Long {
        require(description.isNotBlank())
        return writableDatabase.insertOrThrow("events",null,values(EventRecord(description=description.trim(),domain=domain,trigger=trigger)))
    }
    fun updateEvent(e:EventRecord) { val db=writableDatabase; assertOpen(db,e.id); db.update("events",values(e.copy(updated=System.currentTimeMillis())),"id=?",arrayOf(e.id.toString())) }
    fun addHit(h:HitRecord):Long {
        val db=writableDatabase; db.beginTransaction()
        return try {
            assertOpen(db,h.eventId)
            val latest=latestKind(db,h.eventId)
            require(latest==null || latest=="goal") { "Prvo dodaj početak rada" }
            val id=db.insertOrThrow("hits",null,values(h))
            touch(db,h.eventId); db.setTransactionSuccessful(); id
        } finally {db.endTransaction()}
    }
    fun addGoal(g:GoalRecord):Long {
        val db=writableDatabase; db.beginTransaction()
        return try {
            val eventId=eventForHit(db,g.hitId)
            assertOpen(db,eventId)
            require(latestKind(db,eventId)=="hit") { "Prvo dodaj udar" }
            val id=db.insertOrThrow("goals",null,values(g))
            touch(db,eventId); db.setTransactionSuccessful(); id
        } finally {db.endTransaction()}
    }
    private fun latestKind(db:SQLiteDatabase,eventId:Long):String? {
        db.rawQuery("SELECT id FROM hits WHERE event_id=? ORDER BY id DESC LIMIT 1",arrayOf(eventId.toString())).use { hc ->
            if(!hc.moveToFirst()) return null
            val hitId=hc.getLong(0)
            db.rawQuery("SELECT id FROM goals WHERE hit_id=? LIMIT 1",arrayOf(hitId.toString())).use { gc ->
                return if(gc.moveToFirst()) "goal" else "hit"
            }
        }
    }
    private fun eventForHit(db:SQLiteDatabase,hitId:Long):Long = db.rawQuery("SELECT event_id FROM hits WHERE id=?",arrayOf(hitId.toString())).use { c ->
        require(c.moveToFirst()); c.getLong(0)
    }
    private fun touch(db:SQLiteDatabase,id:Long) { db.execSQL("UPDATE events SET updated=? WHERE id=?",arrayOf(System.currentTimeMillis(),id)) }
    fun updateHit(h:HitRecord) {
        val db=writableDatabase; db.beginTransaction()
        try {assertOpen(db,h.eventId); db.update("hits",values(h),"id=?",arrayOf(h.id.toString()));touch(db,h.eventId);db.setTransactionSuccessful()} finally {db.endTransaction()}
    }
    fun updateGoal(g:GoalRecord) {
        val db=writableDatabase;db.beginTransaction()
        try {val eventId=eventForHit(db,g.hitId);assertOpen(db,eventId);db.update("goals",values(g),"id=?",arrayOf(g.id.toString()));touch(db,eventId);db.setTransactionSuccessful()} finally{db.endTransaction()}
    }
    fun deleteEvent(id:Long) {
        val db=writableDatabase;assertOpen(db,id)
        db.delete("events","id=?",arrayOf(id.toString()))
    }
    fun deleteHit(id:Long) {
        val db=writableDatabase;db.beginTransaction()
        try {
            val eventId=eventForHit(db,id);assertOpen(db,eventId)
            db.delete("hits","id=?",arrayOf(id.toString()))
            touch(db,eventId);db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
    fun deleteGoal(id:Long) {
        val db=writableDatabase;db.beginTransaction()
        try {
            val hitId=db.rawQuery("SELECT hit_id FROM goals WHERE id=?",arrayOf(id.toString())).use { c ->
                require(c.moveToFirst()) {"Početak rada ne postoji"};c.getLong(0)
            }
            val eventId=eventForHit(db,hitId);assertOpen(db,eventId)
            db.delete("goals","id=?",arrayOf(id.toString()))
            touch(db,eventId);db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
    fun nextDeadline():Long? {
        val db=readableDatabase
        var earliest:Long?=null
        db.rawQuery("SELECT id,updated FROM events WHERE closed=0",null).use { c ->
            while(c.moveToNext()) {
                if(latestKind(db,c.getLong(0))=="goal") {
                    val deadline=c.getLong(1)+48L*3600*1000
                    if(earliest==null || deadline<earliest!!) earliest=deadline
                }
            }
        }
        return earliest
    }
    fun autoClose():Boolean {
        val db=writableDatabase
        val now=System.currentTimeMillis()
        val deadline=now-48L*3600*1000
        val pending=mutableListOf<Long>()
        db.rawQuery("SELECT id FROM events WHERE closed=0 AND updated<=?",arrayOf(deadline.toString())).use { c -> while(c.moveToNext()) pending.add(c.getLong(0)) }
        var count=0
        db.beginTransaction()
        try {
            pending.forEach { id -> if(latestKind(db,id)=="goal") {db.execSQL("UPDATE events SET closed=1 WHERE id=?",arrayOf(id));count++} }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return count>0
    }
    fun exportJson():String {
        val all=events()
        val items=JSONArray()
        all.forEach { e ->
            val j=JSONObject().put("description",e.description).put("domain",e.domain).put("trigger",e.trigger)
                .put("created",e.created).put("updated",e.updated).put("closed",e.closed)
            j.put("hits",JSONArray().apply { hits(e.id).forEach { h ->
                val hj=JSONObject().put("created",h.created).put("emotions",JSONArray(h.emotions)).put("intensity",h.intensity)
                    .put("strategies",JSONArray(h.strategies)).put("note",h.note)
                    .put("goals",JSONArray().apply {goals(h.id).forEach {g -> put(JSONObject().put("created",g.created).put("type",g.type).put("duration",g.duration).put("score",g.score).put("evidence",g.evidence).put("startedAt",g.startedAt))} })
                put(hj)
            } })
            items.put(j)
        }
        return JSONObject().put("format","1k-hit").put("version",1).put("events",items).toString(2)
    }
    fun importJson(text:String) {
        val root=JSONObject(text)
        require(root.optString("format")=="1k-hit" && root.optInt("version")==1) {"Nepodržan backup"}
        val arr=root.getJSONArray("events")
        require(arr.length()<=100000) {"Previše zapisa"}
        val db=writableDatabase;db.beginTransaction()
        try {
            db.delete("goals",null,null);db.delete("hits",null,null);db.delete("events",null,null)
            for(i in 0 until arr.length()) {
                val e=arr.getJSONObject(i)
                val id=db.insertOrThrow("events",null,values(EventRecord(description=e.getString("description"),domain=e.getString("domain"),trigger=e.getString("trigger"),created=e.getLong("created"),updated=e.getLong("updated"),closed=e.getBoolean("closed"))))
                val hs=e.getJSONArray("hits")
                for(j in 0 until hs.length()) {
                    val h=hs.getJSONObject(j)
                    val hid=db.insertOrThrow("hits",null,values(HitRecord(eventId=id,created=h.getLong("created"),emotions=strings(h.getJSONArray("emotions").toString()),intensity=h.getInt("intensity"),strategies=strings(h.getJSONArray("strategies").toString()),note=h.optString("note",""))))
                    val gs=h.getJSONArray("goals")
                    for(k in 0 until gs.length()) {
                        val g=gs.getJSONObject(k)
                        db.insertOrThrow("goals",null,values(GoalRecord(hitId=hid,created=g.getLong("created"),type=g.getString("type"),duration=g.getString("duration"),score=g.getDouble("score"),evidence=g.optString("evidence",""),startedAt=g.optLong("startedAt",g.getLong("created")))))
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}
