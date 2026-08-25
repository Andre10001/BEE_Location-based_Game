/// The kind of account.
enum Role {
  player,
  admin;

  String get label => switch (this) {
        Role.player => 'Player',
        Role.admin => 'Administrator',
      };

  /// True when this account is allowed to open the admin menu.
  bool get canManageContent => this == Role.admin;
}