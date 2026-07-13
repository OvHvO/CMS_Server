# Getting Started

## Project Structure

Please make sure your project is organized exactly as shown below.

```text
root/
├── CMS_Client/
│   └── client/                 # Frontend project (includes pom.xml)
├── CMS_Server/
│   ├── common/                 # Shared module (includes pom.xml)
│   └── server/                 # Backend server (includes pom.xml)
└── pom.xml                     # Main Maven parent project
```

> **Important:**  
> All files and folders should follow this structure.

---

## Build the Project

Navigate to the project root (where the main `pom.xml` is located) and execute:

```bash
cd your_root_path
mvn clean install -DskipTests
```

After the build completes successfully, all required dependencies will be resolved and installed automatically.

---

## Important Notes

# TLS/SSL Configuration

To enable secure TLS/SSL communication between the backend server and the client, both applications must trust the same server certificate.

This guide walks through creating:

- `server.keystore` – contains the server's private key and certificate.
- `server.cer` – exported public certificate.
- `client.truststore` – contains the trusted server certificate used by the client.

> **Note**
> These commands use Java's `keytool`, which is included with the JDK.

---

## 1. Create the Server Keystore

Navigate to your backend server directory:

```bash
cd your_back_end/server
```

Generate a new PKCS12 keystore:

```bash
keytool -genkeypair \
  -alias server \
  -keyalg RSA \
  -keysize 2048 \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore server.keystore
```

You will be prompted to enter:

- Keystore password
- Server information (CN, OU, O, L, ST, C) 

This creates:

```
server.keystore
```

which contains the server's private key and certificate.

---

## 2. Export the Server Certificate

Export the public certificate from the keystore:

```bash
keytool -exportcert \
  -alias server \
  -keystore server.keystore \
  -file server.cer
```

This generates:

```
server.cer
```

The certificate contains only the public key and is safe to distribute to clients.

---

## 3. Create the Client Truststore

Import the server certificate into a new client truststore:

```bash
keytool -importcert \
  -alias server \
  -file server.cer \
  -keystore client.truststore
```

When prompted:

```
Trust this certificate? [no]:
```

Type:

```
yes
```

This creates:

```
client.truststore
```

The client will use this truststore to verify the backend server's identity during the TLS handshake.

---

## 4. Copy the Truststore to the Client Project

Move the generated truststore into your frontend client project:

```bash
mv client.truststore your_frontend_client/client/
```

Your project structure should look like:

```
your_back_end/
└── server/
    ├── server.keystore
    └── server.cer

your_frontend_client/
└── client/
    └── client.truststore
```

---

## Summary

| File | Purpose | Used By |
|------|---------|---------|
| `server.keystore` | Stores the server's private key and certificate | Backend Server |
| `server.cer` | Public certificate exported from the server | Used to create truststores |
| `client.truststore` | Stores trusted server certificates | Frontend Client |

---

## Verification

List the contents of the server keystore:

```bash
keytool -list -keystore server.keystore
```

List the contents of the client truststore:

```bash
keytool -list -keystore client.truststore
```

Both should contain an entry with the alias:

```
server
```

If the alias exists in both stores, the client should be able to establish a trusted TLS/SSL connection with the backend server.


### Parent POM Configuration

The `pom.xml` files inside the following modules:

- `CMS_Client/client`
- `CMS_Server/common`
- `CMS_Server/server`

use the `<parent>` tag with **relative paths** to reference the main `pom.xml`.

If you change the project directory structure, you **must update the `relativePath`** in each module's `pom.xml` accordingly.

---

### Main POM Modules

The main `pom.xml` uses the `<modules>` section to include:

- `CMS_Server/common`
- `CMS_Server/server`
- `CMS_Client/client`

These module paths are also configured using **relative paths**. If the project structure changes, update the module paths accordingly.

---

### Example Main POM

A sample main Maven configuration is provided at:

[example pom.xml](docs/examples/main.pom.xml)

You can use this file as a reference when creating or modifying your root `pom.xml`.

---

## Build Summary

1. Arrange the project using the required directory structure.
2. Place the main `pom.xml` in the project root.
3. Verify all module `relativePath` values are correct.
4. Run:

```bash
mvn clean install -DskipTests
```

5. Maven will automatically build all modules and resolve their dependencies.