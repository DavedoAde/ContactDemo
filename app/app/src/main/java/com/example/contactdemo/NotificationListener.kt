package com.example.contactdemo

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ContentProviderOperation
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    // This acts as our memory to track who is at what stage of the conversation
    // Format: "SenderName" -> "STEP_1" (waiting for name) or "STEP_2" (waiting for yes/no)
    private val conversationStates = mutableMapOf<String, String>()
    private val pendingNames = mutableMapOf<String, String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // We only want to respond to messaging apps (WhatsApp, IG, Telegram, etc.)
        // You can add more package names here later. 
        val messagingApps = listOf("com.whatsapp", "com.instagram.android", "org.telegram.messenger")
        if (!messagingApps.contains(packageName)) return

        val extras = sbn.notification.extras
        val senderName = extras.getString(Notification.EXTRA_TITLE)
        val messageText = extras.getString(Notification.EXTRA_TEXT)?.toString()?.lowercase()

        if (senderName.isNullOrEmpty() || messageText.isNullOrEmpty()) return

        // Ignore our own messages
        if (messageText.contains("what is your name")) return
        if (messageText.contains("is your name correct")) return
        if (messageText.contains("nice to meet you")) return

        // --- THE CHATBOT LOGIC ---
        
        val currentState = conversationStates[senderName]

        if (currentState == null) {
            // STEP 1: New conversation. Ask for their name.
            sendReply(sbn, "Hello! Thanks for reaching out. What is your name?")
            conversationStates[senderName] = "WAITING_FOR_NAME"

        } else if (currentState == "WAITING_FOR_NAME") {
            // STEP 2: They just replied with their name. 
            // (We will assume whatever they typed is their name for this demo)
            val capturedName = messageText.replaceFirstChar { it.uppercase() }
            pendingNames[senderName] = capturedName
            
            sendReply(sbn, "Got it! So $capturedName is your name, correct?")
            conversationStates[senderName] = "WAITING_FOR_CONFIRMATION"

        } else if (currentState == "WAITING_FOR_CONFIRMATION") {
            // STEP 3: They replied to the confirmation.
            if (messageText.contains("yes") || messageText.contains("yeah") || messageText.contains("correct") || messageText.contains("yep")) {
                
                val nameToSave = pendingNames[senderName] ?: senderName
                saveContact(nameToSave)
                sendReply(sbn, "Nice to meet you, $nameToSave! I've saved your contact.")
                
                // Clear the memory so the next time they message, it starts fresh
                conversationStates.remove(senderName)
                pendingNames.remove(senderName)
            } else {
                // If they say no, reset the conversation
                sendReply(sbn, "My apologies! Let's try again. What is your name?")
                conversationStates[senderName] = "WAITING_FOR_NAME"
            }
        }
    }

    // --- THE MAGIC TRICK: SENDING A REPLY FROM A NOTIFICATION ---
    private fun sendReply(sbn: StatusBarNotification, replyText: String) {
        val actions = sbn.notification.actions ?: return
        
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            
            // Look for the "Reply" action
            val intent = Intent()
            val bundle = Bundle()
            
            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            
            try {
                action.actionIntent.send(this, 0, intent)
                Log.d("AutoResponder", "Reply sent successfully: $replyText")
            } catch (e: PendingIntent.CanceledException) {
                Log.e("AutoResponder", "Failed to send reply", e)
            }
        }
    }

    // --- SAVING TO CONTACTS ---
    private fun saveContact(name: String) {
        val whereName = ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME + " = ?"
        val whereArgs = arrayOf(name)
        
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI, null, whereName, whereArgs, null
        )
        
        val exists = (cursor?.count ?: 0) > 0
        cursor?.close()

        if (exists) return // Don't save duplicates

        val ops = ArrayList<ContentProviderOperation>()

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build())

        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build())

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
