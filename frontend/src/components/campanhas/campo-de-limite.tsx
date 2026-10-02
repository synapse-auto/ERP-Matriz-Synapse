"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { formatarNumero } from "@/lib/campanhas/formatacao";
import { atalhosDeLimite, limiteAcimaDaMeta, validarLimiteDiario } from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";

import { AvisoDeAtencao } from "./aviso";

interface Props {
  id: string;
  rotulo: string;
  valor: number | null;
  teto: number;
  limiteMeta: number;
  aoMudar: (valor: number | null) => void;
  /** Mostra os avisos de erro mesmo sem a pessoa ter mexido no campo. */
  mostrarErros?: boolean;
  textoDeAjuda?: string;
}

export function lerInteiro(texto: string): number | null {
  if (texto.trim() === "") return null;
  const numero = Number(texto);
  return Number.isFinite(numero) ? Math.trunc(numero) : null;
}

/** Limite diário: campo numérico + controle deslizante + atalhos, sempre dentro do teto da instância. */
export function CampoDeLimite({ id, rotulo, valor, teto, limiteMeta, aoMudar, mostrarErros, textoDeAjuda }: Props) {
  const textos = useTextos().campanhas.passoRitmo;
  const resultado = validarLimiteDiario(valor, teto);
  const idErro = `${id}-erro`;
  const erro =
    resultado === "acima_do_teto"
      ? interpolarCatalogo(textos.limiteAcimaDoTeto, { teto: formatarNumero(teto) })
      : resultado === "invalido" && (mostrarErros || valor !== null)
        ? interpolarCatalogo(textos.limiteInvalido, { teto: formatarNumero(teto) })
        : null;

  return (
    <div className="space-y-3">
      <div className="space-y-1.5">
        <Label htmlFor={id}>{rotulo}</Label>
        <Input
          id={id}
          type="number"
          inputMode="numeric"
          min={1}
          max={teto}
          value={valor ?? ""}
          aria-invalid={erro !== null}
          aria-describedby={idErro}
          className="max-w-40 tabular-nums"
          onChange={(evento) => aoMudar(lerInteiro(evento.target.value))}
        />
        <input
          type="range"
          aria-label={textos.limiteDeslizante}
          min={1}
          max={teto}
          value={Math.min(Math.max(valor ?? 1, 1), teto)}
          onChange={(evento) => aoMudar(Number(evento.target.value))}
          className="h-6 w-full cursor-pointer accent-primary"
        />
        <div className="flex flex-wrap items-center gap-2" role="group" aria-label={textos.atalhos}>
          {atalhosDeLimite(teto).map((atalho) => (
            <Button
              key={atalho}
              type="button"
              size="xs"
              variant={valor === atalho ? "default" : "outline"}
              aria-pressed={valor === atalho}
              onClick={() => aoMudar(atalho)}
            >
              {atalho === teto ? `${textos.atalhoTeto} (${formatarNumero(atalho)})` : formatarNumero(atalho)}
            </Button>
          ))}
        </div>
      </div>
      <p id={idErro} role={erro ? "alert" : undefined} className={erro ? "text-xs text-destructive" : "text-xs text-muted-foreground"}>
        {erro ?? textoDeAjuda ?? interpolarCatalogo(textos.limiteAjuda, { teto: formatarNumero(teto) })}
      </p>
      {limiteMeta > 0 && (
        <p className="text-xs text-muted-foreground">
          {interpolarCatalogo(textos.limiteMeta, { meta: formatarNumero(limiteMeta) })}
        </p>
      )}
      {limiteAcimaDaMeta(valor, limiteMeta) && (
        <AvisoDeAtencao>{interpolarCatalogo(textos.limiteAcimaDaMeta, { meta: formatarNumero(limiteMeta) })}</AvisoDeAtencao>
      )}
    </div>
  );
}
