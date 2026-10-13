import { TelaDeErro } from "@/components/erros/tela-de-erro";
import { buscarTema, buscarTextos, temaParaCssVariaveis } from "@/lib/config/fetch-config";
import { classesDasFontes } from "./fontes";
import "./globals.css";

/** Rota realmente inexistente: não monta sessão, consultas privadas nem o layout dependente da API. */
export default async function NaoEncontradaGlobal() {
  const [textos, tema] = await Promise.allSettled([buscarTextos(), buscarTema()]);
  return (
    <html lang="pt-BR" className={`h-full antialiased ${classesDasFontes}`}>
      <body className="flex min-h-dvh flex-col bg-background font-sans text-foreground">
        {tema.status === "fulfilled" && <style>{temaParaCssVariaveis(tema.value)}</style>}
        <TelaDeErro tipo="404"
          textos={textos.status === "fulfilled" ? textos.value.erros : undefined}
          marca={textos.status === "fulfilled" ? textos.value.app.marca : undefined}
          logoUrl={tema.status === "fulfilled" ? tema.value.logoUrl : null}
        />
      </body>
    </html>
  );
}
