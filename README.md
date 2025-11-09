# Change me

Kotlin Ktor game server template with ready to write docs website.

## Requirements

- **Java 25+**
- **MongoDB v8.0+**
- **Node v18.20.8 or v20.3.0, v22.0.0+** (only for docs)

## Setup

To run the server, ensure MongoDB is running on (default `mongodb://localhost:27017`). Then, run the following command:

```bash
.\gradlew run
```

- File and API server runs on `127.0.0.1:8080`
- Socket server runs on `127.0.0.1:7777`

You can also run the server by executing the script `dev.bat/sh` or via IntelliJ IDE run plugin.

## Build

To build the server, simply run the `build.bat/sh` script. Output will be in `deploy/`. Run the deployment server using `java -jar changeme.jar` or simply execute the script `autorun.bat/sh`.

For manual build:

```bash
.\gradlew shadowJar
```

Optionally, you can build the documentation website:

```bash
cd docs
npm install
npm run build
```

Then, move the `dist` directory to `deploy/`.

Server will be available on the same port as development mode. The documentation website, if built, will be available on `127.0.0.1:8080/docs`.

## Configuration

Various server settings can be set from `src/main/resources/application.yaml`.

Some configuration can be set via environment variables, those typically have `$` within the config.

For example, in PowerShell (Windows):

```ps1
$env:DEV_MODE = "false"
$env:ADMIN = "true"
java -jar changeme.jar
```

## Structure

<details>
<summary>Open</summary>

```
.
├── src/main/kotlin/
│   ├── api/                    # REST API endpoints
│   │   ├── routes/             # API route handlers
│   │   └── models/             # API request/response models
│   ├── context/                # States model (server, player) and tracker
│   ├── core/                   # Core game logic (domain repository and service)
│   │   ├── data/               # Global game data, game definitions, and parser
│   │   ├── model/              # Game data models
│   ├── data/                   # Database implementation
│   │   ├── collection/         # Database collection models
│   │   security                # Security functionality
│   │   ├── validation/         # Validation system
│   ├── server/                 # Game servers and implementation
│   │   ├── core/               # Core server definitions
│   │   ├── handler/            # Message handlers
│   │   ├── messaging/          # Message format definitions
│   │   ├── protocol/           # Protocol and codec definitions
│   │   └── tasks/              # Server task system
│   │       └── impl/           # Server task implementation
│   ├── user/                   # User management
│   │   ├── auth/               # Authentication and session system
│   │   └── model/              # User models
│   ├── utils/                  # Utility package
│   │   ├── constants/          # Global constants
│   │   ├── functions/          # Utility functions
│   │   └── logging/            # Logger system
│   └── Application.kt          # Application entry point
├── src/main/resources/
│   ├── application.yaml        # Server configuration
│   └── logback.xml             # Logging configuration (not much used)
├── static/                     # Game assets
├── docs/                       # Documentation
│   └── src/content/docs/       # Markdown documentation
├── deploy/                    # Build output
├── .logs/                      # Logs file
├── .telemetry/                 # Telemetry file
└── dev.bat/dev.sh              # Script to run development server
└── build.bat/build.sh          # Script to build the server
└── autorun.bat/autorun.sh      # Script to run deployment server (in deploy)
```

</details>

## Docs

Empty documentation template ([built with Starlight](https://starlight.astro.build/)) is available on `docs/`

To run the website locally:

```bash
cd docs
npm install
npm run dev
```

Docs running on `http://localhost:4321/docs`

For more info on setup and configuration, please see
the [official Starlight documentation](https://starlight.astro.build/getting-started/).

### How to add new page:

1. A page must be `.md` file and is enforced to have this on top of them (frontmatter):

```
---
title: Subfolder Example
slug: folderA/folderB/example
description: example
---
```

2. Replace the title appropriately. The description is optional; you can set it to be the same as the title. Any images
   or videos should be placed in `src/assets/`.
3. The slug is produced from the directory structure. For instance, this page is named `example.md` and is under the
   `folderB` within the `folderA`.
4. Next, add the page to the sidebar.
   1. Begin by editing the `astro.config.mjs`.
   2. Follow the existing sidebar link
      format. [More details on official documentation](https://starlight.astro.build/guides/sidebar/).
