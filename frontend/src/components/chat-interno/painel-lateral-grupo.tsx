"use client";

import { useMemo, useState } from "react";
import { PanelRightClose, UserMinus, UserPlus, UsersRound } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  adicionarParticipanteChat,
  listarContatosChat,
  listarParticipantesChat,
  removerParticipanteChat,
  renomearGrupoChat,
} from "@/lib/chat-interno/api";
import type { Textos } from "@/lib/config/schema";
import { ContadorDoPainel } from "@/components/ui/contador-do-painel";
import { ListaDeMidiasDoGrupo } from "@/components/chat-interno/secao-de-midias-grupo";

type TextosChat = Textos["chatInterno"];

type Props = {
  conversaId: string;
  nomeAtual: string;
  usuarioAtual: string | null;
  textos: TextosChat;
  onRetrair: () => void;
  onSaiu?: () => void;
};

/** Sem hierarquia: qualquer participante vê as mesmas ações (add/remove/rename/sair). */
export function PainelLateralGrupo({
  conversaId,
  nomeAtual,
  usuarioAtual,
  textos,
  onRetrair,
  onSaiu,
}: Props) {
  const cache = useQueryClient();
  const [nome, setNome] = useState(nomeAtual);
  const [adicionandoId, setAdicionandoId] = useState<string | null>(null);
  const participantes = useQuery({
    queryKey: ["chat-interno", "participantes", conversaId],
    queryFn: () => listarParticipantesChat(conversaId),
  });
  const contatos = useQuery({
    queryKey: ["chat-interno", "contatos"],
    queryFn: listarContatosChat,
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
    onSuccess: invalidar,
  });
  const remover = useMutation({
    mutationFn: (usuarioId: string) => removerParticipanteChat(conversaId, usuarioId),
    onSuccess: (_data, usuarioId) => {
      invalidar();
      if (usuarioId === usuarioAtual) {
        onSaiu?.();
        onRetrair();
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
    <aside
      id="painel-grupo"
      aria-labelledby="painel-grupo-titulo"
      className="flex h-full min-h-0 w-[344px] shrink-0 flex-col overflow-hidden border-l border-border bg-background"
    >
      <div className="flex flex-none items-center justify-between gap-2 p-4">
        <h2 id="painel-grupo-titulo" className="text-sm font-bold text-foreground">
          {textos.participantesDoGrupo}
        </h2>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={onRetrair}
          aria-expanded="true"
          aria-controls="painel-grupo"
          aria-label={textos.retrair}
          title={textos.retrair}
        >
          <PanelRightClose className="size-(--tamanho-icone-interface)" aria-hidden />
        </Button>
      </div>

      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto p-4 pt-0">
        <div className="flex flex-col items-center gap-3 text-center">
          <span className="flex size-16 shrink-0 items-center justify-center rounded-xl bg-primary/15 text-primary" aria-hidden>
            <UsersRound className="size-[calc(var(--tamanho-icone-interface)*1.75)]" />
          </span>
          <div className="flex w-full items-center gap-2">
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
        </div>

        {(renomear.isError || remover.isError || adicionar.isError) && (
          <p role="alert" className="text-sm text-cor-erro">{textos.erroParticipantes}</p>
        )}

        <ContadorDoPainel
          valor={participantes.data?.length ?? 0}
          rotulo={textos.participantesDoGrupo}
        />

        <div>
          <p className="mb-3 px-0.5 text-xs font-bold tracking-wide text-muted-foreground uppercase">
            {textos.selecionarParticipantes}
          </p>
          <ul className="space-y-1" aria-label={textos.participantesDoGrupo}>
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
        </div>

        {candidatos.length > 0 && (
          <div>
            <p className="mb-3 px-0.5 text-xs font-bold tracking-wide text-muted-foreground uppercase">
              {textos.adicionarParticipante}
            </p>
            <ul className="space-y-1">
              {candidatos.map((c) => (
                <li key={c.id}>
                  <Button
                    type="button"
                    variant="outline"
                    className="w-full justify-start gap-2"
                    disabled={adicionar.isPending}
                    aria-busy={adicionandoId === c.id && adicionar.isPending}
                    onClick={() => {
                      setAdicionandoId(c.id);
                      adicionar.mutate(c.id);
                    }}
                  >
                    <UserPlus className="size-(--tamanho-icone-interface)" aria-hidden />
                    {c.nome}
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}

        <ListaDeMidiasDoGrupo conversaId={conversaId} textos={textos} />
      </div>
    </aside>
  );
}
