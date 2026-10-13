"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { registrarErroDeTela } from "@/lib/erros/conexao";
import { TelaDeErro } from "./tela-de-erro";

export type PropsErroDeRota = { error: Error & { digest?: string }; retry: () => void };

export function ErroDeRota({ error, retry }: PropsErroDeRota) {
  useEffect(() => registrarErroDeTela("rota", error), [error]);
  return <TelaDeErro tipo="renderizacao" onTentarNovamente={retry} />;
}

/** Refaz só a leitura/renderização no servidor. Não recarrega o documento nem reenvia comandos. */
export function BackendIndisponivel() {
  const router = useRouter();
  return <TelaDeErro tipo="backend" onTentarNovamente={() => router.refresh()} />;
}
