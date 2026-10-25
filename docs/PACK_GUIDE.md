# Casket of Echoes: Modpack Guide

Everything a pack can change without code. Server settings live in `<world>/serverconfig/soulsvault-server.toml`
(ship defaults in `defaultconfigs/soulsvault-server.toml`); players' own preferences live in
`config/soulsvault-client.toml`. Both are editable in game with **Configured** / **Config Menus for Forge**.

## Server config sections

| Section | What it controls |
|---|---|
| `inventory` | `fullInventoryReplacement` (E opens the Casket UI), `allowRemoteVaultInFullInventory` |
| `vault` | overflow on full inventory, per-item cap, distinct-item cap (`0` = unlimited), max item NBT size, `requireAttunement` |
| `restPoints` | reach, `replenishRequiresRestPoint`, `echoSwapRequiresRestPoint`, `replenishOnWakeUp` |
| `buildEchoes` | starting slots, max slots (raised by Echo Pouches) |
| `fieldAccess` | Vestige Moth XP cost, Spectral Casket lifetime |
| `combat` | lock after damage (s), hostile radius, boss radius (`#soulsvault:bosses`); all `0` = off |
| `remoteAccess` | `NONE` / `XP_LEVELS` / `XP_POINTS` / `ITEM` cost, paid once per Casket session |
| `upgrades` | Reliquary Shard capacity / kinds per shard, max shards per Casket |
| `equipLoad` | meter on/off, base capacity, optional overload slowdown |
| `party` | provider (`AUTO`, `BUILTIN`, `FTB_TEAMS`, `OPAC`, `VANILLA_TEAMS`, `NONE`), built-in parties, max members, and `LEADER_CHOICE` / `ALWAYS` / `NEVER` rules for shared Casket, friendly fire, advancement and quest sharing; `protectOwnPets` (owners can't hurt their own pets unless sneaking, also under Epic Fight battle mode); whether merge / override are allowed; `returnContributionsOnLeave`; `upgradeCreditMinutes` |
| `keepsakes` | `requireUnlock` (Keepsake Coffer needed), `slots` (kinds of items), `baseStacks` / `upgradedStacks` (stacks of each item before / after a second Coffer) |

## Client options

`config/soulsvault-client.toml` (also in the party GUI's options): party frame style, anchor, scale, `portrait`
(`MODEL`: static full-body model with the teammate's skin, armor and held weapons; `FACE`), `hunger` (`AUTO` hides the
hunger bar when HungerStrike is installed), `markers` (teammates' heads, names and distance in the world, through walls)
and `markerMinDistance`. The Casket screen reopens on the last category, sort mode and left pane
(Gear & Curios / 3x3 Craft / Echoes) while `rememberLastTab` is on.

## Tags

| Tag | Effect |
|---|---|
| `#soulsvault:rest_points` (blocks) | Shift + right-click rests and opens the Casket |
| `#soulsvault:vault_blacklist` (items) | can never be stored (default: shulker boxes, bundles, common backpacks) |
| `#soulsvault:category/<tab>` (items) | forces a tab: `weapons`, `ranged_ammo`, `armor`, `curios_relics`, `consumables`, `materials`, `key_lore` |
| `#soulsvault:consumables`, `#soulsvault:key_items` (items) | older shortcuts for those two tabs |
| `#soulsvault:bosses` (entity types) | counts as a boss for the combat lock (add Cataclysm etc. here) |

## Datapack files

**Rest Point profiles**: `data/<ns>/soulsvault/rest_points/<name>.json`

```json
{
  "blocks": ["minecraft:soul_campfire", "#mypack:bonfires"],
  "priority": 10,
  "requires_lit": true,
  "reach": 6.0,
  "vault_access": true,
  "replenish": true,
  "echo_swap": true,
  "set_spawn": true,
  "heal": true,
  "feed": false,
  "clear_harmful_effects": true
}
```

The highest-priority profile matching a block wins. `vault_access`, `replenish` and `echo_swap` decide whether a
Casket opened there counts as "at a Rest Point" for each action (otherwise the remote rules and
`restPoints.*RequiresRestPoint` apply). The resting effects run on Shift + right-click, unless combat-locked.
Defaults shipped: campfires (must be lit) and the Bottomless Casket. Blocks in `#soulsvault:rest_points` with no
profile get all three powers and no effects.

**Replenish caps** (Estus-style): `data/<ns>/soulsvault/replenish_caps/<name>.json`

```json
{ "items": ["minecraft:golden_apple", "#forge:potions"], "max_carried": 10 }
```

All matching items share the cap. Resting never refills past it; picking up or crafting more is still allowed.

**Equip Load weights**: `data/<ns>/soulsvault/equip_load/<name>.json`

```json
{ "entries": [ { "item": "minecraft:netherite_chestplate", "weight": 15.0 },
               { "tag": "minecraft:swords", "weight": 4.0 } ] }
```

Items without an entry use Epic Fight's weight, then a value derived from armor / attack stats. Capacity is
`equipLoad.baseCapacity` plus the `soulsvault:equip_load_bonus` attribute, so any attribute modifier raises it.

## Casket Attunement (progression gate)

With `vault.requireAttunement = true` a player's Casket starts sealed: the Casket screen still opens (inventory,
gear, crafting), but storage, Rest & Replenish, Build Echo swaps, pickup overflow and Vestige Moths are refused with
"The Casket does not know you yet". Players attune by using a **Casket Sigil** (`soulsvault:casket_sigil`; override
its recipe in a datapack to fit your progression) or through `/casket attune <players>` (e.g. an FTB Quests command
reward). Caskets saved before the option was turned on stay attuned, and creative players are never sealed.

### Milestone advancements

Granted from code, shown in their own "Casket of Echoes" advancement tab, and usable as FTB Quests advancement tasks:

| Advancement | Granted when |
|---|---|
| `soulsvault:attuned` | the player attunes |
| `soulsvault:rested` | Rest & Replenish runs (Rest Point, button or waking up) |
| `soulsvault:refill_pinned` | an item gets auto-refill or overflow-deposit turned on |
| `soulsvault:overflow` | a world pickup overflows into the Casket |
| `soulsvault:echo_saved` / `soulsvault:echo_swapped` | a Build Echo is saved / changed into |
| `soulsvault:echo_slot_added` | an Echo Pouch is used |
| `soulsvault:spectral_casket` | a Vestige Moth summons a Spectral Casket |
| `soulsvault:casket_expanded` | a Reliquary Shard is used |
| `soulsvault:covenant_joined` | the player joins a Covenant Casket |
| `soulsvault:keepsakes_unlocked` / `soulsvault:keepsakes_upgraded` | a Keepsake Coffer unlocks / deepens the Keepsakes |

## Keepsakes (private stash)

The **Keepsakes** button in the Vault pane switches to a small stash that belongs to the player alone, even while
their party shares a Covenant Casket: for quest tokens, trade keys and anything they don't want others to take.
It holds `keepsakes.slots` kinds of items, each up to `baseStacks` stacks (128 of a 64-stack item at 2), and a
second **Keepsake Coffer** (`soulsvault:keepsake_coffer`) deepens it to `upgradedStacks`. Access follows the Vault's
rules: attunement, Rest Points or the remote cost, and the combat lock. Shift-clicking an item while the Keepsakes
view is open stores it there; Rest & Replenish, overflow and Deposit Category never touch it.

## Inventory sorting

The **Sort** button above the Casket screen's inventory sorts the 27 main slots only (never the hotbar, gear,
crafting grid or Vault). With Quark installed and its Inventory Sorting module on, it uses Quark's own sorting.

## Covenant Caskets (shared party storage)

- Each member chooses what happens to their own Casket before using the Covenant: merge, empty first, or override.
  Members whose Casket is empty join automatically. Joins made in FTB Teams, OPAC or `/team` get the same prompt.
- Items are owned by whoever deposited them; a withdrawal uses the withdrawer's own items first, then everyone
  else's in proportion. Leaving returns what you still own (`returnContributionsOnLeave`); disbanding, turning
  sharing off, or the party vanishing in another mod returns everything to everyone, online or offline.
- The Covenant has its own Reliquary Shard level. Members who belonged for `upgradeCreditMinutes` when a shard was
  used get their own Casket raised to that level when they leave.

## Events (Forge bus, server side)

| Event | Use |
|---|---|
| `com.soulsvault.forge.api.event.VaultAccessEvent` | cancelable; `getType()` is `OPEN`, `VAULT`, `REST` or `ECHO_SWAP`; `deny(Component)` |
| `com.soulsvault.forge.api.event.RestReplenishEvent$Pre` | cancelable; skip a rest |
| `com.soulsvault.forge.api.event.RestReplenishEvent$Refill` | per item; `setAmount(int)` |
| `com.soulsvault.forge.api.event.RestReplenishEvent$Post` | what was swept / refilled |

KubeJS (Forge) example, no Casket inside the Nether:

```js
ForgeEvents.onEvent('com.soulsvault.forge.api.event.VaultAccessEvent', e => {
  if (String(e.type) !== 'OPEN' && String(e.entity.level.dimension) === 'minecraft:the_nether') {
    e.deny(Text.of('The Casket will not answer here.'))
  }
})
```

## Java API

`com.soulsvault.api.SoulsVaultApi`: `getVault(player)`, `deposit(player, stack)`, `getAccessDenialReason(player)`,
`isAttuned(player)`, `setAttuned(player, attuned)`, and `registerEchoSlotType(EchoSlotType)` to add Build Echo slot groups (quivers, spell pouches, belts).

## Commands

`/casket attune <players> [true|false]` (permission level 2) attunes players, or seals their Casket again.

`/casket party` (GUI), `create`, `invite <player> [merge|empty_first|override]`, `accept [policy] [confirm]`,
`decline`, `leave`, `kick <member>`, `transfer <member>`, `disband`, `merge`.
