export type StatusPresencaChat = "ONLINE" | "AUSENTE" | "OFFLINE";
export interface ChatContato {
  id: string;
  nome: string;
  fotoUrl?: string | null;
  presenca: StatusPresencaChat;
}
export interface ChatConversa {
  id: string; tipo: "DIRETA" | "GRUPO"; participantes: string;
  ultimaMensagem: string | null; ultimaMensagemEm: string | null; naoLidas: number;
  fotoUrl?: string | null;
}
export interface ChatMensagem {
  id: string; conversaId: string; remetenteId: string; remetenteNome: string;
  tipo?: string; conteudo: string | null; midiaUrl?: string | null; midiaMetadados?: unknown; enviadoEm: string;
}
export interface PaginaChatMensagens { mensagens: ChatMensagem[]; proximoCursor: string | null }
