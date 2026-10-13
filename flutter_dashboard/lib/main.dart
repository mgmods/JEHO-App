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
 @override Widget build(BuildContext context)=>RefreshIndicator(onRefresh:()async=>reload(),child:ListView(padding:const EdgeInsets.all(20),children:[Wrap(spacing:10,runSpacing:10,children:[metric('القسم',module.title,module.icon),metric('الواجهة','Flutter Web',Icons.flutter_dash),metric('الربط','API مباشر',Icons.cloud_done)]),const SizedBox(height:18),FutureBuilder<dynamic>(future:future,builder:(c,s){if(s.connectionState==ConnectionState.waiting)return const Padding(padding:EdgeInsets.all(40),child:Center(child:CircularProgressIndicator()));if(s.hasError)return Card(child:Padding(padding:const EdgeInsets.all(20),child:Column(children:[Text(s.error.toString()),OutlinedButton(onPressed:reload,child:const Text('إعادة المحاولة'))])));final v=s.data;if(module.path=='admin/tasks'||module.path.startsWith('admin/games-store/catalog'))return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_editCollection(context,module.path,v),icon:const Icon(Icons.edit),label:Text(module.path=='admin/tasks'?'تعديل المهام':'تعديل كتالوج الألعاب'))),const SizedBox(height:10),Card(child:Padding(padding:const EdgeInsets.all(12),child:SelectableText(const JsonEncoder.withIndent('  ').convert(v))))]);final editPath=_editablePath(module.path);if(editPath!=null&&v is Map<String,dynamic>)return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_editSettings(context,editPath,v),icon:const Icon(Icons.edit),label:const Text('تعديل الإعدادات'))),const SizedBox(height:10),Card(child:Padding(padding:const EdgeInsets.all(12),child:SelectableText(const JsonEncoder.withIndent('  ').convert(v))))]);if(module.path=='admin/dashboard/overview'&&v is Map)return Wrap(spacing:10,runSpacing:10,children:v.entries.map((e)=>metric(e.key,show(e.value),Icons.insights)).toList());final items=rows(v);if(module.path.startsWith('admin/conversations'))return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[const Padding(padding:EdgeInsets.only(bottom:10),child:Text('مراقبة قراءة فقط · تعرض المحادثات وآخر نشاط والرسائل والمرفقات دون تغيير حالة القراءة.',style:TextStyle(color:Colors.black54))),...items.take(100).map((row){if(row is! Map)return Card(child:ListTile(title:Text(show(row))));final item=Map<String,dynamic>.from(row);final title=(item['title']??item['id']??'محادثة').toString();final participants=item['participants'] is List?(item['participants'] as List).map((p)=>p is Map?(p['displayName']??p['username']??p['id']??'مستخدم').toString():'').where((x)=>x.isNotEmpty).join('، '):'';final last=item['lastMessage'] is Map?item['lastMessage'] as Map:{};final preview=(last['content']??(last['media'] is Map?'مرفق':'')).toString();return Card(child:Padding(padding:const EdgeInsets.all(12),child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[Row(children:[const Icon(Icons.forum_outlined,color:gold),const SizedBox(width:8),Expanded(child:Text(title,style:const TextStyle(fontWeight:FontWeight.w900))),Text((item['messageCount']??0).toString()+' رسالة',style:const TextStyle(fontSize:11,color:Colors.black54))]),if(participants.isNotEmpty)Padding(padding:const EdgeInsets.only(top:5),child:Text(participants,maxLines:2,overflow:TextOverflow.ellipsis,style:const TextStyle(color:Colors.black54,fontSize:12))),if(preview.isNotEmpty)Padding(padding:const EdgeInsets.only(top:8),child:Text('آخر رسالة: '+preview,maxLines:2,overflow:TextOverflow.ellipsis)),if(item['lastMessageAt']!=null)Padding(padding:const EdgeInsets.only(top:4),child:Text('آخر نشاط: '+item['lastMessageAt'].toString(),style:const TextStyle(fontSize:11,color:Colors.black54))),Align(alignment:AlignmentDirectional.centerStart,child:OutlinedButton.icon(onPressed:()=>_showConversationMessages(context,item),icon:const Icon(Icons.visibility_outlined),label:const Text('عرض الرسائل والمرفقات')))])));})]);if(items.isEmpty)return Card(child:Padding(padding:const EdgeInsets.all(24),child:Column(children:[Icon(module.icon,size:42,color:gold),const SizedBox(height:8),const Text('لا توجد عناصر أو أن مسار هذا القسم غير متاح لهذا الحساب.',textAlign:TextAlign.center),SelectableText(show(v),style:const TextStyle(fontSize:12,color:Colors.black54))])));return Card(child:Column(children:[Padding(padding:const EdgeInsets.all(12),child:Text('العناصر ('+items.length.toString()+')')),...items.take(100).map((row){if(row is! Map)return ListTile(title:Text(show(row)));final title=(row['displayName']??row['username']??row['name']??row['title']??row['id']??'عنصر').toString();final sub=row.entries.take(4).map((e)=>e.key+': '+show(e.value)).join(' · ');return ExpansionTile(title:Text(title),subtitle:Text(sub,maxLines:2,overflow:TextOverflow.ellipsis),children:[Padding(padding:const EdgeInsets.all(12),child:SelectableText(const JsonEncoder.withIndent('  ').convert(row))),if(_actionsFor(module.path).isNotEmpty)Padding(padding:const EdgeInsets.fromLTRB(12,0,12,12),child:Wrap(spacing:8,runSpacing:8,children:_actionsFor(module.path).map((a)=>OutlinedButton.icon(onPressed:()=>_runAction(context,row,a),icon:Icon(a.$3),label:Text(a.$1))).toList()))]);})]));})]));
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
  if(path.startsWith('admin/users'))return [('حظر المستخدم','ban',Icons.block),('إلغاء الحظر','unban',Icons.check_circle_outline)];
  if(path.startsWith('admin/agencies/applications'))return [('قبول الطلب','agency-approve',Icons.check_circle),('رفض الطلب','agency-reject',Icons.cancel)];
  if(path.startsWith('admin/reports'))return [('حل البلاغ','report-resolve',Icons.done_all),('رفض البلاغ','report-dismiss',Icons.remove_circle_outline)];
  if(path.startsWith('admin/wallet/withdraws'))return [('الموافقة على السحب','withdraw-approve',Icons.check_circle),('رفض السحب','withdraw-reject',Icons.cancel)];
  if(path.startsWith('admin/wallet/recharges'))return [('تأكيد الشحنة','recharge-complete',Icons.verified),('إلغاء الشحنة','recharge-cancel',Icons.cancel)];
  if(path.startsWith('admin/gender-verifications'))return [('قبول التحقق','gender-approve',Icons.verified_user),('رفض التحقق','gender-reject',Icons.cancel)];
  if(path.startsWith('admin/vip/plans'))return [('منح هذه الخطة لمستخدم','vip-assign',Icons.workspace_premium)];
  if(path.startsWith('admin/vip?'))return [('إلغاء عضوية VIP','vip-revoke',Icons.remove_circle_outline)];
  if(path.startsWith('admin/cosmetics'))return [('تفعيل/تعطيل المظهر','cosmetic-toggle',Icons.toggle_on),('إهداء هذا المظهر','cosmetic-grant',Icons.card_giftcard)];
  if(path.startsWith('vanity-ids/admin/all'))return [('إهداء الآي دي','vanity-grant',Icons.card_giftcard),('تفعيل الآي دي','vanity-enable',Icons.check_circle),('تعطيل الآي دي','vanity-disable',Icons.pause_circle_outline),('سحب الآي دي','vanity-revoke',Icons.remove_circle_outline)];
  if(path.startsWith('admin/agencies?'))return [('تعليق الوكالة','agency-suspend',Icons.pause_circle_outline)];
  if(path.startsWith('admin/recharge-agents/applications'))return [('قبول طلب الوكيل','agent-approve',Icons.check_circle),('رفض طلب الوكيل','agent-reject',Icons.cancel)];
  if(path.startsWith('admin/contests'))return [('إنهاء المسابقة','contest-end',Icons.flag_circle)];
  if(path.startsWith('admin/streams'))return [('إنهاء البث','stream-end',Icons.stop_circle)];
  if(path=='admin/gift-categories')return [('تعديل التصنيف','gift-category-edit',Icons.edit)];
  return const [];
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
  final publicId=(row['publicId']??row['code']??row['slug']??row['cosmeticCode']??rowId).toString();
  final labels=action=='vanity-grant'
    ? ['معرّف المستخدم','المدة بالأيام: 7 أو 15 أو 30 أو permanent']
    : action=='cosmetic-grant'
      ? ['معرّف المستخدم','المدة بالأيام: 7 أو 15 أو 30 أو permanent']
      : ['معرّف المستخدم','المدة بالأيام: 7 أو 15 أو 30 أو permanent'];
  final values=await _askValues(context,action=='vanity-grant'?'إهداء الآي دي $publicId':action=='cosmetic-grant'?'إهداء المظهر $publicId':'منح خطة VIP',labels);
  if(values==null||!context.mounted)return;
  final userId=values[0].trim();
  final term=values[1].trim().toLowerCase();
  final permanent=term=='permanent'||term=='دائم';
  final days=int.tryParse(term);
  if(userId.isEmpty||(!permanent&&![7,15,30].contains(days))){
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('أدخل معرّف المستخدم ومدة 7 أو 15 أو 30 أو permanent.')));
    return;
  }
  final confirmed=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(
    title:Text(action=='vanity-grant'?'تأكيد إهداء الآي دي':action=='cosmetic-grant'?'تأكيد إهداء المظهر':'تأكيد منح VIP'),
    content:Text('سيتم تطبيق الإجراء على المستخدم $userId لمدة ${permanent?'دائمة':'$days يومًا'}، هل تؤكد؟'),
    actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('تأكيد'))],
  ));
  if(confirmed!=true)return;
  try {
    if(action=='vanity-grant'){
      await api.post('vanity-ids/admin/assign',{'userId':userId,'publicId':publicId,'days':days??7,'permanent':permanent});
    } else if(action=='cosmetic-grant'){
      await api.post('admin/cosmetics/grant',{'userId':userId,'cosmeticCode':publicId,'days':days??7,'permanent':permanent});
    } else {
      await api.post('admin/vip/assign',{'userId':userId,'planId':rowId,'days':days??30,'permanent':permanent});
    }
    if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('تم إرسال الطلب إلى الخادم بنجاح.')));reload();}
  } catch(e) {
    if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));
  }
 }
 Future<void> _runAction(BuildContext context,Map row,(String,String,IconData) action) async {
  final id=(row['id']??row['applicationId']??row['reportId']??row['streamId']??row['userId']??'').toString();
  if(id.isEmpty){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('لا يوجد معرّف واضح لهذا العنصر.')));return;}
  if(action.$2=='gift-category-edit'){await _editGiftCategory(context,id,Map<String,dynamic>.from(row));return;}
  if(['vanity-grant','cosmetic-grant','vip-assign'].contains(action.$2)){await _grantAction(context,row,action.$2,id);return;}
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
   case 'vanity-enable': endpoint='vanity-ids/admin/$id/enable'; break;
   case 'vanity-disable': endpoint='vanity-ids/admin/$id/disable'; break;
   case 'vanity-revoke': endpoint='vanity-ids/admin/$id/revoke'; break;
   case 'cosmetic-toggle': endpoint='admin/cosmetics/$id'; body={'isActive': !(row['isActive']==true)}; break;
   case 'agency-suspend': endpoint='admin/agencies/$id/suspend'; body={'reason':'تعليق من لوحة الإدارة'}; break;
   case 'agent-approve': endpoint='admin/recharge-agents/applications/$id/approve'; break;
   case 'agent-reject': endpoint='admin/recharge-agents/applications/$id/reject'; body={'reason':'رفض من لوحة الإدارة'}; break;
   case 'contest-end': endpoint='admin/contests/$id/end'; break;
   case 'stream-end': endpoint='admin/streams/$id/end'; break;
   case 'gender-reject': endpoint='admin/gender-verifications/$id/reject'; break;
   default: throw StateError('إجراء غير معروف: '+action.$2);
  }
  try{await api.post(endpoint,body);if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تم تنفيذ: '+action.$1)));reload();}}
  catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));}
 }
 Widget metric(String label,String value,IconData icon)=>SizedBox(width:220,child:Card(child:Padding(padding:const EdgeInsets.all(16),child:Row(children:[Icon(icon,color:gold,size:26),const SizedBox(width:10),Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(label,style:const TextStyle(color:Colors.black54)),Text(value,maxLines:2,overflow:TextOverflow.ellipsis,style:const TextStyle(fontWeight:FontWeight.w800))]))]))));
}
