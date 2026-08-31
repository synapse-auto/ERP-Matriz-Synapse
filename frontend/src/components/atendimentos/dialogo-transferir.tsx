"use client";

import { useQuery } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { listarDestinosDeTransferencia } from "@/lib/atendimento/api";
import { useTransferirAtendimento } from "@/lib/atendimento/use-transferir-finalizar";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

type Props = {
  atendimentoId: string;
  aberto: boolean;
  onFechar: () => void;
};

export function DialogoTransferir({ atendimentoId, aberto, onFechar }: Props) {
  const textos = useTextos().atendimentos.transferir;
  const papel = useAuthStore((estado) => estado.papel);
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const transferir = useTransferirAtendimento();
  // Lista estreita (id + nome). GET /api/v1/usuarios continua restrito à gestão e não cabe aqui.
  const { data: destinos } = useQuery({
    queryKey: ["destinos-de-transferencia"],
    queryFn: listarDestinosDeTransferencia,
    enabled: aberto,
  });

  const candidatos = (destinos ?? []).filter((destino) => destino.id !== usuarioId);
  const eu = papel === "ATENDENTE" && usuarioId ? { id: usuarioId } : undefined;

  function transferirPara(paraAtendenteId: string | null) {
    transferir.mutate({ atendimentoId, paraAtendenteId }, { onSuccess: onFechar });
  }

  return (
    <Dialog open={aberto} onOpenChange={(valor) => !valor && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.titulo}</DialogTitle>
          <DialogDescription>{textos.descricao}</DialogDescription>
        </DialogHeader>

        <div className="max-h-64 space-y-1 overflow-y-auto">
          <Button
            type="button"
            variant="outline"
            className="w-full justify-start"
            disabled={transferir.isPending}
            onClick={() => transferirPara(null)}
          >
            {textos.devolverParaIa}
          </Button>
          {eu && (
            <Button
              type="button"
              variant="outline"
              className="w-full justify-start"
              disabled={transferir.isPending}
              onClick={() => transferirPara(eu.id)}
            >
              {textos.assumirParaMim}
            </Button>
          )}
          {candidatos.map((destino) => (
            <Button
              key={destino.id}
              type="button"
              variant="outline"
              className="w-full justify-start"
              disabled={transferir.isPending}
              onClick={() => transferirPara(destino.id)}
            >
              {destino.nome}
            </Button>
          ))}
        </div>

        {transferir.isError && (
          <p className="text-sm text-destructive">
            {transferir.error instanceof Error ? transferir.error.message : textos.erro}
          </p>
        )}

        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onFechar}>
            {textos.cancelar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
