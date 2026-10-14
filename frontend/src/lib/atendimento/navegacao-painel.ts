export const EVENTO_VOLTAR_PAINEL = "synapse:voltar-painel-atendimentos";

/** Só seleção/navegação: nunca mutations de leitura, posse ou finalização. */
export function voltarAoPainelOperacional() {
  window.history.pushState(null, "", "/atendimentos");
  window.dispatchEvent(new Event(EVENTO_VOLTAR_PAINEL));
}
