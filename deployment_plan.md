# GitHub Analytics — Oracle Cloud Always Free Deployment Guide

Deploy the full stack on **Oracle Cloud Always Free** — genuinely free with no expiry. A credit card is required at signup for identity verification only ($0 hold, removed in 3–5 days; no charges unless you manually upgrade).

---

## Stack

| Component | Runs On | Free Limits |
|-----------|---------|-------------|
| Backend (Spring Boot) | Oracle ARM VM | 12 GB RAM, 2 OCPU, always-on |
| PostgreSQL 16 | Oracle ARM VM (Docker) | 200 GB block storage total |
| Redis 7 | Oracle ARM VM (Docker) | Same VM, ~50 MB |
| Nginx + SSL | Oracle ARM VM (Docker) | Free Let's Encrypt cert |
| Frontend | Vercel | Unlimited hobby deploys |
| Domain | DuckDNS | Free subdomain (e.g. `myapp.duckdns.org`) |

> **Why Oracle over Render/Railway:** Oracle ARM VM (4 OCPU / 24 GB RAM total across 2 VMs) is always-on with no sleep, far more RAM than any other free tier, and runs the entire backend stack on one machine.
>
> **Idle reclamation warning:** Oracle may reclaim VMs where CPU + memory + network are all <20% for 7 consecutive days. Mitigate with a free keep-alive cron on [cron-job.org](https://cron-job.org) pinging `/api/v1/actuator/health` every 14 minutes.

---

## Environment Variables

Create `.env.prod` on the VM (copy from `backend/.env.example`):

| Variable | Description |
|----------|-------------|
| `GITHUB_CLIENT_ID` | GitHub OAuth App client ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App client secret |
| `GITHUB_REDIRECT_URI` | `https://YOUR_DOMAIN.duckdns.org/api/v1/auth/github/callback` |
| `JWT_SECRET` | 32+ char secret — generate: `openssl rand -hex 32` |
| `ENCRYPTION_KEY` | Exactly 32 chars for AES-256-GCM |
| `POSTGRES_USER` | DB username (e.g. `gitanalytics`) |
| `POSTGRES_PASSWORD` | Strong DB password |
| `CORS_ALLOWED_ORIGINS` | `https://<your-app>.vercel.app` |
| `FRONTEND_URL` | `https://<your-app>.vercel.app` |
| `GROQ_API_KEY` | Optional — for AI summaries |

---

## Deployment Steps

### Step 1 · Oracle Cloud — Create Account & ARM VM

1. Sign up at [cloud.oracle.com](https://cloud.oracle.com) — provide credit card for identity verification (no charge)
2. Choose a **Home Region** geographically close to you (cannot change later)
3. In the Console: **Compute → Instances → Create Instance**
   - Shape: **VM.Standard.A1.Flex** (ARM Ampere — Always Free)
   - OCPUs: **2**, Memory: **12 GB**
   - OS: **Ubuntu 22.04**
   - Boot volume: **50 GB**
   - Add your SSH public key (generate with `ssh-keygen -t ed25519` if needed)
4. **Security List** (VCN → Security Lists → Default): add **Ingress rules**:
   - Port 22 (SSH) from `0.0.0.0/0`
   - Port 80 (HTTP) from `0.0.0.0/0`
   - Port 443 (HTTPS) from `0.0.0.0/0`
5. Note the VM's **Public IP address**

---

### Step 2 · DuckDNS — Free Subdomain

1. Sign in at [duckdns.org](https://www.duckdns.org) with GitHub
2. Create a subdomain: e.g. `github-analytics` → `github-analytics.duckdns.org`
3. Set its IP to the Oracle VM's public IP
4. Verify: `ping github-analytics.duckdns.org` resolves to the Oracle IP

---

### Step 3 · VM Initial Setup

SSH into the VM and run:

```bash
# Update and install Docker
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# Open Ubuntu firewall (Oracle also has network-level rules from Step 1)
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save

# Install Certbot
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/bin/certbot
```

> Log out and back in after `usermod` so the `docker` group takes effect.

---

### Step 4 · Clone Repo & Configure Secrets

```bash
git clone https://github.com/<your-username>/github-analytics.git
cd github-analytics

# Create production env file (NOT committed to git)
cp backend/.env.example .env.prod
nano .env.prod   # fill in all values
```

Replace `YOUR_DOMAIN` in `nginx/default.conf` with your DuckDNS subdomain:
```bash
sed -i 's/YOUR_DOMAIN/github-analytics/g' nginx/default.conf
```

---

### Step 5 · First Start (HTTP only — before SSL)

The HTTPS server block in `nginx/default.conf` is commented out by default. Start with HTTP only:

```bash
# Build and start all services
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# Verify backend is healthy (may take ~60s first start for Flyway migrations)
docker compose -f docker-compose.prod.yml logs -f backend
```

Check Flyway in logs: `Successfully applied N migrations`.

---

### Step 6 · Get SSL Certificate (Let's Encrypt)

```bash
# Get certificate (Nginx must be running and port 80 open)
sudo certbot certonly --webroot \
  -w /var/www/certbot \
  -d YOUR_DOMAIN.duckdns.org \
  --email your@email.com \
  --agree-tos --non-interactive

# Uncomment the HTTPS server block in nginx/default.conf
nano nginx/default.conf

# Reload Nginx
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
```

Certbot auto-renews via a systemd timer — no manual action needed.

---

### Step 7 · Verify Backend

```bash
curl https://YOUR_DOMAIN.duckdns.org/api/v1/actuator/health
# → {"status":"UP"}
```

---

### Step 8 · Vercel (Frontend)

1. Sign up at [vercel.com](https://vercel.com) — no credit card needed
2. New Project → Import GitHub repo → Root Directory: `frontend/`
3. Set environment variables:

| Variable | Value |
|----------|-------|
| `BACKEND_URL` | `https://YOUR_DOMAIN.duckdns.org` |
| `NEXT_PUBLIC_APP_URL` | `https://<your-app>.vercel.app` |

4. Deploy → ~1–2 minutes

---

### Step 9 · GitHub OAuth App

In **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**:

| Field | Value |
|-------|-------|
| Application name | GitHub Analytics |
| Homepage URL | `https://<your-app>.vercel.app` |
| Authorization callback URL | `https://YOUR_DOMAIN.duckdns.org/api/v1/auth/github/callback` |

Copy **Client ID** and generate **Client Secret** → add to `.env.prod` → restart:
```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
```

---

### Step 10 · Keep-alive (Prevent Idle Reclamation)

On [cron-job.org](https://cron-job.org) (free, no signup required):
- URL: `https://YOUR_DOMAIN.duckdns.org/api/v1/actuator/health`
- Schedule: every **14 minutes**

This keeps Oracle from reclaiming the VM due to low activity.

---

## Verification Checklist

| Test | Command / Action | Expected |
|------|-----------------|----------|
| Backend health | `curl https://YOUR_DOMAIN.duckdns.org/api/v1/actuator/health` | `{"status":"UP"}` |
| Flyway | `docker compose -f docker-compose.prod.yml logs backend \| grep "migrations"` | `Successfully applied N migrations` |
| HTTPS | Browser → `https://YOUR_DOMAIN.duckdns.org` | Valid SSL cert, no warning |
| Demo login | `https://<vercel-app>.vercel.app/api/demo-login` | Redirects to `/dashboard` with data |
| GitHub OAuth | Click "Login with GitHub" | Redirects to `/dashboard`, `jwt` cookie set |
| JWT revocation | Login → logout → replay old JWT to `/api/backend/auth/me` | `401 Unauthorized` |
| Cache | `docker compose -f docker-compose.prod.yml exec redis redis-cli keys "ga:a:*"` | Keys appear after first page load |
| Startup validator | Remove `JWT_SECRET` from `.env.prod` and restart | Container exits with `IllegalStateException` |

---

## Useful Commands

```bash
# View logs
docker compose -f docker-compose.prod.yml logs -f

# Restart a single service
docker compose -f docker-compose.prod.yml restart backend

# Pull latest code and redeploy
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build backend

# Connect to PostgreSQL
docker compose -f docker-compose.prod.yml exec postgres psql -U gitanalytics gitanalytics

# Check Redis cache
docker compose -f docker-compose.prod.yml exec redis redis-cli keys "ga:*"
```
