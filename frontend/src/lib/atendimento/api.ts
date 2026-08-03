import { apiFetch } from "@/lib/api/http-client";

import type {
  AtendimentoResumo,
  CartaoAtendimento,
  EnvioResposta,
  MensagemResposta,
  TagResposta,
  UsuarioResposta,
  VisaoAtendimento,
} from "./types";

export function listarAtendimentos(visao: VisaoAtendimento): Promise<CartaoAtendimento[]> {
  return apiFetch<CartaoAtendimento[]>(`/api/v1/atendimentos?visao=${visao}`);
}

/** `desde` ausente traz a conversa inteira — primeira carga da tela. */
export function mensagensDesde(atendimentoId: string, desde?: string): Promise<MensagemResposta[]> {
  const query = desde ? `?desde=${encodeURIComponent(desde)}` : "";
  return apiFetch<MensagemResposta[]>(`/api/v1/atendimentos/${atendimentoId}/mensagens${query}`);
}

export function enviarMensagem(leadId: string, conteudo: string): Promise<EnvioResposta> {
  return apiFetch<EnvioResposta>("/api/v1/atendimentos/mensagens", {
    method: "POST",
    body: JSON.stringify({ leadId, conteudo }),
  });
}

export function transferirAtendimento(
  atendimentoId: string,
  paraAtendenteId: string | null,
): Promise<AtendimentoResumo> {
  return apiFetch<AtendimentoResumo>(`/api/v1/atendimentos/${atendimentoId}/transferir`, {
    method: "POST",
    body: JSON.stringify({ paraAtendenteId }),
  });
}

export function finalizarAtendimento(atendimentoId: string): Promise<AtendimentoResumo> {
  return apiFetch<AtendimentoResumo>(`/api/v1/atendimentos/${atendimentoId}/finalizar`, {
    method: "POST",
  });
}

export function listarUsuarios(): Promise<UsuarioResposta[]> {
  return apiFetch<UsuarioResposta[]>("/api/v1/usuarios");
}

export function listarTags(): Promise<TagResposta[]> {
  return apiFetch<TagResposta[]>("/api/v1/tags");
}
