"use client";

import { CalendarDays, FileText } from "lucide-react";
import { useTextos } from "@/lib/config/textos-provider";
import type { LeadFicha } from "@/lib/lead/types";

/** Nome e telefone ja estao na identidade da ficha; nao duplica nem consulta fonte externa. */
export function DadosClienteLocais({ lead }: { lead: LeadFicha }) {
  const t = useTextos().dadosCliente;
  const cpf = lead.cpf?.trim();
  const digitos = cpf?.replace(/\D/g, "");
  const documento = digitos?.length === 11
    ? digitos.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, "$1.$2.$3-$4") : cpf;
  const nascimento = dataLocal(lead.dadosCustomizados?.data_nascimento);
  return <dl className="space-y-3" aria-label={t.titulo}>
    {[{ rotulo: t.cpf, valor: documento, Icone: FileText }, { rotulo: t.nascimento, valor: nascimento, Icone: CalendarDays }].map(({ rotulo, valor, Icone }) =>
      <div key={rotulo} className="flex min-w-0 items-center gap-2.5">
        <Icone className="size-(--tamanho-icone-interface) shrink-0 text-primary" aria-hidden />
        <div className="min-w-0"><dt className="text-xs text-muted-foreground">{rotulo}</dt><dd className="break-words text-sm text-foreground">{valor || t.naoInformado}</dd></div>
      </div>)}
  </dl>;
}

function dataLocal(valor: unknown): string | null {
  if (typeof valor !== "string") return null;
  // Valor civil, nao instante: nao deslocar nascimento pelo fuso do browser.
  const partes = /^(\d{4})-(\d{2})-(\d{2})(?:T00:00:00(?:\.000)?Z)?$/.exec(valor);
  if (!partes) return null;
  const [, ano, mes, dia] = partes;
  const data = new Date(`${ano}-${mes}-${dia}T00:00:00Z`);
  if (!Number.isFinite(data.getTime()) || data.toISOString().slice(0, 10) !== `${ano}-${mes}-${dia}`) return null;
  return `${dia}/${mes}/${ano}`;
}
