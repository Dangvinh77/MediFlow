import { PageShell } from "@/components/layout/PageShell";
import { RoleGate } from "@/components/auth/RoleGate";
import { PasswordForm } from "@/features/organization/components/PasswordForm";
export default function Page() {
  return <PageShell title="Bảo mật tài khoản" description="Quản lý mật khẩu đăng nhập của bạn."><RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT"]}><PasswordForm /></RoleGate></PageShell>;
}
