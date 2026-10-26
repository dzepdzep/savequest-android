# Save → RankedBoost

**In the app:** open the *RankedBoost* tab, tap Locations or Pokédex. RankedBoost opens inside SaveQuest and the panel already has your imported save and **auto-applies** it on every Gold/Silver map and the Pokédex (you can switch that off in the panel, or use Preview / Apply by hand). Only rankedboost.com is shown in that screen (other links open your browser), the page gets no way to call into the app, and the save is handed over once and removed from the page immediately. Progress ticked there is stored inside SaveQuest's own browser storage. This screen is why the app now has the Internet permission.

The same script also works as a plain **browser userscript** (below). `tools/rankedboost/savequest-rankedboost.user.js` ticks the **caught Pokémon** and the **item balls / hidden items** you already have in your Pokémon Silver save on RankedBoost's own Gold/Silver pages. It is the SaveQuest version of the JohtoAtlas "Save → RankedBoost" importer, and its data (item name, event flag, hidden/visible, RankedBoost page) is generated from SaveQuest's `catalog.json`, so the app and the script always agree.

## Use (Firefox on Android or PC)

1. Install Violentmonkey (or Tampermonkey). Open the raw file link in the app's Tips tab, or paste the file's contents into a new script.
2. Open a RankedBoost page in the **same browser**: `https://rankedboost.com/pokemon/pokedex/gold-silver/` or any location page such as `.../gold-silver/route-29/`.
3. In the "SaveQuest · Save → RankedBoost" panel pick your `.srm` / `.sav` battery save (not a savestate). Tap **Preview**, then **Apply to RankedBoost**.
4. Caught Pokémon are site-wide. Item ticks are per location page: visit the pages you want and Apply again, or switch on *auto-apply* to do it as you browse.

## Safety and limits

* The save is read in the browser only; nothing is uploaded. Only derived caught species and completed flag numbers are kept in that browser's localStorage (no trainer name, no save bytes).
* Existing RankedBoost ticks are never removed. A marker is ticked only when every game pickup behind it is finished and its hidden/visible kind agrees; anything unclear is skipped.
* Not synced: trainers, NPC gifts, shop purchases, and RankedBoost's separate map-pin checklists. The site can change its markup at any time; if buttons stop matching, the script skips them.
* RankedBoost progress lives per browser profile; Firefox progress does not appear in Chrome.

## What the live site looks like (checked 25 Oct 2026)

* Items: `button.v3d-it-detail__got-btn[data-v3d-toggle=<item-slug>]`, state in `aria-pressed`; stored in `rb_v3d_items_got_gold-silver_<location id>` = `{slug:true}`. Each item appears in a labelled desktop panel (`data-v3d-detail="slug|hidden|hidden|"`, `slug|visible|field|`, `slug|gift|gift|`, `slug|shop|buy|`) **and** in an unlabelled phone accordion copy; the labels arrive after load, so the script waits for them.
* Pokémon: `rb_pokemon_caught_gold-silver` = `{slug:1}`; both `true` and `1` display correctly. Pokémon slugs are the site's own (`farfetchd`, `nidoran-f`, `nidoran-m`, `mr-mime`, `ho-oh`).
* Known gaps (skipped, not guessed): Machine Part (Cerulean), several Goldenrod and Olivine City markers have no matching RankedBoost item.

## Maintaining

```
python tools/build_rankedboost_userscript.py          # rebuild after the catalog changes; writes the userscript AND the copy the app injects (test_catalog.py fails if either is stale)
node tools/rankedboost/test_userscript.js             # parsing + matching logic
python tools/rankedboost/test_userscript_browser.py   # end-to-end on a local copy of RankedBoost's button markup (needs playwright + Chromium)
```
