const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_ultron_123";
const BOB_UID = "bob_ultron_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users are denied access to all Ultron Omega collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      category: "FACT",
      title: "Secret",
      encryptedContent: "iv:cipher",
      searchKeywords: "secret",
      importance: 5,
      pinned: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("audit_logs").get());
});

test("Owner can create, list, partially update, and delete encrypted memories; shadow fields & cross-user writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const validMemory = {
    userId: ALICE_UID,
    category: "PREFERENCE",
    title: "Preferred Voice",
    encryptedContent: "base64IvAndCiphertextPayload",
    searchKeywords: "voice deep male english",
    importance: 8,
    pinned: true,
    createdAt: created,
    updatedAt: created,
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set(validMemory)
  );

  // List own memories succeeds even on filtered/unfiltered owner subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );

  // Partial update preserving createdAt and userId succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").update({
      title: "Updated Voice Pref",
      pinned: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow field ("id") fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_2").set({
      ...validMemory,
      id: "mem_2",
    })
  );

  // Cross-user read/write fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").delete()
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_3").set({
      ...validMemory,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").delete()
  );
});

test("Owner can manage conversations and append-only citations & audit logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Architecture Briefing",
      modelTier: "gemini-4-argon",
      languageCode: "en-US",
      lastMessagePreview: "Systems online.",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("citations").doc("cit_1").set({
      userId: ALICE_UID,
      query: "Kotlin 2.2 features",
      title: "Kotlin Releases",
      url: "https://kotlinlang.org/docs/releases.html",
      snippet: "Kotlin 2.2 introduces...",
      summary: "Summary of Kotlin 2.2.",
      createdAt: now,
    })
  );

  // Citations are immutable (no update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("citations").doc("cit_1").update({
      title: "Tampered",
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("log_1").set({
      userId: ALICE_UID,
      actor: "AGENT",
      action: "TORCH_TOGGLE",
      riskLevel: "LOW",
      outcome: "EXECUTED",
      details: "Flashlight enabled by user request",
      integrityHash: "abc123def456",
      createdAt: now,
    })
  );

  // Audit logs cannot be updated (tamper-proof)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("audit_logs").doc("log_1").update({
      outcome: "DENIED",
    })
  );

  // Cross-user audit read fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("audit_logs").get()
  );
});
