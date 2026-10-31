import { LoginForm } from "./LoginForm";

export const dynamic = "force-dynamic";

export default function LoginPage() {
  return <LoginForm schedulingEnabled={process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"} />;
}
