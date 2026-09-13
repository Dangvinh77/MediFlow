import { LabTable } from "@/features/lab/components/LabTable";

export default function LabPage() {
  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <h1 className="text-2xl font-bold">Xét nghiệm</h1>
      <p className="mt-1 text-sm text-zinc-500">Danh sách 20 yêu cầu xét nghiệm gần nhất.</p>
      <LabTable />
    </main>
  );
}
