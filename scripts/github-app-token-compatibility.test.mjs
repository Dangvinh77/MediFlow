import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';

import {
  classifyInstallationToken,
  createAppJwt,
} from './github-app-token-compatibility.mjs';

test('accepts classic opaque installation tokens without a maximum length', () => {
  const token = `ghs_${'a'.repeat(700)}`;
  assert.deepEqual(classifyInstallationToken(token), {
    format: 'stateful',
    length: token.length,
  });
});

test('accepts stateless JWT installation tokens with dots and URL-safe characters', () => {
  const token = `ghs_app_123.${'A-_9'.repeat(100)}.${'z'.repeat(100)}`;
  assert.deepEqual(classifyInstallationToken(token), {
    format: 'stateless',
    length: token.length,
  });
});

test('rejects malformed installation tokens', () => {
  for (const token of [
    'github_pat_example',
    'ghs_too.short',
    `ghs_${'a'.repeat(36)}..${'b'.repeat(36)}`,
    `ghs_${'a'.repeat(36)}.${'b'.repeat(36)}.${'c'.repeat(36)}.extra`,
    `ghs_${'a'.repeat(35)}!`,
  ]) {
    assert.throws(() => classifyInstallationToken(token), /installation token/i);
  }
});

test('creates a signed, short-lived RS256 app JWT', () => {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const jwt = createAppJwt('client-id', privateKey, 1_800_000_000);
  const [header, payload, signature] = jwt.split('.');

  assert.deepEqual(JSON.parse(Buffer.from(header, 'base64url')), { alg: 'RS256', typ: 'JWT' });
  assert.deepEqual(JSON.parse(Buffer.from(payload, 'base64url')), {
    iat: 1_799_999_940,
    exp: 1_800_000_600,
    iss: 'client-id',
  });
  assert.equal(verify(
    'RSA-SHA256',
    Buffer.from(`${header}.${payload}`),
    publicKey,
    Buffer.from(signature, 'base64url'),
  ), true);
});
