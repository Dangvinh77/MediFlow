# mediflow_loader

Reusable Flutter implementation of the MediFlow loading mark. It matches the web package's motion language: a bright tracer runs around a downward equilateral triangle while the inner `MF` mark collapses into the upper-left vertex and restarts.

The package is intentionally dependency-free beyond Flutter itself.

## Add to the MediFlow mobile app

When the root `mobile/pubspec.yaml` is created, add:

```yaml
dependencies:
  mediflow_loader:
    path: packages/mediflow_loader
```

Then:

```dart
import 'package:mediflow_loader/mediflow_loader.dart';

const MediFlowLoader(
  size: 72,
)
```

Customised:

```dart
const MediFlowLoader(
  size: 64,
  color: Color(0xFF0F766E),
  trailColor: Color(0xFF2DD4BF),
  speed: 1.1,
  glow: true,
  label: 'Loading patient data...',
)
```

## Parameters

| Parameter | Default | Meaning |
|---|---|---|
| `size` | `64` | Square loader size |
| `color` | `0xFF0F766E` | Triangle + MF colour |
| `trailColor` | `0xFF2DD4BF` | Moving comet colour |
| `speed` | `1` | Animation multiplier |
| `glow` | `true` | Enables the translucent outer glow |
| `label` | — | Optional visible and semantic loading text |

The painter uses the same normalized `0..100` geometry and the same 1.45-second base loop as the web implementation, so both clients can stay visually aligned without adding Rive/Lottie to either runtime.
