# Contas de teste — isolamento da operação

## Decisões aprovadas e limites

`usuario.usuario_teste` é explícito e inicia `false`. Apenas ADMINISTRADOR ativo pode definir a classificação de outra conta. GESTOR e SUBGESTOR não são ADM. Não há heurística por nome, e-mail ou UUID, nem classificação de produção nesta entrega.

Contas de teste mantêm login e leitura sob as permissões existentes, sem visão global adicional. São somente leitura na operação normal, inclusive quando ADMINISTRADOR. Não podem assumir, responder, participar, criar lembretes/agendamentos ou interagir em chats compartilhados. Escrita de teste futura exige contexto de dados explicitamente isolado.

Uma conta com vínculos operacionais/históricos não pode ser marcada. A resposta 409 traz apenas amostras de IDs técnicos por relação, para revisão privada pelo ADM. Não apaga autoria, transfere carteira, mascara mensagens nem cancela agendamentos. Sem lista confiável de usuários antigos, o operador deve identificá-los explicitamente na Gestão; não há SQL automático de classificação.

## Contrato e arquitetura

- POST `/api/v1/usuarios`: `usuarioTeste` opcional; ausência preserva a criação comum. Presença do campo exige ADM vigente no banco.
- PATCH `/api/v1/usuarios/{id}/classificacao-teste`: `{ "usuarioTeste": true, "confirmado": true }`, confirmação explícita, não permite autoclassificação. Conflito: `USUARIO_TESTE_COM_VINCULOS` e `vinculos[{recurso,registroId}]`.
- `/me` e respostas administrativas expõem o booleano. Payload de edição comum não pode alterar a classificação.
- V107 adiciona coluna, view central `usuario_visivel` (filtro anterior à paginação/contagem) e proteção contra classificação com vínculos e novos vínculos após classificação. A autenticação lê a tabela original. Specification/RLS de leads permanecem intactas.
- O diagnóstico administrativo usa pool geral e contexto SERVICO apenas dentro de função restrita, restaurado inclusive em falha; retorna uma amostra por relação, nunca mensagens ou payload. Classificação e criação compartilham a mesma transação/pool.
- Guards de vínculo usam colunas tipadas e lock compartilhado do usuário, não convertem corpo de mensagem em JSON; atualização só de entrega não repete a verificação. O inventário inclui FKs operacionais, autoria de mensagem e snapshots/outbox pré-existentes.
- Filtro HTTP consulta classificação no banco para mutações; exceções são autenticação e endpoints POST exclusivamente de leitura. Permissões efetivas também são limitadas a leitura. Recusa é 403 com código `CONTA_TESTE_SOMENTE_LEITURA`, auditada sem payload.
- Execução de mensagem programada revalida autor; bloqueio mantém AGENDADA por rollback e exige revisão, sem cancelamento silencioso. Outbox revalida autoria antes do provedor e trata recusa como permanente; auditoria usa transação independente. Não reprocessar produção para testar.

## Matriz de superfícies

| Superfície | Tratamento |
|---|---|
| Equipe, detalhes, fotos, Gestão de permissões | Filtro no servidor e proteção de consulta direta; ADM vê selo/filtro |
| Dashboard, desempenho, painel operacional | Contas de teste fora da equipe e indicadores operacionais mesmo para ADM; não subtrai vendas/mensagens históricas |
| Presença | Identidade de teste somente ao próprio usuário e ADM |
| Transferência, convite, rodízio e disponíveis da IA | Exclusão incondicional como destino; disponibilidade/presença não a tornam elegível |
| Setores e contatos do Chat Interno | Leitura filtrada; novos vínculos operacionais recusados |
| Histórico, autoria, participantes, lembretes, agendamentos | Marcação bloqueada enquanto houver vínculo; registros preservados |
| Auditoria administrativa | Não ADM não descobre ator/entidade de teste; registros mantidos no banco |
| Cache HTTP/cliente e tempo real | Revisão de permissões e aviso existente de acesso alterado; reset de consultas; QueryClient novo por identidade/papel |

Consultas de histórico não retiram mensagens/vendas para esconder autoria: a classificação é recusada antes se qualquer relação estiver presente, e triggers impedem novos vínculos. Destinos por ID devolvem o mesmo motivo de inexistência para uma conta de teste. O filtro administrativo da grade não altera o universo operacional dos indicadores.

## Interface e compatibilidade

Gestão permite criar e classificar contas com confirmação; negativa mostra código legível e IDs técnicos sem conteúdo. Conta marcada recebe aviso de somente leitura. Catálogo `usuariosTeste` tem defaults para catálogos antigos. Não há novas variáveis, polling ou mudanças de fonte/tema.

## Viabilidade de impersonação (não implementada)

Exige sessão delegada curta, emissão própria vinculando ator real e sujeito de teste, escopo somente leitura, início/fim auditados, indicação persistente, revogação, proibição de aninhamento e de destino comum. Retorno deve recuperar a sessão original sem expor senha. Caches, tokens e STOMP devem ser desmontados na troca; permissões são exclusivamente do sujeito. Não reutilizar token ADM nem implementar seletor cosmético. Esforço relativo alto em segurança/testes, separado desta entrega.

## Evidências e validação

Primeira rodada de seis testes HTTP/PostgreSQL detectou erro de pool na classificação recém-criada e fixture de telefone inválida; ambos corrigidos. Segunda rodada: `UsuariosDeTesteIT` 6/6. Alterações posteriores exigem nova execução; não confundir esse resultado parcial com suíte completa ou CI.

- Frontend completo em Node 22/Linux: 162 arquivos, 1.303 testes. A primeira rodada identificou dois testes de geometria do layout que precisavam isolar o novo aviso (o aviso tem teste próprio); corrigidos, sem remover testes. A execução Windows foi interrompida após falha/hang do teste de provisionamento de marca; Linux inclui essa suíte integralmente.
- Typecheck, lint e build aprovados; lint tem quatro avisos anteriores (virtualização e navegação). `git diff --check` sem erro.
- Testes de composição/outbox/programada recusam autor de teste antes do provedor, sem mudar texto persistido nem cancelar agendamentos. Integração HTTP/PostgreSQL cobre padrão, payload adulterado, ADM de teste, destinos, leitura e bloqueio auditado, vínculos e inventário de triggers. Nova negativa STOMP exercita a fila pessoal real e exige aviso sem UUID da conta oculta.
- O primeiro `clean verify` revelou uma incompatibilidade do lock do guard com `synapse_chat_rls`: essa role só lê `usuario`. Corrigido com helper mínimo SECURITY DEFINER, search_path fechado e EXECUTE restrito; não foi concedido UPDATE à role. `EncaminhamentoDoChatParaClienteIT` voltou a passar 34/34 no teste direcionado. A regressão está também coberta por criação real de conversa e asserção negativa do privilégio UPDATE.
- Validação headed na aplicação real com PostgreSQL/Redis descartáveis: ADM cria conta explicitamente, filtro e selo após salvar/recarregar, gestor não recebe identidade/controle administrativo, conta de teste autentica e mostra somente leitura. Font-family computado `Inter, "Inter Fallback", Inter, sans-serif`, fontes `loaded`. Desktop 1440×1000 e celular 390×844; a primeira comparação encontrou corte no botão novo; após ajuste, tabela 346 px, borda direita 363 px em viewport 390 px.

Screenshots sintéticos em `docs/evidencias/usuarios-de-teste/`: criação desktop, filtro desktop/celular, leitura do gestor e sessão de teste. Não são reprodução HTML; usam componentes, estilos e API reais. Logo relativa indisponível no proxy local de desenvolvimento usa o fallback existente, sem mudança de identidade.

Na execução completa seguinte, `UsuariosDeTesteIT` passou 12/12 e o encaminhamento 34/34. Restaram dois casos: o diagnóstico setorial concatenava um segundo WHERE após a exclusão de teste, e a fixture de boot em schema72 não tinha a coluna aditiva exigida pelo mapeamento atual. Corrigidos sem relaxar as asserções: mantém-se a prova de ausência da V73/backfill, com a coluna atual preparada somente na fixture. A nova rodada começou sem Docker e foi repetida após iniciar o engine; esse resultado de ambiente não é aprovação dos testes.

Repetição direcionada aprovada: contas de teste 12/12, distribuição por setor 15/15, boot sem executar V73 1/1. Frontend completo repetido no estado final: 162 arquivos/1.303 testes, typecheck/lint/build aprovados.

A CI do primeiro SHA revelou também uma premissa antiga no smoke empacotado: subir HTTP em schema72 antes de invocar o runner. A imagem atual exige a coluna V107. O smoke agora prepara o banco descartável pelo runner one-shot antes do processo web, sem relaxar a validação do Hibernate nem alterar o boot de produção. O teste com fixture72+coluna aditiva prova ausência de backfill V73; não prova compatibilidade da imagem com um banco V72 puro. Instalação antiga ainda em V72 precisa seguir o runner controlado antes de iniciar esta versão; não há execução de migration pesada no boot web.

Pendente confirmar o `clean verify` completo após essas correções e o CI do SHA publicado. PR deve permanecer em draft enquanto isso não estiver comprovado. Não houve deploy ou alteração de dados reais. Dokploy: nenhuma variável nova; aplicar a migration apenas pelo procedimento normal de publicação controlada após aprovação.

Skills: Playwright carregada antes da validação. As skills clean-code, architecture-patterns, api-design-principles e supabaseboaspraticas não estão disponíveis no catálogo desta sessão; as regras canônicas de arquitetura, segurança e consultas foram aplicadas diretamente.

## Custos e procedimento operacional

A view aplica o corte antes de busca/contagem/paginação; o teste de ADM é não correlacionado, sem N+1 de pessoas. Destinos usam o booleano na consulta existente. O guard de vínculo lê a PK com lock compartilhado apenas ao criar/alterar referências; recebimento LEAD/IA e atualização só de entrega não consultam autoria humana. O compositor reutiliza a consulta de origem; templates passam a verificá-la também. Não há rede externa ou polling novo.

A verificação excepcional de classificação percorre amostras das relações e JSONs históricos no pool geral. Não roda em listagens ou envio/recebimento. Custo em volume real não foi medido: classificar contas antigas exige revisão controlada, não operação em massa. Não se afirma equivalência de CPU em produção com base no banco sintético.

O operador ainda precisa fornecer os IDs de contas antigas por canal privado. Na Gestão como ADM, conferir a conta, solicitar classificação e confirmar; havendo 409, encaminhar os IDs técnicos para revisão, sem apagar vínculos para contornar o bloqueio. Agendamento existente permanece AGENDADA e impede a marcação. Uma execução inconsistente bloqueada exige investigação; não é cancelada silenciosamente nem reenviada por este recurso.
