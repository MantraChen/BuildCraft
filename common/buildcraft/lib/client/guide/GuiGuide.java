/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * You can obtain a copy at https://mozilla.org/MPL/2.0/.
 */
package buildcraft.lib.client.guide;

import java.util.*;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import buildcraft.lib.client.guide.font.*;
import buildcraft.lib.client.guide.parts.*;
import buildcraft.lib.client.guide.parts.contents.GuidePageContents;
import buildcraft.lib.client.sprite.*;
import buildcraft.lib.gui.*;
import buildcraft.lib.gui.pos.*;
import buildcraft.lib.guide.*;

/** Native, resizable two-page reader. Remake by FelixMantra. */
public class GuiGuide extends Screen {
    public static final Identifier ICONS_1 = new Identifier("minecraft", "textures/gui/icons.png");
    public static final Identifier ICONS_2 = new Identifier("buildcraftlib:textures/gui/guide/icons.png");
    public static final Identifier COVER = new Identifier("buildcraftlib:textures/gui/guide/cover.png");
    public static final Identifier LEFT_PAGE =
        new Identifier("buildcraftlib:textures/gui/guide/left_page.png");
    public static final Identifier RIGHT_PAGE =
        new Identifier("buildcraftlib:textures/gui/guide/right_page.png");
    public static final Identifier LEFT_PAGE_BACK =
        new Identifier("buildcraftlib:textures/gui/guide/left_page_back.png");
    public static final Identifier RIGHT_PAGE_BACK =
        new Identifier("buildcraftlib:textures/gui/guide/right_page_back.png");
    public static final Identifier LEFT_PAGE_FIRST =
        new Identifier("buildcraftlib:textures/gui/guide/left_page_first.png");
    public static final Identifier RIGHT_PAGE_LAST =
        new Identifier("buildcraftlib:textures/gui/guide/right_page_last.png");
    public static final Identifier NOTE = new Identifier("buildcraftlib:textures/gui/guide/note.png");

    public static final GuiIcon BOOK_COVER = new GuiIcon(COVER, 0, 0, 202, 248);
    public static final GuiIcon BOOK_BINDING = new GuiIcon(COVER, 204, 0, 11, 248);

    public static final GuiIcon PAGE_LEFT = new GuiIcon(LEFT_PAGE, 0, 0, 193, 248);
    public static final GuiIcon PAGE_RIGHT = new GuiIcon(RIGHT_PAGE, 0, 0, 193, 248);

    public static final GuiIcon PAGE_LEFT_BACK = new GuiIcon(LEFT_PAGE_BACK, 0, 0, 193, 248);
    public static final GuiIcon PAGE_RIGHT_BACK = new GuiIcon(RIGHT_PAGE_BACK, 0, 0, 193, 248);

    public static final GuiIcon PAGE_LEFT_FIRST = new GuiIcon(LEFT_PAGE_FIRST, 0, 0, 193, 248);
    public static final GuiIcon PAGE_RIGHT_LAST = new GuiIcon(RIGHT_PAGE_LAST, 0, 0, 193, 248);

    public static final int PAGE_WIDTH = 168;
    public static final int PAGE_HEIGHT = 190;

    public static final GuiRectangle PAGE_LEFT_TEXT = new GuiRectangle(23, 25, PAGE_WIDTH, PAGE_HEIGHT);
    public static final GuiRectangle PAGE_RIGHT_TEXT = new GuiRectangle(4, 25, PAGE_WIDTH, PAGE_HEIGHT);

    public static final GuiIcon PEN_UP = new GuiIcon(ICONS_2, 0, 0, 14, 135);
    public static final GuiIcon PEN_ANGLED = new GuiIcon(ICONS_2, 17, 0, 100, 100);

    public static final GuiIcon PEN_HIDDEN_MIN = new GuiIcon(ICONS_2, 0, 4, 10, 5);
    public static final GuiIcon PEN_HIDDEN_MAX = new GuiIcon(ICONS_2, 0, 4, 10, 15);

    public static final GuiIcon TURN_BACK = new GuiIcon(ICONS_2, 23, 139, 18, 10);
    public static final GuiIcon TURN_BACK_HOVERED = new GuiIcon(ICONS_2, 23, 152, 18, 10);

    public static final GuiIcon TURN_FORWARDS = new GuiIcon(ICONS_2, 0, 139, 18, 10);
    public static final GuiIcon TURN_FORWARDS_HOVERED = new GuiIcon(ICONS_2, 0, 152, 18, 10);

    public static final GuiIcon BACK = new GuiIcon(ICONS_2, 48, 139, 17, 9);
    public static final GuiIcon BACK_HOVERED = new GuiIcon(ICONS_2, 48, 152, 17, 9);

    public static final GuiIcon BOX_EMPTY = new GuiIcon(ICONS_2, 0, 164, 16, 16);
    public static final GuiIcon BOX_MINUS = new GuiIcon(ICONS_2, 16, 164, 16, 16);
    public static final GuiIcon BOX_PLUS = new GuiIcon(ICONS_2, 32, 164, 16, 16);
    public static final GuiIcon BOX_TICKED = new GuiIcon(ICONS_2, 48, 164, 16, 16);
    public static final GuiIcon BOX_CHAPTER = new GuiIcon(ICONS_2, 64, 164, 16, 16);

    public static final GuiIcon BOX_SELECTED_EMPTY = new GuiIcon(ICONS_2, 0, 180, 16, 16);
    public static final GuiIcon BOX_SELECTED_MINUS = new GuiIcon(ICONS_2, 16, 180, 16, 16);
    public static final GuiIcon BOX_SELECTED_PLUS = new GuiIcon(ICONS_2, 32, 180, 16, 16);
    public static final GuiIcon BOX_SELECTED_TICKED = new GuiIcon(ICONS_2, 48, 180, 16, 16);
    public static final GuiIcon BOX_SELECTED_CHAPTER = new GuiIcon(ICONS_2, 64, 180, 16, 16);

    public static final SpriteRaw BOX_CODE_SPRITE = new SpriteRaw(ICONS_2, 80, 164, 16, 16, 256);
    public static final GuiIcon BOX_CODE = new GuiIcon(BOX_CODE_SPRITE, 256);
    public static final SpriteNineSliced BOX_CODE_SLICED = new SpriteNineSliced(BOX_CODE_SPRITE, 4, 4, 12, 12, 16);

    public static final GuiIcon BORDER_TOP_LEFT = new GuiIcon(ICONS_2, 0, 196, 13, 13);
    public static final GuiIcon BORDER_TOP_RIGHT = new GuiIcon(ICONS_2, 13, 196, 13, 13);
    public static final GuiIcon BORDER_BOTTOM_LEFT = new GuiIcon(ICONS_2, 0, 209, 13, 13);
    public static final GuiIcon BORDER_BOTTOM_RIGHT = new GuiIcon(ICONS_2, 13, 209, 13, 13);

    public static final GuiIcon ORDER_TYPE = new GuiIcon(ICONS_2, 0, 0, 14, 14);
    public static final GuiIcon ORDER_MOD_TYPE = new GuiIcon(ICONS_2, 14, 0, 14, 14);
    public static final GuiIcon ORDER_ALPHABETICAL = new GuiIcon(ICONS_2, 28, 0, 14, 14);

    public static final GuiIcon EXPANDED_ARROW = new GuiIcon(ICONS_2, 96, 164, 16, 16);
    public static final GuiIcon CLOSED_ARROW = new GuiIcon(ICONS_2, 96, 180, 16, 16);

    public static final GuiIcon CHAPTER_MARKER = new GuiIcon(ICONS_2, 0, 56, 32, 32);
    public static final GuiIcon CHAPTER_MARKER_LEFT = new GuiIcon(ICONS_2, 0, 56, 24, 32);
    public static final GuiIcon CHAPTER_MARKER_RIGHT = new GuiIcon(ICONS_2, 8, 56, 24, 32);

    public static final SpriteNineSliced CHAPTER_MARKER_9;
    public static final SpriteNineSliced CHAPTER_MARKER_9_LEFT;
    public static final SpriteNineSliced CHAPTER_MARKER_9_RIGHT;

    public static final GuiIcon NOTE_PAGE = new GuiIcon(NOTE, 0, 0, 131, 164);
    public static final GuiIcon NOTE_UNDERLAY = new GuiIcon(ICONS_2, 0, 1, 3, 4);
    public static final GuiIcon NOTE_OVERLAY = new GuiIcon(ICONS_2, 0, 1, 2, 3);

    public static final GuiIcon SEARCH_ICON = new GuiIcon(ICONS_2, 26, 196, 12, 12);
    public static final GuiIcon SEARCH_TAB_CLOSED = new GuiIcon(ICONS_2, 58, 196, 14, 6);
    public static final GuiIcon SEARCH_TAB_OPEN = new GuiIcon(ICONS_2, 40, 209, 106, 14);

    public static final GuiIcon[] ORDERS = { ORDER_TYPE, ORDER_MOD_TYPE, ORDER_ALPHABETICAL };

    public static final GuiRectangle BACK_POSITION = new GuiRectangle(
        PAGE_LEFT.width - BACK.width / 2, PAGE_LEFT.height - BACK.height - 2, BACK.width, BACK.height
    );

    public static final TypeOrder[] SORTING_TYPES = { //
        new TypeOrder("buildcraft.guide.order.type_subtype", ETypeTag.TYPE, ETypeTag.SUB_TYPE), //
        new TypeOrder("buildcraft.guide.order.mod_type", ETypeTag.MOD, ETypeTag.TYPE), //
        new TypeOrder("buildcraft.guide.order.alphabetical")//
    };

    public static final IGuiArea FLOATING_CHAPTER_MENU;


    static {
        CHAPTER_MARKER_9 = new SpriteNineSliced(CHAPTER_MARKER.sprite, 8, 8, 24, 24, 32);
        CHAPTER_MARKER_9_LEFT = new SpriteNineSliced(CHAPTER_MARKER_LEFT.sprite, 8, 8, 24, 24, 24, 32);
        CHAPTER_MARKER_9_RIGHT = new SpriteNineSliced(CHAPTER_MARKER_RIGHT.sprite, 0, 8, 16, 24, 24, 32);
        FLOATING_CHAPTER_MENU = IGuiArea.create(() -> 10, () -> 10, () -> 330, () -> 228);
    }
    public final MinecraftClient mc = MinecraftClient.getInstance();
    public final MousePosition mouse = new MousePosition();
    public final GuideBook book;
    public final GuideContentsData bookData;
    public TypeOrder sortingOrder = SORTING_TYPES[0];
    public int minX, minY, guiLeft, guiTop, xSize = 386, ySize = 248;
    public ItemStack tooltipStack;
    public final List<List<String>> tooltips = new ArrayList<>();
    private final Deque<GuidePageBase> pages = new ArrayDeque<>();
    private final List<GuideChapter> chapters = new ArrayList<>();
    private final IFontRenderer font = FontManager.INSTANCE.getOrLoadFont("SansSerif", 9);
    private GuidePageBase currentPage;
    private TextFieldWidget search;
    private float bookScale = 1;
    private int generation;

    public GuiGuide() { this((GuideBook) null); }
    public GuiGuide(String name) { this(GuideBookRegistry.INSTANCE.getBook(name)); }
    private GuiGuide(GuideBook book) {
        super(Text.literal("BuildCraft Guide"));
        this.book = book;
        this.bookData = book == null ? GuideManager.BOOK_ALL_DATA : book.data;
        generation = GuideManager.INSTANCE.getGeneration();
        openPage(new GuidePageContents(this));
    }

    @Override protected void init() {
        bookScale = Math.min(1, Math.min(width / 410f, height / 290f));
        minX = guiLeft = Math.round((width / bookScale - xSize) / 2);
        minY = guiTop = Math.round((height / bookScale - ySize) / 2);
        search = new TextFieldWidget(textRenderer, Math.max(4, width / 2 - 100), 4, Math.min(200, width - 8), 16,
            Text.translatable("buildcraft.guide.search"));
        search.setMaxLength(128);
        search.setChangedListener(query -> {
            if (currentPage instanceof GuidePageContents contents) contents.setQuery(query);
        });
        addDrawableChild(search);
        search.setVisible(currentPage instanceof GuidePageContents);
    }

    public IFontRenderer getCurrentFont() { return font; }
    public GuidePageBase getCurrentPage() { return currentPage; }
    public List<GuideChapter> getChapters() { return chapters; }
    public MousePosition getMousePosition() { return mouse; }
    public IGuiArea getBookArea() { return new GuiRectangle(minX, minY, xSize, ySize); }
    public boolean isSmallScreen() { return width / bookScale < 590; }
    public void refreshChapters() {
        chapters.clear();
        chapters.addAll(currentPage.getChapters());
    }
    private void setPage(GuidePageBase page) {
        currentPage = Objects.requireNonNull(page);
        page.setFontRenderer(font);
        refreshChapters();
        chapters.forEach(c -> { c.setFontRenderer(font); c.reset(); });
        if (search != null) { search.setVisible(page instanceof GuidePageContents); search.setFocused(false); }
    }
    public void openPage(GuidePageBase page) {
        if (page == null) return;
        if (currentPage != null && currentPage.shouldPersistHistory()) pages.push(currentPage);
        while (pages.size() > 64) pages.removeLast();
        setPage(page);
    }
    public void navigateTo(GuidePageBase page) { openPage(page); }
    public void navigateTo(GuideChapter chapter) { chapter.navigate(); }
    public void closePage() { if (pages.isEmpty()) close(); else setPage(pages.pop()); }
    public void goBackToMenu() {
        pages.clear();
        setPage(new GuidePageContents(this));
        if (search != null) search.setText("");
    }
    @Override public void tick() {
        if (generation != GuideManager.INSTANCE.getGeneration()) {
            generation = GuideManager.INSTANCE.getGeneration();
            pages.clear();
            GuidePageBase fresh = currentPage.createReloaded();
            setPage(fresh == null ? new GuidePageContents(this) : fresh);
        }
        currentPage.updateScreen();
        currentPage.tick();
        for (GuideChapter chapter : chapters) chapter.updateScreen();
        if (search != null) search.tick();
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        mouse.setMousePosition(mouseX / bookScale, mouseY / bookScale);
        tooltipStack = null;
        tooltips.clear();
        try (GuiDrawContext frame = GuiDrawContext.use(context)) {
            context.getMatrices().push();
            try {
                context.getMatrices().scale(bookScale, bookScale, 1);
                context.setShaderColor(1, 1, 1, 1);
                PAGE_LEFT.drawAt(minX, minY);
                PAGE_RIGHT.drawAt(minX + PAGE_LEFT.width, minY);
                currentPage.renderFirstPage(minX + 23, minY + 25, PAGE_WIDTH, PAGE_HEIGHT);
                currentPage.renderSecondPage(minX + 193 + 4, minY + 25, PAGE_WIDTH, PAGE_HEIGHT);
                if (!isSmallScreen()) {
                    int drawn = 0;
                    for (GuideChapter chapter : chapters) if (!chapter.hasParent()) drawn += chapter.draw(drawn, delta, false);
                }
                (BACK_POSITION.offset(minX, minY).contains(mouse) ? BACK_HOVERED : BACK).drawAt(BACK_POSITION.offset(minX, minY));
                font.drawString("Remake by FelixMantra", minX + 105, minY + 250, 0xFFFFFFFF);
            } finally {
                context.getMatrices().pop();
                context.setShaderColor(1, 1, 1, 1);
            }
            super.render(context, mouseX, mouseY, delta);
            if (tooltipStack != null) context.drawItemTooltip(textRenderer, tooltipStack, mouseX, mouseY);
            else if (!tooltips.isEmpty()) context.drawTooltip(textRenderer,
                tooltips.get(0).stream().map(Text::literal).map(t -> (Text)t).toList(), mouseX, mouseY);
        }
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        mouse.setMousePosition(mouseX / bookScale, mouseY / bookScale);
        if (button == 1) { closePage(); return true; }
        if (button != 0) return false;
        if (BACK_POSITION.offset(minX, minY).contains(mouse)) { closePage(); return true; }
        if (!isSmallScreen()) for (GuideChapter chapter : List.copyOf(chapters)) if (chapter.handleClick() != 0) return true;
        GuidePageBase target = currentPage;
        int index = target.getPage();
        target.handleMouseClick(minX + 23, minY + 25, PAGE_WIDTH, PAGE_HEIGHT, (int)mouse.getX(), (int)mouse.getY(), button, index, false);
        if (currentPage == target) target.handleMouseClick(minX + 197, minY + 25, PAGE_WIDTH, PAGE_HEIGHT,
            (int)mouse.getX(), (int)mouse.getY(), button, index + 1, false);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (super.keyPressed(key, scan, mods)) return true;
        if (search != null && search.isFocused()) return false;
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) currentPage.nextPage();
        else if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) currentPage.lastPage();
        else if (key == GLFW.GLFW_KEY_BACKSPACE) closePage();
        else return false;
        return true;
    }
    @Override public boolean shouldPause() { return false; }
}
