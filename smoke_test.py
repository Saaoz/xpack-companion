#!/usr/bin/env python3
"""Test de bout en bout du mod compagnon dans le vrai client Minecraft (Fabric 1.21.1).

1. démarre l'API Steamcraft (server/) sur une base PGlite jetable ;
2. crée un compte web et lie le joueur de test « SmokeTester » par le flux de code (#34) ;
3. génère un monde avec le serveur Minecraft officiel et y autorise les commandes ;
4. lance le client sans écran (xvfb) avec -Dsteamcraft.smoke=true : le mod tape /give,
   vérifie que l'alerte s'affiche, confirme, s'accorde deux progrès puis quitte ;
5. vérifie côté API : session ouverte, événements reçus, monde signalé, succès débloqué.

Prérequis : Java 21, Node 20+, xvfb-run, Mesa (rendu logiciel), et le paquet Python nbtlib
(pip install nbtlib). Usage : python3 mod/smoke_test.py
"""
import json
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD = ROOT / "mod"
SERVER = ROOT / "server"
PORT = 8790
API = f"http://127.0.0.1:{PORT}"
PLAYER = "SmokeTester"
RUN_DIR = MOD / "fabric" / "run-smoke"
SERVER_JAR = Path.home() / ".gradle/caches/fabric-loom/1.21.1/minecraft-server.jar"


def offline_uuid(name: str) -> str:
    """UUID d'un joueur hors-ligne, calculé comme Minecraft (UUID v3 de « OfflinePlayer:<nom> »)."""
    h = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode()).digest())
    h[6] = h[6] & 0x0F | 0x30
    h[8] = h[8] & 0x3F | 0x80
    return str(uuid.UUID(bytes=bytes(h)))


def http(method, path, body=None, token=None):
    req = urllib.request.Request(API + path, method=method, data=None if body is None else json.dumps(body).encode())
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return res.status, json.loads(res.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


def step(msg):
    print(f"\n=== {msg}", flush=True)


def start_api(work: Path) -> subprocess.Popen:
    step("API Steamcraft")
    subprocess.run(["npm", "run", "build"], cwd=SERVER, check=True, stdout=subprocess.DEVNULL)
    env = dict(os.environ, PORT=str(PORT), DATABASE_URL=f"pglite:{work / 'db'}")
    proc = subprocess.Popen(["node", "dist/index.js"], cwd=SERVER, env=env, stdout=open(work / "api.log", "w"), stderr=subprocess.STDOUT)
    for _ in range(60):
        try:
            if http("GET", "/v1/health")[0] == 200:
                print("API prête")
                return proc
        except OSError:
            pass
        time.sleep(1)
    raise SystemExit("l'API ne démarre pas (voir api.log)")


def link_player() -> tuple[str, str]:
    step("Compte web + liaison du mod")
    status, reg = http("POST", "/v1/auth/register", {"pseudo": "SmokeOwner", "password": "motdepasse123"})
    assert status == 201, reg
    web = reg["token"]
    mc_uuid = offline_uuid(PLAYER)
    _, start = http("POST", "/v1/link/start", {"minecraftUuid": mc_uuid, "minecraftName": PLAYER})
    status, _ = http("POST", "/v1/link/confirm", {"userCode": start["userCode"]}, web)
    assert status == 200
    status, poll = http("POST", "/v1/link/poll", {"deviceCode": start["deviceCode"]})
    assert status == 200, poll
    print(f"joueur {PLAYER} ({mc_uuid}) lié")
    return web, poll["token"]


def prepare_world(work: Path):
    step("Monde de test")
    world_src = work / "mcserver" / "SmokeWorld"
    cached = MOD / "build" / "smoke-world"
    if not cached.exists():
        srv = work / "mcserver"
        srv.mkdir(parents=True, exist_ok=True)
        (srv / "eula.txt").write_text("eula=true\n")
        (srv / "server.properties").write_text("level-name=SmokeWorld\nonline-mode=false\nlevel-seed=steamcraft\n")
        proc = subprocess.Popen(["java", "-Xmx2G", "-jar", str(SERVER_JAR), "--nogui"], cwd=srv, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in proc.stdout:
            if "Done (" in line:
                proc.stdin.write("stop\n")
                proc.stdin.flush()
        proc.wait(timeout=120)
        shutil.copytree(world_src, cached)
    saves = RUN_DIR / "saves" / "SmokeWorld"
    if saves.exists():
        shutil.rmtree(saves)
    shutil.copytree(cached, saves)
    # Monde solo « avec triches » : nécessaire pour que /advancement fonctionne une fois le monde signalé.
    import nbtlib

    level = nbtlib.load(saves / "level.dat")
    level["Data"]["allowCommands"] = nbtlib.Byte(1)
    level.save()
    print("monde prêt, commandes autorisées")


def write_config(mod_token: str):
    cfg = RUN_DIR / "config"
    cfg.mkdir(parents=True, exist_ok=True)
    (cfg / "steamcraft.json").write_text(json.dumps({
        "apiUrl": API,
        "linkUrl": "http://localhost:5173/link",
        "token": mod_token,
        "tokenUuid": offline_uuid(PLAYER).replace("-", ""),
        "consented": True,
        "enabled": True,
        "toasts": True,
    }, indent=2))
    # Pas de didacticiel ni d'écran d'accessibilité au premier lancement.
    (RUN_DIR / "options.txt").write_text("onboardAccessibility:false\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\n")


def run_client() -> str:
    step("Client Minecraft (xvfb, rendu logiciel)")
    env = dict(os.environ, LIBGL_ALWAYS_SOFTWARE="1")
    cmd = ["xvfb-run", "-a", "-s", "-screen 0 1280x720x24", "./gradlew", ":fabric:runSmoke", "--console=plain"]
    proc = subprocess.run(cmd, cwd=MOD, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=1500)
    log = proc.stdout
    (MOD / "build").mkdir(exist_ok=True)
    (MOD / "build" / "smoke-client.log").write_text(log)
    for line in log.splitlines():
        if "[SMOKE]" in line or "Mixin" in line and ("rror" in line or "fail" in line.lower()):
            print("  " + line.split("]: ", 1)[-1])
    return log


def check(web_token: str, log: str) -> bool:
    step("Vérifications")
    ok = True

    def expect(cond, label):
        nonlocal ok
        print(("  ✓ " if cond else "  ✗ ") + label)
        ok = ok and cond

    expect("[SMOKE] joined singleplayer world" in log, "le mod détecte l'entrée dans le monde")
    expect("[SMOKE] command 'give @s minecraft:diamond 64' allowed=false" in log, "la commande /give est retenue avant envoi")
    expect("[SMOKE] warning screen shown" in log, "l'écran d'alerte anti-triche s'affiche")
    expect("[SMOKE] integrity=SIGNALE" in log, "le monde est signalé après confirmation")
    expect("minecraft:story/root" in log and "[SMOKE] advancements done=" in log, "les progrès sont captés en temps réel")
    expect("[SMOKE] stats received=" in log, "les statistiques sont reçues après la demande du mod")
    status, export = http("GET", "/v1/me/export", token=web_token)
    expect(status == 200, "export des données du compte")
    worlds = export.get("worlds", [])
    expect(len(worlds) == 1 and worlds[0]["kind"] == "singleplayer", "une partie solo enregistrée côté serveur")
    expect(bool(worlds) and worlds[0]["status"] == "signale", "statut « signalé » côté serveur")
    types = sorted({e["type"] for e in export.get("events", [])})
    expect("integrity" in types and "advancement" in types, f"événements reçus : {types}")
    unlocks = export.get("unlocks", [])
    expect(any(u["achievement_id"].endswith("/premiers-pas") for u in unlocks), "succès « Premiers pas » débloqué")
    expect(all(not u["counts"] for u in unlocks), "succès obtenus après la triche : non comptabilisés")
    return ok


def main():
    work = Path(tempfile.mkdtemp(prefix="steamcraft-smoke-"))
    api = start_api(work)
    try:
        web, mod_token = link_player()
        prepare_world(work)
        write_config(mod_token)
        log = run_client()
        ok = check(web, log)
    finally:
        api.send_signal(signal.SIGTERM)
    print("\nRÉSULTAT :", "OK" if ok else "ÉCHEC", f"(journal complet : {MOD / 'build' / 'smoke-client.log'})")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
