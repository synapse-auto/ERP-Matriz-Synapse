"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ErroDeApi } from "@/lib/api/errors";
import { encaminharMensagem, listarAtendimentos } from "@/lib/atendimento/api";
import type { MensagemResposta } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";

type Props = {
  origemAtendimentoId: string;
  origemLeadId: string;
  mensagem: MensagemResposta;
  aberto: boolean;
  onFechar: () => void;
};

export function DialogoEncaminhar({
  origemAtendimentoId,
  origemLeadId,
  mensagem,
  aberto,
  onFechar,
}: Props) {
  const textos = useTextos().atendimentos.encaminhar;
  const cache = useQueryClient();
  const [busca, setBusca] = useState("");
  const [destinoId, setDestinoId] = useState<string | null>(null);
  const conversas = useQuery({
    queryKey: ["atendimentos", "TODOS"],
    queryFn: () => listarAtendimentos("TODOS"),
    enabled: aberto,
  });
  const encaminhar = useMutation({
    mutationFn: (destinoAtendimentoId: string) =>
      encaminharMensagem(origemAtendimentoId, mensagem.id, mensagem.enviadoEm, destinoAtendimentoId),
    onSuccess: (resposta) => {
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
      void cache.invalidateQueries({ queryKey: ["mensagens", resposta.atendimentoId] });
      setDestinoId(null);
      setBusca("");
      onFechar();
    },
  });

  const candidatos = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    return (conversas.data ?? []).filter((cartao) => {
      if (cartao.atendimentoId === origemAtendimentoId) return false;
      if (cartao.leadId === origemLeadId) return false;
      if (!termo) return true;
      return (
        cartao.leadNome.toLowerCase().includes(termo)
        || (cartao.ultimaMensagemPreview ?? "").toLowerCase().includes(termo)
      );
    });
  }, [busca, conversas.data, origemAtendimentoId, origemLeadId]);

  function fechar() {
    if (encaminhar.isPending) return;
    setDestinoId(null);
    setBusca("");
    onFechar();
  }

  const erro =
    encaminhar.error instanceof ErroDeApi
      ? encaminhar.error.status === 422
        ? textos.incompativel
        : encaminhar.error.message
      : encaminhar.isError
        ? textos.erro
        : null;

  return (
    <Dialog open={aberto} onOpenChange={(valor) => !valor && fechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.titulo}</DialogTitle>
        </DialogHeader>
        <Input
          value={busca}
          onChange={(evento) => setBusca(evento.target.value)}
          placeholder={textos.busca}
          aria-label={textos.busca}
        />
        <div className="max-h-64 space-y-1 overflow-y-auto">
          {candidatos.length === 0 ? (
            <p className="px-1 py-4 text-sm text-muted-foreground">{textos.vazio}</p>
          ) : (
            candidatos.map((cartao) => (
              <Button
                key={cartao.atendimentoId}
                type="button"
                variant={destinoId === cartao.atendimentoId ? "secondary" : "outline"}
                className="w-full justify-start"
                aria-pressed={destinoId === cartao.atendimentoId}
                disabled={encaminhar.isPending}
                onClick={() => setDestinoId(cartao.atendimentoId)}
              >
                <span className="truncate">{cartao.leadNome}</span>
              </Button>
            ))
          )}
        </div>
        {erro && (
          <p className="text-sm text-destructive" role="alert">
            {erro}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={fechar} disabled={encaminhar.isPending}>
            {textos.cancelar}
          </Button>
          <Button
            type="button"
            disabled={!destinoId || encaminhar.isPending}
            onClick={() => destinoId && encaminhar.mutate(destinoId)}
          >
            {textos.confirmar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
