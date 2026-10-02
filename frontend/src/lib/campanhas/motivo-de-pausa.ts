import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";

/** Textos por causa (`alertaPausa.causas`) e o de reserva para um código que a tela ainda não conhece. */
export interface TextosDasCausas {
  causas: Record<string, string>;
  causaDesconhecida: string;
}

/**
 * O backend guarda o motivo da pausa automática como `CAUSA` ou `CAUSA:detalhe` (ex.: `TAXA_DE_FALHA:100`,
 * `ERRO_DA_META:131048`). A tela nunca mostra o código cru: traduz a causa e deixa o detalhe no texto.
 */
export function motivoDaPausaLegivel(motivo: string | null, textos: TextosDasCausas): string {
  if (!motivo) return "-";
  const [causa, ...resto] = motivo.split(":");
  const detalhe = resto.join(":");
  const modelo = textos.causas[causa];
  if (modelo) return interpolarCatalogo(modelo, { detalhe });
  return interpolarCatalogo(textos.causaDesconhecida, { detalhe: motivo });
}
