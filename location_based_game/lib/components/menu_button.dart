import 'package:flutter/material.dart';

class MenuButton extends StatelessWidget {
  
  // Queste sono le variabili (i "parametri") che questo widget si aspetta di ricevere
  // quando qualcuno lo usa nel codice principale.
  final String title;
  final Widget navigateToScreen;

  // Il Costruttore. Qui chiediamo (tramite 'required') che chi usa questo
  // bottone DEVE fornirci un testo e una destinazione.
  const MenuButton({
    super.key,
    required this.title,
    required this.navigateToScreen,
  });

  // E qui c'è il nostro vecchio codice del bottone, ma reso dinamico!
  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: () {
        // Al posto di un codice fisso, la navigazione andrà verso lo schermoDestinazione 
        // che ci è stato passato.
        Navigator.push(
          context,
          MaterialPageRoute(builder: (context) => navigateToScreen),
        );
      },
      style: ElevatedButton.styleFrom(
        // Lo stile rimane qui, isolato e centralizzato
        padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 15),
        textStyle: const TextStyle(fontSize: 20),
      ),
      // Il figlio usa la variabile 'testo' invece di una parola scritta a mano
      child: Text(title), 
    );
  }
}