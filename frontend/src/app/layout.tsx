import type { Metadata } from "next";

import { AuthProvider } from "@/lib/auth/auth-provider";
import { buscarTema, buscarTextos, temaParaCssVariaveis } from "@/lib/config/fetch-config";
import { TextosProvider } from "@/lib/config/textos-provider";
import { QueryProvider } from "@/lib/query/query-provider";
import { TooltipProvider } from "@/components/ui/tooltip";

import "./globals.css";

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
    <html lang="pt-BR" className="h-full antialiased">
      <head>
        <style>{temaParaCssVariaveis(tema)}</style>
      </head>
      <body className="min-h-full flex flex-col bg-background text-foreground font-sans">
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
