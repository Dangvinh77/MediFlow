# mediflow_loader

The MediFlow mark and loading animation for Flutter, matching the web package `@mediflow/loader`. In the loader the clinical cross beats like a heart (lub-dub) while a highlight runs along the monitor trace and reaches the QRS spike on the beat.

The package depends only on Flutter. `core/widgets/medical_ui.dart` re-exports `MediFlowMark`.

## Use

```dart
import 'package:mediflow_loader/mediflow_loader.dart';

const Center(child: MediFlowLoader())

const MediFlowLoader(size: 72, label: 'Đang tải dữ liệu...')

const MediFlowLoader(semanticLabel: 'Đang tải đơn thuốc')
```

Use it for a page or section that is waiting on data. Keep `CircularProgressIndicator` for the small spinner inside a button and `LinearProgressIndicator` for a thin progress bar above content.

## `MediFlowLoader` parameters

| Parameter | Default | Meaning |
|---|---|---|
| `size` | `64` | Square loader size |
| `color` | scrub in light, white in dark | Cross colour |
| `trailColor` | white in light, scrub in dark | Trace colour |
| `speed` | `1` | Speed multiplier; `1` is one heartbeat every 1.5 seconds |
| `glow` | `true` | Shows the halo that spreads from the cross on each beat |
| `label` | — | Visible text under the loader |
| `semanticLabel` | `label`, else `Đang tải dữ liệu` | What screen readers announce |

When the device asks for reduced motion, the cross beats by fading instead of scaling, the halo goes away, and the trace highlight keeps running, so the loader never looks frozen.

## `MediFlowMark` parameters

| Parameter | Default | Meaning |
|---|---|---|
| `size` | `40` | Square mark size |
| `cross` | white | Cross colour |
| `trace` | scrub | Trace colour |

The painter uses the same `0..100` geometry and the same 1.5-second loop as the web package, so both clients stay aligned without adding Rive or Lottie to either runtime.
