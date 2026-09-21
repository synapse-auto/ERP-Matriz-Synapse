/**
 * Leitura do gráfico "Horário de pico": em que faixa cada barra cai e qual resumo mostrar no
 * cabeçalho ("picos às 9h e 15h · vale entre 12h e 13h"). Tudo derivado das contagens por hora
 * que a API já devolve — nada é estimado.
 */

/** A partir desta fração do máximo a hora é pico: barra forte, número em destaque. */
export const LIMIAR_PICO = 0.6;
/** Entre esta fração e o pico, a hora é intermediária; abaixo, é baixa. */
export const LIMIAR_INTERMEDIARIO = 0.25;
/** Uma hora vizinha entra no vale se estiver até 10% acima da hora mais baixa entre os picos. */
const TOLERANCIA_DO_VALE = 1.1;
const MEIO_DIA = 12;

export type FaixaDaHora = "pico" | "intermediaria" | "baixa";

export function faixaDaHora(quantidade: number, maximo: number): FaixaDaHora {
  if (maximo <= 0) return "baixa";
  const fracao = quantidade / maximo;
  if (fracao >= LIMIAR_PICO) return "pico";
  if (fracao >= LIMIAR_INTERMEDIARIO) return "intermediaria";
  return "baixa";
}

export type ResumoDoHorario =
  | { tipo: "picoUnico"; hora: number }
  | { tipo: "picos"; manha: number; tarde: number }
  | { tipo: "picosEVale"; manha: number; tarde: number; inicio: number; fim: number };

/**
 * Pico da manhã = hora mais movimentada antes do meio-dia; pico da tarde = a mais movimentada a
 * partir dele. O vale é a hora mais baixa entre os dois picos, estendida a uma vizinha quando ela
 * está praticamente no mesmo nível. Sem movimento em um dos turnos, só há um pico a mostrar.
 */
export function resumoDoHorario(porHora: ReadonlyMap<number, number>): ResumoDoHorario | null {
  const contagem = (hora: number) => porHora.get(hora) ?? 0;
  const manha = horaMaisMovimentada(0, MEIO_DIA - 1, contagem);
  const tarde = horaMaisMovimentada(MEIO_DIA, 23, contagem);
  if (manha === null && tarde === null) return null;
  if (manha === null || tarde === null) {
    return { tipo: "picoUnico", hora: (manha ?? tarde) as number };
  }
  if (tarde - manha < 2) return { tipo: "picos", manha, tarde };

  let vale = manha + 1;
  for (let hora = manha + 1; hora < tarde; hora++) {
    if (contagem(hora) < contagem(vale)) vale = hora;
  }
  const teto = contagem(vale) * TOLERANCIA_DO_VALE;
  let inicio = vale;
  let fim = vale;
  if (vale - 1 > manha && contagem(vale - 1) <= teto) {
    inicio = vale - 1;
  } else if (vale + 1 < tarde && contagem(vale + 1) <= teto) {
    fim = vale + 1;
  }
  return { tipo: "picosEVale", manha, tarde, inicio, fim };
}

function horaMaisMovimentada(
  de: number,
  ate: number,
  contagem: (hora: number) => number,
): number | null {
  let melhor: number | null = null;
  for (let hora = de; hora <= ate; hora++) {
    if (contagem(hora) > 0 && (melhor === null || contagem(hora) > contagem(melhor))) melhor = hora;
  }
  return melhor;
}
