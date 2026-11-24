# Paid heroes rework — approved plan (Nov 24, 2026)

All visuals approved by the owner in the Moves Labs (claude.ai artifacts). **Game logic (damage, radius, number of targets, durations) stays the same unless listed below.** Labs: `labs/*_src.html` (build with `labs/build.py src glb out`; placeholders `__RAYAN__`/`__SAMI__` = hero glb, `__GOB__` goblin, `__HAM__` hammer, `__WING__` wing, `__WOLF__` wolf). Models with merged clips: `models/*_lab.glb` (already attr+slim'd, 512 textures). Game three.js is r128.

## Global
- Cartoon style: anticipation, hit-stop (~0.05–0.1 s), squash/stretch, enemies fly + stars, comic words, anime cut-in (radial lines) before big skills, speed lines.
- Phone vibration only for big skills (navigator.vibrate; Android VIBRATE permission) + small camera shake; add a settings toggle.
- Heroes that jump to monsters (Sami bicycle, Bruno slam, Nova swoop, Fenrik leap, Jay dunk) jump there, hit, **jump back home**, with `h.shieldT` (bubble) during the jump and return.
- Fix baked balls: Sami = remove small connected components (whole ball); Jay = cut sphere `[0,.64,.22,.17,.07]` + drop stretched triangles (edge > 3x bind length in any pose, see `dropStretched` in jay_src) + bridges between hand and body.
- Update hero portraits everywhere (hero cards, shop, Play store screenshots, website).
- App icon: trio (already committed on main: 5addcf9). Store 512 icon: `concepts/icon_trio_1024.png`.
- Ads: unchanged (every 3 levels after level 4).
- Fenrik/melee: see below.

## Per hero (approved)
- **Sami (goal)**: atk kickball; Power Shot = flick ball up, jump + spin twice with rainbow spiral, volley (alternate flykick/doublekick), fire comet with colored vortex rings, pierce line 8; Bicycle = flick up, jump toward nearest enemy within 4, backflip upside-down kick, shockwave radius 2 around landing, fly back; GOAL = gold ball + cut-in, explosion r 1.9, confetti, victory clip.
- **Rayan (archer)**: procedural bow in left hand, string pulls to right hand; arrows big gold-tipped; Arrow Rain 18 gold flaming arrows r 1.35, stick ~0.8 s; Piercing Shot giant arrow + gold vortex; Eagle procedural cartoon eagle (10 s in game).
- **Frosta (ice)**: ice shard bolt (splash r .5 slow); Nova r 2.8 spikes + ice blocks 2.5 s + shatter; Blizzard cloud r 1.8, 5 s; Prison = crystal pillars + shatter.
- **Ember (fire)**: flame bolt (splash .5); Fireball grows in hands, lob, boom r 1.1 + scorch; Fire Wall 5 columns on path, 5 s; Meteor Rain 5 rocks r .85, spread 1.3.
- **Bruno (thunder)**: NEW MODEL (engineer, mohawk, blue overalls) + separate crystal maul `hammer_o.glb`. **Becomes ranged: range 3.0, melee:false** (dmg/rate unchanged). Normal attack = quick throw (start clip near release), hammer spins, hits, vanishes, reappears in hand. Hammer Slam = leap to nearest within 4, r 2.2 stun, leap back. Thunder Storm 5 forked bolts. Chain throw up to 6, then vanish/appear.
- **Nova (laser)**: NEW MODEL (gold Sky Knight) + separate wing model `wing.glb` (two pivots, flap). Hover. Eye laser from visor. Swoop: rise, dive r 1.8 stun, fly back + shield. **Laser Spin replaced by Flying Punch Rush**: punch each enemy within 2.6 once (total ≈ old spin damage per enemy), enemies fly up and fall back.
- **Fenrik (wolfm)**: NEW MODEL (Wild Tamer, braids, orange fur). Wolves: leg shader needs a unique program cache key per material; legs move while attacking (lunge). Howl: spirit wolf + sound waves, wolves grow + fire aura for 6 s. Pack: 3 ghost wolves 10 s. **Wolf Leap becomes AoE: target 130 (was 170) + 1 s stun, others within 1.4 take 60 + 0.6 s stun. Armor 0.2 → 0.5.**
- **Jay (hoop)**: fiery basketball with thick black seams; dribble with elbow-hinge arm motion; 3-pointer with hoop + net, r 1.2; Bounce chain 5; Dunk = leap, r 1.9 stun 1.5, slow-mo, leap back + shield.
- **Twin (dragon)**: NOW RIGGED (was static) — model `twin_lab.glb` faces +Z, fire head at local x≈-0.3, ice head x≈+0.3. Quick spit alternates heads; Fire Breath cone 3.2/0.6 from fire head; Ice Breath freezes 3 s; Twin Storm = two orbs spiral around each other to the target, collide, tornado, r 1.8, frozen enemies take x2.

## Stages
1. Bug fixes players see: Sami/Jay ball distortion, jump-and-die heroes (return + shield), Twin static.
2. New models: Bruno, Nova, Fenrik (+ portraits).
3. Effects/moves for the rest, vibration, store/website images.
After each stage: in-game screenshots on several maps + perf check (Galaxy A15 class), show owner before any release.
