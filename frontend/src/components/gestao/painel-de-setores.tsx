"use client";

import { useState } from "react";
import { Check, Pencil, Plus, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Seletor } from "@/components/ui/seletor";
import { Switch } from "@/components/ui/switch";
import { ErroDeApi } from "@/lib/api/errors";
import type { SetoresTextos } from "@/lib/config/schema-setores";
import {
  useAtualizarSetor,
  useAtribuirSetor,
  useCriarSetor,
  type Setor,
} from "@/lib/gestao/setores";

import { preencher } from "./apoio";

function mensagemDeErro(textos: SetoresTextos, erro: unknown): string | null {
  if (!erro) return null;
  if (erro instanceof ErroDeApi && erro.status === 409) return textos.nomeEmUso;
  if (erro instanceof ErroDeApi && erro.status === 422) return erro.message || textos.erroSalvar;
  return textos.erroSalvar;
}

/**
 * Setores da instância (D2 da Disbrel): lista, cria e edita. Setor é só agrupamento da equipe — não muda
 * papel, permissão nem o que cada pessoa vê de lead; o texto da tela diz isso. Criar e editar é de gestor e
 * administrador; quem só pode atribuir (subgestor delegado) vê a lista sem os controles de edição.
 */
export function PainelDeSetores({
  textos,
  setores,
  podeEditar,
}: {
  textos: SetoresTextos;
  setores: Setor[];
  podeEditar: boolean;
}) {
  const criar = useCriarSetor();
  const [nome, setNome] = useState("");
  const nomeValido = nome.trim().length > 0;

  return (
    <section className="space-y-3 rounded-2xl border border-border bg-card p-5" aria-label={textos.titulo}>
      <div>
        <h2 className="text-sm font-bold text-foreground">{textos.titulo}</h2>
        <p className="text-xs text-muted-foreground">{textos.descricao}</p>
      </div>

      {setores.length === 0 && <p className="text-sm text-muted-foreground">{textos.vazio}</p>}
      <ul className="divide-y divide-border">
        {setores.map((setor) => (
          <LinhaDeSetor key={setor.id} textos={textos} setor={setor} podeEditar={podeEditar} />
        ))}
      </ul>

      {podeEditar ? (
        <form
          className="flex flex-wrap items-start gap-2"
          onSubmit={(evento) => {
            evento.preventDefault();
            if (!nomeValido) return;
            criar.mutate(nome.trim(), { onSuccess: () => setNome("") });
          }}
        >
          <Input
            value={nome}
            maxLength={80}
            aria-label={textos.nome}
            placeholder={textos.nome}
            className="max-w-64"
            onChange={(evento) => setNome(evento.target.value)}
          />
          <Button type="submit" disabled={!nomeValido || criar.isPending}>
            <Plus className="size-(--tamanho-icone-interface)" aria-hidden />
            {textos.criar}
          </Button>
          {criar.isError && (
            <p role="alert" className="basis-full text-xs text-destructive">
              {mensagemDeErro(textos, criar.error)}
            </p>
          )}
        </form>
      ) : (
        <p className="text-xs text-muted-foreground">{textos.somenteGestao}</p>
      )}
    </section>
  );
}

function LinhaDeSetor({ textos, setor, podeEditar }: { textos: SetoresTextos; setor: Setor; podeEditar: boolean }) {
  const atualizar = useAtualizarSetor();
  const [editando, setEditando] = useState(false);
  const [nome, setNome] = useState(setor.nome);

  function salvar(alteracao: Partial<{ nome: string; recebeDaTriagem: boolean; ativo: boolean }>, aoSalvar?: () => void) {
    atualizar.mutate(
      {
        id: setor.id,
        dados: {
          nome: setor.nome,
          recebeDaTriagem: setor.recebeDaTriagem,
          ativo: setor.ativo,
          ...alteracao,
        },
      },
      { onSuccess: aoSalvar },
    );
  }

  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 py-2.5">
      <div className="min-w-0 flex-1">
        {editando ? (
          <form
            className="flex items-center gap-2"
            onSubmit={(evento) => {
              evento.preventDefault();
              if (nome.trim()) salvar({ nome: nome.trim() }, () => setEditando(false));
            }}
          >
            <Input
              value={nome}
              maxLength={80}
              aria-label={preencher(textos.renomear, { nome: setor.nome })}
              className="max-w-64"
              onChange={(evento) => setNome(evento.target.value)}
            />
            <Button type="submit" size="icon" variant="outline" aria-label={textos.salvar} disabled={atualizar.isPending}>
              <Check className="size-4" aria-hidden />
            </Button>
            <Button
              type="button"
              size="icon"
              variant="outline"
              aria-label={textos.cancelar}
              onClick={() => {
                setNome(setor.nome);
                setEditando(false);
              }}
            >
              <X className="size-4" aria-hidden />
            </Button>
          </form>
        ) : (
          <p className="flex items-center gap-2 text-sm font-medium text-foreground">
            <span className="truncate">{setor.nome}</span>
            {!setor.ativo && <span className="text-xs text-muted-foreground">({textos.inativo})</span>}
            {podeEditar && (
              <Button
                type="button"
                size="icon"
                variant="ghost"
                className="size-7"
                aria-label={preencher(textos.renomear, { nome: setor.nome })}
                onClick={() => setEditando(true)}
              >
                <Pencil className="size-3.5" aria-hidden />
              </Button>
            )}
          </p>
        )}
        <p className="text-xs text-muted-foreground">
          {preencher(textos.integrantes, { n: setor.quantidadeDeUsuarios })}
        </p>
      </div>

      <span className="inline-flex items-center gap-2 text-xs text-muted-foreground">
        <Switch
          checked={setor.recebeDaTriagem}
          disabled={!podeEditar || atualizar.isPending}
          aria-label={preencher(textos.alternarTriagem, { nome: setor.nome })}
          onCheckedChange={(valor) => salvar({ recebeDaTriagem: valor })}
        />
        {setor.recebeDaTriagem ? textos.recebeDaTriagem : textos.naoRecebeDaTriagem}
      </span>
      <span className="inline-flex items-center gap-2 text-xs text-muted-foreground">
        <Switch
          checked={setor.ativo}
          disabled={!podeEditar || atualizar.isPending}
          aria-label={preencher(textos.alternarAtivo, { nome: setor.nome })}
          onCheckedChange={(valor) => salvar({ ativo: valor })}
        />
        {setor.ativo ? textos.ativo : textos.inativo}
      </span>
      {atualizar.isError && (
        <p role="alert" className="basis-full text-xs text-destructive">
          {mensagemDeErro(textos, atualizar.error)}
        </p>
      )}
    </li>
  );
}

/** Setor de um integrante, em uma célula da tabela da equipe. Somente leitura quando não pode atribuir. */
export function SetorDoIntegrante({
  textos,
  setores,
  usuarioId,
  nome,
  setorId,
  podeAtribuir,
}: {
  textos: SetoresTextos;
  setores: Setor[];
  usuarioId: string;
  nome: string;
  setorId: string | null;
  podeAtribuir: boolean;
}) {
  const atribuir = useAtribuirSetor();
  const atual = setores.find((setor) => setor.id === setorId);

  if (!podeAtribuir) {
    return <span className="text-xs text-muted-foreground">{atual?.nome ?? textos.semSetor}</span>;
  }
  return (
    <div>
      <Seletor
        valor={setorId ?? ""}
        placeholder={textos.semSetor}
        ariaLabel={preencher(textos.setorDe, { nome })}
        desabilitado={atribuir.isPending}
        className="h-8 min-w-36"
        opcoes={setores
          .filter((setor) => setor.ativo || setor.id === setorId)
          .map((setor) => ({ valor: setor.id, rotulo: setor.nome, desabilitada: !setor.ativo }))}
        onChange={(valor) => atribuir.mutate({ usuarioId, setorId: valor || null })}
      />
      {atribuir.isError && (
        <p role="alert" className="mt-1 text-[11px] text-destructive">
          {mensagemDeErro(textos, atribuir.error)}
        </p>
      )}
    </div>
  );
}
