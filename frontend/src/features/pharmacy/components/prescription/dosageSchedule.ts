export const DOSE_SESSIONS = ["Sáng", "Trưa", "Chiều", "Tối", "Khi cần"] as const;
export type DoseSession = (typeof DOSE_SESSIONS)[number];

export const DOSE_TIMINGS = [
  "Trước ăn",
  "Sau ăn",
  "Trong bữa ăn",
  "Lúc đói",
  "Trước khi ngủ",
  "Khi có triệu chứng",
  "Không phụ thuộc bữa ăn",
] as const;
export type DoseTiming = (typeof DOSE_TIMINGS)[number];

export const DOSE_PRESETS: readonly { label: string; sessions: readonly DoseSession[] }[] = [
  { label: "Sáng - Tối", sessions: ["Sáng", "Tối"] },
  { label: "Sáng - Trưa - Tối", sessions: ["Sáng", "Trưa", "Tối"] },
  { label: "Chỉ tối", sessions: ["Tối"] },
  { label: "Khi cần", sessions: ["Khi cần"] },
];

export const DOSE_STEP = 0.5;
export const MAX_DOSE = 20;
/** Outpatient prescriptions run at most 90 days (TT 26/2025/TT-BYT). */
export const MAX_DAYS = 90;
/** Beyond this, the drug must be for a stable chronic disease on the long-prescription list. */
export const STANDARD_MAX_DAYS = 30;

/** Treatment length as written on Vietnamese prescriptions; one "tháng thuốc" is 30 days. */
export const DURATION_UNITS = [
  { unit: "Ngày", label: "ngày", days: 1 },
  { unit: "Tuần", label: "tuần", days: 7 },
  { unit: "Tháng", label: "tháng", days: 30 },
] as const;
export type DurationUnit = (typeof DURATION_UNITS)[number]["unit"];

export const DURATION_PRESETS: readonly { label: string; duration: string; durationUnit: DurationUnit }[] = [
  { label: "1 tuần", duration: "1", durationUnit: "Tuần" },
  { label: "2 tuần", duration: "2", durationUnit: "Tuần" },
  { label: "1 tháng", duration: "1", durationUnit: "Tháng" },
  { label: "2 tháng", duration: "2", durationUnit: "Tháng" },
  { label: "3 tháng", duration: "3", durationUnit: "Tháng" },
];

/** Units dispensed per dose; a bottle or tube cannot be derived from dose × days. */
const COUNTABLE_UNITS = new Set(["viên", "viên nén", "viên nang", "gói", "ống", "nang"]);

export interface DoseRow {
  session: DoseSession;
  selected: boolean;
  timing: DoseTiming;
  dose: number;
}

export interface DosageSchedule {
  rows: DoseRow[];
  /** How many `durationUnit`s the patient takes the drug; empty when not stated. */
  duration: string;
  durationUnit: DurationUnit;
}

function defaultTiming(session: DoseSession): DoseTiming {
  return session === "Khi cần" ? "Khi có triệu chứng" : "Sau ăn";
}

export function createDosageSchedule(): DosageSchedule {
  return {
    rows: DOSE_SESSIONS.map(session => ({ session, selected: false, timing: defaultTiming(session), dose: 1 })),
    duration: "",
    durationUnit: "Ngày",
  };
}

export function applyDosePreset(schedule: DosageSchedule, sessions: readonly DoseSession[]): DosageSchedule {
  return { ...schedule, rows: schedule.rows.map(row => ({ ...row, selected: sessions.includes(row.session) })) };
}

export function formatDose(dose: number): string {
  return dose === 0.5 ? "1/2" : String(dose).replace(".", ",");
}

function unitDays(unit: DurationUnit): number {
  return DURATION_UNITS.find(item => item.unit === unit)?.days ?? 1;
}

/** Whole number of units, 1 or more; null when blank or malformed. */
function parsedAmount(duration: string): number | null {
  const value = Number(duration);
  return /^\d+$/.test(duration.trim()) && value >= 1 ? value : null;
}

/** Total treatment days, or null when the duration is blank, malformed or over the outpatient limit. */
export function scheduleDays(schedule: DosageSchedule): number | null {
  const amount = parsedAmount(schedule.duration);
  const days = amount === null ? null : amount * unitDays(schedule.durationUnit);
  return days !== null && days <= MAX_DAYS ? days : null;
}

/** Shown, not blocking: a doctor may prescribe over 30 days only for an eligible stable chronic disease. */
export function longPrescriptionNotice(schedule: DosageSchedule): string | null {
  const days = scheduleDays(schedule);
  return days !== null && days > STANDARD_MAX_DAYS
    ? "Đơn trên 30 ngày chỉ dùng cho bệnh mạn tính ổn định thuộc danh mục được kê đơn dài ngày."
    : null;
}

export function applyDurationPreset(schedule: DosageSchedule, duration: string, durationUnit: DurationUnit): DosageSchedule {
  return { ...schedule, duration, durationUnit };
}

function durationText(schedule: DosageSchedule, days: number): string {
  const amount = schedule.duration.trim();
  return schedule.durationUnit === "Ngày"
    ? `${days} ngày`
    : `${amount} ${schedule.durationUnit.toLowerCase()} – ${days} ngày`;
}

/** Returns the message to show, or null when the schedule can be applied. */
export function validateDosageSchedule(schedule: DosageSchedule): string | null {
  const selected = schedule.rows.filter(row => row.selected);
  if (selected.length === 0) return "Chọn ít nhất một buổi dùng.";
  if (selected.some(row => !(row.dose > 0) || row.dose > MAX_DOSE || !Number.isInteger(row.dose / DOSE_STEP))) {
    return `Liều mỗi buổi phải từ ${formatDose(DOSE_STEP)} đến ${MAX_DOSE}, theo bước ${formatDose(DOSE_STEP)}.`;
  }
  if (schedule.duration.trim() && scheduleDays(schedule) === null) {
    return "Thời gian dùng phải là số nguyên, tối đa 90 ngày (12 tuần hoặc 3 tháng).";
  }
  return null;
}

/** Produces the read-only "Cách dùng" text stored in the prescription line's dosage. */
export function formatDosageSchedule(schedule: DosageSchedule, unit: string): string {
  const unitText = unit.trim().toLowerCase();
  const parts = schedule.rows.filter(row => row.selected).map(row =>
    `${row.session} ${formatDose(row.dose)}${unitText ? ` ${unitText}` : ""} ${row.timing.toLowerCase()}`);
  const days = scheduleDays(schedule);
  return `${parts.join("; ")}${days === null ? "" : ` (${durationText(schedule, days)})`}`;
}

/** Scheduled doses × days, rounded up. "Khi cần" is unpredictable and never counted. */
export function suggestQuantity(schedule: DosageSchedule, unit: string): number | null {
  const days = scheduleDays(schedule);
  if (days === null || !COUNTABLE_UNITS.has(unit.trim().toLowerCase())) return null;
  const daily = schedule.rows
    .filter(row => row.selected && row.session !== "Khi cần")
    .reduce((sum, row) => sum + row.dose, 0);
  return daily > 0 ? Math.ceil(daily * days) : null;
}
