"use client";

import { Button } from "@/components/ui/button";
import { PillDeStatus, type TomDePill } from "@/components/ui/pill-de-status";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarDia } from "@/lib/campanhas/formatacao";
import { useTextos } from "@/lib/config/textos-provider";
import type { OperacaoDeFinalizacao, StatusDaFinalizacaoEmMassa } from "@/lib/finalizacao-em-massa/types";

const TOM_DO_STATUS: Record<StatusDaFinalizacaoEmMassa, TomDePill> = {
  PENDENTE: "neutro",
  EM_ANDAMENTO: "info",
  CONCLUIDA: "sucesso",
};

interface Props {
  operacoes: OperacaoDeFinalizacao[];
  onAbrir: (id: string) => void;
}

/** E por aqui que se reabre uma operacao depois de fechar a janela: o servidor guarda quem pediu, filtros e contagens. */
export function OperacoesRecentes({ operacoes, onAbrir }: Props) {
  const textos = useTextos().atendimentos.finalizacaoEmMassa;
  if (operacoes.length === 0) return null;
  return (
    <section className="grid gap-1.5 border-t border-border pt-3">
      <h3 className="text-xs font-medium text-muted-foreground">{textos.recentes.titulo}</h3>
      <ul className="grid gap-1">
        {operacoes.slice(0, 5).map((operacao) => (
          <li key={operacao.id} className="flex items-center justify-between gap-2 text-sm">
            <span className="flex min-w-0 items-center gap-2">
              <PillDeStatus tom={TOM_DO_STATUS[operacao.status]}>{textos.recentes.status[operacao.status]}</PillDeStatus>
              <span className="truncate text-muted-foreground">
                {formatarDia(operacao.periodo.de)} – {formatarDia(operacao.periodo.ate)} ·{" "}
                {interpolarCatalogo(textos.recentes.linha, {
                  finalizados: String(operacao.finalizados),
                  encontrados: String(operacao.encontrados),
                })}
              </span>
            </span>
            <Button type="button" variant="ghost" size="sm" onClick={() => onAbrir(operacao.id)}>
              {textos.recentes.abrir}
            </Button>
          </li>
        ))}
      </ul>
    </section>
  );
}
