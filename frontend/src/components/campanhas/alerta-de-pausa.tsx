"use client";

import { ShieldAlert } from "lucide-react";

import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarDataHora } from "@/lib/campanhas/formatacao";
import type { Campanha } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

/** Alerta de destaque da pausa automática: o motivo e o que fazer, antes de qualquer outro bloco. */
export function AlertaDePausa({ campanha }: { campanha: Campanha }) {
  const textos = useTextos().campanhas.detalhe.alertaPausa;
  return (
    <section role="alert" className="rounded-xl border border-cor-erro/40 bg-cor-erro/10 p-4">
      <div className="flex items-start gap-3">
        <ShieldAlert className="mt-0.5 size-5 shrink-0 text-cor-erro" aria-hidden />
        <div className="space-y-2">
          <h2 className="text-base font-bold text-cor-erro">{textos.titulo}</h2>
          <p className="text-sm font-medium">
            {interpolarCatalogo(textos.motivo, { motivo: campanha.motivoDePausa ?? "-" })}
          </p>
          {campanha.pausadaEm && (
            <p className="text-xs text-muted-foreground">
              {interpolarCatalogo(textos.quando, { data: formatarDataHora(campanha.pausadaEm) })}
            </p>
          )}
          <div className="text-sm">
            <p className="font-bold">{textos.oQueFazer}</p>
            <ol className="mt-1 list-decimal space-y-0.5 pl-5 text-muted-foreground">
              <li>{textos.passo1}</li>
              <li>{textos.passo2}</li>
              <li>{textos.passo3}</li>
            </ol>
          </div>
        </div>
      </div>
    </section>
  );
}
