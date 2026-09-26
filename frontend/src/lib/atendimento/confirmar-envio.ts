import type { IdentidadeAutenticada } from "./cache-mensagens";
import { mesclarMensagens } from "./tempo-real";
import type { EnvioResposta, MensagemResposta } from "./types";

/**
 * Aplica a confirmação HTTP de um envio ao histórico, qualquer que seja a ordem em que ela chega
 * em relação ao WebSocket (MENSAGEM, STATUS ou backfill).
 *
 * A bolha otimista carrega a mesma `idempotencyKey` que o backend devolve; por isso ela nunca
 * conta como "já confirmada" — só entradas com outro `id` contam. A versão montada a partir da
 * resposta HTTP entra primeiro e o que veio do servidor é fundido por cima: URL assinada,
 * metadados e citação reais prevalecem, e o status nunca recua (`mesclarMensagens`).
 *
 * `otimista` é o retrato guardado no `onMutate`: se o histórico foi recarregado durante o upload e
 * a bolha sumiu do cache, a confirmação a recoloca em vez de deixar o envio sem bolha.
 */
export function confirmarEnvioNoHistorico(
  atuais: MensagemResposta[],
  otimista: MensagemResposta,
  resposta: EnvioResposta,
  identidade: IdentidadeAutenticada,
): MensagemResposta[] {
  const chave = resposta.idempotencyKey ?? otimista.idempotencyKey ?? null;
  const chegouPeloServidor = (mensagem: MensagemResposta) =>
    mensagem.id !== otimista.id
    && (mensagem.id === resposta.mensagemId || (chave != null && mensagem.idempotencyKey === chave));

  const retratoLocal = atuais.find((mensagem) => mensagem.id === otimista.id) ?? otimista;
  const confirmadaPeloHttp: MensagemResposta = {
    ...retratoLocal,
    id: resposta.mensagemId,
    remetenteId: identidade.id ?? retratoLocal.remetenteId,
    remetenteNome: identidade.nome ?? retratoLocal.remetenteNome,
    statusEntrega: resposta.statusEntrega,
    erroEntrega: null,
    enviadoEm: resposta.enviadoEm,
    idempotencyKey: chave,
  };

  const demais = atuais.filter((mensagem) => mensagem.id !== otimista.id && !chegouPeloServidor(mensagem));
  return mesclarMensagens(demais, [confirmadaPeloHttp, ...atuais.filter(chegouPeloServidor)]);
}
