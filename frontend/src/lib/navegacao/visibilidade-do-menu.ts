import type { AreaFeedback } from "@/lib/feedbacks/types";
import type { MinhasPermissoes } from "@/lib/gestao/types";

/**
 * Capacidade de leitura que torna a tela útil (Gestão, docs/47). Sem ela o backend responde 403 em
 * tudo que a tela carrega — o item some em vez de virar controle fantasma.
 */
const CAPACIDADE_DO_ITEM: Record<string, string> = {
  dashboard: "dashboard.ver",
  campanhas: "campanhas.ver",
  automacao: "automacao.ver",
  templatesWhatsApp: "templates.ver",
  mensagensRapidas: "mensagens_rapidas.usar",
  mensagensProgramadas: "mensagens_programadas.ver",
  lembretes: "lembretes.ver",
};

/**
 * Regras do menu lateral, reutilizadas na escolha de área do feedback.
 *
 * `permissoes` define a fonte da decisão:
 * - objeto: efetivas do backend (perfil, exceção, teto e flags) — o menu reflete revogações sem F5;
 * - `null`: permissões ainda desconhecidas (carregando ou erro) — itens condicionados ficam ocultos,
 *   para nenhum item privilegiado aparecer e sumir em seguida;
 * - ausente: sem contexto de sessão (formulário de feedback), vale a regra por papel.
 */
export function itemDeMenuVisivel(
  chave: string,
  papel: string | null,
  flagsHabilitadas: string[] | undefined,
  flag?: string,
  gerenciaTemplates = true,
  permissoes?: MinhasPermissoes | null,
): boolean {
  if (chave === "templatesWhatsApp" && !gerenciaTemplates) return false;
  // Administração é exclusiva do ADMINISTRADOR e fica fora do catálogo da Gestão (docs/47 §2.2).
  if (chave === "administracao" && papel !== "ADMINISTRADOR") return false;
  const visivel = permissoes === undefined
    ? visivelPeloPapel(chave, papel)
    : visivelPelasPermissoes(chave, permissoes);
  if (!visivel) return false;
  if (!flag) return true;
  return (flagsHabilitadas ?? []).includes(flag);
}

function visivelPelasPermissoes(chave: string, permissoes: MinhasPermissoes | null): boolean {
  if (chave === "gestao") return permissoes?.acessaGestao === true;
  const capacidade = CAPACIDADE_DO_ITEM[chave];
  if (!capacidade) return true;
  return permissoes?.capacidades[capacidade]?.permitido === true;
}

function papelDeGestao(papel: string | null | undefined): boolean {
  return papel === "GESTOR" || papel === "SUBGESTOR" || papel === "ADMINISTRADOR";
}

function visivelPeloPapel(chave: string, papel: string | null): boolean {
  const gestao = papelDeGestao(papel);
  if (chave === "gestao" || chave === "automacao" || chave === "dashboard" || chave === "campanhas") return gestao;
  return true;
}

/**
 * Importação e exportação de leads ficaram fora do catálogo da Gestão (docs/47 §2.2) e continuam
 * com a regra de papel do backend (`SO_GESTAO`).
 */
export function gerenciaImportacaoDeLeads(papel: string | null): boolean {
  return papel === "SUBGESTOR" || papel === "GESTOR" || papel === "ADMINISTRADOR";
}

const MENU_DA_AREA: Record<AreaFeedback, { chave: string; flag?: string } | null> = {
  GERAL: null,
  ATENDIMENTOS: { chave: "atendimentos" },
  AGENDA: { chave: "agenda" },
  DASHBOARD: { chave: "dashboard", flag: "dashboard" },
  EQUIPE: { chave: "gestao" },
  AUTOMACAO: { chave: "automacao" },
  MENSAGENS_PROGRAMADAS: { chave: "mensagensProgramadas" },
  LEMBRETES: { chave: "lembretes" },
  TAGS: { chave: "tags" },
  CONFIGURACOES: null,
};

export function areaDeFeedbackVisivel(
  area: AreaFeedback,
  papel: string | null,
  flagsHabilitadas: string[] | undefined,
): boolean {
  const menu = MENU_DA_AREA[area];
  if (!menu) return true;
  return itemDeMenuVisivel(menu.chave, papel, flagsHabilitadas, menu.flag);
}
