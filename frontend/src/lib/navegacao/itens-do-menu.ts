export interface ItemDeMenuBase {
  chave: string;
  rota: string;
  /** Ausente = feature central. Presente = só aparece se a flag vier habilitada. */
  flag?: string;
}

export const ITENS_MENU: readonly ItemDeMenuBase[] = [
  { chave: "atendimentos", rota: "/atendimentos" },
  { chave: "dashboard", rota: "/dashboard", flag: "dashboard" },
  { chave: "agenda", rota: "/agenda" },
  { chave: "tags", rota: "/tags" },
  { chave: "mensagensRapidas", rota: "/mensagens-rapidas" },
  { chave: "templatesWhatsApp", rota: "/templates-whatsapp" },
  { chave: "bancoArquivos", rota: "/banco-arquivos", flag: "banco_arquivos" },
  { chave: "mensagensProgramadas", rota: "/mensagens-programadas" },
  { chave: "lembretes", rota: "/lembretes" },
  { chave: "chatInterno", rota: "/chat-interno", flag: "chat_interno" },
  { chave: "feedbacks", rota: "/feedbacks" },
];

export const ITENS_GESTAO: readonly ItemDeMenuBase[] = [
  { chave: "equipe", rota: "/equipe" },
  { chave: "campanhas", rota: "/campanhas", flag: "campanhas" },
  { chave: "automacao", rota: "/automacao" },
  { chave: "horarios", rota: "/horarios", flag: "horarios" },
  { chave: "relatorios", rota: "/relatorios", flag: "relatorios" },
  { chave: "administracao", rota: "/administracao" },
];

export const CHAVES_ABA_INFERIOR = ["atendimentos", "dashboard", "agenda"] as const;

export function itemEstaNaAbaInferior(chave: string): boolean {
  return (CHAVES_ABA_INFERIOR as readonly string[]).includes(chave);
}
