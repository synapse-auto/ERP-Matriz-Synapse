import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: ["emoji-mart", "@emoji-mart/data"],
  // O contexto da imagem Docker e so `frontend/`. Os testes podem ler arquivos do repositorio inteiro
  // (docker/, backend/); o build de producao nao os tipa. `npm run typecheck` e o CI continuam tipando tudo.
  typescript: { tsconfigPath: "tsconfig.build.json" },
};

export default nextConfig;
