# HoeFarm

Server-side Fabric mod for Minecraft **26.2**. Right-click a field with a hoe: it harvests the ripe crops,
replants them, tills the soil and plants seeds — in one click. Shift-right-click keeps vanilla behaviour
(one block). Vanilla clients need nothing installed.

## Tiers

| hoe | area | config |
|---|---|---|
| iron | 3×3, one layer | `[1, 0, 0]` |
| diamond | 3×3, ±1 layer | `[1, 1, 1]` |
| netherite | 5×5, ±1 layer | `[2, 1, 1]` |

The config value is `[xz radius, layers up, layers down]`; radius 1 is a 3×3 square, radius 2 is 5×5.
A hoe that is not in the list (wood, stone, gold) falls through to vanilla.

## Per-block order (top to bottom)

1. ripe crop (`CropBlock`: wheat, carrots, potatoes, beetroot, torchflower; `NetherWartBlock`) — harvest via
   loot table and replant, taking one seed out of the drops (or the inventory);
2. dirt / grass / dirt path / coarse dirt / rooted dirt — **any grass, fern or snow layer standing on it is broken
   first**, then tilled (vanilla `HoeItem.TILLABLES` map: grass, path, dirt → farmland; coarse and rooted → dirt,
   rooted drops hanging roots);
3. empty farmland with air above — plant (the crop you clicked, or the first seed in your inventory).

Clicking the plant itself works too: if the ray hits grass, tall grass or a fern, the mod steps down to the block
under it (two steps for tall grass) and treats that as the centre of the area. That is what makes an overgrown
field farmable in one click instead of one hole at a time.

What counts as "can be broken": anything the game itself considers replaceable — short grass, tall grass, ferns,
snow layers, fire. Crops, saplings and flowers are **not** replaceable, so they are never destroyed.

It never tills under an existing crop, never touches immature crops, and never yanks a crop it cannot replant.

## Config

`config/hoefarm.json`, read once at startup:

| key | default | what |
|---|---|---|
| `tiers` | see above | item → `[radius, up, down]` |
| `harvest` / `replant` / `till` / `plant` | `true` | what to do |
| `bare_hand_harvest` | `false` | harvest a single crop with an empty hand |
| `sneak_does_vanilla` | `true` | Shift = one block |
| `durability_per_blocks` | `8` | one durability point per 8 blocks (minimum 1 per click) |
| `hunger_per_block` | `0.005` | exhaustion per block, like breaking a block |
| `blacklist` | `pitcher_crop` | blocks the mod ignores |

`pitcher_crop` is blacklisted because it is a two-block plant: replanting the lower half would leave the upper
half floating.

## Numbers

Iron hoe on a full 3×3 field: 2 durability, ~125 clicks. Netherite on a full 5×5×3 field: 10 durability,
~200 clicks. No XP is awarded — with auto-replant it would be an infinite fountain.

## Self-test

The mod ships a headless self-test (`-Dhoefarm.selftest=1`): it builds fields 1000 blocks from spawn, runs the
same handler through a `FakePlayer` and logs the result.

```
SELFTEST iron:        replanted=9/9  empty=0  durability=2
SELFTEST netherite:   lower=20/20  terrace=5/5  durability=4
SELFTEST till:        farmland=9/9  planted=9/9
SELFTEST grass-till:  farmland=9/9  planted=9/9  grass left=0  durability=3
SELFTEST event-order: replanted=9/9 (9 = hoefarm ran first)
```

`grass-till` is the overgrown field: a 3×3 of grass blocks with short grass and a column of two-block tall grass,
clicked on the **top half of the tall grass**.

## Credits

Harvest / replant logic is adapted from [Right Click Harvest](https://github.com/JamCoreModding/right-click-harvest)
by Jamalam, MIT licensed. Added here: vertical extent, tilling, planting, drops straight into the inventory,
durability per click instead of per block, one sound per action.

## Install

Fabric Loader ≥ 0.20.1 + Fabric API on the server, jar into `mods/`.

## Build

```
./gradlew build
```

## License

MIT.
