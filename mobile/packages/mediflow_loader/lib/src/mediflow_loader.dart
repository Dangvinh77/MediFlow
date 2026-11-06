import 'package:flutter/material.dart';

import 'mark_geometry.dart';

/// MediFlow's loading animation: the brand cross beats like a heart (lub-dub)
/// while a highlight runs along the monitor trace, reaching the QRS spike on
/// the beat.
///
/// When the device asks for reduced motion the cross beats by fading instead
/// of scaling and the halo goes away. The trace keeps running, so the loader
/// never looks frozen.
class MediFlowLoader extends StatefulWidget {
  const MediFlowLoader({
    super.key,
    this.size = 64,
    this.color,
    this.trailColor,
    this.speed = 1,
    this.glow = true,
    this.label,
    this.semanticLabel,
  });

  final double size;

  /// Cross colour. Defaults to scrub in light mode and white in dark mode.
  final Color? color;

  /// Trace colour. Defaults to white in light mode and scrub in dark mode.
  final Color? trailColor;

  /// Speed multiplier. 1 is one heartbeat every 1.5 seconds.
  final double speed;

  /// Shows the halo that spreads out from the cross on each beat.
  final bool glow;

  /// Visible text under the loader.
  final String? label;

  /// What screen readers announce. Defaults to [label].
  final String? semanticLabel;

  @override
  State<MediFlowLoader> createState() => _MediFlowLoaderState();
}

class _MediFlowLoaderState extends State<MediFlowLoader>
    with SingleTickerProviderStateMixin {
  static const _beat = Duration(milliseconds: 1500);
  late final AnimationController _controller;

  Duration get _duration {
    final speed = widget.speed;
    final safeSpeed = speed.isFinite && speed > 0 ? speed : 1.0;
    return Duration(microseconds: (_beat.inMicroseconds / safeSpeed).round());
  }

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: _duration)
      ..repeat();
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
    final size = widget.size;
    final safeSize = size.isFinite && size > 0 ? size : 64.0;
    final dark = Theme.of(context).brightness == Brightness.dark;
    final graphic = RepaintBoundary(
      child: SizedBox.square(
        dimension: safeSize,
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, _) => CustomPaint(
            painter: _LoaderPainter(
              progress: _controller.value,
              cross: widget.color ?? (dark ? markWhite : markScrub),
              trace: widget.trailColor ?? (dark ? markScrub : markWhite),
              glow: widget.glow,
              reduceMotion: MediaQuery.disableAnimationsOf(context),
            ),
          ),
        ),
      ),
    );
    final label = widget.label;

    return Semantics(
      label: widget.semanticLabel ?? label ?? 'Đang tải dữ liệu',
      liveRegion: true,
      child: ExcludeSemantics(
        child: label == null
            ? graphic
            : Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  graphic,
                  const SizedBox(height: 8),
                  Text(
                    label,
                    style: Theme.of(context).textTheme.bodySmall,
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
      ),
    );
  }
}

/// Eases from one keyframe to the next. Times run 0..1 over one heartbeat.
double _keyframes(double t, List<(double, double)> frames, Curve curve) {
  for (var i = 1; i < frames.length; i++) {
    final (end, to) = frames[i];
    if (t > end) continue;
    final (start, from) = frames[i - 1];
    if (end == start) return to;
    return from + (to - from) * curve.transform((t - start) / (end - start));
  }
  return frames.last.$2;
}

class _LoaderPainter extends CustomPainter {
  const _LoaderPainter({
    required this.progress,
    required this.cross,
    required this.trace,
    required this.glow,
    required this.reduceMotion,
  });

  final double progress;
  final Color cross;
  final Color trace;
  final bool glow;
  final bool reduceMotion;

  static const _halo = Color(0xFF2DD4BF);

  // Room around the 0..100 mark so the beat and the halo are not clipped.
  static const _margin = 14.0;
  static const _canvas = 100 + 2 * _margin;

  // Share of the trace the highlight covers, as on the web loader.
  static const _highlight = 0.3;

  static final _cross = crossPath();
  static final _trace = tracePath();

  static const _scaleFrames = <(double, double)>[
    (0, 1),
    (0.24, 1),
    (0.36, 1.12),
    (0.46, 0.98),
    (0.56, 1.07),
    (0.7, 1),
    (1, 1),
  ];

  static const _fadeFrames = <(double, double)>[
    (0, 1),
    (0.24, 1),
    (0.36, 0.55),
    (0.46, 1),
    (0.56, 0.7),
    (0.7, 1),
    (1, 1),
  ];

  static const _haloOpacityFrames = <(double, double)>[
    (0, 0),
    (0.3, 0),
    (0.36, 0.35),
    (0.72, 0),
    (1, 0),
  ];

  static const _haloScaleFrames = <(double, double)>[
    (0, 1),
    (0.3, 1),
    (0.36, 1.02),
    (0.72, 1.26),
    (1, 1.26),
  ];

  void _atCentreScaled(Canvas canvas, double scale, void Function() draw) {
    canvas.save();
    canvas.translate(50, 50);
    canvas.scale(scale);
    canvas.translate(-50, -50);
    draw();
    canvas.restore();
  }

  void _paintHighlight(Canvas canvas, Color color) {
    final metric = _trace.computeMetrics().first;
    // The head starts at the left end and leaves past the right end, so the
    // highlight drains out before the next beat begins.
    final head = progress * (1 + _highlight);
    final tail = head - _highlight;
    final start = tail.clamp(0.0, 1.0) * metric.length;
    final end = head.clamp(0.0, 1.0) * metric.length;
    if (end > start) {
      canvas.drawPath(metric.extractPath(start, end), tracePaint(color));
    }
  }

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width / _canvas);
    canvas.translate(_margin, _margin);

    if (glow && !reduceMotion) {
      final opacity = _keyframes(progress, _haloOpacityFrames, Curves.easeOut);
      if (opacity > 0) {
        _atCentreScaled(
          canvas,
          _keyframes(progress, _haloScaleFrames, Curves.easeOut),
          () => canvas.drawPath(
            _cross,
            Paint()..color = _halo.withValues(alpha: opacity),
          ),
        );
      }
    }

    final opacity = reduceMotion
        ? _keyframes(progress, _fadeFrames, Curves.easeInOut)
        : 1.0;
    final scale = reduceMotion
        ? 1.0
        : _keyframes(progress, _scaleFrames, Curves.easeInOut);

    _atCentreScaled(canvas, scale, () {
      canvas.drawPath(
          _cross, Paint()..color = cross.withValues(alpha: opacity));
      canvas.drawPath(
        _trace,
        tracePaint(trace.withValues(alpha: 0.35 * opacity)),
      );
      _paintHighlight(canvas, trace.withValues(alpha: opacity));
    });
    canvas.restore();
  }

  @override
  bool shouldRepaint(_LoaderPainter oldDelegate) =>
      oldDelegate.progress != progress ||
      oldDelegate.cross != cross ||
      oldDelegate.trace != trace ||
      oldDelegate.glow != glow ||
      oldDelegate.reduceMotion != reduceMotion;
}
