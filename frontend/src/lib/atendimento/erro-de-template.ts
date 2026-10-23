import { ErroDeApi } from "@/lib/api/errors";
import type { Textos } from "@/lib/config/schema";

import { interpolarCatalogo } from "./variaveis-do-template";

type TextosDeErro = Textos["templatesWhatsApp"]["erros"];

const TAMANHO_MAXIMO_DO_MOTIVO = 200;

/**
 * Traduz a falha de criar, editar ou excluir template para uma frase do catálogo.
 *
 * Cada status significa uma coisa diferente e o usuário precisa saber qual: 403 é permissão, 422 é
 * a Meta recusando (o motivo dela é mostrado), 503 é provedor fora do ar. O detalhe do 5xx nunca é
 * exibido: é diagnóstico operacional e pode carregar trecho da resposta crua do provedor.
 */
export function mensagemDeErroDeTemplate(erro: unknown, textos: TextosDeErro): string {
  if (!(erro instanceof ErroDeApi)) return textos.generico;
  if (erro.status === 403) return textos.semPermissao;
  // Codigos da exclusao sao do CRM. Nunca mostrar mensagens livres do provedor.
  switch (erro.problema?.codigo) {
    case "TEMPLATE_EXCLUSAO_CREDENCIAL_RECUSADA": return textos.exclusaoCredencial;
    case "TEMPLATE_EXCLUSAO_PERMISSAO_RECUSADA": return textos.exclusaoPermissao;
    case "TEMPLATE_EXCLUSAO_RECURSO_INDISPONIVEL": return textos.exclusaoRecurso;
    case "TEMPLATE_EXCLUSAO_RECUSADO": return textos.exclusaoRecusada;
    case "TEMPLATE_EXCLUSAO_RESULTADO_INCERTO": return textos.exclusaoIncerta;
    case "TEMPLATE_EXCLUSAO_PROVEDOR_INDISPONIVEL": return textos.indisponivel;
  }
  if (erro.status === 404) return textos.naoEncontrado;
  if (erro.status >= 500) return textos.indisponivel;
  const motivo = motivoSeguro(erro.problema?.detail);
  if (erro.status === 422) {
    return motivo ? interpolarCatalogo(textos.recusado, { motivo }) : textos.recusadoSemMotivo;
  }
  if (erro.status === 400 && motivo) return interpolarCatalogo(textos.invalido, { motivo });
  return textos.generico;
}

function motivoSeguro(detalhe: string | undefined): string {
  if (!detalhe) return "";
  const semMarcacao = detalhe.replace(/<[^>]*>/g, " ").replace(/[<>]/g, "").replace(/\s+/g, " ").trim();
  return semMarcacao.length > TAMANHO_MAXIMO_DO_MOTIVO
    ? `${semMarcacao.slice(0, TAMANHO_MAXIMO_DO_MOTIVO - 1)}…`
    : semMarcacao;
}
