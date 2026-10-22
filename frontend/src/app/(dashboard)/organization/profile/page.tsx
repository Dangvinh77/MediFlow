import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";
import { ProfileWorkspace } from "./ProfileWorkspace";

export const metadata: Metadata = { title: "Hồ sơ cá nhân | MediFlow" };

export default function ProfilePage() {
  return <PageShell title="Hồ sơ cá nhân" description="Thông tin của bạn và bảo mật tài khoản."><ProfileWorkspace /></PageShell>;
}
