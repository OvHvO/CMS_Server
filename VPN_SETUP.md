# WireGuard + PostgreSQL Setup Guide

## Machine Information

| Machine            | Role            | WireGuard IP |
| ------------------ | --------------- | ------------ |
| `UB1` / Machine 1  | Server          | `10.50.0.1`  |
| `UB2` / Machine 2  | DB Server       | `10.50.0.2`  |
| Windows / Frontend | Frontend Client | `10.50.0.3`  |

### Network

```text
WireGuard Network: 10.50.0.0/24

Machine 1 / Server  → 10.50.0.1
Machine 2 / DB      → 10.50.0.2
Frontend            → 10.50.0.3
```

---

# 1. GENKEY ON FIRST MACHINE

## Machine 1 / Server

Run:

```bash
wg genkey | tee ~/ub1_private.key | wg pubkey > ~/ub1_public.key
```

---

# 2. GENKEY ON SECOND MACHINE

## Machine 2 / DB Server

Run:

```bash
wg genkey | tee ~/ub2_private.key | wg pubkey > ~/ub2_public.key
```

## VIEW

```bash
cat ~/ub2_private.key
cat ~/ub2_public.key
```

> You will also need the private/public key generated on Machine 1 / Server.

---

# 3. WIREGUARD CONFIG — FIRST MACHINE / SERVER

Go to **Machine 1 / Server**:

```bash
sudo nano /etc/wireguard/wg0.conf
```

## INSERT INTO

```ini
[Interface]
PrivateKey = UB1_PRIVATE_KEY
Address = 10.50.0.1/24
ListenPort = 51820

[Peer]
PublicKey = UB2_PUBLIC_KEY
AllowedIPs = 10.50.0.2/32
Endpoint = UB2_VIRTUALBOX_IP:51820
PersistentKeepalive = 25
```

Replace:

```text
UB1_PRIVATE_KEY
```

with the private key from Machine 1:

```bash
cat ~/ub1_private.key
```

Replace:

```text
UB2_PUBLIC_KEY
```

with the public key from Machine 2:

```bash
cat ~/ub2_public.key
```

Replace:

```text
UB2_VIRTUALBOX_IP:51820
```

with the VirtualBox IP address and WireGuard port of Machine 2 that Machine 1 can access.

---

# 4. WIREGUARD CONFIG — SECOND MACHINE / DB SERVER

Go to **Machine 2 / DB Server**:

```bash
sudo nano /etc/wireguard/wg0.conf
```

## INSERT INTO

```ini
[Interface]
PrivateKey = UB2_PRIVATE_KEY
Address = 10.50.0.2/24
ListenPort = 51820

[Peer]
PublicKey = UB1_PUBLIC_KEY
AllowedIPs = 10.50.0.1/32
```

Replace:

```text
UB2_PRIVATE_KEY
```

with:

```bash
cat ~/ub2_private.key
```

Replace:

```text
UB1_PUBLIC_KEY
```

with the public key from Machine 1:

```bash
cat ~/ub1_public.key
```

> No need have endpoint first machine will automatically connected to second machine.

---

# 5. START WIREGUARD

Start **second and first machine**:

```bash
sudo wg-quick up wg0
```

Check WireGuard:

```bash
sudo wg
```

Run this on both:

* Machine 1 / Server
* Machine 2 / DB Server

---

# 6. POSTGRESQL SETUP — SECOND MACHINE / DB

Go to **Machine 2 / DB Server**.

## 6.1 Check PostgreSQL Config File

Run:

```bash
sudo -u postgres psql -c "SHOW config_file;"
```

This will show the PostgreSQL configuration file path.

Example:

```text
/etc/postgresql/18/main/postgresql.conf
```

Open the configuration file:

```bash
sudo nano /etc/postgresql/18/main/postgresql.conf
```

> Use your own config file path.

---

# 7. CHANGE `listen_addresses`

Find:

```ini
#listen_addresses = "localhost"
```

Change this to:

```ini
listen_addresses = '*'
```

---

# 8. RESTART POSTGRESQL

Restart PostgreSQL:

```bash
sudo systemctl restart PostgreSQL
```

> If your system uses a different PostgreSQL service name, use the service name configured on your machine.

---

# 9. CHECK `pg_hba.conf`

Run:

```bash
sudo -u postgres psql -c "SHOW hba_file;"
```

This will show the PostgreSQL `pg_hba.conf` path.

Example:

```text
/etc/postgresql/18/main/pg_hba.conf
```

Open it:

```bash
sudo nano /etc/postgresql/18/main/pg_hba.conf
```

> Use your own `pg_hba.conf` path.

---

# 10. ADD DATABASE ACCESS RULE

Add this:

```text
host    dcoms_cms    cms_user    10.50.0.0/24    scram-sha-256
```

This allows the WireGuard network:

```text
10.50.0.0/24
```

to access:

```text
Database: dcoms_cms
User:     cms_user
Method:   scram-sha-256
```

---

# 11. RESTART POSTGRESQL AGAIN

```bash
sudo systemctl restart PostgreSQL
```

---

# 12. TEST POSTGRESQL CONNECTION

From the machine that needs to connect to the DB:

```bash
nc -vz 10.50.0.2 5432
```

If succeeded then done.

The connection being tested is:

```text
10.50.0.2:5432
```

Where:

```text
10.50.0.2 = Machine 2 / DB Server
5432      = PostgreSQL
```

---

# 13. FRONTEND SETUP

## Frontend — Windows / Linux

DOWNLOAD WIREGUARD Client (Win/Linux).

The Frontend will use:

```text
10.50.0.3
```

as its WireGuard IP.

---

# 14. FRONTEND WIREGUARD CONFIG

Insert this:

```ini
[Interface]
PrivateKey = WINDOWS_PRIVATE_KEY
Address = 10.50.0.3/24

[Peer]
PublicKey = UB1_PUBLIC_KEY
AllowedIPs = 10.50.0.1/32
Endpoint = <IP that Machine 1 can access by FE>:51820
PersistentKeepalive = 25
```

Replace:

```text
WINDOWS_PRIVATE_KEY
```

with the private key generated for the Frontend.

Replace:

```text
UB1_PUBLIC_KEY
```

with the public key of Machine 1 / Server.

Replace:

```text
<IP that Machine 1 can access by FE>:51820
```

with the IP address and port that the Frontend can use to reach Machine 1.

---

# 15. ADD FRONTEND PEER TO MACHINE 1

Go to **Machine 1 / Server**:

```bash
sudo nano /etc/wireguard/wg0.conf
```

ADD This:

```ini
[Peer]
PublicKey = WINDOWS_PUBLIC_KEY
AllowedIPs = 10.50.0.3/32
```

Replace:

```text
WINDOWS_PUBLIC_KEY
```

with the public key of the Frontend.

---

# 16. FINAL WIREGUARD NETWORK

```text
                         WireGuard Network
                           10.50.0.0/24
                                |
                +---------------+---------------+
                |                               |
                |                               |
        10.50.0.1/24                    10.50.0.3/24
        Machine 1 / Server               Frontend
                |                         Windows/Linux
                |
                |
        10.50.0.2/24
        Machine 2 / DB Server
                |
                |
        PostgreSQL : 5432
```

---

# 17. FINAL CONFIGURATION SUMMARY

## Machine 1 / Server

```text
Name:       UB1
Role:       Server
WireGuard:  10.50.0.1
Port:       51820
```

Peer:

```text
Machine 2 / DB
WireGuard IP: 10.50.0.2
```

Peer:

```text
Frontend
WireGuard IP: 10.50.0.3
```

---

## Machine 2 / DB Server

```text
Name:       UB2
Role:       DB Server
WireGuard:  10.50.0.2
Port:       51820
PostgreSQL: 5432
```

Peer:

```text
Machine 1 / Server
WireGuard IP: 10.50.0.1
```

---

## Frontend

```text
Role:       Frontend
WireGuard:  10.50.0.3
```

Peer:

```text
Machine 1 / Server
WireGuard IP: 10.50.0.1
Port:         51820
```

---

# 18. IMPORTANT COMMANDS

## WireGuard

### Start

```bash
sudo wg-quick up wg0
```

### Check Status

```bash
sudo wg
```

---

## PostgreSQL

### Check Config File

```bash
sudo -u postgres psql -c "SHOW config_file;"
```

### Check HBA File

```bash
sudo -u postgres psql -c "SHOW hba_file;"
```

### Restart PostgreSQL

```bash
sudo systemctl restart PostgreSQL
```

---

## Test DB Port

```bash
nc -vz 10.50.0.2 5432
```

---

# 19. Expected Connection Flow

```text
Frontend
10.50.0.3
    |
    | WireGuard
    | 10.50.0.0/24
    |
    v
Machine 1 / Server
10.50.0.1
    |
    | WireGuard
    |
    v
Machine 2 / DB Server
10.50.0.2
    |
    | PostgreSQL
    | Port 5432
    |
    v
dcoms_cms
    |
    +-- cms_user
```

The main network is:

```text
10.50.0.0/24
```

The individual WireGuard addresses are:

```text
Machine 1 / Server  = 10.50.0.1
Machine 2 / DB      = 10.50.0.2
Frontend            = 10.50.0.3
```

PostgreSQL is accessed through:

```text
10.50.0.2:5432
```
