export interface ChatContato { id: string; nome: string }
export interface ChatConversa {
  id: string; tipo: "DIRETA" | "GRUPO"; participantes: string;
  ultimaMensagem: string | null; ultimaMensagemEm: string | null; naoLidas: number;
}
export interface ChatMensagem {
  id: string; conversaId: string; remetenteId: string; remetenteNome: string;
  conteudo: string; enviadoEm: string;
}
export interface PaginaChatMensagens { mensagens: ChatMensagem[]; proximoCursor: string | null }
