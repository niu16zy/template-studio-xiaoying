import { chromium, expect } from "@playwright/test";
import assert from "node:assert/strict";

const origin = process.env.STUDIO_ORIGIN ?? "http://frontend:3000";
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: "zh-CN", timezoneId: "Asia/Shanghai" });
const page = await context.newPage();
page.setDefaultTimeout(15000);
const scriptErrors = [];
page.on("pageerror", (error) => scriptErrors.push(error.message));
const reads = new Map();
page.on("request", (request) => {
  if (request.method() === "GET" && /\/api\/tasks\/[a-f0-9-]+$/.test(request.url())) reads.set(request.url(), (reads.get(request.url()) ?? 0) + 1);
});
const usersResponse = await context.request.get(origin + "/api/demo/users");
assert.equal(usersResponse.status(), 200);
const users = await usersResponse.json();
const a = users.find((user) => user.id.endsWith("001"));
const b = users.find((user) => user.id.endsWith("002"));
async function select(user) {
  await page.getByRole("button", { name: /选择演示身份|演示用户/ }).first().click();
  const dialog = page.getByRole("dialog", { name: "演示身份" });
  await dialog.getByRole("button", { name: new RegExp(user.displayName) }).click();
  await expect(dialog.getByRole("status")).toContainText("当前：" + user.displayName);
  await dialog.getByRole("button", { name: "完成", exact: true }).click();
}
const taskCard = (id) => page.getByRole("article", { name: "午后光影任务 " + id, exact: true });
async function screenshot(name, fullPage = true) {
  // Full-page captures do not scroll through lazy images. Load them for the artifact only.
  await page.locator("img").evaluateAll((images) => Promise.all(images.map((image) => {
    image.loading = "eager";
    return image.decode().catch(() => {});
  })));
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: "/reports/" + name, fullPage });
}
let mode = "NONE";
// Only the acceptance harness controls the simulator; regular UI sends no override.
await page.route("**/api/tasks", async (route) => {
  if (route.request().method() !== "POST") return route.continue();
  const body = route.request().postDataJSON();
  body.simulation = { failStage: mode, delaysMs: { ASSET_CHECK: 300, GENERATE: 5000, FINALIZE: 300 } };
  await route.continue({ postData: JSON.stringify(body) });
});
async function submit() {
  await page.getByLabel("选择或替换素材 1").setInputFiles("/samples/sample-01.jpg");
  await expect(page.getByRole("button", { name: "开始制作", exact: true })).toBeEnabled();
  const accepted = page.waitForResponse((response) => response.url().endsWith("/api/tasks") && response.request().method() === "POST");
  await page.getByRole("button", { name: "开始制作", exact: true }).click();
  const response = await accepted; assert.equal(response.status(), 202);
  const task = await response.json();
  await expect(taskCard(task.id)).toBeVisible();
  await expect(page.getByRole("button", { name: "正在提交…" })).toHaveCount(0);
  return task;
}
try {
  await page.goto(origin);
  await expect(page.getByRole("link", { name: /^打开模板：/ })).toHaveCount(6);
  await page.getByRole("button", { name: "日常片刻", exact: true }).click();
  await expect(page.getByRole("link", { name: /^打开模板：/ })).toHaveCount(2);
  await screenshot("catalog-desktop.png");
  await select(a);
  await page.goto(origin + "/templates/afternoon-light");
  await expect(page.getByRole("heading", { name: "午后光影", exact: true })).toBeVisible();
  const success = await submit();
  mode = "GENERATE";
  const failure = await submit();
  await expect(taskCard(success.id).getByText("制作成功", { exact: true })).toBeVisible({ timeout: 30000 });
  await expect(taskCard(failure.id).getByText("制作失败", { exact: true })).toBeVisible({ timeout: 30000 });
  await expect(taskCard(failure.id)).toContainText("按演示设置");
  await screenshot("create-desktop.png", false);
  const preview = taskCard(success.id).getByRole("button", { name: "预览结果：午后光影" });
  await preview.click();
  await expect(page.getByRole("dialog", { name: "午后光影结果预览" })).toBeVisible();
  await page.keyboard.press("Escape"); await expect(preview).toBeFocused();
  const settled = [...reads.values()].reduce((sum, n) => sum + n, 0);
  await page.waitForTimeout(4500);
  assert.equal([...reads.values()].reduce((sum, n) => sum + n, 0), settled, "Terminal tasks should stop polling");
  mode = "NONE";
  const recovering = await submit();
  await page.route("**/api/tasks/" + recovering.id, (route) => route.fulfill({ status: 503, contentType: "application/json", body: '{"message":"acceptance outage"}' }));
  await expect(taskCard(recovering.id).getByRole("button", { name: "恢复查询" })).toBeVisible();
  await expect(taskCard(recovering.id).getByText("制作失败", { exact: true })).toHaveCount(0);
  await page.unroute("**/api/tasks/" + recovering.id);
  await taskCard(recovering.id).getByRole("button", { name: "恢复查询" }).click();
  await page.reload();
  await expect(taskCard(recovering.id)).toBeVisible();
  await expect(taskCard(recovering.id).getByText("制作成功", { exact: true })).toBeVisible({ timeout: 30000 });
  const background = await submit();
  await page.getByRole("link", { name: "模板灵感", exact: true }).click();
  const leaving = [...reads.values()].reduce((sum, n) => sum + n, 0);
  await page.waitForTimeout(7000);
  assert.equal([...reads.values()].reduce((sum, n) => sum + n, 0), leaving, "Leaving the task page should stop polling");
  await page.getByRole("link", { name: "我的记录", exact: true }).click();
  await expect(taskCard(background.id).getByText("制作成功", { exact: true })).toBeVisible();
  await select(b); await expect(taskCard(background.id)).toHaveCount(0);
  await select(a); await expect(taskCard(background.id)).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await screenshot("history-mobile.png");
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), "Mobile history must not overflow horizontally");
  await page.goto(origin + "/templates/afternoon-light");
  await expect(page.getByLabel("选择或替换素材 1")).toBeEnabled();
  await expect(taskCard(background.id)).toBeVisible();
  await expect(taskCard(background.id).locator(".result-trigger img")).toHaveJSProperty("complete", true);
  await screenshot("create-mobile.png");
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), "Mobile creation must not overflow horizontally");
  assert.deepEqual(scriptErrors, [], "No browser runtime errors");
  console.log("Browser acceptance passed: categories, upload/submission, mixed tasks, result preview/Escape focus, query recovery, refresh, stop polling, background completion, identity switch and 390px layout.");
} catch (error) {
  await page.screenshot({ path: "/reports/failure.png", fullPage: true }).catch(() => {});
  throw error;
} finally { await browser.close(); }
