import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

const gold=Color(0xFFB68A35);
const apiBase=String.fromEnvironment('JEHO_API_BASE_URL',defaultValue:'https://jeho-own-backend.mgs.blitz.cloud/api/v1/');
void main()=>runApp(const AdminApp());

class AdminApp extends StatelessWidget {
 const AdminApp({super.key});
 @override Widget build(BuildContext context)=>MaterialApp(title:'JEHO-OWN Admin',debugShowCheckedModeBanner:false,locale:const Locale('ar'),theme:ThemeData(useMaterial3:true,colorScheme:ColorScheme.fromSeed(seedColor:gold),scaffoldBackgroundColor:const Color(0xFFF7F5F0)),builder:(c,child)=>Directionality(textDirection:TextDirection.rtl,child:child??const SizedBox.shrink()),home:const AdminLogin());
}
class Api {
 Api(this.token); final String token;
 Uri uri(String path)=>Uri.parse(apiBase.endsWith('/')?apiBase:apiBase+'/').resolve(path);
 Future<dynamic> get(String path) async {
  final r=await http.get(uri(path),headers:{'Accept':'application/json',if(token.isNotEmpty)'Authorization':'Bearer '+token}).timeout(const Duration(seconds:20));
  dynamic v;try{v=jsonDecode(r.body);}catch(_){v=r.body;}
  if(r.statusCode<200||r.statusCode>=300)throw Exception(v is Map?(v['message']??v['error']??'فشل الطلب').toString():'فشل الطلب: '+r.statusCode.toString());
  return v is Map&&v['data']!=null?v['data']:v;
 }
 Future<dynamic> delete(String path) async {
  final r=await http.delete(uri(path),headers:{'Accept':'application/json','Authorization':'Bearer '+token}).timeout(const Duration(seconds:20));
  dynamic v;try{v=jsonDecode(r.body);}catch(_){v=r.body;}
  if(r.statusCode<200||r.statusCode>=300)throw Exception(v is Map?(v['message']??v['error']??'فشل حذف العنصر').toString():'فشل الطلب: '+r.statusCode.toString());
  return v is Map&&v['data']!=null?v['data']:v;
 }
 Future<dynamic> post(String path, [Map<String,dynamic> body = const {}]) async {
  final r=await http.post(uri(path),headers:{'Content-Type':'application/json','Accept':'application/json','Authorization':'Bearer '+token},body:jsonEncode(body)).timeout(const Duration(seconds:20));
  dynamic v;try{v=jsonDecode(r.body);}catch(_){v=r.body;}
  if(r.statusCode<200||r.statusCode>=300)throw Exception(v is Map?(v['message']??v['error']??'فشل تنفيذ الإجراء').toString():'فشل الطلب: '+r.statusCode.toString());
  return v is Map&&v['data']!=null?v['data']:v;
 }
 Future<dynamic> patch(String path, Map<String,dynamic> body) async {
  final r=await http.patch(uri(path),headers:{'Content-Type':'application/json','Accept':'application/json','Authorization':'Bearer '+token},body:jsonEncode(body)).timeout(const Duration(seconds:20));
  dynamic v;try{v=jsonDecode(r.body);}catch(_){v=r.body;}
  if(r.statusCode<200||r.statusCode>=300)throw Exception(v is Map?(v['message']??v['error']??'فشل حفظ الإعدادات').toString():'فشل الطلب: '+r.statusCode.toString());
  return v is Map&&v['data']!=null?v['data']:v;
 }
 Future<dynamic> put(String path, Map<String,dynamic> body) async {
  final r=await http.put(uri(path),headers:{'Content-Type':'application/json','Accept':'application/json','Authorization':'Bearer '+token},body:jsonEncode(body)).timeout(const Duration(seconds:20));
  dynamic v;try{v=jsonDecode(r.body);}catch(_){v=r.body;}
  if(r.statusCode<200||r.statusCode>=300)throw Exception(v is Map?(v['message']??v['error']??'فشل حفظ البيانات').toString():'فشل الطلب: '+r.statusCode.toString());
  return v is Map&&v['data']!=null?v['data']:v;
 }
 Future<Map<String,dynamic>> login(String email,String password) async {
  final r=await http.post(uri('admin/auth/login'),headers:{'Content-Type':'application/json','Accept':'application/json'},body:jsonEncode({'email':email.trim(),'password':password})).timeout(const Duration(seconds:20));
  final v=jsonDecode(r.body);if(r.statusCode<200||r.statusCode>=300)throw Exception(v is Map?(v['message']??'تعذر تسجيل الدخول').toString():'تعذر تسجيل الدخول');
  final d=v is Map&&v['data'] is Map?v['data']:v;if(d is! Map<String,dynamic>)throw Exception('استجابة الدخول غير صالحة');return d;
 }
}
class AdminLogin extends StatefulWidget {const AdminLogin({super.key});@override State<AdminLogin> createState()=>_AdminLoginState();}
class _AdminLoginState extends State<AdminLogin>{
 final email=TextEditingController(),password=TextEditingController();bool busy=false;String? error;
 @override void dispose(){email.dispose();password.dispose();super.dispose();}
 Future<void> submit() async {setState(() { busy = true; error = null; });try{final d=await Api('').login(email.text,password.text);final t=(d['token']??d['accessToken']??d['access_token']??'').toString();if(t.isEmpty)throw Exception('لم يرجع الخادم رمز جلسة الإدارة');if(!mounted)return;Navigator.pushReplacement(context,MaterialPageRoute<void>(builder:(_)=>AdminHome(token:t,user:d['user'] is Map<String,dynamic>?d['user'] as Map<String,dynamic>:const {})));}catch(e){if(mounted)setState(()=>error=e.toString().replaceFirst('Exception: ',''));}finally{if(mounted)setState(()=>busy=false);}}
 @override Widget build(BuildContext context)=>Scaffold(body:Center(child:ConstrainedBox(constraints:const BoxConstraints(maxWidth:440),child:Card(child:Padding(padding:const EdgeInsets.all(28),child:Column(mainAxisSize:MainAxisSize.min,crossAxisAlignment:CrossAxisAlignment.stretch,children:[const Icon(Icons.admin_panel_settings,size:56,color:gold),const Text('JEHO-OWN',textAlign:TextAlign.center,style:TextStyle(fontSize:28,fontWeight:FontWeight.w900)),const Text('لوحة الإدارة Flutter',textAlign:TextAlign.center),const SizedBox(height:24),TextField(controller:email,textDirection:TextDirection.ltr,decoration:const InputDecoration(labelText:'البريد أو اسم المستخدم')),const SizedBox(height:12),TextField(controller:password,obscureText:true,textDirection:TextDirection.ltr,onSubmitted:(_)=>submit(),decoration:const InputDecoration(labelText:'كلمة المرور')),if(error!=null)Text(error!,style:TextStyle(color:Theme.of(context).colorScheme.error)),const SizedBox(height:16),FilledButton(onPressed:busy?null:submit,child:Text(busy?'جارٍ الدخول…':'تسجيل الدخول')),const SizedBox(height:8),const Text('يستخدم مصادقة الإدارة الحالية ولا يغيّر إعدادات الخادم.',textAlign:TextAlign.center,style:TextStyle(fontSize:12,color:Colors.black54))]))))));
}
class Module {const Module(this.title,this.path,this.icon);final String title,path;final IconData icon;}
const modules=<Module>[
 Module('مخططات لوحة التحكم','admin/dashboard/charts?period=7d',Icons.bar_chart),
 Module('البثوث المباشرة','admin/streams?page=1&limit=50',Icons.live_tv),
 Module('تصنيفات الهدايا','admin/gift-categories',Icons.category),
 Module('خطط VIP','admin/vip/plans',Icons.workspace_premium),
 Module('باقات الشحن','admin/wallet/packages',Icons.add_card),
 Module('باقات السحب','admin/wallet/withdraw-packages',Icons.outbox),
 Module('تسعير وكلاء الشحن','admin/recharge-agent-pricing',Icons.price_change),
 Module('صناديق الحظ','lucky-boxes/admin/boxes',Icons.redeem),
 Module('سجل فتح صناديق الحظ','lucky-boxes/admin/opens',Icons.history),
 Module('كأس الغرف','room-cup/leaderboard',Icons.emoji_events),
 Module('سجلات النظام','admin/logs?page=1&limit=50',Icons.receipt_long),
 Module('ملخص السياسات','admin/policy-brochure/snapshot',Icons.policy),
 Module('إعدادات الدراما','admin/drama/settings',Icons.movie_filter),
 Module('نظرة عامة','admin/dashboard/overview',Icons.dashboard),
 Module('المستخدمون','admin/users?page=1&limit=50',Icons.people),
 Module('المحادثات','admin/conversations?page=1&limit=50',Icons.forum),
 Module('الغرف','admin/rooms?page=1&limit=50',Icons.meeting_room),
 Module('الهدايا','admin/gifts?page=1&limit=50',Icons.card_giftcard),
 Module('الرسائل المدفوعة','admin/paid-messages/settings',Icons.payments),
 Module('البنرات','admin/banners',Icons.image),
 Module('العروض','admin/offers',Icons.local_offer),
 Module('كتالوج الألعاب','admin/games-store/catalog',Icons.sports_esports),
 Module('الدراما','admin/drama/series',Icons.movie),
 Module('الوكالات','admin/agencies',Icons.groups),
 Module('معاملات المحفظة','admin/wallet/transactions?page=1&limit=50',Icons.account_balance_wallet),
 Module('VIP','admin/vip?page=1&limit=50',Icons.workspace_premium),
 Module('طلبات الوكالات','admin/agencies/applications?page=1&limit=50',Icons.assignment_ind),
 Module('وكلاء الشحن','admin/recharge-agents',Icons.currency_exchange),
 Module('طلبات السحب','admin/wallet/withdraws?page=1&limit=50',Icons.outbox),
 Module('الشحنات','admin/wallet/recharges?page=1&limit=50',Icons.add_card),
 Module('المظاهر والإطارات','admin/cosmetics?page=1&limit=50',Icons.auto_awesome),
 Module('الآي دي المميز','vanity-ids/admin/all',Icons.badge),
 Module('المسابقات','admin/contests',Icons.emoji_events),
 Module('المهام اليومية','admin/tasks',Icons.task_alt),
 Module('التقارير','admin/reports?page=1&limit=50',Icons.flag),
 Module('الإشعارات','admin/notifications?page=1&limit=50',Icons.notifications),
 Module('إعدادات الاقتصاد','admin/economy/settings',Icons.tune),
 Module('إعدادات التطبيق','admin/settings',Icons.settings),
 Module('طلبات CP','admin/cp/requests?page=1&limit=50',Icons.favorite),
 Module('إعدادات CP','admin/cp/config',Icons.favorite_border),
 Module('سجل تدقيق CP','admin/cp/audit?page=1&limit=50',Icons.fact_check),
 Module('التحقق من الجنس','admin/gender-verifications?page=1&limit=50',Icons.verified_user),
 Module('المشرفون','admin/operators',Icons.admin_panel_settings),
 Module('صحة النظام','admin/system/health',Icons.health_and_safety),
 Module('إحصائيات مباشرة','admin/dashboard/live',Icons.monitor_heart),
 Module('إحصائيات الرسائل المدفوعة','admin/paid-messages/stats',Icons.query_stats),
 Module('احتمالات الألعاب','admin/games/odds',Icons.casino),
 Module('إعدادات إعلانات الألعاب','admin/games/ads',Icons.ads_click),
 Module('إعدادات دفع شام كاش','admin/payment-settings/sham-cash',Icons.payment),
];
class AdminHome extends StatefulWidget {const AdminHome({required this.token,required this.user,super.key});final String token;final Map<String,dynamic> user;@override State<AdminHome> createState()=>_AdminHomeState();}
class _AdminHomeState extends State<AdminHome>{
 late final api=Api(widget.token);int selected=0;String moduleSearch='';late Future<dynamic> future;
  List<Module> get visibleModules { final q=moduleSearch.trim().toLowerCase(); return q.isEmpty?modules:modules.where((m)=>m.title.toLowerCase().contains(q)).toList(); }
 @override void initState(){super.initState();load();}
 void load(){future=api.get(modules[selected].path);}
 void choose(int i){setState(() { selected = i; load(); });}
 @override Widget build(BuildContext context){final wide=MediaQuery.sizeOf(context).width>=950;return Scaffold(appBar:AppBar(title:Text(modules[selected].title),actions:[IconButton(onPressed:()=>setState(load),icon:const Icon(Icons.refresh)),IconButton(onPressed:()=>Navigator.pushAndRemoveUntil(context,MaterialPageRoute<void>(builder:(_)=>const AdminLogin()),(_)=>false),icon:const Icon(Icons.logout))]),body:Row(children:[if(wide)Container(width:235,color:const Color(0xFF211C14),child:Column(children:[const Padding(padding:EdgeInsets.all(20),child:Text('JEHO ADMIN',style:TextStyle(color:Colors.white,fontWeight:FontWeight.w900))),Padding(padding:const EdgeInsets.fromLTRB(12,0,12,8),child:TextField(onChanged:(value)=>setState(()=>moduleSearch=value),style:const TextStyle(color:Colors.white),decoration:InputDecoration(hintText:'ابحث عن قسم…',hintStyle:const TextStyle(color:Colors.white54,fontSize:13),prefixIcon:const Icon(Icons.search,color:Color(0xFFE4BB63)),filled:true,fillColor:Colors.white10,contentPadding:const EdgeInsets.symmetric(horizontal:10,vertical:8),border:OutlineInputBorder(borderRadius:BorderRadius.circular(12),borderSide:BorderSide.none)))),Expanded(child:ListView.builder(itemCount:visibleModules.length,itemBuilder:(c,i){final module=visibleModules[i];final index=modules.indexOf(module);return ListTile(selected:index==selected,leading:Icon(module.icon,color:index==selected?const Color(0xFFE4BB63):Colors.white70),title:Text(module.title,style:TextStyle(color:index==selected?Colors.white:Colors.white70)),onTap:()=>choose(index));})),Padding(padding:const EdgeInsets.all(12),child:Text((widget.user['name']??widget.user['email']??'مشرف').toString(),style:const TextStyle(color:Colors.white70)))])),Expanded(child:AdminContent(module:modules[selected],future:future,api:api,reload:()=>setState(load)))]),bottomNavigationBar:wide?null:NavigationBar(selectedIndex:selected<4?selected:4,onDestinationSelected:(index){if(index<4){choose(index);return;}showModalBottomSheet<int>(context:context,builder:(ctx)=>SafeArea(child:ListView(children:[for(var i=4;i<modules.length;i++)ListTile(leading:Icon(modules[i].icon),title:Text(modules[i].title),selected:i==selected,onTap:()=>Navigator.pop(ctx,i))]))).then((value){if(value!=null)choose(value);});},destinations:const[NavigationDestination(icon:Icon(Icons.dashboard),label:'الرئيسية'),NavigationDestination(icon:Icon(Icons.people),label:'المستخدمون'),NavigationDestination(icon:Icon(Icons.forum),label:'المحادثات'),NavigationDestination(icon:Icon(Icons.meeting_room),label:'الغرف'),NavigationDestination(icon:Icon(Icons.more_horiz),label:'المزيد')]));}
}
class AdminContent extends StatelessWidget {
 const AdminContent({required this.module,required this.future,required this.api,required this.reload,super.key});final Module module;final Future<dynamic> future;final Api api;final VoidCallback reload;
 List<dynamic> rows(dynamic v){if(v is List)return v;if(v is Map){for(final k in ['items','results','users','rooms','gifts','conversations','data']){if(v[k] is List)return v[k] as List;}}return const[];}
 String show(dynamic v)=>v==null?'—':v is Map||v is List?jsonEncode(v):v.toString();
 @override Widget build(BuildContext context)=>RefreshIndicator(onRefresh:()async=>reload(),child:ListView(padding:const EdgeInsets.all(20),children:[Wrap(spacing:10,runSpacing:10,children:[metric('القسم',module.title,module.icon),metric('الواجهة','Flutter Web',Icons.flutter_dash),metric('الربط','API مباشر',Icons.cloud_done)]),const SizedBox(height:18),if(module.path.startsWith('admin/notifications'))Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_composeAdminMessage(context),icon:const Icon(Icons.send_rounded),label:const Text('إنشاء رسالة إدارية وإرسالها'))),if(module.path.startsWith('admin/conversations'))Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_sendDirectMessage(context),icon:const Icon(Icons.mark_chat_read_outlined),label:const Text('إرسال رسالة مباشرة لمستخدم'))),if(module.path=='admin/vip/plans'||module.path=='vanity-ids/admin/all')Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_createCatalogItem(context,module.path),icon:const Icon(Icons.add_circle_outline_rounded),label:Text(module.path=='admin/vip/plans'?'إنشاء خطة VIP':'إنشاء آي دي مميز'))),
 if(module.path=='admin/gifts'||module.path=='admin/gift-categories')Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_createAdminCatalogRow(context,module.path),icon:const Icon(Icons.add_circle_outline_rounded),label:Text(module.path=='admin/gifts'?'إضافة هدية':'إضافة تصنيف هدايا'))),
 const SizedBox(height:10),FutureBuilder<dynamic>(future:future,builder:(c,s){if(s.connectionState==ConnectionState.waiting)return const Padding(padding:EdgeInsets.all(40),child:Center(child:CircularProgressIndicator()));if(s.hasError)return Card(child:Padding(padding:const EdgeInsets.all(20),child:Column(children:[Text(s.error.toString()),OutlinedButton(onPressed:reload,child:const Text('إعادة المحاولة'))])));final v=s.data;if(module.path=='admin/tasks'||module.path.startsWith('admin/games-store/catalog'))return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_editCollection(context,module.path,v),icon:const Icon(Icons.edit),label:Text(module.path=='admin/tasks'?'تعديل المهام':'تعديل كتالوج الألعاب'))),const SizedBox(height:10),Card(child:Padding(padding:const EdgeInsets.all(12),child:SelectableText(const JsonEncoder.withIndent('  ').convert(v))))]);final editPath=_editablePath(module.path);if(editPath!=null&&v is Map<String,dynamic>)return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_editSettings(context,editPath,v),icon:const Icon(Icons.edit),label:const Text('تعديل الإعدادات'))),const SizedBox(height:10),Card(child:Padding(padding:const EdgeInsets.all(12),child:SelectableText(const JsonEncoder.withIndent('  ').convert(v))))]);if(module.path=='admin/dashboard/overview'&&v is Map)return Wrap(spacing:10,runSpacing:10,children:v.entries.map((e)=>metric(e.key,show(e.value),Icons.insights)).toList());final items=rows(v);if(module.path.startsWith('admin/conversations'))return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[const Padding(padding:EdgeInsets.only(bottom:10),child:Text('مراقبة قراءة فقط · تعرض المحادثات وآخر نشاط والرسائل والمرفقات دون تغيير حالة القراءة.',style:TextStyle(color:Colors.black54))),...items.take(100).map((row){if(row is! Map)return Card(child:ListTile(title:Text(show(row))));final item=Map<String,dynamic>.from(row);final title=(item['title']??item['id']??'محادثة').toString();final participants=item['participants'] is List?(item['participants'] as List).map((p)=>p is Map?(p['displayName']??p['username']??p['id']??'مستخدم').toString():'').where((x)=>x.isNotEmpty).join('، '):'';final last=item['lastMessage'] is Map?item['lastMessage'] as Map:{};final preview=(last['content']??(last['media'] is Map?'مرفق':'')).toString();return Card(child:Padding(padding:const EdgeInsets.all(12),child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[Row(children:[const Icon(Icons.forum_outlined,color:gold),const SizedBox(width:8),Expanded(child:Text(title,style:const TextStyle(fontWeight:FontWeight.w900))),Text((item['messageCount']??0).toString()+' رسالة',style:const TextStyle(fontSize:11,color:Colors.black54))]),if(participants.isNotEmpty)Padding(padding:const EdgeInsets.only(top:5),child:Text(participants,maxLines:2,overflow:TextOverflow.ellipsis,style:const TextStyle(color:Colors.black54,fontSize:12))),if(preview.isNotEmpty)Padding(padding:const EdgeInsets.only(top:8),child:Text('آخر رسالة: '+preview,maxLines:2,overflow:TextOverflow.ellipsis)),if(item['lastMessageAt']!=null)Padding(padding:const EdgeInsets.only(top:4),child:Text('آخر نشاط: '+item['lastMessageAt'].toString(),style:const TextStyle(fontSize:11,color:Colors.black54))),Align(alignment:AlignmentDirectional.centerStart,child:OutlinedButton.icon(onPressed:()=>_showConversationMessages(context,item),icon:const Icon(Icons.visibility_outlined),label:const Text('عرض الرسائل والمرفقات')))])));})]);if(items.isEmpty)return Card(child:Padding(padding:const EdgeInsets.all(24),child:Column(children:[Icon(module.icon,size:42,color:gold),const SizedBox(height:8),const Text('لا توجد عناصر أو أن مسار هذا القسم غير متاح لهذا الحساب.',textAlign:TextAlign.center),SelectableText(show(v),style:const TextStyle(fontSize:12,color:Colors.black54))])));return Card(child:Column(children:[Padding(padding:const EdgeInsets.all(12),child:Text('العناصر ('+items.length.toString()+')')),...items.take(100).map((row){if(row is! Map)return ListTile(title:Text(show(row)));final title=(row['displayName']??row['username']??row['name']??row['title']??row['id']??'عنصر').toString();final sub=row.entries.take(4).map((e)=>e.key+': '+show(e.value)).join(' · ');return ExpansionTile(title:Text(title),subtitle:Text(sub,maxLines:2,overflow:TextOverflow.ellipsis),children:[Padding(padding:const EdgeInsets.all(12),child:SelectableText(const JsonEncoder.withIndent('  ').convert(row))),if(_actionsFor(module.path).isNotEmpty)Padding(padding:const EdgeInsets.fromLTRB(12,0,12,12),child:Wrap(spacing:8,runSpacing:8,children:_actionsFor(module.path).map((a)=>OutlinedButton.icon(onPressed:()=>_runAction(context,row,a),icon:Icon(a.$3),label:Text(a.$1))).toList()))]);})]));})]));
 Future<void> _sendDirectMessage(BuildContext context) async {
  final values=await _askValues(context,'إرسال رسالة مباشرة',['UUID أو Public ID للمستخدم','نص الرسالة']);
  if(values==null||!context.mounted)return;
  final lookup=values[0].trim();final message=values[1].trim();
  if(lookup.isEmpty||message.isEmpty){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('معرّف المستخدم ونص الرسالة مطلوبان.')));return;}
  final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد الرسالة المباشرة'),content:Text('سيتم إرسال رسالة فعلية إلى المستخدم المحدد: '+lookup),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('إرسال'))]));
  if(ok!=true)return;
  try{
   final found=await api.get('admin/users?page=1&limit=40&search='+Uri.encodeQueryComponent(lookup));
   final users=rows(found);
   Map? matched;
   for(final item in users){if(item is Map&&(item['id']?.toString()==lookup||item['publicId']?.toString()==lookup)){matched=item;break;}}
   final peerId=(matched?['id']??'').toString();
   if(peerId.isEmpty)throw Exception('لم يُعثر على مستخدم مطابق تمامًا لهذا المعرّف.');
   final started=await api.post('chat/conversations',{'peerId':peerId});
   final data=started is Map&&started['data'] is Map?started['data']:started;
   final conversationId=data is Map?(data['id']??'').toString():'';
   if(conversationId.isEmpty)throw Exception('الخادم لم يرجع معرّف محادثة صالحًا.');
   await api.post('chat/conversations/'+Uri.encodeComponent(conversationId)+'/messages',{'type':'text','content':message});
   if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم إرسال الرسالة المباشرة.')));reload();}
  }catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تعذر إرسال الرسالة: '+e.toString().replaceFirst('Exception: ',''))));}
 }
 Future<void> _createCatalogItem(BuildContext context,String path) async {
  final isVip=path=='admin/vip/plans';
  final values=await _askValues(context,isVip?'إنشاء خطة VIP':'إنشاء آي دي مميز',isVip?['اسم الخطة','مستوى VIP (رقم)','السعر']:['Public ID الجديد','السعر بالعملات']);
  if(values==null||!context.mounted)return;
  Map<String,dynamic> payload;
  String endpoint;
  if(isVip){
   final level=int.tryParse(values[1]);final price=num.tryParse(values[2]);
   if(values[0].isEmpty||level==null||level<1||price==null||price<0){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('أدخل اسم الخطة ومستوى صحيحًا وسعرًا غير سالب.')));return;}
   endpoint='admin/vip/plans';payload={'name':values[0],'level':level,'price':price,'isActive':true};
  }else{
   final price=int.tryParse(values[1]);
   if(values[0].isEmpty||price==null||price<0){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('أدخل Public ID وسعرًا صحيحًا غير سالب.')));return;}
   endpoint='vanity-ids/admin';payload={'publicId':values[0],'priceCoins':price};
  }
  final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد الإنشاء'),content:Text('سيتم إنشاء '+(isVip?'خطة VIP':'آي دي مميز')+' جديدة في الخادم.'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('إنشاء'))]));
  if(ok!=true)return;
  try{await api.post(endpoint,payload);if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم إنشاء العنصر.')));reload();}}
  catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));}
 }
 Future<String?> _composeAdminMessage(BuildContext context) async {
  final title=TextEditingController();
  final body=TextEditingController();
  final target=TextEditingController();
  final link=TextEditingController();
  final image=TextEditingController();
  final b1t=TextEditingController();final b1u=TextEditingController();
  final b2t=TextEditingController();final b2u=TextEditingController();
  try {
   final values=await showDialog<Map<String,String>>(context:context,builder:(ctx)=>AlertDialog(
    title:const Text('رسالة الإدارة'),
    content:SizedBox(width:620,child:SingleChildScrollView(child:Column(mainAxisSize:MainAxisSize.min,children:[
     TextField(controller:title,decoration:const InputDecoration(labelText:'عنوان الرسالة')),
     TextField(controller:body,minLines:3,maxLines:6,decoration:const InputDecoration(labelText:'محتوى الرسالة')),
     TextField(controller:target,decoration:const InputDecoration(labelText:'معرّف مستخدم أو Public ID (فارغ = للجميع)')),
     TextField(controller:link,decoration:const InputDecoration(labelText:'رابط HTTPS أو رقم روم (اختياري)')),
     TextField(controller:image,decoration:const InputDecoration(labelText:'رابط صورة HTTPS (اختياري)')),
     const Divider(),
     TextField(controller:b1t,decoration:const InputDecoration(labelText:'عنوان الزر الأول (اختياري)')),
     TextField(controller:b1u,decoration:const InputDecoration(labelText:'رابط الزر الأول')),
     TextField(controller:b2t,decoration:const InputDecoration(labelText:'عنوان الزر الثاني (اختياري)')),
     TextField(controller:b2u,decoration:const InputDecoration(labelText:'رابط الزر الثاني')),
    ]))),
    actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,{'title':title.text.trim(),'body':body.text.trim(),'target':target.text.trim(),'link':link.text.trim(),'image':image.text.trim(),'b1t':b1t.text.trim(),'b1u':b1u.text.trim(),'b2t':b2t.text.trim(),'b2u':b2u.text.trim()}),child:const Text('مراجعة'))],
   ));
   if(values==null||!context.mounted)return null;
   final rawLink=values['link']!;
   final numericRoom=rawLink.isNotEmpty&&rawLink.split('').every((c)=>'0123456789'.contains(c));
   final resolvedLink=numericRoom?'https://jeho-own-backend.mgs.blitz.cloud/open/room/'+rawLink:rawLink;
   final links=[resolvedLink,values['image']!,values['b1u']!,values['b2u']!].where((v)=>v.isNotEmpty);
   final invalid=links.any((v){final u=Uri.tryParse(v);return u==null||u.scheme!='https'||u.host.isEmpty;});
   if(values['title']!.isEmpty||values['body']!.isEmpty||(values['b1t']!.isEmpty!=values['b1u']!.isEmpty)||(values['b2t']!.isEmpty!=values['b2u']!.isEmpty)||invalid){
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('أكمل العنوان والمحتوى، وأدخل أزرارًا مكتملة وروابط HTTPS صحيحة.')));
    return null;
   }
   final targetText=values['target']!.isEmpty?'جميع المستخدمين':'المستخدم '+values['target']!;
   final confirmed=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(
    title:const Text('تأكيد الإرسال'),
    content:Text('سيتم إرسال الرسالة إلى '+targetText+'. هذه العملية ترسل إشعارًا فعليًا. هل تريد المتابعة؟'),
    actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('رجوع')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('إرسال'))],
   ));
   if(confirmed!=true)return null;
   String esc(String v)=>v.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;');
   final buttons=[(values['b1t']!,values['b1u']!),(values['b2t']!,values['b2u']!)].where((v)=>v.$1.isNotEmpty&&v.$2.isNotEmpty).toList();
   final html='<div style="font-family:Arial,sans-serif;line-height:1.6"><p>'+esc(values['body']!)+'</p>'+ (values['image']!.isNotEmpty?'<img src="'+esc(values['image']!)+'" style="max-width:100%;border-radius:12px"/>':'')+buttons.map((b)=>'<p><a href="'+esc(b.$2)+'">'+esc(b.$1)+'</a></p>').join()+'</div>';
   final payload=<String,String>{'title':values['title']!,'body':values['body']!,'type':'chat','channel':'both','bodyHtml':html,'audience':values['target']!.isEmpty?'all':'user'};
   if(values['target']!.isNotEmpty)payload['userId']=values['target']!;
   if(resolvedLink.isNotEmpty)payload['link']=resolvedLink;
   if(values['image']!.isNotEmpty)payload['imageUrl']=values['image']!;
   await api.post('notifications/admin-messages/send',payload);
   if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم إرسال طلب الرسالة إلى الخادم.')));reload();}
   return 'sent';
  } catch(e) {
   if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تعذر إرسال الرسالة: '+e.toString().replaceFirst('Exception: ',''))));
   return null;
  } finally {
   for(final c in [title,body,target,link,image,b1t,b1u,b2t,b2u]){c.dispose();}
  }
 }
 Future<void> _showConversationMessages(BuildContext context,Map<String,dynamic> conversation) async {
  final id=(conversation['id']??'').toString();
  if(id.isEmpty){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('معرّف المحادثة غير موجود.')));return;}
  showDialog<void>(context:context,barrierDismissible:false,builder:(ctx)=>const AlertDialog(content:Row(children:[CircularProgressIndicator(),SizedBox(width:16),Expanded(child:Text('جارٍ تحميل سجل الرسائل للقراءة فقط…'))])));
  try{
   final result=await api.get('admin/conversations/'+Uri.encodeComponent(id)+'/messages?page=1&limit=100');
   if(context.mounted)Navigator.of(context,rootNavigator:true).pop();
   final messages=rows(result);
   if(!context.mounted)return;
   await showDialog<void>(context:context,builder:(ctx)=>AlertDialog(
    title:Text('سجل المحادثة · '+(conversation['title']??conversation['id']??'').toString(),maxLines:2,overflow:TextOverflow.ellipsis),
    content:SizedBox(width:760,height:560,child:messages.isEmpty?const Center(child:Text('لا توجد رسائل ظاهرة في هذه الصفحة.')):ListView.separated(
     itemCount:messages.length,separatorBuilder:(_,__)=>const Divider(height:16),itemBuilder:(context,index){
      final message=messages[index];if(message is! Map)return ListTile(title:Text(show(message)));
      final sender=message['sender'] is Map?message['sender'] as Map:{};
      final media=message['media'] is Map?message['media'] as Map:{};
      final content=(message['content']??'').toString();
      final mediaUrl=(media['url']??media['uri']??'').toString();
      final type=(message['type']??'text').toString();
      final sentAt=(message['createdAt']??message['created_at']??'').toString();
      return Container(padding:const EdgeInsets.all(12),decoration:BoxDecoration(color:const Color(0xFFFFF9EB),borderRadius:BorderRadius.circular(12),border:Border.all(color:const Color(0xFFE8DCC5))),child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[
       Row(children:[const Icon(Icons.account_circle_outlined,color:gold),const SizedBox(width:8),Expanded(child:Text((sender['displayName']??sender['username']??message['senderId']??'مستخدم').toString(),style:const TextStyle(fontWeight:FontWeight.w800))),Flexible(child:Text(sentAt.length>16?sentAt.replaceFirst('T',' ').substring(0,16):sentAt,style:const TextStyle(fontSize:11,color:Colors.black54),overflow:TextOverflow.ellipsis))]),
       const SizedBox(height:8),if(content.isNotEmpty)SelectableText(content),
       if(mediaUrl.isNotEmpty)...[const SizedBox(height:8),Row(children:[Icon(type=='image'?Icons.image_outlined:type=='video'?Icons.video_file_outlined:Icons.attach_file_rounded,color:gold),const SizedBox(width:6),Expanded(child:SelectableText(mediaUrl))])],
       if(content.isEmpty&&mediaUrl.isEmpty)Text('رسالة من النوع: '+type,style:const TextStyle(color:Colors.black54)),
      ]));
     })),
    actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('إغلاق'))],
   ));
  }catch(e){
   if(context.mounted){Navigator.of(context,rootNavigator:true).pop();ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تعذر تحميل سجل المحادثة: '+e.toString().replaceFirst('Exception: ',''))));}
  }
 }
 Future<void> _editCollection(BuildContext context,String endpoint,dynamic current) async {
  final initial=current is Map&&current['items'] is List?current['items']:current;
  if(initial is! List){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تنسيق البيانات الحالي لا يسمح بتعديل آمن من هذه الواجهة.')));return;}
  final controller=TextEditingController(text:const JsonEncoder.withIndent('  ').convert(initial));
  try{
   final raw=await showDialog<String>(context:context,builder:(ctx)=>AlertDialog(title:Text(endpoint=='admin/tasks'?'تعديل المهام اليومية':'تعديل كتالوج الألعاب'),content:SizedBox(width:620,child:TextField(controller:controller,minLines:10,maxLines:20,keyboardType:TextInputType.multiline,decoration:const InputDecoration(helperText:'أدخل مصفوفة JSON صالحة. احتفظ بمعرّفات العناصر والحقول المطلوبة.'),style:const TextStyle(fontFamily:'monospace'))),actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,controller.text),child:const Text('متابعة'))]));
   if(raw==null||!context.mounted)return;
   final decoded=jsonDecode(raw);if(decoded is! List)throw const FormatException('يجب أن تكون البيانات مصفوفة JSON.');
   final confirmed=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد حفظ القائمة'),content:Text('سيتم استبدال قائمة '+(endpoint=='admin/tasks'?'المهام':'الألعاب')+' الحالية بالقائمة المدخلة. تأكد من مراجعتها قبل الحفظ.'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('رجوع')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('حفظ'))]));
   if(confirmed!=true)return;
   final body=endpoint=='admin/tasks'?<String,dynamic>{'items':decoded,...(current is Map&&current['enabled'] is bool?{'enabled':current['enabled']}:<String,dynamic>{})}:<String,dynamic>{'items':decoded};
   await api.put(endpoint,body);
   if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم حفظ القائمة من الخادم.')));reload();}
  }catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تعذر الحفظ: '+e.toString().replaceFirst('Exception: ',''))));}
  finally{controller.dispose();}
 }
 String? _editablePath(String path){
  const allowed={'admin/paid-messages/settings':'admin/paid-messages/settings','admin/economy/settings':'admin/economy/settings','admin/games/odds':'admin/games/odds','admin/games/ads':'admin/games/ads','admin/drama/settings':'admin/drama/settings','admin/recharge-agent-pricing':'admin/recharge-agent-pricing'};
  return allowed[path];
 }
 Future<void> _editSettings(BuildContext context,String endpoint,Map<String,dynamic> current) async {
  final controller=TextEditingController(text:const JsonEncoder.withIndent('  ').convert(current));
  try{
   final raw=await showDialog<String>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تعديل الإعدادات بصيغة JSON'),content:SizedBox(width:620,child:TextField(controller:controller,minLines:10,maxLines:20,keyboardType:TextInputType.multiline,decoration:const InputDecoration(helperText:'أرسل كائن JSON صالحًا فقط. تجنّب تعديل أي أسرار أو إعدادات صوت.'),style:const TextStyle(fontFamily:'monospace'))),actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,controller.text),child:const Text('متابعة'))]));
   if(raw==null||!context.mounted)return;
   final decoded=jsonDecode(raw);if(decoded is! Map<String,dynamic>)throw const FormatException('يجب أن يكون المحتوى كائن JSON.');
   final confirmed=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد حفظ الإعدادات'),content:Text('سيتم إرسال تغييرات إلى '+endpoint+' فقط. هل تريد المتابعة؟'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('رجوع')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('حفظ'))]));
   if(confirmed!=true)return;
   await api.patch(endpoint,decoded);
   if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم إرسال الإعدادات وحفظها من الخادم.')));reload();}
  }catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تعذر الحفظ: '+e.toString().replaceFirst('Exception: ',''))));}
  finally{controller.dispose();}
 }
 List<(String,String,IconData)> _actionsFor(String path){
  if(path.startsWith('admin/users'))return [('حظر المستخدم','ban',Icons.block),('إلغاء الحظر','unban',Icons.check_circle_outline),('تعديل الصلاحية والحالة','user-access',Icons.manage_accounts),('تعديل المحفظة','wallet-adjust',Icons.account_balance_wallet)];
  if(path.startsWith('admin/agencies/applications'))return [('قبول الطلب','agency-approve',Icons.check_circle),('رفض الطلب','agency-reject',Icons.cancel)];
  if(path.startsWith('admin/rooms'))return [('تعديل الغرفة','room-edit',Icons.edit),('إغلاق الغرفة','room-close',Icons.meeting_room)];
  if(path.startsWith('admin/reports'))return [('حل البلاغ','report-resolve',Icons.done_all),('رفض البلاغ','report-dismiss',Icons.remove_circle_outline)];
  if(path.startsWith('admin/wallet/withdraws'))return [('الموافقة على السحب','withdraw-approve',Icons.check_circle),('رفض السحب','withdraw-reject',Icons.cancel)];
  if(path.startsWith('admin/wallet/recharges'))return [('تأكيد الشحنة','recharge-complete',Icons.verified),('إلغاء الشحنة','recharge-cancel',Icons.cancel)];
  if(path.startsWith('admin/gender-verifications'))return [('قبول التحقق','gender-approve',Icons.verified_user),('رفض التحقق','gender-reject',Icons.cancel)];
  if(path.startsWith('admin/vip/plans'))return [('منح هذه الخطة لمستخدم','vip-assign',Icons.workspace_premium)];
  if(path.startsWith('admin/vip?'))return [('إلغاء عضوية VIP','vip-revoke',Icons.remove_circle_outline)];
  if(path.startsWith('admin/cosmetics'))return [('تفعيل/تعطيل المظهر','cosmetic-toggle',Icons.toggle_on)];
  if(path.startsWith('vanity-ids/admin/all'))return [('تفعيل الآي دي','vanity-enable',Icons.check_circle),('تعطيل الآي دي','vanity-disable',Icons.pause_circle_outline),('تحرير الآي دي من مالكه','vanity-revoke',Icons.remove_circle_outline)];
  if(path.startsWith('admin/agencies?'))return [('تعليق الوكالة','agency-suspend',Icons.pause_circle_outline)];
  if(path.startsWith('admin/recharge-agents/applications'))return [('قبول طلب الوكيل','agent-approve',Icons.check_circle),('رفض طلب الوكيل','agent-reject',Icons.cancel)];
  if(path.startsWith('admin/contests'))return [('إنهاء المسابقة','contest-end',Icons.flag_circle)];
  if(path.startsWith('admin/streams'))return [('إنهاء البث','stream-end',Icons.stop_circle)];
  if(path=='admin/gifts')return [('تعديل الهدية','gift-edit',Icons.edit),('حذف الهدية','gift-delete',Icons.delete_outline)];
  if(path=='admin/gift-categories')return [('تعديل التصنيف','gift-category-edit',Icons.edit),('حذف التصنيف','gift-category-delete',Icons.delete_outline)];
  return const [];
 }
 Future<void> _createAdminCatalogRow(BuildContext context,String path) async {
  final isGift=path=='admin/gifts';
  final initial=isGift
   ? <String,dynamic>{'name':'','iconUrl':'','coinPrice':0,'diamondValue':0,'type':'NORMAL','category':'normal','isActive':true,'sortOrder':0}
   : <String,dynamic>{'key':'','labelAr':'','labelEn':'','sortOrder':0,'isActive':true};
  await _editAdminCatalogRow(context,path,initial,isGift?'إضافة هدية':'إضافة تصنيف هدايا',create:true);
 }
 Future<void> _editAdminCatalogRow(BuildContext context,String path,Map<String,dynamic> row,String title,{bool create=false}) async {
  final controller=TextEditingController(text:const JsonEncoder.withIndent('  ').convert(row));
  try{
   final raw=await showDialog<String>(context:context,builder:(ctx)=>AlertDialog(title:Text(title),content:SizedBox(width:650,child:SingleChildScrollView(child:TextField(controller:controller,minLines:10,maxLines:22,keyboardType:TextInputType.multiline,decoration:const InputDecoration(helperText:'راجع الحقول والقيم وفق مخطط الخادم. لن تُحفظ التغييرات إلا بعد التأكيد.'),style:const TextStyle(fontFamily:'monospace')))),actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,controller.text),child:const Text('متابعة'))]));
   if(raw==null||!context.mounted)return;
   final decoded=jsonDecode(raw);if(decoded is! Map<String,dynamic>)throw const FormatException('يجب إدخال كائن JSON صالح.');
   if(path=='admin/gifts'){
    if((decoded['name']??'').toString().trim().isEmpty||(decoded['iconUrl']??'').toString().trim().isEmpty)throw const FormatException('اسم الهدية ورابط الصورة مطلوبان.');
    if(int.tryParse((decoded['coinPrice']??'').toString())==null)throw const FormatException('سعر العملات يجب أن يكون عددًا صحيحًا.');
    if(decoded['diamondValue']!=null&&int.tryParse(decoded['diamondValue'].toString())==null)throw const FormatException('قيمة الألماس يجب أن تكون عددًا صحيحًا.');
   } else if((decoded['key']??'').toString().trim().isEmpty||(decoded['labelAr']??'').toString().trim().isEmpty) {
    throw const FormatException('مفتاح التصنيف واسمه العربي labelAr مطلوبان.');
   }
   final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد الحفظ'),content:Text((create?'سيُنشأ عنصر جديد في ':'ستُحدّث بيانات ') + (path=='admin/gifts'?'كتالوج الهدايا.':'تصنيفات الهدايا.')),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('رجوع')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('حفظ'))]));
   if(ok!=true)return;
   if(create){await api.post(path,decoded);}else{final id=(decoded['id']??row['id']??'').toString();if(id.isEmpty)throw const FormatException('معرّف العنصر مفقود.');await api.patch(path+'/'+Uri.encodeComponent(id),decoded);}
   if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(create?'تم إنشاء العنصر.':'تم تحديث العنصر.')));reload();}
  }catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تعذر الحفظ: '+e.toString().replaceFirst('Exception: ',''))));}
  finally{controller.dispose();}
 }
 Future<void> _editGiftCategory(BuildContext context,String id,Map<String,dynamic> row) async {
  final controller=TextEditingController(text:const JsonEncoder.withIndent('  ').convert(row));
  try{
   final raw=await showDialog<String>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تعديل تصنيف الهدية'),content:SizedBox(width:600,child:TextField(controller:controller,minLines:8,maxLines:18,keyboardType:TextInputType.multiline,decoration:const InputDecoration(helperText:'عدّل حقول التصنيف مع الإبقاء على الحقول المطلوبة ومعرّف العنصر.'),style:const TextStyle(fontFamily:'monospace'))),actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,controller.text),child:const Text('متابعة'))]));
   if(raw==null||!context.mounted)return;
   final decoded=jsonDecode(raw);if(decoded is! Map<String,dynamic>)throw const FormatException('يجب إدخال كائن JSON صالح.');
   final confirmed=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد تعديل التصنيف'),content:Text('سيتم تحديث تصنيف الهدية رقم '+id+'.'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('رجوع')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('حفظ'))]));
   if(confirmed!=true)return;
   await api.patch('admin/gift-categories/'+id,decoded);
   if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم تحديث تصنيف الهدية.')));reload();}
  }catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تعذر تحديث التصنيف: '+e.toString().replaceFirst('Exception: ',''))));}
  finally{controller.dispose();}
 }
 Future<List<String>?> _askValues(BuildContext context,String title,List<String> labels) async {
  final controllers=labels.map((_)=>TextEditingController()).toList();
  try {
    return await showDialog<List<String>>(context:context,builder:(ctx)=>AlertDialog(
      title:Text(title),
      content:SizedBox(width:440,child:SingleChildScrollView(child:Column(mainAxisSize:MainAxisSize.min,children:[
        for(var i=0;i<labels.length;i++) Padding(padding:const EdgeInsets.only(bottom:10),child:TextField(controller:controllers[i],decoration:InputDecoration(labelText:labels[i]),textDirection:i==0?TextDirection.ltr:TextDirection.rtl))
      ]))),
      actions:[TextButton(onPressed:()=>Navigator.pop(ctx),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,controllers.map((c)=>c.text.trim()).toList()),child:const Text('متابعة'))],
    ));
  } finally { for(final c in controllers){c.dispose();} }
 }
 Future<void> _grantAction(BuildContext context,Map row,String action,String rowId) async {
  final values=await _askValues(context,'منح خطة VIP',['معرّف المستخدم','معرّف خطة VIP']);
  if(values==null||!context.mounted)return;
  final userId=values[0].trim();final planId=values[1].trim().isEmpty?rowId:values[1].trim();
  if(userId.isEmpty||planId.isEmpty){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('معرّف المستخدم ومعرّف خطة VIP مطلوبان.')));return;}
  final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد منح VIP'),content:Text('سيتم منح الخطة للمستخدم '+userId+'. هل تؤكد؟'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('تأكيد'))]));
  if(ok!=true)return;
  try{await api.post('admin/vip/assign',{'userId':userId,'planId':planId});if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم منح خطة VIP.')));reload();}}
  catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));}
 }
 Future<void> _editAdminRow(BuildContext context,Map row,String action,String id) async {
  if(action=='user-access'){
   final values=await _askValues(context,'تعديل صلاحية المستخدم',['الدور: user / support / admin / super_admin','الحالة: active / suspended / banned']);
   if(values==null||!context.mounted)return;
   const roles=['user','support','admin','super_admin'];const statuses=['active','suspended','banned'];
   if(!roles.contains(values[0])||!statuses.contains(values[1])){
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('القيم المقبولة للدور: user أو support أو admin أو super_admin، وللحالة: active أو suspended أو banned.')));return;
   }
   final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد تعديل الصلاحية'),content:Text('سيتم تحديث دور المستخدم إلى '+values[0]+' وحالته إلى '+values[1]+'.'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('تأكيد'))]));
   if(ok!=true)return;
   try{await api.patch('admin/users/'+id,{'staffRole':values[0],'status':values[1]});if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم تحديث صلاحية المستخدم.')));reload();}}
   catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));}
   return;
  }
  if(action=='wallet-adjust'){
   final values=await _askValues(context,'تعديل محفظة المستخدم',['تغيير العملات (+/-)','تغيير الألماس (+/-)','تغيير العملات الفضية (+/-)','تغيير نقاط اللعب (+/-)','السبب / ملاحظة إلزامية']);
   if(values==null||!context.mounted)return;
   final coins=int.tryParse(values[0])??0;final diamonds=int.tryParse(values[1])??0;final silver=int.tryParse(values[2])??0;final points=int.tryParse(values[3])??0;final note=values[4];
   if(note.isEmpty||(coins==0&&diamonds==0&&silver==0&&points==0)){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('أدخل تغييرًا غير صفري وسببًا واضحًا.')));return;}
   final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد تعديل المحفظة'),content:Text('عملات: $coins، ألماس: $diamonds، فضية: $silver، نقاط: $points. العملية تؤثر فعليًا على رصيد المستخدم.'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('رجوع')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('تنفيذ'))]));
   if(ok!=true)return;
   try{await api.post('admin/wallet/'+id+'/adjust',{'coinsDelta':coins,'diamondsDelta':diamonds,'silverCoinsDelta':silver,'gamePointsDelta':points,'note':note,'reason':note});if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم إرسال تعديل المحفظة.')));reload();}}
   catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));}
   return;
  }
  if(action=='room-edit'){
   final values=await _askValues(context,'تعديل بيانات الغرفة',['اسم الغرفة: '+(row['title']??row['name']??'' ).toString(),'نوع الدخول: public أو private','رسوم الدخول بالعملات: '+(row['entryFeeCoins']??0).toString()]);
   if(values==null||!context.mounted)return;
   final mode=values[1].toLowerCase();final fee=int.tryParse(values[2]);
   if(values[0].trim().isEmpty||!['public','private'].contains(mode)||fee==null||fee<0){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تحقق من اسم الغرفة ونوع الدخول ورسومه.')));return;}
   final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:const Text('تأكيد تعديل الغرفة'),content:const Text('سيتم تحديث بيانات الغرفة على الخادم.'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('حفظ'))]));
   if(ok!=true)return;
   try{await api.patch('admin/rooms/'+id,{'title':values[0].trim(),'accessMode':mode,'entryFeeCoins':fee});if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم تحديث الغرفة.')));reload();}}
   catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));}
  }
 }
 Future<void> _runAction(BuildContext context,Map row,(String,String,IconData) action) async {
  final id=(row['id']??row['applicationId']??row['reportId']??row['streamId']??row['userId']??'').toString();
  if(id.isEmpty){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('لا يوجد معرّف واضح لهذا العنصر.')));return;}
  if(action.$2=='gift-category-edit'){await _editGiftCategory(context,id,Map<String,dynamic>.from(row));return;}
  if(action.$2=='gift-edit'){await _editAdminCatalogRow(context,'admin/gifts/'+id,Map<String,dynamic>.from(row),'تعديل الهدية');return;}
  if(action.$2=='gift-delete'||action.$2=='gift-category-delete'){
   final isGift=action.$2=='gift-delete';
   final ok=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:Text(isGift?'حذف الهدية':'حذف تصنيف الهدايا'),content:Text('هذا إجراء فعلي على الخادم. هل تريد حذف «'+(row['name']??row['title']??id).toString()+'»؟'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('حذف'))]));
   if(ok!=true)return;
   try{await api.delete((isGift?'admin/gifts/':'admin/gift-categories/')+Uri.encodeComponent(id));if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم إرسال طلب الحذف إلى الخادم.')));reload();}}
   catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تعذر الحذف: '+e.toString().replaceFirst('Exception: ',''))));}
   return;
  }
  if(['user-access','wallet-adjust','room-edit'].contains(action.$2)){await _editAdminRow(context,row,action.$2,id);return;}
  if(action.$2=='vip-assign'){await _grantAction(context,row,action.$2,id);return;}
  final confirm=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:Text(action.$1),content:Text('تأكيد تنفيذ «'+action.$1+'» للعنصر '+id+'؟'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('تأكيد'))]));
  if(confirm!=true)return;
  String endpoint;Map<String,dynamic> body=const {};
  switch(action.$2){
   case 'ban': endpoint='admin/users/$id/ban'; body={'reason':'إجراء إداري من لوحة JEHO'}; break;
   case 'unban': endpoint='admin/users/$id/unban'; break;
   case 'agency-approve': endpoint='admin/agencies/applications/$id/approve'; break;
   case 'agency-reject': endpoint='admin/agencies/applications/$id/reject'; break;
   case 'report-resolve': endpoint='admin/reports/$id/resolve'; break;
   case 'report-dismiss': endpoint='admin/reports/$id/dismiss'; break;
   case 'withdraw-approve': endpoint='admin/wallet/withdraws/$id/approve'; break;
   case 'withdraw-reject': endpoint='admin/wallet/withdraws/$id/reject'; body={'reason':'رفض من لوحة الإدارة'}; break;
   case 'recharge-complete': endpoint='admin/wallet/recharges/$id/complete'; break;
   case 'recharge-cancel': endpoint='admin/wallet/recharges/$id/cancel'; break;
   case 'gender-approve': endpoint='admin/gender-verifications/$id/approve'; break;
   case 'vip-revoke': endpoint='admin/vip/$id/revoke'; break;
   case 'room-close': endpoint='admin/rooms/$id/close'; break;
   case 'vanity-enable': endpoint='vanity-ids/admin/$id/enable'; break;
   case 'vanity-disable': endpoint='vanity-ids/admin/$id/disable'; break;
   case 'vanity-revoke': endpoint='vanity-ids/admin/$id/release'; break;
   case 'cosmetic-toggle': endpoint='admin/cosmetics/$id'; body={'isActive': !(row['isActive']==true)}; break;
   case 'agency-suspend': endpoint='admin/agencies/$id/suspend'; body={'reason':'تعليق من لوحة الإدارة'}; break;
   case 'agent-approve': endpoint='admin/recharge-agents/applications/$id/approve'; break;
   case 'agent-reject': endpoint='admin/recharge-agents/applications/$id/reject'; body={'reason':'رفض من لوحة الإدارة'}; break;
   case 'contest-end': endpoint='admin/contests/$id/end'; break;
   case 'stream-end': endpoint='admin/streams/$id/end'; break;
   case 'gender-reject': endpoint='admin/gender-verifications/$id/reject'; break;
   default: throw StateError('إجراء غير معروف: '+action.$2);
  }
  try{if(action.$2=='cosmetic-toggle'){await api.patch(endpoint,body);}else{await api.post(endpoint,body);}if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تم تنفيذ: '+action.$1)));reload();}}
  catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));}
 }
 Widget metric(String label,String value,IconData icon)=>SizedBox(width:220,child:Card(child:Padding(padding:const EdgeInsets.all(16),child:Row(children:[Icon(icon,color:gold,size:26),const SizedBox(width:10),Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(label,style:const TextStyle(color:Colors.black54)),Text(value,maxLines:2,overflow:TextOverflow.ellipsis,style:const TextStyle(fontWeight:FontWeight.w800))]))]))));
}
