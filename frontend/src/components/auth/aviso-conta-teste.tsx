"use client";
import {useMeuUsuario} from "@/lib/equipe/use-equipe";
import {useTextos} from "@/lib/config/textos-provider";

export function AvisoContaTeste() {
  const usuario=useMeuUsuario();
  const {usuariosTeste}=useTextos();
  return usuario.data?.usuarioTeste===true
    ? <p role="status" className="shrink-0 border-b border-border bg-muted px-4 py-2 text-sm font-semibold text-foreground">{usuariosTeste.modoLeitura}</p> : null;
}
