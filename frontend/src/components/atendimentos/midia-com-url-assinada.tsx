"use client";

import { urlSegura } from "@/lib/utils";
import { useUrlAssinadaDaMidia } from "@/lib/lead/use-url-assinada-da-midia";

type Props = {
  leadId: string;
  mensagemId: string;
  tipo: "IMAGEM" | "AUDIO";
  alt: string;
  rotuloAudio: string;
};

/** Preview do painel: pede a URL assinada na abertura do item, nunca coloca `/api/` em src. */
export function MidiaComUrlAssinada({ leadId, mensagemId, tipo, alt, rotuloAudio }: Props) {
  const { data } = useUrlAssinadaDaMidia(leadId, mensagemId);
  const src = urlSegura(data?.url);
  if (!src) {
    return null;
  }
  if (tipo === "IMAGEM") {
    // eslint-disable-next-line @next/next/no-img-element -- mídia do storage, domínio da instância
    return <img src={src} alt={alt} className="mt-1 max-h-28 w-full rounded object-contain" />;
  }
  return <audio className="mt-1 h-8 w-full" controls src={src} aria-label={rotuloAudio} />;
}
