import type { NextConfig } from "next";

const staticExport = process.env.NEXT_STATIC_EXPORT === "true";

const nextConfig: NextConfig = staticExport
  ? {
      output: "export",
      trailingSlash: true,
    }
  : {
      async headers() {
        return [
          {
            source: "/sw.js",
            headers: [
              {
                key: "Content-Type",
                value: "application/javascript; charset=utf-8",
              },
              {
                key: "Cache-Control",
                value: "no-cache, no-store, must-revalidate",
              },
              {
                key: "Content-Security-Policy",
                value: "default-src 'self'; script-src 'self'",
              },
              {
                key: "Service-Worker-Allowed",
                value: "/",
              },
            ],
          },
        ];
      },
    };

export default nextConfig;
