import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import { setTimeout as sleep } from "node:timers/promises";

const origin = "http://127.0.0.1:3000";
const mode = process.argv[2] ?? "success";
const validModes = ["success","fail-check","fail-generate","fail-finalize","multi","submit-only","inspect","verify","verify-existing"];
assert.ok(validModes.includes(mode), "Mode: " + validModes.join(", "));
const user = "00000000-0000-4000-8000-000000000001";
let cookie;
async function request(path, options = {}) {
  const result = await fetch(origin + path, {
    ...options,
    signal: AbortSignal.timeout(15000),
    headers: { ...(cookie ? { Cookie: cookie } : {}), "X-Studio-Request": "1", "X-Studio-User": user, ...options.headers },
  });
  if (!result.ok) throw new Error(path + " returned " + result.status + ": " + await result.text());
  return result;
}
const login = await request("/api/session", {
  method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ userId: user }),
});
cookie = login.headers.get("set-cookie").split(";",1)[0];
const getTask = async (id) => (await request("/api/tasks/" + id)).json();
const terminal = (task) => task.status === "SUCCEEDED" || task.status === "FAILED";
const checkpoint = "/tmp/studio-task-checkpoint.json";

async function assertFinal(task, failure = "NONE") {
  if (failure === "NONE") {
    assert.equal(task.status,"SUCCEEDED");
    assert.ok(task.resultUrl);
    assert.deepEqual(task.steps.map((step) => step.status),["SUCCEEDED","SUCCEEDED","SUCCEEDED"]);
    const image = await request(task.resultUrl);
    assert.equal(image.headers.get("content-type"),"image/png");
    assert.ok((await image.arrayBuffer()).byteLength > 100);
  } else {
    assert.equal(task.status,"FAILED");
    assert.equal(task.failureStage,failure);
    assert.equal(task.errorCode,"SIMULATED_FAILURE");
    assert.equal(task.resultUrl,null);
    const stages=["ASSET_CHECK","GENERATE","FINALIZE"];
    const at=stages.indexOf(failure);
    assert.deepEqual(task.steps.map((step) => step.status),stages.map((_,i) => i<at ? "SUCCEEDED" : i===at ? "FAILED" : "SKIPPED"));
  }
  const again=await getTask(task.id);
  assert.equal(again.status,task.status);
  assert.equal(again.version,task.version);
}

async function wait(id) {
  const deadline=Date.now()+60000;
  let previous="";
  while(Date.now()<deadline) {
    const task=await getTask(id);
    const label=task.status+"/"+task.currentStage;
    if(label!==previous) console.log(id,label,task.errorMessage ?? "");
    previous=label;
    if(terminal(task)) return task;
    await sleep(300);
  }
  throw new Error("Timed out waiting for task " + id);
}

if(mode==="inspect") {
  assert.ok(process.argv[3],"Provide a task ID");
  console.log(JSON.stringify(await getTask(process.argv[3]),null,2));
} else if(mode==="verify-existing") {
  const saved=JSON.parse(readFileSync(checkpoint,"utf8"));
  await assertFinal(await getTask(saved.successId));
  const stopped=await getTask(saved.interruptedId);
  assert.equal(stopped.status,"FAILED");
  assert.ok(["WORKER_INTERRUPTED","SERVICE_RESTARTED"].includes(stopped.errorCode));
  console.log("Saved success result survived; interrupted work remained terminal.");
} else {
  const image=await request("/api/demo-assets/light-cover.jpg");
  const form=new FormData();
  form.set("templateId","afternoon-light");
  form.set("file",new Blob([await image.arrayBuffer()],{type:"image/jpeg"}),"example.jpg");
  const asset=await (await request("/api/assets",{method:"POST",body:form})).json();
  async function submit(failStage="NONE",delays={ASSET_CHECK:300,GENERATE:1200,FINALIZE:300}) {
    const accepted=await request("/api/tasks",{
      method:"POST",headers:{"Content-Type":"application/json"},
      body:JSON.stringify({templateId:"afternoon-light",assetIds:[asset.id],simulation:{failStage,delaysMs:delays}}),
    });
    assert.equal(accepted.status,202);
    const task=await accepted.json();
    assert.equal(task.status,"QUEUED");
    assert.equal(accepted.headers.get("location"),"/api/tasks/"+task.id);
    console.log("Accepted:",task.id);
    return task;
  }
  if(mode==="submit-only") {
    const task=await submit("NONE",{ASSET_CHECK:1000,GENERATE:15000,FINALIZE:1000});
    console.log("This client now exits. Inspect later with: node scripts/tasks-demo.mjs inspect "+task.id);
  } else if(mode==="multi") {
    const created=[];
    for(const failure of ["NONE","GENERATE","NONE"]) created.push({task:await submit(failure),failure});
    const active=await (await request("/api/tasks/active")).json();
    assert.ok(created.some(({task})=>active.some((item)=>item.id===task.id && item.status==="QUEUED")));
    for(const {task,failure} of created) await assertFinal(await wait(task.id),failure);
  } else if(mode==="verify") {
    let successId;
    for(const failure of ["NONE","ASSET_CHECK","GENERATE","FINALIZE"]) {
      const task=await submit(failure);
      await assertFinal(await wait(task.id),failure);
      if(failure==="NONE") successId=task.id;
    }
    const created=[];
    for(let i=0;i<3;i++) created.push(await submit());
    for(const task of created) await assertFinal(await wait(task.id));
    // Leave long-running work for the next CI restart. This file contains IDs only, never cookies.
    const interrupted=await submit("NONE",{ASSET_CHECK:30000,GENERATE:30000,FINALIZE:30000});
    writeFileSync(checkpoint,JSON.stringify({successId,interruptedId:interrupted.id}));
    console.log("Task acceptance, phases, all failures and multiple tasks passed; restart checkpoint prepared.");
  } else {
    const failure=({"fail-check":"ASSET_CHECK","fail-generate":"GENERATE","fail-finalize":"FINALIZE"})[mode] ?? "NONE";
    await assertFinal(await wait((await submit(failure)).id),failure);
  }
}
