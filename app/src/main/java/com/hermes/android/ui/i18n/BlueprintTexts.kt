package com.hermes.android.ui.i18n

/**
 * Persian for the text of Hermes' built-in automation blueprints
 * (`cron/blueprint_catalog.py`), which only speaks English.
 *
 * Keyed by Hermes' own English text, so anything added or reworded later (or a plugin's
 * blueprint) has no entry and shows Hermes' text. Only what the user reads is
 * translated: the values sent back to Hermes stay the English ones.
 */
internal object BlueprintTexts {

    fun text(english: String, persian: Boolean): String =
        if (persian) FA[english.trim()] ?: english else english

    /** Hermes' `scheduleHuman` ("weekdays at 09:00", "every 30 minutes"); unknown shapes stay English. */
    fun schedule(english: String, persian: Boolean): String {
        if (!persian) return english
        Regex("""^every (\d+) minutes$""").find(english)?.let { return "هر ${it.groupValues[1]} دقیقه" }
        Regex("""^(weekdays, )?every (\d+) hours$""").find(english)?.let {
            return (if (it.groupValues[1].isNotEmpty()) "روزهای کاری، " else "") + "هر ${it.groupValues[2]} ساعت"
        }
        if (english == "weekdays, every hour") return "روزهای کاری، هر ساعت"
        if (english == "every hour") return "هر ساعت"
        Regex("""^(.+) at (\d{1,2}:\d{2})$""").find(english)?.let {
            val day = when (val scope = it.groupValues[1]) {
                "daily" -> "هر روز"
                else -> FA[scope] ?: return english
            }
            return "$day ساعت ${it.groupValues[2]}"
        }
        return english
    }

    private val FA = mapOf(
        // Titles
        "Morning briefing" to "گزارش صبحگاهی",
        "Important-mail monitor" to "پایش ایمیل‌های مهم",
        "Weekly review" to "مرور هفتگی",
        "Workday start reminder" to "یادآور شروع روز کاری",
        "Custom reminder" to "یادآور دلخواه",
        "Evening wind-down" to "جمع‌بندی آخر روز",
        "Topic news digest" to "خلاصهٔ خبرهای یک موضوع",
        "Bills & renewals reminder" to "یادآور قبض‌ها و تمدیدها",
        "Price & availability watch" to "پایش قیمت و موجودی",
        "Competitor news watch" to "پایش خبرهای رقبا",
        "Habit check-in" to "پیگیری عادت",
        "Hydration & movement nudge" to "یادآور آب خوردن و تحرک",
        "Weekly meal plan" to "برنامهٔ غذایی هفتگی",
        "Daily learning drip" to "یادگیری روزانه",
        "Gratitude & reflection prompt" to "قدردانی و مرور روز",
        "On-this-day discovery" to "کشف روزانه",

        // Descriptions
        "A short daily briefing: today's calendar, weather, and anything urgent waiting on you." to
            "یک گزارش کوتاه روزانه: برنامهٔ تقویم امروز، آب‌وهوا و هر کار فوری که منتظر توست.",
        "Check your inbox periodically and ping you ONLY about mail that actually needs attention." to
            "صندوق ایمیل را مرتب بررسی می‌کند و فقط برای ایمیل‌هایی خبر می‌دهد که واقعاً به توجه نیاز دارند.",
        "A weekly recap: what got done, what's still open, and what's coming up." to
            "جمع‌بندی هفته: چه کارهایی انجام شد، چه کارهایی باز مانده و چه چیزی در راه است.",
        "A weekday nudge with your agenda and top priorities." to
            "یادآوری روزهای کاری با برنامهٔ روز و مهم‌ترین اولویت‌ها.",
        "A recurring reminder in your own words, on your schedule." to
            "یادآوری تکرارشونده با جملهٔ خودت، در زمانی که می‌خواهی.",
        "An end-of-day check-in: tomorrow's calendar at a glance and anything you should prep tonight." to
            "مرور آخر روز: نگاهی به برنامهٔ فردا و کارهایی که بهتر است امشب آماده کنی.",
        "A recurring digest on a topic you care about — deduped against what was already sent, so only genuinely new items land." to
            "خلاصهٔ منظم خبرهای موضوع دلخواهت؛ چیزهایی که قبلاً فرستاده شده تکرار نمی‌شوند و فقط خبرهای واقعاً تازه می‌آیند.",
        "A heads-up before a recurring payment, subscription renewal, or due date — so nothing auto-charges by surprise." to
            "هشدار پیش از پرداخت تکراری، تمدید اشتراک یا سررسید، تا هیچ پولی بی‌خبر کسر نشود.",
        "Watch an exact product, flight, hotel, or listing and alert when your price or availability condition is met." to
            "یک کالا، پرواز، هتل یا آگهی مشخص را زیر نظر می‌گیرد و وقتی شرط قیمت یا موجودی برقرار شد خبر می‌دهد.",
        "Track named companies for material news — launches, pricing, funding, filings — with a cited digest." to
            "خبرهای مهم شرکت‌های مشخص (محصول جدید، قیمت، سرمایه‌گذاری، گزارش‌های رسمی) را با ذکر منبع خلاصه می‌کند.",
        "A recurring nudge to keep a habit on track and reflect on whether you did it." to
            "یادآوری منظم برای پایبندی به یک عادت و مرور این‌که انجامش دادی یا نه.",
        "A periodic nudge during the day to drink water, stand up, and stretch." to
            "یادآوری در طول روز برای آب خوردن، بلند شدن و کش‌وقوس دادن بدن.",
        "A weekly meal plan plus a consolidated grocery list, tuned to your diet and how much time you have to cook." to
            "برنامهٔ غذایی هفته همراه با فهرست خرید یکجا، متناسب با رژیمت و وقتی که برای آشپزی داری.",
        "One bite-sized lesson a day on a topic you want to learn, building progressively over time." to
            "هر روز یک درس کوتاه از موضوعی که می‌خواهی یاد بگیری، که کم‌کم روی هم ساخته می‌شود.",
        "A gentle evening prompt to reflect on the day and note what went well." to
            "یک یادآوری آرام شبانه برای مرور روز و نوشتن چیزهایی که خوب پیش رفت.",
        "A daily dose of curiosity: a notable historical event, fact, or word for the day." to
            "روزی یک چیز جالب: یک رویداد تاریخی، یک دانستنی یا واژهٔ روز.",

        // Field labels
        "What time?" to "چه ساعتی؟",
        "Where to deliver?" to "کجا فرستاده شود؟",
        "How often?" to "هر چند وقت؟",
        "Only notify me if the mail…" to "فقط وقتی خبر بده که ایمیل…",
        "Which day?" to "کدام روز؟",
        "Remind me to…" to "یادم بینداز که…",
        "Repeat on" to "تکرار در",
        "What topic?" to "چه موضوعی؟",
        "How many bullets?" to "چند مورد؟",
        "What's due?" to "سررسید چیست؟",
        "What exactly to watch?" to "دقیقاً چه چیزی زیر نظر باشد؟",
        "Alert me when…" to "وقتی خبر بده که…",
        "Which companies?" to "کدام شرکت‌ها؟",
        "Which events matter?" to "چه رویدادهایی مهم است؟",
        "Which habit?" to "کدام عادت؟",
        "Start hour" to "ساعت شروع",
        "End hour" to "ساعت پایان",
        "Diet?" to "رژیم غذایی؟",
        "Meals per day?" to "چند وعده در روز؟",
        "Cooking effort?" to "حوصلهٔ آشپزی؟",
        "Learn about…" to "یادگیری دربارهٔ…",
        "What kind?" to "چه نوعی؟",

        // Field help
        "24h local time, e.g. 08:00" to "ساعت ۲۴ساعته به وقت محلی، مثلاً 08:00",
        "origin = the chat you set this up from (or your configured home channel when created from the dashboard); local = save only, no message; or any connected platform name" to
            "origin = کانال اصلی‌ای که در هرمس تنظیم کرده‌ای؛ local = فقط ذخیره، بدون پیام؛ یا نام هر پیام‌رسان وصل‌شده",
        "minutes between checks" to "دقیقه بین هر بررسی",
        "a subject, product, person, or search phrase" to "یک موضوع، محصول، شخص یا عبارت جستجو",
        "URL or precise description — variant, dates, seller" to "لینک یا توضیح دقیق: مدل، تاریخ، فروشنده",
        "threshold price (state the currency), availability, or terms change" to
            "قیمت هدف (با واحد پول)، موجود شدن یا تغییر شرایط",
        "hours between checks — be gentle with rate limits" to "ساعت بین هر بررسی؛ زیاد نکن که محدودیت نخورد",
        "canonical names and domains; aliases help dedup" to "نام رسمی و دامنهٔ سایت؛ نام‌های دیگر به حذف تکراری‌ها کمک می‌کند",
        "hours between nudges" to "ساعت بین هر یادآوری",
        "first hour of the active window (24h)" to "اولین ساعت بازهٔ فعال (۲۴ساعته)",
        "last hour of the active window (24h)" to "آخرین ساعت بازهٔ فعال (۲۴ساعته)",

        // Option values (shown translated, sent as they are)
        "origin" to "کانال اصلی",
        "local" to "فقط ذخیره",
        "everyday" to "هر روز",
        "weekdays" to "روزهای کاری",
        "weekends" to "آخر هفته",
        "sunday" to "یکشنبه",
        "monday" to "دوشنبه",
        "friday" to "جمعه",
        "saturday" to "شنبه",
        "no restrictions" to "بدون محدودیت",
        "vegetarian" to "گیاه‌خواری",
        "vegan" to "وگان",
        "high-protein" to "پرپروتئین",
        "low-carb" to "کم‌کربوهیدرات",
        "dinner only" to "فقط شام",
        "lunch and dinner" to "ناهار و شام",
        "all three" to "هر سه وعده",
        "quick" to "سریع",
        "medium" to "متوسط",
        "ambitious" to "مفصل",
        "on this day in history" to "این روز در تاریخ",
        "word of the day" to "واژهٔ روز",
        "science fact" to "دانستنی علمی",
        "quote of the day" to "جملهٔ روز",

        // Categories
        "daily" to "روزانه",
        "weekly" to "هفتگی",
        "email" to "ایمیل",
        "general" to "عمومی",
    )
}
