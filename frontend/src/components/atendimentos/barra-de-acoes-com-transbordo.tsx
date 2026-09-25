"use client";

import {
  Fragment,
  useCallback,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
  type RefObject,
} from "react";
import { MoreHorizontal } from "lucide-react";

import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  calcularTransbordo,
  larguraOcupada,
  type ItemMedido,
} from "@/lib/atendimento/transbordo-de-acoes";

export interface AcaoTransbordavel {
  id: string;
  /** Nunca vai para o menu. */
  fixo?: boolean;
  /** Maior = permanece visível por mais tempo. */
  prioridade?: number;
  /** Como aparece na barra. */
  inline: ReactNode;
  /** Como aparece no menu "⋯"; obrigatório para ações que podem sair da barra. */
  itemDeMenu?: ReactNode;
}

type Props = {
  acoes: readonly AcaoTransbordavel[];
  rotuloMenu: string;
  /**
   * Elemento cuja largura define o espaço total (o cabeçalho). A barra usa o que sobra depois de
   * `larguraReservada`, que garante a identificação da conversa.
   */
  containerRef: RefObject<HTMLElement | null>;
  larguraReservada: number;
};

const ID_DO_MENU = "__menu";

/**
 * E210 — barra de ações que envia para o "⋯" o que não cabe no espaço efetivamente disponível.
 *
 * Mede cada ação numa cópia invisível e inerte (a mesma renderização, sem foco nem clique), então
 * a decisão acompanha a largura real: sidebar recolhida, painel de detalhes, tags que chegam,
 * troca de papel ou de estado de participação. Não depende de breakpoint.
 */
export function BarraDeAcoesComTransbordo({
  acoes,
  rotuloMenu,
  containerRef,
  larguraReservada,
}: Props) {
  const barraRef = useRef<HTMLDivElement>(null);
  const medidasRef = useRef<HTMLDivElement>(null);
  const [noMenu, setNoMenu] = useState<ReadonlySet<string>>(() => new Set());
  const [linhaPropria, setLinhaPropria] = useState(false);
  // A lista de acoes e recriada a cada render do cabecalho; o observador nao deve ser recriado junto.
  const acoesRef = useRef(acoes);

  const recalcular = useCallback(() => {
    const container = containerRef.current;
    const barra = barraRef.current;
    const medidas = medidasRef.current;
    if (!container || !barra || !medidas) return;

    const estiloContainer = getComputedStyle(container);
    const larguraDoContainer = container.getBoundingClientRect().width;
    // Sem layout (oculto, ainda nao montado): nao ha base para esconder nada.
    if (larguraDoContainer <= 0) {
      setNoMenu((atual) => (atual.size === 0 ? atual : new Set()));
      setLinhaPropria(false);
      return;
    }
    const conteudoDoContainer = larguraDoContainer
      - px(estiloContainer.paddingLeft) - px(estiloContainer.paddingRight);
    const aoLadoDaIdentificacao = conteudoDoContainer - larguraReservada - px(estiloContainer.columnGap);
    const espaco = px(getComputedStyle(barra).columnGap);

    // Fixos estao sempre na barra e sao medidos no lugar; moveis, na regua.
    const larguraDe = (raiz: HTMLElement, id: string) =>
      raiz.querySelector<HTMLElement>(`[data-medida="${id}"]`)?.getBoundingClientRect().width ?? 0;
    const itens: ItemMedido[] = acoesRef.current.map((acao) => ({
      id: acao.id,
      largura: larguraDe(ehFixa(acao) ? barra : medidas, acao.id),
      fixo: ehFixa(acao),
      prioridade: acao.prioridade ?? 0,
    }));
    const larguraDoMenu = larguraDe(medidas, ID_DO_MENU);
    let proximo = calcularTransbordo(itens, aoLadoDaIdentificacao, larguraDoMenu, espaco);
    // Nem os fixos cabem ao lado do nome (conversa muito estreita): a barra desce para uma linha
    // propria, com a largura inteira do cabecalho, em vez de cortar botao.
    const quebra = larguraOcupada(itens, proximo, larguraDoMenu, espaco) > aoLadoDaIdentificacao;
    if (quebra) proximo = calcularTransbordo(itens, conteudoDoContainer, larguraDoMenu, espaco);
    setLinhaPropria(quebra);
    setNoMenu((atual) => (mesmoConjunto(atual, proximo) ? atual : proximo));
  }, [containerRef, larguraReservada]);

  // Toda renderizacao pode trazer acao nova (papel, estado de participacao, telefone): remede.
  useLayoutEffect(() => {
    acoesRef.current = acoes;
    recalcular();
  });

  useLayoutEffect(() => {
    if (typeof ResizeObserver === "undefined") return;
    const observador = new ResizeObserver(() => recalcular());
    if (containerRef.current) observador.observe(containerRef.current);
    if (medidasRef.current) observador.observe(medidasRef.current);
    if (barraRef.current) observador.observe(barraRef.current);
    return () => observador.disconnect();
  }, [containerRef, recalcular]);

  const acoesNoMenu = acoes.filter((acao) => noMenu.has(acao.id));

  return (
    <div
      ref={barraRef}
      className={cn(
        "relative flex shrink-0 items-center justify-end gap-2",
        // Ultimo recurso: se nem os fixos cabem na largura inteira, quebram de linha, nunca cortam.
        linhaPropria && "w-full flex-wrap",
      )}
      data-slot="acoes-cabecalho"
      data-linha-propria={linhaPropria || undefined}
    >
      {acoes.map((acao) =>
        noMenu.has(acao.id) ? null : (
          <div key={acao.id} data-medida={acao.id} className="flex shrink-0 items-center">
            {acao.inline}
          </div>
        ),
      )}
      {acoesNoMenu.length > 0 && (
        <DropdownMenu>
          <DropdownMenuTrigger
            className={buttonVariants({ variant: "ghost", size: "icon" })}
            aria-label={rotuloMenu}
            title={rotuloMenu}
            data-testid="menu-mais-acoes"
          >
            <MoreHorizontal className="size-(--tamanho-icone-interface)" aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-auto min-w-56">
            {acoesNoMenu.map((acao) => (
              <Fragment key={acao.id}>{acao.itemDeMenu}</Fragment>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      )}

      {/* Régua: a mesma renderização de cada ação, invisível e inerte, só para medir. */}
      <div
        ref={medidasRef}
        aria-hidden
        inert
        className="pointer-events-none invisible absolute top-0 right-0 flex w-max items-center gap-2"
        data-slot="regua-acoes-cabecalho"
      >
        {acoes.filter((acao) => !ehFixa(acao)).map((acao) => (
          <div key={acao.id} data-medida={acao.id} className="flex shrink-0 items-center">
            {acao.inline}
          </div>
        ))}
        <div data-medida={ID_DO_MENU} className="flex shrink-0 items-center">
          <span className={buttonVariants({ variant: "ghost", size: "icon" })}>
            <MoreHorizontal className="size-(--tamanho-icone-interface)" />
          </span>
        </div>
      </div>
    </div>
  );
}

function ehFixa(acao: AcaoTransbordavel): boolean {
  return acao.fixo === true || acao.itemDeMenu === undefined;
}

function px(valor: string): number {
  const numero = Number.parseFloat(valor);
  return Number.isFinite(numero) ? numero : 0;
}

function mesmoConjunto(a: ReadonlySet<string>, b: ReadonlySet<string>): boolean {
  if (a.size !== b.size) return false;
  for (const id of a) if (!b.has(id)) return false;
  return true;
}
