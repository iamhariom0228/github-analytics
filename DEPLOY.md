# First-Time Deployment Guide

Deploy the full stack on Oracle Cloud Always Free + Vercel. Free forever, no expiry.

**Total time:** ~45–60 min (mostly waiting on Oracle VM provisioning and first Docker build)

---

## Stack

| Component | Where | Cost |
|-----------|-------|------|
| Backend + Postgres + Redis + Nginx | Oracle ARM VM | Free (2 OCPU, 12 GB RAM, always-on) |
| Frontend | Vercel | Free (unlimited hobby deploys) |
| Domain | DuckDNS | Free subdomain (e.g. `myapp.duckdns.org`) |

> **Oracle requires a credit card at signup** for identity verification only — a $0 hold is placed and removed in 3–5 days. No charges unless you manually upgrade.

---

## Part A — Push Code to GitHub

On your local machine:

```bash
git add -A
git commit -m "feat: add production infrastructure files"
git push origin master
```

---

## Part B — Oracle Cloud Setup

### 1. Create Oracle account

1. Go to [cloud.oracle.com](https://cloud.oracle.com) → Sign Up
2. Enter your credit card (identity verification only)
3. Choose your **Home Region** — pick the one geographically closest to you. **This cannot be changed later.**

### 2. Create the ARM VM

In the Oracle Console:

1. **Compute → Instances → Create Instance**
2. Click **Edit** next to Shape → **Change Shape** → Ampere → **VM.Standard.A1.Flex**
   - OCPUs: `2`
   - RAM: `12 GB`
3. OS: Ubuntu 22.04 (leave as default)
4. Boot volume: `50 GB`
5. Under **Add SSH keys** → paste your public key

   If you don't have one, generate it first:
   ```bash
   ssh-keygen -t ed25519
   cat ~/.ssh/id_ed25519.pub   # copy this
   ```

6. Click **Create** → wait ~2 minutes for it to provision

7. Note the **Public IP address** shown on the instance page

### 3. Open firewall ports

1. On the instance page, click your **VCN** link → **Security Lists** → **Default Security List**
2. Click **Add Ingress Rules** and add these two rules (port 22 should already exist):

   | Source CIDR | Protocol | Port |
   |-------------|----------|------|
   | `0.0.0.0/0` | TCP | `80` |
   | `0.0.0.0/0` | TCP | `443` |

---

## Part C — Free Domain (DuckDNS)

### 4. Get a subdomain

1. Go to [duckdns.org](https://www.duckdns.org) → sign in with GitHub
2. Enter a subdomain name (e.g. `github-analytics`) and click **Add Domain**
3. Set the IP to your Oracle VM's public IP → click **Update IP**
4. Verify it resolves (from your local machine):
   ```bash
   ping github-analytics.duckdns.org
   # should show your Oracle IP
   ```

From here on, replace `YOUR_DOMAIN` with your chosen subdomain (e.g. `github-analytics`).

---

## Part D — VM Setup

### 5. SSH into the VM

```bash
ssh ubuntu@YOUR_ORACLE_IP
```

### 6. Install Docker and open the OS firewall

Oracle VMs have **two layers of firewall** — the network-level rules (Step 3) and Ubuntu's iptables. Both need to be open.

```bash
# Update packages
sudo apt-get update && sudo apt-get upgrade -y

# Install Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# Open OS-level firewall for HTTP and HTTPS
sudo apt-get install -y iptables-persistent
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save

# Install Certbot (for SSL cert later)
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/bin/certbot

# Re-login so the docker group takes effect
exit
```

SSH back in:
```bash
ssh ubuntu@YOUR_ORACLE_IP
```

### 7. Clone the repo

```bash
git clone https://github.com/<your-username>/github-analytics.git
cd github-analytics
```

### 8. Configure Nginx with your domain

```bash
sed -i 's/YOUR_DOMAIN/github-analytics/g' nginx/default.conf
```

Replace `github-analytics` with your actual DuckDNS subdomain.

### 9. Create the production env file

```bash
cp backend/.env.example .env.prod
nano .env.prod
```

Fill in the following values. Leave `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` as placeholders for now — you'll update them in Step 14.

```env
GITHUB_CLIENT_ID=placeholder
GITHUB_CLIENT_SECRET=placeholder
GITHUB_REDIRECT_URI=https://YOUR_DOMAIN.duckdns.org/api/v1/auth/github/callback

# Generate with: openssl rand -hex 32
JWT_SECRET=

# Must be exactly 32 characters
ENCRYPTION_KEY=

POSTGRES_USER=gitanalytics
POSTGRES_PASSWORD=

# Fill these in after Step 13 (Vercel deploy)
CORS_ALLOWED_ORIGINS=https://<your-app>.vercel.app
FRONTEND_URL=https://<your-app>.vercel.app

COOKIE_SECURE=true

# Optional
GROQ_API_KEY=
RESEND_API_KEY=
RESEND_FROM_EMAIL=
```

To generate secrets:
```bash
openssl rand -hex 32        # for JWT_SECRET
openssl rand -base64 24     # for ENCRYPTION_KEY — trim/pad to exactly 32 chars
```

---

## Part E — First Deploy (HTTP only)

### 10. Start all services

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

The first build takes ~3–5 minutes (compiling the Spring Boot jar inside Docker).

Watch the backend until it's ready:
```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

Look for:
- `Successfully applied N migrations` — Flyway ran DB migrations
- `Started GitAnalyticsApplication` — app is up

Verify it's responding:
```bash
curl http://YOUR_DOMAIN.duckdns.org/api/v1/actuator/health
# → {"status":"UP"}
```

> If it's not responding, check that both firewall layers are open (Step 3 and Step 6).

---

## Part F — SSL Certificate

### 11. Get the Let's Encrypt certificate

```bash
sudo certbot certonly --webroot \
  -w /var/www/certbot \
  -d YOUR_DOMAIN.duckdns.org \
  --email your@email.com \
  --agree-tos --non-interactive
```

### 12. Enable HTTPS in Nginx

Open the Nginx config:
```bash
nano nginx/default.conf
```

Uncomment the entire `server { listen 443 ... }` block — remove the `#` from every line in that block. Save and exit.

Reload Nginx:
```bash
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
```

Verify HTTPS works:
```bash
curl https://YOUR_DOMAIN.duckdns.org/api/v1/actuator/health
# → {"status":"UP"}
```

---

## Part G — Frontend on Vercel

### 13. Deploy the frontend

1. Go to [vercel.com](https://vercel.com) → sign in with GitHub → **New Project**
2. Import your GitHub repo
3. Set **Root Directory** to `frontend`
4. Add environment variables:

   | Variable | Value |
   |----------|-------|
   | `BACKEND_URL` | `https://YOUR_DOMAIN.duckdns.org` |
   | `NEXT_PUBLIC_APP_URL` | `https://<your-app>.vercel.app` |

5. Click **Deploy** → wait ~1–2 minutes
6. Note your Vercel URL (e.g. `github-analytics-xyz.vercel.app`)

Now update `.env.prod` on the VM with the real Vercel URL:

```bash
nano .env.prod
# Set CORS_ALLOWED_ORIGINS and FRONTEND_URL to https://<your-app>.vercel.app
```

Restart the backend to pick up the new values:
```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
```

---

## Part H — GitHub OAuth App

### 14. Create the OAuth App

Go to **GitHub → Settings → Developer Settings → OAuth Apps → New OAuth App**:

| Field | Value |
|-------|-------|
| Application name | GitHub Analytics |
| Homepage URL | `https://<your-app>.vercel.app` |
| Authorization callback URL | `https://YOUR_DOMAIN.duckdns.org/api/v1/auth/github/callback` |

Click **Register application**, then:
1. Copy the **Client ID**
2. Click **Generate a new client secret** → copy it

Update `.env.prod`:
```bash
nano .env.prod
# Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET
```

Restart the backend:
```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
```

---

## Part I — Final Checks

### 15. Smoke test

| Test | How | Expected |
|------|-----|----------|
| Backend health | `curl https://YOUR_DOMAIN.duckdns.org/api/v1/actuator/health` | `{"status":"UP"}` |
| HTTPS | Open `https://YOUR_DOMAIN.duckdns.org` in browser | Padlock, no warning |
| Demo login | Visit `https://<vercel-app>/api/demo-login` | Redirects to `/dashboard` with data |
| GitHub OAuth | Click "Login with GitHub" on the frontend | Redirects back to `/dashboard`, data loads |

### 16. Set up keep-alive (prevent Oracle idle reclamation)

Oracle may reclaim VMs where CPU + memory + network are all below 20% for 7 consecutive days. Prevent this with a free cron:

1. Go to [cron-job.org](https://cron-job.org) — no signup required
2. Create a new cron job:
   - URL: `https://YOUR_DOMAIN.duckdns.org/api/v1/actuator/health`
   - Schedule: every **14 minutes**

---

## Making Changes After Go-Live

**Frontend:** Just push to `master` — Vercel auto-deploys in ~1–2 minutes.

**Backend:** SSH in and run:
```bash
cd github-analytics
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build backend
```

---

## Useful Commands

```bash
# View logs for all services
docker compose -f docker-compose.prod.yml logs -f

# View logs for one service
docker compose -f docker-compose.prod.yml logs -f backend

# Restart a service
docker compose -f docker-compose.prod.yml restart backend

# Connect to Postgres
docker compose -f docker-compose.prod.yml exec postgres psql -U gitanalytics gitanalytics

# Inspect Redis cache
docker compose -f docker-compose.prod.yml exec redis redis-cli keys "ga:*"

# Stop everything
docker compose -f docker-compose.prod.yml down

# Stop and wipe all data (destructive!)
docker compose -f docker-compose.prod.yml down -v
```
