"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { usePathname, useRouter } from "next/navigation";
import { Bell, MessageCircle, UserRound, X } from "lucide-react";

import { useConexaoTempoReal } from "@/lib/atendimento/tempo-real";
import {
  chaveTecnicaDaNotificacao,
  obterConversaAtiva,
  ServicoDeNotificacoesTempoReal,
} from "@/lib/atendimento/servico-notificacoes-tempo-real";
import { usePreferenciaSomDeNotificacao } from "@/lib/atendimento/preferencias-notificacoes";
import { tocarSomDeNotificacao, registrarDesbloqueioDeAudio } from "@/lib/atendimento/som-de-notificacao";
import type { NotificacaoTempoReal } from "@/lib/atendimento/types";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

const DURACAO_DO_AVISO_MS = 8000;
const MAXIMO_DE_AVISOS_VISUAIS = 3;

export function NotificacoesTempoReal() {
  const catalogo = useTextos();
  const textos = catalogo.notificacoes;
  const textosAtendimentos = catalogo.atendimentos;
  const cache = useQueryClient();
  const pathname = usePathname();
  const router = useRouter();
  const usuarioId = useAuthStoreId();
  const accessToken = useAuthStore((estado) => estado.accessToken);
  const { somHabilitado } = usePreferenciaSomDeNotificacao();
  const [avisos, setAvisos] = useState<NotificacaoTempoReal[]>([]);
  const servico = useRef(new ServicoDeNotificacoesTempoReal());

  const aoReceber = useCallback((notificacao: NotificacaoTempoReal) => {
    const decisao = servico.current.decidir(notificacao, {
      usuarioId,
      conversaAtiva: obterConversaAtiva(),
      somHabilitado,
    });
    if (!decisao) return;
    if (decisao.atualizarAtendimentos) {
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    }
    if (decisao.atualizarChatInterno) {
      void cache.invalidateQueries({ queryKey: ["chat-interno"] });
    }
    if (decisao.tocar) tocarSomDeNotificacao();
    const avisoJaApresentadoPelaTelaDeAtendimentos = pathname.startsWith("/atendimentos")
      && (notificacao.tipo === "TRANSFERENCIA_RECEBIDA" || notificacao.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA");
    if (decisao.exibir && !avisoJaApresentadoPelaTelaDeAtendimentos) {
      setAvisos((atuais) => [
        notificacao,
        ...atuais.filter((atual) => chaveTecnicaDaNotificacao(atual) !== decisao.chave),
      ].slice(0, MAXIMO_DE_AVISOS_VISUAIS));
    }
  }, [cache, pathname, somHabilitado, usuarioId]);

  useConexaoTempoReal(() => accessToken, undefined, aoReceber);

  useEffect(() => registrarDesbloqueioDeAudio(), []);
  useEffect(() => {
    if (avisos.length === 0) return;
    const timer = window.setTimeout(() => setAvisos([]), DURACAO_DO_AVISO_MS);
    return () => window.clearTimeout(timer);
  }, [avisos]);

  function abrirAviso(notificacao: NotificacaoTempoReal) {
    if (notificacao.tipo === "NOVA_MENSAGEM") {
      const { leadId, atendimentoId } = notificacao.dados;
      router.push(`/atendimentos?leadId=${encodeURIComponent(leadId)}&atendimentoId=${encodeURIComponent(atendimentoId)}&visao=ATIVOS`);
    } else if (notificacao.tipo === "CHAT_INTERNO_MENSAGEM") {
      router.push(`/chat-interno?conversaId=${encodeURIComponent(notificacao.dados.conversaId)}`);
    } else if (notificacao.tipo === "TRANSFERENCIA_RECEBIDA") {
      router.push(`/atendimentos?leadId=${encodeURIComponent(notificacao.dados.leadId)}&atendimentoId=${encodeURIComponent(notificacao.dados.atendimentoId)}&visao=ATIVOS`);
    } else if (notificacao.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA") {
      router.push(`/atendimentos?leadId=${encodeURIComponent(notificacao.dados.leadId)}&atendimentoId=${encodeURIComponent(notificacao.dados.atendimentoId)}&visao=ATIVOS`);
    }
    setAvisos((atuais) => atuais.filter((atual) => chaveTecnicaDaNotificacao(atual) !== chaveTecnicaDaNotificacao(notificacao)));
  }

  return avisos.length > 0 ? (
    <div className="pointer-events-none fixed right-4 top-4 z-50 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
      {avisos.map((aviso) => (
        <div
          key={chaveTecnicaDaNotificacao(aviso)}
          className="pointer-events-auto relative overflow-hidden rounded-xl border border-border bg-background p-4 shadow-lg"
          role="status"
          aria-live="polite"
        >
          <button
            type="button"
            className="absolute right-2 top-2 rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={textos.fechar}
            title={textos.fechar}
            onClick={() => setAvisos((atuais) => atuais.filter((atual) => chaveTecnicaDaNotificacao(atual) !== chaveTecnicaDaNotificacao(aviso)))}
          >
            <X className="size-(--tamanho-icone-interface)" aria-hidden />
          </button>
          <div className="flex items-start gap-3 pr-5">
            <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary" aria-hidden>
              {aviso.tipo === "TRANSFERENCIA_RECEBIDA" ? <UserRound className="size-(--tamanho-icone-interface)" /> : aviso.tipo === "CHAT_INTERNO_MENSAGEM" ? <MessageCircle className="size-(--tamanho-icone-interface)" /> : <Bell className="size-(--tamanho-icone-interface)" />}
            </span>
            <div className="min-w-0">
              <p className="font-semibold text-foreground">{tituloDoAviso(aviso, textos, textosAtendimentos.tempoReal)}</p>
              <p className="mt-1 text-sm text-muted-foreground">{descricaoDoAviso(aviso, textos, textosAtendimentos.tempoReal)}</p>
              {(aviso.tipo === "NOVA_MENSAGEM" || aviso.tipo === "CHAT_INTERNO_MENSAGEM") && (
                <p className="mt-2 line-clamp-2 text-sm text-foreground">{previaDoAviso(aviso, textos, textosAtendimentos.media)}</p>
              )}
              {(aviso.tipo === "NOVA_MENSAGEM" || aviso.tipo === "CHAT_INTERNO_MENSAGEM" || aviso.tipo === "TRANSFERENCIA_RECEBIDA" || aviso.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA") && (
                <button
                  type="button"
                  className="mt-3 text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  onClick={() => abrirAviso(aviso)}
                >
                  {textos.abrir}
                </button>
              )}
            </div>
          </div>
        </div>
      ))}
    </div>
  ) : null;
}

function useAuthStoreId(): string | null {
  // Importar o store neste pequeno adaptador mantém o callback do socket sem estado duplicado.
  return useAuthStore((estado) => estado.usuarioId);
}

function tituloDoAviso(
  aviso: NotificacaoTempoReal,
  textos: ReturnType<typeof useTextos>["notificacoes"],
  tempoReal: ReturnType<typeof useTextos>["atendimentos"]["tempoReal"],
): string {
  if (aviso.tipo === "TRANSFERENCIA_RECEBIDA") return tempoReal.transferenciaRecebida;
  if (aviso.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA") return tempoReal.atendimentoDevolvidoParaIa;
  if (aviso.tipo === "NOVA_MENSAGEM") return textos.mensagemExterna.replace("{nome}", aviso.dados.leadNome);
  if (aviso.tipo === "CHAT_INTERNO_MENSAGEM") {
    return textos.mensagemInterna.replace("{nome}", aviso.dados.remetenteNome || textos.equipe);
  }
  return textos.mensagemInterna;
}

function descricaoDoAviso(
  aviso: NotificacaoTempoReal,
  textos: ReturnType<typeof useTextos>["notificacoes"],
  tempoReal: ReturnType<typeof useTextos>["atendimentos"]["tempoReal"],
): string {
  if (aviso.tipo === "TRANSFERENCIA_RECEBIDA") return tempoReal.transferenciaRecebidaDescricao.replace("{nome}", aviso.dados.leadNome);
  if (aviso.tipo === "ATENDIMENTO_DEVOLVIDO_PARA_IA") return tempoReal.atendimentoDevolvidoParaIaDescricao.replace("{nome}", aviso.dados.leadNome);
  return aviso.tipo === "NOVA_MENSAGEM" ? textos.origemExterna : textos.origemInterna;
}

function previaDoAviso(
  aviso: Extract<NotificacaoTempoReal, { tipo: "NOVA_MENSAGEM" | "CHAT_INTERNO_MENSAGEM" }>,
  textos: ReturnType<typeof useTextos>["notificacoes"],
  media: ReturnType<typeof useTextos>["atendimentos"]["media"],
): string {
  const ehTexto = aviso.dados.tipo == null || aviso.dados.tipo === "TEXTO";
  if (!ehTexto) return rotuloDaMidia(aviso.dados.tipo, aviso.dados.midiaMetadados, textos.midia, media);
  const conteudo = aviso.dados.conteudo?.replace(/[\n\r\t]+/g, " ").trim();
  if (!conteudo) return textos.midia;
  return conteudo.length > 160 ? `${conteudo.slice(0, 157)}${textos.previewContinua}` : conteudo;
}

function rotuloDaMidia(
  tipo: string | null | undefined,
  metadados: string | null | undefined,
  fallback: string,
  media: ReturnType<typeof useTextos>["atendimentos"]["media"],
): string {
  const normalizado = tipo?.toUpperCase();
  if (normalizado === "IMAGEM") return media.imagem;
  if (normalizado === "AUDIO") return media.audio;
  if (normalizado === "VIDEO") return media.visualizador.video;
  if (normalizado === "DOCUMENTO") return media.documento;
  if (normalizado === "LOCALIZACAO") return media.localizacao;
  try {
    const mime = typeof metadados === "string"
      ? String((JSON.parse(metadados) as { mimetype?: unknown }).mimetype ?? "").toLowerCase()
      : "";
    if (mime.startsWith("image/")) return media.imagem;
    if (mime.startsWith("audio/")) return media.audio;
    if (mime.startsWith("video/")) return media.visualizador.video;
  } catch {
    // Metadados de um provedor não são confiáveis para a notificação; usa-se o rótulo genérico.
  }
  return fallback;
}
