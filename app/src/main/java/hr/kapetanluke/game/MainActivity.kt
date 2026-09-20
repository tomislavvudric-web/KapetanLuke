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
        westEnd=worldW*.60f
        pontX=westEnd+8f
        gatX=pontX+55f

        // Kopno je namjerno usko - vezivanje uz sami rub obale.
        p.color=Color.rgb(173,164,139)
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

        p.color=Color.rgb(102,118,121)
        c.drawRect(pontX,118f,pontX+18f,250f,p)
        t.textSize=11f;c.drawText("PONTON",pontX-15f,88f,t)

        c.drawRect(gatX,118f,gatX+30f,360f,p)
        c.drawText("GAT",gatX+3f,88f,t)
        c.drawText("120 m / strana",gatX-25f,106f,t)

        c.drawRect(gatX+30f,68f,worldW,118f,p)
        t.textSize=13f;c.drawText("ISTOČNA OBALA 150 m",gatX+45f,98f,t)

        p.color=Color.rgb(83,101,108)
        c.drawRoundRect(RectF(gatX-150f,380f,gatX-118f,565f),6f,6f,p)
        c.drawRoundRect(RectF(gatX-82f,425f,gatX-50f,565f),6f,6f,p)
        c.drawRoundRect(RectF(gatX,380f,gatX+30f,610f),6f,6f,p)
        t.color=Color.WHITE;t.textSize=12f
        c.drawText("85 m",gatX-150f,585f,t)
        c.drawText("60 m",gatX-82f,585f,t)
        c.drawText("140 m",gatX,630f,t)

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
        c.drawText("${s.name} ${s.length}m$suffix",s.x-len/2,s.y+wid+15f,t)
    }

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
                        selected=longPressShip;longPressArmed=false
                        selected?.let{toast("${it.name} • premještanje omogućeno")}
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
        if(ship.departed || !ship.accepted || ship.placed || ship.name in autoMoving)return
        val input=EditText(ctx).apply{
            hint="npr. Vez 5, Dok 1, Gat, Istočna obala, Ponton"
            textSize=18f
            setSingleLine(true)
        }
        val box=LinearLayout(ctx).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(34,12,34,0)
            addView(TextView(ctx).apply{
                text="${ship.name} • ${ship.length} m • nalog: ${ship.target}\nUpiši odredište ili odaberi RUČNO."
                textSize=17f
            })
            addView(input)
        }
        AlertDialog.Builder(ctx)
            .setTitle("⚓ KAPETANOVA NAREDBA")
            .setView(box)
            .setPositiveButton("POŠALJI BROD"){_,_->
                executeMooringCommand(ship,input.text.toString())
            }
            .setNeutralButton("OKRENI 90°"){_,_->ship.rot=(ship.rot+90)%360;invalidate()}
            .setNegativeButton("RUČNO",null)
            .show()
    }

    private fun executeMooringCommand(ship:Ship,raw:String){
        val cmd=raw.trim().lowercase(Locale("hr","HR")).replace("broj","").replace("br.","").replace("br","")
        if(cmd.isBlank()){toast("Upiši odredište, npr. Vez 5");return}
        val start=18f
        val bw=(westEnd-start)/14f
        val target:Triple<Float,Float,Int>?=when{
            cmd.startsWith("vez") -> {
                val n=Regex("\\d+").find(cmd)?.value?.toIntOrNull()
                if(n==null || n !in 1..14){toast("Vez mora biti od 1 do 14");null}
                else{
                    val maxLen=when(n){in 1..3->45;in 4..8->65;else->110}
                    if(ship.length>maxLen){toast("Vez $n prima do $maxLen m • ${ship.name} ima ${ship.length} m");null}
                    else{
                        val cx=start+(14-n+0.5f)*bw
                        val halfLong=max(18f,ship.length*0.46f)
                        Triple(cx,(122f+halfLong).coerceAtMost(182f),90)
                    }
                }
            }
            cmd.startsWith("dok") -> {
                val n=Regex("\\d+").find(cmd)?.value?.toIntOrNull()
                when(n){
                    1 -> if(ship.length<=85) Triple(gatX-134f,475f,90) else null
                    2 -> if(ship.length<=60) Triple(gatX-66f,495f,90) else null
                    3 -> if(ship.length<=140) Triple(gatX+15f,490f,90) else null
                    else -> null
                }.also{if(it==null)toast("Dok nije valjan ili je brod predug")}
            }
            cmd.contains("isto") -> Triple((gatX+30f+width.toFloat())/2f,160f,0)
            cmd.contains("gat") -> Triple(gatX-20f,260f,90)
            cmd.contains("ponton") -> if(ship.type=="Tender" && ship.length<=8) Triple(pontX+32f,205f,90) else null
            else -> null
        }
        if(target==null){if(!cmd.startsWith("vez")&&!cmd.startsWith("dok"))toast("Ne prepoznajem naredbu. Probaj: Vez 5, Dok 1, Gat, Istočna obala ili Ponton");return}
        val oldX=ship.x;val oldY=ship.y;val oldRot=ship.rot
        ship.x=target.first;ship.y=target.second;ship.rot=target.third
        val problem=placementProblem(ship)
        ship.x=oldX;ship.y=oldY;ship.rot=oldRot
        if(problem!=null){
            AlertDialog.Builder(ctx).setTitle("NAREDBA NIJE MOGUĆA").setMessage(problem).setPositiveButton("U REDU",null).show()
            return
        }
        animateShipTo(ship,target.first,target.second,target.third)
    }

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
        val departed=ships.filter{it.departed}
        val dock85=inPort.filter{dockId(it)==85}.sumOf{it.length}
        val dock60=inPort.filter{dockId(it)==60}.sumOf{it.length}
        val dock140=inPort.filter{dockId(it)==140}.sumOf{it.length}
        val nextWave=if(pendingWave)"NOVI ZAHTJEV ČEKA ODLUKU" else if(nextWaveAt>now)"za ${((nextWaveAt-now)/1000)} s" else "uskoro"
        val msg=buildString{
            append("OPERATIVNO STANJE LUKE\\n\\n")
            append("U LUCI: ${inPort.size}     ČEKA: ${waiting.size}     OTPLOVILI: ${departed.size}\\n")
            append("SLJEDEĆI ZAHTJEV: $nextWave\\n\\n")
            append("ČEKA SMJEŠTAJ\\n")
            if(waiting.isEmpty())append("• Nema prihvaćenih brodova na čekanju.\\n")
            waiting.forEach{append("• ${it.name} | ${it.length} m | ${it.target}\\n")}
            append("\\nBRODOVI U LUCI / ODLASCI\\n")
            if(inPort.isEmpty())append("• Luka je trenutno prazna.\\n")
            inPort.forEach{
                val left=max(0L,(it.departureAt-now+999)/1000)
                val loc=if(it.target=="DOK")"${dockId(it)} m DOK" else it.target
                append("• ${it.name} • $loc • odlazak %02d:%02d\\n".format(left/60,left%60))
            }
            append("\\nKAPACITET DOKOVA\\n")
            append("• Srednji 85 m: $dock85 / 85 m • slobodno ${85-dock85} m\\n")
            append("• Mali 60 m: $dock60 / 60 m • slobodno ${60-dock60} m\\n")
            append("• Veliki 140 m: $dock140 / 140 m • slobodno ${140-dock140} m\\n")
        }
        val infoDialog=AlertDialog.Builder(ctx).setTitle("HARBOUR CONTROL • PREGLED LUKE").setMessage(msg)
            .setPositiveButton("NASTAVI",null)
        if(pendingWave){
            infoDialog.setNeutralButton("OTVORI NOVI ZAHTJEV"){_,_->showIncomingWave()}
        }
        infoDialog.show()
    }


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
            AlertDialog.Builder(ctx).setTitle("BROD NIJE PRIVEZAN").setMessage("${s.name} • ${s.length} m\n\n$reason")
                .setPositiveButton("U REDU",null).show()
        }
    }

    private fun placementProblem(s:Ship):String?{
        if(hullTouchesLand(s))return "⛔ Trup broda prelazi preko kopna. Obala je čvrsta granica."
        if(s.target!="DOK" && collides(s))return "⛔ Mjesto je zauzeto drugim brodom."
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
            if(s.rot%180!=90)return "⛔ Zapadna obala: brod mora biti u četverovezu KRMOM prema obali."
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
            if(s.x>gatX+30f)return s.length<=150
        }
        if(s.x in (gatX-45f)..(gatX+75f) && s.y in 115f..390f)return s.length<=120
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
