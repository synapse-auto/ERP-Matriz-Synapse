"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

import { CartaoConversa } from "@/components/atendimentos/cartao-conversa";
import { PainelLateralLead } from "@/components/leads/painel-lateral-lead";
import type { CartaoAtendimento, VisaoAtendimento } from "@/lib/atendimento/types";
import { useAtendimentos } from "@/lib/atendimento/use-atendimentos";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

/** Agenda real, alimentada pelo mesmo recorte RLS dos atendimentos; nenhum card e mockado. */
export default function PaginaAgenda() {
  const textosGerais = useTextos();
  const textos = textosGerais.agenda;
  const router = useRouter();
  const papel = useAuthStore((estado) => estado.papel);
  const { data, isLoading, isError } = useAtendimentos("TODOS");
  const [leadNoPainel, setLeadNoPainel] = useState<string | null>(null);
  const contatos = (data ?? []).filter((cartao) => cartao.status !== "FINALIZADO");

  function abrirAtendimento(cartao: CartaoAtendimento) {
    const papelAmplo = papel && papel !== "ATENDENTE";
    const visao: VisaoAtendimento = cartao.status === "EM_IA" ? "POTENCIAIS" : papelAmplo ? "TODOS" : "ATIVOS";
    router.push(`/atendimentos?leadId=${encodeURIComponent(cartao.leadId)}&visao=${visao}`);
  }

  return (
    <div className="h-full overflow-y-auto p-6">
      <header className="mb-5">
        <h1 className="text-xl font-semibold text-foreground">{textos.titulo}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{textos.descricao}</p>
      </header>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">{textosGerais.estados.carregando}</p>
      ) : isError ? (
        <p role="alert" className="text-sm text-destructive">
          {textosGerais.estados.erroGenerico}
        </p>
      ) : contatos.length === 0 ? (
        <p className="text-sm text-muted-foreground">{textos.vazia}</p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-border bg-background">
          {contatos.map((cartao) => (
            <CartaoConversa
              key={cartao.atendimentoId}
              cartao={cartao}
              selecionado={false}
              onAbrirPainel={() => setLeadNoPainel(cartao.leadId)}
              onAbrirAtendimento={() => abrirAtendimento(cartao)}
            />
          ))}
        </div>
      )}

      {leadNoPainel && (
        <PainelLateralLead leadId={leadNoPainel} onFechar={() => setLeadNoPainel(null)} />
      )}
    </div>
  );
}
