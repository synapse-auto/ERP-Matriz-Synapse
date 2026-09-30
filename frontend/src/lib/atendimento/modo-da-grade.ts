/**
 * A lista e a ficha usam as larguras declaradas na grade da pagina de Atendimentos. A coluna da
 * conversa precisa continuar utilizavel; o ResizeObserver aplica estes cortes sobre o espaco
 * efetivo da pagina, depois da sidebar, e nao sobre window.innerWidth.
 */
const LARGURA_LISTA = 346;
const LARGURA_FICHA = 344;
const LARGURA_MINIMA_CONVERSA = 320;

export type ModoDaGrade = "ampla" | "dupla" | "unica";

export function modoDaGrade(largura: number): ModoDaGrade {
  if (largura < LARGURA_LISTA + LARGURA_MINIMA_CONVERSA) return "unica";
  if (largura < LARGURA_LISTA + LARGURA_FICHA + LARGURA_MINIMA_CONVERSA) return "dupla";
  return "ampla";
}
