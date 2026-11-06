# @mediflow/loader

The MediFlow mark and loading animation for the web client: the clinical cross with a monitor trace from `MediFlowMark` in `mobile/lib/core/widgets/medical_ui.dart`. In the loader the cross beats like a heart (lub-dub) while a highlight runs along the trace and reaches the QRS spike on the beat.

Both components follow the light/dark theme through the `--mark-cross` and `--mark-trace` tokens. The motion lives in `src/app/globals.css` as Tailwind `animate-mediflow-*` utilities, so this package has no runtime dependency, no timer and no client-side state. Under `prefers-reduced-motion` (including Windows with animation effects off) the cross beats by fading instead of scaling, the halo goes away, and the trace highlight keeps running, so the loader never looks frozen.

## Use inside this repository

`frontend/tsconfig.json` maps `@mediflow/loader` directly to this package source.

```tsx
import { MediFlowLoader, MediFlowMark } from "@mediflow/loader";

<MediFlowLoader size={72} label="Đang tải dữ liệu..." className="flex flex-col items-center gap-2 text-sm" />

<MediFlowMark size={40} />                  // follows the theme
<MediFlowMark size={38} tone="on-brand" />  // white cross for bg-brand-deep
```

## `MediFlowLoader` props

| Prop | Default | Meaning |
|---|---|---|
| `size` | `64` | Square SVG size in CSS pixels |
| `color` | `--mark-cross` | Cross colour |
| `trailColor` | `--mark-trace` | Trace colour |
| `speed` | `1` | Speed multiplier; `1` is one heartbeat every 1.5 seconds |
| `glow` | `true` | Shows the halo that spreads from the cross on each beat |
| `label` | — | Visible and accessible status text |
| `className` | — | Tailwind classes for the outer wrapper |

`slideDuration`, `stagger` and `holdDuration` are deprecated and ignored. They belonged to the retired triangle loader.

## `MediFlowMark` props

| Prop | Default | Meaning |
|---|---|---|
| `size` | `40` | Square SVG size in CSS pixels |
| `tone` | `"theme"` | `"theme"` follows light/dark; `"on-brand"` is the white cross for deep teal surfaces |
| `className` | — | Classes for the SVG |

The mark is decorative (`aria-hidden`), so keep the "MediFlow" name in adjacent text.

## Logo files

Static exports live in `frontend/public/brand/`. See the README there.
