"use client";

import { UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTextos } from "@/lib/config/textos-provider";
import type { ConviteRecebido } from "@/lib/atendimento/use-convite-recebido";

export function BloqueioConvite({ convite }: { convite: ConviteRecebido }) {
  const textos = useTextos().atendimentos.cabecalho;
  return <div className="min-h-0 overflow-y-auto bg-background px-4 pb-4 pt-3" data-slot="convite-pendente">
    <div className="mx-auto max-w-[780px] rounded-xl border border-border bg-card p-4 shadow-md">
      <div className="flex items-start gap-3">
        <UserPlus className="mt-0.5 size-(--tamanho-icone-interface) shrink-0 text-primary" aria-hidden />
        <div className="min-w-0 flex-1 space-y-1">
          <p className="text-sm font-medium">{textos.conviteComposerTitulo}</p>
          <p className="text-sm text-muted-foreground">{textos.conviteComposerDescricao}</p>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-2" aria-busy={convite.processando}>
        <Button onClick={convite.aceitar} disabled={convite.processando || !convite.podeResponder}>{textos.aceitarConvite}</Button>
        <Button variant="outline" onClick={convite.recusar} disabled={convite.processando || !convite.podeResponder}>{textos.recusarConvite}</Button>
      </div>
      {convite.feedback && <p role={convite.feedback.tipo === "erro" ? "alert" : "status"} className="mt-2 text-sm text-muted-foreground">{convite.feedback.texto}</p>}
    </div>
  </div>;
}
