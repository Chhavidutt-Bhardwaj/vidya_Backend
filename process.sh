#!/usr/bin/env bash
# =============================================================================
#  Vidya Backend — server process manager
#
#  Pulls from git, builds with Maven, deploys the jar as a systemd service,
#  health-checks it and rolls back automatically if the new build is unhealthy.
#
#  One-time setup on the server (as root, from inside the cloned repo):
#      sudo ./process.sh setup        # creates user, env file, systemd service + auto-deploy timer
#      sudo nano /etc/vidya/vidya.env # fill in DB / Redis / JWT / payment secrets
#      sudo ./process.sh deploy --force
#
#  Commands:
#      setup              Install service, env file template and auto-deploy timer
#      deploy [--force]   Pull; if there are new commits (or --force) build + deploy
#      start|stop|restart Control the application service
#      status             Service status, deployed commit and health
#      logs [-f]          Application logs (journald)
#      rollback           Switch back to the previous release
#      releases           List kept releases
#      auto-on|auto-off   Enable / disable the auto-deploy timer
#      uninstall          Remove the systemd units (keeps releases and env file)
#
#  Settings (override via environment or /etc/vidya/deploy.conf):
#      APP_NAME=vidya  BRANCH=main  APP_USER=vidya  APP_HOME=/opt/vidya
#      ENV_FILE=/etc/vidya/vidya.env  POLL_INTERVAL=2min  KEEP_RELEASES=5
#      RUN_TESTS=0  (1 = run unit tests during build; integration tests need Docker)
#      HEALTH_TIMEOUT=180  (seconds to wait for /actuator/health to report UP)
# =============================================================================
set -Eeuo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_PATH="$REPO_DIR/$(basename "${BASH_SOURCE[0]}")"

# shellcheck disable=SC1091
[[ -f /etc/vidya/deploy.conf ]] && source /etc/vidya/deploy.conf

APP_NAME="${APP_NAME:-vidya}"
BRANCH="${BRANCH:-main}"
APP_USER="${APP_USER:-vidya}"
APP_HOME="${APP_HOME:-/opt/vidya}"
ENV_FILE="${ENV_FILE:-/etc/vidya/vidya.env}"
POLL_INTERVAL="${POLL_INTERVAL:-2min}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
RUN_TESTS="${RUN_TESTS:-0}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

RELEASES_DIR="$APP_HOME/releases"
CURRENT_JAR="$APP_HOME/current.jar"
LOCK_FILE="/run/lock/${APP_NAME}-deploy.lock"
SERVICE="${APP_NAME}.service"
DEPLOY_SERVICE="${APP_NAME}-deploy.service"
DEPLOY_TIMER="${APP_NAME}-deploy.timer"

# ----------------------------------------------------------------------------- helpers
log()  { printf '[%s] %s\n' "$(date '+%F %T')" "$*"; }
warn() { log "WARN: $*" >&2; }
die()  { log "ERROR: $*" >&2; exit 1; }

require_root() { [[ $EUID -eq 0 ]] || die "Run as root: sudo $0 $*"; }

# Run a command as the app user (git + maven never run as root).
as_app() {
    if [[ $(id -un) == "$APP_USER" ]]; then "$@"; else runuser -u "$APP_USER" -- "$@"; fi
}

server_port() {
    local port=""
    [[ -r $ENV_FILE ]] && port=$(grep -E '^SERVER_PORT=' "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d '"'"'" || true)
    echo "${port:-8085}"
}

health_url() { echo "http://127.0.0.1:$(server_port)/actuator/health"; }

is_healthy() { curl -fsS --max-time 5 "$(health_url)" 2>/dev/null | grep -q '"status":"UP"'; }

wait_healthy() {
    local waited=0
    log "Waiting up to ${HEALTH_TIMEOUT}s for $(health_url) ..."
    while (( waited < HEALTH_TIMEOUT )); do
        if ! systemctl is-active --quiet "$SERVICE"; then
            warn "Service stopped while starting"; return 1
        fi
        is_healthy && { log "Application is UP"; return 0; }
        sleep 5; waited=$((waited + 5))
    done
    return 1
}

current_release() { [[ -L $CURRENT_JAR ]] && readlink -f "$CURRENT_JAR" || true; }

previous_release() {
    local current; current=$(current_release)
    ls -1t "$RELEASES_DIR"/*.jar 2>/dev/null | grep -vxF "${current:-/none}" | head -1 || true
}

switch_to() {   # atomically repoint current.jar
    ln -sfn "$1" "$CURRENT_JAR.tmp" && mv -Tf "$CURRENT_JAR.tmp" "$CURRENT_JAR"
    chown -h "$APP_USER:$APP_USER" "$CURRENT_JAR"
}

prune_releases() {
    local current; current=$(current_release)
    ls -1t "$RELEASES_DIR"/*.jar 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) | while read -r old; do
        [[ $old == "$current" ]] || { log "Pruning old release $(basename "$old")"; rm -f "$old"; }
    done
}

# ----------------------------------------------------------------------------- setup
cmd_setup() {
    require_root setup
    for bin in git curl systemctl flock runuser; do
        command -v "$bin" >/dev/null || die "'$bin' is required but not installed"
    done
    local java_bin; java_bin=$(command -v java || true)
    [[ -n $java_bin ]] || die "Java 25 is required (e.g. apt install openjdk-25-jdk-headless)"
    java_bin=$(readlink -f "$java_bin")
    local java_major
    java_major=$("$java_bin" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
    (( java_major >= 25 )) || die "Java 25+ required, found $java_major ($java_bin)"

    id -u "$APP_USER" &>/dev/null || {
        log "Creating system user $APP_USER"
        useradd --system --home-dir "$APP_HOME" --shell /usr/sbin/nologin "$APP_USER"
    }
    mkdir -p "$RELEASES_DIR" "$APP_HOME/logs" "$(dirname "$ENV_FILE")"
    chown -R "$APP_USER:$APP_USER" "$APP_HOME" "$REPO_DIR"
    chmod +x "$REPO_DIR/mvnw" "$SCRIPT_PATH"
    as_app git config --global --add safe.directory "$REPO_DIR" || true

    if [[ ! -f $ENV_FILE ]]; then
        log "Writing env template to $ENV_FILE (edit it before the first deploy!)"
        cat > "$ENV_FILE" <<'EOF'
# ---- Vidya backend runtime environment (read by systemd) ----
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8085
JAVA_OPTS=-Xms512m -Xmx1536m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8

# Master database (overrides the values hard-coded in application-prod.yml)
VIDYA_DATASOURCE_MASTER_URL=jdbc:postgresql://localhost:5432/vidya_master
VIDYA_DATASOURCE_MASTER_USERNAME=postgres
VIDYA_DATASOURCE_MASTER_PASSWORD=CHANGE_ME

# Admin connection used to create tenant databases
VIDYA_PROVISIONING_ADMIN_URL=jdbc:postgresql://localhost:5432/postgres
VIDYA_PROVISIONING_ADMIN_USERNAME=postgres
VIDYA_PROVISIONING_ADMIN_PASSWORD=CHANGE_ME
TENANT_DB_HOST=localhost
TENANT_DB_PORT=5432

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT — use a long random value: openssl rand -base64 64
VIDYA_SECURITY_JWT_SECRET=CHANGE_ME

# Payments
RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=
RAZORPAY_WEBHOOK_SECRET=
CASHFREE_APP_ID=
CASHFREE_SECRET_KEY=
EOF
    fi
    chown root:"$APP_USER" "$ENV_FILE"; chmod 640 "$ENV_FILE"

    log "Installing $SERVICE"
    cat > "/etc/systemd/system/$SERVICE" <<EOF
[Unit]
Description=Vidya Backend (Spring Boot)
After=network-online.target postgresql.service redis-server.service
Wants=network-online.target

[Service]
Type=simple
User=$APP_USER
Group=$APP_USER
WorkingDirectory=$APP_HOME
EnvironmentFile=$ENV_FILE
ExecStart=/bin/sh -c 'exec $java_bin \$JAVA_OPTS -jar $CURRENT_JAR'
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
TimeoutStopSec=60
LimitNOFILE=65536
NoNewPrivileges=true
PrivateTmp=true
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$APP_NAME

[Install]
WantedBy=multi-user.target
EOF

    log "Installing auto-deploy timer (every $POLL_INTERVAL)"
    cat > "/etc/systemd/system/$DEPLOY_SERVICE" <<EOF
[Unit]
Description=Vidya Backend — pull, build and deploy if git has new commits
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=$SCRIPT_PATH deploy
SyslogIdentifier=${APP_NAME}-deploy
EOF
    cat > "/etc/systemd/system/$DEPLOY_TIMER" <<EOF
[Unit]
Description=Poll git for Vidya Backend changes

[Timer]
OnBootSec=2min
OnUnitInactiveSec=$POLL_INTERVAL
Persistent=true

[Install]
WantedBy=timers.target
EOF

    systemctl daemon-reload
    systemctl enable "$SERVICE" >/dev/null
    systemctl enable --now "$DEPLOY_TIMER" >/dev/null
    log "Setup complete."
    log "Next: edit $ENV_FILE, then run: sudo $SCRIPT_PATH deploy --force"
}

# ----------------------------------------------------------------------------- deploy
cmd_deploy() {
    require_root deploy
    local force=0; [[ ${1:-} == "--force" ]] && force=1

    # Never run two deploys at once (timer + manual).
    mkdir -p "$(dirname "$LOCK_FILE")"
    exec 9>"$LOCK_FILE"
    flock -n 9 || { log "Another deploy is running — skipping"; return 0; }

    cd "$REPO_DIR"
    as_app git fetch --quiet origin "$BRANCH"
    local local_sha remote_sha
    local_sha=$(as_app git rev-parse HEAD)
    remote_sha=$(as_app git rev-parse "origin/$BRANCH")

    if [[ $local_sha == "$remote_sha" && $force -eq 0 && -e $CURRENT_JAR ]]; then
        return 0   # nothing new — stay quiet so the timer doesn't flood the journal
    fi

    if [[ $local_sha != "$remote_sha" ]]; then
        log "New commits on $BRANCH: ${local_sha:0:7} -> ${remote_sha:0:7}"
        as_app git checkout --quiet "$BRANCH"
        as_app git merge --ff-only --quiet "origin/$BRANCH" \
            || die "Cannot fast-forward $REPO_DIR (local changes on the server?). Fix the checkout manually."
    fi
    local sha; sha=$(as_app git rev-parse --short HEAD)
    log "Building commit $sha ($(as_app git log -1 --pretty=%s))"

    local mvn_args=(-B -q clean package)
    [[ $RUN_TESTS == 1 ]] || mvn_args+=(-DskipTests)
    as_app ./mvnw "${mvn_args[@]}" || die "Build failed for $sha — current release left running"

    local built
    built=$(ls -1 target/*.jar 2>/dev/null | grep -v '\.original$' | grep -v -- '-plain\.jar$' | head -1 || true)
    [[ -n $built ]] || die "Build produced no jar in target/"

    local release="$RELEASES_DIR/${APP_NAME}-$(date +%Y%m%d-%H%M%S)-${sha}.jar"
    install -o "$APP_USER" -g "$APP_USER" -m 644 "$built" "$release"
    local previous; previous=$(current_release)

    log "Deploying $(basename "$release")"
    switch_to "$release"
    systemctl restart "$SERVICE"

    if wait_healthy; then
        log "Deploy of $sha succeeded"
        prune_releases
        return 0
    fi

    warn "New release failed its health check — last log lines:"
    journalctl -u "$SERVICE" -n 40 --no-pager >&2 || true
    if [[ -n $previous && -f $previous ]]; then
        warn "Rolling back to $(basename "$previous")"
        switch_to "$previous"
        rm -f "$release"
        systemctl restart "$SERVICE"
        wait_healthy || warn "Previous release is not healthy either — check: $0 logs"
    fi
    die "Deploy of $sha failed"
}

# ----------------------------------------------------------------------------- other commands
cmd_rollback() {
    require_root rollback
    local prev; prev=$(previous_release)
    [[ -n $prev ]] || die "No previous release in $RELEASES_DIR"
    log "Rolling back to $(basename "$prev")"
    switch_to "$prev"
    systemctl restart "$SERVICE"
    wait_healthy || die "Rolled-back release is not healthy — check: $0 logs"
}

cmd_status() {
    systemctl --no-pager status "$SERVICE" | head -12 || true
    echo
    echo "Release : $(basename "$(current_release)" 2>/dev/null || echo none)"
    echo "Commit  : $(cd "$REPO_DIR" && git log -1 --pretty='%h %s (%cr)' 2>/dev/null)"
    echo "Timer   : $(systemctl is-active "$DEPLOY_TIMER" 2>/dev/null) ($(systemctl show "$DEPLOY_TIMER" -p NextElapseUSecRealtime --value 2>/dev/null))"
    if is_healthy; then echo "Health  : UP"; else echo "Health  : DOWN ($(health_url))"; fi
}

cmd_uninstall() {
    require_root uninstall
    systemctl disable --now "$DEPLOY_TIMER" "$SERVICE" 2>/dev/null || true
    rm -f "/etc/systemd/system/$SERVICE" "/etc/systemd/system/$DEPLOY_SERVICE" "/etc/systemd/system/$DEPLOY_TIMER"
    systemctl daemon-reload
    log "Removed systemd units. Kept $APP_HOME and $ENV_FILE."
}

usage() { sed -n '2,30p' "$SCRIPT_PATH" | sed 's/^# \{0,1\}//'; }

main() {
    local cmd="${1:-help}"; shift || true
    case "$cmd" in
        setup)     cmd_setup ;;
        deploy)    cmd_deploy "$@" ;;
        start)     require_root start;   systemctl start "$SERVICE";   wait_healthy ;;
        stop)      require_root stop;    systemctl stop "$SERVICE" ;;
        restart)   require_root restart; systemctl restart "$SERVICE"; wait_healthy ;;
        status)    cmd_status ;;
        logs)      journalctl -u "$SERVICE" --no-pager -n 200 "$@" ;;
        rollback)  cmd_rollback ;;
        releases)  ls -1t "$RELEASES_DIR"/*.jar 2>/dev/null || echo "No releases yet" ;;
        auto-on)   require_root auto-on;  systemctl enable --now "$DEPLOY_TIMER" ;;
        auto-off)  require_root auto-off; systemctl disable --now "$DEPLOY_TIMER" ;;
        uninstall) cmd_uninstall ;;
        help|-h|--help) usage ;;
        *) usage; die "Unknown command: $cmd" ;;
    esac
}

# Kept on one line: `git pull` may rewrite this file mid-run, and bash must not read further.
main "$@"; exit $?
