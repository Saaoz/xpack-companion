# XPack Companion

Le mod compagnon de **[XPack](https://xpack.saaoz.fr)** : chaque progrès que tu obtiens dans un modpack devient un succès sur ton profil XPack, avec des points, une rareté, des paliers de complétion et des classements.

**Mod client uniquement.** Les serveurs n'ont pas besoin du mod, et aucun modpack n'a à le déclarer comme dépendance : un serveur sans le mod t'accepte normalement, et un modpack sans lui fonctionne comme avant.

> Le code source du mod est ouvert, sous licence MIT. Pour le compiler ou contribuer : [CONTRIBUTING.md](CONTRIBUTING.md). Le site et l’API XPack restent privés.

## Ce que fait le mod

- **Tes progrès deviennent des succès.** Le mod lit l'arbre des progrès de ton modpack, avec les titres et descriptions que le jeu affiche (Minecraft et mods), dans ta langue. Tu les retrouves en arbre sur le site, onglet par mod, avec les vraies icônes du jeu.
- **Des toasts en jeu** pour chaque succès (points, rareté, part des joueurs qui l'ont) et pour les paliers de complétion à 25, 50, 75 et 100 %. Tu choisis le coin de l'écran et le délai, et un bouton « Essai » montre le rendu.
- **Un son qui fait plaisir.** Le son « Gourmand » (le ping de l'orbe d'expérience) devient plus riche quand le succès est rare, et monte d'un cran si tu en enchaînes plusieurs. Carillon, cloche et fanfare restent disponibles, ou coupe le son. Volume réglable.
- **Les quêtes dans une jauge à part.** Les quêtes FTB Quests (ATM10…) et BetterQuesting (RLCraft, GT: New Horizons) comptent dans une jauge « Avancement », séparée des points de succès.
- **La difficulté compte.** Chaque succès garde le badge de difficulté de ton monde (Paisible à Hardcore), avec un multiplicateur de points. Le mod te prévient quand tu la baisses.
- **Jouer en équipe.** Invite des coéquipiers depuis le jeu (`/xpack team`) : leurs succès portent le badge « Équipe » et ont leur propre classement. `/xpack check` montre qui a XPack dans la partie.
- **Fair-play sans punition.** Une commande de triche, le mode triche de JEI, REI ou EMI, ou le mode édition de FTB Quests demandent d'abord une confirmation. Un monde où tu triches arrête simplement de compter. Les mondes en créatif ou déclarés « mondes de test » ne sont jamais suivis, et personne n'est banni.
- **Un support intégré.** Depuis le jeu, tu vois ce qui part, tu ajoutes un message, et la demande arrive à l'équipe.

## Télécharger

| Loader | Minecraft | Fichier |
|---|---|---|
| Fabric (et Quilt) | 1.21.1 | `xpack-companion-fabric-1.21.1-<version>.jar` |
| NeoForge | 1.21.1 | `xpack-companion-neoforge-1.21.1-<version>.jar` |
| Forge | 1.20.1 | `xpack-companion-forge-1.20.1-<version>.jar` |
| Forge | 1.12.2 (RLCraft…) | `xpack-companion-forge-1.12.2-<version>.jar` |
| Forge | 1.7.10 (GT: New Horizons…) | `xpack-companion-forge-1.7.10-<version>.jar` |

Les fichiers sont dans **[Releases](https://github.com/Saaoz/xpack-companion/releases)**. Fabric n'a pas besoin de Fabric API.

Le plus simple : la page **[xpack.saaoz.fr/mod](https://xpack.saaoz.fr/mod)** choisit le bon fichier selon ton loader et ta version de Minecraft, et donne la marche à suivre pour ton lanceur (CurseForge, Modrinth App, Prism, ATLauncher, lanceur officiel).

## Installer

1. Télécharge le jar qui correspond à ton modpack.
2. Copie-le dans le dossier `mods` de l'instance de ton modpack.
3. Lance le jeu. Au premier lancement, un écran de consentement s'affiche : **rien n'est envoyé tant que tu ne l'as pas accepté.**
4. Ouvre le menu pause → ⚙ → « Lier mon compte XPack » (ou tape `/xpack settings`) : le mod affiche un code.
5. Saisis ce code sur [xpack.saaoz.fr/link](https://xpack.saaoz.fr/link). Tes succès arrivent ensuite sur ton profil.

**Crée un nouveau monde** pour collectionner tes succès : en solo, un monde commencé sans le mod ne compte pas (sur un serveur, il compte toujours).

> Dans l'app CurseForge, le bouton « Ajouter à un modpack » ne propose que les profils où « Autoriser la gestion du contenu de ce profil » est activé dans les options du profil. L'app ne vérifie pas le loader : choisis un profil du même loader et de la même version que le fichier.

## Commandes

Elles sont traitées par le mod et jamais envoyées au serveur. Elles se complètent avec Tab pendant la saisie.

| Commande | Rôle |
|---|---|
| `/xpack` | Aide |
| `/xpack settings` | Réglages du mod : envoi, toasts, badge, son, volume, liaison du compte, monde de test, support |
| `/xpack team` | Équipe XPack : inviter, accepter, quitter |
| `/xpack check` | Qui a XPack dans la partie |
| `/xpack support` | Contacter le support |
| `/xpack diagnostic` | Copie, anonymisés, les fichiers qui décrivent ton modpack, pour aider le support |

## Tes données

Rien n'est envoyé avant que tu acceptes l'écran de consentement. Une fois ton compte lié, le mod envoie à l'API XPack (`api.xpack.saaoz.fr`) :

- l'identité du modpack, lue dans une liste blanche de champs des fichiers du lanceur (nom, version, identifiants du projet et du fichier dans `modrinth.index.json`, `minecraftinstance.json`, `manifest.json`, `instance.cfg` de Prism et `instance.json` d'ATLauncher ; la liste exacte est la constante `ModpackDetector.WHITELIST`), et un identifiant de monde ; le serveur peut répondre si le modpack est reconnu, le mod l'affiche dans le badge de la pause, sans rien envoyer de plus ;
- tes progrès, tes statistiques et ton temps de jeu ;
- la liste des progrès du modpack (titres et descriptions du jeu) et quelques icônes d'objets, dessinées par le jeu, pour des objets que la plateforme connaît déjà ;
- la difficulté du monde et si d'autres joueurs sont connectés (oui ou non seulement) ;
- des informations techniques : version de Minecraft, loader, version du mod, nombre de mods et empreinte de leur liste ;
- des signaux d'intégrité contre la triche : mode de jeu, triche autorisée, invulnérabilité hors créatif, commandes de triche confirmées, provenance des objets de valeur reçus, packs de ressources ajoutés, compteurs de minage ;
- en multijoueur, des empreintes (SHA-256) des identifiants des joueurs pour savoir qui a XPack, jamais les identifiants ni les pseudos.

Le chat, le journal du jeu et tes fichiers ne sont jamais lus. La liste complète des mods n'est envoyée que si tu contactes le support depuis le mod. Ton jeton de liaison est rangé dans le dossier de ton compte système (`%APPDATA%\XPack\` sous Windows), jamais dans l'instance, pour ne pas partir avec un export de modpack.

Tu peux délier le mod, exporter tes données ou supprimer ton compte à tout moment depuis le site.

## Créateurs de modpacks

Tu peux inclure le mod dans ton pack : la page [/mod](https://xpack.saaoz.fr/mod#creators) donne un texte prêt à copier pour Modrinth et CurseForge. Le mod reste facultatif pour tes joueurs.

## Pour les développeurs de mods

Ton mod peut réagir à ce que XPack affiche déjà au joueur (succès débloqué, palier de 25, 50 ou 75 %, modpack terminé, monde suivi ou non) grâce à une API d'événements **locale** : rien ne quitte le PC et aucune donnée n'est envoyée au serveur de plus. Les événements ne contiennent ni identifiant du joueur, ni jeton, ni identifiant de monde, ni liste de mods.

```java
// Dépendance facultative : vérifie d'abord que le mod « xpack » est chargé
// (Fabric : FabricLoader.getInstance().isModLoaded("xpack") ; NeoForge/Forge : ModList.get().isLoaded("xpack")).
if (xpackLoaded) {
    XPackEvents.register(new XPackEvents.Listener() {
        @Override
        public void onAchievementUnlocked(XPackEvents.AchievementUnlocked e) {
            System.out.println(e.name + " (" + e.points + " pts, " + e.rarity + ")");
        }
    });
}
```

- Le joueur doit activer l'option `eventsApi` (`true`) dans `config/xpack.json` : elle est **désactivée par défaut**, et sans elle aucun écouteur n'est appelé.
- Déclare XPack Companion en `compileOnly` seulement, jamais en dépendance obligatoire (le mod est facultatif pour les joueurs). Les écouteurs sont appelés sur le thread du jeu.
- Détails et garanties de stabilité : [DEVELOPPEMENT.md](DEVELOPPEMENT.md).

## Code source et contributions

Le code du mod est ici, sous [licence MIT](LICENSE) : tu peux le lire, vérifier exactement ce qu’il envoie, le compiler toi-même et proposer des améliorations. Le guide est dans [CONTRIBUTING.md](CONTRIBUTING.md), et la documentation technique dans [DEVELOPPEMENT.md](DEVELOPPEMENT.md). Une faille de sécurité ? Lis [SECURITY.md](SECURITY.md) avant de publier quoi que ce soit.

## Un problème ?

Écris au support depuis [xpack.saaoz.fr](https://xpack.saaoz.fr), ou directement depuis le jeu avec `/xpack support`.

---

# English

**XPack Companion** links your Minecraft modpacks to [XPack](https://xpack.saaoz.fr): every advancement you earn becomes an achievement on your XPack profile, with points, rarity, completion tiers and rankings. It is **client-side only**: servers don't need it and no modpack has to declare it.

**Features**
- Your modpack's advancements become achievements, with the texts the game shows, in your language, and the real item icons on the site.
- In-game toasts for achievements and completion tiers (25 / 50 / 75 / 100 %), with a choice of screen corner and delay.
- A satisfying "Treat" sound, richer for rarer achievements and higher when you chain them. Other sounds or silence in the settings.
- FTB Quests and BetterQuesting quests count in a separate "Progress" gauge.
- Difficulty badge (Peaceful to Hardcore) with point multipliers, and a "Team" badge when you play with teammates (`/xpack team`).
- Fair play without punishment: cheat commands and the cheat modes of JEI, REI, EMI or FTB Quests ask for confirmation first, and a world where you cheat simply stops counting. Nobody is banned.

**Supported versions:** Fabric (and Quilt) 1.21.1, NeoForge 1.21.1, Forge 1.20.1, Forge 1.12.2, Forge 1.7.10. Download from [Releases](https://github.com/Saaoz/xpack-companion/releases) or let [xpack.saaoz.fr/mod](https://xpack.saaoz.fr/mod) pick the right file.

**Install:** drop the jar into your instance's `mods` folder, accept the consent screen at first launch, open the pause menu → ⚙ → “Link my XPack account” (or `/xpack settings`), and enter the code on [xpack.saaoz.fr/link](https://xpack.saaoz.fr/link). Create a new world to collect achievements: in singleplayer, a world started without the mod doesn't count.

**Your data:** nothing is sent before you accept the consent screen. Once linked, the mod sends your progress, statistics, the modpack's identity (a fixed whitelist of fields read from the launcher files, see `ModpackDetector.WHITELIST`; the server may answer whether it recognised the pack, shown in the pause badge) and advancement list, technical details and integrity signals to `api.xpack.saaoz.fr`. Chat, game logs and your files are never read. You can unlink, export your data or delete your account at any time on the website.

**For mod developers:** your mod can react to what XPack already shows the player (achievement unlocked, 25 / 50 / 75 % tiers, modpack completed, world tracked or not) through a **local** events API. Nothing leaves the PC and no extra data is sent to the server. Events carry no player ID, token, world ID or mod list.

```java
// Optional dependency: first check that the "xpack" mod is loaded
// (Fabric: FabricLoader.getInstance().isModLoaded("xpack"); NeoForge/Forge: ModList.get().isLoaded("xpack")).
if (xpackLoaded) {
    XPackEvents.register(new XPackEvents.Listener() {
        @Override
        public void onAchievementUnlocked(XPackEvents.AchievementUnlocked e) {
            System.out.println(e.name + " (" + e.points + " pts, " + e.rarity + ")");
        }
    });
}
```

The player must enable `eventsApi` (`true`) in `config/xpack.json`: it is **off by default**, and nothing is called without it. Depend on XPack Companion as `compileOnly` only, never as a required dependency. Listeners run on the game thread. See [DEVELOPPEMENT.md](DEVELOPPEMENT.md) for details and stability guarantees.

**Source code:** the mod is open source under the [MIT license](LICENSE). You can read exactly what it sends, build it yourself and contribute: see [CONTRIBUTING.md](CONTRIBUTING.md). Security issue? See [SECURITY.md](SECURITY.md). The XPack website and API remain closed source.

---

Licence : [MIT](LICENSE)
