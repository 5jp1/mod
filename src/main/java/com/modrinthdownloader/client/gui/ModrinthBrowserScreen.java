package com.modrinthdownloader.client.gui;

import com.modrinthdownloader.client.util.ModrinthAPI;
import com.modrinthdownloader.client.util.ModrinthAPI.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * Main Modrinth browser screen.
 * Uses Mojang mapping names (required for MC 26.1+):
 *   Screen, GuiGraphics, Button, EditBox, Component
 */
public class ModrinthBrowserScreen extends Screen {

    // ---- Colours ----
    private static final int C_BG        = 0xFF0D0D1A;
    private static final int C_PANEL     = 0xFF1A1A2E;
    private static final int C_CARD      = 0xFF16213E;
    private static final int C_BORDER    = 0xFF2A2A4A;
    private static final int C_ACCENT    = 0xFF1AA34A;
    private static final int C_ACCENT_DK = 0xFF158C3D;
    private static final int C_HOVER     = 0xFF0F3460;
    private static final int C_TEXT      = 0xFFE0E0E0;
    private static final int C_MUTED     = 0xFF888888;
    private static final int C_WARN      = 0xFFFFAA00;
    private static final int C_ERR       = 0xFFFF5555;
    private static final int C_BLUE      = 0xFF4A90D9;

    private final Screen parent;

    // ---- Search state ----
    private EditBox searchBox;
    private ProjectType selectedType  = ProjectType.MOD;
    private SortOrder   selectedSort  = SortOrder.DOWNLOADS;
    private String      selectedVersion = "Any Version";
    private String      searchQuery   = "";

    // ---- Results ----
    private List<SearchResult>   results    = new ArrayList<>();
    private boolean              isLoading  = false;
    private boolean              hasError   = false;
    private String               errorMsg   = "";
    private int                  currentPage = 0;
    private static final int     PAGE_SIZE   = 10;

    // ---- Scroll ----
    private double scrollY       = 0;
    private double targetScrollY = 0;
    private int    contentHeight = 0;

    // ---- Layout ----
    private static final int FILTER_W   = 160;
    private static final int CARD_H     = 90;
    private static final int CARD_GAP   = 6;
    private int listX, listY, listEndY, listW;

    // ---- Detail / download ----
    private SearchResult         detailProject  = null;
    private List<ProjectVersion> detailVersions = new ArrayList<>();
    private boolean              loadingVersions = false;
    private final Map<String, DlState> dlStates = new HashMap<>();

    // ---- Dropdown ----
    private boolean versionDropdown = false;

    private int hoveredCard = -1;

    enum DlState { IDLE, DOWNLOADING, DONE, FAILED }

    public ModrinthBrowserScreen(Screen parent) {
        super(Component.literal("Modrinth Downloader"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        listX    = FILTER_W + 10;
        listW    = this.width - listX - 20;
        listY    = 55;
        listEndY = this.height - 35;

        // Search box  (EditBox = Mojang name for TextFieldWidget)
        searchBox = new EditBox(this.font, listX, 8, listW - 82, 18, Component.literal("Search..."));
        searchBox.setMaxLength(100);
        searchBox.setHint(Component.literal("Search Modrinth..."));
        searchBox.setResponder(s -> searchQuery = s);
        this.addWidget(searchBox);

        // Search button
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> doSearch())
                .bounds(listX + listW - 78, 7, 74, 20).build());

        // Pagination
        addRenderableWidget(Button.builder(Component.literal("◀ Prev"), b -> page(-1))
                .bounds(this.width - 120, this.height - 28, 56, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next ▶"), b -> page(1))
                .bounds(this.width - 60, this.height - 28, 56, 20).build());

        // Back / close
        addRenderableWidget(Button.builder(Component.literal("✕ Close"), b -> {
            if (detailProject != null) {
                detailProject = null;
                detailVersions.clear();
                scrollY = targetScrollY = 0;
            } else {
                Minecraft.getInstance().setScreen(parent);
            }
        }).bounds(4, this.height - 28, 80, 20).build());

        buildFilterButtons();
        doSearch();
    }

    // ---- Filter buttons ----

    private final List<Button> typeButtons = new ArrayList<>();
    private final List<Button> sortButtons = new ArrayList<>();

    private void buildFilterButtons() {
        typeButtons.forEach(this::removeWidget);
        sortButtons.forEach(this::removeWidget);
        typeButtons.clear();
        sortButtons.clear();

        int y = 62;
        for (ProjectType t : ProjectType.values()) {
            boolean sel = t == selectedType;
            Button b = Button.builder(
                    Component.literal((sel ? "▶ " : "  ") + t.displayName),
                    btn -> { selectedType = t; currentPage = 0; scrollY = targetScrollY = 0; doSearch(); buildFilterButtons(); }
            ).bounds(4, y, FILTER_W - 8, 18).build();
            typeButtons.add(b);
            addRenderableWidget(b);
            y += 22;
        }

        y = 225;
        for (SortOrder s : SortOrder.values()) {
            boolean sel = s == selectedSort;
            Button b = Button.builder(
                    Component.literal((sel ? "● " : "○ ") + s.displayName),
                    btn -> { selectedSort = s; currentPage = 0; doSearch(); buildFilterButtons(); }
            ).bounds(4, y, FILTER_W - 8, 16).build();
            sortButtons.add(b);
            addRenderableWidget(b);
            y += 20;
        }
    }

    // ---- Search / API ----

    private void doSearch() {
        isLoading = true;
        hasError  = false;
        results.clear();

        ModrinthAPI.search(searchQuery, selectedType, selectedSort, selectedVersion, PAGE_SIZE, currentPage * PAGE_SIZE)
                .thenAccept(res -> {
                    results      = res;
                    isLoading    = false;
                    contentHeight = results.size() * (CARD_H + CARD_GAP);
                    scrollY = targetScrollY = 0;
                })
                .exceptionally(e -> {
                    isLoading = false;
                    hasError  = true;
                    errorMsg  = "Failed to reach Modrinth API";
                    return null;
                });
    }

    private void page(int delta) {
        if (delta < 0 && currentPage > 0)           { currentPage--; doSearch(); }
        else if (delta > 0 && results.size() == PAGE_SIZE) { currentPage++; doSearch(); }
    }

    private void openDetail(SearchResult r) {
        detailProject = r;
        detailVersions.clear();
        loadingVersions = true;
        scrollY = targetScrollY = 0;

        String loader = selectedType == ProjectType.MOD ? "fabric" : null;
        ModrinthAPI.getVersions(r.projectId, selectedVersion, loader)
                .thenAccept(vs -> { detailVersions = vs; loadingVersions = false; })
                .exceptionally(e -> { loadingVersions = false; return null; });
    }

    private void download(ProjectVersion v) {
        if (v.files.isEmpty()) return;
        FileInfo f = v.files.stream().filter(x -> x.primary).findFirst().orElse(v.files.get(0));
        dlStates.put(v.id, DlState.DOWNLOADING);
        ModrinthAPI.downloadFile(f, selectedType, pct -> {})
                .thenAccept(ok -> dlStates.put(v.id, ok ? DlState.DONE : DlState.FAILED));
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float delta) {
        scrollY += (targetScrollY - scrollY) * 0.2;

        // Backgrounds
        gfx.fill(0, 0, this.width, this.height, C_BG);
        gfx.fill(0, 0, FILTER_W, this.height, C_PANEL);
        gfx.fill(FILTER_W, 0, FILTER_W + 1, this.height, C_BORDER);
        gfx.fill(FILTER_W, 0, this.width, 32, 0xFF111126);
        gfx.fill(FILTER_W, 32, this.width, 33, C_BORDER);

        // Sidebar headings
        gfx.drawString(font, "\u2B07 MODRINTH", 8, 8, C_ACCENT, true);
        gfx.drawString(font, "Downloader", 8, 20, C_MUTED, true);
        gfx.drawString(font, "PROJECT TYPE", 8, 52, C_MUTED, false);
        gfx.fill(4, 58, FILTER_W - 4, 59, C_BORDER);
        gfx.drawString(font, "SORT BY", 8, 216, C_MUTED, false);
        gfx.fill(4, 222, FILTER_W - 4, 223, C_BORDER);

        // Version selector
        int verLabelY = 340;
        gfx.drawString(font, "GAME VERSION", 8, verLabelY, C_MUTED, false);
        gfx.fill(4, verLabelY + 6, FILTER_W - 4, verLabelY + 7, C_BORDER);
        gfx.fill(4, verLabelY + 10, FILTER_W - 4, verLabelY + 24, C_CARD);
        gfx.drawString(font, selectedVersion + " \u25BE", 8, verLabelY + 14, C_TEXT, true);

        if (versionDropdown) {
            int dvY = verLabelY + 25;
            for (String ver : ModrinthAPI.COMMON_VERSIONS) {
                gfx.fill(4, dvY, FILTER_W - 4, dvY + 14,
                        ver.equals(selectedVersion) ? C_ACCENT_DK : C_CARD);
                gfx.drawString(font, ver, 8, dvY + 3,
                        ver.equals(selectedVersion) ? 0xFFFFFFFF : C_TEXT, false);
                dvY += 14;
            }
        }

        // Search box background
        gfx.fill(listX - 2, 5, listX + listW - 2, 27, C_CARD);
        gfx.fill(listX - 2, 5, listX + listW - 2, 6, C_BORDER);
        gfx.fill(listX - 2, 26, listX + listW - 2, 27, C_BORDER);
        searchBox.render(gfx, mx, my, delta);

        if (detailProject != null) renderDetail(gfx, mx, my);
        else                       renderList(gfx, mx, my);

        // Page info
        if (!isLoading && !hasError && detailProject == null) {
            gfx.drawString(font, "Page " + (currentPage + 1) + "  \u00B7  " + results.size() + " results",
                    listX, this.height - 24, C_MUTED, false);
        }

        super.render(gfx, mx, my, delta);
    }

    private void renderList(GuiGraphics gfx, int mx, int my) {
        if (isLoading) {
            String dots = ".".repeat((int) (System.currentTimeMillis() / 500 % 4));
            gfx.drawString(font, "Searching Modrinth" + dots, listX + 10, listY + 80, C_ACCENT, true);
            return;
        }
        if (hasError) {
            gfx.drawString(font, "\u26A0 " + errorMsg, listX + 10, listY + 80, C_ERR, true);
            return;
        }
        if (results.isEmpty()) {
            gfx.drawString(font, "No results found.", listX + 10, listY + 80, C_MUTED, false);
            return;
        }

        hoveredCard = -1;
        gfx.enableScissor(listX, listY, listX + listW, listEndY);

        int y = listY - (int) scrollY;
        for (int i = 0; i < results.size(); i++) {
            if (y + CARD_H > listY && y < listEndY) {
                boolean hov = mx >= listX && mx <= listX + listW
                        && my >= Math.max(y, listY) && my <= Math.min(y + CARD_H, listEndY);
                if (hov) hoveredCard = i;
                renderCard(gfx, results.get(i), listX, y, listW, CARD_H, hov);
            }
            y += CARD_H + CARD_GAP;
        }

        gfx.disableScissor();

        // Scrollbar
        int vis = listEndY - listY;
        if (contentHeight > vis) {
            int sbH = Math.max(20, vis * vis / contentHeight);
            int sbY = listY + (int) ((double) scrollY / contentHeight * vis);
            gfx.fill(this.width - 8, listY, this.width - 4, listEndY, 0xFF222240);
            gfx.fill(this.width - 8, sbY, this.width - 4, sbY + sbH, C_ACCENT);
        }
    }

    private void renderCard(GuiGraphics gfx, SearchResult r, int x, int y, int w, int h, boolean hov) {
        gfx.fill(x, y, x + w, y + h, hov ? C_HOVER : C_CARD);
        gfx.fill(x, y, x + 3, y + h, typeColor(r.projectType)); // left stripe
        gfx.fill(x, y, x + w, y + 1, C_BORDER);
        gfx.fill(x, y + h - 1, x + w, y + h, C_BORDER);

        String title = r.title.length() > 42 ? r.title.substring(0, 39) + "..." : r.title;
        gfx.drawString(font, title, x + 10, y + 8, 0xFFFFFFFF, true);
        gfx.drawString(font, "by " + (r.author.isEmpty() ? "Unknown" : r.author), x + 10, y + 20, C_MUTED, false);

        String desc = r.description.length() > 80 ? r.description.substring(0, 77) + "..." : r.description;
        gfx.drawString(font, desc, x + 10, y + 33, 0xFFBBBBBB, false);

        gfx.drawString(font, "\u2B07 " + fmt(r.downloads), x + 10, y + 52, C_ACCENT, false);
        gfx.drawString(font, "\u2605 " + fmt(r.follows),   x + 80, y + 52, 0xFFFFDD44, false);
        gfx.drawString(font, "[" + cap(r.projectType) + "]", x + 160, y + 52, typeColor(r.projectType), false);

        if (!r.versions.isEmpty()) {
            gfx.drawString(font, "MC: " + r.versions.get(r.versions.size() - 1), x + w - 110, y + 52, C_MUTED, false);
        }

        // Details button
        int bx = x + w - 78, by = y + h / 2 - 10;
        gfx.fill(bx, by, bx + 70, by + 18, C_ACCENT_DK);
        gfx.fill(bx, by, bx + 70, by + 1, C_ACCENT);
        gfx.drawString(font, hov ? "\u25B6 Details" : "Details", bx + 6, by + 5, 0xFFFFFFFF, false);
    }

    private void renderDetail(GuiGraphics gfx, int mx, int my) {
        SearchResult p = detailProject;
        int x = listX, y = listY, w = listW;

        gfx.drawString(font, "\u2190 Back to results  (ESC)", x, y - 12, C_MUTED, false);

        // Header card
        gfx.fill(x, y, x + w, y + 62, C_CARD);
        gfx.fill(x, y, x + 4, y + 62, typeColor(p.projectType));
        gfx.drawString(font, p.title, x + 10, y + 7, 0xFFFFFFFF, true);
        gfx.drawString(font, "by " + p.author, x + 10, y + 19, C_MUTED, false);
        String descShort = p.description.length() > 85 ? p.description.substring(0, 82) + "..." : p.description;
        gfx.drawString(font, descShort, x + 10, y + 31, 0xFFBBBBBB, false);
        gfx.drawString(font, "\u2B07 " + fmt(p.downloads) + "   \u2605 " + fmt(p.follows)
                        + "   [" + cap(p.projectType) + "]",
                x + 10, y + 47, C_ACCENT, false);

        y += 72;
        gfx.drawString(font, "AVAILABLE VERSIONS", x, y, C_MUTED, false);
        y += 12;
        gfx.fill(x, y, x + w, y + 1, C_BORDER);
        y += 5;

        if (loadingVersions) {
            gfx.drawString(font, "Loading versions...", x + 10, y + 10, C_ACCENT, true);
            return;
        }
        if (detailVersions.isEmpty()) {
            gfx.drawString(font, "No compatible versions found.", x + 10, y + 10, C_MUTED, false);
            return;
        }

        int baseY = y;
        gfx.enableScissor(listX, baseY, listX + listW, listEndY);
        int vy = baseY - (int) scrollY;
        for (ProjectVersion v : detailVersions) {
            if (vy + 42 > baseY && vy < listEndY) renderVersionRow(gfx, v, x, vy, w, mx, my);
            vy += 46;
        }
        gfx.disableScissor();
    }

    private void renderVersionRow(GuiGraphics gfx, ProjectVersion v, int x, int y, int w, int mx, int my) {
        int stripe = switch (v.versionType) {
            case "release" -> C_ACCENT;
            case "beta"    -> C_WARN;
            case "alpha"   -> C_ERR;
            default        -> C_MUTED;
        };
        gfx.fill(x, y, x + w, y + 42, C_CARD);
        gfx.fill(x, y, x + 3, y + 42, stripe);
        gfx.fill(x, y + 41, x + w, y + 42, C_BORDER);

        gfx.drawString(font, v.name, x + 8, y + 5, 0xFFFFFFFF, true);
        gfx.drawString(font, "[" + v.versionType.toUpperCase() + "]", x + 8, y + 17, stripe, false);

        String gvStr = String.join(", ", v.gameVersions.stream().limit(3).toList());
        if (v.gameVersions.size() > 3) gvStr += "…";
        gfx.drawString(font, "MC: " + gvStr, x + 85, y + 17, C_MUTED, false);
        gfx.drawString(font, "Loaders: " + String.join(", ", v.loaders), x + 8, y + 29, C_MUTED, false);
        gfx.drawString(font, "\u2B07 " + fmt(v.downloads), x + 220, y + 29, C_ACCENT, false);

        // Download button
        DlState state = dlStates.getOrDefault(v.id, DlState.IDLE);
        int bx = x + w - 92, by2 = y + 11;
        boolean bHov = mx >= bx && mx <= bx + 84 && my >= by2 && my <= by2 + 20;
        int bg = switch (state) {
            case DOWNLOADING -> 0xFF555500;
            case DONE        -> 0xFF005500;
            case FAILED      -> 0xFF550000;
            default          -> bHov ? 0xFF5AAEF0 : C_BLUE;
        };
        gfx.fill(bx, by2, bx + 84, by2 + 20, bg);
        gfx.fill(bx, by2, bx + 84, by2 + 1, 0xFF88BBFF);
        String lbl = switch (state) {
            case DOWNLOADING -> "Downloading…";
            case DONE        -> "\u2713 Installed!";
            case FAILED      -> "\u2717 Failed";
            default          -> "\u2B07 Download";
        };
        gfx.drawString(font, lbl, bx + 5, by2 + 6, 0xFFFFFFFF, false);
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Version dropdown toggle area
        if (mx >= 4 && mx <= FILTER_W - 4 && my >= 350 && my <= 364) {
            versionDropdown = !versionDropdown;
            return true;
        }
        if (versionDropdown) {
            int dvY = 365;
            for (String ver : ModrinthAPI.COMMON_VERSIONS) {
                if (mx >= 4 && mx <= FILTER_W - 4 && my >= dvY && my <= dvY + 14) {
                    selectedVersion = ver;
                    versionDropdown = false;
                    currentPage = 0;
                    doSearch();
                    return true;
                }
                dvY += 14;
            }
            versionDropdown = false;
        }

        // Card click
        if (detailProject == null && !isLoading) {
            int y = listY - (int) scrollY;
            for (SearchResult r : results) {
                if (mx >= listX && mx <= listX + listW && my >= y && my <= y + CARD_H
                        && my >= listY && my <= listEndY) {
                    openDetail(r);
                    return true;
                }
                y += CARD_H + CARD_GAP;
            }
        }

        // Version download click
        if (detailProject != null && !loadingVersions) {
            int baseY = listY + 89;
            int vy = baseY - (int) scrollY;
            for (ProjectVersion v : detailVersions) {
                int bx = listX + listW - 92, by2 = vy + 11;
                if (mx >= bx && mx <= bx + 84 && my >= by2 && my <= by2 + 20) {
                    DlState st = dlStates.getOrDefault(v.id, DlState.IDLE);
                    if (st == DlState.IDLE || st == DlState.FAILED) download(v);
                    return true;
                }
                vy += 46;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int vis     = listEndY - listY;
        int maxScrl = Math.max(0, contentHeight - vis);
        targetScrollY = Mth.clamp(targetScrollY - dy * 18, 0, maxScrl);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            if (detailProject != null) {
                detailProject = null;
                detailVersions.clear();
                scrollY = targetScrollY = 0;
                return true;
            }
        }
        if (key == 257 || key == 335) { doSearch(); return true; } // ENTER / NUM ENTER
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- Helpers ----

    private int typeColor(String t) {
        return switch (t.toLowerCase()) {
            case "mod"          -> 0xFF1AA34A;
            case "modpack"      -> 0xFF9B59B6;
            case "resourcepack" -> 0xFF3498DB;
            case "shader"       -> 0xFFE67E22;
            case "plugin"       -> 0xFFE74C3C;
            case "datapack"     -> 0xFFF1C40F;
            default             -> 0xFF888888;
        };
    }

    private String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
