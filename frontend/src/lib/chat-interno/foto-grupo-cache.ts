import type { InfiniteData, QueryClient } from "@tanstack/react-query";

import type { PaginaInbox } from "@/lib/atendimento/api";
import type { ChatConversa } from "./types";

/** O snapshot autorizado de conversas também governa os avatares internos da inbox paginada. */
export function sincronizarFotosDosGruposNaInbox(
  cache: QueryClient,
  conversas: ReadonlyArray<Pick<ChatConversa, "id" | "tipo" | "fotoUrl">>,
) {
  const fotos = new Map(conversas
    .filter((conversa) => conversa.tipo === "GRUPO" && conversa.fotoUrl !== undefined)
    .map((conversa) => [conversa.id, conversa.fotoUrl]));
  if (!fotos.size) return;
  cache.setQueriesData<InfiniteData<PaginaInbox>>({ queryKey: ["atendimentos", "inbox"] }, (inbox) => {
    if (!inbox) return inbox;
    let mudou = false;
    const pages = inbox.pages.map((pagina) => ({
      ...pagina,
      itens: pagina.itens.map((item) => {
        if (item?.tipo !== "EQUIPE_INTERNA" || item.tipoConversa !== "GRUPO"
          || !fotos.has(item.conversaId) || item.avatarUrl === fotos.get(item.conversaId)) return item;
        mudou = true;
        return { ...item, avatarUrl: fotos.get(item.conversaId) ?? null };
      }),
    }));
    return mudou ? { ...inbox, pages } : inbox;
  });
}
