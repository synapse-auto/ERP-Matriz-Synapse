"use client";

import { useState } from "react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { useFinalizarAtendimento } from "@/lib/atendimento/use-transferir-finalizar";
import type { CartaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { iniciaisDoNome, urlSegura } from "@/lib/utils";

import { DialogoTransferir } from "./dialogo-transferir";
import { AtalhoTags } from "./atalho-tags";

type Props = {
  conversa: CartaoAtendimento;
};

/** Identificação da conversa, tags persistidas e ações operacionais. */
export function CabecalhoConversa({ conversa }: Props) {
  const textos = useTextos().atendimentos.cabecalho;
  const [transferirAberto, setTransferirAberto] = useState(false);
  const finalizar = useFinalizarAtendimento();
  const finalizado = conversa.status === "FINALIZADO";

  const subtitulo = [
    conversa.leadEmpresa,
    conversa.atendenteNome ? `${textos.atendidoPor} ${conversa.atendenteNome}` : textos.semAtendente,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div className="flex items-center justify-between border-b border-border p-3">
      <div className="flex min-w-0 items-center gap-3">
        <Avatar>
          {urlSegura(conversa.leadFotoUrl) && (
            <AvatarImage src={urlSegura(conversa.leadFotoUrl)} alt={conversa.leadNome} />
          )}
          <AvatarFallback>{iniciaisDoNome(conversa.leadNome)}</AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <p className="truncate font-medium text-foreground">{conversa.leadNome}</p>
          <p className="truncate text-xs text-muted-foreground">{subtitulo}</p>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <AtalhoTags leadId={conversa.leadId} />
        {!finalizado && (
          <>
          <Button type="button" variant="outline" size="sm" onClick={() => setTransferirAberto(true)}>
            {textos.transferir}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => finalizar.mutate(conversa.atendimentoId)}
            disabled={finalizar.isPending}
          >
            {textos.finalizar}
          </Button>
          </>
        )}
      </div>

      <DialogoTransferir
        atendimentoId={conversa.atendimentoId}
        aberto={transferirAberto}
        onFechar={() => setTransferirAberto(false)}
      />
    </div>
  );
}
