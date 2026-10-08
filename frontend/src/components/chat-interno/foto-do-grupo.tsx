"use client";

import { useEffect, useRef, useState } from "react";
import { Camera, Trash2 } from "lucide-react";
import { useMutation, useQueryClient, type InfiniteData } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import { ErroDeApi } from "@/lib/api/errors";
import { atualizarFotoDoGrupoChat, removerFotoDoGrupoChat } from "@/lib/chat-interno/api";
import type { ChatConversa, FotoDoGrupo } from "@/lib/chat-interno/types";
import type { PaginaInbox } from "@/lib/atendimento/api";
import type { Textos } from "@/lib/config/schema";
import { AvatarDoGrupo } from "@/components/chat-interno/avatar-do-grupo";

type TextosFoto = Textos["chatInterno"]["fotoGrupo"];

const TIPOS_ACEITOS = ["image/jpeg", "image/png", "image/webp"];

type Props = {
  conversaId: string;
  nome: string;
  fotoUrl?: string | null;
  /** Vem do backend (`podeAlterarFoto`): a tela nunca decide sozinha quem pode trocar a foto. */
  podeAlterar: boolean;
  textos: TextosFoto;
};

/** Traduz a falha do backend para o catálogo de textos; nenhuma frase fixa no componente. */
function mensagemDeErro(erro: unknown, textos: TextosFoto): string {
  if (erro instanceof ErroDeApi) {
    if (erro.status === 403) return textos.erroPermissao;
    if (erro.status === 413) return textos.erroTamanho;
    if (erro.status === 422) return textos.erroImagem;
    if (erro.status === 503) return textos.erroIndisponivel;
  }
  return textos.erroGenerico;
}

/**
 * Foto do grupo no painel de detalhes: mostra a foto atual e, para quem pode, oferece trocar (com
 * prévia, confirmação e cancelamento) e remover. A imagem só sai do navegador depois de confirmada.
 */
export function SecaoFotoDoGrupo({ conversaId, nome, fotoUrl, podeAlterar, textos }: Props) {
  const cache = useQueryClient();
  const entrada = useRef<HTMLInputElement>(null);
  // O isPending da mutation so vira true depois de um ciclo de renderizacao: dois cliques no mesmo
  // instante passariam por ele. Esta trava e sincrona e cobre as duas operacoes (nunca ha duas ao mesmo tempo).
  const operacaoEmCurso = useRef(false);
  const [escolhido, setEscolhido] = useState<{ arquivo: File; previaUrl: string } | null>(null);
  const [erroLocal, setErroLocal] = useState<string | null>(null);

  useEffect(() => {
    if (!escolhido) return;
    return () => URL.revokeObjectURL(escolhido.previaUrl);
  }, [escolhido]);

  const aplicar = async (resposta: FotoDoGrupo) => {
    // Um GET iniciado antes do upload não pode restaurar a foto antiga depois da confirmação.
    await Promise.all([
      cache.cancelQueries({ queryKey: ["chat-interno", "conversas"] }),
      cache.cancelQueries({ queryKey: ["atendimentos", "inbox"] }),
    ]);
    cache.setQueryData<ChatConversa[]>(["chat-interno", "conversas"], (lista) =>
      lista?.map((conversa) => (conversa.id === conversaId ? { ...conversa, fotoUrl: resposta.fotoUrl } : conversa)),
    );
    cache.setQueriesData<InfiniteData<PaginaInbox>>({ queryKey: ["atendimentos", "inbox"] }, (inbox) =>
      inbox && {
        ...inbox,
        pages: inbox.pages.map((pagina) => ({
          ...pagina,
          itens: pagina.itens.map((item) => item?.tipo === "EQUIPE_INTERNA"
            && item.tipoConversa === "GRUPO" && item.conversaId === conversaId
            ? { ...item, avatarUrl: resposta.fotoUrl } : item),
        })),
      },
    );
    void cache.invalidateQueries({ queryKey: ["chat-interno"] });
    void cache.invalidateQueries({ queryKey: ["atendimentos", "inbox"] });
  };
  const enviar = useMutation({
    mutationFn: (arquivo: File) => atualizarFotoDoGrupoChat(conversaId, arquivo),
    onSuccess: async (resposta) => {
      await aplicar(resposta);
      setEscolhido(null);
    },
    onSettled: () => {
      operacaoEmCurso.current = false;
    },
  });
  const remover = useMutation({
    mutationFn: () => removerFotoDoGrupoChat(conversaId),
    onSuccess: aplicar,
    onSettled: () => {
      operacaoEmCurso.current = false;
    },
  });

  const ocupado = enviar.isPending || remover.isPending;
  const erro = erroLocal ?? (enviar.isError ? mensagemDeErro(enviar.error, textos) : null)
    ?? (remover.isError ? mensagemDeErro(remover.error, textos) : null);

  const aoEscolher = (arquivo: File | undefined) => {
    enviar.reset();
    remover.reset();
    if (!arquivo) return;
    if (!TIPOS_ACEITOS.includes(arquivo.type)) {
      setErroLocal(textos.erroTipo);
      return;
    }
    setErroLocal(null);
    setEscolhido({ arquivo, previaUrl: URL.createObjectURL(arquivo) });
  };
  const cancelar = () => {
    if (enviar.isPending) return;
    enviar.reset();
    setErroLocal(null);
    setEscolhido(null);
  };
  const confirmar = () => {
    if (!escolhido || operacaoEmCurso.current) return;
    operacaoEmCurso.current = true;
    enviar.mutate(escolhido.arquivo);
  };
  const removerFoto = () => {
    if (operacaoEmCurso.current) return;
    operacaoEmCurso.current = true;
    remover.mutate();
  };

  return (
    <div role="group" aria-label={textos.titulo} className="flex w-full min-w-0 flex-col items-center gap-3 text-center">
      {escolhido ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={escolhido.previaUrl} alt={textos.previa} className="size-24 rounded-xl object-cover" />
      ) : (
        <AvatarDoGrupo
          id={conversaId}
          nome={nome}
          fotoUrl={fotoUrl}
          tamanho="painel"
          fotoAlt={textos.fotoAlt.replace("{nome}", nome)}
        />
      )}

      {podeAlterar && (
        <>
          <input
            ref={entrada}
            type="file"
            className="sr-only"
            tabIndex={-1}
            aria-label={textos.escolher}
            accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
            disabled={ocupado}
            onChange={(evento) => {
              aoEscolher(evento.target.files?.[0]);
              evento.currentTarget.value = "";
            }}
          />
          {escolhido ? (
            <div className="flex flex-wrap justify-center gap-2">
              <Button type="button" size="sm" onClick={confirmar} disabled={enviar.isPending} aria-busy={enviar.isPending}>
                {enviar.isPending ? textos.enviando : textos.confirmar}
              </Button>
              <Button type="button" size="sm" variant="ghost" onClick={cancelar} disabled={enviar.isPending}>
                {textos.cancelar}
              </Button>
            </div>
          ) : (
            <div className="flex flex-wrap justify-center gap-2">
              <Button type="button" size="sm" variant="outline" onClick={() => entrada.current?.click()} disabled={ocupado}>
                <Camera className="size-(--tamanho-icone-interface)" aria-hidden />
                {textos.alterar}
              </Button>
              {fotoUrl && (
                <Button type="button" size="sm" variant="outline" onClick={removerFoto} disabled={ocupado} aria-busy={remover.isPending}>
                  <Trash2 className="size-(--tamanho-icone-interface)" aria-hidden />
                  {textos.remover}
                </Button>
              )}
            </div>
          )}
        </>
      )}

      {erro && <p role="alert" className="text-sm text-cor-erro">{erro}</p>}
    </div>
  );
}
