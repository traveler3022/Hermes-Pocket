package com.hermes.android.ui.i18n

/**
 * Persian descriptions for Hermes' own slash commands. Hermes' catalog
 * (`commands.catalog`, built from `hermes_cli/commands.py`) only speaks English.
 *
 * Keyed by the canonical command name and nothing else, so Hermes can change
 * freely underneath: a command added later, or a skill or plugin, has no entry
 * and shows Hermes' own text; one removed later is simply never looked up. The
 * usage hint Hermes appends (`(usage: /plan [request])`) is taken from its text
 * each time, so arguments stay whatever the running Hermes says they are.
 */
internal object SlashCommandDescriptions {

    private val usageSuffix = Regex("""\s*\(usage: (.+)\)\s*$""")

    fun describe(command: String, hermesText: String, persian: Boolean): String {
        if (!persian) return hermesText
        val translated = FA[command.lowercase()] ?: return hermesText
        val usage = usageSuffix.find(hermesText)?.groupValues?.get(1)
        return if (usage != null) "$translated (استفاده: $usage)" else translated
    }

    private val FA = mapOf(
        "/plan" to "نوشتن برنامهٔ پیاده‌سازی در یک فایل markdown، بدون اجرای هیچ کاری",
        "/goal" to "تعیین هدفی ثابت که هرمس در چند نوبت پیگیری می‌کند تا محقق شود",
        "/subgoal" to "افزودن یا مدیریت معیارهای اضافه برای هدف فعلی",
        "/btw" to "پرسیدن یک سؤال فرعی دربارهٔ همین گفتگو، بدون قطع کار",
        "/bg" to "اجرای یک درخواست در یک گفتگوی جدا در پس‌زمینه",
        "/loop" to "اجرای دوبارهٔ یک درخواست در فاصله‌های زمانی مشخص در همین گفتگو",
        "/review" to "بررسی کار انجام‌شده (کد، PR، مستندات) توسط یک زیرعامل مستقل",
        "/learn" to "ساختن یک skill قابل استفادهٔ دوباره از هر چیزی که توضیح بدهی",
        "/status" to "نمایش اطلاعات گفتگو، مدل، توکن و context",
        "/help" to "نمایش دستورهای موجود",
        "/handoff" to "سپردن این گفتگو به یک پیام‌رسان (تلگرام، دیسکورد و…)",
        "/worktree" to "نمایش، ساخت یا پاک کردن worktreeهای جدای git",
        "/rollback" to "فهرست یا بازگرداندن نقطه‌های ذخیرهٔ فایل‌ها",
        "/snapshot" to "ساخت یا بازگرداندن snapshot از تنظیمات و وضعیت هرمس",
        "/export" to "خروجی گرفتن از یک پروفایل (تنظیمات، skillها، ظاهر) برای اشتراک",
        "/import" to "وارد کردن فایل یک پروفایل به‌عنوان پروفایل جدید",
        "/agents" to "نمایش عامل‌ها و کارهای در حال اجرا",
        "/journey" to "باز کردن خط زمانی مسیر یادگیری",
        "/steer" to "فرستادن پیام بعد از ابزار بعدی، بدون قطع کار",
        "/heartbeat" to "تعیین درخواستی تکراری که وقتی گفتگو بیکار است دوباره اجرا می‌شود",
        "/refine" to "مرور همین گفتگو و ذخیرهٔ درس‌ها در حافظه یا skillها",
        "/moa" to "اجرای یک درخواست با چند مدل (Mixture of Agents) و بعد برگشت به مدل خودت",
        "/egress" to "نمایش وضعیت پراکسی خروجی Docker",
        "/whoami" to "نمایش سطح دسترسی تو به دستورها (مدیر یا کاربر)",
        "/profile" to "نمایش نام پروفایل فعال و پوشهٔ اصلی آن",
        "/config" to "نمایش تنظیمات فعلی",
        "/codex-runtime" to "روشن یا خاموش کردن runtime مخصوص codex برای مدل‌های OpenAI/Codex",
        "/personality" to "انتخاب یکی از شخصیت‌های آماده",
        "/timestamps" to "روشن یا خاموش کردن ساعت کنار پیام‌ها",
        "/diff" to "نمایش تغییرات git در پوشهٔ کاری",
        "/verbose" to "تغییر میزان نمایش پیشرفت ابزارها",
        "/focus" to "حالت تمرکز: فقط درخواست تو و جواب نهایی",
        "/footer" to "روشن یا خاموش کردن اطلاعات اجرا در پایین جواب نهایی",
        "/yolo" to "حالت YOLO: رد شدن از همهٔ تأییدهای دستورهای خطرناک",
        "/approvals" to "نمایش یا تعیین حالت دائمی تأیید دستورهای خطرناک",
        "/reasoning" to "مدیریت میزان و نمایش استدلال مدل",
        "/fast" to "حالت سریع (پردازش اولویت‌دار OpenAI یا حالت سریع Anthropic)",
        "/skin" to "نمایش یا تغییر پوسته و ظاهر",
        "/busy" to "تعیین رفتار پیام‌ها وقتی هرمس مشغول کار است",
        "/tools" to "مدیریت ابزارها: فهرست، فعال یا غیرفعال کردن",
        "/toolsets" to "فهرست مجموعه‌ابزارهای موجود",
        "/skills" to "جستجو، نصب، بررسی یا مدیریت skillها",
        "/memory" to "مرور نوشته‌های در انتظار حافظه، یا روشن و خاموش کردن تأییدشان",
        "/bundles" to "فهرست بسته‌های skill",
        "/pet" to "روشن یا خاموش کردن حیوان خانگی petdex، یا گرفتن یکی",
        "/hatch" to "ساختن یک حیوان خانگی petdex جدید از روی توضیح",
        "/init" to "ساختن یا به‌روزرسانی AGENTS.md از روی بررسی مخزن",
        "/cron" to "مدیریت کارهای زمان‌بندی‌شده",
        "/suggestions" to "مرور خودکارسازی‌های پیشنهادی (پذیرفتن یا رد کردن)",
        "/blueprint" to "راه‌اندازی یک خودکارسازی از روی قالب آماده",
        "/curator" to "نگهداری skillها در پس‌زمینه (وضعیت، اجرا، سنجاق، بایگانی)",
        "/kanban" to "تابلوی همکاری بین پروفایل‌ها (کارها، پیوندها، نظرها)",
        "/reload" to "بارگذاری دوبارهٔ متغیرهای .env در همین گفتگو",
        "/reload-mcp" to "بارگذاری دوبارهٔ سرورهای MCP از تنظیمات",
        "/reload-skills" to "بررسی دوبارهٔ پوشهٔ skillها برای skillهای تازه یا حذف‌شده",
        "/browser" to "وصل کردن ابزارهای مرورگر به مرورگر Chromium تو، یا رفتن به حالت Browser Use",
        "/plugins" to "فهرست pluginهای نصب‌شده و وضعیتشان",
        "/usage" to "نمایش مصرف توکن و محدودیت‌ها",
        "/subscription" to "دیدن و تغییر اشتراک Nous در مرورگر",
        "/login" to "ورود با حساب Nous",
        "/topup" to "نمایش موجودی Nous و مدیریت پرداخت",
        "/insights" to "نمایش آمار و تحلیل مصرف",
        "/platforms" to "نمایش وضعیت پیام‌رسان‌های متصل",
        "/update" to "به‌روزرسانی هرمس به آخرین نسخه",
        "/version" to "نمایش نسخهٔ هرمس",
        "/debug" to "فرستادن گزارش اشکال (اطلاعات سیستم و لاگ‌ها) و گرفتن لینک اشتراک",
    )
}
