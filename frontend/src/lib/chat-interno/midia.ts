import { apiFetchArquivo } from "@/lib/api/http-client";
import type { ChatMensagem } from "./types";

export function metadadosDaMidiaInterna(mensagem: ChatMensagem) {
  let valor = mensagem.midiaMetadados;
  if (typeof valor === "string") {
    try { valor = JSON.parse(valor); } catch { valor = null; }
  }
  const objeto = valor && typeof valor === "object" && !Array.isArray(valor) ? valor as Record<string, unknown> : {};
  const nome = objeto.nome_original ?? objeto.nome;
  const tamanho = objeto.tamanho_bytes ?? objeto.tamanho;
  return {
    nome: typeof nome === "string" ? nome : null,
    tamanho: typeof tamanho === "number" && Number.isFinite(tamanho) ? tamanho : null,
    mimetype: typeof objeto.mimetype === "string" ? objeto.mimetype : null,
    legenda: typeof objeto.legenda === "string" && objeto.legenda.trim() ? objeto.legenda : mensagem.conteudo,
  };
}

/** Só cria o blob de download depois de receber os bytes; nunca persiste essa URL na mensagem. */
export async function baixarArquivoChat(conversaId: string, mensagemId: string, nome: string | null) {
  const { blob, nome: nomeConfirmado } = await apiFetchArquivo(`/api/v1/chat-interno/conversas/${encodeURIComponent(conversaId)}/midias/${encodeURIComponent(mensagemId)}/arquivo`);
  const url = URL.createObjectURL(blob);
  const ancora = document.createElement("a");
  ancora.href = url;
  ancora.download = ((nomeConfirmado ?? nome)?.replaceAll("\\", "/").split("/").at(-1) || mensagemId).replace(/[\u0000-\u001f\u007f"]/g, "_");
  ancora.rel = "noopener noreferrer";
  document.body.appendChild(ancora);
  try { ancora.click(); } finally {
    ancora.remove();
    // A navegação de download precisa consumir o blob antes de sua revogação.
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
