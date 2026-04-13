const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();


// 🔔 When a claim request is created
exports.onClaimCreated = functions.firestore
    .document("claims/{claimId}")
    .onCreate(async (snap, context) => {

        const claim = snap.data();
        const ownerId = claim.ownerId;

        const userDoc = await admin.firestore()
            .collection("users")
            .doc(ownerId)
            .get();

        const token = userDoc.data().fcmToken;

        const payload = {
            notification: {
                title: "New Claim Request",
                body: "Someone requested your item"
            }
        };

        return admin.messaging().sendToDevice(token, payload);
    });


// 🔔 When claim is approved
exports.onClaimApproved = functions.firestore
    .document("claims/{claimId}")
    .onUpdate(async (change, context) => {

        const before = change.before.data();
        const after = change.after.data();

        if (before.status !== "APPROVED" && after.status === "APPROVED") {

            const claimantId = after.claimantId;

            const userDoc = await admin.firestore()
                .collection("users")
                .doc(claimantId)
                .get();

            const token = userDoc.data().fcmToken;

            const payload = {
                notification: {
                    title: "Claim Approved",
                    body: "Your claim has been approved"
                }
            };

            return admin.messaging().sendToDevice(token, payload);
        }

        return null;
    });


// 🔔 When item is returned
exports.onItemReturned = functions.firestore
    .document("items/{itemId}")
    .onUpdate(async (change, context) => {

        const before = change.before.data();
        const after = change.after.data();

        if (before.status !== "RETURNED" && after.status === "RETURNED") {

            const claimantId = after.claimantId;

            const userDoc = await admin.firestore()
                .collection("users")
                .doc(claimantId)
                .get();

            const token = userDoc.data().fcmToken;

            const payload = {
                notification: {
                    title: "Item Returned",
                    body: "Your item has been returned"
                }
            };

            return admin.messaging().sendToDevice(token, payload);
        }

        return null;
    });