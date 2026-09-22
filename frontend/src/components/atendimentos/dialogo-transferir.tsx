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
import { recebeAtendimento } from "@/lib/equipe/papel";

import { SeletorDestinoAtendimento } from "./seletor-destino-atendimento";

type Props = {
  atendimentoId: string;
  aberto: boolean;
  onFechar: () => void;
};

export function DialogoTransferir({ atendimentoId, aberto, onFechar }: Props) {
  const catalogo = useTextos().atendimentos;
  const textos = catalogo.transferir;
  const papel = useAuthStore((estado) => estado.papel);
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const transferir = useTransferirAtendimento();
  // Lista estreita (id + nome). GET /api/v1/usuarios continua restrito à gestão e não cabe aqui.
  const { data: destinos } = useQuery({
    queryKey: ["destinos-de-transferencia"],
    queryFn: listarDestinosDeTransferencia,
    enabled: aberto,
  });

  const eu = recebeAtendimento(papel) && usuarioId ? { id: usuarioId } : undefined;
  const destinosExcluidos = new Set(usuarioId ? [usuarioId] : []);

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

        <div className="max-h-64 overflow-y-auto">
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
          <SeletorDestinoAtendimento
            key={aberto ? "aberto" : "fechado"}
            destinos={destinos ?? []}
            excluidos={destinosExcluidos}
            desabilitado={transferir.isPending}
            aberto={aberto}
            textos={{ outros: catalogo.cabecalho.outros, voltar: catalogo.cabecalho.voltar }}
            onSelecionar={(destino) => transferirPara(destino.id)}
          />
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
