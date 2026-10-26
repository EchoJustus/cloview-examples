import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import '../lib/analytics_widgets.dart';
import '../lib/app_shell.dart';
import '../lib/muscle_view.dart';

// This gallery is a new test harness. The three imported components are
// English-localized derivatives of the baseline's real Flutter components.
// Its fixed, synthetic presentation data is NOT a business-calculation oracle.
const ink = Color(0xff243b36);
const teal = Color(0xff258675);
const bodyLoads = <String, double>{
  'pectoralis-major': 3, 'latissimus-dorsi': 2,
  'triceps-brachii': 1.5, 'biceps-brachii': 1,
};
const radarValues = <String, double>{
  'Chest': 3, 'Back': 2, 'Shoulders': 0, 'Arms': 2.5, 'Core': 0, 'Lower body': 0,
};
const energyValues = <String, double>{
  '2026-10-10': 0, '2026-10-11': 0, '2026-10-12': 0,
  '2026-10-13': 0, '2026-10-14': 25.725, '2026-10-15': 0, '2026-10-16': 51.45,
};
final captureKey = GlobalKey();
final selections = <String>[];
final months = <String>[];
final navigation = <String>[];

String date(DateTime d) => d.toIso8601String().substring(0, 10);

Map<String, dynamic> presentationData() {
  final first = DateTime(2026, 10, 1);
  final start = first.subtract(Duration(days: first.weekday - 1));
  final actual = {'2026-10-07': 12.0, '2026-10-14': 6.0, '2026-10-16': 12.0};
  return {
    'metric': 'sets',
    'mode': 'actual',
    'range': {'from': '2026-10-10', 'to': '2026-10-16'},
    'previous-range': {'from': '2026-10-03', 'to': '2026-10-09'},
    'comparison': {
      'duration-min': {'current': 18, 'previous': 12, 'delta': 6, 'percent': 50},
      'sets': {'current': 5, 'previous': 3, 'delta': 2, 'percent': 66.6667},
      'training-days': {'current': 2, 'previous': 1, 'delta': 1, 'percent': 100},
      'muscles': [
        {'id': 'pectoralis-major', 'name': 'Pectoralis major', 'current': 3, 'previous': 1},
        {'id': 'latissimus-dorsi', 'name': 'Latissimus dorsi', 'current': 2, 'previous': 2},
        {'id': 'triceps-brachii', 'name': 'Triceps brachii', 'current': 1.5, 'previous': .5},
        {'id': 'biceps-brachii', 'name': 'Biceps brachii', 'current': 1, 'previous': 1},
      ],
    },
    'calendar': {
      'month': '2026-10',
      'cells': [
        for (var i = 0; i < 42; i++)
          {
            'date': date(start.add(Duration(days: i))),
            'day': start.add(Duration(days: i)).day,
            'in-month?': start.add(Duration(days: i)).month == 10,
            'duration-min': actual[date(start.add(Duration(days: i)))] ?? 0,
            'sets': date(start.add(Duration(days: i))) == '2026-10-14' ? 2 : (actual.containsKey(date(start.add(Duration(days: i)))) ? 3 : 0),
            'planned-items': ['2026-10-07', '2026-10-14', '2026-10-16', '2026-10-19'].contains(date(start.add(Duration(days: i)))) ? 3 : 0,
            'plan-complete?': ['2026-10-07', '2026-10-16'].contains(date(start.add(Duration(days: i)))),
            if (actual.containsKey(date(start.add(Duration(days: i)))))
              'kcal': actual[date(start.add(Duration(days: i)))]! * 4.2875,
          },
      ],
    },
  };
}

Widget title(String text, {double size = 24}) => Text(text,
    style: TextStyle(fontSize: size, fontWeight: FontWeight.w600, color: ink));

Widget card(Widget child) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(20)),
    child: child);

Widget dayDetail() => card(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
  title('Friday, October 16', size: 20),
  const SizedBox(height: 12),
  const Text('3 completed sets  /  12 active minutes'),
  const SizedBox(height: 6),
  const Text('51.4 kcal estimated  /  Plan complete'),
  const Divider(height: 30),
  const Text('Upper-body session', style: TextStyle(fontWeight: FontWeight.w600)),
  const SizedBox(height: 12),
  const Text('Chest press - 3 completed sets'),
  const SizedBox(height: 18),
  Wrap(spacing: 10, runSpacing: 8, children: [
    FilledButton(onPressed: () => navigation.add('plans'), child: const Text('Open plan')),
    OutlinedButton(onPressed: () => navigation.add('analytics'), child: const Text('Day analysis')),
  ]),
  const SizedBox(height: 18),
  const Text('Synthetic reference data. No personal training records.', style: TextStyle(fontSize: 12, color: Color(0xff74817a))),
]));

Widget calendarPage(bool mobile) {
  final calendar = TrainingCalendar(dataJson: jsonEncode(presentationData()),
      selectedDate: '2026-10-16', onSelect: selections.add, onMonthChanged: months.add);
  return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    title('Training calendar'),
    const SizedBox(height: 8),
    const Text('Select a date to inspect completed training and plans.'),
    const SizedBox(height: 24),
    if (mobile) ...[calendar, const SizedBox(height: 18), dayDetail()]
    else Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Expanded(flex: 3, child: calendar), const SizedBox(width: 20),
      Expanded(flex: 2, child: dayDetail()),
    ]),
  ]);
}

Widget analysisPage() => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
  title('Training analysis'),
  const SizedBox(height: 8),
  const Text('October 10 - 16, 2026  /  Completed training  /  Sets'),
  const SizedBox(height: 20),
  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Expanded(child: card(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      title('Muscle load', size: 19), const SizedBox(height: 12),
      MuscleBody(height: 420, loads: bodyLoads, onSelect: selections.add),
    ]))),
    const SizedBox(width: 18),
    Expanded(child: Column(children: [
      const TrainingRadar(height: 245, values: radarValues),
      const SizedBox(height: 14),
      const CalorieBars(height: 223, values: energyValues),
    ])),
  ]),
  const SizedBox(height: 18),
  AnalyticsComparison(dataJson: jsonEncode(presentationData())),
]);

Widget gallery({bool calendar = false, bool mobile = false}) => RepaintBoundary(
  key: captureKey,
  child: MaterialApp(debugShowCheckedModeBanner: false,
    theme: ThemeData(useMaterial3: true, fontFamily: 'BaselineSans',
      scaffoldBackgroundColor: const Color(0xfff3f5ee),
      colorScheme: ColorScheme.fromSeed(seedColor: teal),
      textTheme: const TextTheme(bodyMedium: TextStyle(color: ink))),
    home: FitnessShell(selected: calendar ? 'calendar' : 'analytics', onNavigate: navigation.add,
      status: 'English-localized component reference | Synthetic data', onDismiss: () {}, onRetry: () {},
      child: calendar ? calendarPage(mobile) : analysisPage()),
  ),
);

Future<void> capture(WidgetTester tester, String name) async {
  await tester.pumpAndSettle();
  expect(tester.takeException(), isNull);
  await tester.runAsync(() async {
    final boundary = captureKey.currentContext!.findRenderObject() as RenderRepaintBoundary;
    final image = await boundary.toImage(pixelRatio: 1);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    final output = Directory(Platform.environment['BASELINE_OUTPUT_DIR'] ?? '../visuals');
    await output.create(recursive: true);
    await File('${output.path}/$name.png').writeAsBytes(data!.buffer.asUint8List());
    image.dispose();
  });
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    final fontPath = Platform.environment['BASELINE_FONT_PATH'];
    if (fontPath == null) throw StateError('Set BASELINE_FONT_PATH to a local Latin TTF font. Fonts are not redistributed.');
    final loader = FontLoader('BaselineSans');
    loader.addFont(File(fontPath).readAsBytes().then((bytes) => ByteData.sublistView(bytes)));
    await loader.load();
    final iconPath = Platform.environment['BASELINE_ICON_FONT_PATH'];
    if (iconPath == null) throw StateError('Set BASELINE_ICON_FONT_PATH to the Flutter MaterialIcons-Regular.otf file.');
    final icons = FontLoader('MaterialIcons');
    icons.addFont(File(iconPath).readAsBytes().then((bytes) => ByteData.sublistView(bytes)));
    await icons.load();
  });

  testWidgets('English reference gallery renders and preserves component events', (tester) async {
    await tester.runAsync(() async {
      final output = Directory(Platform.environment['BASELINE_OUTPUT_DIR'] ?? '../visuals');
      await output.create(recursive: true);
      await File('${output.path}/presentation-data.json').writeAsString(
        '${const JsonEncoder.withIndent('  ').convert({
          'classification': 'synthetic presentation values, not business oracles',
          'distinct_from': 'fixtures/demo-document.json',
          'selected-date': '2026-10-16',
          'analytics-payload': presentationData(),
          'body-loads': bodyLoads,
          'radar-values': radarValues,
          'daily-energy-values': energyValues,
        })}\n',
      );
    });
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(1600, 1100);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(gallery());
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('muscle-body-canvas')), findsOneWidget);
    await capture(tester, 'desktop-analysis-3d');
    await tester.tap(find.byKey(const ValueKey('muscle-style-2d')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('muscle-body-2d-canvas')), findsOneWidget);
    await capture(tester, 'desktop-analysis-2d');
    final rect = tester.getRect(find.byKey(const ValueKey('muscle-body-2d-canvas')));
    final scale = math.min((rect.height - 28) / 280, rect.width / 245);
    await tester.tapAt(rect.topLeft + Offset(rect.width * .25 - 10 * scale, 7 + 51 * scale));
    await tester.pumpAndSettle();
    expect(selections.last, 'pectoralis-major');

    tester.view.physicalSize = const Size(1440, 1000);
    await tester.pumpWidget(gallery(calendar: true));
    await tester.pumpAndSettle();
    expect(find.byWidgetPredicate((w) => w.key is ValueKey<String> && (w.key as ValueKey<String>).value.startsWith('calendar-date-')), findsNWidgets(42));
    await capture(tester, 'desktop-calendar');
    selections.clear();
    await tester.tap(find.byKey(const ValueKey('calendar-date-2026-10-16')));
    await tester.pumpAndSettle();
    expect(selections, ['2026-10-16']);
    await tester.tap(find.byKey(const ValueKey('calendar-next-month')));
    await tester.pumpAndSettle();
    expect(months, ['2026-11']);

    tester.view.physicalSize = const Size(390, 1180);
    await tester.pumpWidget(gallery(calendar: true, mobile: true));
    await tester.pumpAndSettle();
    await capture(tester, 'mobile-calendar');
    await tester.tap(find.byTooltip('Open navigation menu'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('navigate-library')));
    await tester.pumpAndSettle();
    expect(navigation, ['library']);
    expect(tester.takeException(), isNull);
  });
}
