import { useId } from "react";

export interface MediFlowLoaderProps {
  /** Square loader size in CSS pixels. */
  size?: number;
  /** Main frame and monogram colour. Defaults to the MediFlow primary token. */
  color?: string;
  /** Comet/tracer colour. */
  trailColor?: string;
  /** Animation multiplier. 1 = 1.45 s per loop. */
  speed?: number;
  /** Draws a wider translucent frame behind the crisp outline. */
  glow?: boolean;
  /** Optional visible loading label. It is also used as the accessible status label. */
  label?: string;
  /** Class names are applied to the outer wrapper so the host app can style with Tailwind. */
  className?: string;
}

const TRIANGLE_PATH = "M 15 18 L 85 18 L 50 78.62 Z";
const INNER_TRIANGLE_PATH = "M 19 22 L 81 22 L 50 75.69 Z";
const BASE_DURATION_SECONDS = 1.45;

/**
 * MediFlow's dependency-free animated loader for React/Next.js.
 *
 * The outer tracer follows an equilateral triangle while the inner MF mark
 * collapses toward the upper-left vertex, then resets for the next loop.
 * Animation is declarative SVG (SMIL), so there is no timer, canvas, or
 * third-party animation runtime in the web bundle.
 */
export function MediFlowLoader({
  size = 64,
  color = "#0F766E",
  trailColor = "#2DD4BF",
  speed = 1,
  glow = true,
  label,
  className,
}: MediFlowLoaderProps) {
  const safeSize = Number.isFinite(size) && size > 0 ? size : 64;
  const safeSpeed = Number.isFinite(speed) && speed > 0 ? speed : 1;
  const duration = `${BASE_DURATION_SECONDS / safeSpeed}s`;
  const statusLabel = label ?? "Loading";
  const monogramClipId = `${useId()}-monogram-clip`;

  return (
    <div
      aria-label={statusLabel}
      aria-live="polite"
      className={className}
      data-mediflow-loader=""
      role="status"
    >
      <svg
        aria-hidden="true"
        focusable="false"
        height={safeSize}
        viewBox="0 0 100 100"
        width={safeSize}
      >
        <defs>
          <clipPath id={monogramClipId}>
            <path d={INNER_TRIANGLE_PATH} />
          </clipPath>
        </defs>

        {glow ? (
          <path
            d={TRIANGLE_PATH}
            fill="none"
            opacity="0.16"
            stroke={color}
            strokeLinejoin="round"
            strokeWidth="7"
          />
        ) : null}

        <path
          d={TRIANGLE_PATH}
          fill="none"
          opacity="0.42"
          stroke={color}
          strokeLinejoin="round"
          strokeWidth="1.35"
        />

        {/* A wide, faint comet tail. Dash offset is synced with the head below. */}
        {glow ? (
          <path
            d={TRIANGLE_PATH}
            fill="none"
            opacity="0.22"
            pathLength={100}
            stroke={trailColor}
            strokeDasharray="18 82"
            strokeDashoffset="18"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="7"
          >
            <animate
              attributeName="stroke-dashoffset"
              dur={duration}
              from="18"
              repeatCount="indefinite"
              to="-82"
            />
          </path>
        ) : null}

        {/* Crisp moving tail. */}
        <path
          d={TRIANGLE_PATH}
          fill="none"
          pathLength={100}
          stroke={trailColor}
          strokeDasharray="12 88"
          strokeDashoffset="12"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2.4"
        >
          <animate
            attributeName="stroke-dashoffset"
            dur={duration}
            from="12"
            repeatCount="indefinite"
            to="-88"
          />
        </path>

        {/* Bright shooting-star head. */}
        {glow ? (
          <circle fill={trailColor} opacity="0.24" r="6">
            <animateMotion
              dur={duration}
              path={TRIANGLE_PATH}
              repeatCount="indefinite"
            />
          </circle>
        ) : null}
        <circle fill={trailColor} r="3.2">
          <animateMotion
            dur={duration}
            path={TRIANGLE_PATH}
            repeatCount="indefinite"
          />
        </circle>
        <circle fill="#F0FDFA" r="1.4">
          <animateMotion
            dur={duration}
            path={TRIANGLE_PATH}
            repeatCount="indefinite"
          />
        </circle>

        {/*
          The local origin is the upper-left triangle vertex (15, 18), so a
          scale transform naturally collapses the whole inner mark into it.
          The near-instant scale jump happens while opacity is zero, making the
          loop read as shrink -> disappear -> restart rather than reverse.
        */}
        <g clipPath={`url(#${monogramClipId})`}>
          <g transform="translate(15 18)">
            <g>
              <animateTransform
                attributeName="transform"
                dur={duration}
                keyTimes="0;0.68;0.76;0.761;1"
                repeatCount="indefinite"
                type="scale"
                values="1;0.06;0.06;1;1"
              />
              <animate
                attributeName="opacity"
                dur={duration}
                keyTimes="0;0.55;0.72;0.761;1"
                repeatCount="indefinite"
                values="1;1;0;0;1"
              />

              {/* Original angular MF monogram; coordinates are relative to (15, 18). */}
              <path
                d="M 9 12 H 16 L 23 24 L 30 12 H 37 V 40 H 30 V 25 L 23 37 L 16 25 V 40 H 9 Z"
                fill={color}
              />
              <path
                d="M 41 12 H 61 L 58 19 H 48 V 24 H 57 L 54 31 H 48 V 40 H 41 Z"
                fill={color}
              />
            </g>
          </g>
        </g>
      </svg>

      {label ? <div data-mediflow-loader-label="">{label}</div> : null}
    </div>
  );
}
