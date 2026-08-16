"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { useVirtualizer } from "@tanstack/react-virtual";
import { Search } from "lucide-react";

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
  temMais: boolean;
  carregandoMais: boolean;
  onCarregarMais: () => void;
  buscaAberta: boolean;
};

/**
 * A lista virtualizada recebe paginas por cursor e busca as anteriores ao chegar ao topo. Mensagens
 * novas entram no fim sem alterar o cursor que ancora o historico ja percorrido.
 */
export function ListaMensagens({
  mensagens,
  carregando,
  onReenviar,
  temMais,
  carregandoMais,
  onCarregarMais,
  buscaAberta,
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
    estimateSize: () => 64,
    overscan: 8,
  });

  const ultimoId = filtradas.at(-1)?.id;
  useEffect(() => {
    if (filtradas.length > 0) {
      virtualizador.scrollToIndex(filtradas.length - 1, { align: "end" });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- só ao entrar novas mensagens no fim, não a cada resize do virtualizador
  }, [ultimoId]);

  return (
    <div className="flex h-full min-h-0 flex-col">
      {buscaAberta && (
        <div className="border-b border-border p-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
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
        className="flex-1 overflow-y-auto px-4 py-2"
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
                    <BolhaMensagem
                      mensagem={mensagem}
                      onReenviar={
                        mensagem.statusEntrega === "FALHOU"
                          ? () => onReenviar(mensagem)
                          : undefined
                      }
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
