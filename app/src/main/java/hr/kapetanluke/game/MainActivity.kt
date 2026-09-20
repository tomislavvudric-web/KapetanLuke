package hr.kapetanluke.game

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.*

data class Ship(
    val name:String, val type:String, val length:Int, val days:Int, val target:String,
    var x:Float=0f, var y:Float=0f, var rot:Int=0, var accepted:Boolean=false,
    var placed:Boolean=false, var scored:Boolean=false, var departed:Boolean=false,
    var placedAt:Long=0L, var departureAt:Long=0L, var berthDeadlineAt:Long=0L
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
    private var multiDist=0f
    private var lastTouchWasMulti=false
    private var longPressArmed=false
    private var longPressShip:Ship?=null
    private var longPressStart=0L
    private var nextWaveAt=0L
    private var waveNo=1
    private var pendingWave=false
    private var pendingShips=mutableListOf<Ship>()
    private val longPressMs=550L
    private val hudH=250f
    private var tts:TextToSpeech?=null
    private val autoMoving=mutableSetOf<String>()
    private val safetyGapM=3

    private val catalog=listOf(
        Ship("MY Aurora","Jahta",82,6,"VEZ"), Ship("MY Solis","Jahta",44,4,"VEZ"),
        Ship("MY Adriana","Jahta",63,5,"VEZ"), Ship("MY Bellissima","Jahta",108,8,"VEZ"),
        Ship("Ocean Pioneer","Supply",54,10,"DOK"), Ship("North Wind","Supply",72,7,"VEZ"),
        Ship("Blue Star","Katamaran",42,3,"VEZ"), Ship("Dalmatia Jet","Katamaran",38,4,"VEZ"),
        Ship("Tender 07","Tender",8,4,"PONTON"), Ship("Tender Luna","Tender",7,2,"PONTON"),
        Ship("Tender Bravo","Tender",6,3,"PONTON"), Ship("Adriatic Bulk","Rasuti teret",104,12,"VEZ"),
        Ship("Marjan Trader","Rasuti teret",96,9,"VEZ"), Ship("Jadran Ferry","Trajekt",88,5,"VEZ"),
        Ship("Trogir Ferry","Trajekt",64,4,"VEZ"), Ship("Orkan","Vojni",76,6,"VEZ"),
        Ship("Patrol 21","Vojni",45,4,"VEZ"), Ship("Petrol Mare","Tanker",109,11,"VEZ"),
        Ship("Sea Falcon","Offshore",58,7,"DOK"), Ship("Explorer One","Jahta",132,9,"DOK")
    )
    private val ships=mutableListOf<Ship>()

    private fun newShipSet(){
        ships.clear()
        val chosen=catalog.shuffled().take(7)
        chosen.forEach{q->ships.add(q.copy())}
    }

    private val h=Handler(Looper.getMainLooper())
    private val tick=object:Runnable{
        override fun run(){
            if(running){
                if(secs>0) secs--
                checkDepartures()
                checkIncomingWave()
                if(secs<=0){secs=300;if(!pendingWave)nextWaveAt=SystemClock.elapsedRealtime()}
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
        tts=TextToSpeech(ctx){status->
            if(status==TextToSpeech.SUCCESS){
                val hr=Locale("hr","HR")
                val result=tts?.setLanguage(hr)
                if(result==TextToSpeech.LANG_MISSING_DATA || result==TextToSpeech.LANG_NOT_SUPPORTED){
                    tts?.language=Locale.getDefault()
                }
                tts?.setSpeechRate(0.92f)
            }
        }
        h.post(tick)
        newShipSet()
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
                val firstName=captain.split(" ").firstOrNull()?.ifBlank{"kapetane"}?:"kapetane"
                tts?.speak("Dobro došao kapetane $firstName. Krenimo na posao vezivanja brodova. Sretno!",TextToSpeech.QUEUE_FLUSH,null,"welcome")
                toast("Pozdrav, kapetane $firstName! ⚓")
                nextWaveAt=SystemClock.elapsedRealtime()+30000L
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
                ships.forEachIndexed{i,ship->
                    ship.accepted=checks[i].isChecked
                    if(ship.accepted) ship.berthDeadlineAt=SystemClock.elapsedRealtime()+max(25,ship.days*daySeconds)*1000L
                    else rejected++
                }
                if(rejected>0)score-=rejected*rejectPenalty
                accepted=ships.any{it.accepted}
                layoutWaitingShips();running=true
                toast("Prihvaćeno ${ships.count{it.accepted}} • odbijeno $rejected")
            }.setCancelable(false).show()
    }

    private fun layoutWaitingShips(){
        val base=500f
        ships.filter{it.accepted&&!it.departed}.forEachIndexed{i,s->
            s.x=120f+(i%4)*72f
            s.y=base+(i/4)*58f
        }
    }

    override fun onDraw(c:Canvas){
        super.onDraw(c)
        if(width==0)return
        val W=width.toFloat(); val H=height.toFloat()

        c.drawColor(Color.rgb(8,70,101))
        // diskretne linije mora daju moderniji osjećaj dubine
        p.color=Color.argb(35,210,240,248);p.strokeWidth=1f
        for(yy in 280..height step 70) c.drawLine(0f,yy.toFloat(),W,yy.toFloat(),p)

        c.save()
        c.translate(panX,panY)
        c.scale(scale,scale)

        val worldW=W
        // v0.8.5: geometrija prema stvarnim mjerama Adria Docks.
        // Jedna skala za obalu, gat, ponton, dokove i brodove.
        val shoreStart=18f
        val metresToPx=((worldW*.58f)-shoreStart)/268f
        westEnd=shoreStart+268f*metresToPx
        pontX=westEnd-10f*metresToPx
        gatX=westEnd+28f*metresToPx

        // Zapadna obala 268 m. Kote se namjerno ne crtaju na karti.
        p.color=Color.rgb(173,164,139)
        c.drawRect(0f,68f,westEnd+20f,118f,p)

        val bw=(westEnd-shoreStart)/14f
        t.color=Color.rgb(22,46,52); t.textSize=13f
        for(i in 0 until 14){
            val n=14-i
            val x=shoreStart+i*bw
            c.drawLine(x,110f,x,126f,p)
            c.drawText("V$n",x+1,91f,t)
        }

        // Ponton 30 m, P1-P6. Tenderi: krma lijevo, pramac desno.
        p.color=Color.rgb(102,118,121)
        val pontLen=30f*metresToPx
        c.drawRect(pontX,118f,pontX+14f,118f+pontLen,p)
        t.textSize=10f;c.drawText("P1–P6",pontX-7f,106f,t)
        for(k in 0 until 6){c.drawText("P${k+1}",pontX+18f,132f+k*(pontLen/6f),t)}

        // Gat: gornjih 32 m lijevo je prolaz za tendere bez vezivanja.
        // Korisni vezovi: lijevo 125 m, desno 110 m.
        val gatTop=118f
        val shallow32=32f*metresToPx
        val gatLeft125=125f*metresToPx
        val gatRight110=110f*metresToPx
        val gatBottom=gatTop+shallow32+gatLeft125
        c.drawRect(gatX,gatTop,gatX+28f,gatBottom,p)
        t.textSize=11f;c.drawText("G1",gatX+5f,106f,t)
        p.color=Color.argb(90,220,235,238)
        c.drawRect(gatX-7f,gatTop,gatX,gatTop+shallow32,p)

        // Kosi dio / istočna obala 188 m, nacrtan dijagonalno kao na referentnoj slici.
        p.color=Color.rgb(173,164,139);p.strokeWidth=24f
        val eastX2=min(worldW-18f,gatX+188f*metresToPx*.78f)
        val eastY2=gatTop+188f*metresToPx*.48f
        c.drawLine(gatX+28f,gatTop+4f,eastX2,eastY2,p)
        p.strokeWidth=1f
        t.textSize=11f;c.drawText("I",gatX+38f,gatTop+26f,t)

        // Tri plutajuća doka, sjever-jug: D1 85 m, D2 60 m, D3 140 m.
        p.color=Color.rgb(83,101,108)
        val dockTop=gatBottom+30f
        val d1x=gatX-145f; val d2x=gatX-72f; val d3x=gatX+48f
        c.drawRoundRect(RectF(d1x,dockTop,d1x+28f,dockTop+85f*metresToPx),5f,5f,p)
        c.drawRoundRect(RectF(d2x,dockTop+25f,d2x+28f,dockTop+25f+60f*metresToPx),5f,5f,p)
        c.drawRoundRect(RectF(d3x,dockTop,d3x+28f,dockTop+140f*metresToPx),5f,5f,p)
        t.color=Color.WHITE;t.textSize=11f
        c.drawText("D1",d1x+4f,dockTop-7f,t);c.drawText("D2",d2x+4f,dockTop+18f,t);c.drawText("D3",d3x+4f,dockTop-7f,t)

        if(accepted) ships.filter{!it.departed}.forEach{drawShip(c,it)}
        c.restore()

        // HARBOUR CONTROL: velik, čitljiv i potpuno izvan zooma karte.
        p.color=Color.rgb(5,18,30);c.drawRect(0f,0f,W,hudH,p)
        p.color=Color.rgb(10,42,61);c.drawRoundRect(RectF(14f,14f,W-14f,hudH-16f),30f,30f,p)

        t.color=Color.rgb(104,207,221);t.textSize=18f;c.drawText("⚓  KAPETAN LUKE",30f,48f,t)
        t.color=Color.WHITE;t.textSize=30f;c.drawText(captain.take(20),30f,86f,t)

        // velike brojke u zasebnim karticama
        p.color=Color.rgb(7,30,46);c.drawRoundRect(RectF(28f,106f,W*.30f,218f),22f,22f,p)
        t.color=Color.rgb(116,194,214);t.textSize=16f;c.drawText("VRIJEME",44f,137f,t)
        t.color=Color.WHITE;t.textSize=42f;c.drawText("%02d:%02d".format(secs/60,secs%60),44f,190f,t)

        p.color=Color.rgb(7,30,46);c.drawRoundRect(RectF(W*.32f,106f,W*.55f,218f),22f,22f,p)
        t.color=Color.rgb(116,194,214);t.textSize=16f;c.drawText("BODOVI",W*.34f,137f,t)
        t.color=Color.WHITE;t.textSize=42f;c.drawText(score.toString(),W*.34f,190f,t)

        drawButton(c,RectF(W*.58f,30f,W-24f,92f),"INFO  •  PREGLED LUKE")
        drawButton(c,RectF(W*.58f,106f,W*.78f,166f),"POSTAVKE")
        drawButton(c,RectF(W*.80f,106f,W-24f,166f),"RESET")
        t.color=Color.rgb(174,213,224);t.textSize=14f
        c.drawText("2 prsta = ZOOM + POMAK KARTE",W*.59f,205f,t)
        c.drawText("1 prst na brodu = PREMJEŠTANJE",W*.59f,228f,t)

        p.color=Color.argb(220,5,18,30);c.drawRoundRect(RectF(14f,H-46f,W-14f,H-8f),18f,18f,p)
        t.textSize=14f;t.color=Color.WHITE
        c.drawText("Približi kartu s 2 prsta, namjesti vez, zatim uhvati brod s 1 prstom.",28f,H-21f,t)
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
        val order=if(!s.placed) " → ${shortTarget(s)}" else ""
        c.drawText("${s.name} ${s.length}m$order$suffix",s.x-len/2,s.y+wid+15f,t)
    }

    private fun shortTarget(s:Ship):String=when(s.target){"DOK"->"D1/D2/D3";"PONTON"->"P1–P6";else->"V / I / G"}
    private fun screenToWorldX(x:Float)=(x-panX)/scale
    private fun screenToWorldY(y:Float)=(y-panY)/scale

    override fun onTouchEvent(e:MotionEvent):Boolean{
        val W=width.toFloat()

        // HUD je uvijek odvojen od karte.
        if(e.actionMasked==MotionEvent.ACTION_DOWN && e.y<hudH){
            when{
                e.x>=W*.58f && e.y<100f -> {showInfo();return true}
                e.x in (W*.58f)..(W*.78f) && e.y in 100f..180f -> {showSettings();return true}
                e.x>=W*.78f && e.y in 100f..180f -> {confirmRestart();return true}
            }
            return true
        }

        // DVA PRSTA: vlastiti gesture engine. Nikad ne dira brod.
        if(e.pointerCount>=2){
            selected=null;panning=false;lastTouchWasMulti=true
            val x0=e.getX(0); val y0=e.getY(0); val x1=e.getX(1); val y1=e.getY(1)
            val midX=(x0+x1)/2f; val midY=(y0+y1)/2f
            val dist=hypot((x1-x0).toDouble(),(y1-y0).toDouble()).toFloat().coerceAtLeast(10f)

            if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN || !multiTouch){
                multiTouch=true;multiMidX=midX;multiMidY=midY;multiDist=dist
            }else if(e.actionMasked==MotionEvent.ACTION_MOVE){
                // svjetska točka ispod sredine prstiju prije promjene
                val worldX=(multiMidX-panX)/scale
                val worldY=(multiMidY-panY)/scale
                val factor=(dist/multiDist).coerceIn(.75f,1.35f)
                val newScale=(scale*factor).coerceIn(1f,5f)
                scale=newScale
                // ista svjetska točka ostaje ispod nove sredine -> zoom i pan rade zajedno
                panX=midX-worldX*scale
                panY=midY-worldY*scale
                multiMidX=midX;multiMidY=midY;multiDist=dist
                clampPan();invalidate()
            }
            return true
        }

        // Nakon pinch geste preostali prst ne smije slučajno uhvatiti brod.
        if(lastTouchWasMulti){
            if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL){
                lastTouchWasMulti=false;multiTouch=false
            }
            return true
        }

        if(!accepted)return true
        val wx=screenToWorldX(e.x);val wy=screenToWorldY(e.y)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                down=e.eventTime;lx=e.x;ly=e.y;movedSelected=false
                val hit=ships.filter{it.accepted&&!it.departed&&it.name !in autoMoving}.minByOrNull{
                    hypot((it.x-wx).toDouble(),(it.y-wy).toDouble())
                }?.takeIf{hypot((it.x-wx).toDouble(),(it.y-wy).toDouble()) < 34/scale}
                if(hit?.placed==true){
                    selected=null;longPressShip=hit;longPressStart=e.eventTime;longPressArmed=true;panning=false
                }else{
                    selected=hit;longPressShip=null;longPressArmed=false;panning=selected==null
                }
                return true
            }
            MotionEvent.ACTION_MOVE->{
                if(longPressArmed){
                    val held=e.eventTime-longPressStart
                    val drift=hypot((e.x-lx).toDouble(),(e.y-ly).toDouble())
                    if(held>=longPressMs && drift<42){
                        val ship=longPressShip
                        longPressArmed=false; longPressShip=null; selected=null
                        ship?.let{showChangeBerth(it)}
                    }else if(drift>=42){longPressArmed=false;longPressShip=null}
                }
                selected?.let{ship->
                    val dx=(e.x-lx)/scale;val dy=(e.y-ly)/scale
                    if(abs(dx)+abs(dy)>1f)movedSelected=true
                    val oldX=ship.x;val oldY=ship.y
                    ship.x+=dx;ship.y+=dy
                    if(hullTouchesLand(ship)){ship.x=oldX;ship.y=oldY}
                }
                if(selected==null && panning){panX+=e.x-lx;panY+=e.y-ly;clampPan()}
                lx=e.x;ly=e.y;invalidate();return true
            }
            MotionEvent.ACTION_UP->{
                if(longPressArmed){longPressArmed=false;longPressShip=null;return true}
                selected?.let{ship->
                    if(!movedSelected && !ship.placed && e.eventTime-down<300)showMooringCommand(ship)
                    else if(movedSelected){
                        val wasPlaced=ship.placed
                        validatePlacement(ship)
                        if(wasPlaced && ship.placed)score-=movePenalty
                    }
                }
                selected=null;longPressShip=null;panning=false;invalidate();return true
            }
            MotionEvent.ACTION_CANCEL->{selected=null;panning=false;return true}
        }
        return true
    }

    private fun showMooringCommand(ship:Ship){
        if(ship.departed||!ship.accepted||ship.placed||ship.name in autoMoving)return
        val input=EditText(ctx).apply{hint="V5 / D1 / I / G1 / P2";textSize=22f;setSingleLine(true);setPadding(22,18,22,18)}
        val box=LinearLayout(ctx).apply{
            orientation=LinearLayout.VERTICAL;setPadding(34,16,34,4)
            addView(TextView(ctx).apply{text="${ship.name} • ${ship.type} • ${ship.length} m\nNalog: ${ship.target}";textSize=19f})
            addView(TextView(ctx).apply{text="V1–V14 = zapad • D1–D3 = dokovi\nI = istok • G1 = gat • P1–P6 = ponton";textSize=15f;setPadding(0,12,0,10)})
            addView(input)
        }
        AlertDialog.Builder(ctx).setTitle("⚓ ODABERI VEZ").setView(box)
            .setPositiveButton("VEŽI BROD"){_,_->executeMooringCommand(ship,input.text.toString())}
            .setNeutralButton("OKRENI 90°"){_,_->ship.rot=(ship.rot+90)%360;invalidate()}
            .setNegativeButton("RUČNO",null).show()
    }
    private fun showChangeBerth(ship:Ship){
        if(ship.departed || !ship.placed)return
        val current=locationCode(ship)
        val input=EditText(ctx).apply{hint="V5 / D1 / I / G1 / P2";textSize=22f;setSingleLine(true);setPadding(22,18,22,18)}
        val box=LinearLayout(ctx).apply{
            orientation=LinearLayout.VERTICAL;setPadding(34,16,34,8)
            addView(TextView(ctx).apply{text="${ship.name} • ${ship.length} m\nTrenutno: $current";textSize=19f})
            addView(TextView(ctx).apply{text="Odaberi novi vez. Brod će se sam okrenuti i namjestiti.\nV1–V14 • D1–D3 • I • G1 • P1–P6";textSize=15f;setPadding(0,12,0,10)})
            addView(input)
        }
        AlertDialog.Builder(ctx).setTitle("⚓ PROMIJENI VEZ").setView(box)
            .setPositiveButton("PREMJESTI"){_,_->
                val oldX=ship.x;val oldY=ship.y;val oldRot=ship.rot;val oldPlaced=ship.placed
                ship.placed=false
                val before=autoMoving.size
                executeMooringCommand(ship,input.text.toString())
                if(ship.name !in autoMoving){ship.x=oldX;ship.y=oldY;ship.rot=oldRot;ship.placed=oldPlaced}
                else score-=movePenalty
            }
            .setNegativeButton("ODUSTANI",null).show()
    }

    private fun executeMooringCommand(ship:Ship,raw:String){
        val cmd=raw.trim().uppercase(Locale("hr","HR")).replace(" ","")
        if(cmd.isBlank()){toast("Upiši V5, D1, I, G1 ili P2");return}
        val start=18f;val bw=(westEnd-start)/14f
        val target:Triple<Float,Float,Int>?=when{
            Regex("V(1[0-4]|[1-9])").matches(cmd)->{
                val n=cmd.drop(1).toInt();val ml=when(n){in 1..3->45;in 4..8->65;else->110}
                if(ships.any{it!==ship&&it.placed&&!it.departed&&berthNumber(it)==n}){toast("V$n je zauzet");null}
                else if(ship.length>ml){toast("V$n prima do $ml m");null}
                else Triple(start+(14-n+.5f)*bw,118f+max(18f,ship.length*.525f)+5f,270)
            }
            Regex("D[123]").matches(cmd)->dockAutoTarget(ship,cmd.drop(1).toInt())
            cmd=="I"||cmd=="I1"->eastAutoTarget(ship)
            cmd=="G"||cmd=="G1"->gatAutoTarget(ship)
            Regex("P[1-6]").matches(cmd)->{
                val n=cmd.drop(1).toInt()
                if(ship.type!="Tender"||ship.length>8){toast("Ponton je za tendere do 8 m");null}
                else if(ships.any{it!==ship&&it.placed&&!it.departed&&pontoonSlot(it)==n}){toast("P$n je zauzet");null}
                else Triple(pontX+30f,132f+(n-1)*22f,0)
            }
            else->null
        }
        if(target==null){toast("Naredba: V5 / D1 / I / G1 / P2");return}
        animateShipTo(ship,target.first,target.second,target.third)
    }
    private fun berthNumber(s:Ship):Int{
        if(!(s.y in 105f..260f&&s.x<westEnd))return 0
        return 14-((s.x-18f)/((westEnd-18f)/14f)).toInt().coerceIn(0,13)
    }
    private fun pontoonSlot(s:Ship):Int{
        if(s.x !in (pontX-45f)..(pontX+60f))return 0
        return (((s.y-132f)/22f).roundToInt()+1).coerceIn(1,6)
    }
    private fun dockAutoTarget(ship:Ship,n:Int):Triple<Float,Float,Int>?{
        val cap=when(n){1->85;2->60;3->140;else->return null}
        val used=ships.filter{it!==ship&&it.placed&&!it.departed&&dockCommandId(it)==n}.sumOf{it.length+safetyGapM}
        if(used+ship.length>cap){toast("D$n: slobodno ${max(0,cap-used)} m");return null}
        val x=when(n){1->gatX-134f;2->gatX-66f;else->gatX+15f};val y0=when(n){1->405f;2->450f;else->405f}
        return Triple(x,y0+(used+ship.length/2f)*1.05f,90)
    }
    private fun eastAutoTarget(ship:Ship):Triple<Float,Float,Int>?{
        val used=ships.filter{it!==ship&&it.placed&&!it.departed&&isEast(it)}.sumOf{it.length+safetyGapM}
        if(used+ship.length>188){toast("ISTOK: slobodno ${max(0,188-used)} m");return null}
        val ppm=((width.toFloat()-(gatX+30f)-20f)/188f).coerceAtLeast(.8f)
        return Triple(gatX+30f+(used+ship.length/2f)*ppm,145f,0)
    }
    private fun gatAutoTarget(ship:Ship):Triple<Float,Float,Int>?{
        val l=ships.filter{it!==ship&&it.placed&&!it.departed&&isGat(it)&&it.x<gatX+15f}.sumOf{it.length+safetyGapM}
        val r=ships.filter{it!==ship&&it.placed&&!it.departed&&isGat(it)&&it.x>=gatX+15f}.sumOf{it.length+safetyGapM}
        val left=when{l+ship.length<=125&&r+ship.length<=110->l<=r;l+ship.length<=125->true;r+ship.length<=110->false;else->{toast("GAT: nema mjesta za ${ship.length} m");return null}}
        val used=if(left)l else r
        val baseY=if(left)150f+32f*1.05f else 150f
        return Triple(if(left)gatX-12f else gatX+42f,baseY+(used+ship.length/2f)*1.05f,90)
    }
    private fun isEast(s:Ship)=s.y in 118f..190f&&s.x>gatX+30f
    private fun isGat(s:Ship)=s.x in (gatX-50f)..(gatX+80f)&&s.y in 118f..390f
    private fun dockCommandId(s:Ship)=when{s.x in (gatX-170f)..(gatX-100f)->1;s.x in (gatX-100f)..(gatX-35f)->2;s.x in (gatX-35f)..(gatX+70f)->3;else->0}

    private fun animateShipTo(ship:Ship,tx:Float,ty:Float,trot:Int){
        if(ship.name in autoMoving)return
        autoMoving.add(ship.name)
        val sx=ship.x;val sy=ship.y
        // Prvo prema sigurnoj zoni mora, zatim prema odredištu. Time brod ne presijeca obalu.
        val safeY=max(330f,sy)
        val points=listOf(Pair(sx,safeY),Pair(tx,safeY),Pair(tx,ty))
        var leg=0
        fun runLeg(){
            if(leg>=points.size){
                ship.x=tx;ship.y=ty;ship.rot=trot
                autoMoving.remove(ship.name)
                validatePlacement(ship)
                invalidate();return
            }
            val ax=ship.x;val ay=ship.y;val bx=points[leg].first;val by=points[leg].second
            var step=0;val steps=18
            val r=object:Runnable{
                override fun run(){
                    step++
                    val q=step.toFloat()/steps
                    ship.x=ax+(bx-ax)*q;ship.y=ay+(by-ay)*q
                    invalidate()
                    if(step<steps)h.postDelayed(this,24) else{leg++;runLeg()}
                }
            }
            h.post(r)
        }
        toast("${ship.name} • izvršavam naredbu")
        runLeg()
    }

    private fun clampPan(){
        val maxX=width*(scale-1f)*1.15f+120f
        val maxY=height*(scale-1f)*1.15f+120f
        panX=panX.coerceIn(-maxX,maxX);panY=panY.coerceIn(-maxY,maxY)
    }

    private fun checkIncomingWave(){
        if(!accepted || pendingWave || nextWaveAt==0L)return
        if(SystemClock.elapsedRealtime()>=nextWaveAt){
            waveNo++
            val activeNames=ships.filter{!it.departed}.map{it.name}.toSet()
            pendingShips=catalog.filter{it.name !in activeNames}.shuffled().take((3..5).random()).map{it.copy()}.toMutableList()
            pendingWave=true
            toast("NOVI ZAHTJEV • ${pendingShips.size} BRODOVA")
            showIncomingWave()
        }
    }

    private fun showIncomingWave(){
        if(!pendingWave || pendingShips.isEmpty())return
        val names=pendingShips.mapIndexed{i,b->"${i+1}. ${b.name} • ${b.type} • ${b.length} m • ${b.target} • ${b.days} dana"}.toTypedArray()
        val checked=BooleanArray(pendingShips.size){true}
        AlertDialog.Builder(ctx)
            .setTitle("NOVI ZAHTJEV • TURA $waveNo")
            .setMultiChoiceItems(names,checked){_,which,isChecked->checked[which]=isChecked}
            .setMessage("Označeni brodovi bit će prihvaćeni. Makni kvačicu s brodova koje želiš odbiti.")
            .setPositiveButton("POTVRDI ODLUKU"){_,_->
                pendingShips.forEachIndexed{i,b->
                    b.accepted=checked[i]
                    if(checked[i]){
                        b.x=120f+(i%4)*72f;b.y=500f+(i/4)*58f
                        b.berthDeadlineAt=SystemClock.elapsedRealtime()+max(25,b.days*daySeconds)*1000L
                        ships.add(b)
                    }else score-=rejectPenalty
                }
                pendingShips.clear();pendingWave=false
                nextWaveAt=SystemClock.elapsedRealtime()+40000L
                invalidate()
            }
            .setNegativeButton("KASNIJE",null)
            .show()
    }

    private fun showInfo(){
        val now=SystemClock.elapsedRealtime()
        val waiting=ships.filter{it.accepted&&!it.placed&&!it.departed}
        val inPort=ships.filter{it.accepted&&it.placed&&!it.departed}.sortedBy{it.departureAt}
        val scroll=ScrollView(ctx)
        val root=LinearLayout(ctx).apply{orientation=LinearLayout.VERTICAL;setPadding(28,22,28,26);setBackgroundColor(Color.rgb(7,27,42))}
        fun title(x:String)=TextView(ctx).apply{text=x;textSize=20f;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD;setPadding(0,14,0,8)}
        fun line(x:String)=TextView(ctx).apply{text=x;textSize=16f;setTextColor(Color.rgb(190,222,232));setPadding(12,8,12,8)}
        fun shipRow(prefix:String,q:Ship?):View{
            return TextView(ctx).apply{
                text=if(q==null)"$prefix   🟢 SLOBODAN" else "$prefix   🔴 ${q.name} • ${q.length} m • ${timeLeft(q,now)}"
                textSize=16f;setTextColor(if(q==null)Color.rgb(130,225,170) else Color.WHITE)
                setPadding(14,10,14,10);setBackgroundColor(Color.rgb(13,45,64))
                if(q!=null)setOnClickListener{showChangeBerth(q)}
            }
        }
        root.addView(title("⚓ INFO O VEZOVIMA"))
        val next=if(pendingWave)"NOVI ZAHTJEV ČEKA" else if(nextWaveAt>now)"za ${(nextWaveAt-now)/1000} s" else "USKORO"
        root.addView(line("U luci: ${inPort.size}   •   Čeka vez: ${waiting.size}   •   Novi zahtjev: $next"))
        root.addView(line("Dodirni zauzeti vez/brod za PROMJENU VEZA."))
        root.addView(title("ZAPADNA OBALA • V1–V14"))
        for(n in 1..14)root.addView(shipRow("V$n",inPort.firstOrNull{berthNumber(it)==n}))
        root.addView(title("PONTON • P1–P6"))
        for(n in 1..6)root.addView(shipRow("P$n",inPort.firstOrNull{pontoonSlot(it)==n && it.target=="PONTON"}))
        root.addView(title("GAT • G1"))
        val gat=inPort.filter{isGat(it)};val gatUsed=gat.sumOf{it.length};root.addView(line("G1   ${gatUsed}/240 m • slobodno ${max(0,240-gatUsed)} m • ${gat.size} brodova"));gat.forEach{root.addView(shipRow("↳",it))}
        root.addView(title("ISTOČNA OBALA • I"))
        val east=inPort.filter{isEast(it)};val eastUsed=east.sumOf{it.length};root.addView(line("I   ${eastUsed}/150 m • slobodno ${max(0,150-eastUsed)} m • ${east.size} brodova"));east.forEach{root.addView(shipRow("↳",it))}
        root.addView(title("PLUTAJUĆI DOKOVI"))
        for(n in 1..3){val cap=when(n){1->85;2->60;else->140};val ds=inPort.filter{dockCommandId(it)==n};val used=ds.sumOf{it.length};root.addView(line("D$n   $used/$cap m • slobodno ${max(0,cap-used)} m • ${ds.size} brodova"));ds.forEach{root.addView(shipRow("↳",it))}}
        root.addView(title("ČEKA SMJEŠTAJ"));if(waiting.isEmpty())root.addView(line("✓ Nema brodova na čekanju")) else waiting.forEach{q->root.addView(TextView(ctx).apply{text="• ${q.name} • ${q.length} m • ${shortTarget(q)}";textSize=16f;setTextColor(Color.WHITE);setPadding(14,10,14,10);setOnClickListener{showMooringCommand(q)}})}
        scroll.addView(root)
        val d=Dialog(ctx);d.setContentView(scroll);d.show();d.window?.setLayout((resources.displayMetrics.widthPixels*.96f).toInt(),(resources.displayMetrics.heightPixels*.90f).toInt())
    }
    private fun timeLeft(s:Ship,now:Long):String{val q=max(0L,(s.departureAt-now+999)/1000);return "odlazak %02d:%02d".format(q/60,q%60)}
    private fun locationCode(s:Ship):String{val v=berthNumber(s);if(v>0)return "V$v";val d=dockCommandId(s);if(d>0)return "D$d";if(isEast(s))return "I";if(isGat(s))return "G1";if(s.target=="PONTON")return "P${pontoonSlot(s)}";return s.target}


    private fun hullTouchesLand(s:Ship):Boolean{
        val halfLong=max(18f,s.length*0.46f)
        val halfWide=max(7f,min(16f,s.length*0.10f))
        val hx=if(s.rot%180==0)halfLong else halfWide
        val hy=if(s.rot%180==0)halfWide else halfLong
        val left=s.x-hx;val right=s.x+hx;val top=s.y-hy
        if(top<118f)return true
        if(left<22f)return true
        val worldW=width.toFloat().coerceAtLeast(800f)
        if(right>worldW-22f)return true
        return false
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
            toast("${s.name}: ${reason.replace("⛔ ","").lineSequence().first()}")
        }
    }

    private fun placementProblem(s:Ship):String?{
        if(hullTouchesLand(s))return "⛔ Trup broda prelazi preko kopna. Obala je čvrsta granica."
        if(s.target!="DOK" && !isEast(s) && !isGat(s) && collides(s))return "⛔ Mjesto je zauzeto drugim brodom."
        if(s.target=="PONTON"){
            if(s.type!="Tender" || s.length>8)return "⛔ Ponton je samo za tendere do 8 m."
            if(!(s.x in (pontX-40f)..(pontX+50f) && s.y in 115f..310f))return "⛔ Brod nije postavljen uz ponton."
            return null
        }
        if(s.target=="DOK"){
            val dock=when{
                s.x in (gatX-175f)..(gatX-100f) && s.y in 350f..590f -> Pair("Srednji dok",85)
                s.x in (gatX-100f)..(gatX-35f) && s.y in 395f..590f -> Pair("Mali dok",60)
                s.x in (gatX-35f)..(gatX+70f) && s.y in 350f..635f -> Pair("Veliki dok",140)
                else -> null
            } ?: return "⛔ Nalog traži DOK. Brod mora biti unutar jednog od tri plutajuća doka."

            if(s.length>dock.second)return "⛔ ${dock.first} ima ${dock.second} m, a ${s.name} ima ${s.length} m."

            val occupied=ships.filter{it!==s && it.placed && !it.departed && it.target=="DOK" && sameDock(it,s)}
                .sumOf{it.length}
            val free=dock.second-occupied
            if(s.length>free)return "⛔ ${dock.first}: zauzeto je $occupied/${dock.second} m. Slobodno je samo $free m, a brod ima ${s.length} m."
            return null
        }
        if(s.y in 105f..185f && s.x<westEnd){
            val idx=((s.x-18f)/((westEnd-18f)/14f)).toInt().coerceIn(0,13);val berth=14-idx
            val maxLen=when(berth){in 1..3->45;in 4..8->65;else->110}
            if(s.length>maxLen)return "⛔ Vez $berth prima brodove do $maxLen m, a brod ima ${s.length} m.\nPokušaj odgovarajući veći vez."
            if(((s.rot%360)+360)%360!=270)return "⛔ Zapadna obala: KRMA mora biti prema obali, pramac prema moru."
            return null
        }
        if(s.y in 105f..185f && s.x>gatX+30f){
            if(s.length>150)return "⛔ Istočna obala ima 150 m raspoložive dužine."
            if(s.rot%180!=0)return "⛔ Istočna obala: brod se veže BOČNO, paralelno s obalom."
            return null
        }
        if(s.x in (gatX-45f)..(gatX+75f) && s.y in 115f..390f){
            if(s.length>120)return "⛔ Gat ima 120 m korisne dužine po strani."
            if(s.rot%180!=90)return "⛔ Gat: brod se veže BOČNO, paralelno s gatom."
            return null
        }
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
            if(s.x>gatX+30f)return s.length<=188
        }
        if(s.x in (gatX-45f)..(gatX+75f) && s.y in 115f..390f)return s.length<=if(s.x<gatX+15f)125 else 110
        return false
    }

    private fun dockId(s:Ship):Int=when{
        s.x in (gatX-175f)..(gatX-100f) -> 85
        s.x in (gatX-100f)..(gatX-35f) -> 60
        s.x in (gatX-35f)..(gatX+70f) -> 140
        else -> 0
    }
    private fun sameDock(a:Ship,b:Ship)=dockId(a)!=0 && dockId(a)==dockId(b)

    private fun collides(s:Ship):Boolean=
        ships.any{o->
            o!==s && o.placed && !o.departed &&
            hypot((o.x-s.x).toDouble(),(o.y-s.y).toDouble()) < max(18.0,(o.length+s.length)*.34)
        }

    private fun checkDepartures(){
        val now=SystemClock.elapsedRealtime()
        val missed=ships.filter{it.accepted&&!it.placed&&!it.departed&&it.berthDeadlineAt>0&&now>=it.berthDeadlineAt}
        missed.forEach{s->
            s.departed=true
            score-=failPenalty
            toast("${s.name} otišao bez veza • -$failPenalty")
        }
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
        newShipSet()
        askCaptain()
        invalidate()
    }

    private fun toast(s:String)=Toast.makeText(ctx,s,Toast.LENGTH_SHORT).show()

    override fun onDetachedFromWindow(){
        super.onDetachedFromWindow()
        h.removeCallbacksAndMessages(null)
        tts?.stop();tts?.shutdown();tts=null
    }
}
