"use client";

import { Fragment, useLayoutEffect, useRef, useState, type ReactNode } from "react";

import { cn } from "@/lib/utils";

/**
 * Distribui em ordem, cada item na coluna com menos peso acumulado até ali (empate: a mais à
 * esquerda). Com uma coluna, a saída preserva exatamente a ordem de entrada.
 */
export function distribuirEmColunas<T>(itens: readonly T[], colunas: number, peso: (item: T) => number): T[][] {
  const quantidade = Number.isFinite(colunas) ? Math.max(1, Math.floor(colunas)) : 1;
  const distribuicao: T[][] = Array.from({ length: quantidade }, () => []);
  const cargas: number[] = Array.from({ length: quantidade }, () => 0);
  for (const item of itens) {
    const alvo = cargas.indexOf(Math.min(...cargas));
    distribuicao[alvo].push(item);
    cargas[alvo] += peso(item);
  }
  return distribuicao;
}

/**
 * Quantas colunas a grade CSS resolveu: o `grid-template-columns` computado de um contêiner grid
 * vem em px, uma trilha por coluna. Fora de layout (elemento oculto, ambiente sem CSS) volta 1.
 */
export function contarColunas(trilhasComputadas: string): number {
  const trilhas = trilhasComputadas.trim().split(/\s+/);
  return trilhas.every((trilha) => /^\d+(\.\d+)?px$/.test(trilha)) ? trilhas.length : 1;
}

/** Vão entre colunas e entre cartões; entra também na conta do limite de colunas. */
const ESPACAMENTO = "1rem";

/**
 * Trilhas da grade: `auto-fill` pela largura mínima; com `colunasMaximas`, a largura mínima de cada
 * trilha também nunca fica abaixo da fração que caberia nesse número de colunas — então o CSS nunca
 * resolve mais trilhas que o limite. `min(100%, …)` mantém uma coluna inteira em tela estreita.
 */
function trilhas(larguraMinima: string, colunasMaximas?: number): string {
  const minimo = colunasMaximas
    ? `max(${larguraMinima}, calc((100% - ${colunasMaximas - 1} * ${ESPACAMENTO}) / ${colunasMaximas}))`
    : larguraMinima;
  return `repeat(auto-fill, minmax(min(100%, ${minimo}), 1fr))`;
}

/**
 * Grade de cartões com altura livre (masonry): cada coluna empilha os seus cartões, sem o vão que
 * uma grade por linhas deixa embaixo do cartão mais baixo de cada linha.
 *
 * Quantas colunas cabem é decisão do CSS — `auto-fill` pela largura do contêiner, não da viewport,
 * então a mesma grade serve numa página inteira ou ao lado de um painel. Qual cartão vai para qual
 * coluna é decisão daqui, pelo `peso` estimado de cada item e nunca pela altura medida: um cartão
 * que cresce ou encolhe durante a edição não troca de coluna debaixo do cursor.
 */
export function GradeEmColunas<T>({
  itens,
  chave,
  peso,
  larguraMinima = "22rem",
  colunasMaximas,
  className,
  children,
}: {
  itens: readonly T[];
  chave: (item: T) => string;
  /** Altura relativa estimada; precisa ser estável durante a interação com o item. */
  peso: (item: T) => number;
  /** Largura mínima de cada coluna (comprimento CSS). */
  larguraMinima?: string;
  /** Teto de colunas mesmo em tela larga; sem ele, cabem quantas a largura permitir. */
  colunasMaximas?: number;
  className?: string;
  children: (item: T) => ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [colunas, setColunas] = useState(1);

  // Antes da pintura: a grade nunca aparece em uma coluna para depois se redistribuir.
  useLayoutEffect(() => {
    const grade = ref.current;
    if (!grade) return;
    const medir = () => setColunas(contarColunas(getComputedStyle(grade).gridTemplateColumns));
    medir();
    if (typeof ResizeObserver === "undefined") return;
    const observador = new ResizeObserver(medir);
    observador.observe(grade);
    return () => observador.disconnect();
  }, []);

  return (
    <div
      ref={ref}
      data-slot="grade-em-colunas"
      className={cn("grid items-start", className)}
      style={{ gap: ESPACAMENTO, gridTemplateColumns: trilhas(larguraMinima, colunasMaximas) }}
    >
      {distribuirEmColunas(itens, colunas, peso).map((coluna, indice) => (
        <div key={indice} data-slot="coluna" className="flex min-w-0 flex-col" style={{ gap: ESPACAMENTO }}>
          {coluna.map((item) => (
            <Fragment key={chave(item)}>{children(item)}</Fragment>
          ))}
        </div>
      ))}
    </div>
  );
}
