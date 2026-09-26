/*
 * GERADO a partir da secao "gestao" de textos.json (docs/47). Nao editar a mao: altere
 * textos.json e regenere com scripts/gerar-schema-textos-gestao.py.
 *
 * Todos os campos tem default: frontend e backend tem rollout independente, e um backend
 * anterior a Gestao serve textos.json sem esta secao (mesma regra do resto do TextosSchema).
 */
import { z } from "zod";

const MODULOS_PADRAO: Record<string, { rotulo: string; descricao: string }> = {
  "atendimentos": {
    "rotulo": "Atendimentos",
    "descricao": "Conversas do WhatsApp com leads e clientes"
  },
  "contatos": {
    "rotulo": "Contatos e leads",
    "descricao": "Ficha e dados dos contatos"
  },
  "tags": {
    "rotulo": "Tags",
    "descricao": "Classificação de leads"
  },
  "mensagens_rapidas": {
    "rotulo": "Mensagens rápidas",
    "descricao": "Atalhos de resposta"
  },
  "templates": {
    "rotulo": "Templates de mensagem",
    "descricao": "Modelos aprovados pela Meta para disparo no WhatsApp"
  },
  "resumo_ia": {
    "rotulo": "Resumo por IA",
    "descricao": "Resumo automático da conversa"
  },
  "dashboard": {
    "rotulo": "Dashboard",
    "descricao": "Visão geral consolidada"
  },
  "mensagens_programadas": {
    "rotulo": "Mensagens programadas",
    "descricao": "Envios agendados"
  },
  "lembretes": {
    "rotulo": "Lembretes",
    "descricao": "Lembretes de retorno"
  },
  "automacao": {
    "rotulo": "Automação",
    "descricao": "Parâmetros e regras da automação"
  },
  "equipe": {
    "rotulo": "Gestão da equipe",
    "descricao": "Integrantes, delegações e permissões"
  }
};

const CAPACIDADES_PADRAO: Record<string, string> = {
  "atendimentos.ver": "Ver conversas",
  "atendimentos.responder": "Responder mensagens",
  "atendimentos.iniciar_conversa": "Iniciar nova conversa",
  "atendimentos.abrir_para_contato": "Abrir atendimento para contato existente",
  "atendimentos.transferir": "Transferir para outro atendente",
  "atendimentos.devolver_ia": "Devolver conversa para a IA",
  "atendimentos.finalizar": "Finalizar atendimento",
  "atendimentos.colaborar": "Pedir entrada e convidar colegas para a conversa",
  "atendimentos.finalizar_lote": "Finalizar atendimentos em lote",
  "contatos.editar": "Editar ficha do contato",
  "tags.aplicar": "Aplicar e remover tags nos leads",
  "tags.criar": "Criar tag",
  "tags.editar_excluir": "Editar e excluir tags",
  "mensagens_rapidas.usar": "Usar mensagens rápidas",
  "mensagens_rapidas.criar": "Criar mensagens rápidas",
  "mensagens_rapidas.editar_excluir": "Editar e excluir mensagens rápidas",
  "templates.ver": "Ver templates",
  "templates.criar": "Criar template",
  "templates.editar": "Editar template",
  "templates.excluir": "Excluir template",
  "resumo_ia.ver": "Visualizar resumo",
  "resumo_ia.solicitar": "Gerar resumo",
  "dashboard.ver": "Ver dashboard",
  "mensagens_programadas.ver": "Ver mensagens programadas",
  "mensagens_programadas.criar": "Programar mensagem",
  "mensagens_programadas.editar_cancelar": "Editar e cancelar mensagens programadas",
  "lembretes.ver": "Ver lembretes",
  "lembretes.criar": "Criar lembrete",
  "lembretes.editar_excluir": "Editar e excluir lembretes",
  "automacao.ver": "Ver configurações da automação",
  "automacao.editar_parametros": "Alterar parâmetros da automação",
  "automacao.regras": "Gerenciar regras de follow-up e fidelização",
  "equipe.ver": "Ver equipe e acessar Gestão",
  "equipe.disponibilidade_ia": "Ligar e desligar disponibilidade para a IA",
  "equipe.criar": "Criar usuário",
  "equipe.editar": "Editar nome e e-mail de usuário",
  "equipe.alterar_papel": "Alterar função de usuário",
  "equipe.senha_provisoria": "Gerar senha provisória",
  "equipe.desativar": "Desativar usuário",
  "equipe.excecoes_atendentes": "Ajustar exceções de atendentes (delegação)",
  "equipe.perfis": "Editar perfis de permissão"
};

export const GestaoTextosSchema = z.object({
    "titulo": z.string().default("Gestão"),
    "selo": z.object({
      "gestao": z.string().default("Gestão autorizada"),
      "administrador": z.string().default("Administrador"),
      "delegado": z.string().default("Acesso delegado"),
      "leitura": z.string().default("Somente leitura"),
    }).default({}),
    "descricoes": z.object({
      "equipe": z.string().default("Pessoas, funções e presença da equipe"),
      "permissoes": z.string().default("Defina o que cada perfil pode ver e fazer em cada módulo do CRM"),
      "excecoes": z.string().default("Ajustes individuais por cima do perfil · valem só para a pessoa selecionada"),
    }).default({}),
    "abas": z.object({
      "equipe": z.string().default("Equipe"),
      "permissoes": z.string().default("Permissões"),
      "excecoes": z.string().default("Exceções por usuário"),
      "rotulo": z.string().default("Áreas da Gestão"),
    }).default({}),
    "carregando": z.string().default("Carregando…"),
    "erro": z.string().default("Não foi possível carregar a Gestão."),
    "semAcesso": z.string().default("Você não tem acesso à Gestão."),
    "papeis": z.object({
      "GESTOR": z.string().default("Gestor"),
      "SUBGESTOR": z.string().default("Subgestor"),
      "ATENDENTE": z.string().default("Atendente"),
      "ADMINISTRADOR": z.string().default("Administrador"),
    }).default({}),
    "papeisDescricao": z.object({
      "GESTOR": z.string().default("Acesso total e fixo"),
      "SUBGESTOR": z.string().default("Coordena a equipe e atende"),
      "ATENDENTE": z.string().default("Atende e gerencia seus leads"),
      "ADMINISTRADOR": z.string().default("Acesso técnico e fixo"),
    }).default({}),
    "resumoPerfil": z.object({
      "fixo": z.string().default("Acesso total e fixo"),
      "permissoes": z.string().default("{permitidas} de {total} permissões"),
      "editar": z.string().default("editar perfil"),
      "ver": z.string().default("ver perfil"),
      "usuario": z.string().default("{n} usuário"),
      "usuarios": z.string().default("{n} usuários"),
      "acessoTotal": z.string().default("acesso total"),
    }).default({}),
    "equipe": z.object({
      "novo": z.string().default("Novo usuário"),
      "colunas": z.object({
        "usuario": z.string().default("Usuário"),
        "funcao": z.string().default("Função"),
        "presenca": z.string().default("Presença"),
        "ia": z.string().default("IA"),
        "avaliacao": z.string().default("Avaliação"),
        "permissoes": z.string().default("Permissões"),
        "acoes": z.string().default("Ações"),
      }).default({}),
      "permissoes": z.object({
        "acessoTotal": z.string().default("Acesso total"),
        "padrao": z.string().default("Padrão do perfil"),
        "excecao": z.string().default("1 exceção"),
        "excecoes": z.string().default("{n} exceções"),
      }).default({}),
      "desativado": z.string().default("Desativado"),
      "presenca": z.object({
        "ONLINE": z.string().default("Online"),
        "AUSENTE": z.string().default("Ausente"),
        "OFFLINE": z.string().default("Offline"),
      }).default({}),
      "ia": z.object({
        "disponivel": z.string().default("No rodízio"),
        "indisponivel": z.string().default("Fora do rodízio"),
        "naoAplicavel": z.string().default("Não se aplica"),
        "rotulo": z.string().default("Disponibilidade para a IA de {nome}"),
      }).default({}),
      "acoes": z.object({
        "permissoes": z.string().default("Permissões de {nome}"),
        "editar": z.string().default("Editar {nome}"),
        "senha": z.string().default("Gerar senha provisória para {nome}"),
        "desativar": z.string().default("Desativar {nome}"),
      }).default({}),
      "semAvaliacao": z.string().default("Sem avaliações"),
      "vazio": z.string().default("Nenhum integrante cadastrado."),
      "indicadores": z.string().default("Indicadores da equipe"),
    }).default({}),
    "formulario": z.object({
      "criarTitulo": z.string().default("Novo usuário"),
      "criarDescricao": z.string().default("Dados de acesso e função · permissões herdadas do perfil"),
      "editarTitulo": z.string().default("Editar usuário"),
      "editarDescricao": z.string().default("Nome, e-mail e função"),
      "nome": z.string().default("Nome"),
      "nomePlaceholder": z.string().default("Nome completo"),
      "email": z.string().default("E-mail"),
      "emailPlaceholder": z.string().default("nome@empresa.com.br"),
      "senha": z.string().default("Senha inicial"),
      "senhaAjuda": z.string().default("A pessoa troca a senha no primeiro acesso."),
      "funcao": z.string().default("Função"),
      "funcaoBloqueada": z.string().default("Mudar a função é exclusivo de Gestor e Administrador."),
      "iaAviso": z.string().default("Novos logins entram fora do rodízio da IA. Ative na coluna IA quando a pessoa estiver pronta."),
      "salvar": z.string().default("Salvar usuário"),
      "cancelar": z.string().default("Cancelar"),
      "fechar": z.string().default("Fechar"),
      "erro": z.string().default("Não foi possível salvar o usuário."),
      "emailEmUso": z.string().default("Este e-mail já está em uso."),
    }).default({}),
    "desativacao": z.object({
      "titulo": z.string().default("Desativar usuário"),
      "descricao": z.string().default("Deseja desativar {nome}? O acesso é cortado imediatamente, inclusive em sessões já abertas. Histórico, leads e atendimentos permanecem."),
      "confirmar": z.string().default("Desativar"),
      "cancelar": z.string().default("Cancelar"),
      "erro": z.string().default("Não foi possível desativar o usuário."),
    }).default({}),
    "permissoes": z.object({
      "busca": z.string().default("Buscar permissão (ex.: resumo, templates)"),
      "legendaNivel": z.string().default("nível mínimo do módulo"),
      "legendaSensivel": z.string().default("expõe ou apaga dados"),
      "legendaBloqueado": z.string().default("liberado ao subir o nível"),
      "perfil": z.string().default("Perfil"),
      "notaHeranca": z.string().default("Mudanças no perfil valem para todos os usuários dele, exceto onde houver exceção individual."),
      "niveis": z.object({
        "SEM_ACESSO": z.string().default("Sem acesso"),
        "VER": z.string().default("Ver"),
        "EDITAR": z.string().default("Editar"),
        "GERENCIAR": z.string().default("Gerenciar"),
      }).default({}),
      "nivelRotulo": z.string().default("Nível de {modulo}"),
      "alcance": z.object({
        "MEUS": z.string().default("Meus"),
        "TODOS": z.string().default("Todos"),
      }).default({}),
      "sensivel": z.string().default("Sensível"),
      "motivos": z.object({
        "TETO_DO_PAPEL": z.string().default("Não disponível para este perfil"),
        "FLAG_DESLIGADA": z.string().default("Módulo desligado nesta instância"),
        "NIVEL_DO_MODULO": z.string().default("Liberado ao subir o nível do módulo para {nivel}"),
        "DESLIGADO": z.string().default("Desligado"),
        "DEPENDENCIA": z.string().default("Depende de: {dependencias}"),
        "ESTRUTURAL_MEUS": z.string().default("Regra fixa: só as próprias conversas e as da IA (Potenciais)"),
        "ESTRUTURAL_TODOS": z.string().default("Regra fixa: toda a base"),
      }).default({}),
      "fixoAviso": z.string().default("Gestor tem acesso total e fixo; este perfil não é configurável."),
      "somenteLeitura": z.string().default("Somente Gestor e Administrador editam perfis."),
      "copiar": z.string().default("Copiar permissões"),
      "copiarDe": z.string().default("Copiar de {perfil}"),
      "copiarIndisponivel": z.string().default("{perfil} tem acesso fixo e não pode ser copiado"),
      "vazio": z.string().default("Nenhum módulo corresponde à busca."),
    }).default({}),
    "excecoes": z.object({
      "grupoFixo": z.string().default("{papel} · fixo"),
      "perfilPadrao": z.string().default("padrão do perfil"),
      "perfilComExcecoes": z.string().default("perfil + exceções"),
      "desativado": z.string().default("desativado"),
      "herdaDe": z.string().default("Herda o perfil {papel} · altere só o que for diferente para esta pessoa"),
      "colunas": z.object({
        "permissao": z.string().default("Permissão"),
        "padrao": z.string().default("Padrão do perfil"),
        "usuario": z.string().default("Este usuário"),
      }).default({}),
      "personalizado": z.string().default("Personalizado"),
      "restaurar": z.string().default("Restaurar o padrão de {acao}"),
      "voltarPadrao": z.string().default("Voltar ao padrão do perfil"),
      "copiarDe": z.string().default("Copiar de outro usuário…"),
      "copiaIndisponivel": z.string().default("{nome} tem acesso fixo e não pode ser copiado"),
      "copiaForaDaAlcada": z.string().default("Fora da sua alçada"),
      "selecione": z.string().default("Selecione um integrante para ver as permissões."),
      "sim": z.string().default("Sim"),
      "nao": z.string().default("Não"),
      "excecao": z.string().default("1 exceção"),
      "excecoes": z.string().default("{n} exceções"),
      "rascunho": z.string().default("rascunho"),
      "fixoAviso": z.string().default("{papel} tem acesso fixo; exceções individuais não se aplicam."),
      "somenteLeitura": z.string().default("Você pode ver, mas não alterar as permissões desta pessoa."),
      "proprio": z.string().default("Suas permissões são definidas por Gestor ou Administrador."),
      "nivelNaoDelegavel": z.string().default("Nível de módulo só é alterado por Gestor ou Administrador."),
      "semPermissaoPropria": z.string().default("Você não pode conceder algo que você mesmo não tem."),
      "naoDelegavel": z.string().default("Fora do que foi delegado a você."),
      "vazio": z.string().default("Nenhum integrante visível."),
    }).default({}),
    "barra": z.object({
      "pendente": z.string().default("1 alteração não salva"),
      "pendentes": z.string().default("{n} alterações não salvas"),
      "descartar": z.string().default("Descartar"),
      "salvar": z.string().default("Salvar alterações"),
      "salvando": z.string().default("Salvando…"),
      "impactoPerfil": z.string().default("Vale para {n} usuários ativos deste perfil, exceto onde houver exceção."),
      "impactoUsuario": z.string().default("Vale só para {nome}."),
      "restauracao": z.string().default("Remove todas as exceções de {nome}."),
      "sairTitulo": z.string().default("Descartar alterações?"),
      "sairDescricao": z.string().default("Há alterações não salvas. Se continuar, elas serão perdidas."),
      "sairConfirmar": z.string().default("Descartar e continuar"),
      "sairCancelar": z.string().default("Continuar editando"),
      "sensivelTitulo": z.string().default("Confirmar alteração sensível"),
      "sensivelDescricao": z.string().default("Você está alterando permissões que expõem ou apagam dados. Revise antes de salvar."),
      "sensivelConfirmar": z.string().default("Salvar mesmo assim"),
      "cancelar": z.string().default("Cancelar"),
      "conflito": z.string().default("Outra pessoa salvou esta configuração depois que você a abriu. Suas alterações continuam aqui; recarregue a versão atual para revisar antes de salvar."),
      "recarregar": z.string().default("Recarregar versão atual"),
      "erro": z.string().default("Não foi possível salvar. Suas alterações continuam aqui."),
      "invalida": z.string().default("Algumas alterações não são permitidas: {itens}"),
      "negada": z.string().default("Você não tem alçada para esta alteração."),
      "salvo": z.string().default("Permissões salvas."),
    }).default({}),
    "copia": z.object({
      "titulo": z.string().default("Copiar permissões"),
      "descricao": z.string().default("Prévia calculada pelo servidor · nada é salvo até você aplicar ao rascunho e salvar"),
      "origem": z.string().default("Origem"),
      "origemPlaceholder": z.string().default("Selecione a origem"),
      "muda": z.string().default("O que muda"),
      "impedido": z.string().default("O que não será copiado"),
      "semMudancas": z.string().default("Nenhuma diferença em relação ao atual."),
      "motivos": z.object({
        "TETO_DO_PAPEL": z.string().default("acima do limite do perfil de destino"),
        "FORA_DA_ALCADA": z.string().default("fora da sua alçada"),
      }).default({}),
      "valores": z.object({
        "PERMITIDO": z.string().default("Permitido"),
        "NEGADO": z.string().default("Negado"),
      }).default({}),
      "aplicar": z.string().default("Aplicar ao rascunho"),
      "cancelar": z.string().default("Cancelar"),
      "carregando": z.string().default("Calculando prévia…"),
      "erro": z.string().default("Não foi possível calcular a prévia."),
    }).default({}),
    "sessao": z.object({
      "atualizada": z.string().default("Seu acesso foi atualizado por um gestor."),
    }).default({}),
  }).extend({
  modulos: z.record(z.string(), z.object({ rotulo: z.string(), descricao: z.string() })).default(MODULOS_PADRAO),
  capacidades: z.record(z.string(), z.string()).default(CAPACIDADES_PADRAO),
}).default({});
