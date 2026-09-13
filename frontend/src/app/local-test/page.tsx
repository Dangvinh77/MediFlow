import { MediFlowLoader } from "@mediflow/loader";

export default function LocalTestPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-950">
      <MediFlowLoader
        className="flex flex-col items-center gap-4 text-sm text-white"
        color="#0F766E"
        glow
        holdDuration={0.6}
        label="Loading..."
        size={160}
        slideDuration={1}
        speed={1.5}
        stagger={0.1}
        trailColor="#2DD4BF"
      />
    </main>
  );
}
