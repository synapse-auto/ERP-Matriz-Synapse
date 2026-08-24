import type { Metadata } from "next";
import localFont from "next/font/local";

import { TransicaoDeAbertura } from "@/components/auth/transicao-de-abertura";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { buscarTextos } from "@/lib/config/fetch-config";
import { TextosProvider } from "@/lib/config/textos-provider";
import { QueryProvider } from "@/lib/query/query-provider";
import { TooltipProvider } from "@/components/ui/tooltip";

import "./globals.css";
import "./identidade-synapse.css";

/**
 * As duas fontes do protótipo ficam versionadas junto da aplicação. Além de evitar o fallback
 * mais largo que truncava rótulos como "Mensagens Programadas", `next/font/local` elimina a
 * consulta ao CDN do Google durante `next build` — uma indisponibilidade externa não pode quebrar
 * a imagem do frontend.
 */
const hankenGrotesk = localFont({
  src: "./fonts/HankenGrotesk-Variable.woff2",
  weight: "100 900",
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
  const textos = await buscarTextos();
  return {
    title: textos.app.nome,
    description: textos.app.subtitulo,
  };
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
      className={`h-full antialiased ${hankenGrotesk.variable} ${jetBrainsMono.variable}`}
    >
      <body className="flex h-full flex-col overflow-hidden bg-background font-sans text-foreground">
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
