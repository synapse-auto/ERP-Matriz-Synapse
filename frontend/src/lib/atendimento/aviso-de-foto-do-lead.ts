import type { QueryClient } from "@tanstack/react-query";

/** Invalidação sem IDs/conteúdo de lead: as releituras continuam autorizadas no backend. */
export function ehAvisoDeFotoDoLead(valor: unknown): valor is { tipo: "FOTOS_LEADS_ATUALIZADAS"; eventoId: string } {
  return typeof valor === "object" && valor !== null
    && "tipo" in valor && valor.tipo === "FOTOS_LEADS_ATUALIZADAS"
    && "eventoId" in valor && typeof valor.eventoId === "string";
}
export function invalidarFotosDeLeads(cache: QueryClient) {
  for (const chave of ["avatar", "atendimentos", "agenda", "lead"]) {
    void cache.invalidateQueries({ queryKey: [chave] }, { cancelRefetch: false });
  }
}
