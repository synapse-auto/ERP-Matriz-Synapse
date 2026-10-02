import type { FiltroDePublico, PedidoDeCampanha, VariavelDaCampanha } from "./types";
import { DIAS_UTEIS, instanteDoAgendamento, JANELA_PADRAO } from "./validacao";

export interface FiltroEditavel {
  tagIds: string[];
  etapaId: string;
  cadastroDesde: string;
  cadastroAte: string;
  nuncaConversou: boolean;
  busca: string;
}

export interface EstadoDoAssistente {
  rascunhoId: string | null;
  nome: string;
  template: { nome: string; idioma: string; parametros: number } | null;
  variaveis: VariavelDaCampanha[];
  filtro: FiltroEditavel;
  limiteDiario: number | null;
  ritmoPorMinuto: number | null;
  janelaInicio: string;
  janelaFim: string;
  dias: number[];
  modoDeInicio: "AGORA" | "AGENDADA";
  agendadaPara: string;
  rampaAtiva: boolean;
  rampaIncremento: number | null;
  rampaTeto: number | null;
  consentimento: boolean;
  confirmacaoDigitada: string;
}

export const FILTRO_VAZIO: FiltroEditavel = {
  tagIds: [],
  etapaId: "",
  cadastroDesde: "",
  cadastroAte: "",
  nuncaConversou: false,
  busca: "",
};

export function estadoInicial(limiteDiarioPadrao: number | null): EstadoDoAssistente {
  return {
    rascunhoId: null,
    nome: "",
    template: null,
    variaveis: [],
    filtro: FILTRO_VAZIO,
    limiteDiario: limiteDiarioPadrao,
    ritmoPorMinuto: 5,
    janelaInicio: JANELA_PADRAO.inicio,
    janelaFim: JANELA_PADRAO.fim,
    dias: [...DIAS_UTEIS],
    modoDeInicio: "AGORA",
    agendadaPara: "",
    rampaAtiva: false,
    rampaIncremento: 50,
    rampaTeto: null,
    consentimento: false,
    confirmacaoDigitada: "",
  };
}

/** Só o que foi preenchido vai para a API: filtro sem chaves = Agenda inteira. */
export function filtroSemVazios(filtro: FiltroEditavel): Partial<FiltroDePublico> {
  const resultado: Partial<FiltroDePublico> = {};
  if (filtro.tagIds.length > 0) resultado.tagIds = filtro.tagIds;
  if (filtro.etapaId) resultado.etapaId = filtro.etapaId;
  if (filtro.cadastroDesde) resultado.cadastroDesde = filtro.cadastroDesde;
  if (filtro.cadastroAte) resultado.cadastroAte = filtro.cadastroAte;
  if (filtro.nuncaConversou) resultado.nuncaConversou = true;
  if (filtro.busca.trim()) resultado.busca = filtro.busca.trim();
  return resultado;
}

export function temFiltro(filtro: FiltroEditavel): boolean {
  return Object.keys(filtroSemVazios(filtro)).length > 0;
}

export function filtroDoServidor(filtro: FiltroDePublico): FiltroEditavel {
  return {
    tagIds: filtro.tagIds ?? [],
    etapaId: filtro.etapaId ?? "",
    cadastroDesde: filtro.cadastroDesde ?? "",
    cadastroAte: filtro.cadastroAte ?? "",
    nuncaConversou: filtro.nuncaConversou === true,
    busca: filtro.busca ?? "",
  };
}

/** Rascunho do servidor volta para o estado do assistente, no passo em que a pessoa parou. */
export function estadoDoRascunho(
  campanha: {
    id: string;
    nome: string;
    template: { nome: string; idioma: string; parametros: number };
    variaveis: VariavelDaCampanha[];
    filtro: FiltroDePublico;
    limiteDiario: number;
    ritmoPorMinuto: number;
    janela: { inicio: string; fim: string; dias: number[] };
    rampa: { incrementoPorDia: number; teto: number } | null;
    agendadaPara: string | null;
  },
): EstadoDoAssistente {
  return {
    ...estadoInicial(campanha.limiteDiario),
    rascunhoId: campanha.id,
    nome: campanha.nome,
    template: {
      nome: campanha.template.nome,
      idioma: campanha.template.idioma,
      parametros: campanha.template.parametros,
    },
    variaveis: campanha.variaveis,
    filtro: filtroDoServidor(campanha.filtro),
    limiteDiario: campanha.limiteDiario,
    ritmoPorMinuto: campanha.ritmoPorMinuto,
    janelaInicio: campanha.janela.inicio.slice(0, 5),
    janelaFim: campanha.janela.fim.slice(0, 5),
    dias: campanha.janela.dias,
    modoDeInicio: campanha.agendadaPara ? "AGENDADA" : "AGORA",
    agendadaPara: campanha.agendadaPara ? paraDatetimeLocal(campanha.agendadaPara) : "",
    rampaAtiva: campanha.rampa !== null,
    rampaIncremento: campanha.rampa?.incrementoPorDia ?? 50,
    rampaTeto: campanha.rampa?.teto ?? null,
  };
}

function paraDatetimeLocal(iso: string): string {
  const data = new Date(iso);
  if (Number.isNaN(data.getTime())) return "";
  const dois = (n: number) => String(n).padStart(2, "0");
  return `${data.getFullYear()}-${dois(data.getMonth() + 1)}-${dois(data.getDate())}T${dois(data.getHours())}:${dois(data.getMinutes())}`;
}

export function pedidoDeCampanha(estado: EstadoDoAssistente): PedidoDeCampanha | null {
  if (!estado.template || estado.nome.trim().length === 0) return null;
  const rampa =
    estado.rampaAtiva && estado.rampaIncremento && estado.rampaTeto
      ? { incrementoPorDia: estado.rampaIncremento, teto: estado.rampaTeto }
      : null;
  return {
    nome: estado.nome.trim(),
    templateNome: estado.template.nome,
    templateIdioma: estado.template.idioma,
    variaveis: estado.variaveis,
    filtro: filtroSemVazios(estado.filtro),
    limiteDiario: estado.limiteDiario,
    janela: { inicio: estado.janelaInicio, fim: estado.janelaFim, dias: estado.dias },
    ritmoPorMinuto: estado.ritmoPorMinuto,
    rampa,
    agendadaPara: estado.modoDeInicio === "AGENDADA" ? instanteDoAgendamento(estado.agendadaPara) : null,
  };
}
