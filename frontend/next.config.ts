import type { NextConfig } from "next";

const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async rewrites() {
    const javaOrigin = process.env.JAVA_API_BASE_URL ?? "http://backend:8080";
    return [{ source: "/api/:path*", destination: `${javaOrigin}/api/:path*` }];
  },
};
export default config;
