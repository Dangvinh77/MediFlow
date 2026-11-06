const BEAT_WIDTH = 168;
const BEATS = 12;
const HEIGHT = 72;
const BASELINE = HEIGHT * 0.62;
const AMPLITUDE = HEIGHT * 0.56;

// One heartbeat as [x, y]: P wave, QRS spike, T wave. y is up-negative. Matches PulseLine on mobile.
const BEAT: ReadonlyArray<readonly [number, number]> = [
  [0, 0], [0.16, 0], [0.21, -0.12], [0.25, -0.17], [0.29, -0.12], [0.34, 0], [0.4, 0],
  [0.43, 0.14], [0.47, -1], [0.51, 0.42], [0.54, 0], [0.62, 0], [0.68, -0.2], [0.74, -0.28],
  [0.8, -0.18], [0.86, 0], [1, 0],
];

const TRACE = Array.from({ length: BEATS }, (_, beat) =>
  BEAT.map(([x, y]) => `L${((beat + x) * BEAT_WIDTH).toFixed(1)} ${(BASELINE + y * AMPLITUDE).toFixed(1)}`).join(""),
).join("");
const PATH = `M0 ${BASELINE.toFixed(1)}${TRACE}`;

function Trace({ className }: { className: string }) {
  return (
    <svg className={`absolute inset-0 size-full ${className}`}>
      <path d={PATH} fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} />
    </svg>
  );
}

/** A monitor trace that draws itself once on arrival, then holds. Skipped for reduced motion. */
export function PulseLine({ className = "" }: { className?: string }) {
  return (
    <div aria-hidden="true" className={`relative h-[72px] w-full overflow-hidden ${className}`}>
      <Trace className="text-brand-pulse/16" />
      <Trace className="animate-mediflow-reveal text-brand-pulse/90 motion-reduce:animate-none" />
    </div>
  );
}
