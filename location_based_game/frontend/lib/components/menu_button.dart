import 'package:flutter/material.dart';

class MenuButton extends StatelessWidget {
  final String title;
  final Widget navigateToScreen;

  const MenuButton({
    super.key,
    required this.title,
    required this.navigateToScreen,
  });

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: () {
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => navigateToScreen),
        );
      },
      style: ElevatedButton.styleFrom(
        padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 15),
        textStyle: const TextStyle(fontSize: 20),
      ),
      child: Text(title), 
    );
  }
}