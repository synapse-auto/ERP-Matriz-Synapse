"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ErroDeApi } from "@/lib/api/errors";
import {
  buscarDestinosDoEncaminhamento,
  encaminharAoCliente,
  listarEncaminhamentosAoCliente,
  previaDoEncaminhamentoAoCliente,
} from "@/lib/chat-interno/api";
import type {
  ChatMensagem,
  DestinoDoEncaminhamento,
  EncaminhamentoAoCliente,
  PreviaDoEncaminhamentoAoCliente,
  StatusDaEntregaAoCliente,
} from "@/lib/chat-interno/types";
import type { Textos } from "@/lib/config/schema";

type TextosEncaminhar = Textos["chatInterno"]["encaminharCliente"];

const STATUS_FINAIS: StatusDaEntregaAoCliente[] = ["ENVIADO", "ENTREGUE", "LIDO", "FALHOU"];
/** Intervalo de leitura do estado da entrega enquanto o provedor ainda não respondeu. */
const INTERVALO_DO_STATUS_MS = 2000;
/** Espera depois da última tecla antes de buscar no servidor. */
const ATRASO_DA_BUSCA_MS = 300;

type Props = {
  /** A mensagem a encaminhar; nulo mantém o diálogo fechado. */
  mensagem: ChatMensagem | null;
  conversaId: string;
  textos: TextosEncaminhar;
  onFechar: () => void;
};

/** Traduz a recusa do backend para o catálogo; nenhuma frase fixa no componente. */
function mensagemDeErro(erro: unknown, textos: TextosEncaminhar): string {
  if (erro instanceof ErroDeApi) {
    const motivo = erro.problema?.motivo as keyof TextosEncaminhar["bloqueio"] | undefined;
    if (motivo && motivo in textos.bloqueio) return textos.bloqueio[motivo];
    if (erro.status === 403) return textos.erro["403"];
    if (erro.status === 404) return textos.erro["404"];
    if (erro.status === 409) return textos.erro["409"];
    if (erro.status === 422) return textos.erro["422"];
  }
  return textos.erro.generico;
}

export function DialogoEncaminharAoCliente({ mensagem, conversaId, textos, onFechar }: Props) {
  const aberto = mensagem !== null;
  const cache = useQueryClient();
  const [busca, setBusca] = useState("");
  const [destino, setDestino] = useState<DestinoDoEncaminhamento | null>(null);
  const [buscaAplicada, setBuscaAplicada] = useState("");
  const [envio, setEnvio] = useState<EncaminhamentoAoCliente | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  // Uma chave por tentativa de envio: repetir o clique (ou a rede) nunca reenvia ao cliente.
  const chave = useRef<string | null>(null);
  // Trava síncrona: o estado de pendência da mutação só chega no render seguinte, e dois cliques
  // no mesmo instante passariam os dois.
  const emCurso = useRef(false);

  useEffect(() => {
    const espera = setTimeout(() => setBuscaAplicada(busca.trim()), ATRASO_DA_BUSCA_MS);
    return () => clearTimeout(espera);
  }, [busca]);

  // A busca é do servidor, sob o alcance do usuário (RN-CRM-01): a tela não recorta nada por conta própria.
  const destinos = useQuery({
    queryKey: ["chat-interno", "encaminhar-cliente", "destinos", buscaAplicada],
    enabled: aberto,
    queryFn: () => buscarDestinosDoEncaminhamento(buscaAplicada),
    placeholderData: (anterior) => anterior,
  });

  const previa = useQuery({
    queryKey: ["chat-interno", "encaminhar-cliente", "previa", conversaId, mensagem?.id, destino?.atendimentoId],
    enabled: aberto && destino !== null && envio === null,
    retry: false,
    queryFn: () => previaDoEncaminhamentoAoCliente(destino!.atendimentoId, conversaId, mensagem!.id),
  });

  const status = useQuery({
    queryKey: ["chat-interno", "encaminhar-cliente", "status", conversaId, mensagem?.id, envio?.id],
    enabled: aberto && envio !== null,
    initialData: envio ? [envio] : undefined,
    queryFn: () => listarEncaminhamentosAoCliente(conversaId, mensagem!.id),
    refetchInterval: (consulta) => {
      const atual = consulta.state.data?.find((item) => item.id === envio?.id);
      return atual && STATUS_FINAIS.includes(atual.statusEntrega) ? false : INTERVALO_DO_STATUS_MS;
    },
  });
  const atual = status.data?.find((item) => item.id === envio?.id) ?? envio;

  const enviar = useMutation({
    mutationFn: () => {
      chave.current ??= crypto.randomUUID();
      return encaminharAoCliente(destino!.atendimentoId, conversaId, mensagem!.id, chave.current);
    },
    onSuccess: (resultado) => {
      setEnvio(resultado);
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
    },
    onError: (falha) => setErro(mensagemDeErro(falha, textos)),
    onSettled: () => {
      emCurso.current = false;
    },
  });

  function confirmar() {
    if (emCurso.current || !previa.data?.podeEnviar) return;
    emCurso.current = true;
    setErro(null);
    enviar.mutate();
  }

  function escolher(escolhido: DestinoDoEncaminhamento) {
    chave.current = null;
    setErro(null);
    setDestino(escolhido);
  }

  function voltar() {
    chave.current = null;
    setErro(null);
    setDestino(null);
  }

  function fechar() {
    if (enviar.isPending) return;
    chave.current = null;
    emCurso.current = false;
    setBusca("");
    setBuscaAplicada("");
    setDestino(null);
    setEnvio(null);
    setErro(null);
    onFechar();
  }

  return (
    <Dialog open={aberto} onOpenChange={(valor) => { if (!valor) fechar(); }}>
      <DialogContent className="min-w-0 w-[calc(100vw-2rem)] max-h-[calc(100dvh-2rem)] overflow-x-hidden overflow-y-auto">
        <DialogHeader className="min-w-0 pr-8">
          <DialogTitle>{envio ? textos.statusTitulo : destino ? textos.previaTitulo : textos.titulo}</DialogTitle>
          {!destino && !envio && <DialogDescription>{textos.descricao}</DialogDescription>}
        </DialogHeader>

        {!destino && !envio && (
          <SeletorDeDestino
            textos={textos}
            busca={busca}
            onBusca={setBusca}
            carregando={destinos.isPending}
            falhou={destinos.isError}
            temBusca={buscaAplicada !== ""}
            destinos={destinos.data ?? []}
            onEscolher={escolher}
          />
        )}

        {destino && !envio && (
          <PreviaDoEnvio
            textos={textos}
            carregando={previa.isPending}
            falhou={previa.isError}
            previa={previa.data}
          />
        )}

        {envio && atual && <AcompanhamentoDoEnvio textos={textos} envio={atual} />}

        {erro && <p className="text-sm text-destructive" role="alert">{erro}</p>}

        <DialogFooter className="min-w-0">
          {envio ? (
            <Button type="button" onClick={fechar}>{textos.fechar}</Button>
          ) : (
            <>
              <Button type="button" variant="ghost" onClick={destino ? voltar : fechar} disabled={enviar.isPending}>
                {destino ? textos.voltar : textos.cancelar}
              </Button>
              {destino && (
                <Button
                  type="button"
                  onClick={confirmar}
                  disabled={enviar.isPending || !previa.data?.podeEnviar}
                >
                  {enviar.isPending ? textos.enviando : textos.confirmar}
                </Button>
              )}
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function SeletorDeDestino({
  textos,
  busca,
  onBusca,
  carregando,
  falhou,
  temBusca,
  destinos,
  onEscolher,
}: {
  textos: TextosEncaminhar;
  busca: string;
  onBusca: (valor: string) => void;
  carregando: boolean;
  falhou: boolean;
  temBusca: boolean;
  destinos: DestinoDoEncaminhamento[];
  onEscolher: (destino: DestinoDoEncaminhamento) => void;
}) {
  return (
    <div className="min-w-0 w-full space-y-2">
      <Input
        value={busca}
        onChange={(evento) => onBusca(evento.target.value)}
        placeholder={textos.buscar}
        aria-label={textos.buscar}
        className="min-w-0 w-full"
      />
      {falhou ? (
        <p className="text-sm text-destructive" role="alert">{textos.erroDestinos}</p>
      ) : carregando ? (
        <p className="text-sm text-muted-foreground">{textos.carregandoDestinos}</p>
      ) : destinos.length === 0 ? (
        <p className="text-sm text-muted-foreground">{temBusca ? textos.semResultado : textos.semDestinos}</p>
      ) : (
        <ul className="min-w-0 w-full max-h-64 space-y-1 overflow-x-hidden overflow-y-auto" aria-label={textos.titulo}>
          {destinos.map((destino) => (
            <li key={destino.atendimentoId} className="min-w-0">
              <Button
                type="button"
                variant="ghost"
                className="h-auto min-w-0 w-full justify-between gap-2 py-2 text-left"
                aria-label={textos.escolher.replace("{cliente}", destino.clienteNome)}
                onClick={() => onEscolher(destino)}
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium" title={destino.clienteNome}>{destino.clienteNome}</span>
                  <span className="block text-xs text-muted-foreground">{destino.telefoneMascarado}</span>
                </span>
                <span className="min-w-0 max-w-[40%] shrink truncate text-right text-xs text-muted-foreground" title={destino.responsavelNome ?? textos.semResponsavel}>
                  {destino.responsavelNome ?? textos.semResponsavel}
                </span>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function PreviaDoEnvio({
  textos,
  carregando,
  falhou,
  previa,
}: {
  textos: TextosEncaminhar;
  carregando: boolean;
  falhou: boolean;
  previa: PreviaDoEncaminhamentoAoCliente | undefined;
}) {
  if (falhou) return <p className="text-sm text-destructive" role="alert">{textos.erroPrevia}</p>;
  if (carregando || !previa) return <p className="text-sm text-muted-foreground">{textos.carregandoPrevia}</p>;
  const tipo = textos.tipo[previa.tipo as keyof TextosEncaminhar["tipo"]] ?? previa.tipo;
  return (
    <div className="min-w-0 space-y-3 text-sm">
      <dl className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1">
        <dt className="text-muted-foreground">{textos.cliente}</dt>
        <dd className="min-w-0 break-words font-medium">{previa.clienteNome}</dd>
        <dt className="text-muted-foreground">{textos.telefone}</dt>
        <dd className="min-w-0 break-words">{previa.telefoneMascarado}</dd>
        <dt className="text-muted-foreground">{textos.responsavel}</dt>
        <dd className="min-w-0 break-words">{previa.responsavelNome ?? textos.semResponsavel}</dd>
        <dt className="text-muted-foreground">{textos.conteudo}</dt>
        <dd>{tipo}</dd>
        {previa.nomeArquivo && (
          <>
            <dt className="text-muted-foreground">{textos.arquivo}</dt>
            <dd className="min-w-0 truncate" title={previa.nomeArquivo}>{previa.nomeArquivo}</dd>
          </>
        )}
      </dl>
      {(previa.texto || previa.legenda) && (
        <p className="max-h-32 overflow-x-hidden overflow-y-auto whitespace-pre-wrap break-words rounded-md border bg-muted/40 p-2">
          {previa.texto ?? previa.legenda}
        </p>
      )}
      <p>{textos.efeito[previa.efeito]}</p>
      {previa.bloqueio && (
        <p className="text-destructive" role="alert">{textos.bloqueio[previa.bloqueio]}</p>
      )}
    </div>
  );
}

function AcompanhamentoDoEnvio({ textos, envio }: { textos: TextosEncaminhar; envio: EncaminhamentoAoCliente }) {
  return (
    <div className="space-y-2 text-sm" aria-live="polite">
      <p className="font-medium">{textos.status[envio.statusEntrega]}</p>
      {envio.statusEntrega === "FALHOU" && <p className="text-destructive" role="alert">{textos.falhou}</p>}
      {envio.reutilizado && <p className="text-muted-foreground">{textos.jaEnviado}</p>}
      {envio.transferiuOLead && <p>{textos.assumiu}</p>}
      {envio.conviteCriado && <p>{textos.conviteCriado}</p>}
    </div>
  );
}
