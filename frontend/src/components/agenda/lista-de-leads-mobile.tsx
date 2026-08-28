"use client";

import { ChevronRight } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import type { EtapaAtendimento } from "@/lib/lead/types";
import type { UsuarioEquipe } from "@/lib/equipe/types";
import type { LeadDaAgenda, StatusBasicoLead } from "@/lib/agenda/types";
import type { Textos } from "@/lib/config/schema";

type TextosAgenda = Textos["agenda"];

const CHAVE_DE_STATUS: Record<StatusBasicoLead, keyof TextosAgenda["status"]> = {
  IA: "ia",
  EM_ATENDIMENTO: "emAtendimento",
  FINALIZADO: "finalizado",
};

const COR_DE_STATUS: Record<StatusBasicoLead, string> = {
  IA: "var(--cor-ia)",
  EM_ATENDIMENTO: "var(--cor-atencao)",
  FINALIZADO: "var(--cor-sucesso)",
};

interface Props {
  leads: LeadDaAgenda[];
  etapas: EtapaAtendimento[];
  equipe: UsuarioEquipe[];
  textos: TextosAgenda;
  onAbrirFicha: (lead: LeadDaAgenda) => void;
}

function letraDoNome(nome: string): string {
  const primeira = nome.trim().charAt(0).toLocaleUpperCase("pt-BR");
  const base = primeira.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  return /^[A-Z]$/.test(base) ? base : "#";
}

export function ListaDeLeadsMobile({ leads, etapas, equipe, textos, onAbrirFicha }: Props) {
  const etapaPorId = new Map(etapas.map((etapa) => [etapa.id, etapa]));
  const nomePorAtendenteId = new Map(equipe.map((usuario) => [usuario.id, usuario.nome]));
  const grupos = new Map<string, LeadDaAgenda[]>();
  for (const lead of leads) {
    const letra = letraDoNome(lead.nome);
    const lista = grupos.get(letra) ?? [];
    lista.push(lead);
    grupos.set(letra, lista);
  }
  const letras = [...grupos.keys()].sort((a, b) => a.localeCompare(b, "pt-BR"));

  return (
    <div className="relative">
      <div className="divide-y divide-border">
        {letras.map((letra) => (
          <section key={letra} id={`agenda-letra-${letra}`} className="scroll-mt-3">
            <h2 className="sticky top-0 z-10 bg-muted/80 px-1 py-1.5 text-xs font-bold tracking-wide text-muted-foreground uppercase backdrop-blur-sm">
              {letra}
            </h2>
            <ul>
              {(grupos.get(letra) ?? []).map((lead) => {
                const etapa = lead.etapaAtendimentoId
                  ? etapaPorId.get(lead.etapaAtendimentoId)
                  : undefined;
                const nomeDoAtendente = lead.atendenteResponsavelId
                  ? nomePorAtendenteId.get(lead.atendenteResponsavelId)
                  : undefined;
                const subtitulo = [lead.empresa, lead.localizacao].filter(Boolean).join(" · ");
                return (
                  <li key={lead.id}>
                    <button
                      type="button"
                      onClick={() => onAbrirFicha(lead)}
                      className="flex w-full items-center gap-3 px-1 py-3 text-left"
                    >
                      <AvatarIniciais
                        id={lead.id}
                        nome={lead.nome}
                        className="flex size-11 shrink-0 items-center justify-center rounded-full text-xs font-bold text-primary-foreground"
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-bold text-foreground">{lead.nome}</span>
                        {subtitulo && (
                          <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                            {subtitulo}
                          </span>
                        )}
                        <span className="mt-1.5 flex min-w-0 items-center gap-1.5">
                          {etapa ? (
                            <span
                              className="max-w-[11rem] truncate rounded-full px-2 py-0.5 text-[0.65rem] font-semibold"
                              style={
                                etapa.corVisual
                                  ? {
                                      backgroundColor: `${etapa.corVisual}1f`,
                                      color: etapa.corVisual,
                                    }
                                  : undefined
                              }
                            >
                              {etapa.nome}
                            </span>
                          ) : (
                            <span
                              className="truncate rounded-full px-2 py-0.5 text-[0.65rem] font-semibold"
                              style={{
                                backgroundColor: `color-mix(in srgb, ${COR_DE_STATUS[lead.status]} 16%, transparent)`,
                                color: COR_DE_STATUS[lead.status],
                              }}
                            >
                              {textos.status[CHAVE_DE_STATUS[lead.status]]}
                            </span>
                          )}
                          <span className="truncate text-[0.65rem] text-muted-foreground">
                            {nomeDoAtendente ?? textos.semResponsavel}
                          </span>
                        </span>
                      </span>
                      <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                    </button>
                  </li>
                );
              })}
            </ul>
          </section>
        ))}
      </div>
      <nav
        aria-label={textos.indiceAlfabetico}
        className="pointer-events-none absolute inset-y-2 right-0 flex flex-col justify-center gap-0.5 text-[10px] font-bold text-muted-foreground"
      >
        {letras.map((letra) => (
          <a
            key={letra}
            href={`#agenda-letra-${letra}`}
            className="pointer-events-auto px-1"
          >
            {letra}
          </a>
        ))}
      </nav>
    </div>
  );
}
