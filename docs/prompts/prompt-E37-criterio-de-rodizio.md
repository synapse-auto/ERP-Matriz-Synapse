# Prompt E37 — a IA entrega todos os leads para a mesma pessoa

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto — não é bug de tela, é comissão

A lista que a Automação consulta para decidir a quem entregar um lead sem dono sai daqui:

```sql
-- backend/crm-equipe/.../infrastructure/persistencia/AtendenteDisponivelRepositorioJdbc.java
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
 WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   AND u.papel = 'ATENDENTE' AND u.status_presenca = 'ONLINE'
 ORDER BY u.nome
```

`ORDER BY u.nome` é **ordem alfabética**, não rodízio. E a E33 usou esse mesmo critério no
`POST /internal/v1/atendimentos/{id}/transferir-proximo-humano`.

Consequência: enquanto a Ana estiver disponível, **a Ana recebe todos** os leads que a IA
transferir. O Rafael não recebe nenhum.

O javadoc do `PapelUsuario` já dizia o preço disso:

> *"Os atendentes trabalham por comissao e disputam leads: errar esta resposta e incidente
> comercial, nao bug de tela."*

O mesmo vale se a Automação chamar `GET /internal/v1/atendentes/disponiveis` e pegar o primeiro da
lista — o viés existe nos dois caminhos, independentemente.

## Decisão do arquiteto — o critério

**Quem tem menos atendimentos abertos recebe.** Desempate: quem está há mais tempo sem receber.
Último desempate: `u.id`, só para a ordem ser determinística.

Por quê este e não rodízio puro por vez: rodízio ignora carga. Um atendente com seis conversas
abertas e outro com uma receberiam alternadamente, e o cliente espera mais. "Quem está mais livre
atende" também é a regra que a equipe entende sem precisar de explicação — e regra de distribuição
que o time não entende vira reclamação.

> **Ponto de parada.** Se não existir uma definição de "atendimento aberto" já usada no sistema,
> **pare e avise.** Use a mesma que a aba Atendimentos usa para o filtro **Ativos** — não invente
> uma segunda noção de aberto. Duas definições de "aberto" no mesmo produto é defeito garantido
> mais adiante.

---

## Bloco 1 — O critério, em um lugar só

- A consulta de disponíveis passa a ordenar por: atendimentos abertos (crescente), depois tempo
  desde o último recebido (mais antigo primeiro, quem **nunca** recebeu vem antes de todos), depois
  `u.id`.
- **Não mude quem a consulta devolve.** O `WHERE` fica idêntico: disponível para IA, ativo,
  ATENDENTE, ONLINE. Esta etapa mexe em **ordem**, não em conjunto. Um teste tem que provar isso.
- O critério mora em **um lugar**. Se o `transferir-proximo-humano` tiver a própria cópia da regra,
  as duas divergem na primeira alteração — e ninguém percebe, porque as duas continuam devolvendo
  alguém.
- Cuidado com N+1: contar atendimentos abertos por atendente é uma agregação na mesma consulta, não
  uma consulta por atendente.

## Bloco 2 — `transferir-proximo-humano` usa o critério

- O endpoint da E33 passa a escolher pelo critério do Bloco 1, não por nome.
- Sem atendente elegível continua respondendo `409` com Problem Details, como a E33 fez. **Não
  mude** esse comportamento.
- A transferência continua na mesma transação da escolha. Escolher fora da transação abre janela
  para dois leads irem ao mesmo atendente ao mesmo tempo, que é justamente o que este prompt existe
  para evitar.

## Bloco 3 — `GET /internal/v1/atendentes/disponiveis` sai na mesma ordem

O Dylan pode estar pegando o primeiro da lista no workflow. Se essa rota continuar em ordem
alfabética, o viés volta por fora — com o `transferir-proximo-humano` corrigido e o resultado igual
ao de antes.

- A rota devolve na ordem do critério, para que pegar o primeiro seja correto por construção.
- Documente em `docs/16-acesso-da-automacao.md`: **a ordem é a recomendação**, o primeiro da lista é
  quem deve receber. Sem isso escrito, ninguém sabe que a ordem significa alguma coisa.

---

## Testes — a proteção nasce com um teste que a viola

Pelo controller real, como a E35b, a E36 e a E36b fizeram.

- Três atendentes disponíveis com 3, 1 e 5 atendimentos abertos → o de 1 vem primeiro.
- Empate em atendimentos abertos → vence quem recebeu há mais tempo.
- Atendente que **nunca** recebeu lead → vem antes de quem já recebeu.
- Empate total → ordem determinística por `id`, estável entre execuções.
- **O conjunto não mudou:** mesmos atendentes de antes, só em outra ordem. Monte o cenário e compare
  os ids como conjunto.
- Atendente ONLINE com `disponivel_para_ia = FALSE` continua fora. Regressão da E36.
- Atendente OFFLINE continua fora, mesmo com a flag ligada. Regressão da E36.
- `transferir-proximo-humano` escolhe o mesmo que o topo da lista devolveria.
- Sem elegível → `409`, sem alteração. Regressão da E33.
- Duas transferências seguidas **não** caem no mesmo atendente, quando há mais de um disponível.
  Este é o teste que descreve o defeito em uma frase.

## Definição de pronto

- [ ] Critério em um lugar só, usado pelas duas rotas
- [ ] `WHERE` inalterado; ordem alterada
- [ ] Sem N+1 na contagem de abertos
- [ ] `transferir-proximo-humano` escolhendo pelo critério, na mesma transação
- [ ] `/internal/v1/atendentes/disponiveis` na mesma ordem, documentado no `docs/16`
- [ ] Os dez testes acima
- [ ] Confirmação por mutação: volte o `ORDER BY u.nome` e mostre **qual** teste falha e com o quê
- [ ] CI verde com **número da run**

## No relatório

1. A definição de "atendimento aberto" que você usou, e **onde ela já existia**.
2. A consulta final, para eu conferir a agregação.
3. O resultado da mutação — teste, esperado, obtido.
4. **Os nomes dos testes novos, um por linha.** Não informe o total da suíte.
5. Se o `transferir-proximo-humano` já tinha cópia própria da regra, e o que você fez com ela.
6. Variável nova no Dokploy: expectativa **nenhuma**.
7. O SHA final **e o SHA curto** — `SYNAPSE_IMAGE_TAG` usa a tag curta, nunca `latest`.

---

## Fora desta etapa

Distribuição por skill, por tag, por etapa do funil ou por meta de comissão — nada disso está no
protótipo. Rotinas pré-definidas por atendente (fase 2). Avaliação por atendimento (escala do CSAT
em aberto). Qualquer execução ou varredura: é do n8n, por RN-CRM-07.
