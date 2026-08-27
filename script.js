const fs = require('fs');
const path = 'c:/Users/marcondes/Desktop/projeto_matriz/frontend/src/lib/config/schema.ts';
let content = fs.readFileSync(path, 'utf8');
const novidadesSchema = \  novidades: z.object({
    titulo: z.string(),
    subtituloNovidades: z.string(),
    subtituloEmBreve: z.string(),
    abas: z.object({ novidades: z.string(), embreve: z.string() }),
    itensNovidades: z.array(z.object({ titulo: z.string(), descricao: z.string(), data: z.string() })),
    itensEmBreve: z.array(z.object({ icone: z.string(), titulo: z.string(), descricao: z.string(), status: z.string(), tom: z.string(), previsao: z.string() })),
  }),\;
content = content.replace('export const TextosSchema = z.object({', 'export const TextosSchema = z.object({\n' + novidadesSchema);
fs.writeFileSync(path, content);
