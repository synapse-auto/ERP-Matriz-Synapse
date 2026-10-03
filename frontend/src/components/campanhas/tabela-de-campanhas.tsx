"use client";

import Link from "next/link";
import { ArrowRight, PencilLine } from "lucide-react";

import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarDataHora, formatarNumero } from "@/lib/campanhas/formatacao";
import { usePodeEmCampanhas } from "@/lib/campanhas/hooks";
import type { Campanha } from "@/lib/campanhas/types";
import { percentual } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";

import { BarraDeProgresso } from "./barra-de-progresso";
import { ChipDeStatus } from "./chip-de-status";

/** Esqueleto no formato real: cabeçalho e cinco linhas com a altura das linhas da tabela. */
export function EsqueletoDaTabela() {
  return (
    <div className="space-y-2 rounded-xl border border-border bg-card p-4 shadow-sm" aria-hidden>
      <Skeleton className="h-5 w-40" />
      {Array.from({ length: 5 }, (_, indice) => (
        <Skeleton key={indice} className="h-12 w-full" />
      ))}
    </div>
  );
}

export function destinoDaCampanha(
  campanha: Campanha,
  podeEditar: boolean,
): { href: string; rotulo: "abrir" | "continuarRascunho" } {
  return campanha.status === "RASCUNHO" && podeEditar
    ? { href: `/campanhas/nova?rascunho=${campanha.id}`, rotulo: "continuarRascunho" }
    : { href: `/campanhas/${campanha.id}`, rotulo: "abrir" };
}

function Inicio({ campanha }: { campanha: Campanha }) {
  const textos = useTextos().campanhas.lista;
  if (campanha.iniciadaEm) return <>{formatarDataHora(campanha.iniciadaEm)}</>;
  if (campanha.agendadaPara) {
    return <>{interpolarCatalogo(textos.agendadaPara, { data: formatarDataHora(campanha.agendadaPara) })}</>;
  }
  return <>{textos.naoIniciada}</>;
}

function Progresso({ campanha }: { campanha: Campanha }) {
  const textos = useTextos().campanhas.lista;
  // Funil acumulado: entregue e lido também contam como enviado.
  const { total, enviados: saiu } = campanha.contadores;
  const percentualEnviado = percentual(saiu, total);
  return (
    <div className="min-w-28 space-y-1">
      <BarraDeProgresso
        valor={percentualEnviado}
        tom={campanha.status === "CONCLUIDA" ? "sucesso" : "primario"}
        rotulo={interpolarCatalogo(textos.progressoDe, { percentual: String(percentualEnviado) })}
      />
      <p className="text-xs tabular-nums text-muted-foreground">
        {formatarNumero(saiu)} / {formatarNumero(total)}
      </p>
    </div>
  );
}

function Linha({ campanha }: { campanha: Campanha }) {
  const textos = useTextos().campanhas.lista;
  const destino = destinoDaCampanha(campanha, usePodeEmCampanhas("editar"));
  const Icone = destino.rotulo === "continuarRascunho" ? PencilLine : ArrowRight;
  return (
    <tr className="transition-colors hover:bg-muted/40">
      <td className="max-w-56 px-4 py-3 font-medium">
        <Link href={destino.href} className="line-clamp-2 hover:underline focus-visible:underline">
          {campanha.nome}
        </Link>
      </td>
      <td className="px-4 py-3 text-muted-foreground">{campanha.template.nome}</td>
      <td className="px-4 py-3">
        <ChipDeStatus status={campanha.status} />
      </td>
      <td className="px-4 py-3">
        <Progresso campanha={campanha} />
      </td>
      <td className="px-4 py-3 tabular-nums">
        {interpolarCatalogo(textos.porDia, { limite: formatarNumero(campanha.limiteDiario) })}
      </td>
      <td className="px-4 py-3 text-muted-foreground">
        <Inicio campanha={campanha} />
      </td>
      <td className="px-4 py-3 text-right">
        <Link href={destino.href} className={buttonVariants({ variant: "outline", size: "sm" })}>
          {textos[destino.rotulo]}
          <Icone aria-hidden />
        </Link>
      </td>
    </tr>
  );
}

export function TabelaDeCampanhas({ campanhas }: { campanhas: Campanha[] }) {
  const textos = useTextos().campanhas.lista;
  const colunas = textos.colunas;
  const titulos = [colunas.nome, colunas.template, colunas.status, colunas.progresso, colunas.limite, colunas.inicio];
  return (
    <div className="hidden overflow-x-auto rounded-xl border border-border bg-card shadow-sm md:block">
      <table className="w-full min-w-176 text-left text-sm">
        <caption className="sr-only">{textos.titulo}</caption>
        <thead className="border-b border-border bg-muted/50 text-xs text-muted-foreground">
          <tr>
            {titulos.map((titulo) => (
              <th key={titulo} scope="col" className="px-4 py-3 font-medium">
                {titulo}
              </th>
            ))}
            <th scope="col" className="px-4 py-3 text-right font-medium">
              {colunas.acoes}
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {campanhas.map((campanha) => (
            <Linha key={campanha.id} campanha={campanha} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
