import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  // Next 16.3: 404 de rota inexistente independente de layout/sessão, mantendo HTTP 404.
  experimental: { globalNotFound: true },
  transpilePackages: ["emoji-mart", "@emoji-mart/data"],
  // O contexto da imagem Docker e so `frontend/`. Os testes podem ler arquivos do repositorio inteiro
  // (docker/, backend/); o build de producao nao os tipa. `npm run typecheck` e o CI continuam tipando tudo.
  typescript: { tsconfigPath: "tsconfig.build.json" },
};

export default nextConfig;
