package hr.kapetanluke.game

import android.app.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import kotlin.math.*

data class Ship(val name:String,val type:String,val length:Int,val days:Int,val target:String,var x:Float=0f,var y:Float=0f,var rot:Int=0,var accepted:Boolean=false,var placed:Boolean=false,var scored:Boolean=false)

class MainActivity: Activity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;setContentView(HarborView(this))}
}

class HarborView(private val ctx:Context): View(ctx){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG); private val t=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.DEFAULT_BOLD}
 private val prefs=ctx.getSharedPreferences("kapetan_luke",Context.MODE_PRIVATE)
 private var captain=""; private var score=1000; private var secs=300; private var running=false; private var accepted=false
 private var selected:Ship?=null; private var lx=0f; private var ly=0f; private var down=0L
 private var reward=100; private var rejectPenalty=100; private var failPenalty=200; private var movePenalty=20
 private var westEnd=0f; private var pontX=0f; private var gatX=0f
 private val ships=mutableListOf(
  Ship("MY Aurora","Jahta",82,6,"VEZ"), Ship("Ocean Pioneer","Supply",54,10,"DOK"),
  Ship("Blue Star","Katamaran",42,3,"VEZ"), Ship("Tender 07","Tender",8,4,"PONTON"),
  Ship("Adriatic Bulk","Rasuti teret",104,12,"VEZ")
 )
 private val h=Handler(Looper.getMainLooper()); private val tick=object:Runnable{override fun run(){if(running&&secs>0){secs--; if(secs==0)finishRound(); invalidate()}h.postDelayed(this,1000)}}
 init{isFocusable=true;h.post(tick);post{askCaptain()}}

 private fun askCaptain(){val e=EditText(ctx).apply{hint="Ime i prezime kapetana";setText(prefs.getString("lastCaptain",""))};AlertDialog.Builder(ctx).setTitle("KAPETAN LUKE").setMessage("Upiši ime i prezime kapetana luke").setView(e).setPositiveButton("DALJE"){_,_->captain=e.text.toString().trim().ifBlank{"Kapetan"};prefs.edit().putString("lastCaptain",captain).apply();showMail()}.setCancelable(false).show()}
 private fun showMail(){val msg=buildString{append("Od: Adriatic Marine Services\nPredmet: Zahtjev za prihvat brodova\n\nPoštovani kapetane luke,\nmolimo prihvat sljedećih brodova:\n\n");ships.forEach{append("• ${it.name} — ${it.type} — ${it.length} m — ${it.target} — ${it.days} dana\n")};append("\nPrihvaćanjem preuzimate obvezu smještaja SVIH brodova.")};AlertDialog.Builder(ctx).setTitle("NOVA PORUKA").setMessage(msg).setNegativeButton("ODBIJ") {_,_-> score-=rejectPenalty*ships.size;accepted=false;running=true;toast("Zahtjev odbijen: -${rejectPenalty*ships.size} bodova") }.setPositiveButton("PRIHVATI"){_,_->accepted=true;ships.forEach{it.accepted=true};layoutWaitingShips();running=true;toast("Zahtjev prihvaćen. Smjesti sve brodove!")}.setCancelable(false).show()}
 private fun layoutWaitingShips(){val base=height*0.78f;ships.forEachIndexed{i,s->s.x=70f+i*(width-140f)/max(1,ships.size-1);s.y=base+(i%2)*55f}}

 override fun onDraw(c:Canvas){super.onDraw(c);if(width==0)return; val W=width.toFloat(); val H=height.toFloat();westEnd=W*.60f;pontX=westEnd+14f;gatX=pontX+80f
  c.drawColor(Color.rgb(43,124,153));p.color=Color.rgb(223,214,185);c.drawRect(0f,68f,W,154f,p)
  // West quay: 14 -> 1
  val start=18f;val bw=(westEnd-start)/14f;t.color=Color.rgb(22,46,52);t.textSize=16f
  for(i in 0 until 14){val n=14-i;val x=start+i*bw;c.drawLine(x,145f,x,166f,p);c.drawText(n.toString(),x+4,104f,t)}
  t.textSize=13f;c.drawText("9–14: 65–110 m",25f,132f,t);c.drawText("4–8: 45–65 m",westEnd*.39f,132f,t);c.drawText("1–3: do 45 m",westEnd*.76f,132f,t)
  // Pontoon
  p.color=Color.rgb(188,190,180);c.drawRect(pontX,154f,pontX+26f,300f,p);t.textSize=13f;c.drawText("PONTON",pontX-17f,120f,t);c.drawText("6 x 8 m",pontX-12f,139f,t)
  // Gat
  c.drawRect(gatX,154f,gatX+46f,430f,p);c.drawText("GAT",gatX+7f,120f,t);c.drawText("120 m / strana",gatX-26f,139f,t)
  // East quay
  c.drawRect(gatX+46f,68f,W,154f,p);t.textSize=15f;c.drawText("ISTOČNA OBALA 150 m",gatX+70f,112f,t)
  // Floating docks: medium, small, large; large aligned with gat
  p.color=Color.rgb(180,184,178);c.drawRoundRect(RectF(gatX-178f,448f,gatX-126f,650f),8f,8f,p);c.drawRoundRect(RectF(gatX-96f,492f,gatX-44f,650f),8f,8f,p);c.drawRoundRect(RectF(gatX,448f,gatX+46f,690f),8f,8f,p)
  t.color=Color.WHITE;t.textSize=14f;c.drawText("85 m",gatX-176f,670f,t);c.drawText("60 m",gatX-94f,670f,t);c.drawText("140 m",gatX,710f,t)
  // HUD
  p.color=Color.argb(235,7,32,50);c.drawRect(0f,0f,W,68f,p);t.color=Color.WHITE;t.textSize=21f;c.drawText("KAPETAN: $captain",18f,42f,t);c.drawText("VRIJEME %02d:%02d".format(secs/60,secs%60),W*.42f,42f,t);c.drawText("BODOVI $score",W*.64f,42f,t)
  drawButton(c,RectF(W-245f,10f,W-130f,58f),"POSTAVKE");drawButton(c,RectF(W-120f,10f,W-10f,58f),"RESTART")
  if(accepted){ships.forEach{drawShip(c,it)};t.textSize=15f;t.color=Color.WHITE;c.drawText("Povuci brod • kratki dodir = rotacija 90° • zeleno = valjana pozicija",18f,H-15f,t)} else {t.color=Color.WHITE;t.textSize=24f;c.drawText("Zahtjev je odbijen. Restart za novu rundu.",30f,H*.55f,t)}
 }
 private fun drawButton(c:Canvas,r:RectF,label:String){p.color=Color.rgb(33,78,102);c.drawRoundRect(r,9f,9f,p);t.color=Color.WHITE;t.textSize=14f;c.drawText(label,r.left+10,r.centerY()+5,t)}
 private fun drawShip(c:Canvas,s:Ship){if(s.x==0f)return; val len=(s.length*1.18f).coerceIn(34f,145f);val wid=26f;c.save();c.rotate(s.rot.toFloat(),s.x,s.y);p.color=when(s.type){"Jahta"->Color.WHITE;"Vojni"->Color.rgb(80,104,88);"Supply"->Color.rgb(235,150,48);"Tender"->Color.rgb(245,211,75);"Katamaran"->Color.rgb(230,235,240);else->Color.rgb(130,140,145)};val r=RectF(s.x-len/2,s.y-wid/2,s.x+len/2,s.y+wid/2);c.drawRoundRect(r,13f,13f,p);p.color=if(s.placed)Color.rgb(45,150,80) else Color.DKGRAY;p.style=Paint.Style.STROKE;p.strokeWidth=4f;c.drawRoundRect(r,13f,13f,p);p.style=Paint.Style.FILL;c.restore();t.color=Color.WHITE;t.textSize=13f;c.drawText("${s.name} ${s.length}m ${s.days}d ${s.target}",s.x-len/2,s.y+34f,t)}

 override fun onTouchEvent(e:MotionEvent):Boolean{if(!accepted)return true; val W=width.toFloat();if(e.action==MotionEvent.ACTION_DOWN){if(e.y<68&&e.x>W-245){if(e.x<W-130)showSettings() else restart();return true};down=e.eventTime;lx=e.x;ly=e.y;selected=ships.filter{it.accepted}.minByOrNull{hypot((it.x-e.x).toDouble(),(it.y-e.y).toDouble())}?.takeIf{hypot((it.x-e.x).toDouble(),(it.y-e.y).toDouble())<100};return true}
  if(e.action==MotionEvent.ACTION_MOVE){selected?.let{s->s.x=(s.x+e.x-lx).coerceIn(15f,W-15f);s.y=(s.y+e.y-ly).coerceIn(80f,height-30f);s.placed=false};lx=e.x;ly=e.y;invalidate();return true}
  if(e.action==MotionEvent.ACTION_UP){selected?.let{s->if(e.eventTime-down<220){s.rot=(s.rot+90)%360}else{validatePlacement(s)}};selected=null;invalidate();return true};return true}
 private fun validatePlacement(s:Ship){val valid=when(s.target){"PONTON"->s.length<=8&&s.x in (pontX-45f)..(pontX+65f)&&s.y in 150f..355f;"DOK"->s.y>410f&&s.x in (gatX-230f)..(gatX+100f);else->validBerth(s)};if(valid&&!collides(s)){s.placed=true;if(!s.scored){score+=reward;s.scored=true;toast("${s.name}: +$reward")};if(ships.all{it.placed})finishRound()}else{s.placed=false;toast("Nevaljana pozicija — provjeri dužinu, kopno ili drugi brod")}}
 private fun validBerth(s:Ship):Boolean{if(s.y in 120f..230f){if(s.x<westEnd){val idx=((s.x-18f)/((westEnd-18f)/14f)).toInt().coerceIn(0,13);val berth=14-idx;val max=when(berth){in 1..3->45;in 4..8->65;else->110};return s.length<=max};if(s.x>gatX+45f)return s.length<=150};if(s.x in (gatX-55f)..(gatX+100f)&&s.y in 150f..450f)return s.length<=120;return false}
 private fun collides(s:Ship):Boolean=ships.any{o->o!==s&&o.placed&&hypot((o.x-s.x).toDouble(),(o.y-s.y).toDouble())<max(30.0,(o.length+s.length)*.38)}
 private fun finishRound(){if(!running)return;running=false;val missing=ships.count{accepted&&!it.placed};if(missing>0){score-=missing*failPenalty;toast("$missing prihvaćenih brodova nije smješteno: -${missing*failPenalty}")}saveResult();AlertDialog.Builder(ctx).setTitle("KRAJ RUNDE").setMessage("Kapetan: $captain\nRezultat: $score bodova\nSmješteno: ${ships.count{it.placed}}/${ships.size}\n\nRezultat je spremljen.").setPositiveButton("NOVA IGRA"){_,_->restart()}.setNegativeButton("ZATVORI",null).show();invalidate()}
 private fun saveResult(){val old=prefs.getString("results","")?:"";val row="$captain|$score|${System.currentTimeMillis()}";prefs.edit().putString("results",(row+"\n"+old).lines().take(10).joinToString("\n")).apply()}
 private fun showSettings(){val box=LinearLayout(ctx).apply{orientation=LinearLayout.VERTICAL;setPadding(35,10,35,0)};fun field(label:String,value:Int):EditText{val e=EditText(ctx).apply{hint=label;inputType=2;setText(value.toString())};box.addView(TextView(ctx).apply{text=label});box.addView(e);return e};val fTime=field("Trajanje runde (sekunde)",secs.coerceAtLeast(60));val fReward=field("Bodovi za smješten brod",reward);val fReject=field("Kazna odbijanja po brodu",rejectPenalty);val fFail=field("Kazna prihvaćen, a nesmješten",failPenalty);AlertDialog.Builder(ctx).setTitle("POSTAVKE").setView(box).setPositiveButton("SPREMI"){_,_->secs=fTime.text.toString().toIntOrNull()?.coerceIn(60,3600)?:secs;reward=fReward.text.toString().toIntOrNull()?.coerceIn(0,1000)?:reward;rejectPenalty=fReject.text.toString().toIntOrNull()?.coerceIn(0,1000)?:rejectPenalty;failPenalty=fFail.text.toString().toIntOrNull()?.coerceIn(0,2000)?:failPenalty;invalidate()}.setNegativeButton("ODUSTANI",null).show()}
 private fun restart(){running=false;score=1000;secs=300;accepted=false;ships.forEach{it.x=0f;it.y=0f;it.rot=0;it.accepted=false;it.placed=false;it.scored=false};askCaptain();invalidate()}
 private fun toast(s:String)=Toast.makeText(ctx,s,Toast.LENGTH_SHORT).show()
 override fun onDetachedFromWindow(){super.onDetachedFromWindow();h.removeCallbacksAndMessages(null)}
}
