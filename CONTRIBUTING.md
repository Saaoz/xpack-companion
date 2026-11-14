# Contributing to XPack Companion

*Français plus bas.*

Thanks for your interest! This repository holds the source code of the XPack Companion client mod. The XPack website and API are not open source; the mod talks to the public API at `api.xpack.saaoz.fr`.

## Build

| What | Command | Java |
|---|---|---|
| Core unit tests | `./gradlew :common:test` | 21 |
| Fabric + NeoForge 1.21.1, Forge 1.20.1 | `./gradlew build` | 21 |
| Forge 1.12.2 | `cd forge1122 && ./gradlew setupDecompWorkspace build` | 8 |
| Forge 1.7.10 | `cd forge1710 && ./gradlew build` | 25 for Gradle, 8 installed for compiling |

`./gradlew build` does **not** rebuild the 1.12.2 and 1.7.10 jars: they are separate Gradle projects. Details are in [DEVELOPPEMENT.md](DEVELOPPEMENT.md) (French).

`scripts/test-local.sh` runs all of the above in one go, plus checks that catch what a build does not: missing translation keys (a button would show `xpack.settings.x` as its label), mixin classes named in a `mixins.json` but missing (the game will not start), and leftover merge markers. Add `--jeu` to launch the real client and watch it for a crash, an error, a freeze or an option that cannot be used. On Windows, run it from Git Bash.

## Ground rules

These rules protect players (many of them are young) and keep the mod acceptable on Modrinth and CurseForge. A pull request that breaks one of them will not be merged.

- **No new data sent without discussion.** Open an issue first. Any change to what the mod sends must update the "Your data" section of the README in the same pull request.
- **No new personal data** (real name, birth date, chat, logs, files…).
- **Prevent rather than punish.** The anti-cheat only records signals and asks for confirmation; it never bans, kicks or blocks the game. A new automatic signal must come with its false-positive story (quests, farms, chests, jetpacks…).
- **Speedrun features stay off by default**: no timer, keybind or HUD unless the player enables them.
- **Client-side only**: servers and modpacks must never need the mod.
- **Both languages**: every player-facing text goes into both `shared/src/main/resources/assets/xpack/lang/en_us.json` and `fr_fr.json`.
- A wrong name in a required mixin crashes the game at startup: say on which loaders you tested in game.

## Pull requests

1. Fork, create a branch, keep changes focused.
2. Run `scripts/test-local.sh` (it covers `./gradlew :common:test build` and the 1.12.2 / 1.7.10 builds when the matching JDKs are installed).
3. Describe what you tested, and on which loader and Minecraft version.

Security issues: do not open a public issue, see [SECURITY.md](SECURITY.md).

By contributing, you agree that your contributions are licensed under the [MIT license](LICENSE).

---

# Contribuer à XPack Companion

Merci de ton intérêt ! Ce dépôt contient le code source du mod client XPack Companion. Le site et l'API XPack ne sont pas open source ; le mod parle à l'API publique `api.xpack.saaoz.fr`.

## Compiler

| Quoi | Commande | Java |
|---|---|---|
| Tests du cœur | `./gradlew :common:test` | 21 |
| Fabric + NeoForge 1.21.1, Forge 1.20.1 | `./gradlew build` | 21 |
| Forge 1.12.2 | `cd forge1122 && ./gradlew setupDecompWorkspace build` | 8 |
| Forge 1.7.10 | `cd forge1710 && ./gradlew build` | 25 pour Gradle, 8 installé pour compiler |

`./gradlew build` ne reconstruit **pas** les jars 1.12.2 et 1.7.10 : ce sont des projets Gradle à part. Tous les détails sont dans [DEVELOPPEMENT.md](DEVELOPPEMENT.md).

`scripts/test-local.sh` enchaîne tout ce qui précède, plus ce qu'une compilation ne voit pas : clés de langue manquantes (le bouton afficherait `xpack.settings.x` au lieu de son libellé), classe de mixin citée par un `mixins.json` mais inexistante (le jeu ne démarre pas), marqueurs de fusion oubliés. Avec `--jeu`, il lance le vrai client et le surveille : crash, erreur, gel, option inutilisable. Sous Windows, le lancer depuis Git Bash.

## Règles

Elles protègent les joueurs (dont beaucoup sont jeunes) et gardent le mod acceptable sur Modrinth et CurseForge. Une PR qui en enfreint une ne sera pas fusionnée.

- **Aucune nouvelle donnée envoyée sans discussion.** Ouvre d'abord une issue. Tout changement de ce que le mod envoie met à jour la section « Tes données » du README dans la même PR.
- **Aucune nouvelle donnée personnelle** (nom, date de naissance, chat, journaux, fichiers…).
- **Prévenir plutôt que punir.** L'anti-triche enregistre des signaux et demande confirmation ; il ne bannit, n'expulse et ne bloque jamais le jeu. Tout nouveau signal automatique doit venir avec son histoire de faux positifs (quêtes, fermes, coffres, jetpacks…).
- **Le speedrun reste désactivé par défaut** : aucun chrono, aucune touche, aucun HUD tant que le joueur ne l'a pas activé.
- **Client uniquement** : ni les serveurs ni les modpacks ne doivent avoir besoin du mod.
- **Les deux langues** : chaque texte affiché aux joueurs va dans `shared/src/main/resources/assets/xpack/lang/fr_fr.json` et `en_us.json`.
- Un mixin obligatoire au nom faux fait planter le jeu au démarrage : indique sur quels loaders tu as testé en jeu.

## Pull requests

1. Fork, crée une branche, garde des changements ciblés.
2. Lance `scripts/test-local.sh` (il couvre `./gradlew :common:test build` et les builds 1.12.2 / 1.7.10 si les JDK voulus sont installés).
3. Décris ce que tu as testé, avec quel loader et quelle version de Minecraft.

Faille de sécurité : pas d'issue publique, voir [SECURITY.md](SECURITY.md).

En contribuant, tu acceptes que tes contributions soient publiées sous [licence MIT](LICENSE).
