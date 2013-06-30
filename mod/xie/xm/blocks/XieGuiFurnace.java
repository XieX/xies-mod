package mod.xie.xm.blocks;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerFurnace;

import org.lwjgl.opengl.GL11;


public class XieGuiFurnace extends GuiContainer {
	
    protected XieTileEntityFurnace furnace;

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
        mc.renderEngine.bindTexture("/gui/furnace.png");
        int l = (width - xSize) / 2;
        int i1 = (height - ySize) / 2;
        drawTexturedModalRect(l, i1, 0, 0, xSize, ySize);
        if(furnace.isBurning())
        {
            int j1 = furnace.getBurnTimeRemainingScaled(12);
            drawTexturedModalRect(l + 56, (i1 + 36 + 12) - j1, 176, 12 - j1, 14, j1 + 2);
        }
        int k1 = furnace.getCookProgressScaled(24);
        drawTexturedModalRect(l + 79, i1 + 34, 176, 14, k1 + 1, 16);
    }
}
