#!/usr/bin/env bash
set -euo pipefail

REPO="${REPO:-https://github.com/GrapeGG-developer/glyde.git}"
DIR=/opt/glyde
ENV_FILE=/etc/glyde.env
PORT="${PORT:-10000}"

if [ "$(id -u)" -ne 0 ]; then
  exec sudo -E bash "$0" "$@"
fi

IP="$(curl -4 -fsS https://api.ipify.org || curl -4 -fsS https://ifconfig.me)"
DOMAIN="${DOMAIN:-$(echo "$IP" | tr . -).sslip.io}"

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl git gnupg ca-certificates debian-keyring debian-archive-keyring apt-transport-https iptables-persistent

if ! command -v node >/dev/null || [ "$(node -p 'process.versions.node.split(".")[0]')" -lt 20 ]; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
fi

if ! command -v caddy >/dev/null; then
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y
  apt-get install -y caddy
fi

id glyde >/dev/null 2>&1 || useradd -r -m -d /var/lib/glyde -s /usr/sbin/nologin glyde

if [ -d "$DIR/.git" ]; then
  git -C "$DIR" pull -q --ff-only
else
  git clone -q --depth 1 --filter=blob:none --sparse "$REPO" "$DIR"
  git -C "$DIR" sparse-checkout set backend presets
fi
chown -R glyde:glyde "$DIR"

if [ ! -f "$ENV_FILE" ]; then
  cat > "$ENV_FILE" <<EOF
PORT=$PORT
GITHUB_REPO=GrapeGG-developer/glyde
TURSO_URL=
TURSO_TOKEN=
ADMIN_KEY=
ADMIN_LINK=
EOF
fi
chmod 600 "$ENV_FILE"

cat > /etc/systemd/system/glyde.service <<EOF
[Unit]
Description=Glyde API
After=network-online.target
Wants=network-online.target

[Service]
User=glyde
WorkingDirectory=$DIR/backend
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/node server.js
Restart=always
RestartSec=2
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF

cat > /usr/local/bin/glyde-update <<EOF
#!/usr/bin/env bash
cd $DIR || exit 0
old=\$(git rev-parse HEAD)
git fetch -q --depth 1 origin main && git reset -q --hard origin/main
[ "\$old" != "\$(git rev-parse HEAD)" ] && chown -R glyde:glyde $DIR && systemctl restart glyde
exit 0
EOF
chmod +x /usr/local/bin/glyde-update

cat > /etc/systemd/system/glyde-update.service <<EOF
[Unit]
Description=Glyde API auto update

[Service]
Type=oneshot
ExecStart=/usr/local/bin/glyde-update
EOF

cat > /etc/systemd/system/glyde-update.timer <<EOF
[Unit]
Description=Glyde API auto update

[Timer]
OnBootSec=2min
OnUnitActiveSec=5min

[Install]
WantedBy=timers.target
EOF

cat > /etc/caddy/Caddyfile <<EOF
$DOMAIN {
    encode gzip
    reverse_proxy 127.0.0.1:$PORT {
        flush_interval -1
    }
}
EOF

for p in 80 443; do
  iptables -C INPUT -p tcp --dport $p -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p tcp --dport $p -j ACCEPT
done
netfilter-persistent save >/dev/null 2>&1 || true

systemctl daemon-reload
systemctl enable --now glyde glyde-update.timer
systemctl restart glyde caddy

echo
echo "Server: https://$DOMAIN"
echo "Fill $ENV_FILE and run: systemctl restart glyde"
