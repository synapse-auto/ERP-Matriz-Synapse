import { apiFetch } from "@/lib/api/http-client";
import type { ChatContato, ChatConversa, ChatMensagem, ChatParticipante, PaginaChatMensagens } from "./types";

export const listarConversasChat = () => apiFetch<ChatConversa[]>("/api/v1/chat-interno/conversas");
export const listarContatosChat = () => apiFetch<ChatContato[]>("/api/v1/chat-interno/contatos");
export const abrirConversaDireta = (usuarioId: string) =>
  apiFetch<{ id: string }>("/api/v1/chat-interno/conversas/direta", { method: "POST", body: JSON.stringify({ usuarioId }) });
export const criarGrupoChat = (nome: string, participantes: string[]) =>
  apiFetch<{ id: string }>("/api/v1/chat-interno/conversas/grupo", {
    method: "POST",
    body: JSON.stringify({ nome, participantes }),
  });
export const listarParticipantesChat = (conversaId: string) =>
  apiFetch<ChatParticipante[]>(`/api/v1/chat-interno/conversas/${conversaId}/participantes`);
export const adicionarParticipanteChat = (conversaId: string, usuarioId: string) =>
  apiFetch<void>(`/api/v1/chat-interno/conversas/${conversaId}/participantes`, {
    method: "POST",
    body: JSON.stringify({ usuarioId }),
  });
export const removerParticipanteChat = (conversaId: string, usuarioId: string) =>
  apiFetch<void>(`/api/v1/chat-interno/conversas/${conversaId}/participantes/${usuarioId}`, {
    method: "DELETE",
  });
export const renomearGrupoChat = (conversaId: string, nome: string) =>
  apiFetch<void>(`/api/v1/chat-interno/conversas/${conversaId}/nome`, {
    method: "PUT",
    body: JSON.stringify({ nome }),
  });
export const listarMensagensChat = (id: string, antesDe?: string | null) =>
  apiFetch<PaginaChatMensagens>(`/api/v1/chat-interno/conversas/${id}/mensagens${antesDe ? `?antesDe=${encodeURIComponent(antesDe)}` : ""}`);
export const enviarMensagemChat = (id: string, conteudo: string) =>
  apiFetch<ChatMensagem>(`/api/v1/chat-interno/conversas/${id}/mensagens`, { method: "POST", body: JSON.stringify({ conteudo }) });
export const enviarMidiaChat = (id: string, arquivo: File, legenda?: string) => {
  const formData = new FormData();
  formData.append("arquivo", arquivo);
  if (legenda) formData.append("legenda", legenda);
  return apiFetch<ChatMensagem>(`/api/v1/chat-interno/conversas/${id}/mensagens/midia`, {
    method: "POST",
    body: formData
  });
};
export const marcarChatComoLido = (id: string) =>
  apiFetch<void>(`/api/v1/chat-interno/conversas/${id}/leitura`, { method: "POST" });
export const definirReacaoChat = (conversaId: string, mensagemId: string, emoji: string) =>
  apiFetch<{ mensagemId: string; reacoes: ChatMensagem["reacoes"] }>(
    `/api/v1/chat-interno/conversas/${conversaId}/mensagens/${mensagemId}/reacao`,
    { method: "PUT", body: JSON.stringify({ emoji }) },
  );
export const removerReacaoChat = (conversaId: string, mensagemId: string) =>
  apiFetch<{ mensagemId: string; reacoes: ChatMensagem["reacoes"] }>(
    `/api/v1/chat-interno/conversas/${conversaId}/mensagens/${mensagemId}/reacao`,
    { method: "DELETE" },
  );
