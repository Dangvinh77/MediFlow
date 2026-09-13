import 'dart:math' as math;

import 'package:flutter/material.dart';

/// MediFlow's reusable triangular loading animation.
///
/// A bright tracer runs clockwise around an equilateral triangle while the
/// inner MF mark collapses toward the upper-left vertex and restarts.
class MediFlowLoader extends StatefulWidget {
  const MediFlowLoader({
    super.key,
    this.size = 64,
    this.color = const Color(0xFF0F766E),
    this.trailColor = const Color(0xFF2DD4BF),
    this.speed = 1,
    this.glow = true,
    this.label,
  });

  final double size;
  final Color color;
  final Color trailColor;
  final double speed;
  final bool glow;
  final String? label;

  @override
  State<MediFlowLoader> createState() => _MediFlowLoaderState();
}

class _MediFlowLoaderState extends State<MediFlowLoader>
    with SingleTickerProviderStateMixin {
  static const _baseDuration = Duration(milliseconds: 1450);
  late final AnimationController _controller;

  Duration get _duration {
    final safeSpeed = widget.speed.isFinite && widget.speed > 0 ? widget.speed : 1.0;
    return Duration(
      microseconds: (_baseDuration.inMicroseconds / safeSpeed).round(),
    );
  }

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: _duration)..repeat();
  }

  @override
  void didUpdateWidget(covariant MediFlowLoader oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.speed != widget.speed) {
      _controller.duration = _duration;
      _controller.repeat();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final safeSize = widget.size.isFinite && widget.size > 0 ? widget.size : 64.0;
    final graphic = SizedBox.square(
      dimension: safeSize,
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, _) => CustomPaint(
          painter: _MediFlowLoaderPainter(
            progress: _controller.value,
            color: widget.color,
            trailColor: widget.trailColor,
            glow: widget.glow,
          ),
        ),
      ),
    );

    return Semantics(
      label: widget.label ?? 'Loading',
      liveRegion: true,
      child: widget.label == null
          ? graphic
          : Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                graphic,
                const SizedBox(height: 8),
                Text(
                  widget.label!,
                  style: Theme.of(context).textTheme.bodySmall,
                  textAlign: TextAlign.center,
                ),
              ],
            ),
    );
  }
}

class _MediFlowLoaderPainter extends CustomPainter {
  const _MediFlowLoaderPainter({
    required this.progress,
    required this.color,
    required this.trailColor,
    required this.glow,
  });

  final double progress;
  final Color color;
  final Color trailColor;
  final bool glow;

  static final Path _triangle = Path()
    ..moveTo(15, 18)
    ..lineTo(85, 18)
    ..lineTo(50, 78.62)
    ..close();

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / 100, size.height / 100);

    if (glow) {
      canvas.drawPath(
        _triangle,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 7
          ..strokeJoin = StrokeJoin.round
          ..color = color.withValues(alpha: 0.16),
      );
    }

    canvas.drawPath(
      _triangle,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.35
        ..strokeJoin = StrokeJoin.round
        ..color = color.withValues(alpha: 0.42),
    );

    final metric = _triangle.computeMetrics().first;
    final headOffset = metric.length * progress;

    if (glow) {
      _drawTrail(
        canvas,
        metric,
        headOffset,
        metric.length * 0.18,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 7
          ..strokeCap = StrokeCap.round
          ..color = trailColor.withValues(alpha: 0.22),
      );
    }

    _drawTrail(
      canvas,
      metric,
      headOffset,
      metric.length * 0.12,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.4
        ..strokeCap = StrokeCap.round
        ..color = trailColor,
    );

    final tangent = metric.getTangentForOffset(headOffset);
    if (tangent != null) {
      if (glow) {
        canvas.drawCircle(
          tangent.position,
          6,
          Paint()..color = trailColor.withValues(alpha: 0.24),
        );
      }
      canvas.drawCircle(tangent.position, 3.2, Paint()..color = trailColor);
      canvas.drawCircle(
        tangent.position,
        1.4,
        Paint()..color = const Color(0xFFF0FDFA),
      );
    }

    _drawInnerMark(canvas);
    canvas.restore();
  }

  void _drawTrail(
    Canvas canvas,
    PathMetric metric,
    double headOffset,
    double trailLength,
    Paint paint,
  ) {
    final start = headOffset - trailLength;
    if (start >= 0) {
      canvas.drawPath(metric.extractPath(start, headOffset), paint);
      return;
    }

    canvas.drawPath(metric.extractPath(metric.length + start, metric.length), paint);
    canvas.drawPath(metric.extractPath(0, headOffset), paint);
  }

  void _drawInnerMark(Canvas canvas) {
    final collapse = (progress / 0.68).clamp(0.0, 1.0);
    final easedCollapse = Curves.easeInCubic.transform(collapse);
    final scale = progress <= 0.68
        ? _lerpDouble(1, 0.06, easedCollapse)
        : progress < 0.761
            ? 0.06
            : 1.0;

    final opacity = progress <= 0.55
        ? 1.0
        : progress <= 0.72
            ? 1 - ((progress - 0.55) / 0.17)
            : progress < 0.761
                ? 0.0
                : _lerpDouble(0, 1, ((progress - 0.761) / 0.239).clamp(0, 1));

    if (opacity <= 0) return;

    final markPaint = Paint()..color = color.withValues(alpha: opacity);

    canvas.save();
    canvas.translate(15, 18);
    canvas.scale(scale, scale);

    final mPath = Path()
      ..moveTo(9, 12)
      ..lineTo(16, 12)
      ..lineTo(23, 24)
      ..lineTo(30, 12)
      ..lineTo(37, 12)
      ..lineTo(37, 40)
      ..lineTo(30, 40)
      ..lineTo(30, 25)
      ..lineTo(23, 37)
      ..lineTo(16, 25)
      ..lineTo(16, 40)
      ..lineTo(9, 40)
      ..close();

    final fPath = Path()
      ..moveTo(41, 12)
      ..lineTo(61, 12)
      ..lineTo(58, 19)
      ..lineTo(48, 19)
      ..lineTo(48, 24)
      ..lineTo(57, 24)
      ..lineTo(54, 31)
      ..lineTo(48, 31)
      ..lineTo(48, 40)
      ..lineTo(41, 40)
      ..close();

    canvas.drawPath(mPath, markPaint);
    canvas.drawPath(fPath, markPaint);
    canvas.restore();
  }

  double _lerpDouble(double a, double b, double t) => a + (b - a) * t;

  @override
  bool shouldRepaint(covariant _MediFlowLoaderPainter oldDelegate) {
    return progress != oldDelegate.progress ||
        color != oldDelegate.color ||
        trailColor != oldDelegate.trailColor ||
        glow != oldDelegate.glow;
  }
}
