import 'dart:io';
import 'package:flutter/material.dart';

bool get isAndroid => Platform.isAndroid;

const _navigation = <(String, String, IconData)>[
  ('today', 'Today', Icons.today_outlined),
  ('library', 'Exercises', Icons.fitness_center),
  ('plans', 'Plans', Icons.playlist_add_check),
  ('calendar', 'Calendar', Icons.calendar_month_outlined),
  ('records', 'Records', Icons.history),
  ('analytics', 'Analysis', Icons.insights),
  ('sync', 'Device sync', Icons.devices),
  ('health', 'Phone heart rate', Icons.monitor_heart_outlined),
  ('profile', 'Profile and data', Icons.tune),
];

/// A single responsive shell keeps the same training pages on desktop and phone.
class FitnessShell extends StatelessWidget {
  const FitnessShell({
    super.key,
    required this.selected,
    required this.onNavigate,
    required this.child,
    this.status = '',
    this.saveError = false,
    required this.onRetry,
    required this.onDismiss,
  });
  final String selected;
  final ValueChanged<String> onNavigate;
  final Widget child;
  final String status;
  final bool saveError;
  final VoidCallback onRetry;
  final VoidCallback onDismiss;

  Widget _menu(BuildContext context, {required bool drawer}) => ColoredBox(
    color: const Color(0xff163e32),
    child: SafeArea(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.only(left: 4, bottom: 25),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'CLOGEM',
                    style: TextStyle(fontSize: 23, color: Colors.white),
                  ),
                  Text(
                    'FITNESS JOURNAL',
                    style: TextStyle(fontSize: 10, color: Color(0xffb8cec1)),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                children: [
                  for (final (id, label, icon) in _navigation)
                    if (id != 'health' || isAndroid)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 6),
                        child: ListTile(
                          key: ValueKey('navigate-$id'),
                          selected: selected == id,
                          selectedTileColor: const Color(0xff2c5747),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          leading: Icon(
                            icon,
                            color: const Color(0xffd6e7da),
                            size: 21,
                          ),
                          title: Text(
                            label,
                            style: const TextStyle(
                              fontSize: 13,
                              color: Colors.white,
                            ),
                          ),
                          onTap: () {
                            if (drawer) Navigator.of(context).pop();
                            onNavigate(id);
                          },
                        ),
                      ),
                ],
              ),
            ),
            const Padding(
              padding: EdgeInsets.all(4),
              child: Text(
                'LOCAL - PRIVATE\nRecord every set.',
                style: TextStyle(
                  fontSize: 11,
                  color: Color(0xffb8cec1),
                  height: 1.8,
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final narrow = constraints.maxWidth < 760;
      final content = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (saveError)
            ColoredBox(
              color: const Color(0xffffe7d9),
              child: Padding(
                padding: const EdgeInsets.all(10),
                child: Wrap(
                  spacing: 12,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    const Text('Changes are not saved yet'),
                    FilledButton(onPressed: onRetry, child: const Text('Retry save')),
                  ],
                ),
              ),
            ),
          if (status.isNotEmpty)
            ColoredBox(
              color: const Color(0xffe4ebdf),
              child: Padding(
                padding: const EdgeInsets.only(left: 16),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        status,
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xff16745a),
                        ),
                      ),
                    ),
                    IconButton(
                      onPressed: onDismiss,
                      icon: const Icon(Icons.close, size: 17),
                    ),
                  ],
                ),
              ),
            ),
          Expanded(
            child: SingleChildScrollView(
              key: PageStorageKey('page-$selected'),
              padding: EdgeInsets.all(narrow ? 16 : 28),
              child: child,
            ),
          ),
        ],
      );
      return Scaffold(
        appBar:
            narrow
                ? AppBar(
                  title: Text(
                    _navigation
                        .firstWhere(
                          (e) => e.$1 == selected,
                          orElse: () => _navigation.first,
                        )
                        .$2,
                  ),
                )
                : null,
        drawer:
            narrow
                ? Drawer(
                  child: Builder(
                    builder:
                        (drawerContext) => _menu(drawerContext, drawer: true),
                  ),
                )
                : null,
        body: SafeArea(
          top: !narrow,
          child:
              narrow
                  ? content
                  : Row(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      SizedBox(
                        width: 192,
                        child: _menu(context, drawer: false),
                      ),
                      Expanded(child: content),
                    ],
                  ),
        ),
      );
    },
  );
}
