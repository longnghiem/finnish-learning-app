# Deployment — Single-Instance AWS Free Tier Demo

This directory contains everything required to deploy the Finnish Learning app
on a single EC2 instance running in the AWS Free Tier. Total monthly cost
during the Free Tier (first 12 months): **$0**. After the Free Tier window:
roughly **$8–10/month**.

---

## 1. What you are about to build

One EC2 virtual machine in AWS, configured like this:

```
┌────────────────────────────────────────────────────────────────────────┐
│  EC2  t3.micro  (Amazon Linux 2023, 1 GB RAM + 4 GB swap, 30 GB disk)  │
│                                                                        │
│  nginx :80   ←  the only public entry point                            │
│    ├─ /              →  /var/www/finnish/dist/   (static SPA)          │
│    └─ /api/*         →  127.0.0.1:8080           (Spring Boot)         │
│                                                                        │
│  Spring Boot jar      :8080   (systemd, -Xmx384m, Amazon Corretto 21)  │
│  Kafka KRaft broker   :9092   (systemd, -Xmx256m, loopback only)       │
│  PostgreSQL 15        :5432                      (loopback only)       │
└────────────────────────────────────────────────────────────────────────┘
                    
```

Why one instance:

- Cheapest setup that fits in Free Tier.
- Zero networking between AWS services to misconfigure.
- One SSH session = full debug access.
- One snapshot = full backup.

Trade-off: no high availability, no autoscaling.

---

## 2. Vocabulary — read this first

| Term | What it means in plain English |
|---|---|
| **AWS account** | Your billing identity at Amazon Web Services. One account can spin up unlimited resources, all billed together. |
| **Region** | A geographic AWS datacentre group (`eu-north-1` = Stockholm). Resources live in exactly one region. |
| **EC2** | "Elastic Compute Cloud" — virtual machines you rent by the hour. |
| **Instance** | One running virtual machine. |
| **AMI** | "Amazon Machine Image" — the OS template the instance boots from (we use Amazon Linux 2023). |
| **SELinux** | Linux security module that, in "enforcing" mode on AL2023, blocks processes from doing things their policy does not whitelist — e.g. nginx connecting to localhost, or reading files under non-standard paths. Provisioning script handles the tweaks. |
| **Instance type** | The hardware tier (`t3.micro` = 2 vCPU + 1 GB RAM, Free Tier). |
| **EBS volume** | Virtual disk attached to an instance. We use 30 GB (within the always-free quota). |
| **Security group** | A virtual firewall in front of the instance. Inbound rules say which ports the world can reach. |
| **Key pair** | An SSH key. AWS keeps the public half; you download the private `.pem`. Without the `.pem`, you cannot SSH in. |
| **Public DNS** | The DNS name AWS assigns the instance, e.g. `ec2-13-50-1-23.eu-north-1.compute.amazonaws.com`. |
| **Free Tier** | AWS gives every new account 12 months of free usage on a list of services. `t3.micro` 750 hr/mo + 30 GB EBS are the lines that matter for us. |
| **Budget Alert** | A free AWS Billing feature that emails you when spending crosses a threshold (e.g. $1, $5, $10). Always set this on day 1. |

---

## 3. Prerequisites on your laptop

Install these locally (the heavy lifting happens here, not on EC2):

- **JDK 21** — https://adoptium.net/. Required by `deploy/build.sh` to compile the backend.
- **Node ≥ 18 + npm** — https://nodejs.org/. Required to build the frontend.
- **Docker + Docker Compose** — https://docs.docker.com/get-docker/. Needed only to run a local Postgres on `:5532` for the jOOQ codegen step at build time.
- **rsync, ssh, scp, curl, openssl** — pre-installed on macOS / Linux. On Windows: use WSL2.

Verify each:

```bash
java -version           # openjdk version "21..."
node --version          # v18+ (v20+ recommended)
docker compose version  # Docker Compose v2+
rsync --version
ssh -V
```

---

## 4. AWS account setup (one time, ~30 minutes)

### 4.1 Create the account

1. Open https://portal.aws.amazon.com/billing/signup.
2. Provide email, password, account name (any string, e.g. `finnish-demo`).
3. Provide a payment card. AWS may pre-authorise ~$1 then refund it.
4. Phone verification by SMS or voice.
5. Pick the **Basic Support — Free** plan.
6. Wait for the activation email (usually instant, occasionally up to 24 h).

> **Reality check:** The card is mandatory even for Free Tier. As long as you respect the Free Tier limits and tear down before month 13, you pay nothing.

### 4.2 Set Budget Alerts (do this BEFORE launching anything)

This is your safety net. If anything ever bills, the alert emails you within hours.

1. Sign in: https://console.aws.amazon.com/.
2. Top-right account menu → **Billing and Cost Management**.
3. Left sidebar → **Budgets** → **Create budget**.
4. Choose **Customise (advanced)** → **Cost budget**.
5. Settings:
   - Period: **Monthly**
   - Budget amount: **$1**
   - Name: `monthly-1-usd`
6. Alert threshold: **Actual** spend at **100%** → enter your email.
7. **Create budget**.
8. Repeat for `$5` and `$10` so you get warnings at multiple levels.

> AWS also offers a free **Zero Spend Budget** template. Use it if you prefer one-click setup; the manual flow above is more explicit.

### 4.3 Pick a region

Choose a region close to your interviewer:

| If interviewer is in… | Use region |
|---|---|
| Finland / Northern Europe | `eu-north-1` (Stockholm) |
| Central / Western Europe | `eu-central-1` (Frankfurt) |
| US East Coast | `us-east-1` (N. Virginia) |
| US West Coast | `us-west-2` (Oregon) |

Set the region using the dropdown in the top-right of the AWS Console.
Every resource you create lives in the currently selected region. Stick to one.

### 4.4 Create a key pair

You need an SSH key to SSH into the instance later.

1. EC2 Console: https://console.aws.amazon.com/ec2/.
2. Left sidebar → **Key Pairs** → **Create key pair**.
3. Name: `finnish-demo`. Type: **RSA**. Format: **.pem** (Linux/macOS) or **.ppk** (PuTTY on Windows).
4. **Create key pair**. The browser downloads `finnish-demo.pem`.
5. Move it somewhere safe and fix permissions:

   ```bash
   mkdir -p ~/.ssh
   mv ~/Downloads/finnish-demo.pem ~/.ssh/finnish-demo.pem
   chmod 600 ~/.ssh/finnish-demo.pem
   ```

   Without `chmod 600`, SSH refuses to use the key.

You can never re-download this file. If you lose it, delete the key pair and create a new one (you'll also need to attach the new one to any future instance).

### 4.5 Launch the EC2 instance

1. EC2 Console → **Instances** → **Launch instances**.
2. **Name**: `finnish-demo`.
3. **Application and OS Images (AMI)**:
   - The default "Quick Start" tab shows **Amazon Linux 2023 AMI** at the top — pick that. It is tagged **Free tier eligible**, 64-bit (x86).
   - (Amazon Linux 2023 is the AWS-native distribution: first to get patches, Corretto JDK preinstalled, lowest friction for the rest of this guide.)
4. **Instance type**: `t3.micro` (Free Tier). If `t3.micro` is not Free-tier-eligible in your region, use `t2.micro` instead.
5. **Key pair (login)**: select `finnish-demo` (created in 4.4).
6. **Network settings** → **Edit**:
   - VPC: leave the default.
   - Auto-assign public IP: **Enable**.
   - **Firewall (security groups)** → **Create security group**.
     - Name: `finnish-demo-sg`.
     - Inbound rules:

       | Type   | Protocol | Port | Source           | Why                    |
       |--------|----------|------|------------------|------------------------|
       | SSH    | TCP      | 22   | **My IP**        | so only you can SSH    |
       | HTTP   | TCP      | 80   | `0.0.0.0/0`      | so anyone can view app |

       Do **not** open 8080, 9092, or 5432. Those services bind to `127.0.0.1` on the host and must not be reachable from the internet.
7. **Configure storage**:
   - 1 × **30 GiB** **gp3** root volume.
   - Confirm "Free tier eligible storage" appears in the right panel.
8. Click **Launch instance**. Wait for **Instance State: Running** (~30 s).
9. Click the instance ID → copy the **Public IPv4 DNS** (e.g. `ec2-13-50-1-23.eu-north-1.compute.amazonaws.com`). Note it; the rest of this guide calls it `<EC2_HOST>`.

### 4.6 First SSH

From your laptop:

```bash
ssh -i ~/.ssh/finnish-demo.pem ec2-user@<EC2_HOST>
```

First time: SSH asks "are you sure" → type `yes`. You land on the Amazon Linux shell as the `ec2-user` user. `exit` to come back.

(`ec2-user` is the default Amazon Linux 2023 SSH user. Ubuntu AMIs use `ubuntu`. If you launched a different AMI, check its documentation for the right user.)

If this fails:

| Symptom | Fix |
|---|---|
| `Permission denied (publickey)` | Wrong key path, or the SG rule for SSH points at the wrong IP. |
| `Connection timed out` | SG rule missing for port 22, or your home IP changed. Update the SG. |
| `WARNING: UNPROTECTED PRIVATE KEY FILE` | Forgot `chmod 600 ~/.ssh/finnish-demo.pem`. |

---

## 5. Provision the instance (one time, ~10 minutes)

The provisioning script installs Java, Postgres, nginx, sets up swap, creates
the application user, and bootstraps the Postgres role and database. It does
**not** copy your app code yet.

### 5.1 Get the repo onto the instance

Two options. Option A (recommended for a demo) clones the public repo directly.
Option B copies just `deploy/` if your repo is private and you'd rather not
publish credentials.

**Option A — clone:**

```bash
ssh -i ~/.ssh/finnish-demo.pem ec2-user@<EC2_HOST>

# inside the instance:
sudo dnf install -y git
git clone https://github.com/<you>/finnish-learning-monorepo.git
cd finnish-learning-monorepo
```

(AL2023 does not ship `git` by default. The provisioning script installs it again in step 5.2, but you need it now to clone the repo first.)

**Option B — scp the deploy directory only:**

```bash
# on your laptop:
scp -i ~/.ssh/finnish-demo.pem -r deploy ec2-user@<EC2_HOST>:~/
ssh -i ~/.ssh/finnish-demo.pem ec2-user@<EC2_HOST>
cd ~  # the scripts will run from here
```

### 5.2 Run the provisioning script

```bash
sudo bash deploy/provision-ec2.sh
```

Expected: ends with a "Provisioning complete." banner listing the next steps.
The script is idempotent — re-running is a no-op for stateful steps.

### 5.3 Set the Postgres password

```bash
sudo -u postgres psql -c "ALTER USER finnish WITH PASSWORD '<strong-password>';"
```

Pick a long random password. You will paste it into the env file in 5.5.
Generate one with `openssl rand -base64 24` if you don't have a password manager.

### 5.4 Install Kafka

```bash
sudo bash deploy/kafka/install-kafka.sh
sudo systemctl status kafka --no-pager | head -10
sudo systemd-analyze verify kafka.service
```

Expect `Active: active (running)`, and `systemd-analyze` exits with no output.
A warning like `Invalid environment assignment, ignoring: -Xmx256m` means the
`Environment=` line in `kafka.service` is unquoted — see Troubleshooting.

### 5.5 Install the backend systemd unit and env file

```bash
sudo bash deploy/backend/install-backend.sh
sudo nano /etc/finnish-backend.env       # or: sudo vim /etc/finnish-backend.env
```

Edit these lines:

```env
SPRING_DATASOURCE_PASSWORD=<paste the password from 5.3>
JWT_SECRET=<paste the output of: openssl rand -base64 32>
GROQ_API_KEY=<paste your Groq API key — see below>
```

`GROQ_API_KEY` powers the AI-backed sentence evaluator

To obtain one:

1. Sign up at https://console.groq.com/ (free tier available).
2. **API Keys** → **Create API Key** → copy the `gsk_…` token. You can only
   view it once.
3. Paste it into `GROQ_API_KEY=` in this file.

Optional tunables — defaults are sensible, override only if you need to:

```env
GROQ_BASE_URL=https://api.groq.com/openai/v1   # change only if proxying
GROQ_MODEL=llama-3.3-70b-versatile             # any model your account can access
GROQ_DAILY_QUOTA=50                            # per-user cap; 429 once exceeded
```

`GROQ_DAILY_QUOTA` is enforced in-process by `DailyQuotaTracker` and resets at
00:00 UTC. Tune it to your Groq plan — every successful evaluation counts.

Save and exit. The file is mode `600`, readable only by `root` and the
`finnish` system user.

> No new outbound firewall rule needed: EC2 instances allow all egress by
> default, so the backend can reach `api.groq.com` over HTTPS out of the box.
> If you tightened egress, allow `:443` to `api.groq.com`.

### 5.6 Install the nginx site

```bash
sudo bash deploy/nginx/install-nginx.sh
sudo systemctl status nginx --no-pager | head -5
```

Expect `Active: active (running)`.

### 5.7 Confirm dependencies

```bash
systemctl is-active postgresql kafka nginx
# expect: active  active  active
systemctl is-active finnish-backend
# expect: inactive (the jar is uploaded in step 7)
```

Log out of the EC2: `exit`.

---

## 6. Build artefacts on the laptop (every code change)

```bash
# 1. Start local Postgres for the jOOQ build step
(cd backend && docker compose up -d postgres)

# 2. Build backend jar + frontend bundle into deploy/artefacts/
bash deploy/build.sh
```

Expected end of output:

```
==> Build complete. Artefacts:
-rw-r--r-- 1 you you  48M ... deploy/artefacts/finnish-backend.jar
2.1M    deploy/artefacts/dist
```

If you see a warning about `http://localhost:8080` in the bundle: the
`frontend/.env.production` file is missing or wrong. Fix and rebuild.

---

## 7. Deploy (every code change)

### 7.1 One-time: configure deploy script

```bash
cp deploy/.deploy.env.example deploy/.deploy.env
nano deploy/.deploy.env
```

Set:

```env
EC2_HOST=<EC2_HOST from step 4.5>
SSH_KEY=~/.ssh/finnish-demo.pem
SSH_USER=ec2-user
```

`deploy/.deploy.env` is gitignored.

### 7.2 Run the deploy

```bash
bash deploy/deploy.sh
```

The script:

1. uploads the jar to the EC2 host,
2. uploads the SPA bundle,
3. restarts `finnish-backend`,
4. smoke-tests `http://127.0.0.1:8080/api-docs` until the backend is ready,
5. prints `Deployed: http://<EC2_HOST>/`.

If the smoke test fails, the script prints the last 50 lines of
`journalctl -u finnish-backend` so you can see why.

---

## 8. Verify

In a browser, open `http://<EC2_HOST>/`.

You should see the SPA. Walk through:

1. Register a user.
2. Log in.
3. Open a topic, take a quiz, answer a question.
4. Check `/dashboard` — quiz stats should reflect the answer.
5. Back to a topic, flip a card, expand the **Try a sentence** panel, type a
   Finnish sentence using the shown word and click **Evaluate**. You should
   see grammar / typo chips, a CEFR level, and (when relevant) a suggested
   correction and a B1 example. If the panel reports
   `Evaluator is temporarily unavailable`, check `GROQ_API_KEY` (see 8.2).

If anything is wrong, SSH into the instance and tail logs:

```bash
ssh -i ~/.ssh/finnish-demo.pem ec2-user@<EC2_HOST>
# Application log file (rolling, plain text) — primary debug source:
tail -f /opt/finnish/logs/finnish-backend.log
# Or the systemd journal (console output):
sudo journalctl -u finnish-backend -f
```

### 8.2 Inspect Kafka messages

The backend publishes `QuizAnswerEvent`s to the `quiz-answers` topic; a consumer
inside the same JVM updates the `user_topic_stats` read-model table that powers
the dashboard. When stats look wrong, watching the topic is the fastest way to
tell whether the producer or the consumer is the broken half.

All Kafka CLIs must run as the `kafka` system user (the broker's data dir is
owned by `kafka:kafka`; running as a different user gets `Permission denied`).

```bash
# Tail messages live (each new quiz answer prints one JSON line)
sudo -u kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server 127.0.0.1:9092 \
  --topic quiz-answers

# Replay the entire topic from the beginning (useful right after a fresh deploy)
sudo -u kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server 127.0.0.1:9092 \
  --topic quiz-answers \
  --from-beginning

```

---

## 9. Update / re-deploy

After any code change:

```bash
bash deploy/build.sh
bash deploy/deploy.sh
```

No EC2-side changes needed — provisioning is one-time.

---

## 10. Pause and tear down

### Pause (no compute charges, EBS still bills ~$2/mo after Free Tier)

EC2 Console → Instance → **Instance state → Stop instance**. To resume, **Start instance**.

> When you stop+start an instance, AWS reassigns a new public DNS unless you allocated an Elastic IP.

### Permanent tear-down (zero ongoing cost)

EC2 Console → Instance → **Instance state → Terminate instance**. EBS volume goes with it.

Optional safety net: **EBS Snapshot** before terminating, so you can recreate
the box later with the same seed data:

```bash
# from the AWS CLI; or use the Console Volumes page
aws ec2 create-snapshot --volume-id vol-xxxx --description "finnish-demo backup"
```

Snapshots cost ~$0.05/GB/mo. A 5 GB snapshot is essentially free.

---

## 11. Cost expectations

| Item | Free Tier (months 1–12) | Post-Free-Tier |
|---|---|---|
| `t3.micro` × 750 hr/mo | $0 | ~$7.50/mo |
| 30 GB gp3 EBS | $0 (always-free) | ~$2.40/mo |
| Outbound data (first 1 GB/mo) | $0 | $0 |
| Public IPv4 (assigned, in use) | $0 (always-free) | $3.65/mo |
| **Total** | **$0** | **~$10–14/mo** |

Set the **$1 Budget Alert** in step 4.2. If it ever fires, you've drifted off
the Free Tier path — investigate before continuing.

---

## 12. Troubleshooting

| Symptom | First thing to check |
|---|---|
| `502 Bad Gateway` from nginx | `sudo systemctl status finnish-backend`; jar may not have started. `sudo journalctl -u finnish-backend -n 100`. |
| `502 Bad Gateway`, error.log says `Permission denied` connecting to 127.0.0.1:8080 | SELinux blocking nginx → backend. Run `sudo setsebool -P httpd_can_network_connect 1`. (`provision-ec2.sh` sets this; if missing, the boolean reverted.) |
| `403 Forbidden` on static files | SELinux file context wrong on `/var/www/finnish/dist`. Run `sudo restorecon -R /var/www/finnish`. Check label: `ls -Zd /var/www/finnish/dist` should show `httpd_sys_content_t`. |
| Backend OOM-killed | `dmesg \| grep -i 'out of memory'`. Lower `-Xmx384m` in the systemd unit or stop another service. |
| Kafka won't start | `sudo journalctl -u kafka -n 100`. Usually permissions on `/var/lib/kafka`, or the storage was not formatted. Re-run `install-kafka.sh`. |
| `systemd-analyze verify` warns `kafka.service:NN: Invalid environment assignment, ignoring: -Xmx256m` | `Environment=` value is unquoted, so systemd splits on whitespace and treats `-Xmx256m` as a second `VAR=VALUE`. Fix: quote the value — `Environment="KAFKA_HEAP_OPTS=-Xms256m -Xmx256m"`. Without the quotes, Kafka still starts but with the default heap (1 GB) instead of the capped 256 MB — likely OOM on `t3.micro`. After editing the unit on the host: `sudo systemctl daemon-reload && sudo systemctl restart kafka`. |
| Backend can't connect to Postgres, log says `Ident authentication failed` | `pg_hba.conf` is still using `ident` for TCP localhost. Confirm: `sudo grep '^host' /var/lib/pgsql/data/pg_hba.conf` — both lines must say `scram-sha-256`. Provisioning script handles this; if missing, re-run it. |
| Backend can't connect to Postgres, log says `password authentication failed` | Password mismatch between `/etc/finnish-backend.env` and what you set in 5.3. Re-run the `ALTER USER` then `sudo systemctl restart finnish-backend`. |
| Browser hits CORS error | `APP_CORS_ALLOWED_ORIGINS` is set to something other than empty in `/etc/finnish-backend.env`. For same-origin nginx setup, leave it empty. |
| "Try a sentence" panel returns `Evaluator is temporarily unavailable` (502) | `GROQ_API_KEY` missing, blank, or rejected by Groq. Check `sudo grep '^GROQ_API_KEY' /etc/finnish-backend.env`. Validate the key: `curl -sS -H "Authorization: Bearer $GROQ_API_KEY" https://api.groq.com/openai/v1/models \| head`. After fixing: `sudo systemctl restart finnish-backend`. |
| Panel returns `Daily limit reached` (429) on first try of the day | `DailyQuotaTracker` is process-local; either someone else on the same user already exhausted it, or a sticky clock issue. Restart wipes the counter: `sudo systemctl restart finnish-backend`. Raise the cap via `GROQ_DAILY_QUOTA`. |
| Backend logs `GROQ_MODEL … model_decommissioned` or 4xx from Groq | The configured model id is no longer served. Pick a current model from https://console.groq.com/docs/models, update `GROQ_MODEL=` in `/etc/finnish-backend.env`, restart. |
| Frontend throws `Failed to construct 'URL': Invalid URL` in DevTools | `VITE_API_BASE_URL=""` resolves to an empty string and `new URL("/api/...")` rejects relative URLs. Fix is in `frontend/src/api/config.ts`: empty string must fall back to `window.location.origin`. Rebuild + redeploy. |
| SSH `Connection timed out` after a few days | Your home IP changed. EC2 SG → edit inbound SSH rule → **My IP**. |
| `npm run build` warns about `http://localhost:8080` in bundle | `frontend/.env.production` missing or wrong. |
| Disk full | `df -h`. Likely culprits: `/var/log/journal`, `/var/lib/pgsql`, or `/opt/finnish/logs` (backend log files — rotation-capped at 100 MB total by `logback-spring.xml`; if it is larger, the rolling policy is misconfigured). Run `sudo journalctl --vacuum-size=200M`. |

---

## 13. Explicitly out of scope

These are valuable post-demo enhancements; this runbook deliberately omits them
to keep the demo path short:

- **HTTPS / TLS** — would require a domain name and Certbot. See https://certbot.eff.org/instructions?ws=nginx&os=snap.
- **Custom domain** — requires Route 53 ($0.50/mo) and DNS configuration.
- **CI / CD pipeline** — the current `build.sh` + `deploy.sh` flow is intentional for the demo.
- **Infrastructure as Code** (Terraform, CDK) — the Console clicks above are explicit; an IaC version would mirror them.
- **Remote log aggregation** The backend now writes rolling plain-text log files to `/opt/finnish/logs/` (see `backend/src/main/resources/logback-spring.xml`) and still logs to the journal; shipping those logs off-box is the part not covered here.
- **Multi-instance / load balancer / autoscaling** — not feasible in Free Tier and unnecessary for a single-user demo.
- **Database backups beyond `aws ec2 create-snapshot`** — RDS managed snapshots would be the production answer.

---

## 14. File map of this directory

```
deploy/
├── README.md                       this file
├── .gitignore                      ignores artefacts/ and local *.env
├── .deploy.env.example             template for deploy script config
├── build.sh                        laptop: build backend jar + frontend bundle
├── deploy.sh                       laptop: upload + restart on EC2
├── provision-ec2.sh                EC2: one-time OS + Postgres setup
├── backend/
│   ├── finnish-backend.service     systemd unit for the Spring Boot jar
│   ├── finnish-backend.env.example template for /etc/finnish-backend.env
│   └── install-backend.sh          EC2: install unit + env file
├── kafka/
│   ├── install-kafka.sh            EC2: download + configure single-node KRaft
│   ├── server.properties           Kafka config (loopback listeners only)
│   └── kafka.service               systemd unit for Kafka
└── nginx/
    ├── finnish.conf                site config: SPA + /api/ proxy
    └── install-nginx.sh            EC2: drop site config + reload nginx
```