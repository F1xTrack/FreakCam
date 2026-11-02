package com.freakcam.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

public class GuiCompactDeviceList extends GuiScreen {

    private final GuiScreen parentScreen;
    private final String title;
    private final List<String> devices;
    private final Consumer<String> onSelect;
    private final int boxX;
    private final int boxY;
    private final int boxWidth;
    private final int boxHeight;
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    public GuiCompactDeviceList(GuiScreen parent, String title, List<String> devices, String currentSelection, int boxX,
        int boxY, int boxWidth, int boxHeight, Consumer<String> onSelect) {
        this.parentScreen = parent;
        this.title = title;
        this.devices = devices;
        this.boxX = boxX;
        this.boxY = boxY;
        this.boxWidth = boxWidth;
        this.boxHeight = boxHeight;
        this.onSelect = onSelect;

        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i)
                .equals(currentSelection)) {
                this.selectedIndex = i;
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void initGui() {
        int btnY = boxY + boxHeight - 24;
        this.buttonList.add(
            new GuiButton(
                0,
                boxX + 6,
                btnY,
                (boxWidth - 16) / 2,
                20,
                "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.select")));
        this.buttonList.add(
            new GuiButton(
                1,
                boxX + 10 + (boxWidth - 16) / 2,
                btnY,
                (boxWidth - 16) / 2,
                20,
                "\u00a7c" + StatCollector.translateToLocal("gui.freakcam.cancel")));
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel != 0) {
            int dir = dWheel > 0 ? -1 : 1;
            int visibleItems = (boxHeight - 50) / 18;
            int maxScroll = Math.max(0, devices.size() - visibleItems);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + dir));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (!devices.isEmpty() && selectedIndex >= 0 && selectedIndex < devices.size()) {
                onSelect.accept(devices.get(selectedIndex));
            }
            this.mc.displayGuiScreen(this.parentScreen);
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            int itemY = boxY + 22;
            int itemHeight = 18;
            for (int i = scrollOffset; i < devices.size(); i++) {
                if (itemY + itemHeight > boxY + boxHeight - 28) break;
                if (mouseX >= boxX + 6 && mouseX <= boxX + boxWidth - 6
                    && mouseY >= itemY
                    && mouseY < itemY + itemHeight) {
                    selectedIndex = i;
                    break;
                }
                itemY += itemHeight;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Solid dark background covering entire screen
        drawRect(0, 0, this.width, this.height, 0xDD000000);

        // Solid modal box
        drawRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF101010);
        drawRect(boxX + 1, boxY + 1, boxX + boxWidth - 1, boxY + boxHeight - 1, 0xFF282828);
        drawRect(boxX + 3, boxY + 3, boxX + boxWidth - 3, boxY + 20, 0xFF181818);

        this.drawCenteredString(
            this.fontRendererObj,
            "\u00a7l\u00a76" + this.title,
            boxX + boxWidth / 2,
            boxY + 6,
            0xFFFFFF);

        int itemY = boxY + 22;
        int itemHeight = 18;
        for (int i = scrollOffset; i < devices.size(); i++) {
            if (itemY + itemHeight > boxY + boxHeight - 28) break;

            boolean isHover = mouseX >= boxX + 6 && mouseX <= boxX + boxWidth - 6
                && mouseY >= itemY
                && mouseY < itemY + itemHeight;
            boolean isSel = (i == selectedIndex);

            int bgCol = isSel ? 0xFF006600 : (isHover ? 0xFF3D3D3D : 0xFF222222);
            drawRect(boxX + 6, itemY, boxX + boxWidth - 6, itemY + itemHeight - 2, bgCol);

            String name = devices.get(i);
            if (name.length() > 28) name = name.substring(0, 25) + "...";
            String display = isSel ? "\u00a7a\u00a7l> " + name : "\u00a7f" + name;
            this.drawString(this.fontRendererObj, display, boxX + 10, itemY + 3, 0xFFFFFF);

            itemY += itemHeight;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
