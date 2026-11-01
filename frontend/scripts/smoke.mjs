import assert from "node:assert/strict";
import { existsSync, readFileSync, writeFileSync } from "node:fs";

const origin = "http://127.0.0.1:3000";
const categoriesResponse = await fetch(origin + "/api/categories");
assert.equal(categoriesResponse.status, 200);
const categories = await categoriesResponse.json();
assert.equal(categories.length, 3);
const templatesResponse = await fetch(origin + "/api/templates");
assert.equal(templatesResponse.status, 200);
const templates = await templatesResponse.json();
assert.equal(templates.length, 6);
assert.deepEqual([...new Set(templates.map((item) => item.requiredImageCount))].sort(), [1, 2, 3]);
for (const template of templates) {
  for (const url of [template.coverUrl, template.sampleUrl]) {
    const image = await fetch(origin + url);
    assert.equal(image.status, 200, url);
    assert.match(image.headers.get("content-type"), /^image\/(jpeg|png)/);
    assert.ok((await image.arrayBuffer()).byteLength > 100);
  }
}
console.log("Same-origin catalog and packaged images passed.");

const userA = "00000000-0000-4000-8000-000000000001";
const userB = "00000000-0000-4000-8000-000000000002";
async function login(userId, previous) {
  const result = await fetch(origin + "/api/session", {
    method: "POST", headers: { "Content-Type": "application/json", "X-Studio-Request": "1", ...(previous ? { Cookie: previous } : {}) },
    body: JSON.stringify({ userId }),
  });
  assert.equal(result.status, 200);
  const setCookie = result.headers.get("set-cookie");
  assert.ok(setCookie?.includes("HttpOnly"));
  return setCookie.split(";", 1)[0];
}
let cookie = await login(userA);
const session = await fetch(origin + "/api/session", { headers: { Cookie: cookie } });
assert.equal((await session.json()).id, userA);
// Only a resource ID is retained; no session token is written. A second CI invocation after
// backend recreation verifies that both the database row and uploaded file survived.
const checkpoint = "/tmp/studio-smoke-asset-id";
if (existsSync(checkpoint)) {
  const previousId = readFileSync(checkpoint, "utf8");
  const persisted = await fetch(origin + "/api/assets/" + previousId + "/content", { headers: { Cookie: cookie } });
  assert.equal(persisted.status, 200, "Uploaded file must survive Java container recreation");
}
const imageBytes = await (await fetch(origin + templates[0].coverUrl)).arrayBuffer();
const form = new FormData();
form.set("templateId", "afternoon-light");
form.set("file", new Blob([imageBytes], { type: "image/jpeg" }), "example.jpg");
const upload = await fetch(origin + "/api/assets", {
  method: "POST", headers: { Cookie: cookie, "X-Studio-Request": "1", "X-Studio-User": userA }, body: form,
});
assert.equal(upload.status, 201, "Multipart must pass through the Next.js rewrite");
const asset = await upload.json();
writeFileSync(checkpoint, asset.id);
const check = await fetch(origin + "/api/assets/validate", {
  method: "POST", headers: { Cookie: cookie, "Content-Type": "application/json", "X-Studio-Request": "1", "X-Studio-User": userA },
  body: JSON.stringify({ templateId: "afternoon-light", assetIds: [asset.id] }),
});
assert.equal(check.status, 200);
assert.equal((await check.json()).ready, true);
cookie = await login(userB, cookie);
assert.equal((await fetch(origin + asset.previewUrl, { headers: { Cookie: cookie } })).status, 404);
cookie = await login(userA, cookie);
assert.equal((await fetch(origin + asset.previewUrl, { headers: { Cookie: cookie } })).status, 200);
console.log("Cookie forwarding, multipart upload, readiness and owner isolation passed.");
