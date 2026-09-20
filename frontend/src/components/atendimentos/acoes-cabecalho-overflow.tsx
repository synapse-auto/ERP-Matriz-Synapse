"use client";

import { useCallback, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { LucideIcon } from "lucide-react";
import { MoreHorizontal } from "lucide-react";

import { Button, buttonVariants } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

export type AcaoDoCabecalho = {
  id: string;
  texto: string;
  icone: LucideIcon;
  prioridade: number;
  visivel: boolean;
  requisitosAutorizacao: string[];
  desabilitada?: boolean;
  carregando?: boolean;
  ariaPressed?: boolean;
  onClick: () => void;
  className?: string;
};

export type DistribuicaoDeAcoes = {
  visiveis: string[];
  excedentes: string[];
};

const LARGURA_MENU = 32;
const ESPACO_ENTRE_ACOES = 8;

/**
 * Escolhe as ações que cabem no espaço real do header.
 * Prioridade maior vence; a ordem original é preservada na renderização.
 */
export function distribuirAcoesPorLargura(
  acoes: Array<Pick<AcaoDoCabecalho, "id" | "prioridade">>,
  larguras: Record<string, number>,
  larguraDisponivel: number,
  larguraMenu = LARGURA_MENU,
): DistribuicaoDeAcoes {
  const ids = acoes.map((acao) => acao.id);
  if (acoes.length === 0 || larguraDisponivel <= 0) {
    return { visiveis: [], excedentes: ids };
  }

  const larguraTotal = acoes.reduce((total, acao) => total + (larguras[acao.id] ?? 0), 0);
  const espacos = Math.max(0, acoes.length - 1) * ESPACO_ENTRE_ACOES;
  if (Object.keys(larguras).length >= acoes.length && larguraTotal + espacos <= larguraDisponivel) {
    return { visiveis: ids, excedentes: [] };
  }

  let restante = Math.max(0, larguraDisponivel - larguraMenu - ESPACO_ENTRE_ACOES);
  const selecionadas = new Set<string>();
  const ordenadas = acoes
    .map((acao, indice) => ({ acao, indice }))
    .sort((a, b) => b.acao.prioridade - a.acao.prioridade || a.indice - b.indice);

  for (const { acao } of ordenadas) {
    const largura = larguras[acao.id];
    if (largura === undefined) continue;
    const custo = largura + (selecionadas.size > 0 ? ESPACO_ENTRE_ACOES : 0);
    if (custo <= restante) {
      selecionadas.add(acao.id);
      restante -= custo;
    }
  }

  return {
    visiveis: ids.filter((id) => selecionadas.has(id)),
    excedentes: ids.filter((id) => !selecionadas.has(id)),
  };
}

export function AcoesCabecalhoOverflow({
  acoes,
  rotuloMenu,
  className,
}: {
  acoes: AcaoDoCabecalho[];
  rotuloMenu: string;
  className?: string;
}) {
  const acoesVisiveis = useMemo(() => acoes.filter((acao) => acao.visivel), [acoes]);
  const containerRef = useRef<HTMLDivElement>(null);
  const medidasRef = useRef<HTMLDivElement>(null);
  const [distribuicao, setDistribuicao] = useState<DistribuicaoDeAcoes>({
    visiveis: acoesVisiveis.map((acao) => acao.id),
    excedentes: [],
  });

  const medir = useCallback(() => {
    const container = containerRef.current;
    const medidas = medidasRef.current;
    if (!container || !medidas) return;

    const larguras = Object.fromEntries(
      acoesVisiveis.map((acao) => {
        const elemento = medidas.querySelector<HTMLElement>(`[data-acao-medida="${acao.id}"]`);
        return [acao.id, elemento?.offsetWidth ?? 0];
      }),
    );
    const proxima = distribuirAcoesPorLargura(acoesVisiveis, larguras, container.clientWidth);
    setDistribuicao((anterior) =>
      anterior.visiveis.join(",") === proxima.visiveis.join(",") &&
      anterior.excedentes.join(",") === proxima.excedentes.join(",")
        ? anterior
        : proxima,
    );
  }, [acoesVisiveis]);

  useLayoutEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const frame = requestAnimationFrame(medir);
    if (typeof ResizeObserver === "undefined") {
      return () => cancelAnimationFrame(frame);
    }
    const observer = new ResizeObserver(medir);
    observer.observe(container);
    if (medidasRef.current) observer.observe(medidasRef.current);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [medir]);

  const porId = new Map(acoesVisiveis.map((acao) => [acao.id, acao]));
  const visiveis = distribuicao.visiveis.map((id) => porId.get(id)).filter(Boolean) as AcaoDoCabecalho[];
  const excedentes = distribuicao.excedentes.map((id) => porId.get(id)).filter(Boolean) as AcaoDoCabecalho[];

  return (
    <div
      ref={containerRef}
      className={cn("relative flex min-w-0 flex-nowrap items-center gap-2 overflow-hidden", className)}
      data-slot="acoes-cabecalho-overflow"
    >
      {visiveis.map((acao) => {
        const Icone = acao.icone;
        return (
          <Button
            key={acao.id}
            type="button"
            variant="outline"
            size="sm"
            className={acao.className}
            onClick={acao.onClick}
            disabled={acao.desabilitada || acao.carregando}
            aria-pressed={acao.ariaPressed}
            data-acao-cabecalho={acao.id}
          >
            <Icone className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
            {acao.texto}
          </Button>
        );
      })}
      {excedentes.length > 0 && (
        <DropdownMenu>
          <DropdownMenuTrigger
            className={cn(buttonVariants({ variant: "outline", size: "icon" }), "shrink-0")}
            aria-label={rotuloMenu}
            title={rotuloMenu}
            data-slot="acoes-cabecalho-overflow-trigger"
          >
            <MoreHorizontal className="size-(--tamanho-icone-interface)" aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {excedentes.map((acao) => {
              const Icone = acao.icone;
              return (
                <DropdownMenuItem
                  key={acao.id}
                  disabled={acao.desabilitada || acao.carregando}
                  onClick={acao.onClick}
                  data-acao-menu={acao.id}
                >
                  <Icone aria-hidden />
                  <span>{acao.texto}</span>
                </DropdownMenuItem>
              );
            })}
          </DropdownMenuContent>
        </DropdownMenu>
      )}
      <div
        ref={medidasRef}
        aria-hidden="true"
        className="pointer-events-none invisible absolute left-0 top-0 flex h-0 gap-2 overflow-hidden whitespace-nowrap"
        data-slot="acoes-cabecalho-medidas"
      >
        {acoesVisiveis.map((acao) => {
          const Icone = acao.icone;
          return (
            <span
              key={acao.id}
              data-acao-medida={acao.id}
              className={cn(buttonVariants({ variant: "outline", size: "sm" }), acao.className)}
            >
              <Icone className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
              {acao.texto}
            </span>
          );
        })}
      </div>
    </div>
  );
}
