package com.cuny.exchange

import com.cuny.exchange.data.model.Campus
import com.cuny.exchange.data.model.Category
import com.cuny.exchange.data.model.Condition
import com.cuny.exchange.data.model.Listing
import com.cuny.exchange.data.model.Message
import com.cuny.exchange.data.model.Thread
import com.cuny.exchange.data.model.User
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class DataModelUnitTest {

    @Test
    fun listing_defaultValues_areValid() {
        val listing = Listing()
        assertEquals("", listing.id)
        assertEquals("", listing.sellerId)
        assertEquals("", listing.sellerName)
        assertEquals("", listing.sellerEmail)
        assertEquals(0.0, listing.price, 0.001)
        assertEquals(5.0, listing.sellerRating, 0.001)
        assertEquals(Condition.GOOD.label, listing.condition)
        assertEquals(Category.TEXTBOOKS.label, listing.category)
        assertEquals("active", listing.status)
        assertTrue(listing.isAvailable)
        assertTrue(listing.photoUrls.isEmpty())
        assertNotNull(listing.createdAt)
    }

    @Test
    fun listing_isAvailable_returnsTrueOnlyWhenActive() {
        val activeListing = Listing(status = "active")
        val soldListing = Listing(status = "sold")
        val pendingListing = Listing(status = "deletion-pending")

        assertTrue(activeListing.isAvailable)
        assertFalse(soldListing.isAvailable)
        assertFalse(pendingListing.isAvailable)
    }

    @Test
    fun listing_invariantsValidation() {
        val validListing = Listing(
            id = "item123",
            sellerId = "userABC",
            sellerEmail = "student@hunter.cuny.edu",
            title = "TI-84 Plus CE Calculator",
            price = 75.0,
            photoUrls = listOf("url1", "url2"),
            condition = Condition.LIKE_NEW.label,
            category = Category.CALCULATORS.label
        )

        assertTrue("Price must be non-negative", validListing.price >= 0.0)
        assertTrue("Photo URLs must be <= 5", validListing.photoUrls.size in 0..5)
        assertTrue("Title must not be blank", validListing.title.isNotBlank())
        assertTrue("Title max 100 chars", validListing.title.length <= 100)
        assertTrue("Description max 1000 chars", validListing.description.length <= 1000)
    }

    @Test
    fun user_defaultValues_matchSpecRequirements() {
        val user = User()
        assertEquals("", user.id)
        assertEquals("", user.displayName)
        assertEquals("", user.campus)
        assertEquals("", user.major)
        assertEquals(5.0, user.rating, 0.001)
        assertEquals(0, user.soldCount)
        assertFalse(user.isVerified)
        assertNotNull(user.createdAt)
    }

    @Test
    fun thread_defaultValues_andCampusTracking() {
        val thread = Thread(
            id = "thread1",
            listingId = "list1",
            buyerId = "buyer123",
            sellerId = "seller456",
            buyerCampus = "Brooklyn College",
            sellerCampus = "Baruch College"
        )
        assertEquals("Brooklyn College", thread.buyerCampus)
        assertEquals("Baruch College", thread.sellerCampus)
        assertTrue(thread.unreadUserIds.isEmpty())
        assertNotNull(thread.lastUpdated)
    }

    @Test
    fun message_defaultValues_areValid() {
        val message = Message(
            id = "msg1",
            senderId = "user1",
            senderName = "Alex",
            text = "Hi, is this available?"
        )
        assertEquals("msg1", message.id)
        assertEquals("user1", message.senderId)
        assertEquals("Alex", message.senderName)
        assertEquals("Hi, is this available?", message.text)
        assertNotNull(message.createdAt)
    }

    @Test
    fun condition_fromLabel_resolvesCorrectly() {
        assertEquals(Condition.NEW, Condition.fromLabel("New"))
        assertEquals(Condition.NEW, Condition.fromLabel("new"))
        assertEquals(Condition.LIKE_NEW, Condition.fromLabel("Like New"))
        assertEquals(Condition.GOOD, Condition.fromLabel("Good"))
        assertEquals(Condition.FAIR, Condition.fromLabel("Fair"))
        assertEquals(Condition.GOOD, Condition.fromLabel("Unknown_Value"))
    }

    @Test
    fun category_fromLabel_resolvesCorrectly() {
        assertEquals(Category.TEXTBOOKS, Category.fromLabel("Textbooks"))
        assertEquals(Category.CALCULATORS, Category.fromLabel("Calculators"))
        assertEquals(Category.ELECTRONICS, Category.fromLabel("Electronics"))
        assertEquals(Category.CLOTHING, Category.fromLabel("Clothing"))
        assertEquals(Category.FURNITURE, Category.fromLabel("Furniture"))
        assertEquals(Category.OTHER, Category.fromLabel("Other"))
        assertEquals(Category.ALL, Category.fromLabel("All"))
        assertEquals(Category.OTHER, Category.fromLabel("NonExistent"))
    }

    @Test
    fun campus_defaultCatalog_providesSafeZones() {
        val baruch = Campus.findById("baruch")
        assertNotNull(baruch)
        assertEquals("Baruch College", baruch?.name)
        assertTrue(baruch?.safeZone?.contains("Newman Library", ignoreCase = true) == true)

        val brooklyn = Campus.findByName("Brooklyn College")
        assertNotNull(brooklyn)
        assertEquals("brooklyn", brooklyn?.id)

        assertNull(Campus.findById("non-existent-campus"))
    }
}
