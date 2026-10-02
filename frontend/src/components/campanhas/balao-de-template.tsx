"use client";

import { CheckCheck } from "lucide-react";

import { interpolarCorpoDoTemplate } from "@/lib/atendimento/variaveis-do-template";
import type { CampoDoLead, VariavelDaCampanha } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

/** Valores do contato de exemplo, vindos do catálogo de textos (nada de dado real na prévia). */
export function useContatoDeExemplo(): Record<CampoDoLead, string> {
  const textos = useTextos().campanhas.passoTemplate;
  return {
    PRIMEIRO_NOME: textos.contatoDeExemplo.split(" ")[0] ?? textos.contatoDeExemplo,
    NOME_COMPLETO: textos.contatoDeExemplo,
    EMPRESA: textos.empresaDeExemplo,
    LOCALIZACAO: textos.localizacaoDeExemplo,
  };
}

/** Valores das variáveis `{{n}}` na ordem, com o contato de exemplo (ou a reserva, se faltar o dado). */
export function valoresDeExemplo(
  variaveis: VariavelDaCampanha[],
  contato: Record<CampoDoLead, string>,
): string[] {
  const ordenadas = [...variaveis].sort((a, b) => a.posicao - b.posicao);
  return ordenadas.map((variavel) => contato[variavel.campo] || variavel.reserva);
}

interface Props {
  corpo: string;
  variaveis: VariavelDaCampanha[];
}

/** Prévia do template como bolha de WhatsApp recebida, com o contato de exemplo já preenchido. */
export function BalaoDeTemplate({ corpo, variaveis }: Props) {
  const textos = useTextos();
  const contato = useContatoDeExemplo();
  const mensagem = interpolarCorpoDoTemplate(corpo, valoresDeExemplo(variaveis, contato));
  return (
    <figure className="m-0 rounded-xl border border-border bg-muted p-4" aria-label={textos.campanhas.passoTemplate.previa}>
      <figcaption className="mb-3 text-center text-xs font-bold tracking-wide text-muted-foreground">
        {textos.app.marca} → {textos.campanhas.passoTemplate.contatoDeExemplo}
      </figcaption>
      <div className="flex justify-start">
        <div className="max-w-[88%] rounded-lg rounded-tl-sm bg-card px-3 py-2 text-sm text-card-foreground shadow-sm">
          <p className="whitespace-pre-wrap break-words leading-relaxed">{mensagem}</p>
          <p className="mt-1 flex items-center justify-end gap-1 text-[0.625rem] text-muted-foreground">
            <span>09:14</span>
            <CheckCheck className="size-3 text-cor-info" aria-hidden />
          </p>
        </div>
      </div>
    </figure>
  );
}
