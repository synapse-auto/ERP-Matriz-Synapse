/**
 * E210 — decide quais ações do cabeçalho da conversa vão para o menu "⋯".
 *
 * Função pura: recebe as larguras medidas de cada item (na ordem em que aparecem), a largura
 * disponível e a largura do botão "⋯". Itens fixos nunca saem; entre os móveis, sai primeiro o de
 * menor prioridade. A ordem visual não muda: quem fica continua na posição original.
 */
export interface ItemMedido {
  id: string;
  largura: number;
  /** Nunca vai para o menu (ações essenciais e blocos com estado). */
  fixo: boolean;
  /** Maior = permanece visível por mais tempo. Ignorado para itens fixos. */
  prioridade: number;
}

/** Largura que a barra ocupa com `noMenu` fora dela (e o botão "⋯", se houver algo no menu). */
export function larguraOcupada(
  itens: readonly ItemMedido[],
  noMenu: ReadonlySet<string>,
  larguraDoMenu: number,
  espaco: number,
): number {
  const visiveis = itens.filter((item) => !noMenu.has(item.id));
  const larguraItens = visiveis.reduce((total, item) => total + item.largura, 0);
  const vaos = Math.max(0, visiveis.length - 1) * espaco;
  const menu = noMenu.size > 0 ? larguraDoMenu + (visiveis.length > 0 ? espaco : 0) : 0;
  return larguraItens + vaos + menu;
}

export function calcularTransbordo(
  itens: readonly ItemMedido[],
  disponivel: number,
  larguraDoMenu: number,
  espaco: number,
): ReadonlySet<string> {
  const noMenu = new Set<string>();
  const ocupado = () => larguraOcupada(itens, noMenu, larguraDoMenu, espaco);

  if (ocupado() <= disponivel) return noMenu;

  const moveisPorPrioridade = itens
    .map((item, posicao) => ({ item, posicao }))
    .filter(({ item }) => !item.fixo)
    // Empate: sai primeiro o que está mais à direita, para o corte parecer contínuo.
    .sort((a, b) => a.item.prioridade - b.item.prioridade || b.posicao - a.posicao);

  for (const { item } of moveisPorPrioridade) {
    noMenu.add(item.id);
    if (ocupado() <= disponivel) break;
  }
  return noMenu;
}
