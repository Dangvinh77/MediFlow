
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { formatLocalDate } from "@/lib/format";
import type { DoctorWorkShiftDTO } from "../types";

const weekdays = ["Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"];
export function DoctorWorkShiftWeekGrid({ fromDate, rows, disabled, onEdit, onSession, onCancel }: {
  fromDate: string; rows: DoctorWorkShiftDTO[]; disabled: boolean;
  onEdit: (row: DoctorWorkShiftDTO) => void; onSession: (row: DoctorWorkShiftDTO) => void; onCancel: (row: DoctorWorkShiftDTO) => void;
}) {
  const days = weekdays.map((label, index) => {
    const date = new Date(`${fromDate}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + index);
    return { label, date: date.toISOString().slice(0, 10) };
  });
  const doctors = new Map<string, DoctorWorkShiftDTO[]>();
  for (const row of rows) {
    if (row.shiftDate < days[0].date || row.shiftDate > days[6].date) continue;
    doctors.set(row.doctorId, [...(doctors.get(row.doctorId) ?? []), row]);
  }
  return <div className="overflow-x-auto rounded-lg border border-border" tabIndex={0} role="region" aria-label="Lưới phân ca theo tuần">
    <table className="w-full min-w-[1200px] border-collapse text-sm">
      <caption className="p-3 text-left text-muted-foreground"><L10n>{"Bác sĩ có ca trong tuần đã chọn. Tổng giờ chỉ tính ca đang hiệu lực; không bao gồm ca đã hủy. Có thể cuộn ngang hoặc chọn Theo ngày trên màn hình hẹp."}</L10n></caption>
      <thead><tr className="bg-surface-muted"><th scope="col" className="sticky left-0 z-20 min-w-40 border border-border bg-surface-muted p-3 text-left"><L10n>{"Bác sĩ"}</L10n></th><L10nValue>{days.map(day => <th key={day.date} scope="col" className="border border-border p-3 text-left"><L10nValue>{day.label}</L10nValue> <L10nValue>{formatLocalDate(day.date)}</L10nValue></th>)}</L10nValue><th scope="col" className="border border-border p-3 text-left"><L10n>{"Tổng giờ hiệu lực"}</L10n></th></tr></thead>
      <tbody><L10nValue>{[...doctors].map(([doctorId, shifts]) => {
        const minutes = shifts.filter(row => row.status === "ACTIVE").reduce((sum, row) => {
          const time = (value: string) => Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5));
          return sum + time(row.endTime) - time(row.startTime);
        }, 0);
        return <tr key={doctorId}><th scope="row" className="sticky left-0 z-10 min-w-40 border border-border bg-surface p-3 text-left align-top"><ReferenceText kind="doctor" value={doctorId} /></th><L10nValue>{days.map(day => <td key={day.date} className="min-w-40 space-y-2 border border-border p-2 align-top"><L10nValue>{shifts.filter(row => row.shiftDate === day.date).sort((a, b) => a.startTime.localeCompare(b.startTime)).map(row => <article key={row.shiftId} className="space-y-2 rounded-lg border border-border p-2"><p className="font-semibold">{row.startTime}–{row.endTime}</p><p><ReferenceText kind="room" value={row.roomId} /></p><p><L10nValue>{row.status === "ACTIVE" ? "Đang hiệu lực" : "Đã hủy"}</L10nValue><L10n>{" · Phiên bản "}</L10n>{row.revision}</p><L10nValue>{row.status === "ACTIVE" ? <div className="flex flex-col gap-2"><Button variant="secondary" disabled={disabled} onClick={() => onEdit(row)}><L10n>{"Sửa giờ làm việc"}</L10n></Button><Button disabled={disabled} onClick={() => onSession(row)}><L10n>{"Tạo buổi khám từ giờ làm việc"}</L10n></Button><Button variant="danger" disabled={disabled} onClick={() => onCancel(row)}><L10n>{"Hủy giờ làm việc"}</L10n></Button></div> : null}</L10nValue></article>)}</L10nValue></td>)}</L10nValue><td className="border border-border p-3 align-top font-semibold"><L10nValue>{new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(minutes / 60)}</L10nValue><L10n>{" giờ"}</L10n></td></tr>;
      })}</L10nValue></tbody>
    </table>
  </div>;
}
