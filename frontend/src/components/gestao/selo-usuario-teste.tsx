"use client";

import { PillDeStatus } from "@/components/ui/pill-de-status";
import { useTextos } from "@/lib/config/textos-provider";

export function SeloUsuarioTeste({ teste }: { teste?: boolean }) {
  const { usuariosTeste } = useTextos();
  return teste ? <PillDeStatus tom="neutro">{usuariosTeste.selo}</PillDeStatus> : null;
}
