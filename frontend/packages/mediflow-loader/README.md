# @mediflow/loader

Dependency-free React/Next.js loader used by MediFlow. A comet-like tracer loops around a downward equilateral triangle while two staggered sets assemble and release the clipped `MF` monogram without leaving the frame empty.

## Use inside this repository

`frontend/tsconfig.json` maps `@mediflow/loader` directly to this package source, so no new runtime dependency or lockfile change is required.

```tsx
import { MediFlowLoader } from "@mediflow/loader";

export default function Loading() {
  return (
    <div className="flex min-h-40 items-center justify-center">
      <MediFlowLoader size={72} />
    </div>
  );
}
```

With a visible label:

```tsx
<MediFlowLoader
  size={64}
  speed={1.1}
  color="#0F766E"
  trailColor="#2DD4BF"
  label="Loading patient data..."
  className="flex flex-col items-center gap-2 text-sm text-slate-600"
/>
```

## Props

| Prop | Default | Meaning |
|---|---|---|
| `size` | `64` | Square SVG size in CSS pixels |
| `color` | `#0F766E` | Triangle + MF colour |
| `trailColor` | `#2DD4BF` | Moving comet colour |
| `speed` | `1` | Animation multiplier |
| `glow` | `true` | Enables the wider translucent glow |
| `label` | — | Optional visible and accessible status text |
| `className` | — | Tailwind classes for the outer wrapper |
| `intro` | `true` | Enables the staggered MF assembly loop |
| `slideDuration` | `1.4` | Seconds for each piece to enter |
| `stagger` | `0.5` | Delay in seconds between pieces |
| `holdDuration` | `0.5` | Seconds the assembled mark remains in place |
| `exitDuration` | `1` | Seconds for each piece to leave |

The base loop is 1.45 seconds. The implementation uses declarative SVG animation, so there is no timer, canvas runtime, Rive/Lottie dependency, or client-side React state.

## Extracting/publishing later

The folder is already a valid private package with its own `package.json`. If MediFlow later moves shared UI to a separate repository/registry, publish this folder as `@mediflow/loader` and replace the TypeScript path alias with the normal package dependency.
