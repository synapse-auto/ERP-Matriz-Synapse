"use client";

import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ErroDeApi } from "@/lib/api/errors";
import { useTextos } from "@/lib/config/textos-provider";
import { useCapacidades } from "@/lib/gestao/use-capacidades";
import { aprovarPedido, recusarPedido, invalidarParticipacao, useMeuPedido } from "./use-participacao";
import type { EstadoAtendimentoSelecionado } from "./types";

/** Um único controlador para os dois pontos de resposta; nunca libera envio por otimismo. */
export function useConviteRecebido(estado: EstadoAtendimentoSelecionado | null, reconciliar: () => Promise<unknown>) {
  const id = estado?.cartao.atendimentoId ?? "";
  const pedido = useMeuPedido(id);
  const cache = useQueryClient();
  const textos = useTextos().atendimentos.cabecalho;
  const capacidades = useCapacidades();
  const trava = useRef(false);
  const [processando, setProcessando] = useState(false);
  const [aceiteConfirmadoId, setAceiteConfirmadoId] = useState<string | null>(null);
  // Depois da confirmação canônica, um convite futuro no mesmo atendimento é outra operação.
  if (aceiteConfirmadoId === id && estado?.usuarioAtualParticipa) setAceiteConfirmadoId(null);
  const [retorno, setRetorno] = useState<{ id: string; tipo: "erro" | "sucesso"; texto: string } | null>(null);
  const pendente = Boolean(estado && !estado.usuarioAtualParticipa && (
    estado.cartao.convitePendente || (pedido?.tipo === "CONVITE" && pedido.status === "PENDENTE")
    || aceiteConfirmadoId === id
  ));

  async function responder(aceitar: boolean) {
    if (trava.current || !pendente || aceiteConfirmadoId === id || !capacidades.pode("atendimentos.colaborar") || pedido?.tipo !== "CONVITE" || pedido.status !== "PENDENTE") return;
    trava.current = true;
    setProcessando(true);
    setRetorno(null);
    try {
      await (aceitar ? aprovarPedido(pedido.id) : recusarPedido(pedido.id));
      if (aceitar) setAceiteConfirmadoId(id);
      setRetorno({ id, tipo: "sucesso", texto: aceitar ? textos.sucessoConviteAceito : textos.sucessoConviteRecusado });
      invalidarParticipacao(id);
      // A operação já foi confirmada. Falha de atualização não autoriza envio nem desfaz o aceite.
      await cache.invalidateQueries({ queryKey: ["atendimentos"] }).catch(() => undefined);
      await reconciliar().catch(() => undefined);
    } catch (erro) {
      const status = erro instanceof ErroDeApi ? erro.status : undefined;
      setRetorno({ id, tipo: "erro", texto: status === 409 ? textos.conviteAtualizado
        : status === 403 ? textos.erroSemPermissao
          : status === 404 ? textos.erroParticipacaoNaoEncontrada : textos.erroParticipacao });
      if (status === 404 || status === 409) {
        invalidarParticipacao(id);
        void cache.invalidateQueries({ queryKey: ["atendimentos"] });
        await reconciliar().catch(() => undefined);
      }
    } finally {
      trava.current = false;
      setProcessando(false);
    }
  }
  return { pendente, processando, podeResponder: Boolean(capacidades.pode("atendimentos.colaborar") && aceiteConfirmadoId !== id && pedido?.tipo === "CONVITE" && pedido.status === "PENDENTE"),
    aceitar: () => responder(true), recusar: () => responder(false),
    feedback: retorno?.id === id ? retorno : null };
}

export type ConviteRecebido = ReturnType<typeof useConviteRecebido>;
