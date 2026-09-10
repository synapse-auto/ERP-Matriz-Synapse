import type { CitacaoMensagem } from "@/lib/atendimento/types";

export type StatusPresencaChat = "ONLINE" | "AUSENTE" | "OFFLINE";
export interface ChatContato {
  id: string;
  nome: string;
  fotoUrl?: string | null;
  presenca: StatusPresencaChat;
}
export interface ChatParticipante {
  id: string;
  nome: string;
}
export interface ChatConversa {
  id: string; tipo: "DIRETA" | "GRUPO"; participantes: string;
  ultimaMensagem: string | null; ultimaMensagemEm: string | null; naoLidas: number;
  fotoUrl?: string | null;
}
export interface ChatMensagem {
  id: string; conversaId: string; remetenteId: string; remetenteNome: string;
  tipo?: string; conteudo: string | null; midiaUrl?: string | null; midiaMetadados?: unknown; enviadoEm: string;
  reacoes?: { emoji: string; quantidade: number; reagi: boolean }[];
  removida?: boolean;
  citacao?: (CitacaoMensagem & { origemRemovida?: boolean }) | null;
}
export interface EventoSistemaChat {
  evento: string;
  nome?: string;
  nomeAnterior?: string;
  alvoId?: string;
  alvoNome?: string;
}
export interface PaginaChatMensagens { mensagens: ChatMensagem[]; proximoCursor: string | null }

export type TipoMidiaChatInterno = "IMAGEM" | "AUDIO" | "DOCUMENTO" | "VIDEO";

export interface MidiaDoGrupo {
  mensagemId: string;
  tipo: TipoMidiaChatInterno;
  nome: string | null;
  mimetype: string | null;
  tamanho: number;
  legenda: string | null;
  enviadoEm: string;
}
