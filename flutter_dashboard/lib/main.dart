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
 Future<dynamic> post(String path, [Map<String,dynamic> body = const {}]) async {\n  final r=await http.post(uri(path),headers:{'Content-Type':'application/json','Accept':'application/json','Authorization':'Bearer '+token},body:jsonEncode(body)).timeout(const Duration(seconds:20));\n  dynamic v;try{v=jsonDecode(r.body);}catch(_){v=r.body;}\n  if(r.statusCode<200||r.statusCode>=300)throw Exception(v is Map?(v['message']??v['error']??'فشل تنفيذ الإجراء').toString():'فشل الطلب: '+r.statusCode.toString());\n  return v is Map&&v['data']!=null?v['data']:v;\n }\n Future<dynamic> patch(String path, Map<String,dynamic> body) async {
  final r=await http.patch(uri(path),headers:{'Content-Type':'application/json','Accept':'application/json','Authorization':'Bearer '+token},body:jsonEncode(body)).timeout(const Duration(seconds:20));
  dynamic v;try{v=jsonDecode(r.body);}catch(_){v=r.body;}
  if(r.statusCode<200||r.statusCode>=300)throw Exception(v is Map?(v['message']??v['error']??'فشل حفظ الإعدادات').toString():'فشل الطلب: '+r.statusCode.toString());
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
 Module('إعدادات دفع شام كاش','admin/payment-settings/sham-cash',Icons.payment),
];
class AdminHome extends StatefulWidget {const AdminHome({required this.token,required this.user,super.key});final String token;final Map<String,dynamic> user;@override State<AdminHome> createState()=>_AdminHomeState();}
class _AdminHomeState extends State<AdminHome>{
 late final api=Api(widget.token);int selected=0;late Future<dynamic> future;
 @override void initState(){super.initState();load();}
 void load(){future=api.get(modules[selected].path);}
 void choose(int i){setState(() { selected = i; load(); });}
 @override Widget build(BuildContext context){final wide=MediaQuery.sizeOf(context).width>=950;return Scaffold(appBar:AppBar(title:Text(modules[selected].title),actions:[IconButton(onPressed:()=>setState(load),icon:const Icon(Icons.refresh)),IconButton(onPressed:()=>Navigator.pushAndRemoveUntil(context,MaterialPageRoute<void>(builder:(_)=>const AdminLogin()),(_)=>false),icon:const Icon(Icons.logout))]),body:Row(children:[if(wide)Container(width:235,color:const Color(0xFF211C14),child:Column(children:[const Padding(padding:EdgeInsets.all(20),child:Text('JEHO ADMIN',style:TextStyle(color:Colors.white,fontWeight:FontWeight.w900))),Expanded(child:ListView.builder(itemCount:modules.length,itemBuilder:(c,i)=>ListTile(selected:i==selected,leading:Icon(modules[i].icon,color:i==selected?const Color(0xFFE4BB63):Colors.white70),title:Text(modules[i].title,style:TextStyle(color:i==selected?Colors.white:Colors.white70)),onTap:()=>choose(i)))),Padding(padding:const EdgeInsets.all(12),child:Text((widget.user['name']??widget.user['email']??'مشرف').toString(),style:const TextStyle(color:Colors.white70)))])),Expanded(child:AdminContent(module:modules[selected],future:future,api:api,reload:()=>setState(load)))]),bottomNavigationBar:wide?null:NavigationBar(selectedIndex:selected<5?selected:4,onDestinationSelected:choose,destinations:const[NavigationDestination(icon:Icon(Icons.dashboard),label:'الرئيسية'),NavigationDestination(icon:Icon(Icons.people),label:'المستخدمون'),NavigationDestination(icon:Icon(Icons.forum),label:'المحادثات'),NavigationDestination(icon:Icon(Icons.meeting_room),label:'الغرف'),NavigationDestination(icon:Icon(Icons.more_horiz),label:'المزيد')]));}
}
class AdminContent extends StatelessWidget {
 const AdminContent({required this.module,required this.future,required this.api,required this.reload,super.key});final Module module;final Future<dynamic> future;final Api api;final VoidCallback reload;
 List<dynamic> rows(dynamic v){if(v is List)return v;if(v is Map){for(final k in ['items','results','users','rooms','gifts','conversations','data']){if(v[k] is List)return v[k] as List;}}return const[];}
 String show(dynamic v)=>v==null?'—':v is Map||v is List?jsonEncode(v):v.toString();
 @override Widget build(BuildContext context)=>RefreshIndicator(onRefresh:()async=>reload(),child:ListView(padding:const EdgeInsets.all(20),children:[Wrap(spacing:10,runSpacing:10,children:[metric('القسم',module.title,module.icon),metric('الواجهة','Flutter Web',Icons.flutter_dash),metric('الربط','API مباشر',Icons.cloud_done)]),const SizedBox(height:18),FutureBuilder<dynamic>(future:future,builder:(c,s){if(s.connectionState==ConnectionState.waiting)return const Padding(padding:EdgeInsets.all(40),child:Center(child:CircularProgressIndicator()));if(s.hasError)return Card(child:Padding(padding:const EdgeInsets.all(20),child:Column(children:[Text(s.error.toString()),OutlinedButton(onPressed:reload,child:const Text('إعادة المحاولة'))])));final v=s.data;final editPath=_editablePath(module.path);if(editPath!=null&&v is Map<String,dynamic>)return Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[Align(alignment:AlignmentDirectional.centerStart,child:FilledButton.icon(onPressed:()=>_editSettings(context,editPath,v),icon:const Icon(Icons.edit),label:const Text('تعديل الإعدادات'))),const SizedBox(height:10),Card(child:Padding(padding:const EdgeInsets.all(12),child:SelectableText(const JsonEncoder.withIndent('  ').convert(v))))]);if(module.path=='admin/dashboard/overview'&&v is Map)return Wrap(spacing:10,runSpacing:10,children:v.entries.map((e)=>metric(e.key,show(e.value),Icons.insights)).toList());final items=rows(v);if(items.isEmpty)return Card(child:Padding(padding:const EdgeInsets.all(24),child:Column(children:[Icon(module.icon,size:42,color:gold),const SizedBox(height:8),const Text('لا توجد عناصر أو أن مسار هذا القسم غير متاح لهذا الحساب.',textAlign:TextAlign.center),SelectableText(show(v),style:const TextStyle(fontSize:12,color:Colors.black54))])));return Card(child:Column(children:[Padding(padding:const EdgeInsets.all(12),child:Text('العناصر ('+items.length.toString()+')')),...items.take(100).map((row){if(row is! Map)return ListTile(title:Text(show(row)));final title=(row['displayName']??row['username']??row['name']??row['title']??row['id']??'عنصر').toString();final sub=row.entries.take(4).map((e)=>e.key+': '+show(e.value)).join(' · ');return ExpansionTile(title:Text(title),subtitle:Text(sub,maxLines:2,overflow:TextOverflow.ellipsis),children:[Padding(padding:const EdgeInsets.all(12),child:SelectableText(const JsonEncoder.withIndent('  ').convert(row))),if(_actionsFor(module.path).isNotEmpty)Padding(padding:const EdgeInsets.fromLTRB(12,0,12,12),child:Wrap(spacing:8,runSpacing:8,children:_actionsFor(module.path).map((a)=>OutlinedButton.icon(onPressed:()=>_runAction(context,row,a),icon:Icon(a.$3),label:Text(a.$1))).toList()))]);})]));})]));
 String? _editablePath(String path){
  const allowed={'admin/settings':'admin/settings','admin/paid-messages/settings':'admin/paid-messages/settings','admin/economy/settings':'admin/economy/settings','admin/cp/config':'admin/cp/policy','admin/games/odds':'admin/games/odds','admin/games/ads':'admin/games/ads','admin/drama/settings':'admin/drama/settings'};
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
  return const [];
 }
 Future<void> _runAction(BuildContext context,Map row,(String,String,IconData) action) async {
  final id=(row['id']??row['applicationId']??row['reportId']??'').toString();
  if(id.isEmpty){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('لا يوجد معرّف واضح لهذا العنصر.')));return;}
  final confirm=await showDialog<bool>(context:context,builder:(ctx)=>AlertDialog(title:Text(action.$1),content:Text('تأكيد تنفيذ «'+action.$1+'» للعنصر '+id+'؟'),actions:[TextButton(onPressed:()=>Navigator.pop(ctx,false),child:const Text('إلغاء')),FilledButton(onPressed:()=>Navigator.pop(ctx,true),child:const Text('تأكيد'))]));
  if(confirm!=true)return;
  String endpoint;Map<String,dynamic> body=const {};
  switch(action.$2){
   case 'ban': endpoint='admin/users/$id/ban'; body={'reason':'إجراء إداري من لوحة JEHO'}; break;
   case 'unban': endpoint='admin/users/$id/unban'; break;
   case 'agency-approve': endpoint='admin/agencies/applications/$id/approve'; break;
   case 'agency-reject': endpoint='admin/agencies/applications/$id/reject'; break;
   case 'report-resolve': endpoint='admin/reports/$id/resolve'; break;
   default: endpoint='admin/reports/$id/dismiss';
  }
  try{await api.post(endpoint,body);if(context.mounted){ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text('تم تنفيذ: '+action.$1)));reload();}}
  catch(e){if(context.mounted)ScaffoldMessenger.of(context).showSnackBar(SnackBar(content:Text(e.toString().replaceFirst('Exception: ',''))));}
 }
 Widget metric(String label,String value,IconData icon)=>SizedBox(width:220,child:Card(child:Padding(padding:const EdgeInsets.all(16),child:Row(children:[Icon(icon,color:gold,size:26),const SizedBox(width:10),Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(label,style:const TextStyle(color:Colors.black54)),Text(value,maxLines:2,overflow:TextOverflow.ellipsis,style:const TextStyle(fontWeight:FontWeight.w800))]))]))));
}
