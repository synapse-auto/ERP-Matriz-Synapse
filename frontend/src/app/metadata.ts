import type { Metadata } from "next";

import type { Tema, Textos } from "@/lib/config/schema";

/**
 * A logo da instância só participa da identidade da aba do navegador. A tela de login continua
 * usando a marca fixa Synapse e não recebe tokens visuais do tema do cliente.
 */
export function montarMetadata(textos: Textos, tema: Tema): Metadata {
  const metadata: Metadata = {
    title: textos.app.nome,
    description: textos.app.subtitulo,
  };

  if (tema.logoUrl) {
    metadata.icons = { icon: tema.logoUrl };
  }

  return metadata;
}
