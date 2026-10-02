"use client";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Seletor } from "@/components/ui/seletor";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import type { CampoDoLead, VariavelDaCampanha } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

const CAMPOS: CampoDoLead[] = ["PRIMEIRO_NOME", "NOME_COMPLETO", "EMPRESA", "LOCALIZACAO"];

interface Props {
  variaveis: VariavelDaCampanha[];
  aoMudar: (variaveis: VariavelDaCampanha[]) => void;
}

/** Cada `{{n}}` do template aponta para um campo do contato, com um texto de reserva se o dado faltar. */
export function MapeamentoDeVariaveis({ variaveis, aoMudar }: Props) {
  const textos = useTextos().campanhas.passoTemplate;
  const opcoes = CAMPOS.map((campo) => ({ valor: campo, rotulo: textos.campos[campo] }));

  if (variaveis.length === 0) {
    return <p className="text-sm text-muted-foreground">{textos.semVariaveis}</p>;
  }

  function alterar(posicao: number, mudanca: Partial<VariavelDaCampanha>) {
    aoMudar(variaveis.map((variavel) => (variavel.posicao === posicao ? { ...variavel, ...mudanca } : variavel)));
  }

  return (
    <ul className="space-y-3">
      {variaveis.map((variavel) => {
        const idCampo = `variavel-${variavel.posicao}-campo`;
        const idReserva = `variavel-${variavel.posicao}-reserva`;
        return (
          <li key={variavel.posicao} className="grid gap-3 rounded-lg border border-border p-3 sm:grid-cols-2">
            <p className="text-sm font-bold sm:col-span-2">
              {interpolarCatalogo(textos.variavelRotulo, { numero: `{{${variavel.posicao}}}` })}
            </p>
            <div className="space-y-1.5">
              <Label htmlFor={idCampo}>{textos.campo}</Label>
              <Seletor
                id={idCampo}
                valor={variavel.campo}
                opcoes={opcoes}
                placeholder={textos.campo}
                onChange={(campo) => alterar(variavel.posicao, { campo: campo as CampoDoLead })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor={idReserva}>{textos.reserva}</Label>
              <Input
                id={idReserva}
                value={variavel.reserva}
                maxLength={60}
                placeholder={textos.reservaPlaceholder}
                aria-invalid={variavel.reserva.trim().length === 0}
                onChange={(evento) => alterar(variavel.posicao, { reserva: evento.target.value })}
              />
              <p className="text-xs text-muted-foreground">{textos.reservaAjuda}</p>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
