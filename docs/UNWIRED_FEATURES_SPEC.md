# Hermes2 — Spec برای ۱۰ فیچر سیم‌کشی‌نشده

> برای پیاده‌سازی توسط یک عامل هوش مصنوعی (مثلاً Sonnet). همه RPCها و params از
> سورس واقعی `NousResearch/hermes-agent` → `tui_gateway/server.py` تأیید شده‌اند.

## قوانین معماری (حتماً رعایت شود)
- لایه‌ها: `ui.screen` → `ui.viewmodel` → `GatewayClient` (interface). UI هرگز
  مستقیم از `gateway`/`runtime` import نمی‌کند.
- فراخوانی RPC: `gatewayClient.request(method, params)` که `params` یک
  `Map<String, JsonElement>` است. الگوی موجود:
  `buildJsonObject { put("k", v) }.toMap()`.
- همه ثابت‌های RPC از قبل در `GatewayMethods` (فایل `gateway/GatewayRequest.kt`)
  وجود دارند — نیازی به افزودن نیست.
- بعد از هر فیچر: `./gradlew :app:assembleDebug` و کامیت با پیام فارسی.
- i18n: از `t("English", "فارسی")` استفاده شود (این تابع `@Composable` است و
  نباید داخل لامبدای غیر-Composable مثل `onClick` صدا زده شود — مقدارش را به یک
  `val` بالای لامبدا منتقل کن).

---

## 🟢 فیچر ۱ — نمایش توکن/هزینه سشن (`session.usage`)

**اولویت بالا — دغدغه اصلی کاربر هزینه است.**

- **RPC:** `GatewayMethods.SESSION_USAGE` (`"session.usage"`)
- **params:** `{ "session_id": <activeSessionId> }`
- **return:** `{ "calls": Int, "input": Int, "output": Int, "total": Int, "credits_lines"?: [String] }`
  (`input`/`output`/`total` = تعداد توکن؛ `credits_lines` خطوط اعتبار Nous، اختیاری)

**ViewModel (`SessionsViewModel.kt`):**
- متد `loadUsage(sessionId: String)` که `session.usage` را صدا می‌زند و نتیجه را
  در یک `StateFlow<SessionUsage?>` می‌گذارد.
- data class: `SessionUsage(calls, input, output, total, creditsLines: List<String>)`.

**UI (`SessionsScreen.kt`):**
- در `HistoryDetailView` (وقتی کاربر یک سشن را باز می‌کند) یک کارت کوچک بالای
  لیست پیام‌ها: «Tokens: ▸ in {input} · out {output} · total {total} · {calls} calls».
- اگر `creditsLines` خالی نبود، زیرش نمایش بده.
- **جای دقیق:** بالای history detail، نه وسط چت.

---

## 🟢 فیچر ۲ — autocomplete واقعی دستورات (`commands.catalog` + `complete.slash`)

الان لیست دستورات در `ChatScreen.InputBar` به‌صورت hardcode است
(`listOf("/help","/clear","/config","/model","/session")`). با RPC واقعی جایگزین شود.

- **`GatewayMethods.COMMANDS_CATALOG`** (`"commands.catalog"`) — params: `{}` (هیچ).
  return: کاتالوگ کامل دستورات (لیست `{name, description, ...}`). یک‌بار در init
  بارگذاری شود (cache).
- **`GatewayMethods.COMPLETE_SLASH`** (`"complete.slash"`) — params: `{ "text": <متن فعلی> }`.
  return: `{ "items": [ {label/value...} ] }`. برای autocomplete زنده هنگام تایپ.

**ViewModel (`ChatViewModel.kt`):**
- متد `loadCommandCatalog()` در init → `commands.catalog` → `StateFlow<List<SlashCommandSuggestion>>`.
- (اختیاری) متد `completeSlash(text)` با debounce ۲۰۰ms → `complete.slash` برای
  پیشنهاد زنده. اگر خواستی ساده نگه‌داری، فقط catalog کافی است.

**UI (`ChatScreen.kt` → `InputBar`):**
- لیست hardcode را با مقدار از ViewModel جایگزین کن. فیلتر بر اساس متن تایپ‌شده
  مثل الان بماند.

---

## 🟢 فیچر ۳ — قطع/حذف provider (`model.disconnect`)

- **RPC:** `GatewayMethods.MODEL_DISCONNECT` (`"model.disconnect"`)
- **params:** `{ "slug": <provider slug, مثل "xiaomi"/"gemini"> }`
- **return:** ok؛ خطای `4001` اگر slug خالی، `4005` اگر credential نبود.

**ViewModel (`ConfigViewModel.kt`):**
- متد `disconnectProvider(slug: String)` → `model.disconnect` → بعدش `loadModels()` و
  `loadConfig()` برای رفرش. خطاها را به `errorMessage` بده.

**UI (`ConfigScreen.kt`):**
- در لیست providerها (همان جایی که `model.options` نمایش داده می‌شود)، برای هر
  provider که `authenticated == true` است، یک دکمه/آیتم منوی «Disconnect» اضافه کن.

---

## 🟢 فیچر ۴ — جزئیات ابزار و toolsetها (`tools.show` + `toolsets.list`)

غنی‌سازی صفحه Tools موجود.

- **`GatewayMethods.TOOLS_SHOW`** (`"tools.show"`) — params: `{ "session_id"?: <active> }`.
  return: ابزارها گروه‌بندی‌شده بر اساس toolset (sections: `{toolset: [{name, description}]}`).
- **`GatewayMethods.TOOLSETS_LIST`** (`"toolsets.list"`) — params: `{ "session_id"?: <active> }`.
  return: لیست toolsetها با وضعیت فعال/غیرفعال.

**ViewModel (`ConfigViewModel.kt` یا یک `ToolsViewModel` اگر جدا بود):**
- `loadToolsGrouped()` → `tools.show` → ساختار گروه‌بندی‌شده.
- `loadToolsets()` → `toolsets.list`.

**UI:** در صفحه Tools موجود، ابزارها را زیر هدر toolsetشان گروه کن؛ توضیح هر ابزار
را زیرش نشان بده. اگر toolset غیرفعال بود، با تگ خاکستری.

---

## 🟢 فیچر ۵ — reload بدون ری‌استارت (`reload.mcp` + `reload.env`)

- **`GatewayMethods.RELOAD_MCP`** (`"reload.mcp"`) — params: `{ "session_id"?: <active>, "confirm"?: true }`.
- **`GatewayMethods.RELOAD_ENV`** (`"reload.env"`) — params: `{}`.

**ViewModel (`ConfigViewModel.kt`):**
- `reloadMcp()` و `reloadEnv()` → RPC مربوطه → پیام موفقیت/خطا در `errorMessage`.

**UI (`ConfigScreen.kt`):**
- در بخش «Advanced/پیشرفته» تنظیمات، دو دکمه: «Reload MCP servers» و
  «Reload environment». بعد از کلیک یک Snackbar/Toast تأیید.

---

## 🟠 فیچر ۶ — صفحه پلاگین‌ها (`plugins.list` + `plugins.manage`)

**صفحه جدید لازم است.**

- **`GatewayMethods.PLUGINS_LIST`** (`"plugins.list"`) — params: `{}`.
  return: `{ "plugins": [ {name, version, enabled} ] }`.
- **`GatewayMethods.PLUGINS_MANAGE`** (`"plugins.manage"`) — params: `{ "action": "list"|..., "name"?: <plugin> }`.
  return بسته به action.

**ViewModel جدید (`PluginsViewModel.kt`):** الگوی `SkillsViewModel` را کپی کن.
- `loadPlugins()` → `plugins.list`.
- `managePlugin(name, action)` → `plugins.manage`.

**UI جدید (`PluginsScreen.kt`):** الگوی `SkillsScreen` را کپی کن — لیست کارت‌ها با
نام/نسخه/وضعیت. ناوبری: از `ConfigScreen` یک ردیف «Plugins» اضافه کن (مثل
`onNavigateToSkills`)، و در `MainActivity` یک `Screen.PLUGINS` و route آن.

---

## 🟠 فیچر ۷ — لیست ساب‌ایجنت‌های فعال (`agents.list`)

- **RPC:** `GatewayMethods.AGENTS_LIST` (`"agents.list"`)
- **params:** `{}`
- **return:** `{ "processes": [ {session_id, command, status} ] }`
  (نکته: این پردازش‌های ایجنت در حال اجراست، نه «انواع» ساب‌ایجنت.)

**ViewModel:** در `SessionsViewModel` یا یک ViewModel کوچک — `loadAgents()`.
**UI:** یک بخش/کارت کوچک در صفحه Sessions: «Active agents» با لیست
`command` + `status`. اگر خالی بود، نمایش نده (پنهان بماند، نه پیام خالی وسط صفحه).

---

## 🟠 فیچر ۸ — پنل آمار مصرف (`insights.get`)

- **RPC:** `GatewayMethods.INSIGHTS_GET` (`"insights.get"`)
- **params:** `{ "days"?: 30 }`
- **return:** `{ "days", "sessions": Int, "messages": Int, ... }` (آمار تجمیعی دوره).

**ViewModel:** `loadInsights(days=30)` در `SessionsViewModel`.
**UI:** یک کارت در تب Memory یا یک تب سوم «Insights» در صفحه Sessions: تعداد سشن‌ها،
پیام‌ها، و آمار دوره. selector ساده برای ۷/۳۰/۹۰ روز.

---

## ⚪ نمی‌سازیم (داخلی، بدون ارزش UI)
`session.status`، `session.active_list` (داخلی)، `config.get` (با `config.show`
پوشش داده شده)، `terminal.resize` (ترمینال UI نداریم).

---

## ترتیب پیشنهادی اجرا
1. فیچر ۱ (usage/هزینه) — مهم‌ترین، self-contained
2. فیچر ۳ (model.disconnect) و ۵ (reload) — کوچک، در تنظیمات
3. فیچر ۲ (slash autocomplete) — بهبود InputBar
4. فیچر ۴ (tools.show) — غنی‌سازی صفحه Tools
5. فیچر ۶ (Plugins) — صفحه جدید
6. فیچر ۷ (agents) و ۸ (insights) — پنل‌های اطلاعاتی

هر فیچر را جدا کامیت کن، بعد از هرکدام `assembleDebug` بگیر، و هیچ‌چیز را «وسط
صفحه» تحمیل نکن — جای هر کدام در ستون UI بالا مشخص است.
