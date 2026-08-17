import type { Metadata } from "next";
import localFont from "next/font/local";

import { AuthProvider } from "@/lib/auth/auth-provider";
import { buscarTema, buscarTextos, temaParaCssVariaveis } from "@/lib/config/fetch-config";
import { TextosProvider } from "@/lib/config/textos-provider";
import { QueryProvider } from "@/lib/query/query-provider";
import { TooltipProvider } from "@/components/ui/tooltip";

import "./globals.css";

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

// Tema e textos sao arquivos da instancia lidos pelo backend no runtime; nunca devem ser
// congelados na imagem durante `next build`.
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const textos = await buscarTextos();
  return {
    title: textos.app.nome,
    description: textos.app.subtitulo,
  };
}

/**
 * Server Component: busca tema.json/textos.json a cada carregamento (E10 — "trocar tema.json muda
 * a aparência sem tocar em nenhum .tsx") e injeta as CSS variables direto no <head>, antes de
 * qualquer componente renderizar. É o que faz a tela de login já nascer themeada, sem flash de
 * estilo padrão do shadcn.
 */
export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const [tema, textos] = await Promise.all([buscarTema(), buscarTextos()]);

  return (
    <html
      lang="pt-BR"
      className={`h-full antialiased ${hankenGrotesk.variable} ${jetBrainsMono.variable}`}
    >
      <head>
        <style>{temaParaCssVariaveis(tema)}</style>
      </head>
      <body className="flex h-full flex-col overflow-hidden bg-background font-sans text-foreground">
        <QueryProvider>
          <TextosProvider textos={textos}>
            <AuthProvider>
              <TooltipProvider>{children}</TooltipProvider>
            </AuthProvider>
          </TextosProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
