"use client";

import { CheckCheck, Eye, MessageCircleReply, Send, TriangleAlert, type LucideIcon } from "lucide-react";

import { Skeleton } from "@/components/ui/skeleton";
import { formatarNumero } from "@/lib/campanhas/formatacao";
import type { ListaDeCampanhas } from "@/lib/campanhas/types";
import { percentual } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";

import { BarraDeProgresso } from "./barra-de-progresso";

function Indicador({ icone: Icone, rotulo, valor }: { icone: LucideIcon; rotulo: string; valor: number }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
        <Icone className="size-4" aria-hidden />
        {rotulo}
      </div>
      <p className="mt-1 text-2xl font-bold tabular-nums">{formatarNumero(valor)}</p>
    </div>
  );
}

/** Esqueleto no formato real da tira: um cartão largo (limite) e quatro indicadores. */
export function EsqueletoDaTira() {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6" aria-hidden>
      <Skeleton className="h-24 sm:col-span-2" />
      {Array.from({ length: 4 }, (_, indice) => (
        <Skeleton key={indice} className="h-24" />
      ))}
    </div>
  );
}

export function TiraDeIndicadores({ lista }: { lista: ListaDeCampanhas }) {
  const textos = useTextos().campanhas.indicadores;
  const uso = percentual(lista.enfileiradasHoje, lista.tetoDiario);
  const perto = uso >= 90;
  return (
    <section aria-label={textos.enviadasHoje} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
      <div className="rounded-xl border border-border bg-card p-4 shadow-sm sm:col-span-2">
        <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
          <Send className="size-4" aria-hidden />
          {textos.enviadasHoje} · {textos.limiteDiario}
        </div>
        <p className="mt-1 text-2xl font-bold tabular-nums">
          {formatarNumero(lista.enfileiradasHoje)}{" "}
          <span className="text-sm font-medium text-muted-foreground">
            {interpolarCatalogo(textos.deLimite, { limite: formatarNumero(lista.tetoDiario) })}
          </span>
        </p>
        <BarraDeProgresso
          className="mt-2"
          valor={uso}
          tom={perto ? "atencao" : "primario"}
          rotulo={interpolarCatalogo(textos.rotuloBarra, {
            enviadas: formatarNumero(lista.enfileiradasHoje),
            limite: formatarNumero(lista.tetoDiario),
          })}
        />
        {lista.limiteMetaInformado > 0 && (
          <p className="mt-2 text-xs text-muted-foreground">
            {interpolarCatalogo(textos.metaInformado, { limite: formatarNumero(lista.limiteMetaInformado) })}
          </p>
        )}
      </div>
      <Indicador icone={CheckCheck} rotulo={textos.entregues} valor={lista.indicadores.entregues} />
      <Indicador icone={Eye} rotulo={textos.lidas} valor={lista.indicadores.lidas} />
      <Indicador icone={MessageCircleReply} rotulo={textos.respostas} valor={lista.indicadores.respondidas} />
      <Indicador icone={TriangleAlert} rotulo={textos.falhas} valor={lista.indicadores.falhas} />
    </section>
  );
}
