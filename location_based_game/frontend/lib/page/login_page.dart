import 'package:flutter/material.dart';

import '../api/player_api.dart';
import '../enum/role.dart';
import '../model/player.dart';
import 'admin/admin_menu_page.dart';
import 'player/player_home_page.dart';
import 'register_page.dart';

/// The login page for existing users.
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  bool _isLoading = false;
  String? _errorMessage;

  @override
  void dispose() {
    _nameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  /* Sends the name and password to the backend and, if they are correct,
  * opens the admin menu.
  */
  Future<void> _login() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      final player = await PlayerApi.login(
        _nameController.text.trim(),
        _passwordController.text,
      );
      if (!mounted) return;
      _openPageForRole(player);
    } catch (error) {
      setState(() => _errorMessage = error.toString());
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  /* Navigate to the appropriate page based on the player's role. */
  void _openPageForRole(Player player) {
    String role = player.role.label;
    print("role: $role");
    final Widget nextPage = switch (player.role) {
      Role.admin => AdminMenuPage(playerName: player.nickname),
      Role.player => PlayerHomePage(nickname: player.nickname),
    };
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => nextPage));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Login')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _nameController,
              decoration: const InputDecoration(labelText: 'Name'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _passwordController,
              decoration: const InputDecoration(labelText: 'Password'),
              obscureText: true,
            ),
            const SizedBox(height: 20),
            if (_errorMessage != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(
                  _errorMessage!,
                  style: const TextStyle(color: Colors.red),
                ),
              ),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: _isLoading ? null : _login,
                child: _isLoading
                    ? const CircularProgressIndicator()
                    : const Text('Log in'),
              ),
            ),
            TextButton(
              onPressed: _isLoading
                  ? null
                  : () => Navigator.of(context).push(
                        MaterialPageRoute(builder: (_) => const RegisterPage()),
                      ),
              child: const Text("Don't have an account? Register"),
            ),
          ],
        ),
      ),
    );
  }
}