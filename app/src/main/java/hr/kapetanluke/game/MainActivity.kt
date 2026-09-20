package hr.kapetanluke.game

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import kotlin.math.*

data class Ship(
    val name:String, val type:String, val length:Int, val days:Int, val target:String,
    var x:Float=0f, var y:Float=0f, var rot:Int=0, var accepted:Boolean=false,
    var placed:Boolean=false, var scored:Boolean=false, var departed:Boolean=false,
    var placedAt:Long=0L, var departureAt:Long=0L
)

class MainActivity: Activity() {
    private lateinit var harbor: HarborView
    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        harbor = HarborView(this)
        setContentView(harbor)
    }
}

class HarborView(private val ctx:Context): View(ctx) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val t=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.DEFAULT_BOLD}
    private val prefs=ctx.getSharedPreferences("kapetan_luke",Context.MODE_PRIVATE)
    private var captain=""
    private var score=1000
    private var secs=300
    private var running=false
    private var accepted=false
    private var selected:Ship?=null
    private var lx=0f; private var ly=0f; private var down=0L
    private var reward=100
    private var rejectPenalty=100
    private var failPenalty=200
    private var movePenalty=20
    private var daySeconds=10
    private var westEnd=0f; private var pontX=0f; private var gatX=0f

    private var scale=1f
    private var panX=0f
    private var panY=0f
    private var panning=false
    private var movedSelected=false

    private val ships=mutableListOf(
        Ship("MY Aurora","Jahta",82,6,"VEZ"),
        Ship("Ocean Pioneer","Supply",54,10,"DOK"),
        Ship("Blue Star","Katamaran",42,3,"VEZ"),
        Ship("Tender 07","Tender",8,4,"PONTON"),
        Ship("Adriatic Bulk","Rasuti teret",104,12,"VEZ")
    )

    private val h=Handler(Looper.getMainLooper())
    private val tick=object:Runnable{
        override fun run(){
            if(running){
                if(secs>0) secs--
                checkDepartures()
                if(secs<=0) finishRound()
                invalidate()
            }
            h.postDelayed(this,1000)
        }
    }

    private val scaleDetector=ScaleGestureDetector(ctx,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean{
            scale=(scale*detector.scaleFactor).coerceIn(1f,3.5f)
            invalidate()
            return true
        }
    })

    init{
        isFocusable=true
        h.post(tick)
        post{askCaptain()}
    }

    private fun askCaptain(){
        val e=EditText(ctx).apply{
            hint="Ime i prezime kapetana"
            setText(prefs.getString("lastCaptain",""))
        }
        AlertDialog.Builder(ctx)
            .setTitle("⚓ KAPETAN LUKE")
            .setMessage("Dobro došli u luku.\nUpišite ime i prezime kapetana.")
            .setView(e)
            .setPositiveButton("DALJE"){_,_->
                captain=e.text.toString().trim().ifBlank{"Kapetan"}
                prefs.edit().putString("lastCaptain",captain).apply()
                toast("Pozdrav, kapetane $captain! ⚓")
                showMail()
            }.setCancelable(false).show()
    }

    private fun showMail(){
        val msg=buildString{
            append("Od: Adriatic Marine Services\nPredmet: Zahtjev za prihvat brodova\n\n")
            append("Poštovani kapetane luke,\nmolimo prihvat sljedećih brodova:\n\n")
            ships.forEach{append("• ${it.name} — ${it.type} — ${it.length} m — ${it.target} — ${it.days} dana\n")}
            append("\nPrihvaćanjem preuzimate obvezu smještaja SVIH brodova.")
        }
        AlertDialog.Builder(ctx).setTitle("NOVA PORUKA").setMessage(msg)
            .setNegativeButton("ODBIJ"){_,_->
                score-=rejectPenalty*ships.size
                accepted=false
                running=true
                toast("Zahtjev odbijen: -${rejectPenalty*ships.size} bodova")
            }
            .setPositiveButton("PRIHVATI"){_,_->
                accepted=true
                ships.forEach{it.accepted=true}
                layoutWaitingShips()
                running=true
                toast("Zahtjev prihvaćen. Smjesti sve brodove!")
            }.setCancelable(false).show()
    }

    private fun layoutWaitingShips(){
        val base=height*0.78f
        ships.filter{!it.departed}.forEachIndexed{i,s->
            s.x=80f+i*(width-160f)/max(1,ships.size-1)
            s.y=base+(i%2)*52f
        }
    }

    override fun onDraw(c:Canvas){
        super.onDraw(c)
        if(width==0)return
        val W=width.toFloat(); val H=height.toFloat()

        c.drawColor(Color.rgb(43,124,153))

        c.save()
        c.translate(panX,panY)
        c.scale(scale,scale)

        val worldW=W/scale
        westEnd=worldW*.60f
        pontX=westEnd+8f
        gatX=pontX+55f

        // Kopno je namjerno usko - vezivanje uz sami rub obale.
        p.color=Color.rgb(223,214,185)
        c.drawRect(0f,68f,worldW,118f,p)

        val start=18f
        val bw=(westEnd-start)/14f
        t.color=Color.rgb(22,46,52); t.textSize=13f
        for(i in 0 until 14){
            val n=14-i
            val x=start+i*bw
            c.drawLine(x,110f,x,126f,p)
            c.drawText(n.toString(),x+2,91f,t)
        }
        t.textSize=11f
        c.drawText("9–14: 65–110 m",22f,108f,t)
        c.drawText("4–8: 45–65 m",westEnd*.40f,108f,t)
        c.drawText("1–3: do 45 m",westEnd*.77f,108f,t)

        p.color=Color.rgb(188,190,180)
        c.drawRect(pontX,118f,pontX+18f,250f,p)
        t.textSize=11f;c.drawText("PONTON",pontX-15f,88f,t)

        c.drawRect(gatX,118f,gatX+30f,360f,p)
        c.drawText("GAT",gatX+3f,88f,t)
        c.drawText("120 m / strana",gatX-25f,106f,t)

        c.drawRect(gatX+30f,68f,worldW,118f,p)
        t.textSize=13f;c.drawText("ISTOČNA OBALA 150 m",gatX+45f,98f,t)

        p.color=Color.rgb(180,184,178)
        c.drawRoundRect(RectF(gatX-150f,380f,gatX-118f,565f),6f,6f,p)
        c.drawRoundRect(RectF(gatX-82f,425f,gatX-50f,565f),6f,6f,p)
        c.drawRoundRect(RectF(gatX,380f,gatX+30f,610f),6f,6f,p)
        t.color=Color.WHITE;t.textSize=12f
        c.drawText("85 m",gatX-150f,585f,t)
        c.drawText("60 m",gatX-82f,585f,t)
        c.drawText("140 m",gatX,630f,t)

        if(accepted) ships.filter{!it.departed}.forEach{drawShip(c,it)}
        c.restore()

        // Veliki moderni HUD - uvijek miruje, nikad se ne zumira.
        p.color=Color.rgb(7,31,47)
        c.drawRect(0f,0f,W,82f,p)
        t.color=Color.rgb(231,245,250);t.textSize=16f
        c.drawText("KAPETAN",18f,24f,t)
        t.color=Color.WHITE;t.textSize=23f
        c.drawText(captain,18f,56f,t)

        t.color=Color.rgb(132,205,226);t.textSize=15f
        c.drawText("VRIJEME",W*.34f,24f,t)
        t.color=Color.WHITE;t.textSize=29f
        c.drawText("%02d:%02d".format(secs/60,secs%60),W*.34f,58f,t)

        t.color=Color.rgb(132,205,226);t.textSize=15f
        c.drawText("BODOVI",W*.51f,24f,t)
        t.color=Color.WHITE;t.textSize=29f
        c.drawText(score.toString(),W*.51f,58f,t)

        drawButton(c,RectF(W-355f,14f,W-255f,68f),"INFO")
        drawButton(c,RectF(W-245f,14f,W-130f,68f),"POSTAVKE")
        drawButton(c,RectF(W-120f,14f,W-10f,68f),"RESTART")

        t.textSize=14f;t.color=Color.WHITE
        c.drawText("Brod: povuci • dodir: ↻90°   |   Prazna karta: pomak   |   2 prsta: zoom",18f,H-14f,t)
    }

    private fun drawButton(c:Canvas,r:RectF,label:String){
        p.color=Color.rgb(33,78,102)
        c.drawRoundRect(r,9f,9f,p)
        t.color=Color.WHITE;t.textSize=13f
        c.drawText(label,r.left+9,r.centerY()+5,t)
    }

    // Detaljnija silueta broda gledana odozgo.
    private fun drawShip(c:Canvas,s:Ship){
        if(s.x==0f)return
        val ppm=1.05f
        val len=(s.length*ppm).coerceIn(16f,125f)
        val wid=when(s.type){
            "Tender"->10f
            "Katamaran"->22f
            "Rasuti teret"->27f
            "Supply"->23f
            else->20f
        }
        c.save()
        c.rotate(s.rot.toFloat(),s.x,s.y)

        val hull=Path().apply{
            moveTo(s.x-len/2,s.y)
            lineTo(s.x-len*.36f,s.y-wid/2)
            lineTo(s.x+len*.35f,s.y-wid/2)
            lineTo(s.x+len/2,s.y-wid*.28f)
            lineTo(s.x+len/2,s.y+wid*.28f)
            lineTo(s.x+len*.35f,s.y+wid/2)
            lineTo(s.x-len*.36f,s.y+wid/2)
            close()
        }
        p.color=when(s.type){
            "Jahta"->Color.rgb(245,247,248)
            "Supply"->Color.rgb(232,143,43)
            "Tender"->Color.rgb(246,210,67)
            "Katamaran"->Color.rgb(235,239,242)
            "Rasuti teret"->Color.rgb(112,124,132)
            else->Color.LTGRAY
        }
        c.drawPath(hull,p)

        // paluba / nadgrađe odozgo
        p.color=Color.rgb(215,224,228)
        c.drawRoundRect(RectF(s.x-len*.10f,s.y-wid*.34f,s.x+len*.20f,s.y+wid*.34f),4f,4f,p)
        p.color=Color.rgb(50,83,96)
        c.drawRect(s.x-len*.04f,s.y-wid*.28f,s.x+len*.05f,s.y+wid*.28f,p)

        if(s.type=="Rasuti teret"){
            p.color=Color.rgb(78,88,94)
            for(k in -2..2){
                val cx=s.x+k*len*.12f
                c.drawRect(cx-len*.045f,s.y-wid*.30f,cx+len*.045f,s.y+wid*.30f,p)
            }
        }
        if(s.type=="Katamaran"){
            p.color=Color.rgb(43,124,153)
            c.drawRect(s.x-len*.30f,s.y-wid*.08f,s.x+len*.32f,s.y+wid*.08f,p)
        }

        p.style=Paint.Style.STROKE
        p.strokeWidth=3f
        p.color=if(s.placed)Color.rgb(55,210,95) else Color.rgb(35,45,50)
        c.drawPath(hull,p)
        p.style=Paint.Style.FILL
        c.restore()

        t.color=Color.WHITE;t.textSize=11f
        val remain=if(s.placed) max(0L,(s.departureAt-SystemClock.elapsedRealtime()+999)/1000) else -1
        val suffix=if(remain>=0) " ${remain}s" else ""
        c.drawText("${s.name} ${s.length}m$suffix",s.x-len/2,s.y+wid+15f,t)
    }

    private fun screenToWorldX(x:Float)=(x-panX)/scale
    private fun screenToWorldY(y:Float)=(y-panY)/scale

    override fun onTouchEvent(e:MotionEvent):Boolean{
        scaleDetector.onTouchEvent(e)
        val W=width.toFloat()

        // Drugi prst uvijek prekida odabir broda: dva prsta služe samo zoomu/pomaku.
        if(e.pointerCount>=2){
            selected=null
            panning=true
            if(e.actionMasked==MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress){
                panX+=e.x-lx
                panY+=e.y-ly
                clampPan()
                invalidate()
            }
            lx=e.x;ly=e.y
            return true
        }

        if(e.actionMasked==MotionEvent.ACTION_UP && panning){
            panning=false
            selected=null
            return true
        }
        if(!accepted)return true

        val wx=screenToWorldX(e.x)
        val wy=screenToWorldY(e.y)

        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                if(e.y<82f){
                    when {
                        e.x in (W-355f)..(W-255f) -> { showInfo(); return true }
                        e.x in (W-245f)..(W-130f) -> { showSettings(); return true }
                        e.x>W-120f -> { restart(); return true }
                    }
                }
                down=e.eventTime
                lx=e.x;ly=e.y
                movedSelected=false

                selected=ships.filter{it.accepted&&!it.departed}.minByOrNull{
                    hypot((it.x-wx).toDouble(),(it.y-wy).toDouble())
                }?.takeIf{
                    hypot((it.x-wx).toDouble(),(it.y-wy).toDouble()) < 55/scale
                }

                // Ako nismo pogodili brod, jednim prstom pomičemo kartu.
                panning = selected==null
                return true
            }
            MotionEvent.ACTION_MOVE->{
                if(selected!=null){
                    selected?.let{s->
                        if(s.placed) s.placed=false // čim ga pomaknemo više nije zelen dok ga ponovno ne provjerimo
                        val dx=(e.x-lx)/scale
                        val dy=(e.y-ly)/scale
                        if(abs(dx)+abs(dy)>1f)movedSelected=true
                        s.x+=dx;s.y+=dy
                    }
                }else if(panning){
                    panX+=e.x-lx
                    panY+=e.y-ly
                    clampPan()
                }
                lx=e.x;ly=e.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP->{
                selected?.let{s->
                    if(!movedSelected && e.eventTime-down<300){
                        s.rot=(s.rot+90)%360
                    }else{
                        validatePlacement(s)
                    }
                }
                selected=null
                panning=false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun clampPan(){
        val maxX=width*(scale-1f)*0.85f
        val maxY=height*(scale-1f)*0.85f
        panX=panX.coerceIn(-maxX,maxX)
        panY=panY.coerceIn(-maxY,maxY)
    }

    private fun showInfo(){
        val now=SystemClock.elapsedRealtime()
        val waiting=ships.filter{it.accepted&&!it.placed&&!it.departed}
        val inPort=ships.filter{it.placed&&!it.departed}.sortedBy{it.departureAt}
        val departed=ships.filter{it.departed}

        val msg=buildString{
            append("KAPETAN: $captain\\n")
            append("VRIJEME: %02d:%02d     BODOVI: %d\\n\\n".format(secs/60,secs%60,score))

            append("⏳ ČEKA SMJEŠTAJ (${waiting.size})\\n")
            if(waiting.isEmpty()) append("Sve prihvaćeno je smješteno.\\n")
            waiting.forEach{append("• ${it.name} • ${it.length} m • ${it.target} • ${it.days} dana\\n")}

            append("\\n⚓ U LUCI / ODLASCI (${inPort.size})\\n")
            if(inPort.isEmpty()) append("Nema privezanih brodova.\\n")
            inPort.forEach{
                val left=max(0L,(it.departureAt-now+999)/1000)
                append("• ${it.name} • odlazi za %02d:%02d • ${it.target}\\n".format(left/60,left%60))
            }

            append("\\n✓ OTPLOVILI (${departed.size})\\n")
            departed.forEach{append("• ${it.name}\\n")}
        }

        AlertDialog.Builder(ctx)
            .setTitle("INFO • PREGLED LUKE")
            .setMessage(msg)
            .setPositiveButton("NASTAVI IGRU",null)
            .show()
    }

    private fun validatePlacement(s:Ship){
        val valid=when(s.target){
            "PONTON"->s.length<=8 && s.x in (pontX-40f)..(pontX+50f) && s.y in 115f..310f
            "DOK"->s.y>340f && s.x in (gatX-210f)..(gatX+75f)
            else->validBerth(s)
        }
        if(valid && !collides(s)){
            s.placed=true
            if(!s.scored){
                score+=reward
                s.scored=true
                s.placedAt=SystemClock.elapsedRealtime()
                s.departureAt=s.placedAt+s.days*daySeconds*1000L
                toast("${s.name}: +$reward • odlazak za ${s.days*daySeconds}s")
            }
        }else{
            s.placed=false
            toast("Nevaljana pozicija — brod nije privezan")
        }
    }

    private fun validBerth(s:Ship):Boolean{
        if(s.y in 105f..185f){
            if(s.x<westEnd){
                val idx=((s.x-18f)/((westEnd-18f)/14f)).toInt().coerceIn(0,13)
                val berth=14-idx
                val maxLen=when(berth){in 1..3->45;in 4..8->65;else->110}
                return s.length<=maxLen
            }
            if(s.x>gatX+30f)return s.length<=150
        }
        if(s.x in (gatX-45f)..(gatX+75f) && s.y in 115f..390f)return s.length<=120
        return false
    }

    private fun collides(s:Ship):Boolean=
        ships.any{o->
            o!==s && o.placed && !o.departed &&
            hypot((o.x-s.x).toDouble(),(o.y-s.y).toDouble()) < max(18.0,(o.length+s.length)*.34)
        }

    private fun checkDepartures(){
        val now=SystemClock.elapsedRealtime()
        val due=ships.filter{it.placed&&!it.departed&&it.departureAt>0&&now>=it.departureAt}
        due.forEach{s->
            // Jednostavna provjera blokade: vrlo blizak brod zadržava odlazak.
            val blocked=ships.any{o->
                o!==s && o.placed && !o.departed &&
                hypot((o.x-s.x).toDouble(),(o.y-s.y).toDouble()) < max(22.0,(o.length+s.length)*.30)
            }
            if(blocked){
                if((now-s.departureAt)<1500) toast("${s.name} treba isploviti, ali je blokirana!")
            }else{
                s.departed=true
                s.placed=false
                score+=50
                notifyDeparture("${s.name} napustila je luku. Prostor je slobodan. +50 bodova")
            }
        }
    }

    private fun notifyDeparture(msg:String){
        AlertDialog.Builder(ctx).setTitle("ISPlOVLJENJE BRODA").setMessage(msg)
            .setPositiveButton("U REDU",null).show()
    }

    private fun finishRound(){
        if(!running)return
        running=false
        val missing=ships.count{it.accepted&&!it.placed&&!it.departed}
        if(missing>0)score-=missing*failPenalty
        saveResult()
        AlertDialog.Builder(ctx).setTitle("KRAJ RUNDE")
            .setMessage("Vrijeme je isteklo.\nRezultat: $score\nNesmještenih brodova: $missing")
            .setPositiveButton("U REDU",null).show()
        invalidate()
    }

    private fun saveResult(){
        val old=prefs.getString("results","")?:""
        val row="$captain|$score|${System.currentTimeMillis()}"
        prefs.edit().putString("results",(row+"\n"+old).lines().take(10).joinToString("\n")).apply()
    }

    private fun showSettings(){
        val box=LinearLayout(ctx).apply{orientation=LinearLayout.VERTICAL;setPadding(35,10,35,0)}
        fun field(label:String,value:Int):EditText{
            val e=EditText(ctx).apply{hint=label;inputType=2;setText(value.toString())}
            box.addView(TextView(ctx).apply{text=label})
            box.addView(e)
            return e
        }
        val fTime=field("Trajanje runde (sekunde)",secs.coerceAtLeast(60))
        val fDay=field("Sekundi za 1 dan boravka",daySeconds)
        val fReward=field("Bodovi za smješten brod",reward)
        val fReject=field("Kazna odbijanja po brodu",rejectPenalty)
        val fFail=field("Kazna prihvaćen, a nesmješten",failPenalty)
        val fMove=field("Kazna premještanja",movePenalty)
        AlertDialog.Builder(ctx).setTitle("POSTAVKE").setView(box)
            .setPositiveButton("SPREMI"){_,_->
                secs=fTime.text.toString().toIntOrNull()?.coerceIn(60,3600)?:secs
                daySeconds=fDay.text.toString().toIntOrNull()?.coerceIn(2,120)?:daySeconds
                reward=fReward.text.toString().toIntOrNull()?.coerceIn(0,1000)?:reward
                rejectPenalty=fReject.text.toString().toIntOrNull()?.coerceIn(0,1000)?:rejectPenalty
                failPenalty=fFail.text.toString().toIntOrNull()?.coerceIn(0,2000)?:failPenalty
                movePenalty=fMove.text.toString().toIntOrNull()?.coerceIn(0,500)?:movePenalty
                invalidate()
            }.setNegativeButton("ODUSTANI",null).show()
    }

    private fun restart(){
        running=false
        score=1000
        secs=300
        accepted=false
        scale=1f;panX=0f;panY=0f
        ships.forEach{
            it.x=0f;it.y=0f;it.rot=0;it.accepted=false;it.placed=false
            it.scored=false;it.departed=false;it.placedAt=0;it.departureAt=0
        }
        askCaptain()
        invalidate()
    }

    private fun toast(s:String)=Toast.makeText(ctx,s,Toast.LENGTH_SHORT).show()

    override fun onDetachedFromWindow(){
        super.onDetachedFromWindow()
        h.removeCallbacksAndMessages(null)
    }
}
