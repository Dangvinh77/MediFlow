import type { NextConfig } from "next";

// Gateway origin. In dev the browser calls the Next server same-origin and Next
// proxies /api/* to the gateway, so there is no CORS to configure. Override with
// GATEWAY_URL in the environment (e.g. when the gateway runs elsewhere).
const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8080";

// Hosts allowed to reach Next dev resources (HMR) when the dev server is opened by
// LAN IP instead of localhost. Comma-separated; the demo launcher sets it.
const DEV_ALLOWED_ORIGINS = (process.env.MEDIFLOW_DEV_ALLOWED_ORIGINS ?? "")
  .split(",")
  .map(origin => origin.trim())
  .filter(Boolean);

const nextConfig: NextConfig = {
  output: process.env.NODE_ENV === "production" ? "standalone" : undefined,
  allowedDevOrigins: DEV_ALLOWED_ORIGINS,

  async headers() {
    return ["/account-access", "/reset-password", "/forgot-password", "/demo-mailbox"].map(source => ({
      source, headers: [
        { key: "Cache-Control", value: "no-store" },
        { key: "Referrer-Policy", value: "no-referrer" },
        { key: "X-Robots-Tag", value: "noindex, nofollow" },
      ],
    }));
  },

  async redirects() {
    // Merged screens: shifts live on the doctor calendar, the week on the department overview.
    return [
      { source: "/billing/refunds", destination: "/billing", permanent: false },
      { source: "/appointments/my-shifts", destination: "/appointments/my-calendar", permanent: false },
      { source: "/appointments/department/calendar", destination: "/appointments/department", permanent: false },
      // Entry routes without content of their own. Redirected here, not with redirect() during render,
      // which trips React's dev-only "negative time stamp" performance error. The query string carries over.
      { source: "/pharmacy", destination: "/pharmacy/drugs", permanent: false },
      { source: "/reports", destination: "/reports/outpatient", permanent: false },
      // With real scheduling, registration happens at reception.
      ...(process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true"
        ? [{ source: "/appointments/new", destination: "/appointments/reception", permanent: false }]
        : []),
    ];
  },
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${GATEWAY_URL}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
