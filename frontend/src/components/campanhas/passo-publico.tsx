"use client";

import { Users } from "lucide-react";

import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import {
  filtroSemVazios,
  temFiltro,
  type EstadoDoAssistente,
  type FiltroEditavel,
} from "@/lib/campanhas/estado-do-assistente";
import { usePreviaDoPublico, useValorComAtraso } from "@/lib/campanhas/hooks";
import { useTextos } from "@/lib/config/textos-provider";

import { FiltrosDoPublico } from "./filtros-do-publico";
import { EsqueletoDoResumo, ResumoDoPublico } from "./resumo-do-publico";

interface Props {
  estado: EstadoDoAssistente;
  aoMudar: (mudanca: Partial<EstadoDoAssistente>) => void;
}

export function PassoPublico({ estado, aoMudar }: Props) {
  const textos = useTextos().campanhas.passoPublico;
  const filtroAtrasado = useValorComAtraso(estado.filtro);
  const filtroDaApi = filtroSemVazios(filtroAtrasado);
  const previa = usePreviaDoPublico(filtroDaApi);

  return (
    <div className="space-y-6">
      <div className="flex items-start gap-3 rounded-xl border border-border bg-card p-4 shadow-sm">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-accent text-accent-foreground">
          <Users className="size-5" aria-hidden />
        </span>
        <div>
          <h2 className="text-sm font-bold">{temFiltro(estado.filtro) ? textos.titulo : textos.agendaInteira}</h2>
          <p className="text-sm text-muted-foreground">{textos.agendaInteiraAjuda}</p>
        </div>
      </div>
      <FiltrosDoPublico filtro={estado.filtro} aoMudar={(filtro: FiltroEditavel) => aoMudar({ filtro })} />
      {previa.isPending && <EsqueletoDoResumo />}
      {previa.isError && !previa.data && (
        <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => previa.refetch()} />
      )}
      {previa.data && <ResumoDoPublico previa={previa.data} filtro={filtroDaApi} />}
    </div>
  );
}
