import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { TextosSchema } from "@/lib/config/schema";

/** Catálogo servido pelo backend (`textos.json`), validado pelo mesmo schema do app: sem texto inventado no teste. */
export const textosReais = TextosSchema.parse(
  JSON.parse(readFileSync(resolve(process.cwd(), "../backend/crm-app/src/main/resources/textos.json"), "utf8")),
);
