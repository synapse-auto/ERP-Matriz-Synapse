"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";
import { disponibilidadeDarwin, estadoColetaDarwin, tentarColetaDarwin } from "@/lib/lead/darwin";

/** Consulta de estado não chama Darwin. Sem polling, CPF digitado ou dados de outro lead. */
export function ColetaDarwin({ leadId, permitido }: { leadId: string; permitido: boolean }) {
  const t = useTextos().dadosCliente;
  const textosDarwin = useTextos().darwin;
  const usuario = useAuthStore(s => s.usuarioId);
  const cache = useQueryClient();
  const disponibilidade = useQuery({ queryKey: ["darwin", "disponibilidade", usuario],
    queryFn: ({ signal }) => disponibilidadeDarwin(signal), enabled: permitido, retry: false });
  const estado = useQuery({ queryKey: ["darwin-coleta", leadId, usuario],
    queryFn: ({ signal }) => estadoColetaDarwin(leadId, signal),
    enabled: permitido && disponibilidade.data?.habilitada === true, retry: false });
  const tentativa = useMutation({ mutationFn: () => tentarColetaDarwin(leadId), retry: false,
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["darwin-coleta", leadId] });
      void cache.invalidateQueries({ queryKey: ["lead", leadId], exact: true });
    },
  });
  if (!permitido) return null;
  if (disponibilidade.data?.estado === "CONFIGURACAO_INCOMPLETA") return <p role="status" className="text-xs text-muted-foreground">{textosDarwin.configuracao}</p>;
  if (!disponibilidade.data?.habilitada || estado.data?.estado === "DESABILITADA") return null;
  const atual = estado.data?.estado;
  const processando = tentativa.isPending || atual === "PROCESSANDO";
  const mensagem = processando ? t.consultando : tentativa.isError || estado.isError ? t.falhaColeta
    : atual === "IDENTIFICACAO_PENDENTE" ? t.identificacaoPendente
    : atual === "ATUALIZADO" ? t.atualizado
    : atual === "DADOS_PARCIAIS" ? t.parcial
    : atual === "REVISAO_NECESSARIA" || atual === "IDENTIDADE_DIVERGENTE" ? t.revisao
    : atual === "NAO_ENCONTRADO" ? t.naoEncontrado
    : atual && atual !== "AGUARDANDO" ? t.falhaColeta : null;
  return <div className="space-y-2">
    {mensagem && <p role="status" className="text-xs text-muted-foreground">{mensagem}</p>}
    <Button type="button" variant="outline" size="sm" className="w-full gap-2" disabled={processando || atual === "IDENTIFICACAO_PENDENTE"}
      onClick={() => tentativa.mutate()}><RefreshCw className="size-4 shrink-0" aria-hidden />{t.consultar}</Button>
  </div>;
}
