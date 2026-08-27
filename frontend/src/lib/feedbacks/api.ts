import { apiFetch } from "@/lib/api/http-client";

import type {
  AreaFeedback,
  CursorDeFeedbacks,
  FeedbackCriado,
  PaginaDeFeedbacks,
  TipoFeedback,
} from "./types";

export function enviarFeedback(dados: {
  tipo: TipoFeedback;
  areaChave: AreaFeedback;
  descricao: string;
}) {
  return apiFetch<FeedbackCriado>("/api/v1/feedbacks", {
    method: "POST",
    body: JSON.stringify(dados),
  });
}

export function listarFeedbacks({
  tipo,
  cursor,
  limite = 20,
}: {
  tipo: TipoFeedback | null;
  cursor: CursorDeFeedbacks | null;
  limite?: number;
}) {
  const busca = new URLSearchParams({ limite: String(limite) });
  if (tipo) busca.set("tipo", tipo);
  if (cursor) {
    busca.set("antesDe", cursor.antesDe);
    busca.set("antesDoId", cursor.antesDoId);
  }
  return apiFetch<PaginaDeFeedbacks>(`/api/v1/feedbacks?${busca.toString()}`);
}
