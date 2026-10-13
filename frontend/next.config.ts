import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // Next 16.3: 404 de rota inexistente independente de layout/sessão, mantendo HTTP 404.
  experimental: { globalNotFound: true },
  transpilePackages: ["emoji-mart", "@emoji-mart/data"],
};

export default nextConfig;
