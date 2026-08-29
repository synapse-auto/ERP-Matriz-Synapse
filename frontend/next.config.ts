import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: ["emoji-mart", "@emoji-mart/data"],
};

export default nextConfig;
