import { RecordBreadcrumb } from "@/features/organization/components/RecordLayout";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DepartmentForm } from "@/features/organization/components/DepartmentForm";
export const metadata: Metadata = { title: "Thêm khoa | MediFlow" };
export default function Page() { return <PageShell title="Thêm khoa" description="Tạo khoa hoặc phòng ban mới."><RecordBreadcrumb href="/organization" parent="Tổ chức" current="Thêm khoa" /><RoleGate allowed={["ADMIN"]}><DepartmentForm /></RoleGate></PageShell>; }
