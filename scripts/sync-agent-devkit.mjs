import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, extname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const mode = process.argv[2];

if (!new Set(["--write", "--check"]).has(mode)) {
  console.error("Usage: node scripts/sync-agent-devkit.mjs --write|--check");
  process.exit(2);
}

function read(relativePath) {
  return readFileSync(join(ROOT, relativePath), "utf8").replaceAll("\r\n", "\n");
}

function parseFrontmatter(relativePath) {
  const source = read(relativePath);
  const match = source.match(/^---\n([\s\S]*?)\n---\n([\s\S]*)$/);
  if (!match) throw new Error(`${relativePath}: missing YAML frontmatter`);

  const metadata = {};
  for (const line of match[1].split("\n")) {
    const separator = line.indexOf(":");
    if (separator < 1) continue;
    const key = line.slice(0, separator).trim();
    let value = line.slice(separator + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    metadata[key] = value;
  }
  return { metadata, body: match[2] };
}

function tomlString(value) {
  return JSON.stringify(value);
}

function listMarkdown(relativeDir) {
  return readdirSync(join(ROOT, relativeDir))
    .filter((name) => extname(name) === ".md")
    .sort();
}

function agentToml(sourcePath) {
  const { metadata, body } = parseFrontmatter(sourcePath);
  if (!metadata.name || !metadata.description) throw new Error(`${sourcePath}: name and description are required`);
  if (body.includes("'''")) throw new Error(`${sourcePath}: body contains unsupported TOML literal delimiter`);
  const canWrite = /(^|,\s*)(Write|Edit)(,|$)/.test(metadata.tools ?? "");
  return [
    `name = ${tomlString(metadata.name.replaceAll("-", "_"))}`,
    `description = ${tomlString(metadata.description)}`,
    `sandbox_mode = ${tomlString(canWrite ? "workspace-write" : "read-only")}`,
    "developer_instructions = '''",
    body.trim(),
    "'''",
    "",
  ].join("\n");
}

function commandSkill(sourcePath, name) {
  const { metadata, body: originalBody } = parseFrontmatter(sourcePath);
  if (!metadata.description) throw new Error(`${sourcePath}: description is required`);
  let body = originalBody.trim();
  let description = metadata.description;

  if (name === "new-service") {
    description = "Scaffold a Spring Boot microservice from the MediFlow blueprint. Use when creating a named backend service module.";
    body = `## Input\n\nRequire a service name such as \`patient\` before starting.\n\n${body}`
      .replaceAll("$ARGUMENTS", "<service-name>")
      .replaceAll(".claude/skills/new-microservice/SKILL.md", ".agents/skills/new-microservice/SKILL.md")
      .replaceAll("`spring-boot-engineer` agent", "`spring_boot_engineer` Codex agent");
  }

  if (name === "review-pr") {
    body = body
      .replace(
        "Run `git diff main...HEAD` (or `git diff` for uncommitted work) to get the changeset.",
        "Resolve the default branch from `origin/HEAD` (fall back to `master`, then `main`), then run `git diff <base>...HEAD`; use `git diff` for uncommitted work."
      )
      .replaceAll("`.claude/agents/code-reviewer.md`", "`.codex/agents/code-reviewer.toml`")
      .replaceAll("the `code-reviewer` agent", "the `code_reviewer` Codex agent");
  }

  if (name === "index-codebase") {
    body = body.replace(
      "Note: codebase-memory-mcp is Claude-side. Codex/Cursor users rely on `docs/ai/` instead and can skip this.",
      "The MCP is optional for both Claude and Codex. When unavailable, fall back to repository documentation and text search."
    );
  }

  return [
    "---",
    `name: ${name}`,
    `description: ${JSON.stringify(description)}`,
    "---",
    "",
    body,
    "",
  ].join("\n");
}

function expectedGeneratedFiles() {
  const files = new Map();

  for (const filename of listMarkdown(".claude/agents")) {
    const basename = filename.slice(0, -3);
    files.set(`.codex/agents/${basename}.toml`, agentToml(`.claude/agents/${filename}`));
  }

  for (const filename of listMarkdown(".claude/commands")) {
    const name = filename.slice(0, -3);
    files.set(`.agents/skills/${name}/SKILL.md`, commandSkill(`.claude/commands/${filename}`, name));
  }

  files.set(
    ".agents/skills/new-microservice/SKILL.md",
    read(".claude/skills/new-microservice/SKILL.md")
  );

  const claudeSettings = JSON.parse(read(".claude/settings.json"));
  files.set(
    ".codex/hooks.json",
    `${JSON.stringify({
      description: "Mirror of MediFlow SessionStart hooks from .claude/settings.json for Codex.",
      hooks: claudeSettings.hooks,
    }, null, 2)}\n`
  );

  return files;
}

function writeGenerated(files) {
  for (const [relativePath, content] of files) {
    const absolutePath = join(ROOT, relativePath);
    mkdirSync(dirname(absolutePath), { recursive: true });
    writeFileSync(absolutePath, content, "utf8");
    console.log(`[write] ${relativePath}`);
  }
}

function codexMcpNames(config) {
  return [...config.matchAll(/^\[mcp_servers\.(?:"([^"]+)"|([A-Za-z0-9_-]+))\]$/gm)]
    .map((match) => match[1] ?? match[2])
    .sort();
}

function tracked(relativePath) {
  try {
    return execFileSync("git", ["ls-files", "--error-unmatch", "--", relativePath], {
      cwd: ROOT,
      stdio: "ignore",
    }) !== undefined;
  } catch {
    return false;
  }
}

function audit(files) {
  const errors = [];
  for (const [relativePath, expected] of files) {
    const absolutePath = join(ROOT, relativePath);
    if (!existsSync(absolutePath)) {
      errors.push(`${relativePath}: missing (run --write)`);
      continue;
    }
    const actual = read(relativePath);
    if (actual !== expected) errors.push(`${relativePath}: out of sync (run --write)`);
  }

  const claudeMcp = JSON.parse(read(".mcp.json")).mcpServers;
  const codexConfig = read(".codex/config.toml");
  const claudeNames = Object.keys(claudeMcp).sort();
  const codexNames = codexMcpNames(codexConfig);
  if (JSON.stringify(claudeNames) !== JSON.stringify(codexNames)) {
    errors.push(`MCP server names differ: Claude=${claudeNames.join(",")} Codex=${codexNames.join(",")}`);
  }
  if (claudeMcp["codebase-memory-mcp"]?.command !== "codebase-memory-mcp") {
    errors.push(".mcp.json: codebase-memory-mcp must resolve from PATH");
  }
  if (!codexConfig.includes('command = "codebase-memory-mcp"')) {
    errors.push(".codex/config.toml: codebase-memory-mcp command differs");
  }
  const mermaidUrl = claudeMcp.mermaidchart?.url;
  if (!mermaidUrl || !codexConfig.includes(`url = "${mermaidUrl}"`)) {
    errors.push("Mermaid MCP URL differs between Claude and Codex");
  }

  if (!read("AGENTS.md").includes("docs/ai/14-flutter.md")) errors.push("AGENTS.md: Flutter blueprint missing");
  for (const path of ["frontend/AGENTS.md", "mobile/AGENTS.md"]) {
    if (!existsSync(join(ROOT, path))) errors.push(`${path}: nested instruction entry point missing`);
  }
  if (tracked(".claude/settings.local.json")) errors.push(".claude/settings.local.json must remain untracked");

  const portableFiles = [
    ".mcp.json",
    ".codex/config.toml",
    ".claude/mcp-wrapper.bat",
    ".claude/mcp-wrapper.sh",
    ...files.keys(),
  ];
  for (const path of portableFiles) {
    if (!existsSync(join(ROOT, path))) continue;
    const content = read(path).toLowerCase();
    if (content.includes("c:\\users\\hp") || content.includes("c:\\users\\vip")) {
      errors.push(`${path}: contains a personal Windows path`);
    }
  }

  if (errors.length) {
    console.error("Agent dev-kit audit failed:");
    for (const error of errors) console.error(`- ${error}`);
    process.exitCode = 1;
    return;
  }

  const agentCount = listMarkdown(".claude/agents").length;
  const skillCount = listMarkdown(".claude/commands").length + 1;
  console.log(`AGENT_DEVKIT_AUDIT=PASS agents=${agentCount} skills=${skillCount} hooks=1 rules=2 mcp=${claudeNames.length}`);
}

const files = expectedGeneratedFiles();
if (mode === "--write") writeGenerated(files);
audit(files);
