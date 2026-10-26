import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { NotificationLookup } from "@/features/notification/components/NotificationLookup";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = {
  title: "Thông báo | MediFlow",
};

interface Props { searchParams: Promise<{ patientId?: string; page?: string }> }

export default async function NotificationsPage({ searchParams }: Props) {
  const { patientId, page } = await searchParams;
  const trimmedPatientId = patientId?.trim();
  // Coming back from a notification's detail page: re-run the same search instead of showing a blank form.
  const initialPatientId = trimmedPatientId && isUuid(trimmedPatientId) ? trimmedPatientId : undefined;
  const parsedPage = page ? Number.parseInt(page, 10) : NaN;
  const initialPage = initialPatientId && Number.isInteger(parsedPage) && parsedPage >= 0 ? parsedPage : undefined;
  return (
    <PageShell
      title="Thông báo"
      description="Xem thông báo cá nhân hoặc tra cứu lịch sử thông báo theo quyền của bạn."
    >
      <RoleGate allowed={["ADMIN", "NURSE", "PATIENT"]}>
        <NotificationLookup initialPatientId={initialPatientId} initialPage={initialPage} />
      </RoleGate>
    </PageShell>
  );
}
