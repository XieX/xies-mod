package mod.xie.xm.blocks;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;


public class XieGuiFurnace extends GuiContainer {
	
    protected XieTileEntityFurnace furnace;
    private static ResourceLocation background = new ResourceLocation("textures/gui/container/furnace.png");

    public XieGuiFurnace(InventoryPlayer inventoryplayer, XieTileEntityFurnace tileentityfurnace) {
        super(new ContainerFurnace(inventoryplayer, tileentityfurnace));
        furnace = tileentityfurnace;
    }

    protected void drawGuiContainerForegroundLayer() {
        fontRenderer.drawString(furnace.block.getDisplayName(), 60, 6, 0x404040);
        fontRenderer.drawString("Inventory", 8, (ySize - 96) + 2, 0x404040);
    }

    // copy-pasta from GuiFurnace
    protected void drawGuiContainerBackgroundLayer(float f, int i, int j) {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.func_110434_K().func_110577_a(background);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, xSize, ySize);
        if(furnace.isBurning()) {
            int t = furnace.getBurnTimeRemainingScaled(12);
            drawTexturedModalRect(x + 56, (y + 36 + 12) - t, 176, 12 - t, 14, t + 2);
        }
        int cookTime = furnace.getCookProgressScaled(24);
        drawTexturedModalRect(x + 79, y + 34, 176, 14, cookTime + 1, 16);
    }

}
