"use client";

import { useMemo, useState } from "react";
import { Search, UsersRound, X } from "lucide-react";

import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import type { ChatContato } from "@/lib/chat-interno/types";
import type { Textos } from "@/lib/config/schema";
import { cn } from "@/lib/utils";

type TextosChat = Textos["chatInterno"];

type Props = {
  aberto: boolean;
  onFechar: () => void;
  contatos: ChatContato[];
  onCriar: (nome: string, participantes: string[]) => Promise<unknown>;
  textos: TextosChat;
};

export function DialogoCriarGrupo({ aberto, onFechar, contatos, onCriar, textos }: Props) {
  const [nome, setNome] = useState("");
  const [busca, setBusca] = useState("");
  const [selecionados, setSelecionados] = useState<Set<string>>(new Set());
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState(false);

  const pessoas = useMemo(() => {
    const termo = busca.trim().toLocaleLowerCase("pt-BR");
    return [...contatos]
      .filter((c) => !termo || c.nome.toLocaleLowerCase("pt-BR").includes(termo))
      .toSorted((a, b) => a.nome.localeCompare(b.nome, "pt-BR"));
  }, [busca, contatos]);

  function fechar() {
    setNome("");
    setBusca("");
    setSelecionados(new Set());
    setEnviando(false);
    setErro(false);
    onFechar();
  }

  function alternar(id: string) {
    setSelecionados((atual) => {
      const proximo = new Set(atual);
      if (proximo.has(id)) proximo.delete(id);
      else proximo.add(id);
      return proximo;
    });
  }

  async function confirmar() {
    const nomeLimpo = nome.trim();
    if (!nomeLimpo || selecionados.size < 1 || enviando) return;
    setEnviando(true);
    setErro(false);
    try {
      await onCriar(nomeLimpo, [...selecionados]);
      fechar();
    } catch {
      setEnviando(false);
      setErro(true);
    }
  }

  const podeCriar = nome.trim().length > 0 && selecionados.size >= 1 && !enviando;

  return (
    <Dialog open={aberto} onOpenChange={(abertoAgora) => !abertoAgora && fechar()}>
      <DialogContent className="max-w-md gap-0 overflow-hidden p-0" showCloseButton={false}>
        <DialogHeader className="border-b border-border px-5 py-4 pr-12">
          <DialogTitle className="flex items-center gap-2">
            <UsersRound className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-primary" aria-hidden />
            {textos.novoGrupo}
          </DialogTitle>
          <DialogDescription>{textos.selecionarParticipantesDescricao}</DialogDescription>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            className="absolute right-3 top-3"
            aria-label={textos.fecharSeletor}
            onClick={fechar}
          >
            <X className="size-(--tamanho-icone-interface)" aria-hidden />
          </Button>
        </DialogHeader>

        <div className="space-y-3 p-4">
          <div className="space-y-1.5">
            <label className="text-sm font-medium" htmlFor="nome-grupo">{textos.nomeDoGrupo}</label>
            <Input
              id="nome-grupo"
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              placeholder={textos.nomeDoGrupoPlaceholder}
              maxLength={120}
            />
          </div>

          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-(--tamanho-icone-interface) -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder={textos.buscarPessoa}
              aria-label={textos.buscarPessoa}
              className="h-10 rounded-xl pl-9"
            />
          </div>

          {selecionados.size < 1 && (
            <p className="text-xs text-muted-foreground">{textos.participantesMinimos}</p>
          )}
          {erro && <p role="alert" className="text-sm text-cor-erro">{textos.erroCriarGrupo}</p>}

          <ul className="max-h-56 space-y-1 overflow-y-auto" aria-label={textos.selecionarParticipantes}>
            {pessoas.map((contato) => {
              const marcado = selecionados.has(contato.id);
              return (
                <li key={contato.id}>
                  <button
                    type="button"
                    className={cn(
                      "flex w-full items-center gap-3 rounded-xl p-2.5 text-left outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring",
                      marcado && "bg-primary/10",
                    )}
                    onClick={() => alternar(contato.id)}
                    aria-pressed={marcado}
                  >
                    <AvatarIniciais
                      id={contato.id}
                      nome={contato.nome}
                      fotoUrl={contato.fotoUrl}
                      className="flex size-10 items-center justify-center rounded-xl text-xs font-bold text-white"
                    />
                    <span className="min-w-0 flex-1 truncate font-medium">{contato.nome}</span>
                    <span
                      className={cn(
                        "flex size-5 items-center justify-center rounded border text-xs",
                        marcado ? "border-primary bg-primary text-primary-foreground" : "border-border",
                      )}
                      aria-hidden
                    >
                      {marcado ? "✓" : ""}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>

          <Button type="button" className="w-full" disabled={!podeCriar} onClick={() => void confirmar()}>
            {textos.criarGrupo}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
