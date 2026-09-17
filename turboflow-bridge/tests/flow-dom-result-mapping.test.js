import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../flow-dom-method.js', import.meta.url), 'utf8');
const claimStart = source.indexOf('async function claimGenerationTile');
const waitStart = source.indexOf('async function waitForGeneratedImage', claimStart);
const runStart = source.indexOf('async function runDomTranslate', waitStart);
const claimSource = source.slice(claimStart, waitStart);
const waitSource = source.slice(waitStart, runStart);

test('claims only one newly-created tile and rejects ambiguous tile creation', () => {
  assert.match(source, /const claimedTileKeys = new Map\(\)/);
  assert.match(claimSource, /newTiles\.length > 1/);
  assert.match(claimSource, /refusing ambiguous result mapping/);
  assert.match(claimSource, /!claimedTileKeys\.has\(key\)/);
  assert.match(claimSource, /createTileClaim\(newTile, preSubmitImageIds, assignmentId\)/);
  assert.doesNotMatch(claimSource, /newImage\s*=\s*resultImages/);
});

test('never recovers a detached tile from a page-global unclaimed result', () => {
  assert.match(waitSource, /findClaimReplacement\(claim\)/);
  assert.match(waitSource, /data-turboflow-assignment-id/);
  assert.doesNotMatch(waitSource, /unclaimedResults/);
  assert.doesNotMatch(waitSource, /replacements\[claim\.position\]/);
});
