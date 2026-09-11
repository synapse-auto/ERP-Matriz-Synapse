/** Espelha VisaoAtendimento.java. */
export type VisaoAtendimento =
  | "ATIVOS"
  | "PENDENTES"
  | "POTENCIAIS"
  | "TODOS"
  | "FINALIZADOS";

/** Abas da lista — FINALIZADOS é estado do menu, não aba (E136). */
export const ABAS_ATENDENTE: VisaoAtendimento[] = ["ATIVOS", "PENDENTES", "POTENCIAIS"];
export const ABAS_GESTAO: VisaoAtendimento[] = ["TODOS", "ATIVOS", "PENDENTES", "POTENCIAIS"];

export function ehAbaDeAtendimento(visao: VisaoAtendimento): boolean {
  return visao !== "FINALIZADOS";
}

/** Espelha StatusAtendimento.java. */
export type StatusAtendimento = "EM_IA" | "EM_ATENDIMENTO" | "FINALIZADO";

/** Espelha StatusEntrega.java — o ciclo PENDENTE → ENVIADO → ENTREGUE → LIDO, ou PENDENTE → FALHOU. */
export type StatusEntrega =
  "PENDENTE" | "ENVIADO" | "ENTREGUE" | "LIDO" | "FALHOU";

/** Espelha TipoMensagem.java. */
export type TipoMensagem = "TEXTO" | "AUDIO" | "IMAGEM" | "DOCUMENTO" | "VIDEO" | "BOTOES" | "LISTA" | "LOCALIZACAO";

export interface ConfiguracaoComposer {
  tamanhoMaximoAudioBytes: number;
  duracaoMaximaAudioSegundos: number;
  tempoNotificacaoSegundos: number;
}

/** Espelha ConfigInstanciaController — GET /api/v1/config/canal. */
export interface CapacidadeDoCanal {
  exigeTemplateForaDaJanela: boolean;
  gerenciaTemplates: boolean;
}

export type CategoriaTemplateWhatsApp = "UTILIDADE" | "MARKETING" | "AUTENTICACAO";
export type StatusTemplateWhatsApp =
  | "APROVADO"
  | "PENDENTE"
  | "REJEITADO"
  | "PAUSADO"
  | "DESCONHECIDO";

export interface TemplateWhatsApp {
  id: string;
  nome: string;
  idioma: string;
  categoria: CategoriaTemplateWhatsApp;
  status: StatusTemplateWhatsApp;
  corpo: string;
  quantidadeDeParametros: number;
}

/** Espelha RemetenteTipo.java. */
export type RemetenteTipo = "LEAD" | "ATENDENTE" | "SISTEMA" | "IA";

/** Espelha PainelDeAtendimentosController.contagem() — GET /api/v1/atendimentos/contagem. */
/** A API omite TODOS para atendentes, que não têm essa aba. */
export type ContagemPorVisao = Partial<Record<VisaoAtendimento, number>>;

/** Espelha PainelDeAtendimentosController.CartaoAtendimento — GET /api/v1/atendimentos?visao=. */
export interface CartaoAtendimento {
  tipo?: "CLIENTE";
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  leadFotoUrl: string | null;
  leadEmpresa: string | null;
  leadCodigo?: string | null;
  canalTipo: string | null;
  etapaId: string | null;
  etapaNome: string | null;
  etapaCor: string | null;
  status: StatusAtendimento;
  atendenteId: string | null;
  atendenteNome: string | null;
  /** Atendimento aberto do lead; nulo quando todo o histórico está finalizado. */
  atendimentoAtivoId?: string | null;
  ultimaMensagemPreview: string | null;
  ultimaMensagemRemetenteTipo: RemetenteTipo | null;
  ultimaMensagemEm: string | null;
  /** Base para a estimativa client-side da janela de 24h — ver janela-24h.ts. */
  ultimaMensagemDoLeadEm: string | null;
  naoLidas: number;
}

export interface CartaoEquipeInterna {
  tipo: "EQUIPE_INTERNA";
  atendimentoId: null;
  conversaId: string;
  nome: string;
  avatarUrl: string | null;
  identificadorVisual: string;
  ultimaMensagemPreview: string | null;
  ultimaMensagemEm: string | null;
  naoLidas: number;
  participantes: string | null;
  tipoConversa: "DIRETA" | "GRUPO";
}

export type ItemInbox = (CartaoAtendimento & {
  conversaId?: null;
  nome?: string;
  avatarUrl?: string | null;
  identificadorVisual?: string;
  participantes?: null;
  tipoConversa?: null;
}) | CartaoEquipeInterna;

/** Espelha AtendimentoMensagensController.ResumoReacaoResposta. */
export interface ResumoReacao {
  emoji: string;
  quantidade: number;
  reagi: boolean;
}

export interface CitacaoMensagem {
  origemId: string | null;
  tipoReferencia: "RESPOSTA" | "ENCAMINHAMENTO";
  autor: string;
  tipoConteudo: TipoMensagem | string;
  previa: string;
  origemRemovida?: boolean;
}

/** Motivo informado pelo provedor quando a entrega falhou. */
export interface ErroDeEntrega {
  codigo: number | null;
  titulo: string | null;
}

/** Espelha AtendimentoMensagensController.MensagemResposta — GET /api/v1/atendimentos/{id}/mensagens. */
export interface MensagemResposta {
  id: string;
  /** Atendimento de origem; permite desenhar marcos ao atravessar o histórico do lead. */
  atendimentoId?: string;
  atendimentoIniciadoEm?: string | null;
  atendimentoFinalizadoEm?: string | null;
  atendimentoResponsavelNome?: string | null;
  remetenteTipo: RemetenteTipo;
  remetenteId: string | null;
  remetenteNome: string | null;
  tipo: TipoMensagem;
  conteudo: string | null;
  midiaUrl: string | null;
  midiaMetadados: string | null;
  opcoes: string | null;
  statusEntrega: StatusEntrega;
  erroEntrega: ErroDeEntrega | null;
  enviadoEm: string;
  reacoes?: ResumoReacao[];
  citacao?: CitacaoMensagem | null;
  /** Chave do clique de envio, presente nas mensagens humanas e usada para reconciliar o otimista. */
  idempotencyKey?: string | null;
}

export interface PaginaMensagens {
  mensagens: MensagemResposta[];
  proximoCursor: string | null;
}

/** Espelha AtendimentoAcoesController.EnvioResposta — POST /api/v1/atendimentos/mensagens. */
export interface EnvioResposta {
  atendimentoId: string;
  mensagemId: string;
  statusEntrega: StatusEntrega;
  enviadoEm: string;
  transferiuOLead: boolean;
  idempotencyKey?: string | null;
}

/** Espelha AtendimentoAcoesController.NovoContatoResposta — POST /api/v1/atendimentos/novo-contato. */
export interface NovoContatoResposta {
  leadId: string;
  atendimentoId: string;
  mensagemId: string | null;
  leadCriado: boolean;
}

export interface PedidoDeNovoContato {
  nome: string;
  telefone: string;
  primeiraMensagem?: string;
  template?: {
    nome: string;
    idioma: string;
    parametros: string[];
  };
}

/** Espelha AtendimentoAcoesController.AtendimentoResumo — resposta de /transferir e /finalizar. */
export interface AtendimentoResumo {
  id: string;
  status: StatusAtendimento;
  atendenteId: string | null;
}

export interface ContagemFinalizacaoPorAtendente {
  atendenteId: string;
  nome: string;
  quantidade: number;
}

export interface FinalizacaoEmLotePrevia {
  quantidade: number;
  porAtendente: ContagemFinalizacaoPorAtendente[];
}

export interface FinalizacaoEmLoteResposta {
  solicitados: number;
  finalizados: number;
  recusados: number;
}

export interface ParticipanteAtendimento {
  usuarioId: string;
  nome: string;
  entrouEm: string;
  fotoUrl?: string | null;
}

export type StatusPedidoEntrada = "PENDENTE" | "APROVADO" | "RECUSADO" | "EXPIRADO";
export interface PedidoEntradaAtendimento {
  id: string;
  atendimentoId: string;
  solicitanteId: string;
  solicitanteNome: string;
  status: StatusPedidoEntrada;
  solicitadoEm: string;
}

/** Espelha DestinosDeTransferenciaController.DestinoResposta. */
export interface DestinoDeTransferencia {
  id: string;
  nome: string;
}

/** Espelha UsuarioController.UsuarioResposta — GET /api/v1/usuarios. */
export interface UsuarioResposta {
  id: string;
  nome: string;
  email: string;
  papel: "ATENDENTE" | "SUBGESTOR" | "GESTOR" | "ADMINISTRADOR";
  ativo: boolean;
  statusPresenca: "ONLINE" | "AUSENTE" | "OFFLINE";
}

/** Espelha TagController.TagResposta — GET /api/v1/tags. */
export interface TagResposta {
  id: string;
  nome: string;
  cor: string;
  icone: string | null;
}

// --- Tempo real (STOMP) ----------------------------------------------------
// Envelope e payloads espelham RelayDeTempoRealListener.java / RedisSubscriberDeAtendimento.java.

export interface MensagemTempoReal {
  eventoId?: string;
  atendimentoId: string;
  leadId: string;
  mensagemId: string;
  leadNome?: string | null;
  remetenteTipo: RemetenteTipo;
  remetenteId: string | null;
  tipo: TipoMensagem;
  conteudo: string | null;
  midiaUrl: string | null;
  midiaMetadados: string | null;
  opcoes: string | null;
  statusEntrega: StatusEntrega;
  enviadoEm: string;
  citacao?: CitacaoMensagem | null;
  idempotencyKey?: string | null;
}

export interface StatusTempoReal {
  atendimentoId: string;
  leadId: string;
  mensagemId: string;
  statusEntrega: StatusEntrega;
  ocorridoEm: string;
  idempotencyKey?: string | null;
}

export interface TransferenciaTempoReal {
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  deAtendenteId: string | null;
  paraAtendenteId: string | null;
  quemTransferiu: string | null;
  atorTipo: "USUARIO" | "AUTOMACAO" | "SISTEMA";
  ocorridoEm: string;
}

export interface TransferenciaRecebidaTempoReal {
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  quemTransferiu: string | null;
  atorTipo: "USUARIO" | "AUTOMACAO" | "SISTEMA";
  ocorridoEm: string;
}

export interface AtendimentoDevolvidoParaIaTempoReal {
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  ocorridoEm: string;
}

export interface FinalizacaoTempoReal {
  atendimentoId: string;
  leadId: string;
  quemFinalizou: string;
  ocorridoEm: string;
}

export interface ReacaoTempoReal {
  atendimentoId: string;
  mensagemId: string;
  enviadoEm: string;
  atorId: string;
  emojiDoAtor: string | null;
  reacoes: { emoji: string; quantidade: number }[];
}

export type EventoTempoReal =
  | { tipo: "MENSAGEM"; dados: MensagemTempoReal }
  | { tipo: "STATUS"; dados: StatusTempoReal }
  | { tipo: "TRANSFERENCIA"; dados: TransferenciaTempoReal }
  | { tipo: "FINALIZACAO"; dados: FinalizacaoTempoReal }
  | { tipo: "REACAO"; dados: ReacaoTempoReal };

export type NotificacaoTempoReal = {
  tipo: "NOVA_MENSAGEM";
  eventoId?: string;
  dados: NovaMensagemTempoReal;
} | {
  tipo: "TRANSFERENCIA_RECEBIDA";
  eventoId?: string;
  dados: TransferenciaRecebidaTempoReal;
} | {
  tipo: "ATENDIMENTO_DEVOLVIDO_PARA_IA";
  eventoId?: string;
  dados: AtendimentoDevolvidoParaIaTempoReal;
} | {
  tipo: "CHAT_INTERNO_MENSAGEM";
  eventoId?: string;
  dados: ChatInternoMensagemTempoReal;
} | {
  tipo: "CHAT_INTERNO_REACAO";
  eventoId?: string;
  dados: ChatInternoReacaoTempoReal;
} | {
  tipo: "CHAT_INTERNO_MENSAGEM_REMOVIDA";
  eventoId?: string;
  dados: ChatInternoMensagemRemovidaTempoReal;
} | {
  tipo: "CHAT_INTERNO_MENSAGEM_EDITADA";
  eventoId?: string;
  dados: ChatInternoMensagemEditadaTempoReal;
};

export interface NovaMensagemTempoReal {
  atendimentoId: string;
  leadId: string;
  leadNome: string;
  mensagemId: string;
  remetenteTipo: RemetenteTipo;
  remetenteId: string | null;
  tipo: TipoMensagem;
  conteudo: string | null;
  midiaMetadados: string | null;
  enviadoEm: string;
  idempotencyKey?: string | null;
}

export interface ChatInternoMensagemTempoReal {
  conversaId: string;
  mensagemId: string;
  remetenteId: string;
  remetenteNome?: string | null;
  tipo?: string | null;
  conteudo: string | null;
  midiaMetadados?: string | null;
  enviadoEm: string;
}

export interface ChatInternoReacaoTempoReal {
  conversaId: string;
  mensagemId: string;
  atorId: string;
  emojiDoAtor: string | null;
  reacoes: { emoji: string; quantidade: number }[];
}

export interface ChatInternoMensagemRemovidaTempoReal {
  conversaId: string;
  mensagemId: string;
}

export interface ChatInternoMensagemEditadaTempoReal extends ChatInternoMensagemTempoReal {
  editadoEm: string;
}

/** Payload de /user/queue/revogacoes. */
export interface RevogacaoTempoReal {
  atendimentoId: string;
}
