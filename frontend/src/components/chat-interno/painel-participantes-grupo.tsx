"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { UserMinus, UserPlus, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  adicionarParticipanteChat,
  listarContatosChat,
  listarParticipantesChat,
  removerParticipanteChat,
  renomearGrupoChat,
} from "@/lib/chat-interno/api";
import type { Textos } from "@/lib/config/schema";

type TextosChat = Textos["chatInterno"];

type Props = {
  aberto: boolean;
  onFechar: () => void;
  conversaId: string;
  nomeAtual: string;
  usuarioAtual: string | null;
  textos: TextosChat;
  onSaiu?: () => void;
};

/** Sem hierarquia: qualquer participante vê as mesmas ações (add/remove/rename/sair). */
export function PainelParticipantesGrupo({
  aberto,
  onFechar,
  conversaId,
  nomeAtual,
  usuarioAtual,
  textos,
  onSaiu,
}: Props) {
  const cache = useQueryClient();
  const [nome, setNome] = useState(nomeAtual);
  const [adicionandoId, setAdicionandoId] = useState<string | null>(null);
  const participantes = useQuery({
    queryKey: ["chat-interno", "participantes", conversaId],
    queryFn: () => listarParticipantesChat(conversaId),
    enabled: aberto,
  });
  const contatos = useQuery({
    queryKey: ["chat-interno", "contatos"],
    queryFn: listarContatosChat,
    enabled: aberto,
  });

  const idsNoGrupo = useMemo(
    () => new Set((participantes.data ?? []).map((p) => p.id)),
    [participantes.data],
  );
  const candidatos = useMemo(
    () => (contatos.data ?? []).filter((c) => !idsNoGrupo.has(c.id)),
    [contatos.data, idsNoGrupo],
  );

  const invalidar = () => {
    void cache.invalidateQueries({ queryKey: ["chat-interno"] });
  };

  const renomear = useMutation({
    mutationFn: () => renomearGrupoChat(conversaId, nome.trim()),
    onSuccess: () => invalidar(),
  });
  const remover = useMutation({
    mutationFn: (usuarioId: string) => removerParticipanteChat(conversaId, usuarioId),
    onSuccess: (_d, usuarioId) => {
      invalidar();
      if (usuarioId === usuarioAtual) {
        onSaiu?.();
        onFechar();
      }
    },
  });
  const adicionar = useMutation({
    mutationFn: (usuarioId: string) => adicionarParticipanteChat(conversaId, usuarioId),
    onSuccess: () => {
      setAdicionandoId(null);
      invalidar();
    },
  });

  return (
    <Dialog open={aberto} onOpenChange={(a) => !a && onFechar()}>
      <DialogContent className="max-w-md gap-0 overflow-hidden p-0" showCloseButton={false}>
        <DialogHeader className="border-b border-border px-5 py-4 pr-12">
          <DialogTitle>{textos.participantesDoGrupo}</DialogTitle>
          <DialogDescription>{textos.selecionarParticipantesDescricao}</DialogDescription>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="absolute right-3 top-3"
            aria-label={textos.fecharSeletor}
            onClick={onFechar}
          >
            <X className="size-(--tamanho-icone-interface)" aria-hidden />
          </Button>
        </DialogHeader>

        <div className="space-y-4 p-4">
          <div className="flex gap-2">
            <Input
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              aria-label={textos.renomearGrupo}
              maxLength={120}
            />
            <Button
              type="button"
              variant="outline"
              disabled={!nome.trim() || nome.trim() === nomeAtual || renomear.isPending}
              onClick={() => renomear.mutate()}
            >
              {textos.salvarNome}
            </Button>
          </div>

          {(renomear.isError || remover.isError || adicionar.isError) && (
            <p role="alert" className="text-sm text-cor-erro">{textos.erroParticipantes}</p>
          )}

          <ul className="max-h-48 space-y-1 overflow-y-auto" aria-label={textos.participantesDoGrupo}>
            {(participantes.data ?? []).map((p) => {
              const souEu = p.id === usuarioAtual;
              return (
                <li key={p.id} className="flex items-center justify-between gap-2 rounded-xl px-2 py-2">
                  <span className="truncate font-medium">
                    {souEu ? `${p.nome} (${textos.voce})` : p.nome}
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="text-cor-erro"
                    aria-label={souEu ? textos.sairDoGrupo : textos.removerParticipante}
                    disabled={remover.isPending}
                    onClick={() => remover.mutate(p.id)}
                  >
                    <UserMinus className="size-(--tamanho-icone-interface)" aria-hidden />
                    {souEu ? textos.sairDoGrupo : textos.removerParticipante}
                  </Button>
                </li>
              );
            })}
          </ul>

          {candidatos.length > 0 && (
            <div className="space-y-2">
              <p className="text-sm font-medium">{textos.adicionarParticipante}</p>
              <ul className="max-h-40 space-y-1 overflow-y-auto">
                {candidatos.map((c) => (
                  <li key={c.id}>
                    <Button
                      type="button"
                      variant="outline"
                      className="w-full justify-start gap-2"
                      disabled={adicionar.isPending}
                      onClick={() => {
                        setAdicionandoId(c.id);
                        adicionar.mutate(c.id);
                      }}
                    >
                      <UserPlus className="size-(--tamanho-icone-interface)" aria-hidden />
                      {c.nome}
                      {adicionandoId === c.id && adicionar.isPending ? "…" : ""}
                    </Button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
