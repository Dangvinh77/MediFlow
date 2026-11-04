import { RecoveryForm } from "@/features/organization/components/RecoveryForm";
export const dynamic = "force-dynamic";
export const metadata = { title: "Đặt mật khẩu mới | MediFlow", robots: { index: false, follow: false }, referrer: "no-referrer" };
export default async function ResetPasswordPage({ searchParams }: { searchParams: Promise<{ token?: string }> }) {
  const params = await searchParams;
  return <RecoveryForm key={typeof params.token === "string" ? params.token : "missing"} token={typeof params.token === "string" ? params.token : ""} />;
}
