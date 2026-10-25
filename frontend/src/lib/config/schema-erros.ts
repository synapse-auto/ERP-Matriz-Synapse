import { z } from "zod";

/** Catálogo local mínimo: também funciona quando o catálogo da instância está inacessível. */
export const TEXTOS_DE_ERRO = {
  marcaFallback: "CRM",
  codigo404: "404",
  naoEncontrada: "Página não encontrada",
  descricao404: "Não encontramos esta página. Você pode voltar e continuar seu atendimento.",
  inicio: "Voltar à tela inicial",
  voltar: "Voltar à página anterior",
  indisponivel: "Sistema temporariamente indisponível",
  descricaoIndisponivel: "Não foi possível conectar ao sistema. Tente novamente em instantes.",
  erroRenderizacao: "Não foi possível exibir esta tela",
  descricaoRenderizacao: "Ocorreu um erro inesperado. Tente abrir esta tela novamente.",
  offline: "Você está sem conexão",
  descricaoOffline: "Verifique sua conexão com a internet e tente novamente. O conteúdo aberto foi mantido.",
  tentarNovamente: "Tentar novamente",
  tentando: "Tentando novamente…",
} as const;

export const ErrosTextosSchema = z.object({
  marcaFallback: z.string().default(TEXTOS_DE_ERRO.marcaFallback),
  codigo404: z.string().default(TEXTOS_DE_ERRO.codigo404),
  naoEncontrada: z.string().default(TEXTOS_DE_ERRO.naoEncontrada),
  descricao404: z.string().default(TEXTOS_DE_ERRO.descricao404),
  inicio: z.string().default(TEXTOS_DE_ERRO.inicio),
  voltar: z.string().default(TEXTOS_DE_ERRO.voltar),
  indisponivel: z.string().default(TEXTOS_DE_ERRO.indisponivel),
  descricaoIndisponivel: z.string().default(TEXTOS_DE_ERRO.descricaoIndisponivel),
  erroRenderizacao: z.string().default(TEXTOS_DE_ERRO.erroRenderizacao),
  descricaoRenderizacao: z.string().default(TEXTOS_DE_ERRO.descricaoRenderizacao),
  offline: z.string().default(TEXTOS_DE_ERRO.offline),
  descricaoOffline: z.string().default(TEXTOS_DE_ERRO.descricaoOffline),
  tentarNovamente: z.string().default(TEXTOS_DE_ERRO.tentarNovamente),
  tentando: z.string().default(TEXTOS_DE_ERRO.tentando),
}).prefault({});

export type TextosDeErro = z.infer<typeof ErrosTextosSchema>;
