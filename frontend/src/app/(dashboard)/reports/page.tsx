import { redirect } from "next/navigation";

export const dynamic = "force-dynamic";

/** Legacy daily/monthly/medicine views were merged into the reconciled report; old links keep their query. */
export default async function ReportsPage({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const query = new URLSearchParams();
  Object.entries(await searchParams).forEach(([name, value]) => {
    (Array.isArray(value) ? value : value === undefined ? [] : [value]).forEach(item => query.append(name, item));
  });
  redirect(`/reports/outpatient${query.size ? `?${query}` : ""}`);
}
