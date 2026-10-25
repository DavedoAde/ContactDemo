package com.example.contactdemo

import android.app.Notification
import android.content.ContentProviderOperation
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.Toast

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // We only want to listen to messaging apps. You can add more package names later.
        // For now, let's just test with any notification that has a title.
        val extras = sbn.notification.extras
        val senderName = extras.getString(Notification.EXTRA_TITLE)
        val messageText = extras.getString(Notification.EXTRA_TEXT)

        if (!senderName.isNullOrEmpty() && !messageText.isNullOrEmpty()) {
            // To prevent spamming, let's just save the name if it looks like a new person.
            // For this demo, we will save EVERY notification sender to your contacts.
            
            saveContactFromNotification(senderName)
        }
    }

    private fun saveContactFromNotification(name: String) {
        // Check if contact already exists to avoid duplicates (simplified check)
        val whereName = ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME + " = ?"
        val whereArgs = arrayOf(name)
        
        val cursor = contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null, whereName, whereArgs, null
        )
        
        val exists = cursor?.count ?: 0 > 0
        cursor?.close()

        if (exists) return // Don't save if they are already in contacts

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
            // Note: Toast from a background service might not always show depending on Android version, 
            // but we'll keep it here for testing.
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
