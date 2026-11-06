import { CROSS_COLOR, CROSS_PATH, TRACE_COLOR, TRACE_PATH, TRACE_WIDTH } from "./geometry";

export interface MediFlowMarkProps {
  // Square size in CSS pixels. Defaults to 40.
  size?: number;
  // "theme" follows the light/dark tokens; "on-brand" is the white cross for deep teal surfaces.
  tone?: "theme" | "on-brand";
  className?: string;
}

const ON_BRAND = { cross: "#FFFFFF", trace: "#0F766E" };

export function MediFlowMark({ size = 40, tone = "theme", className }: MediFlowMarkProps) {
  const onBrand = tone === "on-brand";
  return (
    <svg
      aria-hidden="true"
      className={className}
      data-mediflow-mark={tone}
      focusable="false"
      height={size}
      viewBox="0 0 100 100"
      width={size}
    >
      <path d={CROSS_PATH} fill={onBrand ? ON_BRAND.cross : CROSS_COLOR} />
      <path
        d={TRACE_PATH}
        fill="none"
        stroke={onBrand ? ON_BRAND.trace : TRACE_COLOR}
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={TRACE_WIDTH}
      />
    </svg>
  );
}
