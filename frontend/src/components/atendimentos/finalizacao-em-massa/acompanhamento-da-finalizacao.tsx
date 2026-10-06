"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { PillDeStatus } from "@/components/ui/pill-de-status";
import { Skeleton } from "@/components/ui/skeleton";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { useTextos } from "@/lib/config/textos-provider";
import { useItensDaFinalizacao, useOperacaoDeFinalizacao } from "@/lib/finalizacao-em-massa/hooks";
import type { StatusDoItemDeFinalizacao } from "@/lib/finalizacao-em-massa/types";

type AbaDeItens = Exclude<StatusDoItemDeFinalizacao, "PENDENTE">;
const ABAS: AbaDeItens[] = ["FINALIZADO", "IGNORADO", "FALHA"];

interface Props {
  operacaoId: string;
  onFechar: () => void;
  onNova: () => void;
}

/** Progresso enquanto roda (pode fechar e voltar) e, concluida, o resultado com o detalhe de cada item. */
export function AcompanhamentoDaFinalizacao({ operacaoId, onFechar, onNova }: Props) {
  const textos = useTextos().atendimentos.finalizacaoEmMassa;
  const consulta = useOperacaoDeFinalizacao(operacaoId);
  const operacao = consulta.data;
  const concluida = operacao?.status === "CONCLUIDA";
  const [aba, setAba] = useState<AbaDeItens>("FINALIZADO");
  const itens = useItensDaFinalizacao(operacaoId, aba, concluida);

  if (consulta.isError && !operacao) {
    return <ErroDeCarregamento mensagem={textos.andamento.erro} onTentarNovamente={() => consulta.refetch()} />;
  }
  if (!operacao) return <Skeleton className="h-24 w-full" />;

  if (!concluida) {
    const iniciada = operacao.processados > 0 || operacao.status === "EM_ANDAMENTO";
    return (
      <div className="grid gap-3">
        <div
          role="progressbar"
          aria-label={textos.andamento.titulo}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={operacao.percentual}
          className="h-2 w-full overflow-hidden rounded-full bg-muted"
        >
          <div className="h-full bg-primary transition-[width]" style={{ width: `${operacao.percentual}%` }} />
        </div>
        <p role="status" className="text-sm text-foreground">
          {iniciada
            ? interpolarCatalogo(textos.andamento.progresso, {
                processados: String(operacao.processados),
                encontrados: String(operacao.encontrados),
              })
            : textos.andamento.aguardando}
        </p>
        <p className="text-xs text-muted-foreground">{textos.andamento.segundoPlano}</p>
        <div className="flex justify-end">
          <Button type="button" variant="outline" onClick={onFechar}>
            {textos.fechar}
          </Button>
        </div>
      </div>
    );
  }

  const parcial = operacao.ignorados > 0 || operacao.falhas > 0;
  return (
    <div className="grid gap-3">
      <p role="status" className="text-sm font-medium text-foreground">
        {parcial ? textos.resultado.tituloParcial : textos.resultado.titulo}
      </p>
      <div className="flex flex-wrap gap-2">
        <PillDeStatus tom="neutro">
          {interpolarCatalogo(textos.resultado.encontrados, { n: String(operacao.encontrados) })}
        </PillDeStatus>
        <PillDeStatus tom="sucesso">
          {interpolarCatalogo(textos.resultado.finalizados, { n: String(operacao.finalizados) })}
        </PillDeStatus>
        {operacao.ignorados > 0 && (
          <PillDeStatus tom="atencao">
            {interpolarCatalogo(textos.resultado.ignorados, { n: String(operacao.ignorados) })}
          </PillDeStatus>
        )}
        {operacao.falhas > 0 && (
          <PillDeStatus tom="erro">
            {interpolarCatalogo(textos.resultado.falhas, { n: String(operacao.falhas) })}
          </PillDeStatus>
        )}
      </div>

      <div role="tablist" aria-label={textos.resultado.detalhes} className="flex gap-1 border-b border-border">
        {ABAS.map((valor) => (
          <button
            key={valor}
            type="button"
            role="tab"
            aria-selected={aba === valor}
            className={`px-3 py-1.5 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring ${
              aba === valor ? "border-b-2 border-primary font-medium text-foreground" : "text-muted-foreground"
            }`}
            onClick={() => setAba(valor)}
          >
            {textos.resultado.abas[valor]}
          </button>
        ))}
      </div>
      <div role="tabpanel" className="max-h-56 overflow-y-auto">
        {itens.isLoading ? (
          <Skeleton className="h-16 w-full" />
        ) : itens.isError ? (
          <ErroDeCarregamento mensagem={textos.resultado.erroItens} onTentarNovamente={() => itens.refetch()} />
        ) : itens.data && itens.data.itens.length > 0 ? (
          <ul className="grid gap-1.5 text-sm">
            {itens.data.itens.map((item) => (
              <li key={item.atendimentoId} className="flex flex-wrap items-baseline justify-between gap-x-3">
                <span className="truncate">{item.leadNome ?? textos.resultado.leadSemNome}</span>
                <span className="text-xs text-muted-foreground">
                  {item.atendenteNome}
                  {item.motivo ? ` · ${textos.motivos[item.motivo]}` : ""}
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted-foreground">{textos.resultado.semItens}</p>
        )}
      </div>

      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onNova}>
          {textos.resultado.nova}
        </Button>
        <Button type="button" onClick={onFechar}>
          {textos.fechar}
        </Button>
      </div>
    </div>
  );
}
