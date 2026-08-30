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
import type { ChatContato, StatusPresencaChat } from "@/lib/chat-interno/types";
import type { Textos } from "@/lib/config/schema";
import { cn } from "@/lib/utils";

type TextosChat = Textos["chatInterno"];

type Props = {
  aberto: boolean;
  onFechar: () => void;
  contatos: ChatContato[];
  carregando?: boolean;
  erro?: boolean;
  onTentarNovamente?: () => void;
  onSelecionar: (id: string) => Promise<unknown>;
  textos: TextosChat;
};

const ORDEM_PRESENCA: Record<StatusPresencaChat, number> = {
  ONLINE: 0,
  AUSENTE: 1,
  OFFLINE: 2,
};

const CLASSE_PRESENCA: Record<StatusPresencaChat, string> = {
  ONLINE: "bg-cor-sucesso",
  AUSENTE: "bg-cor-atencao",
  OFFLINE: "bg-muted-foreground",
};

export function DialogoSelecionarPessoa({
  aberto,
  onFechar,
  contatos,
  carregando = false,
  erro = false,
  onTentarNovamente,
  onSelecionar,
  textos,
}: Props) {
  const [busca, setBusca] = useState("");
  const [selecionandoId, setSelecionandoId] = useState<string | null>(null);
  const [erroAoAbrir, setErroAoAbrir] = useState(false);

  const pessoas = useMemo(() => {
    const termo = busca.trim().toLocaleLowerCase("pt-BR");
    return [...contatos]
      .filter((contato) => !termo || contato.nome.toLocaleLowerCase("pt-BR").includes(termo))
      .toSorted((a, b) => {
        const ordemA = ORDEM_PRESENCA[a.presenca] ?? ORDEM_PRESENCA.OFFLINE;
        const ordemB = ORDEM_PRESENCA[b.presenca] ?? ORDEM_PRESENCA.OFFLINE;
        return ordemA - ordemB || a.nome.localeCompare(b.nome, "pt-BR");
      });
  }, [busca, contatos]);

  function fechar() {
    setBusca("");
    setErroAoAbrir(false);
    setSelecionandoId(null);
    onFechar();
  }

  async function selecionar(contato: ChatContato) {
    if (selecionandoId) return;
    setSelecionandoId(contato.id);
    setErroAoAbrir(false);
    try {
      await onSelecionar(contato.id);
      fechar();
    } catch {
      setSelecionandoId(null);
      setErroAoAbrir(true);
    }
  }

  const rotuloPresenca = (presenca: StatusPresencaChat) => {
    if (presenca === "ONLINE") return textos.online;
    if (presenca === "AUSENTE") return textos.ausente;
    return textos.offline;
  };

  return (
    <Dialog open={aberto} onOpenChange={(abertoAgora) => !abertoAgora && fechar()}>
      <DialogContent className="max-w-md gap-0 overflow-hidden p-0" showCloseButton={false}>
        <DialogHeader className="border-b border-border px-5 py-4 pr-12">
          <DialogTitle className="flex items-center gap-2">
            <UsersRound className="size-[calc(var(--tamanho-icone-interface)*1.25)] text-primary" aria-hidden />
            {textos.selecionarPessoa}
          </DialogTitle>
          <DialogDescription>{textos.selecionarPessoaDescricao}</DialogDescription>
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
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-(--tamanho-icone-interface) -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              autoFocus
              value={busca}
              onChange={(evento) => setBusca(evento.target.value)}
              placeholder={textos.buscarPessoa}
              aria-label={textos.buscarPessoa}
              className="h-10 rounded-xl pl-9"
            />
          </div>

          {erroAoAbrir && <p role="alert" className="text-sm text-cor-erro">{textos.erroAbrirConversa}</p>}

          {carregando ? (
            <p className="py-8 text-center text-sm text-muted-foreground" aria-live="polite">{textos.carregando}</p>
          ) : erro ? (
            <div className="space-y-3 py-6 text-center">
              <p role="alert" className="text-sm text-cor-erro">{textos.erroContatos}</p>
              <Button type="button" variant="outline" onClick={onTentarNovamente}>{textos.tentarNovamente}</Button>
            </div>
          ) : pessoas.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">{textos.semPessoas}</p>
          ) : (
            <ul className="max-h-72 space-y-1 overflow-y-auto" aria-label={textos.selecionarPessoa}>
              {pessoas.map((contato) => {
                const presenca = contato.presenca ?? "OFFLINE";
                const rotulo = rotuloPresenca(presenca);
                return (
                  <li key={contato.id}>
                    <button
                      type="button"
                      className="flex w-full items-center gap-3 rounded-xl p-2.5 text-left outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-60"
                      onClick={() => void selecionar(contato)}
                      disabled={Boolean(selecionandoId)}
                      aria-label={`${contato.nome}, ${rotulo}`}
                    >
                      <span className="relative shrink-0">
                        <AvatarIniciais id={contato.id} nome={contato.nome} fotoUrl={contato.fotoUrl} className="flex size-10 items-center justify-center rounded-xl text-xs font-bold text-white" />
                        <span className={cn("absolute -bottom-0.5 -right-0.5 size-3 rounded-full border-2 border-popover", CLASSE_PRESENCA[presenca])} aria-hidden />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium text-foreground">{contato.nome}</span>
                        <span className="block text-xs text-muted-foreground">{rotulo}</span>
                      </span>
                      {selecionandoId === contato.id && <span className="text-xs text-muted-foreground" aria-live="polite">{textos.carregando}</span>}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
