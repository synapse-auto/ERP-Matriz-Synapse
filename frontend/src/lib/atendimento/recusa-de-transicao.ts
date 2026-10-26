import { ErroDeApi } from "@/lib/api/errors";

/** Estes conflitos garantem rollback antes de criar a intencao de envio. */
export function envioRecusadoNaTransicao(erro: unknown): boolean {
  return erro instanceof ErroDeApi && erro.status === 409
    && ["SAIDA_SUSPENSA_TRANSICAO_CANAL", "REFERENCIA_DE_OUTRO_CANAL"]
      .includes(erro.problema?.codigo ?? "");
}
