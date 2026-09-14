#!/usr/bin/env node

import { createSign } from 'node:crypto';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const TOKEN_PATTERN = /^ghs_[A-Za-z0-9.\-_]{36,}$/;
const API_VERSION = '2022-11-28';

function encodeJson(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

export function createAppJwt(clientId, privateKey, nowSeconds = Math.floor(Date.now() / 1000)) {
  const header = encodeJson({ alg: 'RS256', typ: 'JWT' });
  const payload = encodeJson({
    iat: nowSeconds - 60,
    exp: nowSeconds + 600,
    iss: clientId,
  });
  const unsigned = `${header}.${payload}`;
  const signer = createSign('RSA-SHA256');
  signer.update(unsigned);
  signer.end();
  return `${unsigned}.${signer.sign(privateKey).toString('base64url')}`;
}

export function classifyInstallationToken(token) {
  if (typeof token !== 'string' || !TOKEN_PATTERN.test(token)) {
    throw new Error('GitHub App installation token has an unsupported format.');
  }
  const dotCount = [...token].filter((character) => character === '.').length;
  if (dotCount === 0) {
    return { format: 'stateful', length: token.length };
  }
  if (dotCount === 2 && token.slice(4).split('.').every(Boolean)) {
    return { format: 'stateless', length: token.length };
  }
  throw new Error('GitHub App installation token has an unsupported JWT structure.');
}

function mask(value) {
  process.stdout.write(`::add-mask::${value}\n`);
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`GitHub API ${options.method ?? 'GET'} ${url} failed (${response.status}): ${text}`);
  }
  return text ? JSON.parse(text) : null;
}

function runCommand(command, args, token, extraEnv = {}) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    env: { ...process.env, ...extraEnv },
  });
  if (result.status !== 0) {
    const details = `${result.stdout ?? ''}\n${result.stderr ?? ''}`.replaceAll(token, '***').trim();
    throw new Error(`${command} compatibility check failed: ${details || `exit ${result.status}`}`);
  }
}

async function verifyConsumers(token, repository, apiUrl, serverUrl) {
  await requestJson(`${apiUrl}/repos/${repository}`, {
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'X-GitHub-Api-Version': API_VERSION,
    },
  });

  runCommand('gh', ['api', `repos/${repository}`, '--silent'], token, {
    GH_TOKEN: token,
  });

  const askPassDir = await mkdtemp(path.join(tmpdir(), 'mediflow-token-'));
  const askPassPath = path.join(askPassDir, 'askpass.sh');
  try {
    await writeFile(askPassPath, [
      '#!/bin/sh',
      'case "$1" in',
      "  *Username*) printf '%s\\n' 'x-access-token' ;;",
      "  *) printf '%s\\n' \"$MEDIFLOW_APP_TOKEN\" ;;",
      'esac',
      '',
    ].join('\n'), { mode: 0o700 });
    runCommand('git', [
      'ls-remote',
      '--exit-code',
      `${serverUrl}/${repository}.git`,
      'HEAD',
    ], token, {
      GIT_ASKPASS: askPassPath,
      GIT_TERMINAL_PROMPT: '0',
      MEDIFLOW_APP_TOKEN: token,
    });
  } finally {
    await rm(askPassDir, { recursive: true, force: true });
  }
}

async function revokeToken(token, apiUrl) {
  await requestJson(`${apiUrl}/installation/token`, {
    method: 'DELETE',
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${token}`,
      'X-GitHub-Api-Version': API_VERSION,
    },
  });
}

async function createInstallationToken({
  appJwt,
  installationId,
  repositoryName,
  apiUrl,
  override,
}) {
  return requestJson(`${apiUrl}/app/installations/${installationId}/access_tokens`, {
    method: 'POST',
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${appJwt}`,
      'Content-Type': 'application/json',
      'X-GitHub-Api-Version': API_VERSION,
      'X-GitHub-Stateless-S2S-Token': override,
    },
    body: JSON.stringify({
      repositories: [repositoryName],
      permissions: {
        contents: 'write',
        pull_requests: 'write',
      },
    }),
  });
}

export async function main(env = process.env) {
  const required = ['APP_CLIENT_ID', 'APP_PRIVATE_KEY', 'GITHUB_REPOSITORY'];
  const missing = required.filter((name) => !env[name]);
  if (missing.length > 0) {
    throw new Error(`Missing environment variables: ${missing.join(', ')}`);
  }

  const apiUrl = env.GITHUB_API_URL ?? 'https://api.github.com';
  const serverUrl = env.GITHUB_SERVER_URL ?? 'https://github.com';
  const [owner, repositoryName] = env.GITHUB_REPOSITORY.split('/');
  if (!owner || !repositoryName) {
    throw new Error('GITHUB_REPOSITORY must use owner/repository format.');
  }

  const privateKey = env.APP_PRIVATE_KEY.includes('\\n')
    ? env.APP_PRIVATE_KEY.replaceAll('\\n', '\n')
    : env.APP_PRIVATE_KEY;
  const appJwt = createAppJwt(env.APP_CLIENT_ID, privateKey);
  mask(appJwt);

  const installation = await requestJson(`${apiUrl}/repos/${owner}/${repositoryName}/installation`, {
    headers: {
      Accept: 'application/vnd.github+json',
      Authorization: `Bearer ${appJwt}`,
      'X-GitHub-Api-Version': API_VERSION,
    },
  });

  for (const [override, expectedFormat] of [
    ['enabled', 'stateless'],
    ['disabled', 'stateful'],
  ]) {
    const response = await createInstallationToken({
      appJwt,
      installationId: installation.id,
      repositoryName,
      apiUrl,
      override,
    });
    const token = response.token;
    mask(token);
    try {
      const classification = classifyInstallationToken(token);
      if (classification.format !== expectedFormat) {
        throw new Error(`${override} returned ${classification.format}; expected ${expectedFormat}.`);
      }
      await verifyConsumers(token, env.GITHUB_REPOSITORY, apiUrl, serverUrl);
      process.stdout.write(`${override}: ${classification.format} token (${classification.length} chars) works with REST, gh and git.\n`);
    } finally {
      await revokeToken(token, apiUrl);
    }
  }
}

const isMain = process.argv[1]
  && path.resolve(process.argv[1]) === path.resolve(fileURLToPath(import.meta.url));
if (isMain) {
  main().catch((error) => {
    process.stderr.write(`GitHub App token compatibility failed: ${error.message}\n`);
    process.exitCode = 1;
  });
}
