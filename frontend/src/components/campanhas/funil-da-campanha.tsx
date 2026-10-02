"use client";

import { CheckCheck, Eye, Hourglass, MessageCircleReply, Send, type LucideIcon } from "lucide-react";

import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarNumero } from "@/lib/campanhas/formatacao";
import type { ContadoresDaCampanha } from "@/lib/campanhas/types";
import { percentual } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";

import { BarraDeProgresso } from "./barra-de-progresso";

/** Etapas do funil. Os contadores do backend são acumulados: lido também conta como entregue e enviado. */
export function etapasDoFunil(contadores: ContadoresDaCampanha) {
  const naFila = contadores.pendentes + Math.max(0, contadores.enfileirados - contadores.enviados);
  return {
    naFila,
    enviadas: contadores.enviados,
    entregues: contadores.entregues,
    lidas: contadores.lidos,
    responderam: contadores.respondidos,
  };
}

function Etapa({
  icone: Icone,
  rotulo,
  valor,
  total,
}: {
  icone: LucideIcon;
  rotulo: string;
  valor: number;
  total: number;
}) {
  const textos = useTextos().campanhas.detalhe.funil;
  const pct = percentual(valor, total);
  return (
    <li className="space-y-1.5 rounded-xl border border-border bg-card p-4 shadow-sm">
      <p className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
        <Icone className="size-4" aria-hidden />
        {rotulo}
      </p>
      <p className="text-2xl font-bold tabular-nums">{formatarNumero(valor)}</p>
      <BarraDeProgresso valor={pct} rotulo={`${rotulo}: ${interpolarCatalogo(textos.percentualDe, { percentual: String(pct) })}`} />
      <p className="text-xs text-muted-foreground">{interpolarCatalogo(textos.percentualDe, { percentual: String(pct) })}</p>
    </li>
  );
}

export function FunilDaCampanha({ contadores }: { contadores: ContadoresDaCampanha }) {
  const textos = useTextos().campanhas.detalhe.funil;
  const etapas = etapasDoFunil(contadores);
  const total = contadores.total;
  return (
    <section aria-label={textos.rotulo} className="space-y-3">
      <h2 className="text-sm font-bold">{textos.titulo}</h2>
      <ol className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <Etapa icone={Hourglass} rotulo={textos.naFila} valor={etapas.naFila} total={total} />
        <Etapa icone={Send} rotulo={textos.enviadas} valor={etapas.enviadas} total={total} />
        <Etapa icone={CheckCheck} rotulo={textos.entregues} valor={etapas.entregues} total={total} />
        <Etapa icone={Eye} rotulo={textos.lidas} valor={etapas.lidas} total={total} />
        <Etapa icone={MessageCircleReply} rotulo={textos.responderam} valor={etapas.responderam} total={total} />
      </ol>
      <p className="text-xs text-muted-foreground">
        {textos.falhas}: {formatarNumero(contadores.falhas)} · {textos.ignorados}: {formatarNumero(contadores.ignorados)} ·{" "}
        {textos.conferencia}: {formatarNumero(contadores.conferencia)}
      </p>
    </section>
  );
}
