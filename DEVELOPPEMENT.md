# XPack Companion — développement

> Documentation technique du mod (en français). Le README présente le mod aux joueurs ; ce fichier explique comment il est construit. Les numéros (#34, #80…) renvoient aux issues du dépôt privé du site XPack, d’où vient l’historique de ce code.

Mod **client uniquement** qui envoie en temps réel tes progrès Minecraft à XPack et prévient avant toute triche. Les modpacks n'ont **jamais** à le déclarer comme dépendance :
- un serveur sans le mod accepte le joueur ;
- un modpack sans le mod fonctionne normalement.

| Loader | Minecraft | Jar |
|---|---|---|
| Fabric (sans Fabric API) | 1.21.1 | `fabric/build/libs/xpack-companion-fabric-1.21.1-<version>.jar` |
| NeoForge | 1.21.1 | `neoforge/build/libs/xpack-companion-neoforge-1.21.1-<version>.jar` |
| Forge | 1.20.1 (Java 17) | `forge/build/libs/xpack-companion-forge-1.20.1-<version>.jar` (remappé pour Forge : c'est celui-là qu'il faut distribuer, pas celui de `build/devlibs`) |

## Ce que fait le mod

- **Liaison du compte** (#34) : uniquement depuis les réglages (menu pause → ⚙, ou `/xpack settings`, aussi au menu principal). Le code s'y affiche avec **Ouvrir le site** (`/link?code=…` prérempli, le site demande « Lier le compte Minecraft *Steve* ? ») et **Copier le code**. Dans le chat, un seul rappel par lancement (« lie ton compte via ⚙ »), jamais de code ni de lien. Un code expiré n'est pas relancé tout seul.
- **Mondes de test** : un monde que le mod découvre en créatif (ou en spectateur, ou créé en mode créatif) est un monde de test. Le joueur peut aussi en déclarer un depuis les réglages. Dans ces mondes, **rien n'est envoyé et rien n'est signalé** : ni succès, ni pénalité. C'est définitif pour le monde concerné, et les autres mondes ne changent pas. En revanche, un monde déjà suivi qui passe en créatif reste suivi et devient « signalé ».
- **Bouton ⚙ dans le menu pause** : réglages, liaison, déliaison, monde de test, contacter le support.
- **Détection automatique sans blocage** : créatif, spectateur, invulnérabilité (le vol n'est pas détecté), mod interdit. Une notification reste 20 s : « Ce monde n'est plus comptabilisé », avec la raison et **[K] Contacter le support** (touche réglable avec `supportKey`). Le même message s'affiche dans le chat, avec un lien cliquable. Les commandes de triche gardent leur écran de confirmation.
- **Support** : le joueur voit ce qui part (détections, modpack, versions, liste des mods ; jamais le chat, le journal du jeu ni ses fichiers) et ajoute un message. La demande arrive par e-mail à l'équipe, et la réponse va à l'e-mail du compte XPack. Il faut un compte lié.

### Commandes (dans le chat, traitées par le mod, jamais envoyées au serveur)

| Commande | Rôle |
|---|---|
| `/xpack diagnostic` | Copie, anonymisés, les fichiers du lanceur qui décrivent le modpack dans `<instance>/xpack-diagnostic/diagnostic-<date>/`, avec la liste des mods et ce que le mod a détecté. Un lien du chat ouvre le dossier. C'est ce dossier qu'il faut envoyer pour les tests de détection |
| `/xpack check` | Pour chaque autre joueur connecté : mod XPack « installé et lié », « installé, compte non lié » ou « non détecté » (#126). Les succès d'équipe ne sont vérifiés que si tout le groupe a le mod lié ; un rappel s'affiche une fois par jour quand on joue à plusieurs, et à chaque équipe rejointe |
| `/xpack support` | Écran de contact du support |
| `/xpack settings` | Réglages du mod |
| `/xpack` ou `/xpack help` | Aide : une ligne par commande, cliquable. Les commandes sont proposées en suggestion pendant la saisie, comme celles des autres mods (ajoutées à l'arbre de commandes du client) |
- **Les progrès du modpack sont les succès** (#80) : le mod envoie l'arbre des progrès tel que le jeu l'affiche (titres et descriptions du jeu et des mods, dans la langue du joueur). En solo, c'est l'arbre complet (progrès cachés compris) ; en multijoueur, ce que le serveur a rendu visible. Points : progrès 5, objectif 10, défi 25.
- **Progrès en temps réel** (#35) : lus dans les paquets que le serveur envoie au client, donc aussi sur un serveur distant sans mod. Le premier paquet (état complet) est envoyé comme « instantané », non vérifié.
- **Speedrun** (désactivé par défaut) : option à activer dans les réglages (bouton « Speedrun… ») ou par `/xpack speedrun`. Tant qu'elle est désactivée, le mod n'affiche aucun chrono, n'écoute aucune touche et ne lance aucune run. Une fois activée, un nouveau monde en solo démarre le chrono selon le mode choisi (tout auto, fin auto, début auto, tout manuel) ; un monde déjà ouvert n'est pas concerné. Seule la run « tout auto » peut être classée ; les autres sont des chronos libres, exportables (JSON, CSV, LiveSplit, texte pour speedrun.com).
- **Statistiques** (#36) : demandées au serveur toutes les 60 s, seulement celles utilisées par les succès du modpack.
- **File d'envoi** (#37) : lots toutes les 5 s, reprise en cas de coupure, événements non envoyés sauvegardés dans le monde.
- **Identification du modpack** (#38) : `modrinth.index.json`, `minecraftinstance.json` (CurseForge), `manifest.json`, sinon empreinte de la liste de mods.
- **Anti-triche** (#43, #44, #47, #48) :
  - alerte avant toute commande de triche, y compris F3+F4 ;
  - détection du mode créatif ou spectateur, de l'invulnérabilité et du vol en survie ;
  - un monde signalé le reste, et le serveur fait foi.
- **Toast** « Succès XPack » (#39) et messages en français et en anglais.
- **Son des succès** (#59), joué avec le toast. Quatre sons au choix (gourmand, carillon, cloche, fanfare) ou coupé, avec un volume réglable. Plus le succès est rare, plus le son est marqué. Ce sont des sons du jeu : ils marchent aussi sur Fabric sans Fabric API. Le bouton **▶** des réglages fait écouter chaque rareté, de commune à légendaire.

## Structure

```
common/    Logique pure Java, sans Minecraft : règles, intégrité, file, API, détection du modpack. Tests JUnit.
           Compilée en Java 21 pour 1.21.1 et en Java 17 pour Forge 1.20.1.
shared/    Code Minecraft vanilla (noms Mojang) commun à tous les loaders : mixins, écrans, toasts.
           src/main = commun · src/mc1211 = 1.21.1 · src/mc1201 = 1.20.1 (dont Compat.java : les quelques appels qui changent)
fabric/    Point d'entrée Fabric (+ run « smoke » pour le test de bout en bout).
neoforge/  Point d'entrée NeoForge.
forge/     Point d'entrée Forge 1.20.1 (ModDevGradle « legacyforge » : remappage SRG et refmap des mixins).
legacy/    Code commun des ports 1.12.2 et 1.7.10 (Java 8, package net.xpack.companion.legacy) : écrans, textes, affichage, suggestions du chat.
           Compilé par chacun des deux projets (srcDir dans leur build.gradle). Ce qui dépend de l'API du jeu passe par deux classes
           qui existent sous le même nom dans chaque port : `Platform` (pont vers Client/Sounds/Toasts) et `LegacyGuiScreen` (base des écrans).
           Une correction d'un écran ou d'un texte se fait donc une seule fois ici ; `Client*`, `Toasts*`, `Sounds*`, `PacketSpy`, `XPackCommand` restent par version.
```

Les règles anti-triche viennent de `spec/integrity-rules.json`, le même fichier que le site et l’API XPack (copie synchronisée depuis le dépôt du site). `spec/launcher-fixtures/` contient des fichiers réels de lanceurs pour les tests de détection du modpack.

## Commandes

```bash
./gradlew :common:test          # tests unitaires du cœur
./gradlew build                 # jars Fabric et NeoForge (1.21.1) et Forge (1.20.1)
./gradlew :forge:build          # jar Forge 1.20.1 seul (Windows : .\gradlew.bat :forge:build)
```

> **Attention : `./gradlew build` ne construit pas 1.12.2 ni 1.7.10.** `forge1122/` et `forge1710/` sont des projets Gradle à part (sections plus bas) : sans les reconstruire, leurs jars locaux restent ceux de la version précédente (#209). Pour tester une version, prendre les 5 jars du workflow « Publier le mod » (artefacts) ou de la release GitHub, ou lancer `./gradlew build` dans chacun des deux dossiers.

### Test de bout en bout

Le test de bout en bout (`smoke_test.py`) vit dans le dépôt du site XPack, car il démarre l’API XPack (privée) :
1. démarre l’API ;
2. lie un joueur de test ;
3. génère un monde avec le serveur officiel ;
4. lance le client Fabric sans écran (`xvfb-run`, rendu logiciel Mesa) avec `-Dxpack.smoke=true`.

Le mod tape alors `/give`, vérifie l'écran d'alerte, confirme, s'accorde deux progrès puis quitte. Le script vérifie ensuite, côté API, la session, les événements, le statut « signalé » et le succès débloqué.

Prérequis : Java 21, Node 20+, `xvfb-run`, Mesa, et `pip install nbtlib`.

### Test en jeu en CI (sans écran, hors ligne)

Le workflow `.github/workflows/runtime-test.yml` ([MC-Runtime-Test](https://github.com/headlesshq/mc-runtime-test) 5.0.0 + HeadlessMC) lance le vrai client avec le jar du mod, pour Fabric 1.21.1, NeoForge 1.21.1 et Forge 1.20.1. Il n'a besoin ni de compte ni de l'API XPack. Le client rejoint un monde solo, puis avec `-Dxpack.smoke.ci=true` le mod accorde `minecraft:story/root` via le serveur intégré et écrit `XPACK SMOKE OK` dans `run/logs/latest.log` quand le mixin le détecte (code dans `SmokeTest`, inactif sans la propriété). Le workflow échoue si le jeu plante ou si cette ligne manque ; les journaux sont publiés en artefact. 1.12.2 et 1.7.10 ne sont pas couverts.

## Configuration (`config/xpack.json`)

| Clé | Rôle |
|---|---|
| `apiUrl` | Adresse de l'API XPack |
| `linkUrl` | Page du site où saisir le code de liaison |
| `consented` | Réponse à l'écran de consentement du premier lancement (absent = pas encore demandé ; rien n'est envoyé sans `true`) |
| `enabled` | Coupe tout envoi sans délier le compte |
| `toasts` | Notifications de succès en jeu |
| `badge` | Pastille d'état XPack dans le menu pause (comptabilisé / hors-ligne / non comptabilisé) |
| `eventsApi` | API d'événements locale pour les autres mods (`false` par défaut ; voir « API d'événements » plus bas). Se règle à la main dans le fichier |
| `sound` | Son des succès : `gourmand` (défaut), `carillon`, `cloche`, `fanfare` ou `off` |
| `soundVolume` | Volume de ce son, de 0 à 100 (défaut 80), en plus du volume général du jeu |
| `supportKey` | Touche qui ouvre le support pendant une alerte (`key.keyboard.k` par défaut ; J est pris par JourneyMap) |

Ces réglages sont aussi modifiables en jeu : bouton **⚙** du menu pause, bouton **Configurer** de la liste des mods sur NeoForge, ou Mod Menu sur Fabric (facultatif).

### Journal

Les échecs sans conséquence pour le joueur (fichier illisible, disque indisponible, API injoignable) laissent une ligne dans le journal du jeu, via `XLog` (`common`, `java.util.logging`, logger `xpack`). Jamais de jeton, de contenu de fichier, de corps de requête ni de dossier personnel : le nom de l'exception et un message court, un même message une fois par minute. Un loader peut rediriger ces lignes avec `XLog.setSink`.

### API d'événements (package `net.xpack.companion.api`)

Les autres mods du même jeu peuvent s'abonner à ce que XPack affiche déjà au joueur, sans rien envoyer de plus au serveur : tout reste local. Point d'entrée : `XPackEvents` (`register`, `unregister`, `enabled`) et l'interface `XPackEvents.Listener` (méthodes `default` vides).

| Méthode | Quand | Champs |
|---|---|---|
| `onAchievementUnlocked` | une réponse du serveur contient un succès débloqué | `name`, `points`, `rarity`, `item`, `percent`, `difficulty`, `quest` (ceux du toast) |
| `onMilestone` | palier 25, 50 ou 75 % | `modpackName`, `tier`, `achievements`, `total` |
| `onModpackCompleted` | 100 % | `modpackName`, `achievements`, `playtimeTicks` |
| `onWorldTracking` | le suivi du monde change | `tracked`, `reason` : `joined` (entrée), `practice` (monde de test), `flagged` (monde signalé, ne compte plus), `left` (sortie) |

- **Option** : `eventsApi` dans `config/xpack.json`, `false` par défaut. Tant qu'elle est coupée (ou sans écouteur), la diffusion ne fait rien. Le cœur la relit à chaque événement (`XPackEvents.bind`), donc un changement de configuration s'applique tout de suite.
- **Thread** : le thread du jeu, comme le reste du cœur (`XPackCore`). Les succès, paliers et le 100 % partent dès l'arrivée de la réponse du lot, **même si les toasts sont coupés** et sans le délai des toasts.
- **Isolation** : chaque appel d'écouteur est protégé par `try/catch` ; l'exception est journalisée une fois (`System.err`, `common` n'a pas de logger propre) et n'empêche ni les autres écouteurs, ni le toast, ni le jeu.
- **Rien de personnel** : ni UUID, ni pseudo, ni jeton, ni identifiant de monde ou de session, ni liste de mods, ni rien qui ne soit déjà dans un toast ou un message. Toute nouvelle donnée envoyée au serveur doit d'abord être discutée dans une issue (voir `CONTRIBUTING.md`) ; cette API n'en ajoute aucune.
- **Dépendance facultative** : les mods clients déclarent XPack Companion en `compileOnly` et vérifient sa présence avant tout appel.
- **Stabilité** : on n'enlève et on ne renomme ni classe, ni champ, ni méthode de ce package sans changement de version majeure. On peut ajouter des champs, des événements et des méthodes `default`. Les valeurs de `reason` documentées ne changent pas de sens.
- `XPackEvents.bind`, `dispatch` et `clearListeners` sont publics parce que le cœur est dans un autre package : ils sont réservés à XPack (la documentation le dit), les autres mods n'ont à utiliser que `register`, `unregister` et `enabled`.

### Jetons : jamais dans l'instance

Le jeton de liaison **n'est pas** dans `config/xpack.json`. Le dossier `config/` part avec les exports de modpack (CurseForge, Modrinth, Prism), les archives d'instance partagées et les dépôts Git des créateurs : un jeton rangé là finirait publié, et n'importe qui pourrait envoyer de faux progrès au nom du joueur. Il est donc rangé dans le dossier de l'utilisateur, avec un jeton par serveur XPack et par compte Minecraft. Toutes les instances le partagent : on ne lie son compte qu'une fois.

| Système | Fichier |
|---|---|
| Windows | `%APPDATA%\XPack\credentials.json` |
| macOS | `~/Library/Application Support/XPack/credentials.json` |
| Linux | `$XDG_CONFIG_HOME/xpack/credentials.json` (sinon `~/.config/xpack/`) |
| Autre emplacement | `-Dxpack.credentials=<fichier>` (tests, installations portables) |

Un jeton laissé dans `config/xpack.json` par une ancienne version est déplacé automatiquement au démarrage.

L'état multijoueur (identifiant de monde et événements en attente, un `<empreinte>.json` par serveur) est rangé dans le dossier `servers/` **à côté** de `credentials.json`, pas dans `config/xpack/servers/` (qui voyageait avec les exports). Les anciens fichiers sont déplacés une fois au démarrage (`ServerStore`). `/xpack diagnostic` ne copie des fichiers JSON du lanceur que des listes de champs (`ModpackDetector.WHITELIST` + `Diagnostics.EXTRA_KEYS`).

## Portage 1.12.2 (Forge) — en cours (#173)

`forge1122/` est un projet Gradle **à part** : ForgeGradle 2.3 impose Gradle 4.10 et Java 8, incompatibles avec le build principal (Gradle 8, Java 21).

```bash
cd forge1122
# JAVA_HOME doit pointer sur un JDK 8
./gradlew setupDecompWorkspace build    # la première fois : ~3 min (télécharge et décompile Minecraft)
```

Forge 1.12.2-14.23.5.2847 : c'est la dernière version dont le paquet `userdev` existe encore sur le Maven de Forge (les versions 2855 et 2860 n'en ont plus). Le mod tourne avec n'importe quel Forge 1.12.2 récent.

État : le squelette compile et produit un jar (`@Mod` client uniquement), avec le cœur `common/` à l'intérieur.

- **Cœur en Java 8** : `common/` est compilé en `--release 8` (les tests restent en Java 21). Pas de `record`, de `switch` moderne, de `java.net.http` ni de `List.of` : les équivalents sont dans `J8.java`, et le transport HTTP passe par `HttpURLConnection`.
- **Gson 2.2.4** : c'est la version qu'embarque Minecraft 1.12.2. Pas de `JsonParser.parseString`, ni de `deepCopy` publique : utiliser `J8.parseJson` et `J8.copy`. Le projet 1.12.2 compile le cœur face à ce Gson, donc une API trop récente fait échouer son build.

- **Couche client 1.12.2** (`forge1122/src/main/java/.../forge1122/`) : sans Mixin. `PacketSpy` s'insère dans le pipeline Netty de la connexion et lit les paquets de progrès, de statistiques et de ramassage sans les modifier ; les commandes passent par `ClientChatEvent` de Forge, le reste par le tick client. Les statistiques 1.12.2 (`stat.mineBlock.minecraft.stone`) sont traduites au format commun par `LegacyStats`. Les textes sont les mêmes fichiers `lang/*.json` que les autres versions.
- **Quêtes BetterQuesting** (RLCraft, solo) : chaque quête du livre compte dans la jauge « Avancement » (pas dans les succès), lue dans les fichiers `betterquesting/` du monde par `QuestWatcher` (voir aussi la 1.7.10).
- **Fait** : progrès en temps réel et catalogue, statistiques, objets reçus, détection du mode de jeu, de la triche et du mode créatif, commandes `/xpack settings|check|diagnostic`, alerte avant une commande de triche (écran oui/non du jeu), consentement, toasts, écran de réglages (liaison du compte) et bouton XPack du menu pause.
- **Alignés sur les autres versions** : toasts (succès, paliers, alerte d'intégrité : mêmes couleurs, durées et regroupement ; un succès s'affiche tout de suite, sous le toast du jeu, paliers et alertes après lui), son des succès, badge d'état du menu pause, écran de réglages (envoi, toasts, badge, son, volume, écoute, liaison, monde de test, support) et écran de contact du support.
- **Pas encore fait** : écran Équipe, mode triche de JEI/FTB Quests, suivi des blocs minés pour la détection de x-ray.
- **Pas encore testé dans un vrai client 1.12.2** : le jar compile et se construit, le cœur tourne sous Java 8, mais aucune partie n'a été jouée.

## Portage 1.7.10 (Forge, GT: New Horizons)

`forge1710/` est un projet Gradle **à part**, avec [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle) (l'outil de la communauté GTNH pour la 1.7.10). Il demande **Gradle 9.2 lancé avec Java 25** ; le code est compilé en Java 8 (toolchain).

```bash
cd forge1710
# JAVA_HOME doit pointer sur un JDK 25 (pour Gradle) ; un JDK 8 doit être installé (compilation)
./gradlew build    # la première fois : ~2 min (télécharge et décompile Minecraft 1.7.10)
```

Même cœur que les autres versions (`common/`, Java 8, Gson 2.2) et mêmes textes. La couche client est adaptée à la 1.7.10 :
- **Pas de progrès** (advancements) : les succès XPack sont les *achievements* du jeu et des mods (GT: New Horizons en a des centaines), lus dans le paquet de statistiques ; le premier paquet d'une partie est l'état initial (non vérifié). Leur identifiant est `achievement:<nom du jeu>`.
- **Statistiques** : les identifiants numériques de la 1.7.10 (`stat.mineBlock.1`) sont traduits en noms (`minecraft:mined/minecraft:stone`) par `LegacyStats`.
- **Commandes de triche** : pas d'événement de chat en 1.7.10, la commande est retenue dans la connexion (`PacketSpy.Outbound`) avant l'envoi, puis renvoyée si le joueur confirme.
- **Toasts** : la 1.7.10 n'a pas de système de toasts (il date de la 1.12) : la pile et le glissement sont dessinés par le mod (`Toasts1710`), au même dessin que les autres versions.
- **Quêtes BetterQuesting** (solo) : comme en 1.12.2, jauge « Avancement » pour tous les modpacks (seuil de 300 progrès retiré le 04/11/2026). Format GTNH pris en charge : identifiants UUID, `QuestProgress/<uuid>.json`, livre envoyé en plusieurs parties.
- Textes, sons, badge du menu pause, réglages, support, `/xpack` avec complétion et suggestions : comme en 1.12.2.
- **Pas encore fait** : écran Équipe, mode triche de JEI/FTB Quests, suivi des blocs minés (x-ray).
- **Pas encore testé dans un vrai client 1.7.10** : le jar compile et se construit, mais aucune partie n'a été jouée.
