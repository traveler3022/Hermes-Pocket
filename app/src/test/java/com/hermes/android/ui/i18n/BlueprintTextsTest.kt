package com.hermes.android.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class BlueprintTextsTest {

    @Test
    fun `English stays as Hermes wrote it`() {
        assertEquals("Morning briefing", BlueprintTexts.text("Morning briefing", persian = false))
        assertEquals("daily at 08:00", BlueprintTexts.schedule("daily at 08:00", persian = false))
    }

    @Test
    fun `known text is translated, unknown text is kept`() {
        assertEquals("گزارش صبحگاهی", BlueprintTexts.text("Morning briefing", persian = true))
        assertEquals("A plugin's blueprint", BlueprintTexts.text("A plugin's blueprint", persian = true))
    }

    @Test
    fun `schedule shapes from scheduleHuman`() {
        assertEquals("هر روز ساعت 08:00", BlueprintTexts.schedule("daily at 08:00", persian = true))
        assertEquals("روزهای کاری ساعت 09:00", BlueprintTexts.schedule("weekdays at 09:00", persian = true))
        assertEquals("یکشنبه ساعت 18:00", BlueprintTexts.schedule("sunday at 18:00", persian = true))
        assertEquals("هر 30 دقیقه", BlueprintTexts.schedule("every 30 minutes", persian = true))
        assertEquals("روزهای کاری، هر ساعت", BlueprintTexts.schedule("weekdays, every hour", persian = true))
        assertEquals("هر 3 ساعت", BlueprintTexts.schedule("every 3 hours", persian = true))
        assertEquals("on a schedule", BlueprintTexts.schedule("on a schedule", persian = true))
        assertEquals("tuesday at 10:00", BlueprintTexts.schedule("tuesday at 10:00", persian = true))
    }
}
