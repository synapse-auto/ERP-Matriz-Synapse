/**
 * Estimativa client-side da janela de 24h da Meta, para avisar o atendente ANTES de ele digitar.
 *
 * <p>Não é a fonte da verdade — o backend não expõe o valor configurado de
 * `synapse.canal.whatsapp.janela-texto-livre` (default 24h), então usamos o mesmo default aqui.
 * Se a instância mudar essa config, esta estimativa erra a favor da segurança do atendente (ele
 * pode digitar e o envio real falha com 422, nunca o contrário: nunca fingimos que a janela está
 * aberta quando o backend vai recusar por engano de arredondamento). A autoridade real continua
 * sendo a checagem em `EnviarMensagemUseCase`.
 */
const JANELA_PADRAO_HORAS = 24;

export function janelaTextoLivreAberta(
  ultimaMensagemDoLeadEm: string | null,
  agora: Date = new Date(),
): boolean {
  if (!ultimaMensagemDoLeadEm) {
    return false;
  }
  const decorridoMs = agora.getTime() - new Date(ultimaMensagemDoLeadEm).getTime();
  const janelaMs = JANELA_PADRAO_HORAS * 60 * 60 * 1000;
  return decorridoMs < janelaMs;
}
