import { MedicalRecordTable } from "@/features/medical-record/components/MedicalRecordTable";

export default function RecordsPage() {
  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <h1 className="text-2xl font-bold">Hồ sơ khám</h1>
      <p className="mt-1 text-sm text-zinc-500">Tra cứu hồ sơ theo mã bệnh nhân.</p>
      <MedicalRecordTable />
    </main>
  );
}
