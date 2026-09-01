import type { Textos } from "@/lib/config/schema";
import type { EventoSistemaChat } from "./types";

type TextosSistema = Textos["chatInterno"]["sistema"];

export function parseEventoSistema(conteudo: string | null | undefined): EventoSistemaChat | null {
  if (!conteudo?.trim().startsWith("{")) return null;
  try {
    const parsed = JSON.parse(conteudo) as EventoSistemaChat;
    return typeof parsed.evento === "string" ? parsed : null;
  } catch {
    return null;
  }
}

export function textoEventoSistema(
  evento: EventoSistemaChat,
  remetenteNome: string,
  textos: TextosSistema,
): string {
  const alvo = evento.alvoNome ?? "";
  const nome = evento.nome ?? "";
  switch (evento.evento) {
    case "GRUPO_CRIADO":
      return `${remetenteNome} ${textos.grupoCriado.replace("{nome}", nome)}`;
    case "PARTICIPANTE_ADICIONADO":
      return `${remetenteNome} ${textos.participanteAdicionado.replace("{alvo}", alvo)}`;
    case "PARTICIPANTE_REMOVIDO":
      return `${remetenteNome} ${textos.participanteRemovido.replace("{alvo}", alvo)}`;
    case "PARTICIPANTE_SAIU":
      return textos.participanteSaiu.replace("{alvo}", alvo || remetenteNome);
    case "NOME_ALTERADO":
      return `${remetenteNome} ${textos.nomeAlterado.replace("{nome}", nome)}`;
    default:
      return `${remetenteNome} ${textos.eventoDesconhecido}`;
  }
}

export function previewUltimaMensagem(
  conteudo: string | null | undefined,
  textos: TextosSistema,
): string {
  if (!conteudo) return "";
  const evento = parseEventoSistema(conteudo);
  if (!evento) return conteudo;
  switch (evento.evento) {
    case "GRUPO_CRIADO":
      return textos.grupoCriado.replace("{nome}", evento.nome ?? "");
    case "PARTICIPANTE_ADICIONADO":
      return textos.participanteAdicionado.replace("{alvo}", evento.alvoNome ?? "");
    case "PARTICIPANTE_REMOVIDO":
      return textos.participanteRemovido.replace("{alvo}", evento.alvoNome ?? "");
    case "PARTICIPANTE_SAIU":
      return textos.participanteSaiu.replace("{alvo}", evento.alvoNome ?? "");
    case "NOME_ALTERADO":
      return textos.nomeAlterado.replace("{nome}", evento.nome ?? "");
    default:
      return textos.eventoDesconhecido;
  }
}
