import { RecoveryForm } from "@/features/organization/components/RecoveryForm";
export const dynamic = "force-dynamic";
export const metadata = { title: "Quên mật khẩu | MediFlow", robots: { index: false, follow: false }, referrer: "no-referrer" };
export default function ForgotPasswordPage() { return <RecoveryForm />; }
