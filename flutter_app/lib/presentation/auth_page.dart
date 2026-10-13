import 'package:flutter/material.dart';

import '../data/jeho_api_client.dart';

class AuthPage extends StatefulWidget {
  const AuthPage({
    required this.apiClient,
    required this.onAuthenticated,
    super.key,
  });

  final JehoApiClient apiClient;
  final ValueChanged<Map<String, dynamic>> onAuthenticated;

  @override
  State<AuthPage> createState() => _AuthPageState();
}

class _AuthPageState extends State<AuthPage> {
  final _formKey = GlobalKey<FormState>();
  final _identifier = TextEditingController();
  final _password = TextEditingController();
  final _email = TextEditingController();
  final _username = TextEditingController();
  final _displayName = TextEditingController();

  bool _isRegister = false;
  bool _isBusy = false;
  bool _obscurePassword = true;
  String? _error;

  @override
  void dispose() {
    _identifier.dispose();
    _password.dispose();
    _email.dispose();
    _username.dispose();
    _displayName.dispose();
    super.dispose();
  }

  Future<void> _run(
    Future<Map<String, dynamic>> Function() action, {
    bool validate = true,
  }) async {
    if (_isBusy) return;
    if (validate && !(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    setState(() {
      _isBusy = true;
      _error = null;
    });
    try {
      final session = await action();
      if (mounted) widget.onAuthenticated(session);
    } on JehoApiException catch (error) {
      if (mounted) setState(() => _error = error.message);
    } catch (_) {
      if (mounted) setState(() => _error = 'حدث خطأ غير متوقع. حاول مرة أخرى.');
    } finally {
      if (mounted) setState(() => _isBusy = false);
    }
  }

  Future<Map<String, dynamic>> _guestAction() =>
      widget.apiClient.guestLogin(displayName: _displayName.text);

  Future<Map<String, dynamic>> _submitCredentials() {
    if (_isRegister) {
      return widget.apiClient.register(
        email: _email.text,
        username: _username.text,
        password: _password.text,
        displayName: _displayName.text,
      );
    }
    return widget.apiClient.login(
      identifier: _identifier.text,
      password: _password.text,
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(
          image: DecorationImage(
            image: AssetImage('assets/original/android-res/drawable-nodpi/bg_auth_3d.png'),
            fit: BoxFit.cover,
          ),
        ),
        child: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Container(
                      width: 148,
                      height: 148,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        gradient: const LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [
                            Color(0xFFFFE9D8),
                            Color(0xFFFFF7E9),
                            Color(0xFFF3D6A1),
                          ],
                        ),
                        border: Border.all(
                          color: const Color(0x33FE2C55),
                          width: 1.2,
                        ),
                        boxShadow: const [
                          BoxShadow(
                            color: Color(0x22C47B77),
                            blurRadius: 30,
                            spreadRadius: 3,
                          ),
                        ],
                      ),
                      child: Container(
                        width: 126,
                        height: 126,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: Colors.white,
                          border: Border.all(
                            color: const Color(0x33D9A44A),
                            width: 2,
                          ),
                        ),
                        child: ClipOval(
                          child: Image.asset(
                            'assets/original/android-res/drawable-xxhdpi/jeho_logo.webp',
                            width: 116,
                            height: 116,
                            fit: BoxFit.cover,
                            errorBuilder: (context, error, stackTrace) =>
                                const Icon(
                                  Icons.forum_rounded,
                                  size: 52,
                                  color: Color(0xFF8C641D),
                                ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      'JEHO-OWN',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.headlineSmall?.copyWith(
                        fontSize: 26,
                        letterSpacing: 1.4,
                        fontWeight: FontWeight.w900,
                        color: const Color(0xFF352C29),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Container(
                      width: 40,
                      height: 3,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(8),
                        gradient: const LinearGradient(
                          colors: [Color(0xFFFFD77A), Color(0xFFE99B35)],
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      _isRegister ? 'أنشئ حسابك للمتابعة' : 'سجّل الدخول للمتابعة',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyLarge?.copyWith(
                        color: const Color(0xFF756966),
                      ),
                    ),
                    const SizedBox(height: 28),
                    if (_isRegister) ...[
                      TextFormField(
                        controller: _displayName,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: 'الاسم الظاهر (اختياري)',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 14),
                      TextFormField(
                        controller: _email,
                        keyboardType: TextInputType.emailAddress,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: 'البريد الإلكتروني',
                          border: OutlineInputBorder(),
                        ),
                        validator: (value) {
                          final email = (value ?? '').trim();
                          if (!email.contains('@') || !email.contains('.')) {
                            return 'أدخل بريدًا إلكترونيًا صحيحًا';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 14),
                      TextFormField(
                        controller: _username,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: 'اسم المستخدم (إنجليزي وأرقام وشرطة سفلية)',
                          border: OutlineInputBorder(),
                        ),
                        validator: (value) {
                          final username = (value ?? '').trim();
                          if (!RegExp(r'^[a-zA-Z0-9_]{3,48}$').hasMatch(username)) {
                            return 'اسم المستخدم من 3 إلى 48 حرفًا';
                          }
                          return null;
                        },
                      ),
                    ] else
                      TextFormField(
                        controller: _identifier,
                        textInputAction: TextInputAction.next,
                        decoration: const InputDecoration(
                          labelText: 'البريد أو اسم المستخدم أو المعرّف',
                          border: OutlineInputBorder(),
                          prefixIcon: Icon(Icons.person_outline),
                        ),
                        validator: (value) => (value ?? '').trim().isEmpty
                            ? 'أدخل بيانات الدخول'
                            : null,
                      ),
                    const SizedBox(height: 14),
                    TextFormField(
                      controller: _password,
                      obscureText: _obscurePassword,
                      textInputAction: TextInputAction.done,
                      onFieldSubmitted: (_) => _run(_submitCredentials),
                      decoration: InputDecoration(
                        labelText: 'كلمة المرور',
                        border: const OutlineInputBorder(),
                        prefixIcon: const Icon(Icons.lock_outline),
                        suffixIcon: IconButton(
                          tooltip: _obscurePassword ? 'إظهار كلمة المرور' : 'إخفاء كلمة المرور',
                          onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                          icon: Icon(_obscurePassword
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined),
                        ),
                      ),
                      validator: (value) {
                        if ((value ?? '').length < 6) {
                          return 'كلمة المرور يجب أن تكون 6 أحرف على الأقل';
                        }
                        return null;
                      },
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 14),
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.errorContainer,
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          _error!,
                          style: TextStyle(color: theme.colorScheme.onErrorContainer),
                        ),
                      ),
                    ],
                    const SizedBox(height: 20),
                    FilledButton(
                      onPressed: _isBusy ? null : () => _run(_submitCredentials),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        child: _isBusy
                            ? const SizedBox(
                                width: 22,
                                height: 22,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : Text(_isRegister ? 'إنشاء الحساب' : 'تسجيل الدخول'),
                      ),
                    ),
                    TextButton(
                      onPressed: _isBusy
                          ? null
                          : () => setState(() {
                                _isRegister = !_isRegister;
                                _error = null;
                              }),
                      child: Text(
                        _isRegister ? 'لديك حساب؟ سجّل الدخول' : 'ليس لديك حساب؟ إنشاء حساب',
                      ),
                    ),
                    const Divider(height: 28),
                    OutlinedButton.icon(
                      onPressed: _isBusy ? null : () => _run(_guestAction, validate: false),
                      icon: const Icon(Icons.person_add_alt_1_rounded),
                      label: const Text('الدخول كضيف'),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      'تواصل مع أصدقائك، واكتشف الغرف والمجتمعات في JEHO',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: const Color(0xFF756966),
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
      ),
    );
  }
}
