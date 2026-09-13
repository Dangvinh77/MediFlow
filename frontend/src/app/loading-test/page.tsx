import { MediFlowLoader } from "@mediflow/loader";

export default function LoadingTestPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-950">
      <MediFlowLoader
        size={160}
        speed={1.5}
        color="#0F766E"
        trailColor="#2DD4BF"
        glow
        label="Loading..."
        className="flex flex-col items-center gap-4 text-sm text-white"
        holdDuration={0.6}
        slideDuration={1}
        stagger={0.1}
      />
    </main>
  );
}
