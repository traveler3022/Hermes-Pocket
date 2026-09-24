package com.hermes.android.ui.viewmodel

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.android.gateway.GatewayClient
import com.hermes.android.gateway.GatewayException
import com.hermes.android.gateway.GatewayMethods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import kotlinx.serialization.json.contentOrNull

/**
 * ViewModel for the Skills Browser screen.
 *
 * Lists available skills, shows skill details, enables/disables skills.
 *
 * Reference: Phase 1.5 Rule 1, Rule 2
 */
@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val gatewayClient: GatewayClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        loadSkills()
    }

    fun loadSkills() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Fix S10F01: skills.manage requires {action: "list"} param
                val params = buildJsonObject {
                    put("action", "list")
                }
                val result = gatewayClient.request(GatewayMethods.SKILLS_MANAGE, params.toMap())
                val skills = parseSkills(result)
                _uiState.value = _uiState.value.copy(
                    skills = skills,
                    isLoading = false,
                )
                Timber.i("[Skills] Loaded ${skills.size} skills")
            } catch (e: GatewayException) {
                Timber.e(e, "[Skills] Failed to load")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load skills: ${e.message}",
                )
            }
        }
    }

    /** skills.manage search answers {"results": [{name, description}]}, not the list's category map. */
    private fun parseSearchResults(result: kotlinx.serialization.json.JsonElement): List<SkillItem> {
        val rows = (result as? JsonObject)?.get("results") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            val name = ((row as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            SkillItem(name = name, category = "Search results")
        }
    }

    /** skills.manage inspect nests everything under "info": description, source, SKILL.md preview. */
    private fun inspectDetail(result: kotlinx.serialization.json.JsonElement): String? {
        val info = (result as? JsonObject)?.get("info") as? JsonObject ?: return null
        fun field(key: String) = (info[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        return listOfNotNull(
            field("description"),
            field("source")?.let { "Source: $it" },
            field("skill_md_preview"),
        ).joinToString("\n\n").ifBlank { null }
    }

    private fun parseSkills(result: kotlinx.serialization.json.JsonElement): List<SkillItem> {
        return try {
            // Fix S10F02: Hermes get_available_skills() returns Dict[str, List[str]]
            // = {category: [skill_name, ...]}
            // server.py:12206: return _ok(rid, {"skills": get_available_skills()})
            // banner.py:93: returns skills_by_category: Dict[str, List[str]]
            val obj = result as? JsonObject ?: return emptyList()
            val skillsObj = obj["skills"] as? JsonObject ?: return emptyList()
            // Flatten {category: [names]} into list of SkillItem
            skillsObj.entries.flatMap { (category, namesArr) ->
                val names = namesArr as? kotlinx.serialization.json.JsonArray ?: return@flatMap emptyList()
                names.mapNotNull { nameEl ->
                    val name = (nameEl as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    SkillItem(
                        name = name,
                        category = category,
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun installSkill(skillName: String) {
        // Fix S10F02: Hermes skills.manage does NOT support enable/disable.
        // Official actions are: list, search, install, browse, inspect.
        // Use "install" for adding a skill (server.py:12224)
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("action", "install")
                    put("query", skillName)
                }
                gatewayClient.request(GatewayMethods.SKILLS_MANAGE, params.toMap())
                Timber.i("[Skills] Installed: $skillName")
                loadSkills() // Refresh list
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Install failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to install: ${e.message}",
                )
            }
        }
    }

    fun reloadSkills() {
        viewModelScope.launch {
            try {
                gatewayClient.request(GatewayMethods.SKILLS_RELOAD)
                Timber.i("[Skills] Reloaded")
                loadSkills()
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Reload failed")
            }
        }
    }

    /**
     * Search the skill registry ("search" — one of the official skills.manage
     * actions per server.py:12224, alongside list/install/browse/inspect).
     * Only "list" and "install" were ever wired here, so there was no way to
     * find a skill you didn't already know the exact name of. Empty query
     * falls back to the locally-known list.
     */
    fun searchSkills(query: String) {
        if (query.isBlank()) {
            loadSkills()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val params = buildJsonObject {
                    put("action", "search")
                    put("query", query)
                }
                val result = gatewayClient.request(GatewayMethods.SKILLS_MANAGE, params.toMap())
                val skills = parseSearchResults(result)
                _uiState.value = _uiState.value.copy(
                    skills = skills,
                    isLoading = false,
                )
                Timber.i("[Skills] Search '$query' found ${skills.size}")
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Search failed")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Search failed: ${e.message}",
                )
            }
        }
    }

    /**
     * Fetch skill detail text ("inspect" action) for the detail dialog.
     */
    fun inspectSkill(skillName: String) {
        viewModelScope.launch {
            try {
                val params = buildJsonObject {
                    put("action", "inspect")
                    put("query", skillName)
                }
                val result = gatewayClient.request(GatewayMethods.SKILLS_MANAGE, params.toMap())
                _uiState.value = _uiState.value.copy(
                    inspectedSkillName = skillName,
                    inspectedSkillDetail = inspectDetail(result) ?: "(no details returned)",
                )
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Inspect failed")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Inspect failed: ${e.message}",
                )
            }
        }
    }

    fun dismissInspect() {
        _uiState.value = _uiState.value.copy(inspectedSkillName = null, inspectedSkillDetail = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ── Manual skill add/edit/delete ────────────────────────────────────
    //
    // skills.manage only supports list/search/install/browse/inspect —
    // "install" pulls a named skill from the remote skills hub, it can't
    // create one from scratch. There's no dedicated create/edit/delete RPC,
    // so this edits ~/.hermes/skills directly and reloads via skills.reload.
    //
    // Hermes only loads a skill from a folder holding SKILL.md
    // (tools/skills_tool.py _find_all_skills), listed under its frontmatter
    // `name:` or else the folder name. This used to read and write
    // ~/.hermes/skills/<name>.md, which Hermes never scans: a new skill never
    // appeared, editing showed an empty file, and delete removed nothing.

    /**
     * Python defining `root` and `find(name)`: the SKILL.md Hermes lists under
     * [name] in ~/.hermes/skills, or None. Same order as Hermes' own scan.
     */
    private val findSkillPython = """
        import base64, pathlib, re, shutil
        root = pathlib.Path.home() / '.hermes' / 'skills'
        def find(name):
            if not root.is_dir():
                return None
            for p in sorted(root.rglob('SKILL.md')):
                try:
                    head = p.read_text(encoding='utf-8', errors='replace')[:4000]
                except OSError:
                    continue
                m = re.match(r'---\s*\n(.*?)\n---', head, re.S)
                n = re.search(r'^name:[ \t]*(.*)', m.group(1), re.M) if m else None
                listed = n.group(1).strip().strip('"\'') if n else p.parent.name
                if listed == name:
                    return p
            return None
    """.trimIndent()

    private fun b64Utf8(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /** Open the editor for a brand-new skill. */
    fun startNewSkill() {
        _uiState.value = _uiState.value.copy(
            editingSkillName = "",
            editingSkillOriginalName = null,
            editingSkillContent = "",
        )
    }

    /** Open the editor pre-filled with an existing skill's file content. */
    fun startEditSkill(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                editingSkillName = name,
                editingSkillOriginalName = name,
                isLoadingSkillContent = true,
            )
            try {
                val content = gatewayClient.execPython(
                    findSkillPython + "\n" +
                        "p = find(base64.b64decode('${b64Utf8(name)}').decode())\n" +
                        "print(p.read_text(encoding='utf-8') if p else '', end='')\n"
                )
                _uiState.value = _uiState.value.copy(editingSkillContent = content, isLoadingSkillContent = false)
            } catch (e: Exception) {
                Timber.w(e, "[Skills] Failed to load skill content")
                // Closed, not left open empty: saving that would wipe the skill.
                dismissSkillEditor()
                _uiState.value = _uiState.value.copy(
                    isLoadingSkillContent = false,
                    errorMessage = "Failed to load skill: ${e.message}",
                )
            }
        }
    }

    fun dismissSkillEditor() {
        _uiState.value = _uiState.value.copy(
            editingSkillName = null,
            editingSkillOriginalName = null,
            editingSkillContent = "",
        )
    }

    fun saveSkill(name: String, content: String) {
        val slug = safeSkillSlug(name)
        if (slug.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Skill name can't be empty")
            return
        }
        // The editor locks the name of an existing skill, so this is either a
        // new skill or the one being edited, never a rename.
        val editing = _uiState.value.editingSkillOriginalName
        viewModelScope.launch {
            try {
                gatewayClient.execPython(
                    findSkillPython + "\n" +
                        "name = base64.b64decode('${b64Utf8(editing ?: name.trim())}').decode()\n" +
                        "content = base64.b64decode('${b64Utf8(content)}').decode()\n" +
                        "p = find(name)\n" +
                        (if (editing == null) {
                            "if p is not None or (root / '$slug' / 'SKILL.md').exists():\n" +
                                "    raise SystemExit('a skill named ' + name + ' already exists')\n" +
                                "p = root / '$slug' / 'SKILL.md'\n"
                        } else {
                            "p = p or root / '$slug' / 'SKILL.md'\n"
                        }) +
                        "p.parent.mkdir(parents=True, exist_ok=True)\n" +
                        "p.write_text(content, encoding='utf-8')\n" +
                        // The flat file older versions of the app wrote (never loaded).
                        "(root / '$slug.md').unlink(missing_ok=True)\n" +
                        "print(p)\n"
                )
                Timber.i("[Skills] Saved: $slug")
                dismissSkillEditor()
                reloadSkills()
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Save failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save skill: ${e.message}")
            }
        }
    }

    /** Ask before deleting: it removes the skill's whole folder. */
    fun requestDeleteSkill(name: String) {
        _uiState.value = _uiState.value.copy(pendingDeleteSkill = name)
    }

    fun cancelDeleteSkill() {
        _uiState.value = _uiState.value.copy(pendingDeleteSkill = null)
    }

    fun confirmDeleteSkill() {
        val name = _uiState.value.pendingDeleteSkill ?: return
        _uiState.value = _uiState.value.copy(pendingDeleteSkill = null)
        deleteSkill(name)
    }

    private fun deleteSkill(name: String) {
        val slug = safeSkillSlug(name)
        viewModelScope.launch {
            try {
                val removed = gatewayClient.execPython(
                    findSkillPython + "\n" +
                        "p = find(base64.b64decode('${b64Utf8(name)}').decode())\n" +
                        "gone = False\n" +
                        // Never the skills root itself (a SKILL.md lying directly in it).
                        "if p is not None and p.parent != root and root in p.parents:\n" +
                        "    shutil.rmtree(p.parent)\n" +
                        "    gone = True\n" +
                        "for legacy in (root / '$slug.md', root / '$slug.markdown'):\n" +
                        "    if legacy.exists():\n" +
                        "        legacy.unlink()\n" +
                        "        gone = True\n" +
                        "print('OK' if gone else 'MISSING', end='')\n"
                )
                if (removed != "OK") {
                    // Bundled, plugin or external_dirs skills live outside ~/.hermes/skills.
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "\"$name\" isn't in ~/.hermes/skills, so it can't be deleted here",
                    )
                    return@launch
                }
                Timber.i("[Skills] Deleted: $name")
                reloadSkills()
            } catch (e: Exception) {
                Timber.e(e, "[Skills] Delete failed")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to delete skill: ${e.message}")
            }
        }
    }

    /** Names are interpolated straight into a shell/python command — strip
     *  anything that isn't a safe filename character. */
    private fun safeSkillSlug(name: String): String =
        name.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
}

data class SkillsUiState(
    val skills: List<SkillItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val inspectedSkillName: String? = null,
    val inspectedSkillDetail: String? = null,
    // Manual add/edit — null = editor closed, "" = new skill (no name yet
    // typed), non-null = editing that existing skill.
    val editingSkillName: String? = null,
    val editingSkillOriginalName: String? = null,
    val editingSkillContent: String = "",
    val isLoadingSkillContent: Boolean = false,
    /** Skill waiting on the delete confirmation, or null. */
    val pendingDeleteSkill: String? = null,
)

data class SkillItem(
    val name: String,
    val category: String,
)
