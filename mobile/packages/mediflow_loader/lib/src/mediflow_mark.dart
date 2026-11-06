import 'package:flutter/material.dart';

import 'mark_geometry.dart';

/// The MediFlow mark: a clinical cross with a monitor trace through it.
class MediFlowMark extends StatelessWidget {
  const MediFlowMark({
    super.key,
    this.size = 40,
    this.cross = markWhite,
    this.trace = markScrub,
  });

  final double size;
  final Color cross;
  final Color trace;

  @override
  Widget build(BuildContext context) => ExcludeSemantics(
        child: CustomPaint(
          size: Size.square(size),
          painter: _MarkPainter(cross, trace),
        ),
      );
}

class _MarkPainter extends CustomPainter {
  const _MarkPainter(this.cross, this.trace);

  final Color cross;
  final Color trace;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.scale(size.width / 100);
    canvas.drawPath(crossPath(), Paint()..color = cross);
    canvas.drawPath(tracePath(), tracePaint(trace));
  }

  @override
  bool shouldRepaint(_MarkPainter oldDelegate) =>
      oldDelegate.cross != cross || oldDelegate.trace != trace;
}
