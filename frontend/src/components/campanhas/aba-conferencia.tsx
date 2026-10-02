"use client";

import { useState } from "react";
import { ClipboardCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Skeleton } from "@/components/ui/skeleton";
import { useConferencia, useEhAdministradorDeCampanhas, useMarcarComoConferido } from "@/lib/campanhas/hooks";
import { useTextos } from "@/lib/config/textos-provider";

import { EstadoVazio } from "./estados";
import { PaginacaoSimples } from "./paginacao-simples";
import { TabelaDeDestinatarios } from "./tabela-de-destinatarios";

/** Conferência manual: envios cujo resultado não se sabe. Nunca reenvia; a pessoa confere no provedor. */
export function AbaConferencia({ campanhaId }: { campanhaId: string }) {
  const t = useTextos().campanhas.detalhe.conferencia;
  const ehAdministrador = useEhAdministradorDeCampanhas();
  const [pagina, setPagina] = useState(0);
  const consulta = useConferencia(campanhaId, pagina);
  const marcar = useMarcarComoConferido(campanhaId);

  return (
    <div className="space-y-4">
      <p className="max-w-3xl text-sm text-muted-foreground">{t.descricao}</p>
      {consulta.isPending && (
        <div role="status" aria-label={t.carregando} className="space-y-2">
          {Array.from({ length: 3 }, (_, indice) => (
            <Skeleton key={indice} className="h-12 w-full" />
          ))}
        </div>
      )}
      {consulta.isError && !consulta.data && (
        <ErroDeCarregamento mensagem={t.erroCarregar} onTentarNovamente={() => consulta.refetch()} />
      )}
      {consulta.data && consulta.data.itens.length === 0 && (
        <EstadoVazio icone={<ClipboardCheck className="size-6" aria-hidden />} titulo={t.vazio} descricao={t.descricao} />
      )}
      {consulta.data && consulta.data.itens.length > 0 && (
        <>
          <TabelaDeDestinatarios
            linhas={consulta.data.itens}
            legenda={t.titulo}
            rotuloDaAcao={t.titulo}
            acao={(linha) => (
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={!ehAdministrador || marcar.isPending}
                onClick={() => marcar.mutate(linha.id)}
              >
                {marcar.isPending && marcar.variables === linha.id ? t.marcando : t.marcar}
              </Button>
            )}
          />
          {!ehAdministrador && <p className="text-xs text-muted-foreground">{t.somenteAdministrador}</p>}
          {marcar.isError && (
            <p role="alert" className="text-xs text-destructive">
              {t.erro}
            </p>
          )}
          <PaginacaoSimples pagina={pagina} total={consulta.data.total} rotulo={t.titulo} aoMudar={setPagina} />
        </>
      )}
    </div>
  );
}
