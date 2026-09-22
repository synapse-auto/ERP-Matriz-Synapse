"use client";

import { useMemo, useState } from "react";
import { ArrowLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { DestinoDeTransferencia } from "@/lib/atendimento/types";

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
};

/**
 * Seletor comum de destinos para transferência e convite.
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
        {outros.map((destino) => (
          <Button
            key={destino.id}
            type="button"
            variant="outline"
            className="w-full justify-start"
            disabled={desabilitado}
            onClick={() => onSelecionar(destino)}
          >
            {destino.nome}
          </Button>
        ))}
      </div>
    );
  }

  return (
    <div data-testid="destinos-principais-lista" className="space-y-1">
      {principais.map((destino) => (
        <Button
          key={destino.id}
          type="button"
          variant="outline"
          className="w-full justify-start"
          disabled={desabilitado}
          onClick={() => onSelecionar(destino)}
        >
          {destino.nome}
        </Button>
      ))}
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
