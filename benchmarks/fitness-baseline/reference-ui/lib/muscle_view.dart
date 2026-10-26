import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

/// Names shared by the selectable 3D regions and the statistics widgets.
const Map<String, String> muscleNames = {
  'pectoralis-major': 'Pectoralis major',
  'latissimus-dorsi': 'Latissimus dorsi',
  'trapezius': 'Trapezius',
  'rhomboids': 'Rhomboids',
  'anterior-deltoid': 'Anterior deltoid',
  'lateral-deltoid': 'Lateral deltoid',
  'posterior-deltoid': 'Posterior deltoid',
  'biceps-brachii': 'Biceps brachii',
  'triceps-brachii': 'Triceps brachii',
  'rectus-abdominis': 'Rectus abdominis',
  'obliques': 'Obliques',
  'erector-spinae': 'Erector spinae',
  'gluteus-maximus': 'Gluteus maximus',
  'gluteus-medius': 'Gluteus medius',
  'quadriceps': 'Quadriceps',
  'rectus-femoris': 'Rectus femoris',
  'hamstrings': 'Hamstrings',
  'iliopsoas': 'Iliopsoas',
  'gastrocnemius': 'Gastrocnemius',
  'soleus': 'Soleus',
};

const _ink = Color(0xFF243B36);
const _muted = Color(0xFF74817A);
const _teal = Color(0xFF258675);
const _orange = Color(0xFFE98D4F);
const _paper = Color(0xFFF6F5EF);

double _positive(double value) => value.isFinite && value > 0 ? value : 0;

double _maximum(Iterable<double> values) =>
    values.fold<double>(0, (a, b) => math.max(a, _positive(b)));

/// A procedural triangle-mesh body, with perspective, depth sorting and light.
///
/// Values in 0-1 retain that scale. Larger values are normalized to the largest
/// supplied value. This is a training-load illustration, not an anatomical or
/// medical measurement. Muscle IDs match [muscleNames].
class MuscleBody extends StatefulWidget {
  const MuscleBody({
    super.key,
    this.loads = const {},
    this.onSelect,
    this.height = 470,
  });

  final Map<String, double> loads;
  final ValueChanged<String>? onSelect;
  final double height;

  @override
  State<MuscleBody> createState() => _MuscleBodyState();
}

class _MuscleBodyState extends State<MuscleBody> {
  double _yaw = 0;
  double _pitch = 0;
  String? _selected;
  _BodyPainter? _painter;
  _FlatBodyPainter? _flatPainter;
  bool _flat = false;

  void _view(double yaw) => setState(() {
    _yaw = yaw;
    _pitch = 0;
  });

  void _select(String id) {
    setState(() => _selected = id);
    widget.onSelect?.call(id);
  }

  @override
  Widget build(BuildContext context) {
    final maximum = math.max(1.0, _maximum(widget.loads.values));
    final selectedLoad = _positive(widget.loads[_selected] ?? 0) / maximum;
    // Keep the scene and GPU vertex buffer when an unrelated timer/parent
    // rebuilds. A new map instance containing the same numbers is unchanged.
    if (_painter == null ||
        _painter!.yaw != _yaw ||
        _painter!.pitch != _pitch ||
        _painter!.selected != _selected ||
        !mapEquals(_painter!.loads, widget.loads)) {
      _painter = _BodyPainter(
        yaw: _yaw,
        pitch: _pitch,
        loads: Map<String, double>.of(widget.loads),
        selected: _selected,
      );
    }
    if (_flatPainter == null ||
        _flatPainter!.selected != _selected ||
        !mapEquals(_flatPainter!.loads, widget.loads)) {
      _flatPainter = _FlatBodyPainter(
        Map<String, double>.of(widget.loads),
        _selected,
      );
    }
    return Container(
      height: widget.height,
      decoration: BoxDecoration(
        color: _paper,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: const Color(0xFFE5E8DF)),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 8, 10, 0),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    _flat ? 'Front and back' : 'Drag to rotate',
                    style: const TextStyle(color: _muted, fontSize: 12),
                  ),
                ),
                _styleButton('3D', false),
                _styleButton('2D', true),
                if (!_flat)
                  TextButton(
                    key: const ValueKey('muscle-view-front'),
                    onPressed: () => _view(0),
                    style: TextButton.styleFrom(
                      foregroundColor: _teal,
                      minimumSize: const Size(42, 34),
                      padding: const EdgeInsets.symmetric(horizontal: 9),
                    ),
                    child: const Text('Front'),
                  ),
                if (!_flat)
                  TextButton(
                    key: const ValueKey('muscle-view-back'),
                    onPressed: () => _view(math.pi),
                    style: TextButton.styleFrom(
                      foregroundColor: _teal,
                      minimumSize: const Size(42, 34),
                      padding: const EdgeInsets.symmetric(horizontal: 9),
                    ),
                    child: const Text('Back'),
                  ),
              ],
            ),
          ),
          Expanded(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final size = constraints.biggest;
                return Semantics(
                  label:
                      _flat ? '2D front and back muscle map. Select a region.' : '3D muscle load model. Drag to rotate and select a region.',
                  child: MouseRegion(
                    cursor:
                        _flat
                            ? SystemMouseCursors.click
                            : SystemMouseCursors.grab,
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onPanUpdate:
                          _flat
                              ? null
                              : (details) => setState(() {
                                _yaw =
                                    (_yaw + details.delta.dx / 115) %
                                    (2 * math.pi);
                                _pitch = (_pitch + details.delta.dy / 220)
                                    .clamp(-0.38, 0.38);
                              }),
                      onTapUp: (details) {
                        final id =
                            _flat
                                ? _flatPainter?.pick(
                                  details.localPosition,
                                  size,
                                )
                                : _painter?.pick(details.localPosition, size);
                        if (id != null) _select(id);
                      },
                      child: RepaintBoundary(
                        child: CustomPaint(
                          key: ValueKey(
                            _flat
                                ? 'muscle-body-2d-canvas'
                                : 'muscle-body-canvas',
                          ),
                          painter: _flat ? _flatPainter : _painter,
                          size: size,
                          isComplex: !_flat,
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 0, 14, 11),
            child: Column(
              children: [
                SizedBox(
                  height: 25,
                  child:
                      _selected == null
                          ? Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              _LegendDot(
                                color: _flat ? const Color(0xFFB1D8C5) : _teal,
                                text: 'Lower',
                              ),
                              const SizedBox(width: 16),
                              _LegendDot(
                                color:
                                    _flat ? const Color(0xFF217B67) : _orange,
                                text: 'Higher',
                              ),
                              const SizedBox(width: 8),
                              const Text(
                                'relative load',
                                style: TextStyle(color: _muted, fontSize: 11),
                              ),
                            ],
                          )
                          : Text(
                            '${muscleNames[_selected]} - relative load ${(selectedLoad * 100).round()}%',
                            key: const ValueKey('muscle-selection'),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: _teal,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                ),
                Text(
                  _flat ? 'Select a muscle. Deep muscles are projected on the surface.' : 'Training load illustration; not a medical model.',
                  style: const TextStyle(color: _muted, fontSize: 10.5),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _styleButton(String label, bool flat) => Padding(
    padding: const EdgeInsets.only(right: 4),
    child: ChoiceChip(
      key: ValueKey('muscle-style-${label.toLowerCase()}'),
      label: Text(label, style: const TextStyle(fontSize: 11)),
      visualDensity: VisualDensity.compact,
      showCheckmark: false,
      selected: _flat == flat,
      onSelected: (_) => setState(() => _flat = flat),
    ),
  );
}

class _LegendDot extends StatelessWidget {
  const _LegendDot({required this.color, required this.text});
  final Color color;
  final String text;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(
        width: 7,
        height: 7,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      ),
      const SizedBox(width: 5),
      Text(text, style: const TextStyle(color: _muted, fontSize: 11)),
    ],
  );
}

class _FlatRegion {
  const _FlatRegion(this.id, this.path, this.anchor);
  final String id;
  final Path path;
  final Offset anchor;
}

/// Original schematic anatomy, in fixed 100 x 280 body coordinates. Front and
/// back use exactly the same shell, scale and camera. Shapes are cached once.
class _FlatAnatomy {
  static final Path shell = _shell();
  static final List<_FlatRegion> front = _regions(false);
  static final List<_FlatRegion> back = _regions(true);

  static Path _polygon(List<Offset> points) => Path()..addPolygon(points, true);
  static Path _mirror(Path path) => path.transform(
    Float64List.fromList([-1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 100, 0, 0, 1]),
  );

  static Path _shell() {
    final shell =
        Path()
          ..addOval(const Rect.fromLTWH(40, 3, 20, 26))
          ..addRRect(
            RRect.fromRectAndRadius(
              const Rect.fromLTWH(44, 24, 12, 14),
              const Radius.circular(3),
            ),
          )
          ..moveTo(44, 32)
          ..quadraticBezierTo(34, 34, 28, 40)
          ..quadraticBezierTo(27, 53, 32, 69)
          ..lineTo(35, 103)
          ..quadraticBezierTo(28, 121, 31, 141)
          ..quadraticBezierTo(50, 151, 69, 141)
          ..quadraticBezierTo(72, 121, 65, 103)
          ..lineTo(68, 69)
          ..quadraticBezierTo(73, 53, 72, 40)
          ..quadraticBezierTo(66, 34, 56, 32)
          ..close();
    final arm =
        Path()
          ..moveTo(29, 38)
          ..cubicTo(21, 37, 18, 45, 18, 54)
          ..lineTo(14, 80)
          ..quadraticBezierTo(12, 85, 14, 90)
          ..lineTo(8, 121)
          ..lineTo(5, 135)
          ..quadraticBezierTo(3, 144, 8, 147)
          ..quadraticBezierTo(12, 146, 13, 137)
          ..lineTo(15, 130)
          ..quadraticBezierTo(20, 112, 22, 94)
          ..lineTo(25, 85)
          ..quadraticBezierTo(29, 68, 32, 56)
          ..quadraticBezierTo(36, 44, 29, 38)
          ..close();
    final leg =
        Path()
          ..moveTo(31, 132)
          ..quadraticBezierTo(25, 157, 29, 184)
          ..lineTo(30, 202)
          ..quadraticBezierTo(27, 220, 32, 240)
          ..lineTo(33, 258)
          ..lineTo(29, 267)
          ..quadraticBezierTo(27, 273, 33, 273)
          ..lineTo(42, 273)
          ..quadraticBezierTo(46, 270, 43, 264)
          ..lineTo(42, 255)
          ..lineTo(45, 216)
          ..lineTo(44, 200)
          ..lineTo(49, 163)
          ..lineTo(49, 143)
          ..close();
    return shell
      ..addPath(arm, Offset.zero)
      ..addPath(_mirror(arm), Offset.zero)
      ..addPath(leg, Offset.zero)
      ..addPath(_mirror(leg), Offset.zero);
  }

  static List<_FlatRegion> _regions(bool back) {
    final regions = <_FlatRegion>[];
    void pair(String id, List<Offset> points, Offset anchor) {
      final path = _polygon(points);
      regions.add(_FlatRegion(id, path, anchor));
      regions.add(
        _FlatRegion(id, _mirror(path), Offset(100 - anchor.dx, anchor.dy)),
      );
    }

    if (!back) {
      pair('pectoralis-major', const [
        Offset(49, 42),
        Offset(36, 39),
        Offset(30, 45),
        Offset(34, 58),
        Offset(49, 61),
      ], const Offset(40, 51));
      pair('anterior-deltoid', const [
        Offset(30, 39),
        Offset(23, 41),
        Offset(21, 51),
        Offset(26, 60),
        Offset(31, 50),
      ], const Offset(27, 48));
      pair('lateral-deltoid', const [
        Offset(23, 42),
        Offset(19, 46),
        Offset(18, 57),
        Offset(21, 63),
        Offset(23, 53),
      ], const Offset(20, 52));
      pair('biceps-brachii', const [
        Offset(24, 60),
        Offset(20, 64),
        Offset(17, 81),
        Offset(21, 86),
        Offset(26, 68),
      ], const Offset(22, 72));
      pair('obliques', const [
        Offset(34, 64),
        Offset(41, 70),
        Offset(40, 105),
        Offset(35, 114),
        Offset(35, 95),
      ], const Offset(38, 91));
      for (var y = 65.0; y < 102; y += 13) {
        pair('rectus-abdominis', [
          Offset(43, y),
          Offset(49, y),
          Offset(49, y + 10),
          Offset(42, y + 10),
        ], Offset(46, y + 5));
      }
      pair('iliopsoas', const [
        Offset(37, 112),
        Offset(43, 113),
        Offset(49, 132),
        Offset(42, 126),
      ], const Offset(42, 119));
      pair('quadriceps', const [
        Offset(32, 141),
        Offset(45, 146),
        Offset(43, 177),
        Offset(38, 193),
        Offset(31, 181),
        Offset(29, 159),
      ], const Offset(33, 166));
      pair('rectus-femoris', const [
        Offset(37, 145),
        Offset(41, 147),
        Offset(40, 178),
        Offset(37, 185),
        Offset(35, 177),
      ], const Offset(38, 163));
    } else {
      pair('trapezius', const [
        Offset(49, 30),
        Offset(43, 34),
        Offset(31, 40),
        Offset(40, 49),
        Offset(49, 62),
      ], const Offset(45, 43));
      pair('posterior-deltoid', const [
        Offset(29, 39),
        Offset(22, 41),
        Offset(19, 49),
        Offset(23, 59),
        Offset(32, 50),
      ], const Offset(26, 48));
      pair('triceps-brachii', const [
        Offset(23, 60),
        Offset(20, 64),
        Offset(16, 82),
        Offset(21, 86),
        Offset(27, 66),
      ], const Offset(22, 72));
      pair('rhomboids', const [
        Offset(49, 64),
        Offset(41, 52),
        Offset(38, 62),
        Offset(44, 77),
        Offset(49, 73),
      ], const Offset(44, 65));
      pair('latissimus-dorsi', const [
        Offset(32, 60),
        Offset(39, 64),
        Offset(43, 82),
        Offset(43, 105),
        Offset(35, 94),
      ], const Offset(37, 82));
      pair('erector-spinae', const [
        Offset(46, 78),
        Offset(49, 76),
        Offset(49, 115),
        Offset(44, 111),
      ], const Offset(47, 96));
      pair('gluteus-medius', const [
        Offset(34, 111),
        Offset(40, 115),
        Offset(37, 128),
        Offset(30, 128),
      ], const Offset(34, 119));
      pair('gluteus-maximus', const [
        Offset(40, 117),
        Offset(49, 119),
        Offset(48, 141),
        Offset(35, 144),
        Offset(31, 136),
      ], const Offset(41, 132));
      pair('hamstrings', const [
        Offset(32, 147),
        Offset(45, 148),
        Offset(43, 180),
        Offset(39, 194),
        Offset(32, 187),
        Offset(29, 165),
      ], const Offset(37, 167));
      pair('gastrocnemius', const [
        Offset(32, 204),
        Offset(41, 205),
        Offset(43, 219),
        Offset(39, 238),
        Offset(33, 232),
        Offset(30, 217),
      ], const Offset(37, 219));
      pair('soleus', const [
        Offset(33, 234),
        Offset(39, 240),
        Offset(40, 252),
        Offset(34, 254),
      ], const Offset(37, 246));
    }
    return regions;
  }
}

class _FlatBodyPainter extends CustomPainter {
  _FlatBodyPainter(this.loads, this.selected);
  final Map<String, double> loads;
  final String? selected;
  double _scale(Size size) =>
      math.min((size.height - 28) / 280, size.width / 245);
  Offset _origin(Size size, bool back) =>
      Offset(size.width * (back ? .75 : .25) - 50 * _scale(size), 7);

  String? pick(Offset point, Size size) {
    final scale = _scale(size);
    if (scale <= 0) return null;
    for (final back in [false, true]) {
      final local = (point - _origin(size, back)) / scale;
      for (final region
          in (back ? _FlatAnatomy.back : _FlatAnatomy.front).reversed) {
        if (region.path.contains(local)) return region.id;
      }
    }
    return null;
  }

  @override
  void paint(Canvas canvas, Size size) {
    final scale = _scale(size);
    if (scale <= 0) return;
    final maximum = math.max(1.0, _maximum(loads.values));
    for (final back in [false, true]) {
      final origin = _origin(size, back);
      final regions = back ? _FlatAnatomy.back : _FlatAnatomy.front;
      canvas.save();
      canvas.translate(origin.dx, origin.dy);
      canvas.scale(scale);
      canvas.drawPath(
        _FlatAnatomy.shell,
        Paint()..color = const Color(0xFFDDE2DE),
      );
      for (final region in regions) {
        final load = _positive(loads[region.id] ?? 0) / maximum;
        final color =
            load == 0
                ? const Color(0xFFCAD2CC)
                : Color.lerp(
                  const Color(0xFFB1D8C5),
                  const Color(0xFF217B67),
                  load,
                )!;
        canvas.drawPath(region.path, Paint()..color = color);
        canvas.drawPath(
          region.path,
          Paint()
            ..color = region.id == selected ? _orange : const Color(0xFFF6F5EF)
            ..style = PaintingStyle.stroke
            ..strokeWidth = region.id == selected ? 1.2 : .5,
        );
      }
      canvas.restore();
      _text(
        canvas,
        back ? 'Back' : 'Front',
        Offset(size.width * (back ? .75 : .25), size.height - 9),
        fontSize: 11,
      );
      // A labelled leader for the selected region; both outlines stay fixed.
      final matches = regions.where((region) => region.id == selected);
      if (matches.isNotEmpty) {
        final region = matches.first;
        final anchor = origin + region.anchor * scale;
        final edge = Offset(size.width * (back ? .5 : 0) + 4, anchor.dy);
        canvas.drawLine(
          anchor,
          edge + const Offset(25, 0),
          Paint()
            ..color = _teal
            ..strokeWidth = .8,
        );
        canvas.drawCircle(anchor, 2, Paint()..color = _teal);
        _text(
          canvas,
          muscleNames[selected] ?? '',
          edge + const Offset(28, -13),
          maxWidth: 56,
          fontSize: 10,
          color: _ink,
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant _FlatBodyPainter oldDelegate) =>
      selected != oldDelegate.selected || !mapEquals(loads, oldDelegate.loads);
}

class _V3 {
  const _V3(this.x, this.y, this.z);
  final double x, y, z;
  _V3 operator +(_V3 o) => _V3(x + o.x, y + o.y, z + o.z);
  _V3 operator -(_V3 o) => _V3(x - o.x, y - o.y, z - o.z);
  _V3 operator *(double s) => _V3(x * s, y * s, z * s);
  double dot(_V3 o) => x * o.x + y * o.y + z * o.z;
  _V3 cross(_V3 o) =>
      _V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
  _V3 get unit {
    final length = math.sqrt(dot(this));
    return length < 1e-9 ? const _V3(0, 0, 1) : this * (1 / length);
  }
}

class _Mesh {
  const _Mesh(this.vertices, this.faces, this.region, this.color);
  final List<_V3> vertices;
  final List<List<int>> faces;
  final String? region;
  final Color color;
}

class _Face {
  const _Face(this.a, this.b, this.c, this.depth, this.color, this.region);
  final Offset a, b, c;
  final double depth;
  final Color color;
  final String? region;
  Path get path =>
      Path()
        ..moveTo(a.dx, a.dy)
        ..lineTo(b.dx, b.dy)
        ..lineTo(c.dx, c.dy)
        ..close();

  bool contains(Offset p) {
    double side(Offset u, Offset v) =>
        (p.dx - v.dx) * (u.dy - v.dy) - (u.dx - v.dx) * (p.dy - v.dy);
    final d1 = side(a, b), d2 = side(b, c), d3 = side(c, a);
    return !((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0));
  }
}

/// All geometry is built once in world coordinates; every frame transforms and
/// projects those vertices. Muscle ellipsoids partly intersect the body shell.
class _BodyGeometry {
  static final List<_Mesh> meshes = _build();

  static List<_Mesh> _build() {
    final result = <_Mesh>[];
    const shell = Color(0xFFC4CEBD);
    const muscle = Color(0xFFB3CBB7);
    void add(
      _V3 c,
      _V3 r, {
      String? id,
      _V3 axis = const _V3(0, 1, 0),
      Color? color,
    }) {
      final ey = axis.unit;
      final ex = ey.cross(const _V3(0, 0, 1)).unit;
      final ez = ex.cross(ey).unit;
      const rows = 10, columns = 18;
      final vertices = <_V3>[];
      for (var i = 0; i <= rows; i++) {
        final theta = math.pi * i / rows;
        for (var j = 0; j <= columns; j++) {
          final phi = math.pi * 2 * j / columns;
          vertices.add(
            c +
                ex * (r.x * math.sin(theta) * math.cos(phi)) +
                ey * (r.y * math.cos(theta)) +
                ez * (r.z * math.sin(theta) * math.sin(phi)),
          );
        }
      }
      final faces = <List<int>>[];
      for (var i = 0; i < rows; i++) {
        for (var j = 0; j < columns; j++) {
          final a = i * (columns + 1) + j;
          final b = a + columns + 1;
          if (i != 0) faces.add([a, a + 1, b]);
          if (i != rows - 1) faces.add([a + 1, b + 1, b]);
        }
      }
      result.add(
        _Mesh(vertices, faces, id, color ?? (id == null ? shell : muscle)),
      );
    }

    add(const _V3(0, 5.89, 0), const _V3(.29, .39, .27));
    add(const _V3(0, 5.88, .27), const _V3(.065, .085, .062));
    add(const _V3(0, 5.38, 0), const _V3(.19, .24, .19));
    add(const _V3(0, 4.94, 0), const _V3(.65, .58, .30));
    add(const _V3(0, 4.35, -.015), const _V3(.475, .48, .255));
    add(const _V3(0, 3.84, -.01), const _V3(.50, .40, .29));
    for (final side in [-1.0, 1.0]) {
      add(_V3(side * .29, 5.88, 0), const _V3(.052, .11, .075));
      add(
        _V3(side * .097, 5.98, .24),
        const _V3(.029, .014, .011),
        color: const Color(0xFF667A6D),
      );
      add(_V3(side * .69, 5.02, 0), const _V3(.255, .27, .245));
      add(
        _V3(side * .92, 4.53, 0),
        const _V3(.205, .54, .205),
        axis: _V3(-side * .32, 1, 0),
      );
      add(_V3(side * 1.075, 4.05, 0), const _V3(.165, .17, .17));
      add(
        _V3(side * 1.16, 3.69, .015),
        const _V3(.148, .43, .155),
        axis: _V3(-side * .2, 1, 0),
      );
      add(_V3(side * 1.24, 3.16, .025), const _V3(.12, .22, .085));
      add(
        _V3(side * 1.13, 3.22, .06),
        const _V3(.045, .11, .06),
        axis: _V3(-side * .3, 1, 0),
      );
      add(
        _V3(side * .315, 2.95, 0),
        const _V3(.275, .86, .28),
        axis: _V3(-side * .045, 1, 0),
      );
      add(_V3(side * .35, 2.08, .015), const _V3(.193, .20, .21));
      add(_V3(side * .37, 1.39, 0), const _V3(.185, .68, .21));
      add(_V3(side * .38, .67, .025), const _V3(.125, .20, .14));
      add(_V3(side * .39, .37, .18), const _V3(.17, .18, .34));

      // Anterior and lateral surfaces.
      add(
        _V3(side * .315, 4.97, .275),
        const _V3(.315, .24, .115),
        id: 'pectoralis-major',
        axis: _V3(side * .10, 1, 0),
      );
      add(
        _V3(side * .68, 5.035, .145),
        const _V3(.21, .235, .14),
        id: 'anterior-deltoid',
        axis: _V3(-side * .25, 1, 0),
      );
      add(
        _V3(side * .80, 4.99, 0),
        const _V3(.135, .25, .215),
        id: 'lateral-deltoid',
        axis: _V3(-side * .25, 1, 0),
      );
      add(
        _V3(side * .935, 4.51, .155),
        const _V3(.135, .345, .095),
        id: 'biceps-brachii',
        axis: _V3(-side * .32, 1, 0),
      );
      add(
        _V3(side * .37, 4.30, .155),
        const _V3(.125, .355, .105),
        id: 'obliques',
        axis: _V3(side * .13, 1, 0),
      );
      for (var row = 0; row < 3; row++) {
        add(
          _V3(side * .135, 4.56 - row * .235, .247),
          const _V3(.125, .118, .079),
          id: 'rectus-abdominis',
        );
      }
      add(
        _V3(side * .25, 3.665, .225),
        const _V3(.13, .22, .075),
        id: 'iliopsoas',
        axis: _V3(side * .28, 1, 0),
      );
      add(
        _V3(side * .335, 2.965, .185),
        const _V3(.235, .665, .15),
        id: 'quadriceps',
        axis: _V3(-side * .045, 1, 0),
      );
      add(
        _V3(side * .315, 2.98, .31),
        const _V3(.108, .57, .053),
        id: 'rectus-femoris',
        axis: _V3(-side * .045, 1, 0),
      );

      // Posterior surfaces remain physical geometry when orbiting the model.
      add(
        _V3(side * .685, 5.015, -.15),
        const _V3(.21, .24, .135),
        id: 'posterior-deltoid',
        axis: _V3(-side * .25, 1, 0),
      );
      add(
        _V3(side * .92, 4.50, -.155),
        const _V3(.16, .365, .092),
        id: 'triceps-brachii',
        axis: _V3(-side * .32, 1, 0),
      );
      add(
        _V3(side * .265, 5.22, -.175),
        const _V3(.275, .275, .115),
        id: 'trapezius',
        axis: _V3(-side * .6, 1, 0),
      );
      add(
        _V3(side * .21, 4.91, -.275),
        const _V3(.175, .29, .065),
        id: 'rhomboids',
        axis: _V3(side * .2, 1, 0),
      );
      add(
        _V3(side * .40, 4.57, -.22),
        const _V3(.215, .46, .105),
        id: 'latissimus-dorsi',
        axis: _V3(side * .23, 1, 0),
      );
      add(
        _V3(side * .105, 4.32, -.255),
        const _V3(.072, .49, .06),
        id: 'erector-spinae',
      );
      add(
        _V3(side * .26, 3.745, -.235),
        const _V3(.275, .29, .165),
        id: 'gluteus-maximus',
      );
      add(
        _V3(side * .44, 3.925, -.07),
        const _V3(.125, .235, .235),
        id: 'gluteus-medius',
      );
      add(
        _V3(side * .335, 2.94, -.19),
        const _V3(.22, .64, .13),
        id: 'hamstrings',
        axis: _V3(-side * .045, 1, 0),
      );
      add(
        _V3(side * .375, 1.48, -.16),
        const _V3(.175, .43, .12),
        id: 'gastrocnemius',
      );
      add(
        _V3(side * .375, 1.04, -.145),
        const _V3(.135, .38, .072),
        id: 'soleus',
      );
    }
    return result;
  }
}

class _BodyPainter extends CustomPainter {
  _BodyPainter({
    required this.yaw,
    required this.pitch,
    required this.loads,
    required this.selected,
  });
  final double yaw, pitch;
  final Map<String, double> loads;
  final String? selected;
  List<_Face>? _faces;
  ui.Vertices? _vertices;
  Size? _size;
  late final double _cosYaw = math.cos(yaw), _sinYaw = math.sin(yaw);
  late final double _cosPitch = math.cos(pitch), _sinPitch = math.sin(pitch);

  _V3 _rotate(_V3 v) {
    final x = v.x * _cosYaw + v.z * _sinYaw;
    final z = -v.x * _sinYaw + v.z * _cosYaw;
    return _V3(
      x,
      v.y * _cosPitch - z * _sinPitch,
      v.y * _sinPitch + z * _cosPitch,
    );
  }

  List<_Face> _scene(Size size) {
    if (_faces != null && _size == size) return _faces!;
    _vertices = null;
    _size = size;
    final scale = math.min(size.height / 6.8, size.width / 3.35);
    final center = Offset(size.width / 2, size.height / 2);
    Offset project(_V3 v) {
      final perspective = 8.5 / (8.5 - v.z);
      return center +
          Offset(v.x * scale * perspective, -v.y * scale * perspective);
    }

    final faces = <_Face>[];
    final maximum = math.max(1.0, _maximum(loads.values));
    final light = const _V3(-.45, .70, 1.0).unit;
    for (final mesh in _BodyGeometry.meshes) {
      final vertices = mesh.vertices
          .map((v) => _rotate(v - const _V3(0, 3.23, 0)))
          .toList(growable: false);
      final projected = vertices.map(project).toList(growable: false);
      final load = _positive(loads[mesh.region] ?? 0) / maximum;
      final base = load == 0 ? mesh.color : Color.lerp(_teal, _orange, load)!;
      for (final indices in mesh.faces) {
        final a = vertices[indices[0]],
            b = vertices[indices[1]],
            c = vertices[indices[2]];
        final normal = (b - a).cross(c - a).unit;
        final centroid = (a + b + c) * (1 / 3);
        if (normal.dot(const _V3(0, 0, 8.5) - centroid) <= 0) continue;
        var brightness = .66 + .34 * math.max(0.0, normal.dot(light));
        if (mesh.region != null && mesh.region == selected) brightness += .12;
        final color = Color.fromARGB(
          255,
          (base.r * 255 * brightness).round().clamp(0, 255),
          (base.g * 255 * brightness).round().clamp(0, 255),
          (base.b * 255 * brightness).round().clamp(0, 255),
        );
        faces.add(
          _Face(
            projected[indices[0]],
            projected[indices[1]],
            projected[indices[2]],
            centroid.z,
            color,
            mesh.region,
          ),
        );
      }
    }
    faces.sort((a, b) => a.depth.compareTo(b.depth));
    return _faces = faces;
  }

  String? pick(Offset point, Size size) {
    // The foremost triangle wins, including non-selectable shell geometry:
    // a muscle on the far side must not be selectable through the body.
    for (final face in _scene(size).reversed) {
      if (face.contains(point)) return face.region;
    }
    return null;
  }

  @override
  void paint(Canvas canvas, Size size) {
    final shadow =
        Paint()
          ..color = const Color(0x18273E31)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 11);
    canvas.drawOval(
      Rect.fromCenter(
        center: Offset(size.width / 2, size.height * .965),
        width: math.min(size.width * .37, 145),
        height: 12,
      ),
      shadow,
    );
    final faces = _scene(size);
    if (_vertices == null) {
      // One GPU triangle batch replaces thousands of per-triangle paths and
      // strokes. The depth-sorted triangle list is reused for accurate picking.
      final positions = <Offset>[];
      final colors = <Color>[];
      for (final face in faces) {
        positions.addAll([face.a, face.b, face.c]);
        colors.addAll([face.color, face.color, face.color]);
      }
      _vertices = ui.Vertices(
        ui.VertexMode.triangles,
        positions,
        colors: colors,
      );
    }
    canvas.drawVertices(_vertices!, BlendMode.src, Paint());
  }

  @override
  bool shouldRepaint(covariant _BodyPainter oldDelegate) =>
      yaw != oldDelegate.yaw ||
      pitch != oldDelegate.pitch ||
      selected != oldDelegate.selected ||
      !mapEquals(loads, oldDelegate.loads);
}

/// Relative training distribution. Keys may be Chinese labels or muscle IDs.
class TrainingRadar extends StatelessWidget {
  const TrainingRadar({super.key, this.values = const {}, this.height = 240});
  final Map<String, double> values;
  final double height;

  @override
  Widget build(BuildContext context) {
    final entries =
        values.isEmpty
            ? const {
              'Chest': 0.0,
              'Back': 0.0,
              'Shoulders': 0.0,
              'Arms': 0.0,
              'Core': 0.0,
              'Lower body': 0.0,
            }.entries.toList()
            : values.entries.toList();
    return _ChartPanel(
      height: height,
      title: 'Training distribution',
      note: 'Relative load',
      child: Semantics(
        label: entries
            .map((e) => '${muscleNames[e.key] ?? e.key} ${_positive(e.value)}')
            .join(', '),
        child: RepaintBoundary(
          child: CustomPaint(
            painter: _RadarPainter(entries),
            size: Size.infinite,
          ),
        ),
      ),
    );
  }
}

/// Each bar is a supplied energy value in kcal; horizontal scrolling keeps a
/// long history readable instead of silently dropping records.
class CalorieBars extends StatelessWidget {
  const CalorieBars({super.key, this.values = const {}, this.height = 220});
  final Map<String, double> values;
  final double height;

  @override
  Widget build(BuildContext context) => _ChartPanel(
    height: height,
    title: 'Energy history',
    note: 'kcal',
    child:
        values.isEmpty
            ? const Center(
              child: Text(
                'Complete training to view energy',
                style: TextStyle(color: _muted, fontSize: 12),
              ),
            )
            : LayoutBuilder(
              builder: (context, constraints) {
                final width = math.max(
                  constraints.maxWidth,
                  values.length * 58.0 + 40,
                );
                return SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Semantics(
                    label: values.entries
                        .map((e) => '${e.key} ${_positive(e.value)} kcal')
                        .join(', '),
                    child: RepaintBoundary(
                      child: CustomPaint(
                        size: Size(width, constraints.maxHeight),
                        painter: _BarsPainter(values.entries.toList()),
                      ),
                    ),
                  ),
                );
              },
            ),
  );
}

class _ChartPanel extends StatelessWidget {
  const _ChartPanel({
    required this.height,
    required this.title,
    required this.note,
    required this.child,
  });
  final double height;
  final String title, note;
  final Widget child;
  @override
  Widget build(BuildContext context) => Container(
    height: height,
    padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
    decoration: BoxDecoration(
      color: _paper,
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: const Color(0xFFE5E8DF)),
    ),
    child: Column(
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  color: _ink,
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                ),
              ),
            ),
            Text(note, style: const TextStyle(color: _muted, fontSize: 11)),
          ],
        ),
        const SizedBox(height: 6),
        Expanded(child: child),
      ],
    ),
  );
}

void _text(
  Canvas canvas,
  String value,
  Offset center, {
  double fontSize = 11,
  Color color = _muted,
  double maxWidth = 90,
  FontWeight weight = FontWeight.normal,
}) {
  final painter = TextPainter(
    text: TextSpan(
      text: value,
      style: TextStyle(
        color: color,
        fontSize: fontSize,
        fontFamily: 'BaselineSans',
        fontWeight: weight,
        height: 1.25,
      ),
    ),
    textAlign: TextAlign.center,
    textDirection: TextDirection.ltr,
    maxLines: 2,
    ellipsis: '...',
  )..layout(maxWidth: maxWidth);
  painter.paint(canvas, center - Offset(painter.width / 2, painter.height / 2));
}

class _RadarPainter extends CustomPainter {
  const _RadarPainter(this.entries);
  final List<MapEntry<String, double>> entries;
  @override
  void paint(Canvas canvas, Size size) {
    final n = math.max(3, entries.length);
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.max(4.0, math.min(size.width * .31, size.height * .34));
    Offset point(int i, double fraction) {
      final angle = -math.pi / 2 + 2 * math.pi * i / n;
      return center +
          Offset(math.cos(angle), math.sin(angle)) * radius * fraction;
    }

    Path polygon(double fraction) {
      final path = Path()..moveTo(point(0, fraction).dx, point(0, fraction).dy);
      for (var i = 1; i < n; i++) {
        path.lineTo(point(i, fraction).dx, point(i, fraction).dy);
      }
      return path..close();
    }

    final grid =
        Paint()
          ..color = const Color(0xFFD9E1D7)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1;
    for (var ring = 1; ring <= 4; ring++) {
      canvas.drawPath(polygon(ring / 4), grid);
    }
    for (var i = 0; i < n; i++) {
      canvas.drawLine(center, point(i, 1), grid);
    }
    final maximum = math.max(1.0, _maximum(entries.map((e) => e.value)));
    final data = Path();
    for (var i = 0; i < n; i++) {
      final p = point(
        i,
        i < entries.length ? _positive(entries[i].value) / maximum : 0,
      );
      if (i == 0) {
        data.moveTo(p.dx, p.dy);
      } else {
        data.lineTo(p.dx, p.dy);
      }
    }
    data.close();
    canvas.drawPath(data, Paint()..color = _teal.withValues(alpha: .15));
    canvas.drawPath(
      data,
      Paint()
        ..color = _teal
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2,
    );
    for (var i = 0; i < entries.length; i++) {
      final p = point(i, _positive(entries[i].value) / maximum);
      canvas.drawCircle(p, 3, Paint()..color = _teal);
      final label = point(i, 1.34);
      _text(
        canvas,
        muscleNames[entries[i].key] ?? entries[i].key,
        Offset(
          label.dx.clamp(35, math.max(35.0, size.width - 35)),
          label.dy.clamp(9, math.max(9.0, size.height - 9)),
        ),
        fontSize: entries.length > 8 ? 9 : 11,
        maxWidth: 72,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _RadarPainter oldDelegate) =>
      !_entriesEqual(entries, oldDelegate.entries);
}

class _BarsPainter extends CustomPainter {
  const _BarsPainter(this.entries);
  final List<MapEntry<String, double>> entries;
  @override
  void paint(Canvas canvas, Size size) {
    final bottom = size.height - 30;
    final top = 21.0;
    final chartHeight = math.max(1.0, bottom - top);
    final maximum = math.max(1.0, _maximum(entries.map((e) => e.value)));
    final cell = (size.width - 28) / entries.length;
    final grid =
        Paint()
          ..color = const Color(0xFFDFE5DB)
          ..strokeWidth = 1;
    for (var i = 0; i < 4; i++) {
      final y = bottom - chartHeight * i / 3;
      canvas.drawLine(Offset(10, y), Offset(size.width - 10, y), grid);
    }
    for (var i = 0; i < entries.length; i++) {
      final value = _positive(entries[i].value);
      final x = 14 + cell * (i + .5);
      final barHeight = value / maximum * chartHeight;
      final rect = Rect.fromLTWH(
        x - math.min(15.0, cell * .26),
        bottom - barHeight,
        math.min(30.0, cell * .52),
        barHeight,
      );
      if (barHeight > 0) {
        canvas.drawRRect(
          RRect.fromRectAndCorners(
            rect,
            topLeft: const Radius.circular(6),
            topRight: const Radius.circular(6),
          ),
          Paint()..color = i == entries.length - 1 ? _orange : _teal,
        );
      }
      _text(
        canvas,
        value.round().toString(),
        Offset(x, bottom - barHeight - 11),
        color: _ink,
        fontSize: 10,
        weight: FontWeight.w500,
      );
      _text(
        canvas,
        muscleNames[entries[i].key] ?? entries[i].key,
        Offset(x, bottom + 17),
        fontSize: 10,
        maxWidth: cell - 5,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _BarsPainter oldDelegate) =>
      !_entriesEqual(entries, oldDelegate.entries);
}

bool _entriesEqual(
  List<MapEntry<String, double>> a,
  List<MapEntry<String, double>> b,
) {
  if (a.length != b.length) return false;
  for (var i = 0; i < a.length; i++) {
    if (a[i].key != b[i].key ||
        _positive(a[i].value) != _positive(b[i].value)) {
      return false;
    }
  }
  return true;
}
