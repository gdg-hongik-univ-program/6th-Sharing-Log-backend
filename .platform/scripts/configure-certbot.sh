#!/bin/bash
set -euo pipefail

readonly HTTPS_PUBLIC_HOST="${HTTPS_PUBLIC_HOST:?HTTPS_PUBLIC_HOST must be set}"
readonly CERTBOT_EMAIL="${CERTBOT_EMAIL:?CERTBOT_EMAIL must be set}"
readonly RENEWAL_HOOK="/etc/letsencrypt/renewal-hooks/deploy/50-reload-nginx.sh"

if [[ ! "$HTTPS_PUBLIC_HOST" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$ ]]; then
    echo "HTTPS_PUBLIC_HOST must be a lowercase hostname without a scheme: $HTTPS_PUBLIC_HOST" >&2
    exit 1
fi

if [[ ! "$CERTBOT_EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
    echo "CERTBOT_EMAIL must be a valid email address" >&2
    exit 1
fi

dnf install -y certbot python3-certbot-nginx

install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
cat > "$RENEWAL_HOOK" <<'EOF'
#!/bin/sh
systemctl reload nginx
EOF
chmod 0755 "$RENEWAL_HOOK"

# The proxy server has already been deployed when this hook runs. Certbot can therefore
# validate the public HTTP endpoint, then add or restore the HTTPS listener and redirect.
certbot --nginx \
    --non-interactive \
    --agree-tos \
    --email "$CERTBOT_EMAIL" \
    --redirect \
    --keep-until-expiring \
    -d "$HTTPS_PUBLIC_HOST"

nginx -t
systemctl reload nginx
