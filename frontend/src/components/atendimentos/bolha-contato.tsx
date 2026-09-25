"use client";

import { useState } from "react";
import { Check, Copy, Loader2, MessageCircle, Phone, UserPlus, UserRound } from "lucide-react";

import { ErroDeApi } from "@/lib/api/errors";
import {
  telefoneParaNovoContato,
  useAberturaDeConversaDoContato,
} from "@/lib/atendimento/abrir-conversa-do-contato";
import { buscarAtendimentoPorTelefone } from "@/lib/atendimento/api";
import {
  contatosDaMensagem,
  numeroDiscavel,
  type ContatoCompartilhado,
  type TelefoneDoContato,
} from "@/lib/atendimento/contato-compartilhado";
import { copiarTexto } from "@/lib/mensagens/copiar-texto";
import { cn } from "@/lib/utils";

/**
 * Textos da bolha. As chaves específicas de contato são opcionais para não reprovar o catálogo de
 * um filho publicado antes delas; sem elas, a bolha mostra só o dado e usa as ações genéricas. Sem
 * `abrirConversa`, a ação de abrir conversa não aparece.
 */
export interface TextosDaBolhaContato {
  contato?: string;
  contatoSemNome?: string;
  contatoSemTelefone?: string;
  copiarTelefone?: string;
  ligarPara?: string;
  abrirConversa?: string;
  abrirConversaCom?: string;
  procurandoConversa?: string;
  conversaNaoEncontrada?: string;
  iniciarNovoContato?: string;
  iniciarNovoContatoCom?: string;
  telefoneInvalidoParaConversa?: string;
  erroAbrirConversa?: string;
  tentarAbrirConversaDeNovo?: string;
  copiar: string;
  copiada: string;
  copiarErro: string;
}

const ACAO_ICONE =
  "inline-flex size-8 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors "
  + "hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 "
  + "focus-visible:ring-ring";

const ACAO_TEXTO =
  "inline-flex min-h-8 items-center justify-center gap-1.5 rounded-md border border-border/70 "
  + "bg-background/70 px-2.5 text-xs font-semibold text-foreground transition-colors hover:bg-muted "
  + "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring "
  + "disabled:cursor-default disabled:opacity-60";

export function BolhaContato({
  midiaMetadados,
  textos,
}: {
  midiaMetadados: string | null;
  textos: TextosDaBolhaContato;
}) {
  const contatos = contatosDaMensagem(midiaMetadados);

  return (
    <div className="flex w-full min-w-0 max-w-sm flex-col gap-2" aria-label={textos.contato}>
      {contatos.length === 0 && textos.contato && (
        <span className="font-semibold">{textos.contato}</span>
      )}
      {contatos.map((contato, indice) => (
        <CartaoDoContato key={indice} contato={contato} textos={textos} />
      ))}
    </div>
  );
}

function CartaoDoContato({
  contato,
  textos,
}: {
  contato: ContatoCompartilhado;
  textos: TextosDaBolhaContato;
}) {
  const nome = contato.nome ?? textos.contatoSemNome ?? contato.telefones[0]?.numero;
  const iniciais = iniciaisDe(contato.nome);

  return (
    <section
      className="min-w-0 overflow-hidden rounded-xl border border-border/60 bg-background/60"
      data-slot="cartao-contato"
    >
      <div className="flex items-center gap-3 px-3 pt-3 pb-2.5">
        <div
          className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary"
          aria-hidden
        >
          {iniciais ?? <UserRound className="size-5" />}
        </div>
        <div className="min-w-0 flex-1">
          <p className="line-clamp-2 break-words font-semibold leading-snug text-foreground" title={nome}>
            {nome}
          </p>
          {textos.contato && (
            <p className="text-xs text-muted-foreground">{textos.contato}</p>
          )}
        </div>
      </div>
      {contato.telefones.length === 0 && textos.contatoSemTelefone && (
        <p className="border-t border-border/60 px-3 py-2 text-xs text-muted-foreground">
          {textos.contatoSemTelefone}
        </p>
      )}
      {contato.telefones.length > 0 && (
        <ul className="divide-y divide-border/60 border-t border-border/60">
          {contato.telefones.map((telefone, posicao) => (
            <LinhaDeTelefone
              key={`${telefone.numero}-${posicao}`}
              telefone={telefone}
              nomeDoContato={contato.nome ?? ""}
              textos={textos}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

type EstadoDaAbertura = "ocioso" | "procurando" | "semConversa" | "invalido" | "erro";

function LinhaDeTelefone({
  telefone,
  nomeDoContato,
  textos,
}: {
  telefone: TelefoneDoContato;
  nomeDoContato: string;
  textos: TextosDaBolhaContato;
}) {
  const abertura = useAberturaDeConversaDoContato();
  const [copia, setCopia] = useState<"copiado" | "erro" | null>(null);
  const [estado, setEstado] = useState<EstadoDaAbertura>("ocioso");
  const discavel = numeroDiscavel(telefone.numero);
  const rotuloLigar = textos.ligarPara?.replaceAll("{numero}", telefone.numero) ?? telefone.numero;
  const podeAbrir = Boolean(abertura && discavel && textos.abrirConversa);

  async function copiar() {
    setCopia((await copiarTexto(telefone.numero)) ? "copiado" : "erro");
  }

  async function abrirConversa() {
    if (!abertura || estado === "procurando") return;
    setEstado("procurando");
    try {
      // waId é o identificador que o WhatsApp entrega; o backend normaliza qualquer um dos dois.
      const cartao = await buscarAtendimentoPorTelefone(telefone.waId ?? telefone.numero);
      if (cartao) {
        setEstado("ocioso");
        abertura.abrirCartao(cartao);
      } else {
        setEstado("semConversa");
      }
    } catch (erro) {
      setEstado(erro instanceof ErroDeApi && erro.status === 400 ? "invalido" : "erro");
    }
  }

  function iniciarNovoContato() {
    abertura?.iniciarNovoContato({
      nome: nomeDoContato,
      telefone: telefoneParaNovoContato(telefone.numero),
    });
  }

  const mensagemDoEstado =
    estado === "procurando" ? textos.procurandoConversa
      : estado === "semConversa" ? textos.conversaNaoEncontrada
        : estado === "invalido" ? textos.telefoneInvalidoParaConversa
          : estado === "erro" ? textos.erroAbrirConversa
            : undefined;

  return (
    <li className="flex flex-col gap-2 px-3 py-2.5">
      <div className="flex min-w-0 items-center gap-2">
        <div className="min-w-0 flex-1">
          <p className="break-all text-sm font-medium tabular-nums text-foreground">{telefone.numero}</p>
          {telefone.tipo && <p className="text-xs text-muted-foreground">{telefone.tipo}</p>}
        </div>
        {discavel && (
          <div className="flex shrink-0 items-center gap-0.5">
            <a href={`tel:${discavel}`} aria-label={rotuloLigar} title={rotuloLigar} className={ACAO_ICONE}>
              <Phone className="size-4" aria-hidden />
            </a>
            <button
              type="button"
              onClick={() => void copiar()}
              aria-label={textos.copiarTelefone ?? textos.copiar}
              title={textos.copiarTelefone ?? textos.copiar}
              className={ACAO_ICONE}
            >
              {copia === "copiado" ? <Check className="size-4" aria-hidden /> : <Copy className="size-4" aria-hidden />}
            </button>
          </div>
        )}
      </div>

      {podeAbrir && (
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => void abrirConversa()}
            disabled={estado === "procurando"}
            aria-busy={estado === "procurando"}
            aria-label={textos.abrirConversaCom?.replaceAll("{numero}", telefone.numero) ?? textos.abrirConversa}
            className={cn(ACAO_TEXTO, "flex-1 sm:flex-none")}
          >
            {estado === "procurando"
              ? <Loader2 className="size-3.5 animate-spin" aria-hidden />
              : <MessageCircle className="size-3.5" aria-hidden />}
            {estado === "erro" && textos.tentarAbrirConversaDeNovo
              ? textos.tentarAbrirConversaDeNovo
              : textos.abrirConversa}
          </button>
          {estado === "semConversa" && textos.iniciarNovoContato && (
            <button
              type="button"
              onClick={iniciarNovoContato}
              aria-label={textos.iniciarNovoContatoCom?.replaceAll("{numero}", telefone.numero) ?? textos.iniciarNovoContato}
              className={cn(ACAO_TEXTO, "flex-1 sm:flex-none")}
            >
              <UserPlus className="size-3.5" aria-hidden />
              {textos.iniciarNovoContato}
            </button>
          )}
        </div>
      )}

      <p
        className={cn(
          "text-xs",
          mensagemDoEstado ? "block" : "sr-only",
          estado === "erro" || estado === "invalido" ? "text-destructive" : "text-muted-foreground",
        )}
        role="status"
        aria-live="polite"
      >
        {mensagemDoEstado ?? (copia === "copiado" ? textos.copiada : copia === "erro" ? textos.copiarErro : "")}
      </p>
    </li>
  );
}

function iniciaisDe(nome: string | undefined): string | null {
  const partes = nome?.trim().split(/\s+/).filter(Boolean) ?? [];
  if (partes.length === 0) return null;
  const primeira = partes[0][0] ?? "";
  const ultima = partes.length > 1 ? (partes[partes.length - 1][0] ?? "") : "";
  const iniciais = `${primeira}${ultima}`.toLocaleUpperCase("pt-BR");
  return /\p{L}/u.test(iniciais) ? iniciais : null;
}
