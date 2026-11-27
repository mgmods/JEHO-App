package com.Dramizo.Series.presentation.admin

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.Dramizo.Series.data.remote.api.AdminApi
import com.Dramizo.Series.data.remote.dto.ApiResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal data class AdminDashboardItem(val key:String,val title:String,val subtitle:String,val icon:String)

/** Premium native Material control-center home, extracted from the activity. */
internal class AdminDashboardRenderer(private val a:Activity) {
    private val ink=Color.rgb(27,30,48)
    private val muted=Color.rgb(119,124,145)
    private val violet=Color.rgb(111,84,203)
    private fun dp(v:Int)=(v*a.resources.displayMetrics.density).toInt()

    fun render(body:LinearLayout,role:String,name:String,id:String,items:List<AdminDashboardItem>,api:AdminApi,open:(String)->Unit) {
        body.removeAllViews(); body.setPadding(0,0,0,dp(18)); body.layoutDirection=View.LAYOUT_DIRECTION_RTL
        body.setBackgroundColor(Color.rgb(246,247,251))
        val roleName=when(role){"super"->"السوبر أدمن";"admin"->"الأدمن";else->"المانجر"}
        val hero=LinearLayout(a).apply {
            orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20),dp(19),dp(20),dp(20))
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xFF15182B.toInt(),0xFF302653.toInt(),0xFF60458F.toInt())).apply{cornerRadius=dp(26).toFloat()}
            elevation=dp(5).toFloat()
        }
        val brand=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
        val mark=TextView(a).apply{text="J";textSize=23f;gravity=Gravity.CENTER;setTextColor(0xFF29213D.toInt());typeface=Typeface.DEFAULT_BOLD;background=GradientDrawable().apply{setColor(0xFFE6C17C.toInt());cornerRadius=dp(15).toFloat()}}
        brand.addView(mark,LinearLayout.LayoutParams(dp(46),dp(46)))
        val brandTxt=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(11),0,0,0)}
        txt(brandTxt,"JEHO",15f,true,Color.WHITE);txt(brandTxt,"CONTROL CENTER",9f,true,0xFFC9C2E0.toInt(),2)
        brand.addView(brandTxt,LinearLayout.LayoutParams(0,-2,1f))
        val rank=TextView(a).apply{text=role.uppercase();textSize=9f;letterSpacing=.08f;gravity=Gravity.CENTER;setTextColor(0xFFFFE7B7.toInt());setPadding(dp(10),dp(8),dp(10),dp(8));background=GradientDrawable().apply{setColor(0x24E2B763);cornerRadius=dp(30).toFloat();setStroke(dp(1),0x66E2B763)}}
        brand.addView(rank);hero.addView(brand)
        txt(hero,"مرحباً، $name",21f,true,Color.WHITE,dp(22))
        txt(hero,"مركز القيادة والإدارة الذكية لتطبيق JEHO",12f,false,0xFFD5D2E8.toInt(),7)
        val profile=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(12),dp(10),dp(12),dp(10));background=GradientDrawable().apply{setColor(0x1FFFFFFF);cornerRadius=dp(15).toFloat();setStroke(dp(1),0x24FFFFFF)}}
        val avatar=TextView(a).apply{text=name.trim().firstOrNull()?.toString()?: "J";gravity=Gravity.CENTER;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(0xFF2A2245.toInt());background=GradientDrawable().apply{setColor(0xFFF0DAAD.toInt());shape=GradientDrawable.OVAL}}
        profile.addView(avatar,LinearLayout.LayoutParams(dp(38),dp(38)))
        val details=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,0,0)}
        txt(details,roleName,12f,true,Color.WHITE);txt(details,"المعرّف  $id",10f,false,0xFFC9C6DD.toInt(),3)
        profile.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        val secure=TextView(a).apply{text="● آمن";textSize=10f;gravity=Gravity.CENTER;setTextColor(0xFFA1E7C4.toInt());setPadding(dp(9),dp(6),dp(9),dp(6));background=GradientDrawable().apply{setColor(0x1F5BD59A);cornerRadius=dp(20).toFloat()}}
        profile.addView(secure);hero.addView(profile)
        body.addView(hero,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(17)});animate(hero,0)

        body.addView(heading("نظرة عامة","مؤشرات مباشرة من الخادم"),sectionParams())
        val stats=if(role=="super") listOf(Stat("المستخدمون","users","people"),Stat("الغرف المفتوحة","rooms","rooms"),Stat("البث النشط","activeStreams","streams"),Stat("البلاغات المعلّقة","pendingReports","reports"),Stat("طلبات السحب","pendingWithdraws","wallet"),Stat("إيراد اليوم","todayRevenue","revenue"))
            else listOf(Stat("المستخدمون","users","people"),Stat("الغرف المفتوحة","rooms","rooms"),Stat("البث النشط","activeStreams","streams"),Stat("البلاغات المعلّقة","pendingReports","reports"))
        val values=linkedMapOf<String,TextView>()
        stats.chunked(2).forEachIndexed{ri,pair->
            val row=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
            pair.forEachIndexed{ci,s->
                val card=MaterialCardView(a).apply{radius=dp(19).toFloat();cardElevation=dp(2).toFloat();strokeWidth=dp(1);strokeColor=0xFFE9EBF3.toInt();setCardBackgroundColor(Color.WHITE);useCompatPadding=true}
                val box=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(13),dp(11),dp(13),dp(12))}
                box.addView(AdminLineIconView(a,s.icon,violet),LinearLayout.LayoutParams(dp(27),dp(27)))
                val value=TextView(a).apply{text="—";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ink);gravity=Gravity.RIGHT;includeFontPadding=false}
                box.addView(value,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(7)})
                txt(box,s.title,10.5f,false,muted,3);card.addView(box)
                row.addView(card,LinearLayout.LayoutParams(0,dp(126),1f).apply{if(ci==0)marginEnd=dp(5)else marginStart=dp(5)})
                values[s.key]=value
            }
            body.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)});animate(row,50L+ri*35L)
        }

        val heading=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;txt(this,"مركز العمليات",16f,true,ink);txt(this,items.size.toString()+" أدوات متاحة لصلاحيتك",10f,false,muted,3)}
        body.addView(heading,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(13);bottomMargin=dp(10)})
        val searchCard=MaterialCardView(a).apply{radius=dp(16).toFloat();cardElevation=0f;strokeWidth=dp(1);strokeColor=0xFFE5E7F0.toInt();setCardBackgroundColor(Color.WHITE)}
        val sr=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(13),0,dp(13),0)}
        sr.addView(AdminLineIconView(a,"search",muted),LinearLayout.LayoutParams(dp(23),dp(23)))
        val search=EditText(a).apply{hint="ابحث عن قسم أو خدمة...";textSize=13f;setSingleLine(true);setTextColor(ink);setHintTextColor(0xFF9B9FAE.toInt());background=null;layoutDirection=View.LAYOUT_DIRECTION_RTL;textDirection=View.TEXT_DIRECTION_RTL;gravity=Gravity.CENTER_VERTICAL or Gravity.RIGHT;setPadding(dp(10),0,dp(10),0);imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH}
        sr.addView(search,LinearLayout.LayoutParams(0,dp(52),1f));searchCard.addView(sr)
        body.addView(searchCard,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(13)})
        val empty=TextView(a).apply{text="لا توجد نتائج مطابقة";textSize=12f;gravity=Gravity.CENTER;setTextColor(muted);visibility=View.GONE;setPadding(0,dp(18),0,dp(18))}
        body.addView(empty)
        val entries=mutableListOf<Pair<View,AdminDashboardItem>>()
        items.distinctBy{it.key}.chunked(2).forEachIndexed{ri,pair->
            val row=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
            pair.forEachIndexed{ci,item->
                val tint=iconColor(item.key)
                val tile=MaterialCardView(a).apply{radius=dp(19).toFloat();cardElevation=dp(2).toFloat();strokeWidth=dp(1);strokeColor=0xFFE7E9F2.toInt();setCardBackgroundColor(Color.WHITE);isClickable=true;isFocusable=true;rippleColor=ColorStateList.valueOf(0x1F6F54CB);setOnClickListener{open(item.key)}}
                val content=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(13),dp(13),dp(13),dp(13))}
                val top=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
                val plate=MaterialCardView(a).apply{radius=dp(14).toFloat();cardElevation=0f;strokeWidth=0;setCardBackgroundColor(Color.argb(24,Color.red(tint),Color.green(tint),Color.blue(tint)));addView(AdminLineIconView(a,item.key,tint),ViewGroup.LayoutParams(dp(43),dp(43)))}
                top.addView(plate,LinearLayout.LayoutParams(dp(43),dp(43)));top.addView(View(a),LinearLayout.LayoutParams(0,1,1f))
                top.addView(TextView(a).apply{text="↗";textSize=17f;setTextColor(0xFF9FA3B3.toInt())})
                content.addView(top);txt(content,item.title,13f,true,ink,11);txt(content,item.subtitle,10f,false,muted,4,2);tile.addView(content)
                row.addView(tile,LinearLayout.LayoutParams(0,dp(137),1f).apply{if(ci==0)marginEnd=dp(5)else marginStart=dp(5);bottomMargin=dp(9)})
                entries.add(tile to item)
            }
            if(pair.size==1)row.addView(View(a),LinearLayout.LayoutParams(0,dp(137),1f))
            body.addView(row);animate(row,110L+ri*35L)
        }
        search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                val q=s?.toString()?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty();var n=0
                entries.forEach{(v,item)->val match=q.isEmpty()||item.title.lowercase().contains(q)||item.subtitle.lowercase().contains(q)||item.key.lowercase().contains(q);v.visibility=if(match)View.VISIBLE else View.GONE;if(match)n++}
                empty.visibility=if(q.isNotEmpty()&&n==0)View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        body.addView(TextView(a).apply{text="JEHO  •  الوصول محمي بالصلاحيات من الخادم";textSize=9f;gravity=Gravity.CENTER;setTextColor(0xFF999EAE.toInt());setPadding(0,dp(13),0,dp(2))})
        api.overview().enqueue(object:Callback<ApiResponse<Any>>{
            override fun onResponse(call:Call<ApiResponse<Any>>,response:Response<ApiResponse<Object>>){
                if(a.isFinishing||a.isDestroyed)return
                val data=response.body()?.getData() as? Map<*,*>?:return
                values.forEach{(key,v)->val raw=data[key];v.text=when(raw){is Number->if(key=="todayRevenue")String.format(java.util.Locale.US,"%.0f",raw.toDouble())else raw.toLong().toString();null->"0";else->raw.toString()}}
            }
            override fun onFailure(call:Call<ApiResponse<Object>>,error:Throwable){if(!a.isFinishing&&!a.isDestroyed)values.values.forEach{it.text="—"}}
        })
    }

    private fun heading(t:String,s:String)=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;txt(this,t,16f,true,ink);txt(this,s,10f,false,muted,3)}
    private fun sectionParams()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)}
    private fun txt(p:LinearLayout,v:String,size:Float,bold:Boolean,color:Int,top:Int=0,max:Int=1){
        p.addView(TextView(a).apply{text=v;textSize=size;setTextColor(color);gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;textDirection=View.TEXT_DIRECTION_RTL;includeFontPadding=false;maxLines=max;ellipsize=android.text.TextUtils.TruncateAt.END;if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);if(top>0)setPadding(0,dp(top),0,0)},LinearLayout.LayoutParams(-1,-2))
    }
    private fun animate(v:View,delay:Long){v.alpha=0f;v.translationY=dp(12).toFloat();v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(310).setInterpolator(DecelerateInterpolator(1.6f)).start()}
    private fun iconColor(k:String)=when{ k.contains("user")||k.contains("gender")->Color.rgb(69,132,218);k.contains("room")||k.contains("stream")->Color.rgb(42,161,133);k.contains("gift")||k.contains("vip")||k.contains("cosmetic")->Color.rgb(191,139,55);k.contains("report")->Color.rgb(218,104,115);k.contains("agency")||k.contains("recharge")->Color.rgb(81,137,180);k.contains("wallet")||k.contains("withdraw")->Color.rgb(48,151,129);else->violet }
    private data class Stat(val title:String,val key:String,val icon:String)
}

private class AdminLineIconView(a:Activity,private val kind:String,tint:Int):View(a){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=tint;style=Paint.Style.STROKE;strokeWidth=2f*resources.displayMetrics.density;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val f=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=tint;style=Paint.Style.FILL}
    override fun onDraw(c:Canvas){
        super.onDraw(c);val s=minOf(width,height)/28f;c.save();c.translate((width-24f*s)/2f,(height-24f*s)/2f);c.scale(s,s)
        when{
            kind=="people"||kind.contains("user")->{c.drawCircle(9f,8f,3.2f,p);c.drawCircle(17f,9f,2.6f,p);c.drawArc(RectF(3f,13f,15f,23f),180f,180f,false,p);c.drawArc(RectF(13f,15f,22f,23f),180f,180f,false,p)}
            kind=="rooms"||kind.contains("room")->{c.drawPath(Path().apply{moveTo(2f,11f);lineTo(12f,3f);lineTo(22f,11f);lineTo(22f,21f);lineTo(2f,21f);close()},p);c.drawRect(9f,14f,15f,21f,p)}
            kind=="streams"||kind.contains("stream")->{c.drawCircle(12f,12f,2f,f);c.drawArc(RectF(5f,5f,19f,19f),215f,110f,false,p);c.drawArc(RectF(1f,1f,23f,23f),215f,110f,false,p)}
            kind=="reports"||kind.contains("report")->{c.drawPath(Path().apply{moveTo(12f,2f);lineTo(21f,5f);lineTo(20f,14f);lineTo(12f,22f);lineTo(4f,14f);lineTo(3f,5f);close()},p);c.drawLine(12f,7f,12f,13f,p);c.drawCircle(12f,17f,.8f,f)}
            kind.contains("agency")->{c.drawRect(4f,4f,20f,22f,p);c.drawLine(8f,8f,8f,10f,p);c.drawLine(12f,8f,12f,10f,p);c.drawLine(16f,8f,16f,10f,p);c.drawRect(10f,15f,14f,22f,p)}
            kind.contains("vip")->{c.drawPath(Path().apply{moveTo(3f,7f);lineTo(8f,12f);lineTo(12f,4f);lineTo(16f,12f);lineTo(21f,7f);lineTo(19f,19f);lineTo(5f,19f);close()},p);c.drawLine(6f,22f,18f,22f,p)}
            kind.contains("gift")->{c.drawRect(3f,9f,21f,21f,p);c.drawLine(2f,9f,22f,9f,p);c.drawLine(12f,9f,12f,21f,p);c.drawPath(Path().apply{moveTo(12f,9f);cubicTo(3f,9f,6f,1f,10f,5f);lineTo(12f,9f)},p);c.drawPath(Path().apply{moveTo(12f,9f);cubicTo(21f,9f,18f,1f,14f,5f);lineTo(12f,9f)},p)}
            kind=="search"->{c.drawCircle(10f,10f,6f,p);c.drawLine(14.5f,14.5f,21f,21f,p)}
            kind.contains("wallet")||kind.contains("revenue")->{c.drawRoundRect(RectF(2f,5f,22f,20f),3f,3f,p);c.drawLine(2f,9f,22f,9f,p);c.drawRoundRect(RectF(14f,12f,23f,18f),2f,2f,p);c.drawCircle(17f,15f,.8f,f)}
            else->{c.drawRoundRect(RectF(3f,3f,21f,21f),4f,4f,p);c.drawCircle(8f,8f,1f,f);c.drawCircle(16f,8f,1f,f);c.drawCircle(8f,16f,1f,f);c.drawCircle(16f,16f,1f,f)}
        };c.restore()
    }
}
