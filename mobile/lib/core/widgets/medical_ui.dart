import 'package:flutter/material.dart';

import '../theme/medical_colors.dart';

/// The MediFlow mark: a clinical cross with a monitor trace through it.
class MediFlowMark extends StatelessWidget {
  const MediFlowMark({super.key, this.size = 40, this.cross = Colors.white});
  final double size;
  final Color cross;

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: CustomPaint(size: Size.square(size), painter: _MarkPainter(cross)),
  );
}

class _MarkPainter extends CustomPainter {
  const _MarkPainter(this.cross);
  final Color cross;

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width;
    final arm = s * 0.34;
    final radius = Radius.circular(s * 0.09);
    final fill = Paint()..color = cross;
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromCenter(center: Offset(s / 2, s / 2), width: arm, height: s),
        radius,
      ),
      fill,
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromCenter(center: Offset(s / 2, s / 2), width: s, height: arm),
        radius,
      ),
      fill,
    );
    final trace = Path()
      ..moveTo(s * 0.1, s * 0.5)
      ..lineTo(s * 0.36, s * 0.5)
      ..lineTo(s * 0.44, s * 0.38)
      ..lineTo(s * 0.54, s * 0.62)
      ..lineTo(s * 0.62, s * 0.5)
      ..lineTo(s * 0.9, s * 0.5);
    canvas.drawPath(
      trace,
      Paint()
        ..color = MediFlowBrand.scrub
        ..style = PaintingStyle.stroke
        ..strokeWidth = s * 0.07
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_MarkPainter oldDelegate) => oldDelegate.cross != cross;
}

/// A monitor trace that draws itself once when it appears, then holds. This
/// is the app's single piece of unprompted motion, and it is skipped when the
/// device asks for reduced motion.
class PulseLine extends StatefulWidget {
  const PulseLine({
    super.key,
    this.height = 56,
    this.color = MediFlowBrand.pulse,
  });
  final double height;
  final Color color;

  @override
  State<PulseLine> createState() => _PulseLineState();
}

class _PulseLineState extends State<PulseLine>
    with SingleTickerProviderStateMixin {
  late final AnimationController _sweep = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1500),
  );
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (MediaQuery.disableAnimationsOf(context)) {
      _sweep.value = 1;
    } else {
      _sweep.forward();
    }
  }

  @override
  void dispose() {
    _sweep.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
    child: RepaintBoundary(
      child: SizedBox(
        height: widget.height,
        width: double.infinity,
        child: AnimatedBuilder(
          animation: _sweep,
          builder: (context, _) => CustomPaint(
            painter: _PulsePainter(
              Curves.easeInOutCubic.transform(_sweep.value),
              widget.color,
            ),
          ),
        ),
      ),
    ),
  );
}

class _PulsePainter extends CustomPainter {
  const _PulsePainter(this.progress, this.color);
  final double progress;
  final Color color;

  // One heartbeat as (x, y): P wave, QRS spike, T wave. y is up-negative.
  static const _beat = <(double, double)>[
    (0, 0),
    (0.16, 0),
    (0.21, -0.12),
    (0.25, -0.17),
    (0.29, -0.12),
    (0.34, 0),
    (0.4, 0),
    (0.43, 0.14),
    (0.47, -1),
    (0.51, 0.42),
    (0.54, 0),
    (0.62, 0),
    (0.68, -0.2),
    (0.74, -0.28),
    (0.8, -0.18),
    (0.86, 0),
    (1, 0),
  ];

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    const beatWidth = 168.0;
    final beats = (size.width / beatWidth).ceil() + 1;
    final baseline = size.height * 0.62;
    final amplitude = size.height * 0.56;
    final path = Path()..moveTo(0, baseline);
    for (var beat = 0; beat < beats; beat++) {
      for (final (x, y) in _beat) {
        path.lineTo((beat + x) * beatWidth, baseline + y * amplitude);
      }
    }
    final metric = path.computeMetrics().first;
    final visibleLength = size.width / (beats * beatWidth) * metric.length;
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..strokeJoin = StrokeJoin.round
      ..strokeCap = StrokeCap.round;

    canvas.save();
    canvas.clipRect(Offset.zero & size);
    canvas.drawPath(
      metric.extractPath(0, visibleLength),
      stroke..color = color.withValues(alpha: 0.16),
    );
    final head = visibleLength * progress;
    if (head > 0) {
      canvas.drawPath(
        metric.extractPath(0, head),
        stroke..color = color.withValues(alpha: 0.9),
      );
      final tip = metric.getTangentForOffset(head)?.position;
      if (tip != null && progress < 1) {
        canvas.drawCircle(
          tip,
          7,
          Paint()..color = color.withValues(alpha: 0.25),
        );
        canvas.drawCircle(tip, 3, Paint()..color = Colors.white);
      }
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_PulsePainter oldDelegate) =>
      oldDelegate.progress != progress || oldDelegate.color != color;
}

/// A rounded square holding an icon in its area's wayfinding colour.
class IconBadge extends StatelessWidget {
  const IconBadge(this.icon, {super.key, required this.color, this.size = 42});
  final IconData icon;
  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) {
    final medical = context.medical;
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: medical.wash(color),
        borderRadius: BorderRadius.circular(size * 0.3),
      ),
      child: Icon(icon, color: medical.accent(color), size: size * 0.55),
    );
  }
}

enum StatusTone { neutral, info, success, warning, danger }

/// Maps a backend status code to the triage tone clinicians expect. Unknown
/// codes stay neutral rather than implying a meaning the server did not send.
StatusTone statusToneOf(String status) {
  final code = status.toUpperCase();
  bool any(List<String> words) => words.any(code.contains);
  if (any(['CANCEL', 'REJECT', 'FAIL', 'NO_SHOW', 'EXPIRED', 'VOID'])) {
    return StatusTone.danger;
  }
  // Before success: UNPAID contains PAID.
  if (any(['PENDING', 'WAIT', 'DRAFT', 'REQUESTED', 'QUOTE', 'UNPAID'])) {
    return StatusTone.warning;
  }
  if (any(['COMPLETE', 'DONE', 'PAID', 'DISPENSED', 'CONFIRMED', 'FINAL'])) {
    return StatusTone.success;
  }
  if (any(['PROGRESS', 'READY', 'CHECKED', 'PROCESS', 'IN_EXAM', 'OPEN'])) {
    return StatusTone.info;
  }
  return StatusTone.neutral;
}

/// A compact status marker. The dot keeps the status legible for readers who
/// cannot rely on the hue alone, since the label always travels with it.
class StatusPill extends StatelessWidget {
  const StatusPill(this.label, {super.key, this.tone = StatusTone.neutral});
  final String label;
  final StatusTone tone;

  @override
  Widget build(BuildContext context) {
    final medical = context.medical;
    final color = switch (tone) {
      StatusTone.neutral => medical.mutedText,
      StatusTone.info => medical.info,
      StatusTone.success => medical.success,
      StatusTone.warning => medical.warning,
      StatusTone.danger => medical.danger,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: ShapeDecoration(
        color: color.withValues(alpha: medical.isDark ? 0.18 : 0.1),
        shape: const StadiumBorder(),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              label,
              style: Theme.of(context).textTheme.labelMedium
                  ?.copyWith(color: color, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

/// A warning that needs reading before the next action, such as a command
/// whose outcome is unknown. It reads as a notice, not as ordinary body text.
class NoticeBox extends StatelessWidget {
  const NoticeBox(this.message, {super.key});
  final String message;

  @override
  Widget build(BuildContext context) {
    final color = context.medical.warning;
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: 10,
        children: [
          Icon(Icons.warning_amber_rounded, color: color, size: 20),
          Expanded(
            child: Text(message, style: TextStyle(color: color)),
          ),
        ],
      ),
    );
  }
}

/// A centred icon and message for empty, error and placeholder states.
class StateMessage extends StatelessWidget {
  const StateMessage({
    super.key,
    required this.icon,
    required this.color,
    required this.child,
  });
  final IconData icon;
  final Color color;
  final Widget child;

  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      IconBadge(icon, color: color, size: 64),
      const SizedBox(height: 16),
      child,
    ],
  );
}
