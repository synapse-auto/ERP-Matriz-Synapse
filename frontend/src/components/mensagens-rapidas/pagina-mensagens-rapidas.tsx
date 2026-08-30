"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Pencil, Trash2, Zap } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Textarea } from "@/components/ui/textarea";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";
import {
  criarMensagemRapida,
  editarMensagemRapida,
  listarMensagensRapidas,
  removerMensagemRapida,
} from "@/lib/suporte/api";
import type { MensagemRapida } from "@/lib/suporte/types";

export function PaginaMensagensRapidas() {
  const t = useTextos().mensagensRapidas;
  const gestor = useAuthStore((s) => s.papel) !== "ATENDENTE";
  const cache = useQueryClient();
  const [aberto, setAberto] = useState(false);
  const [edicao, setEdicao] = useState<MensagemRapida | null>(null);

  const consulta = useQuery({
    queryKey: ["mensagens-rapidas"],
    queryFn: () => listarMensagensRapidas(),
  });
  const remover = useMutation({
    mutationFn: removerMensagemRapida,
    onSuccess: () => cache.invalidateQueries({ queryKey: ["mensagens-rapidas"] }),
  });

  const grupos = agruparPorAtendente(consulta.data ?? []);

  return (
    <div className="space-y-5 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">{t.titulo}</h1>
          <p className="text-sm text-muted-foreground">{t.descricao}</p>
        </div>
        <Button onClick={() => setAberto(true)}>{t.nova}</Button>
      </header>

      <div className="flex items-center gap-3 rounded-lg border border-primary/20 bg-primary/5 p-3">
        <Zap className="size-[calc(var(--tamanho-icone-interface)*1.25)] shrink-0 text-primary" />
        <p className="text-sm text-primary">{t.dica}</p>
      </div>

      {consulta.isLoading ? (
        <p>{t.carregando}</p>
      ) : consulta.isError ? (
        <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => consulta.refetch()} />
      ) : !consulta.data?.length ? (
        <p className="text-muted-foreground">{t.vazio}</p>
      ) : (
        <div className="space-y-6">
          {grupos.map((grupo) => (
            <div key={grupo.atendenteId}>
              {gestor && (
                <div className="mb-3 flex items-center gap-2.5">
                  <AvatarIniciais
                    id={grupo.atendenteId}
                    nome={grupo.atendenteNome}
                    className="flex size-8 shrink-0 items-center justify-center rounded-lg text-xs font-bold text-white"
                  />
                  <span className="text-sm font-bold">{grupo.atendenteNome}</span>
                  <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-bold text-muted-foreground">
                    {grupo.itens.length}
                  </span>
                </div>
              )}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {grupo.itens.map((mensagem) => (
                  <CardDeMensagemRapida
                    key={mensagem.id}
                    mensagem={mensagem}
                    textos={t}
                    onEditar={() => setEdicao(mensagem)}
                    onRemover={() => remover.mutate(mensagem.id)}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <Formulario aberto={aberto} onFechar={() => setAberto(false)} />
      {edicao && <Formulario aberto existente={edicao} onFechar={() => setEdicao(null)} />}
    </div>
  );
}

type TextosMensagensRapidas = ReturnType<typeof useTextos>["mensagensRapidas"];

function agruparPorAtendente(
  itens: MensagemRapida[],
): { atendenteId: string; atendenteNome: string; itens: MensagemRapida[] }[] {
  const porId = new Map<string, { atendenteId: string; atendenteNome: string; itens: MensagemRapida[] }>();
  for (const item of itens) {
    const grupo = porId.get(item.atendenteId);
    if (grupo) {
      grupo.itens.push(item);
    } else {
      porId.set(item.atendenteId, {
        atendenteId: item.atendenteId,
        atendenteNome: item.atendenteNome,
        itens: [item],
      });
    }
  }
  return [...porId.values()];
}

function CardDeMensagemRapida({
  mensagem,
  textos,
  onEditar,
  onRemover,
}: {
  mensagem: MensagemRapida;
  textos: TextosMensagensRapidas;
  onEditar: () => void;
  onRemover: () => void;
}) {
  return (
    <div className="flex flex-col gap-2.5 rounded-lg border bg-card p-4">
      <div className="flex items-center gap-2">
        <span className="rounded-md border border-primary/20 bg-primary/10 px-2.5 py-1 font-mono text-xs font-bold text-primary">
          /{mensagem.palavraChave}
        </span>
        <div className="ml-auto flex gap-1">
          <Button
            size="icon"
            variant="ghost"
            className="size-8"
            aria-label={`${textos.editar} /${mensagem.palavraChave}`}
            onClick={onEditar}
          >
            <Pencil className="size-(--tamanho-icone-interface)" />
          </Button>
          <Button
            size="icon"
            variant="ghost"
            className="size-8 text-destructive hover:text-destructive"
            aria-label={`${textos.remover} /${mensagem.palavraChave}`}
            onClick={onRemover}
          >
            <Trash2 className="size-(--tamanho-icone-interface)" />
          </Button>
        </div>
      </div>
      <p className="text-sm text-muted-foreground">{mensagem.conteudo}</p>
    </div>
  );
}

function Formulario({
  aberto,
  existente,
  onFechar,
}: {
  aberto: boolean;
  existente?: MensagemRapida;
  onFechar: () => void;
}) {
  const t = useTextos().mensagensRapidas.formulario;
  const cache = useQueryClient();
  const [chave, setChave] = useState(existente?.palavraChave ?? "");
  const [conteudo, setConteudo] = useState(existente?.conteudo ?? "");
  const salvar = useMutation({
    mutationFn: () =>
      existente
        ? editarMensagemRapida(existente.id, { palavraChave: chave, conteudo })
        : criarMensagemRapida({ palavraChave: chave, conteudo }),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ["mensagens-rapidas"] });
      onFechar();
    },
  });

  return (
    <Dialog open={aberto} onOpenChange={(v) => !v && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{existente ? t.editarTitulo : t.criarTitulo}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            salvar.mutate();
          }}
        >
          <label className="block space-y-1">
            <span>{t.chave}</span>
            <div className="flex items-center">
              <span className="px-2 font-mono">/</span>
              <Input
                required
                pattern="[\p{L}\p{N}_-]+"
                value={chave}
                onChange={(e) => setChave(e.target.value)}
              />
            </div>
          </label>
          <label className="block space-y-1">
            <span>{t.conteudo}</span>
            <Textarea required value={conteudo} onChange={(e) => setConteudo(e.target.value)} />
          </label>
          {salvar.isError && <p className="text-destructive">{t.erro}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onFechar}>
              {t.cancelar}
            </Button>
            <Button type="submit">{t.salvar}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
