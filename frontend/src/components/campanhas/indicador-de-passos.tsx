"use client";

import { Check } from "lucide-react";

import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { PASSOS } from "@/lib/campanhas/passos";
import { useTextos } from "@/lib/config/textos-provider";
import { cn } from "@/lib/utils";

import { BarraDeProgresso } from "./barra-de-progresso";

/** Passos numerados com a barra de progresso; o passo atual leva aria-current="step". */
export function IndicadorDePassos({ passo, concluidos }: { passo: number; concluidos: number }) {
  const textos = useTextos().campanhas.assistente;
  return (
    <nav aria-label={textos.rotuloProgresso} className="space-y-3">
      <BarraDeProgresso
        valor={((passo + 1) / PASSOS.length) * 100}
        rotulo={interpolarCatalogo(textos.passoDe, { passo: String(passo + 1), total: String(PASSOS.length) })}
      />
      <ol className="grid grid-cols-4 gap-2">
        {PASSOS.map((chave, indice) => {
          const feito = indice < concluidos && indice !== passo;
          const atual = indice === passo;
          return (
            <li key={chave} aria-current={atual ? "step" : undefined} className="flex min-w-0 items-center gap-2">
              <span
                className={cn(
                  "flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-bold",
                  atual && "bg-primary text-primary-foreground",
                  feito && "bg-cor-sucesso text-primary-foreground",
                  !atual && !feito && "bg-muted text-muted-foreground",
                )}
              >
                {feito ? <Check className="size-3.5" aria-hidden /> : indice + 1}
              </span>
              <span className={cn("hidden truncate text-sm sm:inline", atual ? "font-bold" : "text-muted-foreground")}>
                {textos.passos[chave]}
              </span>
            </li>
          );
        })}
      </ol>
      <p className="text-sm font-bold sm:hidden" aria-hidden>
        {textos.passos[PASSOS[passo]]}
      </p>
    </nav>
  );
}
