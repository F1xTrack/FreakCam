package com.freakcam.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

public class GuiSlider extends GuiButton {

    public interface ISliderListener {

        void onChange(GuiSlider slider, float value);
    }

    private float value; // 0.0 to 1.0
    private final float minValue;
    private final float maxValue;
    private final String labelPrefix;
    private final String labelSuffix;
    private final ISliderListener listener;
    private boolean dragging = false;

    public GuiSlider(int id, int x, int y, int width, int height, String labelPrefix, String labelSuffix,
        float minValue, float maxValue, float currentValue, ISliderListener listener) {
        super(id, x, y, width, height, "");
        this.labelPrefix = labelPrefix;
        this.labelSuffix = labelSuffix;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.listener = listener;
        setValue(currentValue);
    }

    public float getValue() {
        return minValue + value * (maxValue - minValue);
    }

    public void setValue(float val) {
        this.value = (Math.max(minValue, Math.min(maxValue, val)) - minValue) / (maxValue - minValue);
        updateDisplayString();
    }

    private void updateDisplayString() {
        this.displayString = String.format("%s: %.2f%s", labelPrefix, getValue(), labelSuffix);
    }

    @Override
    public int getHoverState(boolean mouseOver) {
        return 0;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible && this.dragging) {
            this.value = (float) (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
            this.value = Math.max(0.0f, Math.min(1.0f, this.value));
            updateDisplayString();
            if (listener != null) {
                listener.onChange(this, getValue());
            }
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            this.value = (float) (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
            this.value = Math.max(0.0f, Math.min(1.0f, this.value));
            this.dragging = true;
            updateDisplayString();
            if (listener != null) {
                listener.onChange(this, getValue());
            }
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.dragging = false;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible) {
            mc.getTextureManager()
                .bindTexture(buttonTextures);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;

            int k = this.getHoverState(this.field_146123_n);
            this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, 46 + k * 20, this.width / 2, this.height);
            this.drawTexturedModalRect(
                this.xPosition + this.width / 2,
                this.yPosition,
                200 - this.width / 2,
                46 + k * 20,
                this.width / 2,
                this.height);

            this.mouseDragged(mc, mouseX, mouseY);

            int handleX = (int) (this.value * (float) (this.width - 8));
            this.drawTexturedModalRect(this.xPosition + handleX, this.yPosition, 0, 66, 4, 20);
            this.drawTexturedModalRect(this.xPosition + handleX + 4, this.yPosition, 196, 66, 4, 20);

            int color = 14737632;
            if (!this.enabled) {
                color = 10526880;
            } else if (this.field_146123_n) {
                color = 16777120;
            }

            this.drawCenteredString(
                mc.fontRenderer,
                this.displayString,
                this.xPosition + this.width / 2,
                this.yPosition + (this.height - 8) / 2,
                color);
        }
    }
}
