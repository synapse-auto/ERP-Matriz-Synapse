"use client";

import { useQuery } from "@tanstack/react-query";

import { listarLembretes, listarMensagensProgramadas } from "./api";

/** Lembretes de UM lead (E17 §Bloco 2) — seção "Lembretes" do painel de atendimento. */
export function useLembretesDoLead(leadId: string | null) {
  return useQuery({
    queryKey: ["lembretes", "lead", leadId],
    queryFn: () => listarLembretes({ leadId: leadId!, pagina: 0 }),
    enabled: Boolean(leadId),
  });
}

/** Mensagens programadas de UM lead (E17 §Bloco 2) — seção "Mensagens programadas" do painel. */
export function useMensagensProgramadasDoLead(leadId: string | null) {
  return useQuery({
    queryKey: ["mensagens-programadas", "lead", leadId],
    queryFn: () => listarMensagensProgramadas({ leadId: leadId!, pagina: 0 }),
    enabled: Boolean(leadId),
  });
}
