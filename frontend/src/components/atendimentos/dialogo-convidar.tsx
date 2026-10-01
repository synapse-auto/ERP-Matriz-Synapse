"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  convidarParaAtendimento,
  listarDestinosDeTransferencia,
} from "@/lib/atendimento/api";
import type { ParticipanteAtendimento } from "@/lib/atendimento/types";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

import { SeletorDestinoAtendimento } from "./seletor-destino-atendimento";

type Props = {
  atendimentoId: string;
  responsavelId: string | null;
  responsavelNome: string | null;
  participantes: ParticipanteAtendimento[];
  aberto: boolean;
  onFechar: () => void;
  onSucesso?: () => void;
};

/**
 * Convite para participar: escolher um colega e confirmar. Convidar só chama `/convidar` — nunca
 * altera o responsável nem antecipa sucesso antes da resposta da API.
 *
 * Reaproveita o seletor visual da transferência, mas a elegibilidade é do convite: quem já é
 * responsável, quem já participa e o próprio usuário ficam de fora.
 */
export function DialogoConvidar(props: Props) {
  return (
    <Dialog open={props.aberto} onOpenChange={(valor) => !valor && props.onFechar()}>
      <DialogContent className="max-w-[calc(100%-2rem)] sm:max-w-md">
        {props.aberto && <ConteudoConvite {...props} />}
      </DialogContent>
    </Dialog>
  );
}

function ConteudoConvite({
  atendimentoId,
  responsavelId,
  responsavelNome,
  participantes,
  onFechar,
  onSucesso,
}: Props) {
  const catalogo = useTextos().atendimentos;
  const textos = catalogo.cabecalho;
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const [selecionadoId, setSelecionadoId] = useState<string | null>(null);
  const destinos = useQuery({
    queryKey: ["destinos-de-transferencia"],
    queryFn: listarDestinosDeTransferencia,
  });
  const convite = useMutation({
    mutationFn: (atendenteId: string) =>
      convidarParaAtendimento(atendimentoId, atendenteId, crypto.randomUUID()),
    onSuccess: () => {
      onSucesso?.();
      onFechar();
    },
  });

  const excluidos = new Set(
    [usuarioId, responsavelId, ...participantes.map((participante) => participante.usuarioId)]
      .filter((id): id is string => Boolean(id)),
  );
  const candidatos = (destinos.data ?? []).filter((destino) => !excluidos.has(destino.id));
  const nomesParticipantes = participantes.map((participante) => participante.nome).join(", ");
  const enviando = convite.isPending;

  return (
    <>
      <DialogHeader>
        <DialogTitle>{textos.convidarTitulo}</DialogTitle>
        <DialogDescription>{textos.convidarSemTransferencia}</DialogDescription>
      </DialogHeader>

      <dl className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-sm" data-testid="convite-equipe-atual">
        <dt className="text-muted-foreground">{textos.convidarResponsavel}</dt>
        <dd className="min-w-0 truncate font-medium" title={responsavelNome ?? undefined}>
          {responsavelNome ?? textos.semAtendente}
        </dd>
        <dt className="text-muted-foreground">{textos.convidarParticipantesAtuais}</dt>
        <dd className="min-w-0 truncate" title={nomesParticipantes || undefined}>
          {nomesParticipantes || textos.convidarSemParticipantes}
        </dd>
      </dl>

      <div role="group" aria-label={textos.convidarEscolha} className="min-w-0 space-y-2">
        <p className="text-sm font-medium">{textos.convidarEscolha}</p>
        {destinos.isLoading && (
          <p role="status" className="text-sm text-muted-foreground">{textos.convidarCarregando}</p>
        )}
        {destinos.isError && (
          <p role="alert" className="text-sm text-destructive">{textos.convidarErroCarregar}</p>
        )}
        {destinos.isSuccess && candidatos.length === 0 && (
          <p className="text-sm text-muted-foreground">{textos.convidarVazio}</p>
        )}
        <div className="max-h-64 min-w-0 overflow-y-auto">
          <SeletorDestinoAtendimento
            destinos={candidatos}
            desabilitado={enviando}
            aberto
            textos={{ outros: textos.outros, voltar: textos.voltar }}
            selecionadoId={selecionadoId}
            onSelecionar={(destino) => {
              convite.reset();
              setSelecionadoId(destino.id);
            }}
          />
        </div>
      </div>

      {convite.isError && (
        <p role="alert" className="text-sm text-destructive">{textos.convidarErro}</p>
      )}
      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onFechar} disabled={enviando}>
          {catalogo.transferir.cancelar}
        </Button>
        <Button
          type="button"
          disabled={!selecionadoId || enviando}
          onClick={() => selecionadoId && convite.mutate(selecionadoId)}
        >
          {enviando ? textos.convidarEnviando : textos.convidarConfirmar}
        </Button>
      </DialogFooter>
    </>
  );
}
