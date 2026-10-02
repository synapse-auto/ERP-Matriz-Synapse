"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect } from "react";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Skeleton } from "@/components/ui/skeleton";
import { estadoDoRascunho, estadoInicial } from "@/lib/campanhas/estado-do-assistente";
import {
  funcionalidadeIndisponivel,
  useConfiguracaoDeCampanhas,
  useDetalheDaCampanha,
  useEhAdministradorDeCampanhas,
} from "@/lib/campanhas/hooks";
import { useTextos } from "@/lib/config/textos-provider";

import { AssistenteDeCampanha } from "./assistente-de-campanha";
import { CampanhasIndisponiveis, SoAdministradorCria } from "./estados";

function EsqueletoDoAssistente() {
  const rotulo = useTextos().campanhas.assistente.titulo;
  return (
    <main className="space-y-6 p-6" role="status" aria-label={rotulo}>
      <Skeleton className="h-9 w-64" />
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-80 w-full" />
    </main>
  );
}

/** Reabre um rascunho no assistente: o rascunho do servidor volta ao estado da tela. */
function ReabrirRascunho({ id, teto, limiteMeta }: { id: string; teto: number; limiteMeta: number }) {
  const textos = useTextos().campanhas;
  const roteador = useRouter();
  const consulta = useDetalheDaCampanha(id);
  const status = consulta.data?.campanha.status;

  useEffect(() => {
    if (status && status !== "RASCUNHO") roteador.replace(`/campanhas/${id}`);
  }, [status, id, roteador]);

  if (consulta.isError) {
    return (
      <main className="p-6">
        <ErroDeCarregamento mensagem={textos.assistente.erroCarregarRascunho} onTentarNovamente={() => consulta.refetch()} />
      </main>
    );
  }
  if (!consulta.data || status !== "RASCUNHO") return <EsqueletoDoAssistente />;
  return (
    <AssistenteDeCampanha
      inicial={estadoDoRascunho(consulta.data.campanha)}
      tetoDaInstancia={teto}
      limiteMeta={limiteMeta}
    />
  );
}

export function PaginaNovaCampanha() {
  const textos = useTextos().campanhas;
  const rascunho = useSearchParams().get("rascunho");
  const ehAdministrador = useEhAdministradorDeCampanhas();
  const configuracao = useConfiguracaoDeCampanhas();

  if (!ehAdministrador) return <SoAdministradorCria />;
  if (configuracao.isError && funcionalidadeIndisponivel(configuracao.error)) return <CampanhasIndisponiveis />;
  if (configuracao.isError) {
    return (
      <main className="p-6">
        <ErroDeCarregamento mensagem={textos.configuracaoDaInstancia.erro} onTentarNovamente={() => configuracao.refetch()} />
      </main>
    );
  }
  if (!configuracao.data) return <EsqueletoDoAssistente />;

  const { tetoDiarioDaInstancia: teto, limiteDiarioPadrao, limiteMetaInformado } = configuracao.data;
  if (rascunho) return <ReabrirRascunho id={rascunho} teto={teto} limiteMeta={limiteMetaInformado} />;
  return (
    <AssistenteDeCampanha
      inicial={estadoInicial(Math.min(limiteDiarioPadrao, teto))}
      tetoDaInstancia={teto}
      limiteMeta={limiteMetaInformado}
    />
  );
}
