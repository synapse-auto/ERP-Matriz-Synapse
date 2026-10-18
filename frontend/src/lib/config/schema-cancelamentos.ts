import { z } from "zod";

/** Fallback local para catalogos de instancias anteriores; personalizavel em textos.json. */
export const TEXTOS_CANCELAMENTOS = {
  titulo: "Cancelamentos",
  descricao: "Consulta de agendamentos cancelados. Nenhuma alteração é realizada por esta tela.",
  periodo: "Período",
  base: "Data usada no período",
  registro: "Registro do cancelamento",
  agendamento: "Horário do agendamento",
  inicio: "De",
  fim: "Até",
  profissional: "Profissional",
  tipo: "Tipo de agendamento",
  nome: "Nome do contato",
  descricaoFiltro: "Descrição",
  todas: "Todas",
  comDescricao: "Com descrição",
  semDescricao: "Sem descrição informada",
  aplicar: "Aplicar filtros",
  contato: "Contato",
  detalhes: "Ver detalhes",
  detalheTitulo: "Detalhes do cancelamento",
  detalheDescricao: "Informações fornecidas pela integração. Somente leitura.",
  descricaoCampo: "Descrição / motivo informado",
  naoInformado: "Não informado",
  carregando: "Carregando cancelamentos…",
  vazio: "Nenhum cancelamento encontrado com esses filtros.",
  naoConfigurada: "Integração de cancelamentos ainda não configurada.",
  pendencia: "A conexão com a fonte de dados aguarda confirmação da equipe de integração.",
  indisponivel: "A fonte de cancelamentos está temporariamente indisponível.",
  erro: "Não foi possível carregar os cancelamentos.",
  filtroInvalido: "Confira o período e os limites dos filtros.",
  semAcesso: "Você não tem acesso a esta consulta.",
  naoEncontrado: "Registro inexistente ou não disponível para você.",
  tentar: "Tentar novamente",
  anterior: "Página anterior",
  proxima: "Próxima página",
  pagina: "Página {pagina}",
  fechar: "Fechar",
  vinculoIndisponivel: "Abrir atendimento indisponível: correlação e acesso ainda não confirmados.",
  notaDatas: "Registro é a data de gravação na fonte; não comprova o instante em que o cancelamento ocorreu.",
  fuso: "Horários em {fuso}",
} as const;

export const CancelamentosTextosSchema = z.object(
  Object.fromEntries(Object.entries(TEXTOS_CANCELAMENTOS).map(([chave, valor]) => [chave, z.string().default(valor)])) as {
    [K in keyof typeof TEXTOS_CANCELAMENTOS]: z.ZodDefault<z.ZodString>
  },
).default({});
