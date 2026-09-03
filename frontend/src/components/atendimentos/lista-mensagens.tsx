"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { useVirtualizer } from "@tanstack/react-virtual";
import { Search, ShieldCheck } from "lucide-react";

import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useTextos } from "@/lib/config/textos-provider";
import type { MensagemResposta } from "@/lib/atendimento/types";

import { BolhaMensagem } from "./bolha-mensagem";

type Props = {
  mensagens: MensagemResposta[];
  carregando: boolean;
  onReenviar: (mensagem: MensagemResposta) => void;
  onDefinirReacao: (mensagem: MensagemResposta, emoji: string) => Promise<void>;
  onRemoverReacao: (mensagem: MensagemResposta) => Promise<void>;
  temMais: boolean;
  carregandoMais: boolean;
  onCarregarMais: () => void;
  buscaAberta: boolean;
  canalTipo: string | null;
  atendenteId: string | null;
  atendenteNome: string | null;
  leadId?: string;
  onResponder?: (mensagem: MensagemResposta) => void;
  onEncaminhar?: (mensagem: MensagemResposta) => void;
};

/** Espaço real dentro do virtualizador para nenhuma mensagem encostar no cabeçalho ou composer. */
export const ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO = 16;

/**
 * A lista virtualizada recebe paginas por cursor e busca as anteriores ao chegar ao topo. Mensagens
 * novas entram no fim sem alterar o cursor que ancora o historico ja percorrido.
 */
export function ListaMensagens({
  mensagens,
  carregando,
  onReenviar,
  onDefinirReacao,
  onRemoverReacao,
  temMais,
  carregandoMais,
  onCarregarMais,
  buscaAberta,
  canalTipo,
  atendenteId,
  atendenteNome,
  leadId,
  onResponder,
  onEncaminhar,
}: Props) {
  const textos = useTextos();
  const [busca, setBusca] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);

  const filtradas = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    if (!termo) {
      return mensagens;
    }
    return mensagens.filter((mensagem) =>
      mensagem.conteudo?.toLowerCase().includes(termo),
    );
  }, [mensagens, busca]);

  const virtualizador = useVirtualizer({
    count: filtradas.length,
    getScrollElement: () => containerRef.current,
    getItemKey: (indice) => chaveDaMensagem(filtradas, indice),
    estimateSize: () => 48,
    overscan: 8,
    paddingStart: ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
    paddingEnd: ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
    scrollPaddingStart: ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
    scrollPaddingEnd: ESPACAMENTO_DE_SEGURANCA_DO_HISTORICO,
  });

  const ultimoId = filtradas.at(-1)?.id;
  useEffect(() => {
    if (filtradas.length > 0) {
      virtualizador.scrollToIndex(filtradas.length - 1, { align: "end" });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- só ao entrar novas mensagens no fim, não a cada resize do virtualizador
  }, [ultimoId]);

  return (
    <div className="flex min-h-0 flex-1 flex-col" data-slot="lista-mensagens">
      {buscaAberta && (
        <div className="border-b border-border p-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2 top-1/2 size-[calc(var(--tamanho-icone-interface)*0.875)] -translate-y-1/2 text-muted-foreground" />
            <Input
              value={busca}
              onChange={(evento) => setBusca(evento.target.value)}
              placeholder={textos.atendimentos.filtros.busca}
              className="pl-7"
            />
          </div>
        </div>
      )}

      <div
        ref={containerRef}
        className="min-h-0 flex-1 overflow-y-auto overscroll-contain scroll-py-4 [scrollbar-width:thin] [scrollbar-color:var(--border)_transparent] px-4 py-2"
        data-slot="historico-mensagens"
        style={{
          // O composer flutua sobre a lista; reservar sua altura mantém a última bolha alcançável.
          paddingBottom: "calc(0.5rem + var(--altura-composer, 0px))",
          scrollPaddingBottom: "calc(1rem + var(--altura-composer, 0px))",
        }}
        onScroll={(evento) => {
          if (evento.currentTarget.scrollTop < 80 && temMais && !carregandoMais)
            onCarregarMais();
        }}
      >
        {carregando ? (
          <div className="space-y-3 py-2">
            {Array.from({ length: 6 }).map((_, indice) => (
              <Skeleton key={indice} className="h-12 w-2/3" />
            ))}
          </div>
        ) : filtradas.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {textos.estados.vazio}
          </p>
        ) : (
          <>
            {temMais && (
              <div className="flex justify-center py-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={onCarregarMais}
                  disabled={carregandoMais}
                >
                  {carregandoMais
                    ? textos.atendimentos.mensagem.carregandoAnteriores
                    : textos.atendimentos.mensagem.carregarAnteriores}
                </Button>
              </div>
            )}
            <div
              style={{
                height: virtualizador.getTotalSize(),
                position: "relative",
              }}
            >
              {virtualizador.getVirtualItems().map((item) => {
                const mensagem = filtradas[item.index];
                const anterior = filtradas[item.index - 1];
                const mostrarData =
                  !anterior ||
                  diaDaMensagem(anterior.enviadoEm) !==
                    diaDaMensagem(mensagem.enviadoEm);
                const mudouAtendimento = mudouDeAtendimento(anterior, mensagem);
                const nomeDoRemetente = nomeDaAutoria(
                  mensagem,
                  atendenteId,
                  atendenteNome,
                  textos.atendimentos.mensagem.ia,
                );
                return (
                  <div
                    key={mensagem.id}
                    data-index={item.index}
                    ref={virtualizador.measureElement}
                    style={{
                      position: "absolute",
                      top: 0,
                      left: 0,
                      width: "100%",
                      transform: `translateY(${item.start}px)`,
                    }}
                    className="py-1"
                  >
                    {mostrarData && (
                      <SeparadorDeData enviadoEm={mensagem.enviadoEm} />
                    )}
                    {(item.index === 0 || mudouAtendimento) && (
                      <LinhaDeInicio
                        canalTipo={canalTipo}
                        atendenteNome={
                          mensagem.atendimentoResponsavelNome ??
                          (item.index === 0 ? atendenteNome : null)
                        }
                        troca={mudouAtendimento}
                      />
                    )}
                    <BolhaMensagem
                      mensagem={mensagem}
                      leadId={leadId}
                      nomeDoRemetente={nomeDoRemetente}
                      onReenviar={
                        mensagem.statusEntrega === "FALHOU"
                          ? () => onReenviar(mensagem)
                          : undefined
                      }
                      onDefinirReacao={(emoji) => onDefinirReacao(mensagem, emoji)}
                      onRemoverReacao={() => onRemoverReacao(mensagem)}
                      onResponder={onResponder ? () => onResponder(mensagem) : undefined}
                      onEncaminhar={onEncaminhar ? () => onEncaminhar(mensagem) : undefined}
                    />
                  </div>
                );
              })}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

/** A altura medida acompanha a mensagem, mesmo quando uma página antiga entra no topo da lista. */
export function chaveDaMensagem(mensagens: MensagemResposta[], indice: number): string | number {
  return mensagens[indice]?.id ?? indice;
}

/** O marcador aparece apenas quando a ordem cronologica cruza a fronteira de dois atendimentos. */
export function mudouDeAtendimento(
  anterior: MensagemResposta | undefined,
  atual: MensagemResposta,
): boolean {
  return Boolean(anterior?.atendimentoId)
    && Boolean(atual.atendimentoId)
    && anterior?.atendimentoId !== atual.atendimentoId;
}

/** O dado persistido prevalece; o responsavel atual e so fallback para eventos em tempo real. */
export function nomeDaAutoria(
  mensagem: MensagemResposta,
  atendenteId: string | null,
  atendenteNome: string | null,
  rotuloIa: string | null = null,
): string | null {
  if (mensagem.remetenteTipo === "IA") return rotuloIa;
  if (mensagem.remetenteTipo !== "ATENDENTE") return null;
  return (
    mensagem.remetenteNome ??
    (mensagem.remetenteId && mensagem.remetenteId === atendenteId
      ? atendenteNome
      : null)
  );
}

function SeparadorDeData({ enviadoEm }: { enviadoEm: string }) {
  const textos = useTextos().atendimentos.mensagem;
  return (
    <div className="mb-3 flex justify-center">
      <span className="rounded-full bg-muted px-3 py-1 text-xs font-semibold text-muted-foreground">
        {rotuloDaData(enviadoEm, textos.hoje, textos.ontem)}
      </span>
    </div>
  );
}

function LinhaDeInicio({
  canalTipo,
  atendenteNome,
  troca = false,
}: {
  canalTipo: string | null;
  atendenteNome: string | null;
  troca?: boolean;
}) {
  const textos = useTextos().atendimentos;
  const canal = canalTipo === "WHATSAPP" ? textos.canais.whatsapp : canalTipo;
  const partes: Array<string | null> = [
    troca ? textos.mensagem.trocaAtendimento : textos.mensagem.atendimentoRecebido,
    canal,
  ];
  if (atendenteNome) {
    partes.push(
      textos.mensagem.responsavelAtual.replace("{nome}", atendenteNome),
    );
  }
  return (
    <p
      className="mb-3 inline-flex w-full items-center justify-center gap-1.5 text-xs text-muted-foreground"
      data-slot="linha-atendimento-recebido"
      data-troca-atendimento={troca ? "true" : undefined}
    >
      <ShieldCheck className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
      {partes.filter(Boolean).join(" · ")}
    </p>
  );
}

function diaDaMensagem(valor: string): string {
  const data = new Date(valor);
  return `${data.getFullYear()}-${data.getMonth()}-${data.getDate()}`;
}

export function rotuloDaData(
  valor: string,
  hoje: string,
  ontem: string,
): string {
  const data = new Date(valor);
  const agora = new Date();
  const inicioHoje = new Date(
    agora.getFullYear(),
    agora.getMonth(),
    agora.getDate(),
  );
  const inicioDaData = new Date(
    data.getFullYear(),
    data.getMonth(),
    data.getDate(),
  );
  const inicioOntem = new Date(inicioHoje);
  inicioOntem.setDate(inicioHoje.getDate() - 1);
  if (inicioDaData.getTime() === inicioHoje.getTime()) return hoje;
  if (inicioDaData.getTime() === inicioOntem.getTime()) return ontem;
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "long",
    year: data.getFullYear() === agora.getFullYear() ? undefined : "numeric",
  }).format(data);
}
