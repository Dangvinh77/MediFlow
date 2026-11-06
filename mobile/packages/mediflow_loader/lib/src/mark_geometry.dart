import 'package:flutter/painting.dart';

/// Brand colours of the mark. The light theme swaps them: scrub cross, white
/// trace.
const markScrub = Color(0xFF0F766E);
const markWhite = Color(0xFFFFFFFF);

/// Width of the trace on the 0..100 canvas.
const traceWidth = 7.0;

/// The clinical cross on a 0..100 canvas.
Path crossPath() {
  const radius = Radius.circular(9);
  return Path()
    ..addRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(33, 0, 34, 100), radius),
    )
    ..addRRect(
      RRect.fromRectAndRadius(const Rect.fromLTWH(0, 33, 100, 34), radius),
    );
}

/// The monitor trace through the cross on a 0..100 canvas.
Path tracePath() => Path()
  ..moveTo(10, 50)
  ..lineTo(36, 50)
  ..lineTo(44, 38)
  ..lineTo(54, 62)
  ..lineTo(62, 50)
  ..lineTo(90, 50);

/// Paint for the trace, in any colour.
Paint tracePaint(Color color) => Paint()
  ..color = color
  ..style = PaintingStyle.stroke
  ..strokeWidth = traceWidth
  ..strokeCap = StrokeCap.round
  ..strokeJoin = StrokeJoin.round;
