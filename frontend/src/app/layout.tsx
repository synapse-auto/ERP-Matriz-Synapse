import type { Metadata } from "next";
import { Inter } from "next/font/google";
import localFont from "next/font/local";

import { TransicaoDeAbertura } from "@/components/auth/transicao-de-abertura";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { buscarTema, buscarTextos } from "@/lib/config/fetch-config";
import { TextosProvider } from "@/lib/config/textos-provider";
import { QueryProvider } from "@/lib/query/query-provider";
import { TooltipProvider } from "@/components/ui/tooltip";
import { montarMetadata } from "./metadata";

import "./globals.css";
import "./identidade-synapse.css";

/**
 * Inter vem do Google Fonts só no `next build`: o Next baixa os arquivos e os serve da própria
 * imagem. Em runtime o navegador não consulta o CDN — a aba Atendimentos não depende de uma
 * fonte externa ficar no ar.
 */
const inter = Inter({
  subsets: ["latin", "latin-ext"],
  display: "swap",
  variable: "--fonte-base-carregada",
});
const jetBrainsMono = localFont({
  src: "./fonts/JetBrainsMono-Variable.woff2",
  weight: "100 800",
  display: "swap",
  variable: "--fonte-mono-carregada",
});

// Os textos são arquivos da instância lidos pelo backend no runtime; nunca devem ser congelados na
// imagem durante `next build`. O tema, por sua vez, é buscado somente no layout do shell: a tela
// de login é a identidade fixa do produto Synapse, não a marca de cada cliente.
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const [textos, tema] = await Promise.all([buscarTextos(), buscarTema()]);
  // O favicon identifica a instância na aba do navegador; não reintroduza tema/logo visível no
  // /login. A identidade visual da tela continua fixa na marca Synapse.
  return montarMetadata(textos, tema);
}

/**
 * Server Component raiz: fornece o catálogo de textos a toda a aplicação. As variáveis do tema da
 * instância vivem no layout `(shell)`, para que `/login` não consulte `tema.json` nem use a logo
 * do cliente.
 */
export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const textos = await buscarTextos();

  return (
    <html
      lang="pt-BR"
      className={`h-full antialiased ${inter.variable} ${jetBrainsMono.variable} ${inter.className}`}
    >
      <body className="flex h-full flex-col overflow-hidden bg-background font-sans font-normal text-foreground">
        <QueryProvider>
          <TextosProvider textos={textos}>
            <AuthProvider>
              <TooltipProvider>
                {children}
                <TransicaoDeAbertura />
              </TooltipProvider>
            </AuthProvider>
          </TextosProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
