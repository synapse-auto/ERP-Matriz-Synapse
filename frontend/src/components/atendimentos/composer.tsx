"use client";

import { type ChangeEvent, type ClipboardEvent, type KeyboardEvent, type Ref, useEffect, useImperativeHandle, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  Clock,
  File,
  LayoutTemplate,
  Mic,
  Paperclip,
  Send,
  Square,
  Trash2,
  X,
  Zap,
  type LucideIcon,
} from "lucide-react";

import { Button, buttonVariants } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Textarea } from "@/components/ui/textarea";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { ErroDeApi } from "@/lib/api/errors";
import { estadoDaJanelaTextoLivre } from "@/lib/atendimento/janela-24h";
import { listarTemplatesWhatsApp, obterCapacidadeDoCanal } from "@/lib/atendimento/api";
import { arquivosDaAreaDeTransferencia, filtrarArquivos, TIPOS_DE_ANEXO_ACEITOS } from "@/lib/atendimento/arquivos-do-composer";
import { citacaoDeResposta } from "@/lib/atendimento/citacao";
import { motivoDaFalhaDeMidia, type FalhaDeEnvioMidia } from "@/lib/atendimento/falhas-de-midia";
import { useConfiguracaoComposer } from "@/lib/atendimento/use-configuracao-composer";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useEnviarMidia } from "@/lib/atendimento/use-enviar-midia";
import type { CartaoAtendimento, MensagemResposta } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { listarMensagensRapidas } from "@/lib/suporte/api";
import { useLead } from "@/lib/lead/use-painel-lead";
import { resolverMensagemRapida } from "@/lib/suporte/resolver-mensagem-rapida";
import { PainelEmojiComposer } from "@/components/mensagens/painel-emoji-composer";
import { FormularioMensagemProgramada } from "@/components/mensagens-programadas/formulario-mensagem-programada";
import { inserirNoCursor, posicionarCursor } from "@/lib/mensagens/inserir-no-cursor";

import { CitacaoMensagemVisual } from "./citacao-mensagem";
import { ModalDeTemplates } from "./modal-de-templates";
import { useGravadorAudio } from "./use-gravador-audio";

type Props = {
  conversa: CartaoAtendimento;
  resposta?: MensagemResposta | null;
  onCancelarResposta?: () => void;
  onMensagemEnviada?: () => void;
  onFalhasDeMidia?: (falhas: FalhaDeEnvioMidia[]) => void;
  ref?: Ref<ComposerHandle>;
};

export type ComposerHandle = {
  adicionarArquivos: (novos: File[]) => void;
};

function tamanhoLegivel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function duracaoLegivel(segundos: number): string {
  const minutos = Math.floor(segundos / 60);
  return `${String(minutos).padStart(2, "0")}:${String(segundos % 60).padStart(2, "0")}`;
}

let sequenciaFalhaDeMidia = 0;

function idDaFalhaDeMidia(): string {
  sequenciaFalhaDeMidia += 1;
  return `falha-midia-${sequenciaFalhaDeMidia}`;
}

function AcaoMenuAnexo({
  label,
  icone: Icone,
  aoSelecionar,
  disabled,
}: {
  label: string;
  icone: LucideIcon;
  aoSelecionar: () => void;
  disabled: boolean;
}) {
  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <button
            type="button"
            role="menuitem"
            aria-label={label}
            className="flex size-12 items-center justify-center rounded-lg border border-transparent text-muted-foreground transition-colors outline-none hover:bg-muted hover:text-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50"
            disabled={disabled}
            onClick={aoSelecionar}
          >
            <Icone className="size-[calc(var(--tamanho-icone-interface)*1.5)]" aria-hidden />
          </button>
        }
      />
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}

/**
 * Três pontos do prompt E11/E11b: estado real de entrega (delegado a `useEnviarMensagem`/
 * `useEnviarMidia`), aviso de janela de 24h ANTES de digitar, e anexo — imagem, áudio ou
 * documento — com seleção, preview, progresso de upload e erro acionável.
 */
export function Composer({
  conversa,
  resposta = null,
  onCancelarResposta,
  onMensagemEnviada,
  onFalhasDeMidia,
  ref,
}: Props) {
  const catalogo = useTextos();
  const textosAtendimentos = catalogo.atendimentos;
  const textos = textosAtendimentos.composer;
  const [texto, setTexto] = useState("");
  const [arquivos, setArquivos] = useState<File[]>([]);
  const [avisoTipo, setAvisoTipo] = useState(false);
  const [progresso, setProgresso] = useState<number | null>(null);
  const [indiceEnvio, setIndiceEnvio] = useState<number | null>(null);
  const [falhasArquivos, setFalhasArquivos] = useState<Map<File, string>>(new Map());
  const [agendamentoAberto, setAgendamentoAberto] = useState(false);
  const [painelTemplateAberto, setPainelTemplateAberto] = useState(false);
  const [menuAnexoAberto, setMenuAnexoAberto] = useState(false);
  const [modoMenuAnexo, setModoMenuAnexo] = useState<"acoes" | "mensagens-rapidas">("acoes");
  const [atalhoSelecionado, setAtalhoSelecionado] = useState(0);
  const inputArquivoRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const composerContainerRef = useRef<HTMLDivElement>(null);
  const enviar = useEnviarMensagem(onMensagemEnviada);
  const enviarMidia = useEnviarMidia();
  const configuracaoComposer = useConfiguracaoComposer();
  const gravador = useGravadorAudio(configuracaoComposer.data);
  const rapidas = useQuery({
    queryKey: ["mensagens-rapidas", "minhas"],
    queryFn: () => listarMensagensRapidas(true),
  });
  const lead = useLead(conversa.leadId);
  const [variaveisPendentes, setVariaveisPendentes] = useState<string[]>([]);
  const capacidadeDoCanal = useQuery({
    queryKey: ["config", "canal"],
    queryFn: obterCapacidadeDoCanal,
    staleTime: 5 * 60 * 1000,
  });
  const exigeTemplateForaDaJanela = capacidadeDoCanal.data?.exigeTemplateForaDaJanela ?? true;
  const estadoDaJanela = estadoDaJanelaTextoLivre(conversa.ultimaMensagemDoLeadEm);
  const janelaAberta = !exigeTemplateForaDaJanela || estadoDaJanela === "aberta";
  const templates = useQuery({
    queryKey: ["whatsapp-templates"],
    queryFn: listarTemplatesWhatsApp,
    enabled:
      conversa.status !== "FINALIZADO" &&
      capacidadeDoCanal.data?.gerenciaTemplates !== false &&
      (!janelaAberta || painelTemplateAberto),
  });
  const [parametros, setParametros] = useState<Record<string, string[]>>({});
  const citacaoResposta = resposta ? citacaoDeResposta(resposta) : null;

  function fecharMenuAnexo() {
    setMenuAnexoAberto(false);
    setModoMenuAnexo("acoes");
  }

  function aoAlterarMenuAnexo(aberto: boolean) {
    setMenuAnexoAberto(aberto);
    if (!aberto) setModoMenuAnexo("acoes");
  }

  function abrirTemplatesPeloMenu() {
    fecharMenuAnexo();
    setPainelTemplateAberto(true);
  }

  function abrirMensagensRapidasPeloMenu() {
    setModoMenuAnexo("mensagens-rapidas");
  }

  function selecionarMensagemRapida(mensagem: NonNullable<typeof rapidas.data>[number]) {
    const resolvida = resolverMensagemRapida(mensagem.conteudo, { nome: lead.data?.nome ?? "", empresa: lead.data?.empresa });
    setTexto(resolvida.texto);
    setVariaveisPendentes(resolvida.pendentes);
    fecharMenuAnexo();
  }

  function navegarPelasAcoesDoMenu(evento: KeyboardEvent<HTMLDivElement>) {
    if (modoMenuAnexo !== "acoes") return;
    const acoes = Array.from(
      evento.currentTarget.querySelectorAll<HTMLButtonElement>('[role="menuitem"]'),
    );
    if (acoes.length === 0) return;
    const indiceAtual = acoes.indexOf(document.activeElement as HTMLButtonElement);
    let proximoIndice: number | null = null;

    if (evento.key === "ArrowRight" || evento.key === "ArrowDown") {
      proximoIndice = (indiceAtual + 1 + acoes.length) % acoes.length;
    } else if (evento.key === "ArrowLeft" || evento.key === "ArrowUp") {
      proximoIndice = (indiceAtual - 1 + acoes.length) % acoes.length;
    } else if (evento.key === "Home") {
      proximoIndice = 0;
    } else if (evento.key === "End") {
      proximoIndice = acoes.length - 1;
    }

    if (proximoIndice === null) return;
    evento.preventDefault();
    acoes[proximoIndice]?.focus();
  }

  function adicionarArquivos(novos: File[]) {
    if (!janelaAberta || gravador.fase !== "INATIVO" || enviarMidia.isPending) return;
    const { aceitos, rejeitados } = filtrarArquivos(novos, TIPOS_DE_ANEXO_ACEITOS);
    if (aceitos.length > 0) {
      setArquivos((atual) => [...atual, ...aceitos]);
    }
    setAvisoTipo(rejeitados.length > 0);
  }

  useImperativeHandle(ref, () => ({ adicionarArquivos }));

  useEffect(() => {
    if (resposta) textareaRef.current?.focus();
  }, [resposta]);

  useEffect(() => {
    const composer = composerContainerRef.current;
    const zona = composer?.parentElement;
    if (!composer || !zona || conversa.status === "FINALIZADO" || !janelaAberta) return;

    const atualizarAltura = () => {
      zona.style.setProperty("--altura-composer", `${composer.getBoundingClientRect().height}px`);
    };
    atualizarAltura();

    const observador = typeof ResizeObserver === "undefined"
      ? null
      : new ResizeObserver(atualizarAltura);
    observador?.observe(composer);
    return () => {
      observador?.disconnect();
      zona.style.removeProperty("--altura-composer");
    };
  }, [conversa.status, janelaAberta]);

  if (conversa.status === "FINALIZADO") {
    return (
      <div className="shrink-0 bg-background px-4 pb-4 pt-3">
        <div className="mx-auto max-w-[780px] rounded-xl border border-input bg-card p-3 text-center text-sm text-muted-foreground shadow-md">
          {textosAtendimentos.finalizar.sucesso}
        </div>
      </div>
    );
  }

  function alvoDeResposta() {
    return resposta
      ? { mensagemId: resposta.id, enviadoEm: resposta.enviadoEm }
      : undefined;
  }

  function limparAposEnvio() {
    setArquivos([]);
    setTexto("");
    setProgresso(null);
    setIndiceEnvio(null);
    setAvisoTipo(false);
    setFalhasArquivos(new Map());
    onCancelarResposta?.();
  }

  async function enviarConteudo() {
    if (variaveisPendentes.length > 0) return;
    if (arquivos.length > 0) {
      const fila = arquivos;
      const legenda = texto.trim() || undefined;
      const respostaAlvo = alvoDeResposta();
      const falhas: FalhaDeEnvioMidia[] = [];
      let primeiraMensagemNotificada = false;
      for (let indice = 0; indice < fila.length; indice++) {
        setIndiceEnvio(indice);
        setProgresso(0);
        const arquivo = fila[indice];
        const legendaDoArquivo = indice === 0 ? legenda : undefined;
        const respostaDoArquivo = indice === 0 ? respostaAlvo : undefined;
        const citacaoDoArquivo = indice === 0 ? citacaoResposta : undefined;
        try {
          await enviarMidia.mutateAsync({
            atendimentoId: conversa.atendimentoId,
            leadId: conversa.leadId,
            arquivo,
            legenda: legendaDoArquivo,
            onProgresso: setProgresso,
            resposta: respostaDoArquivo,
            citacao: citacaoDoArquivo,
          });
          if (!primeiraMensagemNotificada) {
            onMensagemEnviada?.();
            primeiraMensagemNotificada = true;
          }
          if (indice === 0) {
            setTexto("");
            onCancelarResposta?.();
          }
        } catch (erro) {
          falhas.push({
            id: idDaFalhaDeMidia(),
            atendimentoId: conversa.atendimentoId,
            leadId: conversa.leadId,
            arquivo,
            legenda: legendaDoArquivo,
            resposta: respostaDoArquivo,
            citacao: citacaoDoArquivo,
            motivo: motivoDaFalhaDeMidia(erro, textos.anexoErro),
          });
        }
      }
      setProgresso(null);
      setIndiceEnvio(null);
      if (falhas.length > 0) {
        setArquivos(falhas.map(({ arquivo }) => arquivo));
        setFalhasArquivos(new Map(falhas.map(({ arquivo, motivo }) => [arquivo, motivo])));
        onFalhasDeMidia?.(falhas);
      } else {
        limparAposEnvio();
      }
      return;
    }
    const conteudo = texto.trim();
    if (!conteudo) {
      return;
    }
    if (!resposta) {
      setTexto("");
    }
    enviar.mutate(
      {
        atendimentoId: conversa.atendimentoId,
        leadId: conversa.leadId,
        conteudo,
        resposta: alvoDeResposta(),
        citacao: citacaoResposta,
      },
      { onSuccess: limparAposEnvio },
    );
  }

  function aoSelecionarArquivo(evento: ChangeEvent<HTMLInputElement>) {
    adicionarArquivos(Array.from(evento.target.files ?? []));
    evento.target.value = "";
  }

  function aoColar(evento: ClipboardEvent<HTMLTextAreaElement>) {
    const arquivosColados = arquivosDaAreaDeTransferencia(evento.clipboardData);
    if (arquivosColados.length === 0) return;
    evento.preventDefault();
    adicionarArquivos(arquivosColados);
  }

  function removerArquivo(indice: number) {
    const removido = arquivos[indice];
    setArquivos((atual) => atual.filter((_, item) => item !== indice));
    if (removido) {
      setFalhasArquivos((falhas) => {
        if (!falhas.has(removido)) return falhas;
        const nova = new Map(falhas);
        nova.delete(removido);
        return nova;
      });
    }
    setProgresso(null);
    setIndiceEnvio(null);
  }

  function enviarGravacao() {
    if (!gravador.arquivo || gravador.erro) return;
    setProgresso(0);
    enviarMidia.mutate(
      {
        atendimentoId: conversa.atendimentoId,
        leadId: conversa.leadId,
        arquivo: gravador.arquivo,
        onProgresso: setProgresso,
        gravacaoDoComposer: true,
      },
      {
        onSuccess: () => {
          onMensagemEnviada?.();
          gravador.descartar();
          setProgresso(null);
        },
        onError: () => {
          gravador.descartar();
          setProgresso(null);
        },
      },
    );
  }

  function aoPressionarTecla(evento: KeyboardEvent<HTMLTextAreaElement>) {
    if (
      sugestoes.length > 0 &&
      (evento.key === "ArrowDown" || evento.key === "ArrowUp")
    ) {
      evento.preventDefault();
      setAtalhoSelecionado((atual) =>
        evento.key === "ArrowDown"
          ? (atual + 1) % sugestoes.length
          : (atual - 1 + sugestoes.length) % sugestoes.length,
      );
      return;
    }
    if (
      sugestoes.length > 0 &&
      (evento.key === "Enter" || evento.key === "Tab")
    ) {
      evento.preventDefault();
      const escolhida = sugestoes[atalhoSelecionado];
      if (escolhida) {
        const resolvida = resolverMensagemRapida(escolhida.conteudo, { nome: lead.data?.nome ?? "", empresa: lead.data?.empresa });
        setTexto(resolvida.texto);
        setVariaveisPendentes(resolvida.pendentes);
      }
      setAtalhoSelecionado(0);
      return;
    }
    if (evento.key === "Escape" && resposta) {
      evento.preventDefault();
      onCancelarResposta?.();
      return;
    }
    if (evento.key === "Enter" && !evento.shiftKey) {
      evento.preventDefault();
      enviarConteudo();
    }
  }

  const erroDeTexto =
    enviar.error instanceof ErroDeApi
      ? resposta
        ? (enviar.error.problema?.detail ?? textos.respostaErro)
        : enviar.error.message
      : enviar.isError
        ? textosAtendimentos.mensagem.status.falhou
        : null;
  const erroDeMidia = falhasArquivos.size > 0
    ? Array.from(falhasArquivos.entries())
        .map(([arquivo, motivo]) => `${arquivo.name}: ${motivo}`)
        .join(" · ")
    : enviarMidia.error instanceof ErroDeApi
      ? enviarMidia.error.message
      : enviarMidia.isError
        ? textos.anexoErro
        : null;
  const erroDeGravacao =
    gravador.erro === "SEM_MICROFONE"
      ? textos.audioSemMicrofone
      : gravador.erro === "PERMISSAO"
        ? textos.audioPermissaoNegada
        : gravador.erro === "EM_USO"
          ? textos.audioMicrofoneEmUso
          : gravador.erro === "CAPTURA"
            ? textos.audioErroCaptura
            : gravador.erro === "TAMANHO"
              ? textos.audioExcedeuLimite
              : null;
  const mensagemDeErro =
    erroDeTexto ?? erroDeMidia ?? erroDeGravacao ?? (avisoTipo ? textos.anexoTipoNaoPermitido : null);
  const termoAtalho =
    texto.startsWith("/") && !texto.includes(" ")
      ? texto.slice(1).toLowerCase()
      : null;
  const sugestoes =
    termoAtalho === null
      ? []
      : (rapidas.data ?? []).filter((m) =>
          m.palavraChave.toLowerCase().includes(termoAtalho),
        );

  function enviarTemplateEscolhido(
    template: { nome: string; idioma: string; corpo: string },
    valores: string[],
  ) {
    enviar.mutate({
      atendimentoId: conversa.atendimentoId,
      leadId: conversa.leadId,
      conteudo: template.corpo,
      template: {
        nome: template.nome,
        idioma: template.idioma,
        parametros: valores,
      },
    });
    setPainelTemplateAberto(false);
  }

  const modalDeTemplates = (
    <ModalDeTemplates
      aberto={painelTemplateAberto}
      onAbertoChange={setPainelTemplateAberto}
      textos={textos}
      rotulosDeCategoria={catalogo.templatesWhatsApp.categorias}
      rotulosDeStatus={catalogo.templatesWhatsApp.status}
      templates={templates}
      parametros={parametros}
      onParametros={(chave, valores) =>
        setParametros((atual) => ({ ...atual, [chave]: valores }))
      }
      enviando={enviar.isPending}
      onEnviar={enviarTemplateEscolhido}
    />
  );

  if (!janelaAberta) {
    const semJanela = estadoDaJanela === "inexistente";
    return (
      <div className="min-h-0 overflow-y-auto bg-background px-4 pb-4 pt-3">
        <div className="mx-auto max-w-[780px] rounded-xl border border-border bg-card p-4 shadow-md">
          <div className="flex items-start gap-3">
            <Clock
              className="mt-0.5 size-(--tamanho-icone-interface) shrink-0 text-muted-foreground"
              aria-hidden
            />
            <div className="min-w-0 flex-1 space-y-1">
              <p className="text-sm font-medium text-foreground">
                {semJanela ? textos.janelaInexistenteTitulo : textos.janelaFechadaTitulo}
              </p>
              <p className="text-sm text-muted-foreground">
                {semJanela ? textos.janelaInexistenteDescricao : textos.janelaFechadaDescricao}
              </p>
            </div>
          </div>
          <Button type="button" className="mt-4" onClick={() => setPainelTemplateAberto(true)}>
            {textos.novaMensagem}
          </Button>
        </div>
        {modalDeTemplates}
      </div>
    );
  }

  return (
    <div
      ref={composerContainerRef}
      data-slot="composer"
      className="pointer-events-none absolute inset-x-0 bottom-0 z-20 shrink-0 px-4 pb-4 pt-3"
    >
      <div className="relative mx-auto w-full max-w-[874px] [--tamanho-icone-composer:var(--tamanho-icone-interface)]">
        <div className="pointer-events-auto rounded-xl border border-input bg-card p-3 shadow-md [--tamanho-icone-interface:calc(var(--tamanho-icone-composer)*1.1)]">
        <p
          className="mb-1.5 flex items-center gap-1.5 px-1 text-xs text-muted-foreground"
        >
          <span className="size-1.5 shrink-0 rounded-full bg-muted-foreground/50" aria-hidden />
          {textos.janelaAberta}
        </p>
        {citacaoResposta && (
          <div className="mb-2 flex items-start gap-2 rounded-md border border-border bg-muted/50 px-2 py-1.5">
            <div className="min-w-0 flex-1 text-muted-foreground">
              <CitacaoMensagemVisual citacao={citacaoResposta} textos={textosAtendimentos.mensagem.citacao} />
            </div>
            <button
              type="button"
              className="shrink-0 rounded p-0.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-destructive"
              aria-label={textosAtendimentos.mensagem.citacao.cancelar}
              onClick={() => onCancelarResposta?.()}
            >
              <X className="size-[calc(var(--tamanho-icone-interface)*0.875)]" />
            </button>
          </div>
        )}

        {arquivos.length > 0 && (
          <div className="mb-2 space-y-1">
            {indiceEnvio !== null && arquivos.length > 1 && (
              <p className="text-xs text-muted-foreground" role="status">
                {textos.anexoEnviandoLote
                  .replace("{atual}", String(indiceEnvio + 1))
                  .replace("{total}", String(arquivos.length))}
              </p>
            )}
            {arquivos.map((item, indice) => (
              <div
                key={`${item.name}-${item.size}-${indice}`}
                className="flex items-center gap-2 rounded-md border border-border bg-muted/50 px-2 py-1 text-sm"
              >
                <Paperclip
                  className="size-(--tamanho-icone-interface) shrink-0 text-muted-foreground"
                  aria-hidden
                />
                <span className="flex-1 truncate">{item.name}</span>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {tamanhoLegivel(item.size)}
                </span>
                {falhasArquivos.get(item) && (
                  <span className="max-w-56 truncate text-xs text-destructive" title={falhasArquivos.get(item)}>
                    {falhasArquivos.get(item)}
                  </span>
                )}
                {enviarMidia.isPending && indiceEnvio === indice && progresso !== null ? (
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {progresso}%
                  </span>
                ) : (
                  <button
                    type="button"
                    className="shrink-0 rounded p-0.5 hover:bg-destructive/10 hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-destructive"
                    aria-label={textos.anexoRemover}
                    disabled={enviarMidia.isPending}
                    onClick={() => removerArquivo(indice)}
                  >
                    <X className="size-[calc(var(--tamanho-icone-interface)*0.875)]" />
                  </button>
                )}
              </div>
            ))}
          </div>
        )}

        {gravador.fase === "GRAVANDO" && (
          <div className="mb-2 flex items-center gap-2 rounded-md border border-border bg-muted/50 p-2">
            <Mic
              className="size-(--tamanho-icone-interface) animate-pulse text-destructive"
              aria-hidden
            />
            <span className="flex-1 text-sm">
              {textos.audioGravando} · {duracaoLegivel(gravador.segundos)}
            </span>
            <Button
              type="button"
              size="icon-sm"
              variant="ghost"
              className="hover:bg-destructive/10 hover:text-destructive focus-visible:ring-destructive"
              onClick={gravador.descartar}
              aria-label={textos.audioDescartar}
            >
              <Trash2 className="size-(--tamanho-icone-interface)" aria-hidden />
            </Button>
            <Button
              type="button"
              size="icon-sm"
              onClick={gravador.parar}
              aria-label={textos.audioParar}
            >
              <Square className="size-[calc(var(--tamanho-icone-interface)*0.875)] fill-current" aria-hidden />
            </Button>
          </div>
        )}

        {gravador.fase === "PREVISUALIZACAO" && gravador.previewUrl && (
          <div className="mb-2 flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/50 p-2">
            <audio
              className="h-9 min-w-0 flex-1"
              controls
              src={gravador.previewUrl}
              aria-label={textos.audioPreview}
            />
            {progresso !== null && (
              <span className="text-xs text-muted-foreground">
                {progresso}%
              </span>
            )}
            <Button
              type="button"
              size="icon-sm"
              variant="ghost"
              className="hover:bg-destructive/10 hover:text-destructive focus-visible:ring-destructive"
              onClick={gravador.descartar}
              disabled={enviarMidia.isPending}
              aria-label={textos.audioDescartar}
            >
              <Trash2 className="size-(--tamanho-icone-interface)" aria-hidden />
            </Button>
          </div>
        )}

        {gravador.limiteAtingido && gravador.fase === "PREVISUALIZACAO" && (
          <p className="mb-2 text-xs text-muted-foreground" role="status">
            {textos.audioLimiteDuracao}
          </p>
        )}

        <div className="flex items-end gap-2">
          <div className="flex shrink-0 items-center gap-1">
            <input
              ref={inputArquivoRef}
              type="file"
              accept={TIPOS_DE_ANEXO_ACEITOS}
              multiple
              className="hidden"
              onChange={aoSelecionarArquivo}
              disabled={gravador.fase !== "INATIVO" || enviarMidia.isPending}
            />
            <Popover open={menuAnexoAberto} onOpenChange={aoAlterarMenuAnexo}>
              <Tooltip>
                <TooltipTrigger
                  render={
                    <PopoverTrigger
                      className={buttonVariants({ variant: "ghost", size: "icon-lg" })}
                      aria-label={textos.anexo}
                      disabled={gravador.fase !== "INATIVO"}
                    >
                      <Paperclip className="size-[calc(var(--tamanho-icone-interface)*1.25)]" aria-hidden />
                    </PopoverTrigger>
                  }
                />
                <TooltipContent>{textos.anexo}</TooltipContent>
              </Tooltip>
              <PopoverContent
                side="top"
                align="start"
                className={modoMenuAnexo === "acoes" ? "grid w-auto grid-cols-3 gap-2 p-3" : "max-h-60 w-72 overflow-y-auto p-1"}
                onKeyDown={navegarPelasAcoesDoMenu}
              >
                {modoMenuAnexo === "acoes" ? (
                  <div role="menu" aria-label={textos.anexo} className="contents">
                    <AcaoMenuAnexo
                      label={textos.anexoMenuArquivos}
                      icone={File}
                      aoSelecionar={() => {
                        fecharMenuAnexo();
                        requestAnimationFrame(() => inputArquivoRef.current?.click());
                      }}
                      disabled={gravador.fase !== "INATIVO"}
                    />
                    {capacidadeDoCanal.data?.gerenciaTemplates !== false && (
                      <AcaoMenuAnexo
                        label={textos.anexoMenuTemplates}
                        icone={LayoutTemplate}
                        aoSelecionar={abrirTemplatesPeloMenu}
                        disabled={gravador.fase !== "INATIVO"}
                      />
                    )}
                    {!rapidas.isError && (
                      <AcaoMenuAnexo
                        label={textos.mensagensRapidas}
                        icone={Zap}
                        aoSelecionar={abrirMensagensRapidasPeloMenu}
                        disabled={gravador.fase !== "INATIVO"}
                      />
                    )}
                  </div>
                ) : (
                  <ul role="listbox" aria-label={textos.mensagensRapidas}>
                    {rapidas.data?.map((mensagem) => (
                      <li key={mensagem.id}>
                        <button
                          type="button"
                          className="w-full rounded-md p-2 text-left outline-none hover:bg-accent focus-visible:bg-accent"
                          onClick={() => selecionarMensagemRapida(mensagem)}
                        >
                          <span className="block truncate font-mono text-xs text-primary">
                            /{mensagem.palavraChave}
                          </span>
                          <span className="block truncate text-sm">
                            {mensagem.conteudo}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </PopoverContent>
            </Popover>

            <Tooltip>
              <TooltipTrigger
                className={buttonVariants({ variant: "ghost", size: "icon" })}
                aria-label={textos.agendar}
                onClick={() => setAgendamentoAberto(true)}
                disabled={gravador.fase !== "INATIVO"}
              >
                <Clock className="size-(--tamanho-icone-interface)" />
              </TooltipTrigger>
              <TooltipContent>{textos.agendar}</TooltipContent>
            </Tooltip>

            <PainelEmojiComposer
              rotulo={textos.emoji}
              i18n={textosAtendimentos.mensagem.acoes.seletor}
              disabled={gravador.fase !== "INATIVO"}
              onEscolher={(emoji) => {
                const campo = textareaRef.current;
                setTexto((atual) => {
                  const { texto, cursor } = inserirNoCursor(atual, emoji, campo);
                  requestAnimationFrame(() => posicionarCursor(textareaRef.current, cursor));
                  return texto;
                });
                setVariaveisPendentes([]);
              }}
            />
          </div>

          {gravador.disponivel && gravador.fase === "INATIVO" && arquivos.length === 0 && (
            <div className="order-last shrink-0">
              <Tooltip>
                <TooltipTrigger
                  className={buttonVariants({ variant: "ghost", size: "icon" })}
                  aria-label={textos.audioGravar}
                  onClick={gravador.iniciar}
                  disabled={enviarMidia.isPending}
                >
                  <Mic className="size-(--tamanho-icone-interface)" />
                </TooltipTrigger>
                <TooltipContent>{textos.audioGravar}</TooltipContent>
              </Tooltip>
            </div>
          )}

          <div className="relative min-w-0 flex-1">
            <Textarea
              ref={textareaRef}
              value={texto}
              onChange={(evento) => {
                setTexto(evento.target.value);
                setVariaveisPendentes([]);
                setAtalhoSelecionado(0);
              }}
              onPaste={aoColar}
              onKeyDown={aoPressionarTecla}
              placeholder={
                arquivos.length > 0 ? textos.anexoLegendaPlaceholder : textos.placeholder
              }
              rows={1}
              className="min-h-11 max-h-32 w-full min-w-0 resize-none break-words border-0 bg-transparent px-2 py-2 shadow-none focus-visible:border-0 focus-visible:ring-2"
              disabled={gravador.fase !== "INATIVO"}
            />
            {sugestoes.length > 0 && (
              <ul
                role="listbox"
                aria-label={textos.mensagensRapidas}
                className="absolute bottom-full z-40 mb-2 max-h-48 w-full overflow-y-auto rounded-lg border border-border bg-popover p-1 shadow-lg"
              >
                {sugestoes.map((m, indice) => (
                  <li key={m.id}>
                    <button
                      type="button"
                      className={
                        indice === atalhoSelecionado
                          ? "w-full rounded bg-accent p-2 text-left"
                          : "w-full rounded p-2 text-left hover:bg-accent"
                      }
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => {
                        const resolvida = resolverMensagemRapida(m.conteudo, { nome: lead.data?.nome ?? "", empresa: lead.data?.empresa });
                        setTexto(resolvida.texto);
                        setVariaveisPendentes(resolvida.pendentes);
                        setAtalhoSelecionado(0);
                      }}
                    >
                      <span className="font-mono text-xs text-primary">
                        /{m.palavraChave}
                      </span>
                      <span className="block truncate text-sm">
                        {m.conteudo}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="order-last flex shrink-0 items-center gap-1">
            <Button
              type="button"
              size="icon"
              onClick={() => {
                if (gravador.fase === "PREVISUALIZACAO") enviarGravacao();
                else enviarConteudo();
              }}
              disabled={
                enviar.isPending
                || enviarMidia.isPending
                || (gravador.fase === "PREVISUALIZACAO"
                  ? Boolean(gravador.erro) || !gravador.arquivo
                  : gravador.fase !== "INATIVO" || (!texto.trim() && arquivos.length === 0) || variaveisPendentes.length > 0)
              }
              aria-label={textos.enviar}
            >
              <Send className="size-(--tamanho-icone-interface)" />
            </Button>
          </div>
        </div>

        {mensagemDeErro && (
          <p className="mt-1 text-xs text-destructive" role="alert">
            {mensagemDeErro}
          </p>
        )}
        {variaveisPendentes.length > 0 && (
          <p className="mt-1 text-xs text-destructive" role="alert">
            {catalogo.mensagensRapidas.variaveisPendentes.replace("{variaveis}", variaveisPendentes.map((item) => `{${item}}`).join(", "))}
          </p>
        )}
        {modalDeTemplates}
        <FormularioMensagemProgramada
          key={agendamentoAberto ? "agendamento-aberto" : "agendamento-fechado"}
          aberto={agendamentoAberto}
          leadId={conversa.leadId}
          leadNome={conversa.leadNome}
          conteudoInicial={texto}
          onFechar={() => setAgendamentoAberto(false)}
          onSalvo={() => setTexto("")}
        />
        </div>
      </div>
    </div>
  );
}
