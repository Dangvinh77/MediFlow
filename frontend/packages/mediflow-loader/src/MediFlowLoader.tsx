import type { CSSProperties } from "react";
import { CROSS_COLOR, CROSS_PATH, HALO_COLOR, TRACE_COLOR, TRACE_PATH, TRACE_WIDTH } from "./geometry";

export interface MediFlowLoaderProps {
  // Square size in CSS pixels. Defaults to 64.
  size?: number;
  // Cross colour. Defaults to the light/dark --mark-cross token.
  color?: string;
  // Trace colour. Defaults to the light/dark --mark-trace token.
  trailColor?: string;
  // Animation speed multiplier. 1 is one heartbeat every 1.5 seconds.
  speed?: number;
  // Shows the halo that spreads out from the cross on each beat. Defaults to true.
  glow?: boolean;
  // Visible status text, also used as the accessible label. Defaults to an unseen "Loading".
  label?: string;
  // Classes for the outer wrapper, so the host can lay it out with Tailwind.
  className?: string;
  /** @deprecated Ignored. Belonged to the retired triangle loader. */
  slideDuration?: number;
  /** @deprecated Ignored. Belonged to the retired triangle loader. */
  stagger?: number;
  /** @deprecated Ignored. Belonged to the retired triangle loader. */
  holdDuration?: number;
}

const BEAT_SECONDS = 1.5;
// Room around the 0..100 mark so the beat and the halo are not clipped.
const VIEW_BOX = "-14 -14 128 128";
const CENTERED = "origin-center [transform-box:fill-box]";

export function MediFlowLoader({
  size = 64,
  color = CROSS_COLOR,
  trailColor = TRACE_COLOR,
  speed = 1,
  glow = true,
  label,
  className,
}: MediFlowLoaderProps) {
  const safeSize = Number.isFinite(size) && size > 0 ? size : 64;
  const safeSpeed = Number.isFinite(speed) && speed > 0 ? speed : 1;
  const beat = { "--mediflow-beat-duration": `${BEAT_SECONDS / safeSpeed}s` } as CSSProperties;

  return (
    <div
      aria-busy="true"
      aria-label={label ?? "Loading"}
      aria-live="polite"
      className={className}
      data-mediflow-loader=""
      role="status"
    >
      <svg
        aria-hidden="true"
        focusable="false"
        height={safeSize}
        overflow="visible"
        style={beat}
        viewBox={VIEW_BOX}
        width={safeSize}
      >
        {glow ? (
          <path
            className={`${CENTERED} animate-mediflow-halo opacity-0 motion-reduce:hidden`}
            d={CROSS_PATH}
            fill={HALO_COLOR}
          />
        ) : null}
        <g className={`${CENTERED} animate-mediflow-beat motion-reduce:animate-mediflow-beat-soft`}>
          <path d={CROSS_PATH} fill={color} />
          <path
            className="opacity-35"
            d={TRACE_PATH}
            fill="none"
            stroke={trailColor}
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={TRACE_WIDTH}
          />
          <path
            className="animate-mediflow-trace"
            d={TRACE_PATH}
            fill="none"
            pathLength={100}
            stroke={trailColor}
            strokeDasharray="30 130"
            strokeDashoffset="30"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={TRACE_WIDTH}
          />
        </g>
      </svg>

      {label ? <div className="mt-2 text-center">{label}</div> : null}
    </div>
  );
}
