"use client";

import { useState } from "react";
import { Check, Copy, Phone, UserRound } from "lucide-react";

import {
  contatosDaMensagem,
  numeroDiscavel,
  type TelefoneDoContato,
} from "@/lib/atendimento/contato-compartilhado";
import { copiarTexto } from "@/lib/mensagens/copiar-texto";

/**
 * Textos da bolha. As chaves específicas de contato são opcionais para não reprovar o catálogo de
 * um filho publicado antes delas; sem elas, a bolha mostra só o dado e usa as ações genéricas.
 */
export interface TextosDaBolhaContato {
  contato?: string;
  contatoSemNome?: string;
  contatoSemTelefone?: string;
  copiarTelefone?: string;
  ligarPara?: string;
  copiar: string;
  copiada: string;
  copiarErro: string;
}

export function BolhaContato({
  midiaMetadados,
  textos,
}: {
  midiaMetadados: string | null;
  textos: TextosDaBolhaContato;
}) {
  const contatos = contatosDaMensagem(midiaMetadados);

  return (
    <div className="flex flex-col gap-2" aria-label={textos.contato}>
      {contatos.length === 0 && textos.contato && (
        <span className="font-semibold">{textos.contato}</span>
      )}
      {contatos.map((contato, indice) => (
        <div key={indice} className="flex flex-col gap-1.5 rounded-lg bg-background/10 p-2.5">
          <div className="flex items-center gap-3">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
              <UserRound className="size-5" aria-hidden />
            </div>
            <span className="min-w-0 flex-1 break-words font-semibold">
              {contato.nome ?? textos.contatoSemNome ?? contato.telefones[0]?.numero}
            </span>
          </div>
          {contato.telefones.length === 0 && textos.contatoSemTelefone && (
            <span className="text-xs opacity-70">{textos.contatoSemTelefone}</span>
          )}
          {contato.telefones.map((telefone, posicao) => (
            <LinhaDeTelefone key={`${telefone.numero}-${posicao}`} telefone={telefone} textos={textos} />
          ))}
        </div>
      ))}
    </div>
  );
}

function LinhaDeTelefone({
  telefone,
  textos,
}: {
  telefone: TelefoneDoContato;
  textos: TextosDaBolhaContato;
}) {
  const [copia, setCopia] = useState<"copiado" | "erro" | null>(null);
  const discavel = numeroDiscavel(telefone.numero);
  const rotuloLigar = textos.ligarPara?.replaceAll("{numero}", telefone.numero) ?? telefone.numero;

  async function copiar() {
    setCopia((await copiarTexto(telefone.numero)) ? "copiado" : "erro");
  }

  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="min-w-0 flex-1 break-all tabular-nums">{telefone.numero}</span>
      {telefone.tipo && <span className="shrink-0 text-xs opacity-70">{telefone.tipo}</span>}
      {discavel && (
        <>
          <a
            href={`tel:${discavel}`}
            aria-label={rotuloLigar}
            title={rotuloLigar}
            className="inline-flex size-8 shrink-0 items-center justify-center rounded-md bg-background/20 transition-colors hover:bg-background/30"
          >
            <Phone className="size-4" aria-hidden />
          </a>
          <button
            type="button"
            onClick={() => void copiar()}
            aria-label={textos.copiarTelefone ?? textos.copiar}
            title={textos.copiarTelefone ?? textos.copiar}
            className="inline-flex size-8 shrink-0 items-center justify-center rounded-md bg-background/20 transition-colors hover:bg-background/30"
          >
            {copia === "copiado" ? <Check className="size-4" aria-hidden /> : <Copy className="size-4" aria-hidden />}
          </button>
        </>
      )}
      <span className="sr-only" aria-live="polite">
        {copia === "copiado" ? textos.copiada : copia === "erro" ? textos.copiarErro : ""}
      </span>
    </div>
  );
}
