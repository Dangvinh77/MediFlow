import type { NextConfig } from "next";

// Gateway origin. In dev the browser calls the Next server same-origin and Next
// proxies /api/* to the gateway, so there is no CORS to configure. Override with
// GATEWAY_URL in the environment (e.g. when the gateway runs elsewhere).
const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  output: process.env.NODE_ENV === "production" ? "standalone" : undefined,

  async headers() {
    return ["/account-access", "/reset-password", "/forgot-password", "/demo-mailbox"].map(source => ({
      source, headers: [
        { key: "Cache-Control", value: "no-store" },
        { key: "Referrer-Policy", value: "no-referrer" },
        { key: "X-Robots-Tag", value: "noindex, nofollow" },
      ],
    }));
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
