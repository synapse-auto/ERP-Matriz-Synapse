# Prompt E96 — Dashboard: destravar o scroll e aproximar do modelo

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/dashboard-scroll-e-identidade`) e PR.
> **Sem merge, sem deploy.** Só `frontend/`. Verificação proporcional: suíte do frontend, **sem Maven**.

---

## Bloco 1 — O scroll travado (faça este primeiro, e sozinho)

Na tela de Dashboard não dá para rolar: o conteúdo é cortado no "Horário de pico" e não há barra de
rolagem. Comece por aqui e **feche este bloco antes de encostar em cor**, senão você vai depurar
layout com o CSS mudando debaixo do pé.

**A cadeia de altura já está mapeada — não perca tempo redescobrindo:**

```
<html class="h-full">
  <body class="flex h-full flex-col overflow-hidden">
    [data-slot=page-canvas]   flex flex-1 min-h-0 overflow-hidden
      wrapper                 flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden
        <main data-slot=page-surface>  flex h-full min-h-0 flex-1 flex-col overflow-y-auto
          raiz do dashboard   flex min-h-full flex-col gap-6 p-6 lg:p-8
```

No papel isso deveria rolar: o `<main>` tem altura definida e `overflow-y-auto`. **Não está rolando,
então a leitura estática não basta — diagnostique no navegador**, com a aplicação no ar:

- descubra **qual elemento é de fato o contêiner de rolagem** e compare `scrollHeight` com
  `clientHeight` em cada candidato da cadeia acima;
- o `overflow-hidden` do `page-canvas` é suspeito de estar **cortando** o excesso em vez de deixar o
  `<main>` rolar. Confirme antes de mexer;
- confira se algum elemento **dentro** do dashboard tem altura fixa ou `overflow` próprio comendo a
  rolagem.

**Relate a causa antes da correção.** Se você "consertar" trocando classe até parecer funcionar, o
mesmo bug volta na próxima tela.

**A restrição que torna isso não-trivial:** a tela de Atendimentos depende dessa mesma cadeia para
ter rolagem interna nos painéis (lista, conversa, painel do lead) sem a página inteira rolar. **A
correção não pode quebrar Atendimentos.** Se a solução exigir tratamento diferente por rota, diga
isso no relatório em vez de aplicar um `overflow` global.

Teste que prova: com o Dashboard aberto numa janela baixa, o "Horário de pico" fica alcançável por
rolagem — e Atendimentos continua com os painéis rolando por dentro, sem rolagem da página.

## Bloco 2 — Identidade visual, contra o modelo

A primeira captura é o CRM hoje; as demais são o modelo. As diferenças são de tratamento, não de
estrutura de dados. Use **tokens**, nunca hex — o `tema.json` troca cor por instância.

**Abas.** O modelo usa sublinhado; hoje é pílula azul preenchida. Troque para sublinhado.
**Mantenha o sufixo "Em breve"** nas abas que ainda não existem e mantenha-as claramente inativas —
o modelo não tem esse sufixo porque é mock com tudo pronto. Aba futura que parece clicável é pior que
aba feia.

**Filtro de período.** Hoje os doze meses são pílulas **azuis sólidas**, o que parece "tudo
selecionado" e vira uma parede de azul. No modelo são pílulas suaves, com contorno, e só a seleção
fica destacada. Acrescente também a opção "Ano inteiro". E o "Período de originação", hoje dois
campos `De`/`Até` expostos, vira **um botão com ícone de calendário** que abre a escolha.

**Cartões de indicador.** Hoje: ícone pálido, monocromático, todos iguais. No modelo: um chip tonal
com **cor por métrica**, rótulo pequeno acima, número grande, e uma sub-linha de acumulado. Aplique
isso usando os tokens que já existem (`primary`, `cor-ia`, `cor-sucesso`, `cor-atencao`, `chart-*`) —
se faltar token para alguma métrica, use o mais próximo e diga qual no relatório.

**Funil de conversão.** Hoje a barra é um fiapo quase invisível e o número fica solto na direita. No
modelo a barra é preenchida, com o número **dentro** dela e a taxa de conversão da etapa à direita.
Importante: **com valor zero a barra precisa continuar visível** como trilho — é o estado real desta
instância hoje.

## Bloco 3 — Três coisas que você não decide sozinho

Implemente como está escrito aqui e **registre no relatório para o Marcondes confirmar**:

1. **Selos de tendência** (`↑ +8%`, `↓ −12%`) aparecem em todos os cartões do modelo. Eles exigem
   comparação com o período anterior. **Verifique se a API devolve esse dado.** Se não devolver,
   **não invente e não calcule no cliente**: deixe o cartão sem o selo e relate que falta o dado no
   backend. Selo de tendência errado num painel executivo é pior que selo nenhum.
2. **"Top atendentes"** hoje é *por avaliação*; no modelo é *por vendas fechadas*. Isso é troca de
   métrica, não de visual. **Mantenha a métrica atual** e aplique só o tratamento visual do modelo
   (medalha colorida no 1º, 2º e 3º, valor à direita). Trocar a métrica é decisão de negócio.
3. **O modelo tem seis cartões em grade 3×2 com o "Top atendentes" numa coluna à direita**; hoje são
   cinco numa linha só. Adote a grade do modelo **com os indicadores que existem hoje** — não crie
   indicador novo para preencher a grade.

## Bloco 4 — O estado vazio é o estado real

Repare na captura do CRM: taxa de conversão 0,0%, funil inteiro zerado, cinco avaliações. **O modelo
é um mock cheio de dados; esta instância está quase vazia.** Uma tela desenhada só para o mock fica
pior que a atual quando o dado é zero.

Então: valide tudo com os dados reais de hoje, e garanta que zero não vira barra invisível, divisão
por zero, `NaN`, `-Infinity` nem espaço em branco sem explicação.

## O que não fazer

- Nada de hex. Nada de cor fora dos tokens.
- Não remova o "Em breve" nem habilite aba que não existe.
- Não invente métrica, tendência ou dado que a API não devolve.
- Nada fora do Dashboard e do que o Bloco 1 exigir — se a correção do scroll pedir mudança no shell,
  ela é permitida, mas precisa estar isolada e justificada.
- Não mexa na sidebar: ela está em outra etapa e nas capturas aparece só retraída, o que é estado, não
  defeito.

---

## Verificação

- `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`. **Sem Maven.**
- Teste do scroll: Dashboard alcança o fim do conteúdo; Atendimentos mantém rolagem interna dos
  painéis sem rolar a página.
- Teste de estado vazio: com zero em tudo, nada de `NaN` ou barra sumida.
- **Capturas obrigatórias**, com a aplicação no ar: Dashboard no topo, Dashboard rolado até o fim,
  e Atendimentos com uma conversa aberta provando que não regrediu.

## Relatório

1. A causa real do scroll travado, em uma frase, com o elemento nomeado.
2. Se a correção precisou tocar o shell e por quê.
3. Se a API devolve dado de tendência.
4. Quais tokens você usou por métrica, e onde faltou token.
