"use client";

import Link from "next/link";
import { ArrowLeft } from "lucide-react";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Skeleton } from "@/components/ui/skeleton";
import { funcionalidadeIndisponivel, useConfiguracaoDeCampanhas } from "@/lib/campanhas/hooks";
import { useTextos } from "@/lib/config/textos-provider";

import { CampanhasIndisponiveis } from "./estados";
import { FormularioDeConfiguracaoDeCampanhas } from "./formulario-de-configuracao";

export function PaginaConfiguracaoDeCampanhas() {
  const textos = useTextos().campanhas;
  const t = textos.configuracaoDaInstancia;
  const consulta = useConfiguracaoDeCampanhas();

  if (consulta.isError && funcionalidadeIndisponivel(consulta.error)) return <CampanhasIndisponiveis />;

  return (
    <main className="max-w-4xl space-y-5 p-6">
      <header className="space-y-2">
        <Link href="/campanhas" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:underline">
          <ArrowLeft className="size-4" aria-hidden />
          {textos.voltar}
        </Link>
        <h1 className="text-2xl font-bold">{t.titulo}</h1>
        <p className="text-sm text-muted-foreground">{t.descricao}</p>
      </header>
      {consulta.isPending && (
        <div role="status" aria-label={t.carregando} className="space-y-4">
          <Skeleton className="h-48 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      )}
      {consulta.isError && <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => consulta.refetch()} />}
      {consulta.data && <FormularioDeConfiguracaoDeCampanhas key={JSON.stringify(consulta.data)} configuracao={consulta.data} />}
    </main>
  );
}
