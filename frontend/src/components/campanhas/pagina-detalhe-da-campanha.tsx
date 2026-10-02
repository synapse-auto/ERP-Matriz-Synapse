"use client";

import Link from "next/link";
import { ArrowLeft, RefreshCw } from "lucide-react";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarDataHora } from "@/lib/campanhas/formatacao";
import { funcionalidadeIndisponivel, useDetalheDaCampanha } from "@/lib/campanhas/hooks";
import type { Campanha } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

import { AbaConferencia } from "./aba-conferencia";
import { AbaDestinatarios } from "./aba-destinatarios";
import { AjusteDeLimite } from "./ajuste-de-limite";
import { AlertaDePausa } from "./alerta-de-pausa";
import { ChipDeStatus } from "./chip-de-status";
import { ControlesDaCampanha } from "./controles-da-campanha";
import { CampanhasIndisponiveis } from "./estados";
import { FunilDaCampanha } from "./funil-da-campanha";
import { GraficoPorDia } from "./grafico-por-dia";

function EsqueletoDoDetalhe() {
  const rotulo = useTextos().campanhas.detalhe.carregando;
  return (
    <main className="space-y-5 p-6" role="status" aria-label={rotulo}>
      <Skeleton className="h-9 w-72" />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        {Array.from({ length: 5 }, (_, indice) => (
          <Skeleton key={indice} className="h-32" />
        ))}
      </div>
      <Skeleton className="h-64 w-full" />
    </main>
  );
}

function datasDaCampanha(campanha: Campanha, textos: ReturnType<typeof useTextos>["campanhas"]["detalhe"]): string[] {
  const datas = [interpolarCatalogo(textos.criadaEm, { data: formatarDataHora(campanha.criadaEm) })];
  if (campanha.iniciadaEm) datas.push(interpolarCatalogo(textos.iniciadaEm, { data: formatarDataHora(campanha.iniciadaEm) }));
  if (campanha.concluidaEm) datas.push(interpolarCatalogo(textos.concluidaEm, { data: formatarDataHora(campanha.concluidaEm) }));
  return datas;
}

export function PaginaDetalheDaCampanha({ id }: { id: string }) {
  const textos = useTextos().campanhas;
  const consulta = useDetalheDaCampanha(id);

  if (consulta.isError && funcionalidadeIndisponivel(consulta.error)) return <CampanhasIndisponiveis />;
  if (consulta.isError && !consulta.data) {
    return (
      <main className="p-6">
        <ErroDeCarregamento mensagem={textos.detalhe.erro} onTentarNovamente={() => consulta.refetch()} />
      </main>
    );
  }
  if (!consulta.data) return <EsqueletoDoDetalhe />;

  const detalhe = consulta.data;
  const { campanha } = detalhe;
  const atualizaSozinha = campanha.status === "EM_ANDAMENTO" || campanha.status === "AGENDADA";

  return (
    <main className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <Link href="/campanhas" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:underline">
            <ArrowLeft className="size-4" aria-hidden />
            {textos.voltar}
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-bold">{campanha.nome}</h1>
            <ChipDeStatus status={campanha.status} />
          </div>
          <p className="text-sm text-muted-foreground">
            {interpolarCatalogo(textos.detalhe.template, { nome: campanha.template.nome })} · {datasDaCampanha(campanha, textos.detalhe).join(" · ")}
          </p>
          {atualizaSozinha && (
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <RefreshCw className="size-3 motion-safe:animate-spin" aria-hidden />
              {textos.detalhe.atualizadoAutomaticamente}
            </p>
          )}
        </div>
        <ControlesDaCampanha campanha={campanha} />
      </header>
      {campanha.status === "PAUSADA_AUTOMATICAMENTE" && <AlertaDePausa campanha={campanha} />}
      <Tabs defaultValue="visao-geral">
        <TabsList>
          <TabsTrigger value="visao-geral">{textos.detalhe.abas.visaoGeral}</TabsTrigger>
          <TabsTrigger value="destinatarios">{textos.detalhe.abas.destinatarios}</TabsTrigger>
          <TabsTrigger value="conferencia">
            {textos.detalhe.abas.conferencia}
            {campanha.contadores.conferencia > 0 && ` (${campanha.contadores.conferencia})`}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="visao-geral" className="space-y-5 pt-4">
          <FunilDaCampanha contadores={campanha.contadores} />
          <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,24rem)]">
            <section className="space-y-2 rounded-xl border border-border bg-card p-4 shadow-sm">
              <h2 className="text-sm font-bold">{textos.detalhe.grafico.titulo}</h2>
              <GraficoPorDia dias={detalhe.porDia} limiteDoDia={detalhe.limiteEfetivoHoje} />
            </section>
            <AjusteDeLimite key={campanha.limiteDiario} detalhe={detalhe} />
          </div>
        </TabsContent>
        <TabsContent value="destinatarios" className="pt-4">
          <AbaDestinatarios campanhaId={campanha.id} />
        </TabsContent>
        <TabsContent value="conferencia" className="pt-4">
          <AbaConferencia campanhaId={campanha.id} />
        </TabsContent>
      </Tabs>
    </main>
  );
}
