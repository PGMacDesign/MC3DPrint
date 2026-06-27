#!/usr/bin/env python3
"""Generate the 1.21.4+ item *model definitions* (assets/<ns>/items/<id>.json).

1.21.4 split item rendering into two layers: the model JSON in models/item/<id>.json
(unchanged, pre-1.21.4 style) AND a "model definition" in items/<id>.json that an item's
minecraft:item_model component resolves to. Without the definition, 1.21.4+ renders the
item as the magenta/black missing-texture (1.21.1 ignores items/ entirely, so these files
are harmless there — the same shared resource set serves both Stonecutter nodes).

One definition per existing models/item/<id>.json, pointing back at that model:
    { "model": { "type": "minecraft:model", "model": "mc3dprint:item/<id>" } }

Idempotent + reproducible — re-run after adding any item (a new models/item file).
"""
import json
import pathlib

MOD_ID = "mc3dprint"
ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / MOD_ID
MODELS_ITEM = ASSETS / "models" / "item"
ITEMS = ASSETS / "items"


def main() -> None:
    ITEMS.mkdir(parents=True, exist_ok=True)
    model_ids = sorted(p.stem for p in MODELS_ITEM.glob("*.json"))
    written = 0
    for item_id in model_ids:
        definition = {
            "model": {
                "type": "minecraft:model",
                "model": f"{MOD_ID}:item/{item_id}",
            }
        }
        out = ITEMS / f"{item_id}.json"
        out.write_text(json.dumps(definition, indent=2) + "\n")
        written += 1
    print(f"Wrote {written} item model definitions to {ITEMS.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
