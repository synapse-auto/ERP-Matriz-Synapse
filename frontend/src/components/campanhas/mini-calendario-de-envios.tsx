"use client";

import { CalendarDays } from "lucide-react";

import { Skeleton } from "@/components/ui/skeleton";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { diaDaSemanaIso, formatarDia, formatarDiaCurto, formatarNumero } from "@/lib/campanhas/formatacao";
import type { ProjecaoDeEnvio } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

import { AvisoDeAtencao } from "./aviso";

export function EsqueletoDaProjecao() {
  const rotulo = useTextos().campanhas.passoRitmo.carregando;
  return (
    <div role="status" aria-label={rotulo} className="grid grid-cols-7 gap-1.5">
      {Array.from({ length: 14 }, (_, indice) => (
        <Skeleton key={indice} className="h-14" />
      ))}
    </div>
  );
}

/** Mini-calendário: uma célula por dia de envio, com as mensagens do dia e a barra contra o limite do dia. */
export function MiniCalendarioDeEnvios({ projecao }: { projecao: ProjecaoDeEnvio }) {
  const textos = useTextos().campanhas.passoRitmo;
  const dias = projecao.dias;
  if (dias.length === 0) return <p className="text-sm text-muted-foreground">{textos.semProjecao}</p>;

  // Alinha a primeira semana: células vazias antes do primeiro dia, para a coluna bater com o dia da semana.
  const deslocamento = diaDaSemanaIso(dias[0].dia) - 1;
  return (
    <section className="space-y-3" aria-label={textos.calendarioRotulo}>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
        <span className="flex items-center gap-1.5 font-bold">
          <CalendarDays className="size-4" aria-hidden />
          {projecao.terminoEstimado
            ? interpolarCatalogo(textos.terminaEm, { data: formatarDia(projecao.terminoEstimado) })
            : textos.semProjecao}
        </span>
        <span className="text-muted-foreground">
          {interpolarCatalogo(textos.diasNecessarios, { dias: String(dias.length) })}
        </span>
      </div>
      <ol className="grid grid-cols-7 gap-1.5 text-center text-xs">
        {Object.values(textos.diasCurtos).map((curto) => (
          <li key={curto} aria-hidden className="font-medium text-muted-foreground">
            {curto}
          </li>
        ))}
        {Array.from({ length: deslocamento }, (_, indice) => (
          <li key={`vazio-${indice}`} aria-hidden />
        ))}
        {dias.map((dia) => {
          const uso = dia.limiteDoDia > 0 ? Math.min(100, (dia.mensagens / dia.limiteDoDia) * 100) : 0;
          return (
            <li key={dia.dia} className="overflow-hidden rounded-md border border-border bg-card">
              <p className="px-1 pt-1 text-[0.65rem] text-muted-foreground">{formatarDiaCurto(dia.dia)}</p>
              <p className="px-1 font-bold tabular-nums">{interpolarCatalogo(textos.mensagensNoDia, { mensagens: formatarNumero(dia.mensagens) })}</p>
              <div className="mt-1 h-1 bg-muted" aria-hidden>
                <div className="h-full bg-primary" style={{ width: `${uso}%` }} />
              </div>
            </li>
          );
        })}
      </ol>
      {!projecao.completa && (
        <AvisoDeAtencao>{textos.estimativaIncompleta}</AvisoDeAtencao>
      )}
    </section>
  );
}
