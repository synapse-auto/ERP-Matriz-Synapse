import { FileText, Image, MapPin, Music2, Video } from "lucide-react";

import type { CitacaoMensagem, OrigemDaCitacao } from "@/lib/atendimento/types";
import { previaExibida } from "@/lib/atendimento/citacao";
import { urlSegura } from "@/lib/utils";
import type { Textos } from "@/lib/config/schema";

type TextosCitacao = Textos["atendimentos"]["mensagem"]["citacao"];

type Props = {
  citacao: CitacaoMensagem;
  textos: TextosCitacao;
  origem?: OrigemDaCitacao | null;
  carregandoOrigem?: boolean;
  onNavegar?: () => void;
};

type MetadadosDaOrigem = {
  nome?: string;
  legenda?: string;
  mimetype?: string;
  duracaoSegundos?: number;
  duracao?: number;
};

/**
 * Prévia persistida — texto React, nunca HTML da origem. Quando a origem está disponível, a
 * miniatura usa somente a URL assinada que o endpoint autorizado devolveu.
 */
export function CitacaoMensagemVisual({
  citacao,
  textos,
  origem,
  carregandoOrigem = false,
  onNavegar,
}: Props) {
  const titulo =
    citacao.tipoReferencia === "ENCAMINHAMENTO"
      ? textos.encaminhamento
      : textos.resposta.replace("{autor}", citacao.autor.trim() || textos.origemIndisponivel);
  const origemRemovida = Boolean(citacao.origemRemovida || origem?.removida);
  const tipo = origem?.tipo ?? citacao.tipoConteudo;
  const metadados = lerMetadados(origem?.midiaMetadados);
  const previa = origemRemovida
    ? textos.mensagemRemovida ?? textos.origemIndisponivel
    : previaExibida(citacao, {
        imagem: textos.imagem,
        audio: textos.audio,
        documento: textos.documento,
        video: textos.video ?? textos.origemIndisponivel,
        localizacao: textos.localizacao ?? textos.origemIndisponivel,
        origemIndisponivel: textos.origemIndisponivel,
      });
  const src = origemRemovida ? undefined : urlSegura(origem?.midiaUrl);
  const navegavel = Boolean(onNavegar && !origemRemovida && citacao.origemId);
  const conteudo = (
    <>
      {carregandoOrigem ? (
        <span className="block h-10 w-14 animate-pulse rounded bg-muted" aria-hidden />
      ) : !origemRemovida && tipo === "IMAGEM" && src ? (
        // eslint-disable-next-line @next/next/no-img-element -- URL curta assinada pelo backend
        <img
          src={src}
          alt={metadados.legenda ?? textos.imagem}
          className="size-10 shrink-0 rounded object-cover"
        />
      ) : !origemRemovida ? (
        <IconeDaCitacao tipo={tipo} textos={textos} duracao={metadados.duracaoSegundos ?? metadados.duracao} />
      ) : null}
      <span className="min-w-0 flex-1">
        <span className="block font-semibold">{titulo}</span>
        <span className="block truncate opacity-80">
          {origemRemovida ? previa : metadados.legenda || previa}
        </span>
      </span>
    </>
  );

  return (
    <div className="mb-1.5">
      {navegavel ? (
        <button
          type="button"
          className="flex w-full items-center gap-2 rounded-md border-l-2 border-current/40 bg-background/15 px-2 py-1 text-left text-xs transition-colors hover:bg-background/25 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          aria-label={textos.irParaOrigem ?? textos.origemIndisponivel}
          onClick={onNavegar}
        >
          {conteudo}
        </button>
      ) : (
        <div className="flex items-center gap-2 rounded-md border-l-2 border-current/40 bg-background/15 px-2 py-1 text-xs">
          {conteudo}
        </div>
      )}
    </div>
  );
}

function IconeDaCitacao({
  tipo,
  textos,
  duracao,
}: {
  tipo: string | null | undefined;
  textos: TextosCitacao;
  duracao?: number;
}) {
  const Icone = tipo === "IMAGEM" ? Image
    : tipo === "VIDEO" ? Video
      : tipo === "AUDIO" ? Music2
        : tipo === "LOCALIZACAO" ? MapPin
          : FileText;
  const rotulo = tipo === "IMAGEM" ? textos.imagem
    : tipo === "VIDEO" ? (textos.video ?? textos.origemIndisponivel)
    : tipo === "AUDIO" ? textos.audio
      : tipo === "LOCALIZACAO" ? (textos.localizacao ?? textos.origemIndisponivel)
          : textos.documento;
  return (
    <span className="flex size-10 shrink-0 flex-col items-center justify-center rounded bg-muted/60 text-muted-foreground" aria-label={rotulo}>
      <Icone className="size-4" aria-hidden />
      {tipo === "AUDIO" && typeof duracao === "number" && Number.isFinite(duracao) && duracao > 0 && (
        <span className="text-[0.6rem] leading-none">{formatarDuracao(duracao)}</span>
      )}
    </span>
  );
}

function lerMetadados(valor: OrigemDaCitacao["midiaMetadados"]): MetadadosDaOrigem {
  if (!valor) return {};
  if (typeof valor === "object") return valor as MetadadosDaOrigem;
  try {
    const parseado: unknown = JSON.parse(valor);
    return parseado && typeof parseado === "object" ? parseado as MetadadosDaOrigem : {};
  } catch {
    return {};
  }
}

function formatarDuracao(segundos: number): string {
  const inteiro = Math.max(0, Math.floor(segundos));
  return `${Math.floor(inteiro / 60)}:${String(inteiro % 60).padStart(2, "0")}`;
}
