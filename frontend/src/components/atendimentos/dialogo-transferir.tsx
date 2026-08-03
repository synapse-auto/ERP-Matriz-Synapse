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
import { listarUsuarios } from "@/lib/atendimento/api";
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
  const email = useAuthStore((estado) => estado.email);
  const papel = useAuthStore((estado) => estado.papel);
  const transferir = useTransferirAtendimento();
  // GET /api/v1/usuarios é restrito a quem enxerga toda a equipe — um ATENDENTE toma 403 nele. Para
  // esse papel a trava de autorização do backend só permite devolver pra IA de qualquer forma, então
  // a lista de colegas nem apareceria com efeito — pular a chamada evita o 403 sem perder nada.
  const podeVerEquipe = papel !== null && papel !== "ATENDENTE";
  const { data: usuarios } = useQuery({
    queryKey: ["usuarios"],
    queryFn: listarUsuarios,
    enabled: aberto && podeVerEquipe,
  });

  const candidatos = (usuarios ?? []).filter((usuario) => usuario.papel === "ATENDENTE" && usuario.ativo);
  const eu = usuarios?.find((usuario) => usuario.email === email);

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
          {candidatos.map((usuario) => (
            <Button
              key={usuario.id}
              type="button"
              variant="outline"
              className="w-full justify-start"
              disabled={transferir.isPending}
              onClick={() => transferirPara(usuario.id)}
            >
              {usuario.nome}
            </Button>
          ))}
        </div>

        {transferir.isError && <p className="text-sm text-destructive">{textos.erro}</p>}

        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onFechar}>
            {textos.cancelar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
