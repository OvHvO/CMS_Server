# Getting Started

## Project Structure

Please make sure your project is organized exactly as shown below.

```text
root/
├── Clinic_Management_System/
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

### Parent POM Configuration

The `pom.xml` files inside the following modules:

- `Clinic_Management_System/client`
- `CMS_Server/common`
- `CMS_Server/server`

use the `<parent>` tag with **relative paths** to reference the main `pom.xml`.

If you change the project directory structure, you **must update the `relativePath`** in each module's `pom.xml` accordingly.

---

### Main POM Modules

The main `pom.xml` uses the `<modules>` section to include:

- `CMS_Server/common`
- `CMS_Server/server`
- `Clinic_Management_System/client`

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