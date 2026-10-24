package com.hermes.android.ui.i18n

import com.hermes.android.domain.achievements.Tier

/**
 * Persian text for Hermes' achievements. The `hermes-achievements` plugin only speaks English.
 *
 * Keyed by the achievement id (and, for the requirement line, by the plugin's own English
 * metric label) and nothing else, so Hermes can change freely underneath: an achievement or
 * metric added later has no entry and shows Hermes' own text; one removed later is never looked up.
 * Numbers and tier thresholds always come from the running Hermes.
 */
internal object AchievementTexts {

    fun name(id: String, hermesText: String, persian: Boolean): String =
        if (persian) NAMES[id] ?: hermesText else hermesText

    fun description(id: String, hermesText: String, persian: Boolean): String =
        if (persian) DESCRIPTIONS[id] ?: hermesText else hermesText

    /**
     * The requirement line Hermes writes ("Requirement: X. Tier ladder: Copper 200, Silver 500." or
     * "Requirement: A ≥ 3; B ≥ 5."). A line of any other shape is returned as Hermes wrote it.
     */
    fun criteria(hermesText: String, persian: Boolean): String {
        if (!persian) return hermesText
        if (hermesText.startsWith("Secret:")) return SECRET_CRITERIA
        LADDER.matchEntire(hermesText)?.let { m ->
            val label = METRICS[m.groupValues[1]] ?: return hermesText
            val steps = m.groupValues[2].split(", ").map { step ->
                val (tierName, threshold) = step.split(" ").let { it.first() to it.last() }
                "${Tier.fromHermes(tierName)?.let(::tierFa) ?: tierName} $threshold"
            }
            return "شرط: $label. پلکان سطح‌ها: ${steps.joinToString("، ")}."
        }
        ALL_OF.matchEntire(hermesText)?.let { m ->
            val parts = m.groupValues[1].split("; ").map { part ->
                val (label, count) = part.split(" ≥ ").let { it.first() to it.getOrNull(1) }
                val fa = METRICS[label] ?: return hermesText
                if (count == null) fa else "$fa ≥ $count"
            }
            return "شرط: ${parts.joinToString("؛ ")}."
        }
        return hermesText
    }

    private val LADDER = Regex("""Requirement: (.+)\. Tier ladder: (.+)\.""")
    private val ALL_OF = Regex("""Requirement: (.+)\.""")

    private fun tierFa(tier: Tier): String = when (tier) {
        Tier.Copper -> "مس"
        Tier.Silver -> "نقره"
        Tier.Gold -> "طلا"
        Tier.Diamond -> "الماس"
        Tier.Olympian -> "المپیایی"
        is Tier.Unknown -> tier.hermesName
    }

    private const val SECRET_CRITERIA =
        "مخفی: شرط دقیق آن تا وقتی هرمس اولین نشانهٔ مرتبط را نبیند پنهان می‌ماند. " +
            "به کار با هرمس در اشکال‌زدایی، ابزارها، حافظه، مهارت‌ها، افزونه‌ها و مدل‌ها ادامه بده تا آشکار شود."

    private val NAMES = mapOf(
        "let_him_cook" to "بذار بپزه",
        "autonomous_avalanche" to "بهمن خودکار",
        "toolchain_maxxer" to "حداکثرکنندهٔ زنجیرهٔ ابزار",
        "full_send" to "تمام‌قد",
        "subagent_commander" to "فرمانده زیرعامل‌ها",
        "background_process_enjoyer" to "دوستدار پردازش پس‌زمینه",
        "cron_necromancer" to "احضارکنندهٔ cron",
        "red_text_connoisseur" to "خبرهٔ متن قرمز",
        "stack_trace_sommelier" to "چشندهٔ stack trace",
        "actually_read_the_logs" to "لاگ‌ها را واقعاً خواندی",
        "port_3000_taken" to "پورت ۳۰۰۰ اشغاله",
        "permission_denied_any_percent" to "رکورد «دسترسی ممنوع»",
        "dependency_hell_tourist" to "گردشگر جهنم وابستگی‌ها",
        "the_fix_was_restarting" to "راه‌حل ریستارت بود",
        "forgot_the_env_var" to "متغیر محیطی یادم رفت",
        "yaml_colon_incident" to "حادثهٔ دونقطهٔ YAML",
        "docker_name_collision" to "تداخل نام داکر",
        "supposed_to_be_quick" to "قرار بود سریع باشه",
        "one_more_small_change" to "فقط یک تغییر کوچک دیگه",
        "vibe_architect" to "معمار حال‌وهوا",
        "pixel_goblin" to "غول پیکسل",
        "ship_first_ask_later" to "اول بفرست، بعد بپرس",
        "css_exorcist" to "جن‌گیر CSS",
        "one_character_fix" to "اصلاح تک‌حرفی",
        "skillsmith" to "مهارت‌ساز",
        "skill_issue_skill_created" to "مشکل مهارت؟ مهارت ساخته شد.",
        "memory_keeper" to "نگهبان حافظه",
        "memory_palace" to "کاخ حافظه",
        "context_dragon" to "اژدهای کانتکست",
        "gateway_dweller" to "ساکن گیت‌وی",
        "plugin_goblin" to "غول افزونه",
        "rollback_wizard" to "جادوگر بازگشت",
        "rabbit_hole_certified" to "گواهی‌شدهٔ سوراخ خرگوش",
        "citation_goblin" to "غول ارجاع",
        "docs_archaeologist" to "باستان‌شناس مستندات",
        "browser_possession" to "تسخیر مرورگر",
        "terminal_goblin" to "غول ترمینال",
        "patch_wizard" to "جادوگر پچ",
        "file_archaeologist" to "باستان‌شناس فایل",
        "image_whisperer" to "نجواگر تصویر",
        "voice_of_the_machine" to "صدای ماشین",
        "model_hopper" to "مدل‌پرّان",
        "openrouter_enjoyer" to "دوستدار OpenRouter",
        "codex_conjurer" to "احضارکنندهٔ Codex",
        "multi_model_mage" to "جادوگر چندمدلی",
        "five_model_flight" to "پرواز پنج‌مدلی",
        "provider_polyglot" to "چندزبانهٔ provider",
        "model_sommelier" to "چشندهٔ مدل",
        "claude_confidant" to "همدم Claude",
        "gemini_cartographer" to "نقشه‌کش Gemini",
        "open_weights_pilgrim" to "زائر وزن‌های باز",
        "toolset_cartographer" to "نقشه‌کش مجموعه‌ابزار",
        "config_surgeon" to "جراح کانفیگ",
        "rebase_acrobat" to "بندبازِ rebase",
        "test_suite_tamer" to "رام‌کنندهٔ تست‌ها",
        "screenshot_hunter" to "شکارچی اسکرین‌شات",
        "marathon_operator" to "اپراتور ماراتن",
        "weekend_warrior" to "جنگجوی آخر هفته",
        "night_shift_operator" to "اپراتور شیفت شب",
        "cache_hit_appreciator" to "قدردان cache hit",
    )

    private val DESCRIPTIONS = mapOf(
        "let_him_cook" to "بگذار هرمس در یک گفتگو زنجیرهٔ جدی از ابزارها را خودکار اجرا کند.",
        "autonomous_avalanche" to "در همهٔ گفتگوها بهمنی از فراخوانی ابزار هرمس جمع کن.",
        "toolchain_maxxer" to "در یک گفتگو از طیف گسترده‌ای از ابزارهای مختلف هرمس استفاده کن.",
        "full_send" to "ترمینال، فایل‌ها و وب/مرورگر همه در یک اجرای واقعی درگیر شوند.",
        "subagent_commander" to "کار واگذارشده به عامل‌ها را هماهنگ کن.",
        "background_process_enjoyer" to "آن‌قدر پردازش طولانی را شروع یا کنترل کن که لایق این عنوان شوی.",
        "cron_necromancer" to "کارهای زمان‌بندی‌شدهٔ خودکار را از مرگ برگردان.",
        "red_text_connoisseur" to "آن‌قدر خطا ببین که به متن قرمز ذائقه پیدا کنی.",
        "stack_trace_sommelier" to "tracebackها را دسته‌دسته بچش، نه جرعه‌جرعه.",
        "actually_read_the_logs" to "به‌جای حدس زدن، چندین بار لاگ‌ها را بررسی کن.",
        "port_3000_taken" to "آن‌قدر تداخل پورت سرور توسعه ببین که بی‌حس شوی.",
        "permission_denied_any_percent" to "تا دیوار دسترسی‌ها یک‌نفس بدو.",
        "dependency_hell_tourist" to "نصب بسته‌ها شکست می‌خورد، ولی زندگی به‌هرحال ادامه دارد.",
        "the_fix_was_restarting" to "بعد از آن‌قدر خطا ریستارت کن که اسمش را بگذاری یک تکنیک.",
        "forgot_the_env_var" to "احراز هویت یا پیکربندی شکست خورد چون یک متغیر محیطی نبود.",
        "yaml_colon_incident" to "نحو پیکربندی دندان نشان می‌دهد.",
        "docker_name_collision" to "نام کانتینر از قبل وجود دارد. معلومه که دارد.",
        "supposed_to_be_quick" to "یک درخواست کوچک به یک سفر کامل تبدیل می‌شود.",
        "one_more_small_change" to "در یک گفتگو آن‌قدر فایل ویرایش کن که عبارت «تغییر کوچک» بی‌معنی شود.",
        "vibe_architect" to "در یک گفتگوی پروژه سطح وسیعی را لمس کن.",
        "pixel_goblin" to "مدتی طولانی روی فرانت‌اند، CSS، SVG یا تنظیم ظاهر کار کن.",
        "ship_first_ask_later" to "فعالیت git بعد از یک زنجیرهٔ جدی از ابزارها.",
        "css_exorcist" to "جن‌های استایل را بارها از رابط بیرون کن.",
        "one_character_fix" to "یک ویرایش ریز بعد از کوهی از خطا. دردناک. زیبا.",
        "skillsmith" to "آن‌قدر با مهارت‌های هرمس کار کن که ردّ انگشتت بماند.",
        "skill_issue_skill_created" to "به‌جای تکرار کردن خودت، رویه‌های ماندگار بساز یا اصلاح کن.",
        "memory_keeper" to "دانش ماندگار را با حافظه یا Mnemosyne نگه دار.",
        "memory_palace" to "ردّ جدی از حافظهٔ ماندگار بساز.",
        "context_dragon" to "بارها با فشردگی، کانتکست عظیم یا فشار توکن درگیر شو.",
        "gateway_dweller" to "در جریان‌کارهای هرمس که به گیت‌وی وصل‌اند زندگی کن.",
        "plugin_goblin" to "آن‌قدر افزونه استفاده یا توسعه بده که داشبورد متوجه شود.",
        "rollback_wizard" to "جادوی بازگشت و بازیابی از checkpoint را به کار ببر.",
        "rabbit_hole_certified" to "آن‌قدر محتوای وب جست‌وجو یا استخراج کن که به مارپیچ پژوهش برسی.",
        "citation_goblin" to "آن‌قدر صفحهٔ وب استخراج کن که کتابدار کوچکی شوی.",
        "docs_archaeologist" to "بارها و بارها منابع مستندات را بکاوی.",
        "browser_possession" to "بارها مرورگری را با خودکارسازی تسخیر کن.",
        "terminal_goblin" to "مدت جدی در سرزمین شل بگذران.",
        "patch_wizard" to "فایل‌ها را با پچ‌های هدفمند به اراده‌ات خم کن.",
        "file_archaeologist" to "با خواندن و جست‌وجو در سیستم فایل بکاو.",
        "image_whisperer" to "آن‌قدر از ابزار تولید تصویر یا بینایی برای کار تصویری استفاده کن.",
        "voice_of_the_machine" to "بارها از تبدیل متن به گفتار یا ابزار صدا استفاده کن.",
        "model_hopper" to "آن‌قدر بین providerها/مدل‌ها عوض کن یا بررسی‌شان کن که عادت شود.",
        "openrouter_enjoyer" to "کار مدل را بارها از مسیر OpenRouter عبور بده.",
        "codex_conjurer" to "آن‌قدر کمک به‌سبک Codex احضار کن که مراسم شود.",
        "multi_model_mage" to "در تاریخچهٔ هرمس از طیف واقعی نام‌های مدل مختلف استفاده کن.",
        "five_model_flight" to "دست‌کم پنج مدل زبانی مختلف را امتحان کن، نه فقط اولین مدلی که جواب داد.",
        "provider_polyglot" to "در تاریخچهٔ هرمس از مدل‌های چند provider مختلف استفاده کن.",
        "model_sommelier" to "آن‌قدر گفتگو با مدل/provider بچش که سلیقه پیدا کنی.",
        "claude_confidant" to "بارها استدلال به‌سبک Claude را وارد جریان کارت کن.",
        "gemini_cartographer" to "آن‌قدر جریان‌کار مرتبط با Gemini نقشه بکش که زمین را بشناسی.",
        "open_weights_pilgrim" to "از طریق فرادادهٔ گفتگوهای هرمس، واقعاً با مدل‌های محلی/وزن‌باز گفتگو کن.",
        "toolset_cartographer" to "مجموعه‌ابزارهای هرمس را عمدی پیمایش کن، نه این‌که ابزارها را یک لکه‌ی مبهم ببینی.",
        "config_surgeon" to "روی فایل‌های واقعی کانفیگ، manifestها، فایل‌های env و تنظیمات داشبورد بی‌ترس عمل کن.",
        "rebase_acrobat" to "جراحی واقعی تاریخچهٔ git را انجام بده: rebase، تعارض، merge، fetch، push.",
        "test_suite_tamer" to "آن‌قدر دستور راستی‌آزمایی اجرا کن که متن سبز بخشی از مراسم شود.",
        "screenshot_hunter" to "به‌جای ادعای کارکردن، مدرک تصویری بگیر، بررسی کن و صیقل بده.",
        "marathon_operator" to "تعداد جدی گفتگو با هرمس جمع کن.",
        "weekend_warrior" to "آن‌قدر در آخر هفته‌ها هرمس را اجرا کن که سبک زندگی شود.",
        "night_shift_operator" to "بارها در ساعت‌های جن‌زده گفتگو اجرا کن.",
        "cache_hit_appreciator" to "رفتار cache/پرامپت را ببین یا از آن سود ببر.",
    )

    private val METRICS = mapOf(
        "tool calls in one session" to "فراخوانی ابزار در یک گفتگو",
        "distinct Hermes tools used in one session" to "ابزارهای متفاوت هرمس در یک گفتگو",
        "terminal calls in one session" to "فراخوانی ترمینال در یک گفتگو",
        "file/search/patch calls in one session" to "فراخوانی فایل/جست‌وجو/پچ در یک گفتگو",
        "web search/extract or browser calls in one session" to "فراخوانی جست‌وجو/استخراج وب یا مرورگر در یک گفتگو",
        "messages in one session" to "پیام در یک گفتگو",
        "files touched in one session" to "فایل لمس‌شده در یک گفتگو",
        "lifetime delegate_task calls" to "کل فراخوانی‌های delegate_task",
        "lifetime background process operations" to "کل عملیات پردازش پس‌زمینه",
        "lifetime scheduled-job operations" to "کل عملیات کارهای زمان‌بندی‌شده",
        "error/failed/traceback messages observed" to "پیام‌های خطا/شکست/traceback دیده‌شده",
        "traceback or exception mentions" to "اشاره به traceback یا استثنا",
        "log inspections" to "بررسی لاگ",
        "dev-server port conflict detections" to "تشخیص تداخل پورت سرور توسعه",
        "permission-denied errors" to "خطای دسترسی ممنوع",
        "package-install failures" to "شکست نصب بسته",
        "successful package installs after package work" to "نصب موفق بسته بعد از کار با بسته‌ها",
        "restart/reload actions after error clusters" to "ریستارت/بارگذاری مجدد بعد از خوشهٔ خطاها",
        "missing auth/config/environment-variable events" to "رویدادهای نبودِ احراز هویت/کانفیگ/متغیر محیطی",
        "YAML/config parse incidents" to "حادثهٔ تجزیهٔ YAML/کانفیگ",
        "Docker/container-name conflicts" to "تداخل نام Docker/کانتینر",
        "frontend/CSS/SVG/React activity mentions" to "اشاره به فعالیت فرانت‌اند/CSS/SVG/React",
        "CSS, styling, Tailwind, or className activity" to "فعالیت CSS، استایل، Tailwind یا className",
        "git workflow commands" to "دستورهای git",
        "tiny typo-style fixes after error clusters" to "اصلاح‌های ریز از نوع غلط تایپی بعد از خوشهٔ خطاها",
        "Hermes skill mentions or tool use" to "اشاره به مهارت‌های هرمس یا استفاده از ابزار",
        "skill_manage create/patch/delete operations" to "عملیات ساخت/پچ/حذف skill_manage",
        "memory or Mnemosyne tool events" to "رویدادهای ابزار حافظه یا Mnemosyne",
        "durable memory writes" to "نوشتن در حافظهٔ ماندگار",
        "context, compression, token, or cache-pressure mentions" to "اشاره به کانتکست، فشرده‌سازی، توکن یا فشار کش",
        "gateway/API/chat-platform activity" to "فعالیت گیت‌وی/API/پلتفرم پیام‌رسان",
        "dashboard plugin development or usage signals" to "نشانه‌های توسعه یا استفاده از افزونهٔ داشبورد",
        "rollback/checkpoint recovery mentions" to "اشاره به بازگشت/بازیابی از checkpoint",
        "documentation/README/docs activity" to "فعالیت مستندات/README",
        "model/provider-related activity" to "فعالیت مربوط به مدل/provider",
        "OpenRouter mentions" to "اشاره به OpenRouter",
        "Codex mentions" to "اشاره به Codex",
        "prompt-cache/cache-hit mentions" to "اشاره به کش پرامپت/cache hit",
        "lifetime web_search/web_extract calls" to "کل فراخوانی‌های web_search/web_extract",
        "lifetime web_extract calls" to "کل فراخوانی‌های web_extract",
        "lifetime browser automation calls" to "کل فراخوانی‌های خودکارسازی مرورگر",
        "lifetime Hermes tool calls" to "کل فراخوانی‌های ابزار هرمس",
        "lifetime terminal calls" to "کل فراخوانی‌های ترمینال",
        "lifetime targeted patch edits" to "کل ویرایش‌های پچ هدفمند",
        "lifetime read_file/search_files calls" to "کل فراخوانی‌های read_file/search_files",
        "image generation or vision tool calls" to "فراخوانی ابزار تولید تصویر یا بینایی",
        "text-to-speech or voice tool calls" to "فراخوانی ابزار تبدیل متن به گفتار یا صدا",
        "distinct model names seen in session metadata" to "نام‌های متفاوت مدل در فرادادهٔ گفتگوها",
        "distinct model providers inferred from session metadata" to "providerهای متفاوت مدل برآمده از فرادادهٔ گفتگوها",
        "Claude/Anthropic model mentions" to "اشاره به مدل‌های Claude/Anthropic",
        "Gemini/Google model mentions" to "اشاره به مدل‌های Gemini/Google",
        "local/open-weight model mentions" to "اشاره به مدل‌های محلی/وزن‌باز",
        "Hermes sessions whose model metadata is local/open-weight" to "گفتگوهای هرمس که مدلشان محلی/وزن‌باز است",
        "toolset or tool-family mentions" to "اشاره به مجموعه‌ابزار یا خانوادهٔ ابزار",
        "configuration/environment/manifest activity" to "فعالیت کانفیگ/محیط/manifest",
        "git history operations such as rebase, merge, fetch, push, or tag" to "عملیات تاریخچهٔ git مثل rebase، merge، fetch، push یا tag",
        "test/check/verification command mentions" to "اشاره به دستورهای تست/بررسی/راستی‌آزمایی",
        "screenshot, Playwright, PNG, or vision-inspection activity" to "فعالیت اسکرین‌شات، Playwright، PNG یا بررسی تصویری",
        "release, version, publish, or git tag events" to "رویدادهای انتشار، نسخه یا تگ git",
        "Hermes sessions" to "گفتگوهای هرمس",
        "sessions started on weekends" to "گفتگوهای شروع‌شده در آخر هفته",
        "sessions started late night or before dawn" to "گفتگوهای شروع‌شده در دل شب یا پیش از سپیده‌دم",
    )
}
