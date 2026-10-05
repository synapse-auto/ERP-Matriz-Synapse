"use client";

import { useMemo, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { useQuantidadeAtendimentosFinalizaveis } from "@/lib/atendimento/use-transferir-finalizar";
import { formatarDia } from "@/lib/campanhas/formatacao";
import { useTextos } from "@/lib/config/textos-provider";
import { listaDeNomes } from "@/lib/finalizacao-em-massa/aviso";
import { useIniciarFinalizacao, useOperacoesDeFinalizacao } from "@/lib/finalizacao-em-massa/hooks";
import { operacaoEstaAtiva, type FiltroDeFinalizacao } from "@/lib/finalizacao-em-massa/types";
import { novaChaveDeIdempotencia } from "@/lib/finalizacao-em-massa/validacao";

import { AcompanhamentoDaFinalizacao } from "./acompanhamento-da-finalizacao";
import { FormularioDeFinalizacao } from "./formulario-de-finalizacao";
import { OperacoesRecentes } from "./operacoes-recentes";

const FILTRO_VAZIO: FiltroDeFinalizacao = { atendenteIds: [], de: "", ate: "", horaInicio: null, horaFim: null };

type Etapa = "filtros" | "confirmar";

interface Props {
  aberta: boolean;
  onFechar: () => void;
}

/**
 * Janela da finalizacao em massa. Tres momentos: filtros com previa, confirmacao explicita e acompanhamento/resultado.
 * Quem fecha a janela nao interrompe nada: a operacao roda no servidor e volta a aparecer ao reabrir (ativa) ou em
 * "Operacoes recentes". A tela so mostra; permissao, visibilidade e limites sao do servidor.
 */
export function JanelaFinalizacaoEmMassa({ aberta, onFechar }: Props) {
  const textos = useTextos().atendimentos.finalizacaoEmMassa;
  const atendentesDisponiveis = useQuantidadeAtendimentosFinalizaveis(aberta);
  const recentes = useOperacoesDeFinalizacao(aberta);
  const iniciar = useIniciarFinalizacao();

  const [filtro, setFiltro] = useState<FiltroDeFinalizacao>(FILTRO_VAZIO);
  const [etapa, setEtapa] = useState<Etapa>("filtros");
  const [totalPrevisto, setTotalPrevisto] = useState(0);
  const [chave, setChave] = useState<string | null>(null);
  const [operacaoEscolhida, setOperacaoEscolhida] = useState<string | null>(null);
  const [dispensouAtiva, setDispensouAtiva] = useState(false);
  // Trava sincrona: dois cliques no mesmo frame passam antes de `isPending` virar true.
  const enviando = useRef(false);

  const atendentes = useMemo(
    () => (atendentesDisponiveis.data?.porAtendente ?? []).map((item) => ({ id: item.atendenteId, nome: item.nome })),
    [atendentesDisponiveis.data],
  );
  const operacaoAtiva = dispensouAtiva ? undefined : recentes.data?.find(operacaoEstaAtiva);
  const operacaoAcompanhada = operacaoEscolhida ?? operacaoAtiva?.id ?? null;

  function reiniciar() {
    setFiltro(FILTRO_VAZIO);
    setEtapa("filtros");
    setChave(null);
    setOperacaoEscolhida(null);
    iniciar.reset();
  }

  function fechar() {
    reiniciar();
    setDispensouAtiva(false);
    onFechar();
  }

  function revisar(total: number) {
    setTotalPrevisto(total);
    setChave(novaChaveDeIdempotencia());
    iniciar.reset();
    setEtapa("confirmar");
  }

  function confirmar() {
    if (enviando.current || chave === null) return;
    enviando.current = true;
    iniciar.mutate(
      { filtro, chave },
      {
        onSuccess: (operacao) => {
          setOperacaoEscolhida(operacao.id);
          setEtapa("filtros");
        },
        onSettled: () => {
          enviando.current = false;
        },
      },
    );
  }

  function novaFinalizacao() {
    setDispensouAtiva(true);
    reiniciar();
  }

  const titulo = operacaoAcompanhada
    ? textos.andamento.titulo
    : etapa === "confirmar"
      ? textos.confirmacao.titulo
      : textos.titulo;
  const nomesEscolhidos = atendentes.filter((item) => filtro.atendenteIds.includes(item.id)).map((item) => item.nome);

  return (
    <Dialog open={aberta} onOpenChange={(novo) => !novo && fechar()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{titulo}</DialogTitle>
          {!operacaoAcompanhada && etapa === "filtros" && <DialogDescription>{textos.descricao}</DialogDescription>}
        </DialogHeader>

        {operacaoAcompanhada ? (
          <AcompanhamentoDaFinalizacao operacaoId={operacaoAcompanhada} onFechar={fechar} onNova={novaFinalizacao} />
        ) : etapa === "confirmar" ? (
          <div className="grid gap-3">
            <p
              role="alert"
              className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
            >
              {textos.confirmacao.aviso}
            </p>
            <p className="text-sm text-foreground">
              {interpolarCatalogo(textos.confirmacao.resumo, {
                total: String(totalPrevisto),
                atendentes: listaDeNomes(nomesEscolhidos),
                de: formatarDia(filtro.de),
                ate: formatarDia(filtro.ate),
              })}
            </p>
            {iniciar.isError && (
              <p role="alert" className="text-sm text-destructive">
                {iniciar.error instanceof Error && iniciar.error.message
                  ? iniciar.error.message
                  : textos.confirmacao.erro}
              </p>
            )}
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" disabled={iniciar.isPending} onClick={() => setEtapa("filtros")}>
                {textos.confirmacao.voltar}
              </Button>
              <Button type="button" variant="destructive" disabled={iniciar.isPending} onClick={confirmar}>
                {iniciar.isPending
                  ? textos.confirmacao.iniciando
                  : interpolarCatalogo(textos.confirmacao.confirmar, { total: String(totalPrevisto) })}
              </Button>
            </div>
          </div>
        ) : (
          <>
            <FormularioDeFinalizacao
              filtro={filtro}
              onFiltroAlterado={setFiltro}
              atendentes={atendentes}
              carregandoAtendentes={atendentesDisponiveis.isLoading}
              erroDeAtendentes={atendentesDisponiveis.isError}
              onRecarregarAtendentes={() => void atendentesDisponiveis.refetch()}
              onRevisar={revisar}
              onFechar={fechar}
            />
            <OperacoesRecentes operacoes={recentes.data ?? []} onAbrir={setOperacaoEscolhida} />
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
