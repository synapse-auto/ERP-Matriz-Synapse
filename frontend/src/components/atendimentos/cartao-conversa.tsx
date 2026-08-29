"use client";

import { MessageCircleMore, Users } from "lucide-react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { AvatarIniciais, tomDoAvatar } from "@/components/ui/avatar-iniciais";
import type { ItemInbox } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { cn, iniciaisDoNome, urlSegura } from "@/lib/utils";

type Props = {
  cartao: ItemInbox;
  selecionado: boolean;
  onAbrirAtendimento: () => void;
};

/** Etapa, foto, nome, atendente responsável, prévia da última mensagem — o card do prompt E11. */
export function CartaoConversa({
  cartao,
  selecionado,
  onAbrirAtendimento,
}: Props) {
  const catalogo = useTextos();
  const textos = catalogo.atendimentos;
  if (cartao.tipo === "EQUIPE_INTERNA") {
    const hora = formatarHoraDaLista(
      cartao.ultimaMensagemEm,
      catalogo.atendimentos.mensagem?.hoje ?? "",
      catalogo.atendimentos.mensagem?.ontem ?? "",
    );
    return (
      <button
        type="button"
        onClick={onAbrirAtendimento}
        aria-current={selecionado ? "true" : undefined}
        className={cn(
          "m-1.5 flex w-[calc(100%-0.75rem)] items-start gap-3 rounded-xl border border-transparent px-3 py-3 text-left transition-colors hover:bg-muted",
          selecionado && "border-primary/20 bg-primary/10 shadow-[inset_3px_0_0_var(--primary)]",
        )}
      >
        <AvatarIniciais
          id={cartao.conversaId}
          nome={cartao.nome}
          fotoUrl={cartao.avatarUrl}
          className="flex size-11 shrink-0 items-center justify-center rounded-xl text-sm font-bold text-white"
        />
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-2">
            <p className="truncate text-sm font-bold text-foreground">{cartao.nome}</p>
            <span className="flex shrink-0 items-center gap-1.5">
              {hora && <time className="text-[0.7rem] text-muted-foreground">{hora}</time>}
              {cartao.naoLidas > 0 && <Badge className="h-5 min-w-5 justify-center rounded-full px-1 text-[0.625rem]">{cartao.naoLidas}</Badge>}
            </span>
          </div>
          {cartao.ultimaMensagemPreview && <p className="mt-1 truncate text-xs text-foreground/70">{cartao.ultimaMensagemPreview}</p>}
          <span className="mt-2 inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-[0.65rem] font-semibold text-muted-foreground">
            <Users className="size-3" aria-hidden />{catalogo.chatInterno.titulo}
          </span>
        </div>
      </button>
    );
  }
  const hora = formatarHoraDaLista(
    cartao.ultimaMensagemEm,
    catalogo.atendimentos.mensagem?.hoje ?? "",
    catalogo.atendimentos.mensagem?.ontem ?? "",
  );
  const canal =
    cartao.canalTipo === "WHATSAPP" ? textos.canais.whatsapp : cartao.canalTipo;

  return (
    <button
      type="button"
      onClick={onAbrirAtendimento}
      aria-current={selecionado ? "true" : undefined}
      className={cn(
        "m-1.5 flex w-[calc(100%-0.75rem)] items-start gap-3 rounded-xl border border-transparent px-3 py-3 text-left transition-colors hover:bg-muted",
        selecionado &&
          "border-primary/20 bg-primary/10 shadow-[inset_3px_0_0_var(--primary)]",
      )}
    >
      <div className="relative shrink-0">
        <Avatar className="size-11 rounded-full">
          {urlSegura(cartao.leadFotoUrl) && (
            <AvatarImage
              src={urlSegura(cartao.leadFotoUrl)}
              alt={cartao.leadNome}
            />
          )}
          <AvatarFallback
            className="rounded-full text-white"
            style={{ backgroundColor: tomDoAvatar(cartao.leadId) }}
          >
            {iniciaisDoNome(cartao.leadNome)}
          </AvatarFallback>
        </Avatar>
        {canal && (
          <span
            className="absolute -right-0.5 -bottom-0.5 inline-flex size-4 items-center justify-center rounded-full bg-cor-sucesso text-white"
            title={canal}
          >
            <MessageCircleMore className="size-2.5" aria-hidden />
          </span>
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <p className="truncate text-sm font-bold text-foreground">
            {cartao.leadNome}
          </p>
          <span className="flex shrink-0 items-center gap-1.5">
            {hora && (
              <time className="text-[0.7rem] text-muted-foreground">{hora}</time>
            )}
            {cartao.naoLidas > 0 && (
              <Badge
                className="h-5 min-w-5 justify-center rounded-full px-1 text-[0.625rem]"
                title={textos.cartao.naoLidas.replace(
                  "{quantidade}",
                  String(cartao.naoLidas),
                )}
              >
                {cartao.naoLidas}
              </Badge>
            )}
          </span>
        </div>

        {cartao.leadEmpresa && (
          <p className="mt-0.5 truncate text-xs text-muted-foreground">
            {cartao.leadEmpresa}
          </p>
        )}

        {cartao.ultimaMensagemPreview && (
          <p className="mt-1 truncate text-xs text-foreground/70">
            {cartao.ultimaMensagemPreview}
          </p>
        )}

        <div className="mt-2 flex min-h-5 items-center gap-1.5">
          {cartao.etapaNome && (
            <span
              className="max-w-[11rem] truncate rounded-full bg-muted px-2 py-0.5 text-[0.65rem] font-semibold text-muted-foreground"
              style={
                cartao.etapaCor
                  ? {
                      backgroundColor: `${cartao.etapaCor}22`,
                      color: cartao.etapaCor,
                    }
                  : undefined
              }
            >
              {cartao.etapaNome}
            </span>
          )}
          {cartao.status === "EM_IA" && (
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[0.65rem] font-semibold text-primary">
              {textos.cartao.atendidoPelaIa}
            </span>
          )}
          {cartao.atendenteNome && (
            <span className="ml-auto truncate text-[0.65rem] text-muted-foreground">
              {cartao.atendenteNome}
            </span>
          )}
        </div>
      </div>
    </button>
  );
}

function formatarHoraDaLista(valor: string | null, hojeRotulo: string, ontemRotulo: string): string | null {
  if (!valor) return null;
  const data = new Date(valor);
  if (Number.isNaN(data.getTime())) return null;
  const hoje = new Date();
  if (data.toDateString() === hoje.toDateString()) {
    return new Intl.DateTimeFormat("pt-BR", {
      hour: "2-digit",
      minute: "2-digit",
    }).format(data);
  }
  const ontem = new Date(hoje);
  ontem.setDate(hoje.getDate() - 1);
  if (data.toDateString() === ontem.toDateString()) return ontemRotulo;
  return new Intl.DateTimeFormat("pt-BR", { weekday: "short" })
    .format(data)
    .replace(".", "");
}
