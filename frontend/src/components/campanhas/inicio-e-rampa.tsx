"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioItem } from "@/components/ui/radio-group";
import { SeletorDataHora } from "@/components/ui/seletor-data-hora";
import { Switch } from "@/components/ui/switch";
import type { EstadoDoAssistente } from "@/lib/campanhas/estado-do-assistente";
import { useTextos } from "@/lib/config/textos-provider";

import { lerInteiro } from "./campo-de-limite";

interface Props {
  estado: EstadoDoAssistente;
  teto: number;
  rampaValida: boolean;
  aoMudar: (mudanca: Partial<EstadoDoAssistente>) => void;
}

export function InicioDaCampanha({ estado, aoMudar }: Pick<Props, "estado" | "aoMudar">) {
  const textos = useTextos().campanhas.passoRitmo;
  return (
    <fieldset className="space-y-3">
      <legend className="text-sm font-bold">{textos.inicio}</legend>
      <RadioGroup
        value={estado.modoDeInicio}
        onValueChange={(modo) => aoMudar({ modoDeInicio: modo as "AGORA" | "AGENDADA" })}
        className="gap-2"
      >
        <RadioItem value="AGORA">{textos.iniciarAgora}</RadioItem>
        <RadioItem value="AGENDADA">{textos.agendar}</RadioItem>
      </RadioGroup>
      {estado.modoDeInicio === "AGENDADA" && (
        <div className="max-w-md space-y-1.5">
          <Label htmlFor="agendada-para">{textos.agendadaPara}</Label>
          <SeletorDataHora
            id="agendada-para"
            valor={estado.agendadaPara}
            placeholderData={textos.agendadaPara}
            rotuloHora={textos.janelaInicio}
            rotuloMinuto={textos.janelaFim}
            onChange={(agendadaPara) => aoMudar({ agendadaPara })}
          />
        </div>
      )}
    </fieldset>
  );
}

export function RampaDeLimite({ estado, rampaValida, aoMudar }: Props) {
  const textos = useTextos().campanhas.passoRitmo;
  return (
    <fieldset className="space-y-3">
      <legend className="sr-only">{textos.rampa}</legend>
      <label className="flex items-start gap-3">
        <Switch checked={estado.rampaAtiva} onCheckedChange={(rampaAtiva) => aoMudar({ rampaAtiva })} />
        <span>
          <span className="block text-sm font-bold">{textos.rampa}</span>
          <span className="block text-xs text-muted-foreground">{textos.rampaAjuda}</span>
        </span>
      </label>
      {estado.rampaAtiva && (
        <div className="grid max-w-md grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="rampa-incremento">{textos.rampaIncremento}</Label>
            <Input
              id="rampa-incremento"
              type="number"
              min={1}
              value={estado.rampaIncremento ?? ""}
              onChange={(evento) => aoMudar({ rampaIncremento: lerInteiro(evento.target.value) })}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="rampa-teto">{textos.rampaTeto}</Label>
            <Input
              id="rampa-teto"
              type="number"
              min={1}
              value={estado.rampaTeto ?? ""}
              aria-invalid={!rampaValida}
              onChange={(evento) => aoMudar({ rampaTeto: lerInteiro(evento.target.value) })}
            />
          </div>
          {!rampaValida && (
            <p role="alert" className="col-span-2 text-xs text-destructive">
              {textos.rampaInvalida}
            </p>
          )}
        </div>
      )}
    </fieldset>
  );
}
