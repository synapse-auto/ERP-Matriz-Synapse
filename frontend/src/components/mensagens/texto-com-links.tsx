"use client";

import type { ReactNode } from "react";

type ParteDeTexto =
  | { tipo: "texto"; texto: string }
  | { tipo: "link"; texto: string; destino: string };

const URL_EXPLICITA = /https?:\/\/[^\s<>"'`]+/giu;
const CARACTERE_QUE_CONTINUA_URL = /[\p{L}\p{N}_@/.:+-]/u;
const PONTUACAO_FINAL = /[.,!?;:]/;

/**
 * Divide texto simples em texto e URLs absolutas HTTP(S). O texto continua sendo renderizado pelo
 * React como texto, sem interpretar HTML recebido de uma mensagem.
 */
export function partesDeTextoComLinks(texto: string): ParteDeTexto[] {
  const partes: ParteDeTexto[] = [];
  const encontrados = texto.matchAll(URL_EXPLICITA);
  let cursor = 0;

  for (const encontrado of encontrados) {
    const candidatoCru = encontrado[0];
    const inicio = encontrado.index ?? 0;
    const caractereAnterior = texto[inicio - 1];

    // Evita reconhecer um esquema embutido em palavra, endereço ou outro URI.
    if (caractereAnterior && CARACTERE_QUE_CONTINUA_URL.test(caractereAnterior)) continue;

    const urlComPontuacaoRemovida = removerPontuacaoFinal(candidatoCru);
    const destino = destinoHttpSeguro(urlComPontuacaoRemovida);
    if (!destino || !urlComPontuacaoRemovida) continue;

    if (inicio > cursor) {
      partes.push({ tipo: "texto", texto: texto.slice(cursor, inicio) });
    }
    partes.push({ tipo: "link", texto: urlComPontuacaoRemovida, destino });
    cursor = inicio + urlComPontuacaoRemovida.length;
  }

  if (cursor < texto.length) partes.push({ tipo: "texto", texto: texto.slice(cursor) });
  return partes.length ? partes : [{ tipo: "texto", texto }];
}

export function TextoComLinks({ texto, rotuloAbrir }: { texto: string; rotuloAbrir: string }) {
  const partes = partesDeTextoComLinks(texto);
  const nos: ReactNode[] = partes.map((parte, indice) => {
    if (parte.tipo === "texto") return parte.texto;

    return (
      <a
        key={`${indice}-${parte.destino}`}
        href={parte.destino}
        target="_blank"
        rel="noopener noreferrer"
        aria-label={rotuloAbrir.replaceAll("{nome}", parte.texto)}
        className="rounded-sm underline underline-offset-2 decoration-current transition-opacity hover:opacity-80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
      >
        {parte.texto}
      </a>
    );
  });

  return <>{nos}</>;
}

function destinoHttpSeguro(url: string): string | null {
  try {
    const analisada = new URL(url);
    if (
      !["http:", "https:"].includes(analisada.protocol) ||
      !analisada.hostname ||
      analisada.username ||
      analisada.password
    ) {
      return null;
    }
    return analisada.href;
  } catch {
    return null;
  }
}

function removerPontuacaoFinal(candidato: string): string {
  let fim = candidato.length;
  while (fim > 0 && PONTUACAO_FINAL.test(candidato[fim - 1])) fim--;

  let mudou = true;
  while (mudou && fim > 0) {
    mudou = false;
    for (const [abertura, fechamento] of [["(", ")"], ["[", "]"], ["{", "}"]]) {
      if (candidato[fim - 1] !== fechamento) continue;
      const trecho = candidato.slice(0, fim);
      const aberturas = [...trecho].filter((caractere) => caractere === abertura).length;
      const fechamentos = [...trecho].filter((caractere) => caractere === fechamento).length;
      if (fechamentos > aberturas) {
        fim--;
        while (fim > 0 && PONTUACAO_FINAL.test(candidato[fim - 1])) fim--;
        mudou = true;
        break;
      }
    }
  }

  return candidato.slice(0, fim);
}
