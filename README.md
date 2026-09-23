# Spoiled Loot Guard

Keeps [Spoiled](https://www.curseforge.com/minecraft/mc-mods/spoiled) from forcing unopened loot
containers to roll their loot.

Minecraft 1.20.1 / Forge. Server-side. MIT.

## The problem

Spoiled walks every container each tick to age the food inside it. For **block** containers it
already skips unopened dungeon chests — `RandomizableContainerBlockEntity` with
`getLootTable() != null`. The **entity** branch, reached through
`SpoilHandler#updateContainer`, has no such check.

Reading one slot of an unopened chest minecart runs vanilla's
`AbstractMinecartContainer#getItem` → `ContainerEntity#getChestVehicleItem` →
`unpackChestVehicleLootTable(null)`. The `Player` argument only records who opened it, so passing
`null` does not stop anything: **the loot is generated even though nobody opened the container.**

That is expensive in a pack with Alex's Caves. An abandoned mineshaft minecart rolls
`minecraft:chests/abandoned_mineshaft`, which is the one and only condition on Alex's Caves'
`cabin_map` loot modifier:

```json
{
  "type": "alexscaves:cabin_map",
  "conditions": [
    { "condition": "forge:loot_table_id",
      "loot_table_id": "minecraft:chests/abandoned_mineshaft" }
  ]
}
```

So every roll builds an Underground Cabin Map. `MapItem#renderBiomePreviewMap` asks for the biome
at each square of the map, and because the cabin sits in terrain nobody has loaded,
`LevelReader#getBiome` cannot answer from a loaded chunk and falls through to
`MultiNoiseBiomeSource#getNoiseBiome` — recomputing the terrain noise from scratch, in our case
through Tectonic's density functions and citadel's mixins.

Measured on our server, 2026-09-23:

| | |
| --- | --- |
| server thread stalled | **5 min 10 s (310,653 ms), 0 ticks** |
| players disconnected | **3, within 0.1 s of each other** |
| share of that window in this path | **89.3%** |
| times the same storm fired that day | **33** |

Chunky makes it worse over time. Pre-generation stores only the loot table reference, not the
contents, so every pre-generated mineshaft minecart is still unopened and waiting. Region files
written two weeks earlier and never visited since still carry `LootTable` tags.

## What this does

Applies Spoiled's own block-side rule to the entity branch: if the entity is a `ContainerEntity`
whose `getLootTable()` is still set, the spoilage pass skips it.

Spoilage behaviour does not change. An unopened container holds no food yet, so there is nothing
to age; once a player opens it the reference is cleared and it is walked normally from then on.

It also logs the location of a skipped container, at most one line per ten minutes:

```
未開封の入れ物を腐敗の巡回から外した: entity.minecraft.chest_minecart @ minecraft:overworld [-3104, 26, 1488]（前回の記録から 7 件 ／ 通算 7 件）
```

That line exists because when we investigated the stall we could tell *what* had been touched but
not *where* — the server log records neither player positions nor chunk loads, so we could not
work out what had brought the new land into range. One line per storm answers that next time.

## Upstream

Not reported yet. Searching `Mrbysco/Spoiled` issues for `minecart`, `lag` and `performance`
returns nothing, and the block-side exclusion suggests the entity branch is an oversight rather
than a decision. If it is fixed upstream this mod becomes unnecessary; leaving it installed does
no harm, since it only cancels a pass that would have done nothing useful.
