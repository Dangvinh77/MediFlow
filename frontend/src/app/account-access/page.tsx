import { redirect } from "next/navigation";
export const dynamic = "force-dynamic";
export const metadata = { robots: { index: false, follow: false }, referrer: "no-referrer" };
export default async function AccountAccessPage({ searchParams }: { searchParams: Promise<{ purpose?: string; token?: string }> }) {
  const params = await searchParams;
  if (params.purpose === "reset" && typeof params.token === "string" && params.token.length <= 512) redirect(`/reset-password?token=${encodeURIComponent(params.token)}`);
  redirect("/login");
}
