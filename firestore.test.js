const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const CHARLIE_UID = "charlie_uid_3";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

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

async function seedPublicProfile(uid, username, fullName, discoverable = true) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = pastDate(5000);
    await db.collection("publicProfiles").doc(uid).set({
      userId: uid,
      fullName,
      username,
      bio: "Hello on Linkora",
      online: true,
      discoverable,
      lastSeenAt: now,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated users are denied all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("publicProfiles").where("discoverable", "==", true).get());
  await assertFails(unauth.collection("usernames").doc("alice_k").get());
  await assertFails(unauth.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("stories").where("visibility", "==", "public").get());
  await assertFails(unauth.collection("calls").where("memberUids", "array-contains", ALICE_UID).get());
});

test("Private user account PII isolation and owner create/update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice Khan",
      username: "alice_khan",
      email: "alice@example.com",
      bio: "Building cool things",
      online: true,
      discoverable: true,
      profileCompleted: true,
      notificationsEnabled: true,
      friendUids: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Updated bio",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user PII read and list denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());

  // Shadow field injection rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
});

test("Public profiles discovery query succeeds; unfiltered query fails", async () => {
  await seedPublicProfile(ALICE_UID, "alice_k", "Alice", true);
  await seedPublicProfile(BOB_UID, "bob_m", "Bob", false);

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("publicProfiles").where("discoverable", "==", true).get()
  );
  await assertFails(aliceDb.collection("publicProfiles").get());
});

test("Unique username reservation prevents duplicates and spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("usernames").doc("linkora_star").get());
  await assertSucceeds(
    aliceDb.collection("usernames").doc("linkora_star").set({
      userId: ALICE_UID,
      username: "linkora_star",
      createdAt: pastDate(1000),
    })
  );

  // Bob cannot hijack or update Alice's username
  await assertFails(
    bobDb.collection("usernames").doc("linkora_star").set({
      userId: BOB_UID,
      username: "linkora_star",
      createdAt: pastDate(500),
    })
  );
  await assertFails(bobDb.collection("usernames").doc("linkora_star").delete());
});

test("Friend request flow with recipient-only acceptance and terminal lock", async () => {
  await seedPublicProfile(ALICE_UID, "alice_k", "Alice");
  await seedPublicProfile(BOB_UID, "bob_m", "Bob");

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const reqId = `${ALICE_UID}_${BOB_UID}`;
  const now = pastDate(3000);

  await assertSucceeds(
    aliceDb.collection("friendRequests").doc(reqId).set({
      fromUid: ALICE_UID,
      toUid: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      fromName: "Alice",
      fromUsername: "alice_k",
      toName: "Bob",
      toUsername: "bob_m",
      status: "pending",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Sender cannot accept own request
  await assertFails(
    aliceDb.collection("friendRequests").doc(reqId).update({
      status: "accepted",
      updatedAt: pastDate(1000),
    })
  );

  // Outsider cannot read
  await assertFails(charlieDb.collection("friendRequests").doc(reqId).get());

  // Recipient can query and accept
  await assertSucceeds(
    bobDb.collection("friendRequests").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("friendRequests").doc(reqId).update({
      status: "accepted",
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state locked: cannot mutate accepted request
  await assertFails(
    bobDb.collection("friendRequests").doc(reqId).update({
      status: "declined",
      updatedAt: pastDate(500),
    })
  );
});

test("Chats and messages enforce member isolation and receiver-only seen status", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const chatId = `${ALICE_UID}_${BOB_UID}`;
  const now = pastDate(4000);

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      memberUsernames: { [ALICE_UID]: "alice_k", [BOB_UID]: "bob_m" },
      memberAvatars: {},
      lastMessageText: "Salam!",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      lastMessageStatus: "sent",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Send message as Alice
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      text: "Salam Bob!",
      audioDurationSec: 0,
      status: "sent",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can list messages with memberUids filter and mark delivered -> seen
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      status: "delivered",
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      status: "seen",
      updatedAt: pastDate(1000),
    })
  );

  // Bob can update real-time typing indicator, archive, lock, disappearing timer
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      [`typingUsers.${BOB_UID}`]: true,
      [`archivedBy.${BOB_UID}`]: true,
      [`lockedBy.${BOB_UID}`]: true,
      disappearingDurationMs: 24 * 60 * 60 * 1000,
      updatedAt: pastDate(500),
    })
  );

  // Alice can send photo and location attachments
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_photo").set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "photo",
      text: "Photo",
      mediaData: "data:image/jpeg;base64,abc123",
      audioDurationSec: 0,
      status: "sent",
      createdAt: pastDate(400),
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_loc").set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "location",
      text: "Shared location",
      locationLat: 31.5204,
      locationLng: 74.3587,
      locationLabel: "Lahore, Pakistan",
      audioDurationSec: 0,
      status: "sent",
      createdAt: pastDate(300),
      updatedAt: pastDate(300),
    })
  );

  // Group chat with 3 members succeeds
  await assertSucceeds(
    aliceDb.collection("chats").doc("group_linkora_1").set({
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob", [CHARLIE_UID]: "Charlie" },
      memberUsernames: { [ALICE_UID]: "alice_k", [BOB_UID]: "bob_m", [CHARLIE_UID]: "charlie_x" },
      memberAvatars: {},
      isGroup: true,
      groupName: "Linkora Squad",
      lastMessageText: "Group created",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      lastMessageStatus: "sent",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Charlie cannot read 1-on-1 chat or messages
  await assertFails(charlieDb.collection("chats").doc(chatId).get());
  await assertFails(
    charlieDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").get()
  );
});

test("Stories enforce public vs friends visibility and owner deletion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(3000);
  const expires = Date.now() + 24 * 60 * 60 * 1000;

  // Friends-only story by Alice allowing Bob
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_friends").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorUsername: "alice_k",
      storyType: "text",
      caption: "Friends only update!",
      backgroundHex: "#4F46E5",
      visibility: "friends",
      allowedViewerUids: [ALICE_UID, BOB_UID],
      expiresAtMillis: expires,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Public story by Alice
  await assertSucceeds(
    aliceDb.collection("stories").doc("story_public").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorUsername: "alice_k",
      storyType: "music",
      caption: "Listening to classics",
      musicTitle: "Pasoori",
      musicArtist: "Ali Sethi",
      backgroundHex: "#0EA5E9",
      visibility: "public",
      allowedViewerUids: [ALICE_UID],
      expiresAtMillis: expires,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob sees friends story; Charlie cannot see friends story, but can see public story
  await assertSucceeds(bobDb.collection("stories").doc("story_friends").get());
  await assertFails(charlieDb.collection("stories").doc("story_friends").get());
  await assertSucceeds(charlieDb.collection("stories").where("visibility", "==", "public").get());
  await assertSucceeds(bobDb.collection("stories").where("allowedViewerUids", "array-contains", BOB_UID).get());

  // Only author can delete
  await assertFails(bobDb.collection("stories").doc("story_friends").delete());
  await assertSucceeds(aliceDb.collection("stories").doc("story_friends").delete());
});

test("Call sessions enforce ringing -> connected -> media relay -> ended terminal state", async () => {
  await seedPublicProfile(ALICE_UID, "alice_k", "Alice");
  await seedPublicProfile(BOB_UID, "bob_m", "Bob");

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      calleeId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      callerName: "Alice",
      calleeName: "Bob",
      callType: "video",
      status: "ringing",
      durationSeconds: 0,
      callerAudioSeq: 0,
      calleeAudioSeq: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Charlie cannot read call
  await assertFails(charlieDb.collection("calls").doc("call_1").get());

  // Bob accepts call
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "connected",
      updatedAt: pastDate(3000),
    })
  );

  // Caller updates own video frame while connected
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      callerVideoFrame: "base64frame",
      callerAudioSeq: 1,
      updatedAt: pastDate(2000),
    })
  );

  // Caller ends call
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 42,
      updatedAt: pastDate(1000),
    })
  );

  // Terminal lock prevents updates after ended
  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      status: "connected",
      updatedAt: pastDate(500),
    })
  );
});

test("Private AI messages subcollection enforces owner-only access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("aiMessages").doc("ai_1").set({
      role: "user",
      text: "Salam Linkora AI, kya haal hai?",
      createdAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("aiMessages").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("aiMessages").get()
  );
});

test("User can manage blockedUids and chat supports delete-for-me, delete-for-everyone, and permanent delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice Khan",
      username: "alice_khan",
      bio: "Online",
      online: true,
      discoverable: true,
      profileCompleted: true,
      notificationsEnabled: true,
      friendUids: [BOB_UID],
      blockedUids: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice blocks Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      blockedUids: [BOB_UID],
      updatedAt: pastDate(3000),
    })
  );

  const chatId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      memberUsernames: { [ALICE_UID]: "alice_khan", [BOB_UID]: "bob_m" },
      memberAvatars: {},
      lastMessageText: "Hello!",
      lastMessageType: "text",
      lastMessageSenderId: ALICE_UID,
      lastMessageStatus: "sent",
      lastMessageAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_del").set({
      chatId,
      senderId: ALICE_UID,
      receiverId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      text: "Secret message",
      audioDurationSec: 0,
      status: "sent",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can delete-for-me (hide for self only)
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_del").update({
      deletedForUids: [BOB_UID],
      updatedAt: pastDate(2500),
    })
  );

  // Bob CANNOT delete Alice's message for everyone or permanently delete it
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_del").update({
      deletedForEveryone: true,
      text: "This message was deleted",
      updatedAt: pastDate(2000),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_del").delete()
  );

  // Alice (sender) CAN delete for everyone
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_del").update({
      deletedForEveryone: true,
      text: "This message was deleted",
      messageType: "text",
      audioData: null,
      audioDurationSec: 0,
      mediaData: null,
      fileData: null,
      fileName: null,
      fileMimeType: null,
      fileSizeBytes: 0,
      locationLat: null,
      locationLng: null,
      locationLabel: null,
      updatedAt: pastDate(1500),
    })
  );

  // Alice (sender) CAN permanently delete message
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_del").delete()
  );
});

test("WebRTC SDP offer/answer and ICE candidates exchange enforces participant isolation", async () => {
  await seedPublicProfile(ALICE_UID, "alice_k", "Alice");
  await seedPublicProfile(BOB_UID, "bob_m", "Bob");

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("calls").doc("webrtc_call_1").set({
      callerId: ALICE_UID,
      calleeId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      callerName: "Alice",
      calleeName: "Bob",
      callType: "video",
      status: "ringing",
      durationSeconds: 0,
      webrtcEnabled: true,
      callerAudioSeq: 0,
      calleeAudioSeq: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Caller publishes WebRTC SDP Offer while ringing
  await assertSucceeds(
    aliceDb.collection("calls").doc("webrtc_call_1").update({
      offerSdp: "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\n",
      webrtcEnabled: true,
      updatedAt: pastDate(4000),
    })
  );

  // Callee accepts with WebRTC SDP Answer
  await assertSucceeds(
    bobDb.collection("calls").doc("webrtc_call_1").update({
      status: "connected",
      answerSdp: "v=0\r\no=- 54321 2 IN IP4 127.0.0.1\r\n",
      updatedAt: pastDate(3000),
    })
  );

  // Participants exchange ICE candidates
  await assertSucceeds(
    aliceDb.collection("calls").doc("webrtc_call_1").collection("iceCandidates").doc("cand_1").set({
      senderId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:1 1 UDP 2122260223 192.168.1.2 54321 typ host",
      createdAt: pastDate(2000),
    })
  );

  await assertSucceeds(
    bobDb.collection("calls").doc("webrtc_call_1").collection("iceCandidates")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Outsider Charlie cannot read or inject ICE candidates
  await assertFails(
    charlieDb.collection("calls").doc("webrtc_call_1").collection("iceCandidates").doc("cand_1").get()
  );
  await assertFails(
    charlieDb.collection("calls").doc("webrtc_call_1").collection("iceCandidates").doc("cand_spoof").set({
      senderId: CHARLIE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      sdpMid: "0",
      sdpMLineIndex: 0,
      candidate: "candidate:spoof",
      createdAt: pastDate(1000),
    })
  );
});
