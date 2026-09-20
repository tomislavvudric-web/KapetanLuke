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
    private var multiTouch=false
    private var multiMidX=0f
    private var multiMidY=0f
    private val hudH=150f

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
        val root=LinearLayout(ctx).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(28,22,28,18)
            background=rounded(Color.rgb(10,34,51),22f)
        }
        root.addView(TextView(ctx).apply{
            text="NOVA LUČKA PORUKA";textSize=23f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(ctx).apply{
            text="Adriatic Marine Services  •  Zahtjev za prihvat\nOdaberi svaki brod zasebno.";textSize=15f
            setTextColor(Color.rgb(170,205,219));setPadding(0,8,0,14)
        })
        val checks=mutableListOf<CheckBox>()
        ships.forEach{ship->
            val cb=CheckBox(ctx).apply{
                text="${ship.name}   •   ${ship.type}\n${ship.length} m   •   ${ship.target}   •   ${ship.days} dana"
                textSize=17f;setTextColor(Color.WHITE);isChecked=true;setPadding(6,8,4,8)
                buttonTintList=android.content.res.ColorStateList.valueOf(Color.rgb(37,180,196))
            }
            checks+=cb;root.addView(cb)
        }
        val scroll=ScrollView(ctx).apply{addView(root)}
        AlertDialog.Builder(ctx).setView(scroll)
            .setNegativeButton("ODBIJ SVE"){_,_->
                ships.forEach{it.accepted=false};score-=rejectPenalty*ships.size
                accepted=false;running=true;toast("Odbijeno ${ships.size} brodova: -${rejectPenalty*ships.size}")
            }
            .setPositiveButton("POTVRDI ODABIR"){_,_->
                var rejected=0
                ships.forEachIndexed{i,ship-> ship.accepted=checks[i].isChecked; if(!ship.accepted)rejected++ }
                if(rejected>0)score-=rejected*rejectPenalty
                accepted=ships.any{it.accepted}
                layoutWaitingShips();running=true
                toast("Prihvaćeno ${ships.count{it.accepted}} • odbijeno $rejected")
            }.setCancelable(false).show()
    }

    private fun layoutWaitingShips(){
        val base=height*0.78f
        ships.filter{it.accepted&&!it.departed}.forEachIndexed{i,s->
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

        // Moderni Harbour Control HUD - fizički odvojen od karte i nikad se ne zumira.
        p.color=Color.rgb(6,25,39);c.drawRect(0f,0f,W,hudH,p)
        p.color=Color.rgb(12,48,68);c.drawRoundRect(RectF(12f,12f,W-12f,138f),24f,24f,p)
        t.color=Color.rgb(130,202,220);t.textSize=14f;c.drawText("KAPETAN",28f,39f,t)
        t.color=Color.WHITE;t.textSize=24f;c.drawText(captain.take(18),28f,72f,t)
        t.color=Color.rgb(130,202,220);t.textSize=14f;c.drawText("VRIJEME",28f,101f,t)
        t.color=Color.WHITE;t.textSize=28f;c.drawText("%02d:%02d".format(secs/60,secs%60),28f,132f,t)
        t.color=Color.rgb(130,202,220);t.textSize=14f;c.drawText("BODOVI",W*.31f,101f,t)
        t.color=Color.WHITE;t.textSize=28f;c.drawText(score.toString(),W*.31f,132f,t)

        drawButton(c,RectF(W-330f,24f,W-222f,78f),"INFO")
        drawButton(c,RectF(W-212f,24f,W-94f,78f),"POSTAVKE")
        drawButton(c,RectF(W-330f,86f,W-94f,132f),"RESET IGRE")

        p.color=Color.argb(210,6,25,39);c.drawRoundRect(RectF(14f,H-42f,W-14f,H-8f),17f,17f,p)
        t.textSize=13f;t.color=Color.WHITE
        c.drawText("1 prst: brod  •  2 prsta: ZOOM + POMAK KARTE",28f,H-20f,t)
    }

    private fun drawButton(c:Canvas,r:RectF,label:String){
        p.color=Color.rgb(28,113,139)
        c.drawRoundRect(r,18f,18f,p)
        t.color=Color.WHITE;t.textSize=14f
        c.drawText(label,r.left+14,r.centerY()+5,t)
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
        val W=width.toFloat()

        // DVA PRSTA = ISKLJUČIVO KARTA. Brod se odmah otpušta.
        if(e.pointerCount>=2 || multiTouch){
            if(e.pointerCount>=2){
                selected=null;panning=false
                val midX=(e.getX(0)+e.getX(1))/2f
                val midY=(e.getY(0)+e.getY(1))/2f
                if(!multiTouch){multiTouch=true;multiMidX=midX;multiMidY=midY}
                else if(e.actionMasked==MotionEvent.ACTION_MOVE){
                    // pan po sredini dva prsta, istodobno sa pinch zoomom
                    panX+=midX-multiMidX;panY+=midY-multiMidY
                    multiMidX=midX;multiMidY=midY
                }
                scaleDetector.onTouchEvent(e)
                clampPan();invalidate();return true
            }else{
                // Nakon podizanja jednog prsta ne smije se slučajno uhvatiti brod.
                scaleDetector.onTouchEvent(e)
                if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_POINTER_UP || e.actionMasked==MotionEvent.ACTION_CANCEL){
                    multiTouch=false;selected=null;panning=false
                }
                invalidate();return true
            }
        }
        scaleDetector.onTouchEvent(e)
        if(!accepted && e.y>=hudH)return true
        val wx=screenToWorldX(e.x);val wy=screenToWorldY(e.y)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                if(e.y<hudH){
                    when{
                        e.x in (W-330f)..(W-222f) && e.y<82f -> {showInfo();return true}
                        e.x in (W-212f)..(W-94f) && e.y<82f -> {showSettings();return true}
                        e.x in (W-330f)..(W-94f) && e.y>=82f -> {confirmRestart();return true}
                    }
                    return true
                }
                down=e.eventTime;lx=e.x;ly=e.y;movedSelected=false
                selected=ships.filter{it.accepted&&!it.departed}.minByOrNull{
                    hypot((it.x-wx).toDouble(),(it.y-wy).toDouble())
                }?.takeIf{ hypot((it.x-wx).toDouble(),(it.y-wy).toDouble()) < 38/scale }
                // jedan prst na praznom prostoru može pomicati kartu
                panning=selected==null
                return true
            }
            MotionEvent.ACTION_MOVE->{
                selected?.let{ship->
                    if(ship.placed)ship.placed=false
                    val dx=(e.x-lx)/scale;val dy=(e.y-ly)/scale
                    if(abs(dx)+abs(dy)>1f)movedSelected=true
                    ship.x+=dx;ship.y+=dy
                } ?: run{
                    if(panning){panX+=e.x-lx;panY+=e.y-ly;clampPan()}
                }
                lx=e.x;ly=e.y;invalidate();return true
            }
            MotionEvent.ACTION_UP->{
                selected?.let{ship->
                    if(!movedSelected && e.eventTime-down<300)ship.rot=(ship.rot+90)%360 else validatePlacement(ship)
                }
                selected=null;panning=false;invalidate();return true
            }
            MotionEvent.ACTION_CANCEL->{selected=null;panning=false;return true}
        }
        return true
    }

    private fun clampPan(){
        val maxX=width*(scale-1f)*1.15f+120f
        val maxY=height*(scale-1f)*1.15f+120f
        panX=panX.coerceIn(-maxX,maxX);panY=panY.coerceIn(-maxY,maxY)
    }

    private fun showInfo(){
        val now=SystemClock.elapsedRealtime()
        val waiting=ships.filter{it.accepted&&!it.placed&&!it.departed}
        val inPort=ships.filter{it.placed&&!it.departed}.sortedBy{it.departureAt}
        val rejected=ships.filter{!it.accepted}
        val departed=ships.filter{it.departed}
        val root=LinearLayout(ctx).apply{orientation=LinearLayout.VERTICAL;setPadding(28,24,28,24);background=rounded(Color.rgb(7,29,44),24f)}
        fun title(x:String){root.addView(TextView(ctx).apply{text=x;textSize=22f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;setPadding(0,12,0,8)})}
        fun row(x:String,accent:Boolean=false){root.addView(TextView(ctx).apply{text=x;textSize=17f;setTextColor(if(accent)Color.rgb(111,210,198) else Color.rgb(224,239,244));setPadding(12,10,12,10);background=rounded(Color.rgb(13,48,67),14f)})}
        root.addView(TextView(ctx).apply{text="HARBOUR CONTROL";textSize=27f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD})
        root.addView(TextView(ctx).apply{text="$captain   •   %02d:%02d   •   $score bodova".format(secs/60,secs%60);textSize=17f;setTextColor(Color.rgb(132,205,226));setPadding(0,4,0,8)})
        title("ČEKA SMJEŠTAJ  ${waiting.size}")
        if(waiting.isEmpty())row("Sve prihvaćeno je smješteno ✓",true) else waiting.forEach{row("${it.name}  •  ${it.length} m  •  ${it.target}  •  ${it.days} dana")}
        title("U LUCI / ODLASCI  ${inPort.size}")
        if(inPort.isEmpty())row("Nema privezanih brodova") else inPort.forEach{
            val left=max(0L,(it.departureAt-now+999)/1000);row("${it.name}  •  odlazak za %02d:%02d  •  ${it.target}".format(left/60,left%60),left<30)
        }
        title("ODBIJENI  ${rejected.size}");if(rejected.isEmpty())row("Nema odbijenih brodova") else rejected.forEach{row("${it.name}  •  ${it.length} m")}
        title("OTPLOVILI  ${departed.size}");if(departed.isEmpty())row("Još nema isplovljenja") else departed.forEach{row("${it.name} ✓",true)}
        val scroll=ScrollView(ctx).apply{addView(root)}
        AlertDialog.Builder(ctx).setView(scroll).setPositiveButton("NASTAVI IGRU",null).show().also{d->
            d.window?.setLayout((resources.displayMetrics.widthPixels*.94).toInt(),(resources.displayMetrics.heightPixels*.88).toInt())
        }
    }

    private fun validatePlacement(s:Ship){
        val reason=placementProblem(s)
        if(reason==null){
            s.placed=true
            if(!s.scored){
                score+=reward;s.scored=true;s.placedAt=SystemClock.elapsedRealtime();s.departureAt=s.placedAt+s.days*daySeconds*1000L
                toast("${s.name}: +$reward • uspješno privezan")
            }
        }else{
            s.placed=false
            AlertDialog.Builder(ctx).setTitle("BROD NIJE PRIVEZAN").setMessage("${s.name} • ${s.length} m\n\n$reason")
                .setPositiveButton("U REDU",null).show()
        }
    }

    private fun placementProblem(s:Ship):String?{
        if(collides(s))return "⛔ Mjesto je zauzeto drugim brodom."
        if(s.target=="PONTON"){
            if(s.type!="Tender" || s.length>8)return "⛔ Ponton je samo za tendere do 8 m."
            if(!(s.x in (pontX-40f)..(pontX+50f) && s.y in 115f..310f))return "⛔ Brod nije postavljen uz ponton."
            return null
        }
        if(s.target=="DOK"){
            if(!(s.y>340f && s.x in (gatX-210f)..(gatX+75f)))return "⛔ Nalog traži DOK. Postavi brod u jedan od plutajućih dokova."
            val maxDock=when{ s.x<gatX-100f->85;s.x<gatX-35f->60;else->140 }
            if(s.length>maxDock)return "⛔ Odabrani dok ima $maxDock m, a brod ${s.length} m."
            return null
        }
        if(s.y in 105f..185f && s.x<westEnd){
            val idx=((s.x-18f)/((westEnd-18f)/14f)).toInt().coerceIn(0,13);val berth=14-idx
            val maxLen=when(berth){in 1..3->45;in 4..8->65;else->110}
            if(s.length>maxLen)return "⛔ Vez $berth prima brodove do $maxLen m, a brod ima ${s.length} m.\nPokušaj odgovarajući veći vez."
            return null
        }
        if(s.y in 105f..185f && s.x>gatX+30f){if(s.length>150)return "⛔ Istočna obala ima 150 m raspoložive dužine.";return null}
        if(s.x in (gatX-45f)..(gatX+75f) && s.y in 115f..390f){if(s.length>120)return "⛔ Gat ima 120 m korisne dužine po strani.";return null}
        return "⛔ Brod nije dovoljno blizu valjanog veza, gata ili istočne obale."
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
        AlertDialog.Builder(ctx).setTitle("ISPLOVLJENJE BRODA").setMessage(msg)
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

    private fun confirmRestart(){
        AlertDialog.Builder(ctx).setTitle("RESET IGRE")
            .setMessage("Želiš prekinuti trenutnu partiju i vratiti se na početak?\n\nSpremljeni najbolji rezultati ostaju sačuvani.")
            .setNegativeButton("ODUSTANI",null).setPositiveButton("RESETIRAJ"){_,_->restart()}.show()
    }

    private fun rounded(color:Int,radius:Float)=android.graphics.drawable.GradientDrawable().apply{
        setColor(color);cornerRadius=radius
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
