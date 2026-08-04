# Prompt — Enviar os commits pendentes

> Rode agora. Dois minutos.

---

A branch `main` está **12 commits à frente de `origin/main`**. É uma semana de trabalho (E11, E11b, E12, E13) existindo em uma única máquina, sem cópia em lugar nenhum.

## O que fazer

```
git status
git log --oneline origin/main..main
git push origin main
```

Confirme depois que `origin/main` está no mesmo SHA que `main`.

## Alterações locais não commitadas

Existem quatro arquivos com alteração pendente que vêm sendo preservados há várias etapas:

- `.gitignore`
- `design/TOKENS.md`
- `backend/build.txt`
- `backend/set`

Avalie cada um:

- `.gitignore` e `design/TOKENS.md` — mudanças legítimas; commite se fizerem sentido
- `backend/build.txt` e `backend/set` — parecem lixo de sessões anteriores (saída redirecionada e um comando digitado errado). Se forem isso, apague; se não, me diga o que são

Nada de arquivo solto na raiz do backend indefinidamente.

## A partir de agora, regra permanente

**Toda tarefa termina com commit e push.** Não acumule.

Ao fim de qualquer etapa, antes de reportar:

1. `git status` — nada relevante fora do commit
2. Commit com mensagem em Conventional Commits
3. `git push origin main`
4. Confirme que `origin/main` avançou

No relatório, informe o SHA **e** que o push foi confirmado. Se o push falhar, isso é parte do relatório, não algo a resolver em silêncio.

Motivo: código só existe em um lugar é código que pode desaparecer. E o CI só roda no que chega ao GitHub — sem push, o pipeline não tem o que verificar.
