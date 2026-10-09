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

export type ResultadoVenda = "VENDEU" | "NAO_VENDEU";
export type OrigemResultadoVenda = "MANUAL" | "FINALIZACAO";

/** Espelha StatusEntrega.java — o ciclo PENDENTE → ENVIADO → ENTREGUE → LIDO, ou PENDENTE → FALHOU. */
export type StatusEntrega =
  "PENDENTE" | "ENVIADO" | "ENTREGUE" | "LIDO" | "FALHOU";

/** Espelha TipoMensagem.java. */
export type TipoMensagem =
  | "TEXTO"
  | "AUDIO"
  | "IMAGEM"
  | "DOCUMENTO"
  | "VIDEO"
  | "BOTOES"
  | "LISTA"
  | "LOCALIZACAO"
  | "CONTATO";

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
  /** Convite vigente destinado exclusivamente ao usuário autenticado. */
  convitePendente?: boolean;
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
  /** Gestao com dono: leitura do responsavel atual; demais casos: leitura pessoal. */
  naoLidas: number;
  /** Classificação explícita recebida do n8n para este ciclo, sem inferência do CRM. */
  emNegociacao?: boolean;
  resultadoVenda?: ResultadoVenda | null;
  valorVenda?: number | null;
  vendaRegistradaPorId?: string | null;
  vendaRegistradaPorNome?: string | null;
  vendaRegistradaEm?: string | null;
  origemResultadoVenda?: OrigemResultadoVenda | null;
}

export interface ResultadoVendaResposta {
  atendimentoId: string;
  resultado: ResultadoVenda;
  valor: number | null;
  registradoPorId: string;
  registradoEm: string;
  origem: OrigemResultadoVenda;
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

/** Projeção segura da mensagem de origem, usada apenas para miniatura e navegação. */
export interface OrigemDaCitacao {
  id: string;
  tipo?: TipoMensagem | string | null;
  conteudo?: string | null;
  midiaUrl?: string | null;
  midiaMetadados?: string | Record<string, unknown> | null;
  removida?: boolean;
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
  /** E214: emoji atual do cliente no WhatsApp; separado de `reacoes`, que são da equipe. */
  reacaoDoCliente?: string | null;
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
    corpoRenderizado?: string;
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

export interface ParticipanteAtendimento {
  usuarioId: string;
  nome: string;
  entrouEm: string;
  fotoUrl?: string | null;
  /** CONVITE e PEDIDO_APROVADO respondem sem assumir; ENTRADA_DIRETA (gestor, Agenda) assume ao enviar. */
  origem?: "ENTRADA_DIRETA" | "CONVITE" | "PEDIDO_APROVADO";
}

/** Snapshot REST que governa toda a conversa selecionada. */
export interface EstadoAtendimentoSelecionado {
  cartao: CartaoAtendimento;
  versao: number;
  participantes: ParticipanteAtendimento[];
  usuarioAtualEhResponsavel: boolean;
  usuarioAtualParticipa: boolean;
  podeEnviar: boolean;
}

export type StatusPedidoEntrada = "PENDENTE" | "APROVADO" | "RECUSADO" | "EXPIRADO";
export type TipoPedidoEntrada = "SOLICITACAO" | "CONVITE";
export interface PedidoEntradaAtendimento {
  id: string;
  atendimentoId: string;
  solicitanteId: string;
  solicitanteNome: string;
  status: StatusPedidoEntrada;
  tipo: TipoPedidoEntrada;
  solicitadoEm: string;
}

/** Espelha DestinosDeTransferenciaController.DestinoResposta. */
export interface DestinoDeTransferencia {
  id: string;
  nome: string;
  /** Campo adicionado após a primeira versão; ausência mantém compatibilidade com respostas antigas. */
  papel?: "ATENDENTE" | "SUBGESTOR" | "OPERADOR" | null;
}

/** Espelha UsuarioController.UsuarioResposta — GET /api/v1/usuarios. */
export interface UsuarioResposta {
  id: string;
  nome: string;
  email: string;
  papel: "ATENDENTE" | "OPERADOR" | "SUBGESTOR" | "GESTOR" | "ADMINISTRADOR";
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

export interface ConviteAtendimentoTempoReal {
  atendimentoId: string;
  leadId: string;
  convidadorId: string;
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

/** E214: reação do cliente no WhatsApp; `emoji` nulo = o cliente removeu. */
export interface ReacaoClienteTempoReal {
  atendimentoId: string;
  mensagemId: string;
  enviadoEm: string;
  emoji: string | null;
}

export interface ResumoIaStatusTempoReal {
  atendimentoId: string;
  leadId: string;
  solicitacaoId: string;
  status: "PENDENTE" | "PROCESSANDO" | "CONCLUIDO" | "FALHOU";
  erroCodigo: string | null;
  ocorridoEm: string;
}

/** Aviso leve: o texto do card nunca viaja pelo WebSocket, a tela revalida pela API autorizada. */
export interface InformacoesChatbotTempoReal {
  atendimentoId: string;
  leadId: string;
  informacaoId: string;
  ocorridoEm: string;
}

/** Card interno com o que o chatbot coletou antes da transferência; não é mensagem. */
export interface CartaoInformacoesChatbot {
  id: string;
  atendimentoId: string;
  conteudo: string;
  origem: "AUTOMACAO";
  registradoEm: string;
}

/** Página em ordem cronológica; `proximoCursor` aponta para a página MAIS ANTIGA, e é nulo na última. */
export interface InformacoesChatbotResposta {
  itens: CartaoInformacoesChatbot[];
  proximoCursor: string | null;
}

export type TipoEventoEstadoAtendimento =
  | "ATENDIMENTO_INICIADO"
  | "MENSAGEM_RECEBIDA"
  | "MENSAGEM_ENVIADA"
  | "MENSAGEM_ENVIADA_AUTOMACAO"
  | "ATENDIMENTO_TRANSFERIDO"
  | "ATENDIMENTO_DEVOLVIDO_IA"
  | "ATENDIMENTO_FINALIZADO"
  | "PEDIDO_ENTRADA_SOLICITADO"
  | "CONVITE_ATENDIMENTO_CRIADO"
  | "PEDIDO_ENTRADA_APROVADO"
  | "PEDIDO_ENTRADA_RECUSADO"
  | "PARTICIPANTE_ENTROU"
  | "PARTICIPANTE_SAIU"
  | "LEITURA_DO_RESPONSAVEL"
  | "CLASSIFICACAO_NEGOCIACAO_ALTERADA"
  | "RESULTADO_VENDA_ATUALIZADO";

export interface EstadoAtendimentoTempoReal {
  atendimentoId: string;
  leadId: string;
  eventoTipo: TipoEventoEstadoAtendimento;
  versao: number;
  ocorridoEm: string;
}

export type EventoCanonicoAtendimentoTempoReal = {
  tipo: "ATENDIMENTO_ESTADO";
  contrato: "atendimento.estado.v1";
  eventoId: string;
  versaoContrato: 1;
  dados: EstadoAtendimentoTempoReal;
};

export type EventoTempoReal =
  | { tipo: "MENSAGEM"; dados: MensagemTempoReal }
  | { tipo: "STATUS"; dados: StatusTempoReal }
  | { tipo: "TRANSFERENCIA"; dados: TransferenciaTempoReal }
  | { tipo: "FINALIZACAO"; dados: FinalizacaoTempoReal }
  | { tipo: "REACAO"; dados: ReacaoTempoReal }
  | { tipo: "REACAO_CLIENTE"; dados: ReacaoClienteTempoReal }
  | { tipo: "RESUMO_IA_STATUS"; dados: ResumoIaStatusTempoReal }
  | { tipo: "INFORMACOES_CHATBOT"; dados: InformacoesChatbotTempoReal }
  | EventoCanonicoAtendimentoTempoReal;

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
  tipo: "CONVITE_ATENDIMENTO";
  eventoId?: string;
  dados: ConviteAtendimentoTempoReal;
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
} | EventoCanonicoAtendimentoTempoReal;

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
