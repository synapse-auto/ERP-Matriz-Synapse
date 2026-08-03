"use client";

import { Download, FileText } from "lucide-react";

import { useTextos } from "@/lib/config/textos-provider";
import { cn, urlSegura } from "@/lib/utils";
import type { MensagemResposta } from "@/lib/atendimento/types";

import { StatusEntregaIcone } from "./status-entrega";

interface MidiaMetadados {
  nome?: string;
  mimetype?: string;
  tamanho?: number;
  legenda?: string;
}

function metadadosDaMidia(json: string | null): MidiaMetadados {
  if (!json) {
    return {};
  }
  try {
    return JSON.parse(json) as MidiaMetadados;
  } catch {
    return {};
  }
}

type Props = {
  mensagem: MensagemResposta;
  onReenviar?: () => void;
};

/** Texto, imagem, áudio ou documento — a bolha renderiza os quatro tipos que o backend já entrega. */
export function BolhaMensagem({ mensagem, onReenviar }: Props) {
  const textos = useTextos().atendimentos.media;
  const doAtendente = mensagem.remetenteTipo !== "LEAD";
  const metadados = metadadosDaMidia(mensagem.midiaMetadados);
  const midiaUrl = urlSegura(mensagem.midiaUrl);
  const hora = new Date(mensagem.enviadoEm).toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });

  return (
    <div className={cn("flex", doAtendente ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[70%] rounded-lg px-3 py-2 text-sm",
          doAtendente ? "bg-primary text-primary-foreground" : "bg-muted text-foreground",
        )}
      >
        {mensagem.tipo === "IMAGEM" && (
          <div className="space-y-1">
            {midiaUrl && (
              // eslint-disable-next-line @next/next/no-img-element -- mídia externa do provedor, sem domínio fixo para o loader do Next
              <img
                src={midiaUrl}
                alt={metadados.legenda ?? textos.imagem}
                className="max-h-64 rounded-md object-cover"
              />
            )}
            {metadados.legenda && <p>{metadados.legenda}</p>}
          </div>
        )}

        {mensagem.tipo === "AUDIO" &&
          (midiaUrl ? (
            <audio controls src={midiaUrl} className="max-w-full" />
          ) : (
            <p>{textos.audio}</p>
          ))}

        {mensagem.tipo === "DOCUMENTO" && (
          <a
            href={midiaUrl ?? "#"}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-2 underline underline-offset-2"
          >
            <FileText className="size-4 shrink-0" aria-hidden />
            <span className="truncate">{metadados.nome ?? textos.documento}</span>
            <Download className="size-3.5 shrink-0" aria-hidden />
          </a>
        )}

        {mensagem.tipo === "TEXTO" && (
          <p className="whitespace-pre-wrap break-words">{mensagem.conteudo}</p>
        )}

        <div
          className={cn(
            "mt-1 flex items-center gap-1.5 text-[0.7rem]",
            doAtendente ? "justify-end text-primary-foreground/70" : "text-muted-foreground",
          )}
        >
          <span>{hora}</span>
          {doAtendente && (
            <StatusEntregaIcone status={mensagem.statusEntrega} onReenviar={onReenviar} />
          )}
        </div>
      </div>
    </div>
  );
}
