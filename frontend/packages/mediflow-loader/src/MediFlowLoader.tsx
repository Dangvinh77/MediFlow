import { useId } from "react";

export interface MediFlowLoaderProps {
  // Kích thước loader tính bằng CSS pixel (hình vuông). Mặc định 64.
  size?: number;
  // Màu viền tam giác và chữ MF (khi flow = false). Mặc định #0F766E.
  color?: string;
  // Màu của vệt sáng (tracer + gradient monogram). Mặc định #2DD4BF.
  trailColor?: string;
  // Hệ số tốc độ animation. 1 = tốc độ chuẩn. Mặc định 1.
  speed?: number;
  // Bật/tắt viền sáng dày bao quanh tam giác + tracer chạy quanh. Mặc định true.
  glow?: boolean;
  // Nhãn hiển thị bên dưới loader. Cũng dùng làm aria-label. Mặc định "Loading".
  label?: string;
  // Class names áp cho wrapper ngoài, để host app style bằng Tailwind.
  className?: string;
  // Bật/tắt animation intro/exit cho 3 miếng logo. Mặc định true.
  intro?: boolean;
  // Thời gian trượt của mỗi miếng logo trong pha intro (giây). Mặc định 1.4.
  slideDuration?: number;
  // Khoảng lệch pha giữa các miếng logo khi intro/exit (giây). Mặc định 0.5.
  stagger?: number;
  // Thời gian giữ logo hoàn chỉnh trước khi exit (giây). Mặc định 0.5.
  holdDuration?: number;
  // Thời gian exit của mỗi miếng logo (giây). Mặc định 1.0.
  exitDuration?: number;
  // Bật/tắt hiệu ứng gradient lóa sáng chạy qua monogram. Mặc định true.
  flow?: boolean;
}

const TRIANGLE_PATH = "M 15 18 L 85 18 L 50 78.62 Z";
const INNER_TRIANGLE_PATH = "M 19 22 L 81 22 L 50 75.69 Z";

const INTRO_FROM_SCALE = 5;
const INTRO_FROM_X = INTRO_FROM_SCALE * 30;
const INTRO_FROM_Y = INTRO_FROM_SCALE * 25;

const PIECE_OFFSET_Y: number[] = [-30, 10, 50];
const PIECE_CENTERS: [number, number][] = [
  [10, 10],
  [30, 20],
  [45, 35],
];

const SLIDE_DURATION_SECONDS = 1.4;
const STAGGER_SECONDS = 0.5;
const HOLD_DURATION_SECONDS = 0.5;
const EXIT_DURATION_SECONDS = 1.0;

const LOGO_PIECES = [
  "M 4 4 H 16.12 L 10.17 15.76 Z",
  "M 17.85 4.08 L 28.66 17.86 L 41.27 4 H 66 L 61.15 12.91 H 44.04 L 33.23 36.1 V 21.06 L 27.07 27.78 L 19.93 18.28 V 33.66 L 11.27 17.7 Z",
  "M 44.32 17.61 H 58.52 L 52.98 27.53 H 46.33 V 37.94 L 35.03 57.69 L 29.98 47.52 Z",
];

export function MediFlowLoader({
  size = 64,
  color = "#0F766E",
  trailColor = "#2DD4BF",
  speed = 1,
  glow = true,
  label,
  className,
  intro = true,
  slideDuration = SLIDE_DURATION_SECONDS,
  stagger = STAGGER_SECONDS,
  holdDuration = HOLD_DURATION_SECONDS,
  exitDuration = EXIT_DURATION_SECONDS,
  flow = true,
}: MediFlowLoaderProps) {
  const safeSize = Number.isFinite(size) && size > 0 ? size : 64;
  const safeSpeed = Number.isFinite(speed) && speed > 0 ? speed : 1;
  const safeHold =
    Number.isFinite(holdDuration) && holdDuration >= 0
      ? holdDuration
      : HOLD_DURATION_SECONDS;
  const safeExit =
    Number.isFinite(exitDuration) && exitDuration > 0
      ? exitDuration
      : EXIT_DURATION_SECONDS;
  const statusLabel = label ?? "Loading";
  const instanceId = useId();
  const monogramClipId = `${instanceId}-monogram-clip`;
  const monogramFlowId = `${instanceId}-monogram-flow`;

  const slide = slideDuration;
  const stg = stagger;
  const hold = safeHold;
  const exit = safeExit;

  const introTotal = slide + 2 * stg;
  const T = introTotal + hold + 2 * stg + exit;
  const totalDur = `${T}s`;

  const halfPeriod = T / 2;
  const beginA = "0s";
  const beginB = `${halfPeriod}s`;

  const duration = `${halfPeriod / safeSpeed}s`;

  // Màu fill cho monogram: gradient nếu flow = true, màu tĩnh nếu false
  const monogramFill = flow ? `url(#${monogramFlowId})` : color;

  const kt = (t: number) => (t / T).toFixed(4);

  const buildSlide = (pieceIndex: number) => {
    const offsetY = PIECE_OFFSET_Y[pieceIndex];
    const introStart = pieceIndex * stg;
    const exitStart = introTotal + hold + pieceIndex * stg;
    const exitEnd = exitStart + exit;

    const fromX = INTRO_FROM_X;
    const fromY = INTRO_FROM_Y + offsetY;
    const exitX = -fromX;
    const exitY = -fromY;

    return {
      keyTimes: `0;${kt(introStart)};${kt(introStart + slide)};${kt(exitStart)};${kt(exitEnd)};1`,
      translateValues:
        `${fromX} ${fromY};` +
        `${fromX} ${fromY};` +
        `0 0;` +
        `0 0;` +
        `${exitX} ${exitY};` +
        `${exitX} ${exitY}`,
      scaleValues:
        `${INTRO_FROM_SCALE};` +
        `${INTRO_FROM_SCALE};` +
        `1;1;` +
        `${INTRO_FROM_SCALE};` +
        `${INTRO_FROM_SCALE}`,
      opacityValues: `0;0;1;1;0;0`,
      keySplines:
        `0 0 1 1;` +
        `0.42 0 0.58 1;` +
        `0 0 1 1;` +
        `0.42 0 0.58 1;` +
        `0 0 1 1`,
    };
  };

  const pieceAnims = [buildSlide(0), buildSlide(1), buildSlide(2)];

  const renderLogoPiece = (
    anim: ReturnType<typeof buildSlide>,
    d: string,
    key: string,
    center: [number, number],
    begin: string,
  ) => (
    <g key={key}>
      <g>
        {intro ? (
          <animateTransform
            attributeName="transform"
            type="translate"
            dur={totalDur}
            begin={begin}
            values={anim.translateValues}
            keyTimes={anim.keyTimes}
            calcMode="spline"
            keySplines={anim.keySplines}
            repeatCount="indefinite"
          />
        ) : null}

        <g transform={`translate(${center[0]} ${center[1]})`}>
          <g>
            {intro ? (
              <animateTransform
                attributeName="transform"
                type="scale"
                dur={totalDur}
                begin={begin}
                values={anim.scaleValues}
                keyTimes={anim.keyTimes}
                calcMode="spline"
                keySplines={anim.keySplines}
                repeatCount="indefinite"
              />
            ) : null}
            <g transform={`translate(${-center[0]} ${-center[1]})`}>
              <g>
                {intro ? (
                  <animate
                    attributeName="opacity"
                    dur={totalDur}
                    begin={begin}
                    values={anim.opacityValues}
                    keyTimes={anim.keyTimes}
                    calcMode="spline"
                    keySplines={anim.keySplines}
                    repeatCount="indefinite"
                  />
                ) : null}
                <path d={d} fill={monogramFill} />
              </g>
            </g>
          </g>
        </g>
      </g>
    </g>
  );

  const renderLogoSet = (begin: string, setKey: string) => (
    <g key={setKey} transform="translate(15 18)">
      {LOGO_PIECES.map((d, i) =>
        renderLogoPiece(
          pieceAnims[i],
          d,
          `${setKey}-piece-${i}`,
          PIECE_CENTERS[i],
          begin,
        ),
      )}
    </g>
  );

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
        overflow="visible"
      >
        <defs>
          <clipPath id={monogramClipId}>
            <path d={INNER_TRIANGLE_PATH} />
          </clipPath>

          {/* Chỉ khai báo gradient khi flow = true */}
          {flow ? (
            <linearGradient
              gradientUnits="userSpaceOnUse"
              id={monogramFlowId}
              spreadMethod="repeat"
              x1="-24"
              x2="0"
              y1="-24"
              y2="0"
            >
              <stop offset="0" stopColor={color} />
              <stop offset="0.36" stopColor={color} />
              <stop offset="0.5" stopColor={trailColor} />
              <stop offset="0.64" stopColor={color} />
              <stop offset="1" stopColor={color} />
              <animate attributeName="x1" dur={duration} from="-24" repeatCount="indefinite" to="0" />
              <animate attributeName="x2" dur={duration} from="0" repeatCount="indefinite" to="24" />
              <animate attributeName="y1" dur={duration} from="-24" repeatCount="indefinite" to="0" />
              <animate attributeName="y2" dur={duration} from="0" repeatCount="indefinite" to="24" />
            </linearGradient>
          ) : null}
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

        {/* TRACER — giữ nguyên công thức gốc, chỉ đổi duration */}
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
        {glow ? (
          <circle fill={trailColor} opacity="0.24" r="6">
            <animateMotion dur={duration} path={TRIANGLE_PATH} repeatCount="indefinite" />
          </circle>
        ) : null}
        <circle fill={trailColor} r="3.2">
          <animateMotion dur={duration} path={TRIANGLE_PATH} repeatCount="indefinite" />
        </circle>
        <circle fill="#F0FDFA" r="1.4">
          <animateMotion dur={duration} path={TRIANGLE_PATH} repeatCount="indefinite" />
        </circle>

        {/* 2 bộ logo lệch pha nhau nửa chu kỳ */}
        <g clipPath={`url(#${monogramClipId})`}>
          {renderLogoSet(beginA, "set-a")}
          {renderLogoSet(beginB, "set-b")}
        </g>
      </svg>

      {label ? <div className="mt-2 text-center">{label}</div> : null}
    </div>
  );
}