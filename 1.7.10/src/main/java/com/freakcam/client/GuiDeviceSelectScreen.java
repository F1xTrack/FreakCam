package com.freakcam.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.StatCollector;

public class GuiDeviceSelectScreen extends GuiScreen {

    private final GuiScreen parentScreen;
    private final String title;
    private final List<String> devices;
    private final Consumer<String> onSelect;
    private int selectedIndex = 0;
    private DeviceSlot deviceSlot;

    public GuiDeviceSelectScreen(GuiScreen parent, String title, List<String> devices, String currentSelection,
        Consumer<String> onSelect) {
        this.parentScreen = parent;
        this.title = title;
        this.devices = devices;
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
        this.deviceSlot = new DeviceSlot();
        this.deviceSlot.registerScrollButtons(7, 8);
        this.buttonList.add(
            new GuiButton(
                0,
                this.width / 2 - 100,
                this.height - 38,
                200,
                20,
                "\u00a7a" + StatCollector.translateToLocal("gui.freakcam.select")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (!devices.isEmpty() && selectedIndex >= 0 && selectedIndex < devices.size()) {
                onSelect.accept(devices.get(selectedIndex));
            }
            this.mc.displayGuiScreen(this.parentScreen);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.deviceSlot.drawScreen(mouseX, mouseY, partialTicks);
        this.drawCenteredString(this.fontRendererObj, "\u00a7l\u00a76" + this.title, this.width / 2, 12, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    class DeviceSlot extends GuiSlot {

        public DeviceSlot() {
            super(
                GuiDeviceSelectScreen.this.mc,
                GuiDeviceSelectScreen.this.width,
                GuiDeviceSelectScreen.this.height,
                32,
                GuiDeviceSelectScreen.this.height - 48,
                24);
        }

        @Override
        protected int getSize() {
            return devices.size();
        }

        @Override
        protected void elementClicked(int index, boolean isDoubleClick, int mouseX, int mouseY) {
            selectedIndex = index;
            if (isDoubleClick) {
                actionPerformed((GuiButton) buttonList.get(0));
            }
        }

        @Override
        protected boolean isSelected(int index) {
            return index == selectedIndex;
        }

        @Override
        protected void drawBackground() {}

        @Override
        protected void drawSlot(int index, int x, int y, int height, Tessellator tessellator, int mouseX, int mouseY) {
            String name = devices.get(index);
            String display = isSelected(index) ? "\u00a7a\u00a7l> " + name : "\u00a77" + name;
            GuiDeviceSelectScreen.this
                .drawString(GuiDeviceSelectScreen.this.fontRendererObj, display, x + 10, y + 6, 0xFFFFFF);
        }
    }
}
