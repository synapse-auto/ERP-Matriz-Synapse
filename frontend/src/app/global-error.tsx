"use client";

import { useEffect } from "react";
import { TelaDeErro } from "@/components/erros/tela-de-erro";
import type { PropsErroDeRota } from "@/components/erros/erro-de-rota";
import { registrarErroDeTela } from "@/lib/erros/conexao";
import { classesDasFontes } from "./fontes";
import "./globals.css";

export default function ErroGlobal({ error, retry }: PropsErroDeRota) {
  useEffect(() => registrarErroDeTela("raiz", error), [error]);
  return (
    <html lang="pt-BR" className={`h-full antialiased ${classesDasFontes}`}>
      <body className="flex min-h-dvh flex-col bg-background font-sans text-foreground">
        <TelaDeErro tipo="renderizacao" onTentarNovamente={retry} />
      </body>
    </html>
  );
}
