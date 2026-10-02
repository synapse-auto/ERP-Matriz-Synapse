"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useTextos } from "@/lib/config/textos-provider";

import { lerInteiro } from "./campo-de-limite";

export interface CampoNumericoProps {
  id: string;
  rotulo: string;
  ajuda?: string;
  valor: number | null;
  invalido: boolean;
  desabilitado: boolean;
  aoMudar: (valor: number | null) => void;
}

/** Campo inteiro com rótulo, ajuda e erro ligados por aria-describedby. */
export function CampoNumerico({ id, rotulo, ajuda, valor, invalido, desabilitado, aoMudar }: CampoNumericoProps) {
  const textos = useTextos().campanhas.configuracaoDaInstancia;
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{rotulo}</Label>
      <Input
        id={id}
        type="number"
        value={valor ?? ""}
        disabled={desabilitado}
        aria-invalid={invalido}
        aria-describedby={`${id}-ajuda`}
        className="max-w-48 tabular-nums"
        onChange={(evento) => aoMudar(lerInteiro(evento.target.value))}
      />
      <p id={`${id}-ajuda`} className={invalido ? "text-xs text-destructive" : "text-xs text-muted-foreground"}>
        {invalido ? textos.valorInvalido : (ajuda ?? "")}
      </p>
    </div>
  );
}
