#!/usr/bin/env bash
# Invoked over authenticated SSH by SshDeployment. No Microsoft credentials are accepted.
set -euo pipefail
umask 077

fail() { printf 'PingBypass: %s\n' "$*" >&2; exit 1; }

run_worker() {
    local session_relative=$1 username=$2 profile_id=$3 minecraft_version=$4 loader_version=$5 relay_port=$6
    local ketamine_root="$HOME/.local/share/ketamine-pingbypass"
    local session="$HOME/$session_relative"
    local launcher_name launcher_hash
    [[ "$relay_port" =~ ^[0-9]+$ && "$relay_port" -ge 1024 && "$relay_port" -le 65535 ]] \
        || fail 'Invalid private relay port.'
    case "$(uname -m)" in
        x86_64|amd64)
            launcher_name=headlessmc-launcher-linux-x64
            launcher_hash=ea9b71f06fa7daeea6a669fcc1d6d506ff3088086954f7a8ad8a1ef98a7b3b26 ;;
        aarch64|arm64)
            launcher_name=headlessmc-launcher-linux-arm64
            launcher_hash=3126cd54eed1972a6b6d07b24c372e9b17a5c10453b6716e6dfa3e463e52b15a ;;
        *) fail 'The VPS must run Linux on x86-64 or ARM64.' ;;
    esac
    local launcher="$ketamine_root/cache/$launcher_name-2.10.0"
    mkdir -p "$ketamine_root/cache/java" "$ketamine_root/cache/minecraft" "$session/HeadlessMC"
    if [[ ! -f "$launcher" ]] || ! printf '%s  %s\n' "$launcher_hash" "$launcher" | sha256sum --check --status; then
        printf 'PingBypass: downloading verified HeadlessMC 2.10.0...\n'
        curl --fail --location --silent --show-error --proto '=https' --tlsv1.2 \
            --connect-timeout 20 --max-time 300 --retry 2 \
            "https://github.com/headlesshq/headlessmc/releases/download/2.10.0/$launcher_name" \
            --output "$session/launcher.download"
        printf '%s  %s\n' "$launcher_hash" "$session/launcher.download" | sha256sum --check --status \
            || fail 'HeadlessMC checksum verification failed. No downloaded executable was started.'
        chmod 700 "$session/launcher.download"
        mv "$session/launcher.download" "$launcher"
    fi

    # Per-session game/config and shared immutable download caches. Paths in properties remain relative,
    # so usernames with spaces in their home directory do not break HMC's JVM argument parser.
    ln -s "$ketamine_root/cache/java" "$session/HeadlessMC/java"
    ln -s "$ketamine_root/cache/minecraft" "$session/minecraft"
    cat > "$session/HeadlessMC/config.properties" <<EOF
hmc.graal.java.version=25
hmc.auto.download.java=true
hmc.java.require.exact=true
hmc.mcdir=minecraft
hmc.gamedir=game
hmc.game.dir.for.each.version=false
hmc.offline=true
hmc.offline.username=$username
hmc.offline.uuid=${profile_id//-/}
hmc.offline.token=
hmc.store.accounts=false
hmc.account.refresh.on.game.launch=false
hmc.auto.download.specifics=false
hmc.jline.enabled=false
hmc.jline.enable.progressbar=false
hmc.filehandler.enabled=false
hmc.exit.on.failed.command=true
hmc.rethrow.launch.exceptions=true
hmc.dont.ask.for.quit=true
hmc.jvmargs=-Xms256m -Xmx2G @worker.jvmargs
EOF
    cat > "$session/game/worker.jvmargs" <<EOF
-Djava.awt.headless=true
-Dketamine.pingbypass.worker=true
-Dketamine.pingbypass.port=$relay_port
-Dketamine.pingbypass.secretFile=worker.secret
EOF
    cat > "$session/game/options.txt" <<'EOF'
onboardAccessibility:false
pauseOnLostFocus:false
renderDistance:8
simulationDistance:8
maxFps:60
enableVsync:false
soundCategory_master:0.0
EOF
    cd "$session"
    printf 'PingBypass: preparing Java 25 and Minecraft %s; the first connection downloads game files...\n' "$minecraft_version"
    if [[ ! -f "minecraft/versions/$minecraft_version/$minecraft_version.json" \
            || ! -f "minecraft/versions/$minecraft_version/$minecraft_version.jar" ]]; then
        "$launcher" --command download "$minecraft_version"
    fi
    if [[ ! -f "minecraft/versions/fabric-loader-$loader_version-$minecraft_version/fabric-loader-$loader_version-$minecraft_version.json" ]]; then
        "$launcher" --command fabric "$minecraft_version" --uid "$loader_version" --java 25
    fi
    printf 'PingBypass: starting the headless worker...\n'
    exec "$launcher" --command launch "fabric-loader-$loader_version-$minecraft_version" -lwjgl -offline
}

if [[ ${1:-} == --run ]]; then
    shift
    run_worker "$@"
    exit
fi

[[ $# == 6 ]] || fail 'Expected session directory, player name, UUID, Minecraft version, Fabric Loader version, and relay port.'
session_relative=$1
username=$2
profile_id=$3
minecraft_version=$4
loader_version=$5
relay_port=$6
[[ "$session_relative" =~ ^\.local/share/ketamine-pingbypass/sessions/[a-f0-9-]{36}$ ]] || fail 'Invalid session directory.'
[[ "$username" =~ ^[A-Za-z0-9_]{1,16}$ ]] || fail 'Invalid player name.'
[[ "$profile_id" =~ ^[a-fA-F0-9-]{36}$ ]] || fail 'Invalid player UUID.'
[[ "$minecraft_version" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || fail 'Invalid Minecraft version.'
[[ "$loader_version" =~ ^[0-9][A-Za-z0-9.+_-]{0,63}$ ]] || fail 'Invalid Fabric Loader version.'
[[ "$relay_port" =~ ^[0-9]+$ && "$relay_port" -ge 1024 && "$relay_port" -le 65535 ]] || fail 'Invalid relay port.'
ketamine_root="$HOME/.local/share/ketamine-pingbypass"
session="$HOME/$session_relative"
worker_pid=
watcher_pid=
cleanup() {
    local result=$?
    trap - EXIT HUP INT TERM
    if [[ -n "$watcher_pid" ]]; then kill "$watcher_pid" 2>/dev/null || true; fi
    if [[ -n "$worker_pid" ]]; then
        kill -TERM -- "-$worker_pid" 2>/dev/null || true
        # HeadlessMC may have a launcher, a JVM and a game subprocess: terminate the full process group.
        sleep 1
        kill -KILL -- "-$worker_pid" 2>/dev/null || true
        wait "$worker_pid" 2>/dev/null || true
    fi
    rm -rf -- "$session"
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP

[[ "$(uname -s)" == Linux ]] || fail 'The VPS must run Linux.'
for program in curl sha256sum setsid flock; do
    command -v "$program" >/dev/null || fail "Install $program on the VPS, then connect again (Debian/Ubuntu: sudo apt install curl coreutils util-linux)."
done

# One worker per SSH account avoids silently joining another session's listener or corrupting caches.
exec 9> "$ketamine_root/worker.lock"
flock -n 9 || fail 'Another PingBypass worker is running for this SSH account. Disconnect it first.'
IFS= read -r -t 20 secret || fail 'The local client closed before sending the session key.'
[[ "$secret" =~ ^[0-9a-f]{64}$ ]] || fail 'Invalid session key.'
printf '%s\n' "$secret" > "$session/game/worker.secret"
unset secret

# This SSH stdin belongs to the supervising shell, never to HeadlessMC. A vanished client stops
# downloads and the complete game process group even if the SSH TCP socket has not timed out yet.
exec 3<&0
supervisor_pid=$$
(
    while IFS= read -r -t 45 -u 3 heartbeat; do :; done
    kill -TERM "$supervisor_pid" 2>/dev/null || true
) &
watcher_pid=$!
setsid bash "$session/bootstrap.sh" --run "$session_relative" "$username" "$profile_id" \
    "$minecraft_version" "$loader_version" "$relay_port" < /dev/null &
worker_pid=$!
wait "$worker_pid"
