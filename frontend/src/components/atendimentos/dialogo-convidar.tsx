"use client";

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
  participantes: ParticipanteAtendimento[];
  aberto: boolean;
  onFechar: () => void;
  onSucesso?: () => void;
};

/** Convites usam a mesma lista estreita de destinos elegíveis da transferência. */
export function DialogoConvidar({
  atendimentoId,
  participantes,
  aberto,
  onFechar,
  onSucesso,
}: Props) {
  const catalogo = useTextos().atendimentos;
  const textos = catalogo.cabecalho;
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const destinos = useQuery({
    queryKey: ["destinos-de-transferencia"],
    queryFn: listarDestinosDeTransferencia,
    enabled: aberto,
  });
  const convite = useMutation({
    mutationFn: (atendenteId: string) =>
      convidarParaAtendimento(atendimentoId, atendenteId, crypto.randomUUID()),
    onSuccess: () => {
      onSucesso?.();
      onFechar();
    },
  });

  const participantesIds = new Set(participantes.map((participante) => participante.usuarioId));
  const candidatos = destinos.data ?? [];
  const destinosExcluidos = new Set(
    [usuarioId, ...participantesIds].filter((id): id is string => Boolean(id)),
  );

  return (
    <Dialog open={aberto} onOpenChange={(valor) => !valor && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.convidarTitulo}</DialogTitle>
          <DialogDescription>{textos.convidarDescricao}</DialogDescription>
        </DialogHeader>

        {destinos.isLoading && <p className="text-sm text-muted-foreground">{textos.convidarCarregando}</p>}
        {!destinos.isLoading && candidatos.length === 0 && (
          <p className="text-sm text-muted-foreground">{textos.convidarVazio}</p>
        )}
        <div className="max-h-64 overflow-y-auto">
          <SeletorDestinoAtendimento
            key={aberto ? "aberto" : "fechado"}
            destinos={candidatos}
            excluidos={destinosExcluidos}
            desabilitado={convite.isPending}
            aberto={aberto}
            textos={{ outros: textos.outros, voltar: textos.voltar }}
            onSelecionar={(destino) => convite.mutate(destino.id)}
          />
        </div>
        {convite.isError && <p className="text-sm text-destructive">{textos.convidarErro}</p>}
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onFechar} disabled={convite.isPending}>
            {catalogo.transferir.cancelar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
