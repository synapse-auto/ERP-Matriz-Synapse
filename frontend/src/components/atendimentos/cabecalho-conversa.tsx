"use client";

import { useState } from "react";
import {
  ArrowLeftRight,
  CheckCheck,
  MessageCircleMore,
  Phone,
  Search,
} from "lucide-react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button, buttonVariants } from "@/components/ui/button";
import { useFinalizarAtendimento } from "@/lib/atendimento/use-transferir-finalizar";
import type { CartaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { useLead } from "@/lib/lead/use-painel-lead";
import { cn, iniciaisDoNome, urlSegura } from "@/lib/utils";

import { DialogoTransferir } from "./dialogo-transferir";
import { AtalhoTags } from "./atalho-tags";

type Props = {
  conversa: CartaoAtendimento;
  onAlternarBusca: () => void;
  buscaAberta: boolean;
};

/** Identificação da conversa, tags persistidas e ações operacionais. */
export function CabecalhoConversa({
  conversa,
  onAlternarBusca,
  buscaAberta,
}: Props) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos.cabecalho;
  const [transferirAberto, setTransferirAberto] = useState(false);
  const finalizar = useFinalizarAtendimento();
  const lead = useLead(conversa.leadId);
  const finalizado = conversa.status === "FINALIZADO";
  const telefone = lead.data?.telefone ?? null;
  const canal =
    conversa.canalTipo === "WHATSAPP"
      ? catalogo.atendimentos.canais.whatsapp
      : conversa.canalTipo;

  const subtitulo = [
    telefone,
    lead.data?.empresa ?? conversa.leadEmpresa,
    conversa.atendenteNome
      ? `${textos.atendidoPor} ${conversa.atendenteNome}`
      : textos.semAtendente,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <div
      className="flex h-[72px] items-center justify-between border-b border-border bg-background px-5"
      data-slot="cabecalho-conversa"
    >
      <div className="flex min-w-0 items-center gap-3">
        <Avatar>
          {urlSegura(conversa.leadFotoUrl) && (
            <AvatarImage
              src={urlSegura(conversa.leadFotoUrl)}
              alt={conversa.leadNome}
            />
          )}
          <AvatarFallback>{iniciaisDoNome(conversa.leadNome)}</AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <p className="truncate font-bold text-foreground">
              {conversa.leadNome}
            </p>
            {canal && (
              <span className="inline-flex items-center gap-1 rounded-md bg-cor-sucesso/10 px-2 py-0.5 text-[0.7rem] font-semibold text-cor-sucesso">
                <MessageCircleMore className="size-3" aria-hidden />
                {canal}
              </span>
            )}
          </div>
          <p className="truncate text-xs text-muted-foreground">{subtitulo}</p>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        {!finalizado && (
          <>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setTransferirAberto(true)}
            >
              <ArrowLeftRight className="size-3.5" aria-hidden />
              {textos.transferir}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="border-cor-sucesso/25 bg-cor-sucesso/10 text-cor-sucesso hover:bg-cor-sucesso/15 hover:text-cor-sucesso"
              onClick={() => finalizar.mutate(conversa.atendimentoId)}
              disabled={finalizar.isPending}
            >
              <CheckCheck className="size-3.5" aria-hidden />
              {textos.finalizar}
            </Button>
          </>
        )}
        <span className="mx-1 h-5 w-px bg-border" aria-hidden />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={textos.buscar}
          aria-pressed={buscaAberta}
          onClick={onAlternarBusca}
        >
          <Search className="size-4" aria-hidden />
        </Button>
        <AtalhoTags leadId={conversa.leadId} />
        {telefone && (
          <a
            href={`tel:${telefone.replace(/[^+\d]/g, "")}`}
            aria-label={`${catalogo.painelLead.dados.telefone}: ${telefone}`}
            className={cn(buttonVariants({ variant: "ghost", size: "icon" }))}
          >
            <Phone className="size-4" aria-hidden />
          </a>
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
