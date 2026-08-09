"use client";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import type { EtapaAtendimento } from "@/lib/lead/types";
import type { UsuarioEquipe } from "@/lib/equipe/types";
import type { LeadDaAgenda, StatusBasicoLead } from "@/lib/agenda/types";
import type { Textos } from "@/lib/config/schema";
import { iniciaisDoNome } from "@/lib/utils";

type TextosAgenda = Textos["agenda"];

const CHAVE_DE_STATUS: Record<StatusBasicoLead, keyof TextosAgenda["status"]> = {
  IA: "ia",
  EM_ATENDIMENTO: "emAtendimento",
  FINALIZADO: "finalizado",
};

interface Props {
  leads: LeadDaAgenda[];
  etapas: EtapaAtendimento[];
  equipe: UsuarioEquipe[];
  textos: TextosAgenda;
  onAbrirFicha: (lead: LeadDaAgenda) => void;
  onAbrirAtendimento: (lead: LeadDaAgenda) => void;
}

export function TabelaDeLeads({ leads, etapas, equipe, textos, onAbrirFicha, onAbrirAtendimento }: Props) {
  const etapaPorId = new Map(etapas.map((etapa) => [etapa.id, etapa]));
  const nomePorAtendenteId = new Map(equipe.map((usuario) => [usuario.id, usuario.nome]));

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead className="bg-muted">
          <tr>
            <th className="p-2 text-left font-medium text-muted-foreground">{textos.colunas.lead}</th>
            <th className="p-2 text-left font-medium text-muted-foreground">{textos.colunas.telefone}</th>
            <th className="p-2 text-left font-medium text-muted-foreground">{textos.colunas.cidade}</th>
            <th className="p-2 text-left font-medium text-muted-foreground">{textos.colunas.etapa}</th>
            <th className="p-2 text-left font-medium text-muted-foreground">{textos.colunas.tags}</th>
            <th className="p-2 text-left font-medium text-muted-foreground">{textos.colunas.responsavel}</th>
            <th className="p-2 text-right font-medium text-muted-foreground">{textos.colunas.ultimoContato}</th>
          </tr>
        </thead>
        <tbody>
          {leads.map((lead) => {
            const etapa = lead.etapaAtendimentoId ? etapaPorId.get(lead.etapaAtendimentoId) : undefined;
            const nomeDoAtendente = lead.atendenteResponsavelId
              ? nomePorAtendenteId.get(lead.atendenteResponsavelId)
              : undefined;
            return (
              <tr
                key={lead.id}
                className="cursor-pointer border-t border-border hover:bg-muted"
                onClick={() => onAbrirFicha(lead)}
                onDoubleClick={() => onAbrirAtendimento(lead)}
              >
                <td className="p-2">
                  <div className="flex min-w-0 items-center gap-2.5">
                    <Avatar className="size-8">
                      <AvatarFallback>{iniciaisDoNome(lead.nome)}</AvatarFallback>
                    </Avatar>
                    <div className="min-w-0">
                      <p className="truncate font-medium text-foreground">{lead.nome}</p>
                      {lead.empresa && (
                        <p className="truncate text-xs text-muted-foreground">{lead.empresa}</p>
                      )}
                    </div>
                  </div>
                </td>
                <td className="p-2 font-mono text-xs text-muted-foreground">{lead.telefone ?? "—"}</td>
                <td className="p-2 text-muted-foreground">{lead.localizacao ?? "—"}</td>
                <td className="p-2">
                  {etapa ? (
                    <span
                      className="rounded-full px-2 py-0.5 text-xs font-medium"
                      style={
                        etapa.corVisual
                          ? { backgroundColor: `${etapa.corVisual}22`, color: etapa.corVisual }
                          : undefined
                      }
                    >
                      {etapa.nome}
                    </span>
                  ) : (
                    <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                      {textos.status[CHAVE_DE_STATUS[lead.status]]}
                    </span>
                  )}
                </td>
                <td className="p-2">
                  <div className="flex flex-wrap gap-1">
                    {lead.tags.map((tag) => (
                      <span
                        key={tag.tagId}
                        className="rounded-full border px-1.5 py-0.5 text-[0.65rem] font-medium"
                        style={{ borderColor: tag.cor, color: tag.cor }}
                      >
                        {tag.nome}
                      </span>
                    ))}
                  </div>
                </td>
                <td className="p-2 text-muted-foreground">
                  {nomeDoAtendente ?? (
                    <span className="italic">{textos.semResponsavel}</span>
                  )}
                </td>
                <td className="p-2 text-right text-xs text-muted-foreground">
                  {lead.ultimaInteracaoEm
                    ? new Intl.DateTimeFormat(undefined, { dateStyle: "short", timeStyle: "short" }).format(
                        new Date(lead.ultimaInteracaoEm),
                      )
                    : "—"}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
