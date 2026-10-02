"use client";

import { Label } from "@/components/ui/label";
import { Seletor } from "@/components/ui/seletor";
import { alternarDia, janelaValida } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";
import { cn } from "@/lib/utils";

const DIAS = [1, 2, 3, 4, 5, 6, 7] as const;
const HORARIOS = Array.from({ length: 48 }, (_, indice) => {
  const hora = String(Math.floor(indice / 2)).padStart(2, "0");
  return `${hora}:${indice % 2 === 0 ? "00" : "30"}`;
}).map((horario) => ({ valor: horario, rotulo: horario }));

interface Props {
  inicio: string;
  fim: string;
  dias: number[];
  aoMudar: (mudanca: { janelaInicio?: string; janelaFim?: string; dias?: number[] }) => void;
}

/** Horário (de meia em meia hora) e dias da semana em que a campanha pode enviar. */
export function JanelaDeEnvio({ inicio, fim, dias, aoMudar }: Props) {
  const textos = useTextos().campanhas.passoRitmo;
  const janelaOk = janelaValida(inicio, fim);
  return (
    <fieldset className="space-y-3">
      <legend className="text-sm font-bold">{textos.janela}</legend>
      <div className="grid max-w-md grid-cols-2 gap-3">
        <div className="space-y-1.5">
          <Label htmlFor="janela-inicio">{textos.janelaInicio}</Label>
          <Seletor id="janela-inicio" valor={inicio} opcoes={HORARIOS} placeholder={textos.janelaInicio} onChange={(janelaInicio) => aoMudar({ janelaInicio })} />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="janela-fim">{textos.janelaFim}</Label>
          <Seletor id="janela-fim" valor={fim} opcoes={HORARIOS} placeholder={textos.janelaFim} onChange={(janelaFim) => aoMudar({ janelaFim })} />
        </div>
      </div>
      {!janelaOk && (
        <p role="alert" className="text-xs text-destructive">
          {textos.janelaInvalida}
        </p>
      )}
      <div className="space-y-1.5">
        <p className="text-sm font-medium">{textos.dias}</p>
        <div className="flex flex-wrap gap-2" role="group" aria-label={textos.dias}>
          {DIAS.map((dia) => {
            const ativo = dias.includes(dia);
            return (
              <button
                key={dia}
                type="button"
                aria-pressed={ativo}
                aria-label={textos.diasLongos[String(dia) as keyof typeof textos.diasLongos]}
                onClick={() => aoMudar({ dias: alternarDia(dias, dia) })}
                className={cn(
                  "h-8 min-w-12 rounded-md border px-2 text-sm font-medium outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50",
                  ativo ? "border-primary bg-primary text-primary-foreground" : "border-border bg-card hover:bg-muted",
                )}
              >
                {textos.diasCurtos[String(dia) as keyof typeof textos.diasCurtos]}
              </button>
            );
          })}
        </div>
        <p className={cn("text-xs", dias.length === 0 ? "text-destructive" : "text-muted-foreground")}>{textos.diasAjuda}</p>
      </div>
    </fieldset>
  );
}
