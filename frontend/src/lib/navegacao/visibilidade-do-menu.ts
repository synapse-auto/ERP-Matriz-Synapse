import type { AreaFeedback } from "@/lib/feedbacks/types";
import type { MinhasPermissoes } from "@/lib/gestao/types";

/**
 * Capacidade de leitura que torna a tela útil (Gestão, docs/47). Sem ela o backend responde 403 em
 * tudo que a tela carrega — o item some em vez de virar controle fantasma.
 */
const CAPACIDADE_DO_ITEM: Record<string, string> = {
  dashboard: "dashboard.ver",
  automacao: "automacao.ver",
  templatesWhatsApp: "templates.ver",
  mensagensRapidas: "mensagens_rapidas.usar",
  mensagensProgramadas: "mensagens_programadas.ver",
  lembretes: "lembretes.ver",
};

/**
 * Regras do menu lateral, reutilizadas na escolha de área do feedback. Com `permissoes` (efetivas do
 * backend), o menu reflete revogações sem F5; sem elas (carregando ou primeiro acesso), vale a regra
 * por papel de antes.
 */
export function itemDeMenuVisivel(
  chave: string,
  papel: string | null,
  flagsHabilitadas: string[] | undefined,
  flag?: string,
  gerenciaTemplates = true,
  permissoes?: MinhasPermissoes,
): boolean {
  if (chave === "templatesWhatsApp" && !gerenciaTemplates) return false;
  if (chave === "gestao") {
    if (papel === "GESTOR" || papel === "ADMINISTRADOR") return true;
    if (papel !== "SUBGESTOR") return false;
    if (permissoes && !permissoes.acessaGestao) return false;
  }
  const capacidade = CAPACIDADE_DO_ITEM[chave];
  if (permissoes && capacidade && permissoes.capacidades[capacidade]?.permitido !== true) return false;
  if (chave === "administracao" && papel !== "ADMINISTRADOR") return false;
  if (
    chave === "automacao" &&
    papel !== "GESTOR" &&
    papel !== "SUBGESTOR" &&
    papel !== "ADMINISTRADOR"
  ) {
    return false;
  }
  if (
    chave === "dashboard" &&
    papel !== "GESTOR" &&
    papel !== "SUBGESTOR" &&
    papel !== "ADMINISTRADOR"
  ) {
    return false;
  }
  if (!flag) return true;
  return (flagsHabilitadas ?? []).includes(flag);
}

export function podeGerenciarTemplates(papel: string | null): boolean {
  return papel === "SUBGESTOR" || papel === "GESTOR" || papel === "ADMINISTRADOR";
}

export function podeCriarTemplates(papel: string | null): boolean {
  return podeGerenciarTemplates(papel) || papel === "ATENDENTE";
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
