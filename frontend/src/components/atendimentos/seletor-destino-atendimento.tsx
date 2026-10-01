"use client";

import { useMemo, useState } from "react";
import { ArrowLeft, Check, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { DestinoDeTransferencia } from "@/lib/atendimento/types";
import { cn } from "@/lib/utils";

type Textos = {
  outros: string;
  voltar: string;
};

type Props = {
  destinos: DestinoDeTransferencia[];
  excluidos?: Set<string>;
  desabilitado: boolean;
  aberto: boolean;
  textos: Textos;
  onSelecionar: (destino: DestinoDeTransferencia) => void;
  /**
   * Quando informado, o seletor só marca o destino escolhido (aria-pressed) e quem o usa confirma
   * depois. A transferência não passa este valor e continua agindo no clique.
   */
  selecionadoId?: string | null;
};

/**
 * Seletor comum de destinos para transferência e convite. Só a apresentação é compartilhada: quem
 * pode receber cada ação é decidido por quem monta a lista (`destinos` e `excluidos`).
 *
 * O campo papel é a única fonte para separar a lista. Respostas antigas sem esse campo ficam na
 * lista principal para preservar a compatibilidade sem tentar inferir o papel pelo nome ou ordem.
 */
export function SeletorDestinoAtendimento({
  destinos,
  excluidos = new Set(),
  desabilitado,
  aberto,
  textos,
  onSelecionar,
  selecionadoId,
}: Props) {
  const [secao, setSecao] = useState<"principal" | "outros">("principal");

  const unicos = useMemo(() => {
    const vistos = new Set<string>();
    return destinos.filter((destino) => {
      if (excluidos.has(destino.id) || vistos.has(destino.id)) return false;
      vistos.add(destino.id);
      return true;
    });
  }, [destinos, excluidos]);

  const principais = unicos.filter((destino) => destino.papel !== "SUBGESTOR");
  const outros = unicos.filter((destino) => destino.papel === "SUBGESTOR");
  const botao = (destino: DestinoDeTransferencia) => (
    <BotaoDestino
      key={destino.id}
      destino={destino}
      desabilitado={desabilitado}
      selecionavel={selecionadoId !== undefined}
      selecionado={selecionadoId === destino.id}
      onSelecionar={onSelecionar}
    />
  );

  if (aberto && secao === "outros" && outros.length > 0) {
    return (
      <div data-testid="destinos-outros-lista" className="space-y-1">
        <Button
          type="button"
          variant="ghost"
          className="w-full justify-start"
          onClick={() => setSecao("principal")}
          disabled={desabilitado}
        >
          <ArrowLeft aria-hidden />
          {textos.voltar}
        </Button>
        {outros.map(botao)}
      </div>
    );
  }

  return (
    <div data-testid="destinos-principais-lista" className="space-y-1">
      {principais.map(botao)}
      {outros.length > 0 && (
        <Button
          type="button"
          variant="outline"
          className="w-full justify-between"
          disabled={desabilitado}
          onClick={() => setSecao("outros")}
          data-testid="destinos-outros"
        >
          <span>{textos.outros}</span>
          <ChevronRight aria-hidden />
        </Button>
      )}
    </div>
  );
}

function BotaoDestino({
  destino,
  desabilitado,
  selecionavel,
  selecionado,
  onSelecionar,
}: {
  destino: DestinoDeTransferencia;
  desabilitado: boolean;
  selecionavel: boolean;
  selecionado: boolean;
  onSelecionar: (destino: DestinoDeTransferencia) => void;
}) {
  return (
    <Button
      type="button"
      variant="outline"
      // Nome longo não pode empurrar o botão para fora do modal nem ser cortado no meio da palavra.
      className={cn("w-full min-w-0 justify-start", selecionado && "border-primary bg-primary/10 text-primary")}
      disabled={desabilitado}
      aria-pressed={selecionavel ? selecionado : undefined}
      title={destino.nome}
      onClick={() => onSelecionar(destino)}
    >
      <span className="min-w-0 flex-1 truncate text-left">{destino.nome}</span>
      {selecionado && <Check aria-hidden className="shrink-0" />}
    </Button>
  );
}
