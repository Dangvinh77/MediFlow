import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { clearSession, storeSession, getSessionKey } from "@/lib/session";
const mocks=vi.hoisted(()=>({pathname:"/appointments",router:{replace:vi.fn()},refresh:vi.fn(),cap:{session:null as string|null,data:{role:"DOCTOR",managementDepartments:[{departmentId:"d",departmentName:"Nội",appointmentId:"h",revision:1}]},loading:false,error:null as string|null}}));
vi.mock("next/navigation",()=>({useRouter:()=>mocks.router,usePathname:()=>mocks.pathname}));
vi.mock("@/lib/capabilities",()=>({useCapabilities:()=>mocks.cap,refreshCapabilities:mocks.refresh}));
vi.mock("@/components/layout/DashboardSidebar",()=>({DashboardSidebar:()=>null}));
vi.mock("@/components/layout/DashboardHeader",()=>({DashboardHeader:({workspaceHref,onWorkspaceSwitch,workspaceTitle}:{workspaceHref:string;onWorkspaceSwitch:React.MouseEventHandler;workspaceTitle:string})=><header><span>{workspaceTitle}</span><a href={workspaceHref} onClick={onWorkspaceSwitch}>Switch workspace</a></header>}));
vi.mock("./_components/DashboardReferences",()=>({DashboardReferences:({children}:{children:React.ReactNode})=>children}));
import DashboardShell from "./DashboardShell";
afterEach(()=>{act(()=>clearSession());vi.restoreAllMocks();vi.clearAllMocks();mocks.pathname="/appointments";mocks.cap.error=null;});
function login(){act(()=>storeSession({accessToken:"doctor",refreshToken:"r",role:"DOCTOR"}));mocks.cap.session=getSessionKey();}
it("warns before the workspace switch discards a professional form",()=>{
 login(); mocks.pathname="/records/new"; const confirm=vi.spyOn(window,"confirm").mockReturnValue(false);
 render(<DashboardShell schedulingEnabled><form><input aria-label="Diagnosis"/></form></DashboardShell>);
 fireEvent.change(screen.getByLabelText("Diagnosis"),{target:{value:"Unsaved"}});
 expect(fireEvent.click(screen.getByRole("link",{name:"Switch workspace"}))).toBe(false);
 expect(confirm).toHaveBeenCalledOnce();
});
it("rechecks on focus and management denial while preserving unavailable state",async()=>{
 login();mocks.pathname="/appointments/department";mocks.cap.error="Core unavailable";
 render(<DashboardShell schedulingEnabled><div>Retry authority</div></DashboardShell>);
 fireEvent.focus(window);fireEvent(window,new Event("mediflow:management-denied"));
 await waitFor(()=>expect(mocks.refresh.mock.calls.length).toBeGreaterThanOrEqual(3));
 expect(mocks.router.replace).not.toHaveBeenCalled();
 expect(screen.getByText("Trưởng khoa · Nội")).toBeInTheDocument();
});

it("does not treat calendar filters as unsaved professional work",()=>{
 login();mocks.pathname="/appointments/my-calendar";const confirm=vi.spyOn(window,"confirm").mockReturnValue(false);
 render(<DashboardShell schedulingEnabled><form><input aria-label="Calendar date" type="date"/></form></DashboardShell>);
 fireEvent.change(screen.getByLabelText("Calendar date"),{target:{value:"2026-11-02"}});
 fireEvent.click(screen.getByRole("link",{name:"Switch workspace"}));
 expect(confirm).not.toHaveBeenCalled();
});
