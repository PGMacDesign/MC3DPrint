import { defineCollection, z } from "astro:content";
import { glob } from "astro/loaders";

// Web guide content, adapted from the in-game Patchouli book. Each Markdown file
// under src/content/guide is one topic; `category` + `order` drive how the
// /guide hub groups and sorts them. The hub and the [slug] route are fully
// data-driven — adding a topic is just dropping in a new .md file.
const guide = defineCollection({
  loader: glob({ pattern: "**/*.md", base: "./src/content/guide" }),
  schema: z.object({
    title: z.string(),
    category: z.enum(["Basics", "Machines", "Multiblocks", "Resins"]),
    order: z.number(),
    summary: z.string(),
  }),
});

export const collections = { guide };
