import type { AreaFeedback } from "@/lib/feedbacks/types";

/** Regras do menu lateral, reutilizadas na escolha de área do feedback. */
export function itemDeMenuVisivel(
  chave: string,
  papel: string | null,
  flagsHabilitadas: string[] | undefined,
  flag?: string,
): boolean {
  if (chave === "equipe" && papel !== "GESTOR" && papel !== "ADMINISTRADOR") return false;
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

const MENU_DA_AREA: Record<AreaFeedback, { chave: string; flag?: string } | null> = {
  GERAL: null,
  ATENDIMENTOS: { chave: "atendimentos" },
  AGENDA: { chave: "agenda" },
  DASHBOARD: { chave: "dashboard", flag: "dashboard" },
  EQUIPE: { chave: "equipe" },
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
