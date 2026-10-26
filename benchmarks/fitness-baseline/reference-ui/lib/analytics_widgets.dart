import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/material.dart';

const _ink = Color(0xFF243B36);
const _muted = Color(0xFF74817A);
const _teal = Color(0xFF258675);
const _paper = Color(0xFFF6F5EF);
const _previous = Color(0xFFBCCDC5);

Map<String, dynamic> _map(dynamic value) =>
    value is Map ? Map<String, dynamic>.from(value) : {};
double _number(dynamic value) =>
    value is num && value.isFinite ? value.toDouble() : 0;
String _quantity(dynamic value) {
  final n = _number(value);
  return n == n.roundToDouble() ? n.toInt().toString() : n.toStringAsFixed(1);
}

/// Pure UI adapters: both widgets consume JSON from analytics/payload. The
/// caller owns data, selected date and month navigation; widgets perform no I/O.
/// Parsing is cached until the serialized input actually changes.
abstract class _JsonState<T extends StatefulWidget> extends State<T> {
  String? _lastJson;
  Map<String, dynamic> _lastData = {};
  Map<String, dynamic> decode(String json) {
    if (json != _lastJson) {
      _lastJson = json;
      try {
        _lastData = _map(jsonDecode(json));
      } on FormatException {
        _lastData = {};
      }
    }
    return _lastData;
  }
}

class TrainingCalendar extends StatefulWidget {
  const TrainingCalendar({
    super.key,
    required this.dataJson,
    this.selectedDate,
    this.onSelect,
    this.onMonthChanged,
  });
  final String dataJson;
  final String? selectedDate;
  final ValueChanged<String>? onSelect;

  /// Emits YYYY-MM. The caller produces the new month's analytics payload.
  final ValueChanged<String>? onMonthChanged;

  @override
  State<TrainingCalendar> createState() => _TrainingCalendarState();
}

class _TrainingCalendarState extends _JsonState<TrainingCalendar> {
  bool _canMove(String month, int delta) {
    final parsed = DateTime.tryParse('$month-01');
    if (parsed == null) return false;
    final next = DateTime(parsed.year, parsed.month + delta);
    return next.year >= 1900 && next.year <= 2100;
  }

  void _month(String month, int delta) {
    if (!_canMove(month, delta)) return;
    final parsed = DateTime.tryParse('$month-01');
    if (parsed == null) return;
    final next = DateTime(parsed.year, parsed.month + delta);
    widget.onMonthChanged?.call(
      '${next.year.toString().padLeft(4, '0')}-${next.month.toString().padLeft(2, '0')}',
    );
  }

  @override
  Widget build(BuildContext context) {
    final data = _map(decode(widget.dataJson)['calendar']);
    final month = data['month'] as String? ?? '';
    final cells = (data['cells'] is List ? data['cells'] as List : <dynamic>[])
        .map(_map)
        .toList(growable: false);
    final maxMinutes = cells.fold<double>(
      1,
      (n, c) => math.max(n, _number(c['duration-min'])),
    );
    final monthParts = month.split('-');
    final title =
        monthParts.length == 2
            ? '${monthParts[0]} / ${int.tryParse(monthParts[1]) ?? monthParts[1]}'
            : 'Calendar';
    return _Panel(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              IconButton(
                key: const ValueKey('calendar-previous-month'),
                tooltip: 'Previous month',
                onPressed:
                    widget.onMonthChanged == null || !_canMove(month, -1)
                        ? null
                        : () => _month(month, -1),
                icon: const Icon(Icons.chevron_left),
              ),
              Expanded(
                child: Text(
                  title,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    fontSize: 17,
                    color: _ink,
                  ),
                ),
              ),
              IconButton(
                key: const ValueKey('calendar-next-month'),
                tooltip: 'Next month',
                onPressed:
                    widget.onMonthChanged == null || !_canMove(month, 1)
                        ? null
                        : () => _month(month, 1),
                icon: const Icon(Icons.chevron_right),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              for (final day in ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'])
                Expanded(
                  child: Center(
                    child: Text(
                      day,
                      style: const TextStyle(color: _muted, fontSize: 12),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),
          if (cells.isEmpty)
            const Padding(
              padding: EdgeInsets.all(20),
              child: Text('Choose a month to view training', style: TextStyle(color: _muted)),
            )
          else
            for (var row = 0; row < (cells.length / 7).ceil(); row++)
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (var col = 0; col < 7; col++)
                    Expanded(
                      child:
                          row * 7 + col < cells.length
                              ? _day(cells[row * 7 + col], maxMinutes)
                              : const SizedBox(),
                    ),
                ],
              ),
          const SizedBox(height: 10),
          const Wrap(
            alignment: WrapAlignment.center,
            spacing: 12,
            runSpacing: 4,
            children: [
              Text('Shade: active minutes', style: TextStyle(color: _teal, fontSize: 11)),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.check, size: 12, color: _teal),
                  SizedBox(width: 3),
                  Text('Plan complete', style: TextStyle(color: _ink, fontSize: 11)),
                ],
              ),
              Text('o Plan scheduled', style: TextStyle(color: _muted, fontSize: 11)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _day(Map<String, dynamic> cell, double maximum) {
    final date = cell['date'] as String? ?? '';
    final parsed = DateTime.tryParse(date);
    final selectable =
        parsed != null && parsed.year >= 1900 && parsed.year <= 2100;
    final active = cell['in-month?'] == true;
    final minutes = math.max(0.0, _number(cell['duration-min']));
    final completed = cell['plan-complete?'] == true;
    final planned = _number(cell['planned-items']) > 0;
    final selected = widget.selectedDate == date;
    final kcal = cell['kcal'];
    final detail = [
      if (minutes > 0) '${_quantity(minutes)} min',
      if (kcal is num && kcal.isFinite) '${_quantity(kcal)} kcal',
      if (completed) 'Plan complete' else if (planned) 'Plan scheduled',
    ].join(', ');
    return Semantics(
      label: '$date${detail.isEmpty ? '' : ', $detail'}',
      selected: selected,
      button: selectable,
      child: Padding(
        padding: const EdgeInsets.all(2),
        child: Material(
          color:
              !active
                  ? Colors.transparent
                  : minutes > 0
                  ? Color.lerp(
                    const Color(0xFFEDF4ED),
                    const Color(0xFFA6D5BE),
                    math.sqrt(minutes / maximum),
                  )!
                  : const Color(0xFFEFEEE7),
          borderRadius: BorderRadius.circular(9),
          child: InkWell(
            key: date.isEmpty ? null : ValueKey('calendar-date-$date'),
            borderRadius: BorderRadius.circular(9),
            onTap:
                widget.onSelect == null || !selectable
                    ? null
                    : () => widget.onSelect!(date),
            child: Container(
              height: 74,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(9),
                border: selected ? Border.all(color: _teal, width: 2) : null,
              ),
              padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 5),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.start,
                children: [
                  FittedBox(
                    fit: BoxFit.scaleDown,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          '${cell['day'] ?? ''}',
                          style: TextStyle(
                            fontWeight:
                                selected ? FontWeight.bold : FontWeight.w500,
                            fontSize: 13,
                            color:
                                active ? _ink : _muted.withValues(alpha: .55),
                          ),
                        ),
                        if (completed)
                          const Icon(Icons.check, size: 10, color: _teal)
                        else if (planned)
                          Text(
                            'o',
                            style: TextStyle(
                              fontSize: 9,
                              color: completed ? _teal : _muted,
                            ),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 3),
                  if (minutes > 0)
                    FittedBox(
                      child: Text(
                        '${_quantity(minutes)} min',
                        style: TextStyle(
                          fontSize: 10,
                          color: active ? _teal : _muted,
                        ),
                      ),
                    ),
                  if (kcal is num && kcal.isFinite)
                    FittedBox(
                      child: Text(
                        '${_quantity(kcal)} kcal',
                        style: const TextStyle(fontSize: 9, color: _ink),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class AnalyticsComparison extends StatefulWidget {
  const AnalyticsComparison({super.key, required this.dataJson});
  final String dataJson;
  @override
  State<AnalyticsComparison> createState() => _AnalyticsComparisonState();
}

class _AnalyticsComparisonState extends _JsonState<AnalyticsComparison> {
  @override
  Widget build(BuildContext context) {
    final data = decode(widget.dataJson);
    final comparison = _map(data['comparison']);
    final currentRange = _map(data['range']);
    final previousRange = _map(data['previous-range']);
    final sets = data['metric'] == 'sets';
    final rows =
        (comparison['muscles'] is List
                ? comparison['muscles'] as List
                : <dynamic>[])
            .map(_map)
            .toList();
    final maximum = rows.fold<double>(
      1,
      (n, row) => math.max(
        n,
        math.max(_number(row['current']), _number(row['previous'])),
      ),
    );
    return _Panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            data['mode'] == 'planned' ? 'Planned training comparison' : 'Completed training comparison',
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: _ink,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Current ${currentRange['from'] ?? '-'} - ${currentRange['to'] ?? '-'}',
            style: const TextStyle(fontSize: 11, color: _muted),
          ),
          Text(
            'Previous ${previousRange['from'] ?? '-'} - ${previousRange['to'] ?? '-'}',
            style: const TextStyle(fontSize: 11, color: _muted),
          ),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: _stat('Active minutes', _map(comparison['duration-min']))),
              const SizedBox(width: 6),
              Expanded(child: _stat('Sets', _map(comparison['sets']))),
              const SizedBox(width: 6),
              Expanded(child: _stat('Training days', _map(comparison['training-days']))),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: Text(
                  sets ? 'Muscle set comparison' : 'Weighted muscle minutes',
                  style: const TextStyle(
                    fontWeight: FontWeight.w600,
                    color: _ink,
                  ),
                ),
              ),
              const _Swatch(color: _previous, label: 'Previous'),
              const SizedBox(width: 10),
              const _Swatch(color: _teal, label: 'Current'),
            ],
          ),
          const SizedBox(height: 12),
          if (rows.isEmpty)
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('No comparison data', style: TextStyle(color: _muted)),
            )
          else
            SingleChildScrollView(
              key: const ValueKey('muscle-comparison-scroll'),
              scrollDirection: Axis.horizontal,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  for (final row in rows)
                    SizedBox(
                      width: 91,
                      child: Semantics(
                        label:
                            '${row['name']}, Previous ${_quantity(row['previous'])}, Current ${_quantity(row['current'])}',
                        child: Column(
                          children: [
                            SizedBox(
                              height: 130,
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                crossAxisAlignment: CrossAxisAlignment.end,
                                children: [
                                  _bar(
                                    _number(row['previous']),
                                    maximum,
                                    _previous,
                                  ),
                                  const SizedBox(width: 5),
                                  _bar(_number(row['current']), maximum, _teal),
                                ],
                              ),
                            ),
                            const SizedBox(height: 7),
                            SizedBox(
                              height: 36,
                              child: Text(
                                '${row['name'] ?? row['id'] ?? ''}',
                                textAlign: TextAlign.center,
                                maxLines: 2,
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: _ink,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            ),
          const SizedBox(height: 4),
          Text(
            sets
                ? 'Muscles: primary 1 set, secondary 0.5. Overnight sets count once. Total sets do not sum muscles.'
                : 'Primary muscles use active minutes; secondary muscles use 50%.',
            style: const TextStyle(fontSize: 11, color: _muted),
          ),
        ],
      ),
    );
  }

  Widget _bar(double value, double maximum, Color color) => SizedBox(
    width: 31,
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        FittedBox(
          child: Text(
            _quantity(value),
            style: const TextStyle(fontSize: 10, color: _muted),
          ),
        ),
        const SizedBox(height: 4),
        Container(
          width: 22,
          height: math.max(1, math.max(0, value) / maximum * 103),
          decoration: BoxDecoration(
            color: color,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(4)),
          ),
        ),
      ],
    ),
  );

  Widget _stat(String label, Map<String, dynamic> value) {
    final percent = value['percent'];
    final delta = _number(value['delta']);
    final change =
        percent is num && percent.isFinite
            ? '${percent >= 0 ? '+' : ''}${percent.toStringAsFixed(0)}%'
            : delta > 0
            ? 'New'
            : '-';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 11),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: .65),
        borderRadius: BorderRadius.circular(11),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: const TextStyle(color: _muted, fontSize: 11)),
          const SizedBox(height: 5),
          FittedBox(
            child: Text(
              _quantity(value['current']),
              style: const TextStyle(
                color: _ink,
                fontSize: 23,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Text(
            '$change vs previous',
            style: const TextStyle(color: _teal, fontSize: 10),
          ),
        ],
      ),
    );
  }
}

class _Swatch extends StatelessWidget {
  const _Swatch({required this.color, required this.label});
  final Color color;
  final String label;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(
        width: 7,
        height: 7,
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(2),
        ),
      ),
      const SizedBox(width: 4),
      Text(label, style: const TextStyle(fontSize: 10, color: _muted)),
    ],
  );
}

class _Panel extends StatelessWidget {
  const _Panel({required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: _paper,
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: const Color(0xFFE5E8DF)),
    ),
    child: child,
  );
}
