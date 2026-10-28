#!/usr/bin/env bash
# 一键启用 HTTPS
#
#   DOMAIN=agritime.example.com bash deploy/enable-https.sh
#   DOMAIN=agritime.example.com LE_EMAIL=you@example.com bash deploy/enable-https.sh
#
# 前提（缺一不可，否则申请证书一定失败）：
#   1. 域名已备案（中国大陆服务器未备案的域名访问会被云厂商拦截，返回 403）
#   2. 域名 A 记录已指向本机公网 IP，且生效（dig +short 域名 能查到本机 IP）
#   3. 华为云安全组放行 443，本机 ufw 也放行 443
#
# 脚本做的事：校验解析 → certbot 签发 → 在同一个 server 块上追加 443 监听 → 重载 → 验证续期
set -euo pipefail

DOMAIN="${DOMAIN:?请用 DOMAIN=你的域名 指定，例如 DOMAIN=agritime.example.com}"
LE_EMAIL="${LE_EMAIL:-}"
CONF="/etc/nginx/sites-enabled/agritime"
WEBROOT="/agritime/frontend/pages"

log() { printf '[https] %s\n' "$*"; }
die() { printf '[https] 失败：%s\n' "$*" >&2; exit 1; }

[ "$(id -u)" = "0" ] || die "需要 root 执行"
command -v certbot >/dev/null 2>&1 || die "没装 certbot：apt-get install -y certbot"

# ---------- 1. 校验域名解析 ----------
log "检查 $DOMAIN 是否解析到本机"
LOCAL_IP="$(curl -s -m 5 http://100.100.100.200/latest/meta-data/public-ipv4 2>/dev/null || curl -s -m 5 ifconfig.me || true)"
RESOLVED="$(getent hosts "$DOMAIN" | awk '{print $1}' | head -1)"
log "本机公网 IP: ${LOCAL_IP:-未知}    域名解析: ${RESOLVED:-未解析}"
[ -n "$RESOLVED" ] || die "$DOMAIN 没有解析记录"
if [ -n "$LOCAL_IP" ] && [ "$RESOLVED" != "$LOCAL_IP" ]; then
  die "$DOMAIN 解析到 $RESOLVED，与本机 $LOCAL_IP 不一致"
fi

# 备案校验：带域名 Host 头访问本机，应返回 200 而不是云厂商的 403 拦截页
CODE="$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $DOMAIN" "http://127.0.0.1/" || true)"
if [ "$CODE" = "403" ]; then
  die "带域名访问返回 403，通常是域名未备案被云厂商拦截；备案通过后再执行本脚本"
fi
log "域名访问返回 $CODE"

# ---------- 2. 申请证书 ----------
log "申请证书（Let's Encrypt，webroot 方式，不影响现有服务）"
if [ -n "$LE_EMAIL" ]; then
  AGREEMENT=(--agree-tos -m "$LE_EMAIL")
else
  AGREEMENT=(--agree-tos --register-unsafely-without-email)
fi
certbot certonly --webroot -w "$WEBROOT" -d "$DOMAIN" "${AGREEMENT[@]}" --non-interactive \
  || die "证书申请失败，检查 80 端口是否对公网开放、域名是否备案"

CERT_DIR="/etc/letsencrypt/live/$DOMAIN"
[ -f "$CERT_DIR/fullchain.pem" ] || die "证书文件不存在：$CERT_DIR"

# ---------- 3. 在同一个 server 块上追加 443 ----------
if grep -q "listen 443 ssl" "$CONF"; then
  log "配置里已有 443 监听，跳过改写"
else
  cp "$CONF" "/root/backups/nginx-agritime.before-https.$(date +%Y%m%d-%H%M%S)"
  python3 - "$CONF" "$DOMAIN" "$CERT_DIR" <<'PY'
import sys
conf, domain, cert = sys.argv[1], sys.argv[2], sys.argv[3]
txt = open(conf, encoding="utf-8").read()
ssl_block = """    listen 443 ssl http2;
    server_name %s;

    ssl_certificate     %s/fullchain.pem;
    ssl_certificate_key %s/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
""" % (domain, cert, cert)
txt = txt.replace("    listen 80;\n", "    listen 80;\n" + ssl_block, 1)
open(conf, "w", encoding="utf-8").write(txt)
print("已写入 443 监听")
PY
fi

# ---------- 4. 放行端口并重载 ----------
ufw allow 443/tcp >/dev/null 2>&1 || true
nginx -t || die "nginx 配置检查失败，已保留备份，可用备份回滚"
systemctl reload nginx
log "已重载 nginx"

# ---------- 5. 验证与续期 ----------
sleep 2
CODE="$(curl -s -o /dev/null -w '%{http_code}' --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/" || true)"
log "HTTPS 自测返回 $CODE"
systemctl list-timers 'certbot*' --no-pager 2>/dev/null | head -3 || true
log "完成。访问 https://$DOMAIN/ ，证书到期前 certbot 会自动续期（systemd timer）"
log "提醒：记得去华为云控制台安全组放行 443，否则公网仍访问不到"
