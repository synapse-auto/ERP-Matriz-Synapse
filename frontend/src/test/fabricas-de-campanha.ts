import type {
  Campanha,
  DetalheDaCampanha,
  ListaDeCampanhas,
  PreviaDoPublico,
  ProjecaoDeEnvio,
  TemplateParaCampanha,
} from "@/lib/campanhas/types";

export function campanhaDeTeste(sobrescritas: Partial<Campanha> = {}): Campanha {
  return {
    id: "c1",
    nome: "Retorno de orçamentos",
    status: "EM_ANDAMENTO",
    desligada: false,
    template: { id: "t1", nome: "retorno_orcamento", idioma: "pt_BR", categoria: "UTILIDADE", corpo: "Olá {{1}}, seu orçamento ficou pronto.", parametros: 1 },
    variaveis: [{ posicao: 1, campo: "PRIMEIRO_NOME", reserva: "cliente" }],
    filtro: { tagIds: null, etapaId: null, atendenteId: null, cadastroDesde: null, cadastroAte: null, nuncaConversou: null, busca: null },
    limiteDiario: 100,
    janela: { inicio: "09:00:00", fim: "18:00:00", dias: [1, 2, 3, 4, 5] },
    ritmoPorMinuto: 5,
    rampa: null,
    agendadaPara: null,
    contadores: { total: 200, pendentes: 100, enfileirados: 100, enviados: 80, entregues: 60, lidos: 30, respondidos: 10, falhas: 2, ignorados: 4, conferencia: 1 },
    motivoDePausa: null,
    pausadaEm: null,
    iniciadaEm: "2026-10-01T12:00:00Z",
    concluidaEm: null,
    criadaPor: "u1",
    criadaEm: "2026-09-30T12:00:00Z",
    ...sobrescritas,
  };
}

export function listaDeTeste(itens: Campanha[], sobrescritas: Partial<ListaDeCampanhas> = {}): ListaDeCampanhas {
  return {
    itens,
    pagina: 0,
    tamanho: 10,
    total: itens.length,
    indicadores: { enviadas: 80, entregues: 60, lidas: 30, respondidas: 10, falhas: 2 },
    enfileiradasHoje: 40,
    tetoDiario: 200,
    limiteMetaInformado: 0,
    ...sobrescritas,
  };
}

export function detalheDeTeste(campanha: Campanha): DetalheDaCampanha {
  return {
    campanha,
    porDia: [{ dia: "2026-10-01", enfileiradas: 80 }],
    limiteEfetivoHoje: campanha.limiteDiario,
    tetoDaInstancia: 200,
    limiteMetaInformado: 0,
    enfileiradasHojeNaInstancia: 40,
  };
}

export function templateDeTeste(sobrescritas: Partial<TemplateParaCampanha> = {}): TemplateParaCampanha {
  return {
    id: "t1",
    nome: "retorno_orcamento",
    idioma: "pt_BR",
    categoria: "UTILIDADE",
    status: "APROVADO",
    corpo: "Olá {{1}}, seu orçamento ficou pronto.",
    parametros: 1,
    elegivel: true,
    restricoes: [],
    ...sobrescritas,
  };
}

export function previaDeTeste(sobrescritas: Partial<PreviaDoPublico> = {}): PreviaDoPublico {
  return { total: 150, elegiveis: 120, excluidos: 30, excluidosPorMotivo: { OPT_OUT: 10, TELEFONE_INVALIDO: 20 }, ...sobrescritas };
}

export function projecaoDeTeste(sobrescritas: Partial<ProjecaoDeEnvio> = {}): ProjecaoDeEnvio {
  return {
    destinatarios: 120,
    dias: [
      { dia: "2026-10-05", mensagens: 100, limiteDoDia: 100 },
      { dia: "2026-10-06", mensagens: 20, limiteDoDia: 100 },
    ],
    terminoEstimado: "2026-10-06",
    completa: true,
    tetoDaInstancia: 200,
    limiteMetaInformado: 0,
    limiteEfetivoNoPrimeiroDia: 100,
    ...sobrescritas,
  };
}
