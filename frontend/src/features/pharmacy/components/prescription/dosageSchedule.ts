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
export const MAX_DAYS = 365;

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
  days: string;
}

function defaultTiming(session: DoseSession): DoseTiming {
  return session === "Khi cần" ? "Khi có triệu chứng" : "Sau ăn";
}

export function createDosageSchedule(): DosageSchedule {
  return {
    rows: DOSE_SESSIONS.map(session => ({ session, selected: false, timing: defaultTiming(session), dose: 1 })),
    days: "",
  };
}

export function applyDosePreset(schedule: DosageSchedule, sessions: readonly DoseSession[]): DosageSchedule {
  return { ...schedule, rows: schedule.rows.map(row => ({ ...row, selected: sessions.includes(row.session) })) };
}

export function formatDose(dose: number): string {
  return dose === 0.5 ? "1/2" : String(dose).replace(".", ",");
}

function parsedDays(days: string): number | null {
  const value = Number(days);
  return /^\d+$/.test(days.trim()) && value >= 1 && value <= MAX_DAYS ? value : null;
}

/** Returns the message to show, or null when the schedule can be applied. */
export function validateDosageSchedule(schedule: DosageSchedule): string | null {
  const selected = schedule.rows.filter(row => row.selected);
  if (selected.length === 0) return "Chọn ít nhất một buổi dùng.";
  if (selected.some(row => !(row.dose > 0) || row.dose > MAX_DOSE || !Number.isInteger(row.dose / DOSE_STEP))) {
    return `Liều mỗi buổi phải từ ${formatDose(DOSE_STEP)} đến ${MAX_DOSE}, theo bước ${formatDose(DOSE_STEP)}.`;
  }
  if (schedule.days.trim() && parsedDays(schedule.days) === null) return `Số ngày dùng phải từ 1 đến ${MAX_DAYS}.`;
  return null;
}

/** Produces the read-only "Cách dùng" text stored in the prescription line's dosage. */
export function formatDosageSchedule(schedule: DosageSchedule, unit: string): string {
  const unitText = unit.trim().toLowerCase();
  const parts = schedule.rows.filter(row => row.selected).map(row =>
    `${row.session} ${formatDose(row.dose)}${unitText ? ` ${unitText}` : ""} ${row.timing.toLowerCase()}`);
  const days = parsedDays(schedule.days);
  return `${parts.join("; ")}${days === null ? "" : ` (${days} ngày)`}`;
}

/** Scheduled doses × days, rounded up. "Khi cần" is unpredictable and never counted. */
export function suggestQuantity(schedule: DosageSchedule, unit: string): number | null {
  const days = parsedDays(schedule.days);
  if (days === null || !COUNTABLE_UNITS.has(unit.trim().toLowerCase())) return null;
  const daily = schedule.rows
    .filter(row => row.selected && row.session !== "Khi cần")
    .reduce((sum, row) => sum + row.dose, 0);
  return daily > 0 ? Math.ceil(daily * days) : null;
}
