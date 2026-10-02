"use client";

import Link from "next/link";
import { ArrowRight, PencilLine } from "lucide-react";

import { buttonVariants } from "@/components/ui/button";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarDataHora, formatarNumero } from "@/lib/campanhas/formatacao";
import { useEhAdministradorDeCampanhas } from "@/lib/campanhas/hooks";
import type { Campanha } from "@/lib/campanhas/types";
import { percentual } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";

import { BarraDeProgresso } from "./barra-de-progresso";
import { ChipDeStatus } from "./chip-de-status";
import { destinoDaCampanha } from "./tabela-de-campanhas";

/** Campanha como cartão: no celular a tabela de 7 colunas não cabe, então cada linha vira um cartão. */
export function CartaoDeCampanha({ campanha }: { campanha: Campanha }) {
  const textos = useTextos().campanhas.lista;
  const destino = destinoDaCampanha(campanha, useEhAdministradorDeCampanhas());
  const Icone = destino.rotulo === "continuarRascunho" ? PencilLine : ArrowRight;
  const { total, enviados } = campanha.contadores;
  const pct = percentual(enviados, total);
  const inicio = campanha.iniciadaEm
    ? formatarDataHora(campanha.iniciadaEm)
    : campanha.agendadaPara
      ? interpolarCatalogo(textos.agendadaPara, { data: formatarDataHora(campanha.agendadaPara) })
      : textos.naoIniciada;
  return (
    <li className="space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <Link href={destino.href} className="font-bold hover:underline focus-visible:underline">
          {campanha.nome}
        </Link>
        <ChipDeStatus status={campanha.status} />
      </div>
      <p className="text-xs text-muted-foreground">
        {campanha.template.nome} · {interpolarCatalogo(textos.porDia, { limite: formatarNumero(campanha.limiteDiario) })}
      </p>
      <div className="space-y-1">
        <BarraDeProgresso
          valor={pct}
          tom={campanha.status === "CONCLUIDA" ? "sucesso" : "primario"}
          rotulo={interpolarCatalogo(textos.progressoDe, { percentual: String(pct) })}
        />
        <p className="text-xs tabular-nums text-muted-foreground">
          {formatarNumero(enviados)} / {formatarNumero(total)}
        </p>
      </div>
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs text-muted-foreground">{inicio}</p>
        <Link href={destino.href} className={buttonVariants({ variant: "outline", size: "sm" })}>
          {textos[destino.rotulo]}
          <Icone aria-hidden />
        </Link>
      </div>
    </li>
  );
}
