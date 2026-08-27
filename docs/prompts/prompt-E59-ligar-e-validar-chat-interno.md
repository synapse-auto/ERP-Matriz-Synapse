# Prompt E59 — ligar e validar o chat interno com dois usuários

> Leia `AGENTS.md` e `docs/13-estado-do-projeto.md` antes de começar.
> Esta é uma validação operacional da E44 após a publicação da E58. Não altere código, migration,
> contrato ou imagem. Não faça deploy por conta própria.
> O SHA publicado e comprovado pela E58 é `e0fa645`; a run é `32968074604`.

---

## Contexto — a variável do Dokploy não é a fonte que o menu consulta

O relatório da E58 provou a imagem, mas não provou que o chat interno está habilitado na instância.
No código atual, a cadeia que decide se a aba aparece é:

```java
// backend/crm-automacao-config/src/main/java/com/synapse/crm/automacaoconfig/application/featureflag/FeatureService.java
return flags.listarTodas().stream()
        .filter(FeatureFlag::habilitado)
        .map(FeatureFlag::chave)
        .toList();
```

```typescript
// frontend/src/components/shell/sidebar.tsx
return (flags ?? []).includes(item.flag);
```

O item da barra lateral usa a chave `chat_interno` e o frontend consulta
`GET /api/v1/config/features`. A fonte efetiva hoje é a tabela `feature_flag`.

Já `FEATURE_CHAT_INTERNO` aparece apenas como propriedade `synapse.features.chat-interno` em
`backend/crm-app/src/main/resources/application.yml`; não encontrei código que leia essa propriedade
para montar a resposta de `/api/v1/config/features`. Portanto:

- mudar apenas `FEATURE_CHAT_INTERNO=true` no Dokploy **não prova** que a aba será liberada;
- a operação que efetivamente libera a aba na instância existente é atualizar
  `feature_flag.habilitado` para a linha `chave = 'chat_interno'`;
- não use `habilitada`: a coluna real é `habilitado`;
- não transforme esta constatação em correção de código nesta etapa. A decisão de tornar a variável
  de ambiente autoritativa fica para uma etapa própria, se for desejada.

## Bloco 0 — confirmar a imagem e a instância alvo

Antes de qualquer alteração operacional:

1. Confirme no Dokploy que o backend e o frontend estão rodando com a tag `e0fa645`.
2. Confirme que a instância é a homologação da Estrutural Vidros, não outro filho ou produção.
3. Confirme `/health/critical` antes da alteração.
4. Registre a situação inicial:

```sql
SELECT chave, habilitado, descricao
  FROM feature_flag
 WHERE chave = 'chat_interno';
```

5. Confirme pelo endpoint autenticado que `chat_interno` não está presente em
   `GET /api/v1/config/features`, caso a aba esteja realmente desligada.

> **Ponto de parada.** Se o SHA rodando não for `e0fa645`, se a instância não for a homologação
> combinada, se a linha `chat_interno` não existir, ou se `/health/critical` não estiver `UP`, pare e
> relate. Não crie a flag, não a corrija por migration e não altere outra instância.

## Bloco 1 — ligar a capacidade da instância

A alteração é de configuração da instância, não de código:

```sql
UPDATE feature_flag
   SET habilitado = TRUE
 WHERE chave = 'chat_interno';
```

Depois confirme:

```sql
SELECT chave, habilitado
  FROM feature_flag
 WHERE chave = 'chat_interno';
```

Em seguida, faça uma requisição autenticada a `GET /api/v1/config/features` e confirme que a
resposta contém exatamente `chat_interno`. Faça logout/login ou recarregamento completo no
frontend; a sidebar só pode ser aceita como ligada quando o item **Chat interno** aparecer.

Não altere `R__seed_dev.sql`, não crie migration e não dependa de reiniciar o backend para uma
alteração que o `FeatureService` lê diretamente do banco sem cache.

## Bloco 2 — escolher os dois usuários de validação

Marcondes deve indicar dois usuários ativos da homologação, identificados no relatório apenas por
nome e papel, sem registrar senha ou token. Eles devem ser contas distintas e autorizadas para o
teste. Se não houver escolha de negócio, use dois `ATENDENTE` ativos; não use `ADMINISTRADOR` como
único participante, porque isso não demonstra o fluxo normal da equipe.

Registre antes do teste:

- usuário A: nome, papel e presença observada;
- usuário B: nome, papel e presença observada;
- confirmação de que ambos conseguem entrar no CRM;
- navegador ou perfil separado usado para cada sessão.

## Bloco 3 — validação ponta a ponta com duas sessões

Use duas sessões de navegador independentes, uma para cada usuário.

- [ ] A vê a aba Chat interno depois de uma recarga completa.
- [ ] B vê a aba Chat interno depois de uma recarga completa.
- [ ] A lista B entre os contatos ativos.
- [ ] A abre uma conversa direta com B.
- [ ] B abre a mesma conversa; não deve nascer uma segunda conversa para o mesmo par.
- [ ] A envia uma mensagem de teste com identificador único, por exemplo
      `E59-A-<data-hora>`.
- [ ] B recebe a mensagem sem F5, pelo evento de tempo real `CHAT_INTERNO_MENSAGEM`.
- [ ] B responde com outro identificador único.
- [ ] A recebe a resposta sem F5.
- [ ] Cada mensagem aparece uma única vez para cada participante.
- [ ] Ao abrir a conversa, o contador individual de não lidas de quem abriu zera.
- [ ] O marcador de leitura do outro participante não é alterado pela leitura do primeiro.
- [ ] Atualizar a página preserva a conversa e o histórico.

Capture evidência antes e depois da primeira mensagem, com os nomes das duas sessões visíveis ou
anotados fora da captura. Não exponha token, senha ou dados de cliente real.

## Testes de segurança que esta validação consegue e não consegue provar

Com dois usuários participantes, a validação operacional prova entrega, persistência, idempotência
da abertura da conversa, leitura individual e tempo real. Ela **não prova** que um terceiro não
participante não consegue ler a conversa.

A proteção negativa já deve ser conferida no repositório pelo `RlsIsolamentoIT`: gestor e usuário
não participante não podem ler nem inserir mensagem na conversa de terceiros. Não declare esse
aspecto como validado em homologação sem uma terceira conta aprovada e um teste explícito.

## Definição de pronto

- [ ] Instância e SHA `e0fa645` confirmados antes da alteração.
- [ ] `/health/critical` estava `UP` antes e depois.
- [ ] Estado inicial de `feature_flag.chat_interno` registrado.
- [ ] `feature_flag.chat_interno` ficou `habilitado = TRUE` na instância correta.
- [ ] `GET /api/v1/config/features` devolveu `chat_interno`.
- [ ] Dois usuários aprovados validaram o fluxo em sessões independentes.
- [ ] Mensagens foram entregues em tempo real nos dois sentidos.
- [ ] Leitura individual e persistência após recarga foram confirmadas.
- [ ] Nenhuma senha, token ou dado sensível entrou no relatório ou nas capturas.
- [ ] Nenhum código, migration ou imagem foi alterado nesta etapa.

## No relatório

1. SHA real em execução e evidência de `/health/critical`.
2. Estado inicial e final de `feature_flag.chat_interno`.
3. Nome/papel dos dois usuários, sem credenciais.
4. Resultado item a item do Bloco 3, com horário e evidência.
5. Confirmação de que `/api/v1/config/features` passou a conter `chat_interno`.
6. Se a variável `FEATURE_CHAT_INTERNO` foi alterada no Dokploy, registre-a como alteração
   operacional, mas diga explicitamente que a aba foi liberada pela linha do banco; a variável não é
   consumida pelo `FeatureService` atual.
7. Qualquer falha de WebSocket, RLS, duplicidade, cache ou divergência entre a flag do ambiente e a
   flag do banco.
8. Decisões de negócio que ficaram abertas, especialmente se o chat deve ser ligado para a
   homologação inteira ou somente para contas selecionadas.

## Fora desta etapa

- Não conectar a conversa interna a um atendimento.
- Não implementar grupos, mídia, edição, exclusão, reações, menções ou busca.
- Não criar endpoint novo.
- Não corrigir a divergência entre `FEATURE_CHAT_INTERNO` e `feature_flag`; isso exige decisão sobre
  qual camada deve ser a fonte de verdade.
- Não executar smoke RLS destrutivo ou alterar dados de produção.
