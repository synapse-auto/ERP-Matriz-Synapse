/*
 * Textos da tela de setores (D2 da Disbrel). Escrito à mão (diferente de schema-gestao.ts, que é
 * gerado): todos os campos têm default porque frontend e backend têm rollout independente, e um
 * backend anterior serve textos.json sem esta seção.
 */
import { z } from "zod";

export const SetoresTextosSchema = z
  .object({
    titulo: z.string().default("Setores"),
    descricao: z.string().default("Agrupe a equipe por setor. O setor não muda o que cada pessoa vê nem o que pode fazer."),
    vazio: z.string().default("Nenhum setor cadastrado ainda."),
    novo: z.string().default("Novo setor"),
    nome: z.string().default("Nome do setor"),
    criar: z.string().default("Criar setor"),
    salvar: z.string().default("Salvar"),
    cancelar: z.string().default("Cancelar"),
    renomear: z.string().default("Renomear {nome}"),
    integrantes: z.string().default("{n} integrante(s)"),
    recebeDaTriagem: z.string().default("Recebe da triagem"),
    naoRecebeDaTriagem: z.string().default("Não recebe da triagem"),
    ativo: z.string().default("Ativo"),
    inativo: z.string().default("Inativo"),
    alternarTriagem: z.string().default("Receber da triagem: {nome}"),
    alternarAtivo: z.string().default("Setor ativo: {nome}"),
    colunaSetor: z.string().default("Setor"),
    semSetor: z.string().default("Sem setor"),
    setorDe: z.string().default("Setor de {nome}"),
    somenteGestao: z.string().default("Só gestor e administrador criam e editam setores."),
    erroCarregar: z.string().default("Não foi possível carregar os setores."),
    erroSalvar: z.string().default("Não foi possível salvar. Tente de novo."),
    nomeEmUso: z.string().default("Já existe um setor com este nome."),
    setorInativo: z.string().default("Este setor está inativo e não aceita novos integrantes."),
  })
  .default({});

export type SetoresTextos = z.infer<typeof SetoresTextosSchema>;
