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

function indicesUnicos(corpo: string): number[] {
  const vistos = new Set<number>();
  for (const casamento of corpo.matchAll(VARIAVEL_DO_CORPO)) {
    vistos.add(Number(casamento[1]));
  }
  return [...vistos].sort((a, b) => a - b);
}
