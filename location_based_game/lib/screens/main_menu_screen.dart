import 'package:flutter/material.dart';
import 'game_screen.dart';
import '../components/menu_button.dart';

class MainMenuScreen extends StatelessWidget {

  final String title;

  const MainMenuScreen({
    super.key,
    required this.title
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              title,
              style: TextStyle(
                fontSize: 40,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 50),
            const MenuButton(
              title: 'Gioca',
              navigateToScreen: const GameScreen(title: 'Gioco'),
            ),
          ],
        ),
      ),
    );
  }
}