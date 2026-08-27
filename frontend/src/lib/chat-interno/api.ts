import { apiFetch } from "@/lib/api/http-client";
import type { ChatContato, ChatConversa, ChatMensagem, PaginaChatMensagens } from "./types";

export const listarConversasChat = () => apiFetch<ChatConversa[]>("/api/v1/chat-interno/conversas");
export const listarContatosChat = () => apiFetch<ChatContato[]>("/api/v1/chat-interno/contatos");
export const abrirConversaDireta = (usuarioId: string) =>
  apiFetch<{ id: string }>("/api/v1/chat-interno/conversas/direta", { method: "POST", body: JSON.stringify({ usuarioId }) });
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
