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
import { statusHttpDoErro } from "@/lib/atendimento/abertura-atendimento";
import { listarDestinosDeTransferencia } from "@/lib/atendimento/api";
import type { StatusAtendimento } from "@/lib/atendimento/types";
import { useTransferirAtendimento } from "@/lib/atendimento/use-transferir-finalizar";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import { recebeAtendimento } from "@/lib/equipe/papel";
import { useCapacidades } from "@/lib/gestao/use-capacidades";

import { SeletorDestinoAtendimento } from "./seletor-destino-atendimento";

type Props = {
  atendimentoId: string;
  /** Estado e responsável atuais: decidem quais saídas fazem sentido e quais o backend recusaria. */
  status: StatusAtendimento;
  responsavelId: string | null;
  aberto: boolean;
  onFechar: () => void;
};

export function DialogoTransferir({ atendimentoId, status, responsavelId, aberto, onFechar }: Props) {
  const catalogo = useTextos().atendimentos;
  const textos = catalogo.transferir;
  const papel = useAuthStore((estado) => estado.papel);
  const usuarioId = useAuthStore((estado) => estado.usuarioId);
  const transferir = useTransferirAtendimento();
  const capacidades = useCapacidades();
  const podeDevolver = capacidades.pode("atendimentos.devolver_ia");
  const podeTransferir = capacidades.pode("atendimentos.transferir");
  const potencial = status === "EM_IA";
  // RN-CRM-01/02: quem não alcança todos os leads não escolhe o destino de um Potencial — o backend
  // recusa com 403. Oferecer os colegas e deixar o clique falhar era o "não consigo transferir".
  const podeEscolherDestino = podeTransferir && (!potencial || capacidades.alcancaTodos);
  // Lista estreita (id + nome). GET /api/v1/usuarios continua restrito à gestão e não cabe aqui.
  const destinos = useQuery({
    queryKey: ["destinos-de-transferencia"],
    queryFn: listarDestinosDeTransferencia,
    enabled: aberto && podeEscolherDestino,
  });

  // Devolver o que já está com a IA e assumir o que já é seu não mudam nada: não aparecem.
  const mostrarDevolver = podeDevolver && !potencial;
  const eu =
    podeTransferir && recebeAtendimento(papel) && usuarioId && responsavelId !== usuarioId
      ? { id: usuarioId }
      : undefined;
  const destinosExcluidos = new Set([usuarioId, responsavelId].filter((id): id is string => Boolean(id)));
  const destinosDisponiveis = (destinos.data ?? []).filter((destino) => !destinosExcluidos.has(destino.id));

  function transferirPara(paraAtendenteId: string | null, destinoNome: string | null) {
    transferir.mutate({ atendimentoId, paraAtendenteId, destinoNome }, { onSuccess: onFechar });
  }

  return (
    <Dialog open={aberto && (podeDevolver || podeTransferir)} onOpenChange={(valor) => !valor && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.titulo}</DialogTitle>
          <DialogDescription>{potencial && !podeEscolherDestino ? textos.descricaoPotencial : textos.descricao}</DialogDescription>
        </DialogHeader>

        <div className="max-h-64 space-y-1 overflow-y-auto">
          {mostrarDevolver && (
            <Button
              type="button"
              variant="outline"
              className="w-full justify-start"
              disabled={transferir.isPending}
              onClick={() => transferirPara(null, null)}
            >
              {textos.devolverParaIa}
            </Button>
          )}
          {eu && (
            <Button
              type="button"
              variant="outline"
              className="w-full justify-start"
              disabled={transferir.isPending}
              onClick={() => transferirPara(eu.id, null)}
            >
              {textos.assumirParaMim}
            </Button>
          )}
          {podeEscolherDestino && destinos.isPending && (
            <p className="px-1 py-2 text-sm text-muted-foreground" role="status">
              {textos.carregandoDestinos}
            </p>
          )}
          {podeEscolherDestino && destinos.isError && (
            <div className="flex items-center justify-between gap-2 px-1 py-2 text-sm" role="alert">
              <span className="text-destructive">{textos.erroDestinos}</span>
              <Button type="button" variant="ghost" size="sm" onClick={() => void destinos.refetch()}>
                {textos.tentarNovamente}
              </Button>
            </div>
          )}
          {podeEscolherDestino && destinos.isSuccess && destinosDisponiveis.length === 0 && (
            <p className="px-1 py-2 text-sm text-muted-foreground">{textos.semDestinos}</p>
          )}
          {podeEscolherDestino && destinosDisponiveis.length > 0 && (
            <SeletorDestinoAtendimento
              key={aberto ? "aberto" : "fechado"}
              destinos={destinosDisponiveis}
              desabilitado={transferir.isPending}
              aberto={aberto}
              textos={{ outros: catalogo.cabecalho.outros, voltar: catalogo.cabecalho.voltar }}
              onSelecionar={(destino) => transferirPara(destino.id, destino.nome)}
            />
          )}
        </div>

        {transferir.isError && (
          <p className="text-sm text-destructive" role="alert">
            {mensagemDaRecusa(statusHttpDoErro(transferir.error), textos)}
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

/** Recusa do backend em linguagem de operação; o detalhe técnico do RFC 7807 não chega à tela. */
function mensagemDaRecusa(
  status: number | undefined,
  textos: { erro: string; erroPermissao: string; erroDestino: string; erroFinalizado: string; erroIndisponivel: string },
): string {
  if (status === 403) return textos.erroPermissao;
  if (status === 422) return textos.erroDestino;
  if (status === 409) return textos.erroFinalizado;
  if (status === 404) return textos.erroIndisponivel;
  return textos.erro;
}
