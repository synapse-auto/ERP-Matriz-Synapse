const VARIAVEL_DO_CORPO = /\{\{(\d+)\}\}/g;

export type ErroDeVariavelDoTemplate = {
  tipo: "ausente" | "invalido";
  indice: number;
};

export type AnaliseDeVariaveisDoTemplate = {
  indices: number[];
  erro: ErroDeVariavelDoTemplate | null;
};

export function analisarVariaveisDoCorpo(corpo: string): AnaliseDeVariaveisDoTemplate {
  const indices = indicesUnicos(corpo);
  const invalido = indices.find((indice) => indice < 1);
  if (invalido !== undefined) {
    return { indices, erro: { tipo: "invalido", indice: invalido } };
  }
  let esperado = 1;
  for (const indice of indices) {
    if (indice !== esperado) {
      return { indices, erro: { tipo: "ausente", indice: esperado } };
    }
    esperado += 1;
  }
  return { indices, erro: null };
}

export function rotulosDasVariaveis(indices: number[]): string {
  return indices.map((indice) => `{{${indice}}}`).join(", ");
}

export function interpolarCatalogo(modelo: string, valores: Record<string, string>): string {
  return Object.entries(valores).reduce(
    (texto, [chave, valor]) => texto.replaceAll(`{${chave}}`, valor),
    modelo,
  );
}

/** Substitui `{{n}}` pelo valor correspondente; marcador vazio permanece no texto. */
export function interpolarCorpoDoTemplate(corpo: string, valores: string[]): string {
  return corpo.replace(/\{\{(\d+)\}\}/g, (marcador, bruto: string) => {
    const valor = valores[Number(bruto) - 1];
    return valor?.trim() ? valor : marcador;
  });
}

/**
 * Prévia para o atendente: valor preenchido entra no texto; vazio vira o marcador
 * do catálogo (`[variável n]`). Não reusa o resultado de `interpolarCorpoDoTemplate`
 * para não reescrever um valor que o próprio atendente tenha digitado com `{{n}}`.
 */
export function interpolarPreviaDoTemplate(
  corpo: string,
  valores: string[],
  modeloMarcadorVazio: string,
): string {
  return corpo.replace(/\{\{(\d+)\}\}/g, (_marcador, bruto: string) => {
    const valor = valores[Number(bruto) - 1];
    return valor?.trim()
      ? valor
      : interpolarCatalogo(modeloMarcadorVazio, { indice: bruto });
  });
}

const RAIO_DO_TRECHO = 22;

/** Trecho do corpo em que a variável cai, para o atendente parear campo e posição. */
export function trechoDaVariavel(corpo: string, indice: number): string {
  const marcador = `{{${indice}}}`;
  const posicao = corpo.indexOf(marcador);
  if (posicao < 0) {
    return marcador;
  }
  const inicio = Math.max(0, posicao - RAIO_DO_TRECHO);
  const fim = Math.min(corpo.length, posicao + marcador.length + RAIO_DO_TRECHO);
  const prefixo = inicio > 0 ? "…" : "";
  const sufixo = fim < corpo.length ? "…" : "";
  return `${prefixo}${corpo.slice(inicio, fim)}${sufixo}`;
}

export function parametrosDoTemplatePreenchidos(valores: string[]): boolean {
  return valores.every((valor) => valor.trim() !== "");
}

function indicesUnicos(corpo: string): number[] {
  const vistos = new Set<number>();
  for (const casamento of corpo.matchAll(VARIAVEL_DO_CORPO)) {
    vistos.add(Number(casamento[1]));
  }
  return [...vistos].sort((a, b) => a - b);
}
