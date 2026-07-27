# 11 — IDE Setup (mixed-IDE team)

MediFlow is **IDE-agnostic**: the source of truth is the Maven CLI + `docs/ai/` + git, never IDE config. Any IDE works, but our stack uses **annotation processors (Lombok, MapStruct)** — the #1 source of "works on my machine" pain. This page makes every IDE behave the same.

## Non-negotiables (all IDEs)

1. **Build & test through Maven, not the IDE runner.** `mvn verify` is the truth; CI runs Maven. The IDE is just an editor.
2. **Enable annotation processing** — required for Lombok getters/builders and MapStruct mappers. Without it you get phantom `cannot find symbol` / null mappers.
3. **Never commit IDE config.** `.idea/`, `*.iml`, `.vscode/`, `.settings/`, `.classpath`, `.project` are gitignored. Formatting is governed by `.editorconfig` (all three IDEs read it).
4. Import as a **Maven multi-module** project from the root `pom.xml`.

## IntelliJ IDEA (recommended, Community is enough)

- Open the root `pom.xml` → "Open as Project". Modules auto-detected.
- **Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing.**
- Install the **Lombok** plugin (bundled in recent IntelliJ; enable it).
- MapStruct: works once annotation processing is on; the `mapstruct-processor` is already a dependency.
- Set Project SDK = **JDK 21**. Enable `.editorconfig` support (on by default).
- Optional: Claude Code JetBrains plugin, or use the built-in terminal for the Claude Code CLI.

## VS Code → prefer **Cursor** (VS Code fork)

- Install the **Extension Pack for Java** + **Spring Boot Extension Pack**.
- Lombok: install **Lombok Annotations Support** extension; annotation processing is honored by the Java language server.
- MapStruct: generated sources land in `target/generated-sources`; run `mvn compile` once so the language server sees them.
- Set `java.configuration.runtimes` to JDK 21.
- **Cursor** is a VS Code fork and auto-reads `.cursor/rules/project.mdc`, so VS Code-leaning devs get the AI framework for free. Plain VS Code users: use the Claude Code CLI in the integrated terminal.

## NetBeans (⚠️ needs manual Lombok — read this)

NetBeans has the weakest support for our stack; follow these or you WILL hit mapper/getter errors:

1. Install **JDK 21** and set it as the platform.
2. **Lombok must be wired manually:** add the Lombok jar to `netbeans.conf` (`netbeans_default_options` → `-J-javaagent:<path>/lombok.jar`), or run "Actions → Install Lombok" if your NetBeans/plugin offers it. Restart the IDE.
3. Ensure **annotation processing is enabled in the project** (Project Properties → Build → Compiling → "Enable Annotation Processing" + "…in Editor").
4. Always run `mvn compile` after pulling so MapStruct regenerates mappers; don't rely on NetBeans incremental compile for generated sources.
5. If you still see `cannot find symbol getXxx()` while `mvn verify` passes → it's a NetBeans annotation-processing issue, not a code bug. Fix the IDE, don't "add manual getters".

## Sanity check (any IDE)

```bash
mvn -q -DskipTests install   # if this passes but your IDE shows red, the IDE (not the code) is misconfigured
```

The rule of thumb: **if the Maven build is green, the code is correct — fix the IDE, not the code.**
