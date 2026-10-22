import type { Metadata } from "next";
import { OrganizationWorkspace } from "./OrganizationWorkspace";

export const metadata: Metadata = {
  title: "Tổ chức | MediFlow",
};

export default function OrganizationPage() {
  return <OrganizationWorkspace />;
}
