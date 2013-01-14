package mod.xie.xm.blocks;

import net.minecraft.client.model.ModelChest;
import net.minecraft.client.model.ModelLargeChest;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;

import org.lwjgl.opengl.GL11;

public class XieChestRenderer extends TileEntitySpecialRenderer {
	 	protected ModelChest chestModel;
	    protected ModelChest doubleChestModel;
	    
	    public XieChestRenderer() {
	        chestModel = new ModelChest();
	        doubleChestModel = new ModelLargeChest();
	    }

	    public void renderTileEntityAt(TileEntity tileentity, double d, double d1, double d2, float f) {
	    	renderTileEntityChestAt((TileEntityChest)tileentity, d, d1, d2, f);
	    }

	    public void renderTileEntityChestAt(TileEntityChest tileEntityChest, double d, double d1, double d2, float f) {
	    	XieTileEntityChest chestEntity = (XieTileEntityChest)tileEntityChest;
	    	XieBlockChest chestBlock = chestEntity.chest;
	    	
	        // determine metadata
	    	int meta;
	        if (chestEntity.worldObj == null) {
	            if (chestBlock.vertical) meta = 7;
	            else meta = 3;
	        } else {
	            meta = chestEntity.getBlockMetadata();
	            if (chestBlock != null && (meta < 0 || meta > 7)) {
	            	chestBlock.unifyAdjacentChests(chestEntity.worldObj, chestEntity.xCoord, chestEntity.yCoord, chestEntity.zCoord);
	                meta = chestEntity.getBlockMetadata();
	            }
	            chestEntity.checkForAdjacentChests();
	        }

	        // check for presence of "lower" chest and return, to avoid doubling up
	        if (chestEntity.adjacentChestZNeg != null || chestEntity.adjacentChestXNeg != null || chestEntity.adjacentChestYPos != null) return;

	        // build model for single or double chest as appropriate
	        ModelChest modelchest;
	        if (chestEntity.adjacentChestXPos != null || chestEntity.adjacentChestZPosition != null || chestEntity.adjacentChestYNeg != null) {
	            modelchest = doubleChestModel;
	            bindTextureByName(chestBlock.getLargeTex());
	        } else {
	            modelchest = chestModel;
	            bindTextureByName(chestBlock.getSmallTex());
	        }

	        // start render
	        GL11.glPushMatrix();
	        GL11.glEnable(32826 /*GL_RESCALE_NORMAL_EXT*/);
	        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	        GL11.glTranslatef((float)d, (float)d1 + 1.0F, (float)d2 + 1.0F);
	        GL11.glScalef(1.0F, -1F, -1F);
	        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
	        
	        // horizontal rotation
	        int angle = 0;
	        if (meta == 2 || meta == 6) angle = 180;
	        //if (meta == 3 || meta == 7) angle = 0; // default
	        if (meta == 4 || meta == 1) angle = 90;
	        if (meta == 5 || meta == 0) angle = -90;
	        if (chestBlock.mergeHorizontal) {
	        	if (meta == 2 && chestEntity.adjacentChestXPos != null) GL11.glTranslatef(1.0F, 0.0F, 0.0F);
	        	if (meta == 5 && chestEntity.adjacentChestZPosition != null) GL11.glTranslatef(0.0F, 0.0F, -1F);
	        }
	        GL11.glRotatef(angle, 0.0F, 1.0F, 0.0F);
	        
	        // vertical chest rotation
	        if (meta == 0 || meta == 1 || meta == 6 || meta == 7) GL11.glRotatef(90, 0, 0, 1F);
	        
	        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);

	        // update angle
	        float f1 = chestEntity.prevLidAngle + (chestEntity.lidAngle - chestEntity.prevLidAngle) * f;
	        if (chestEntity.adjacentChestZNeg != null) {
	            float f2 = chestEntity.adjacentChestZNeg.prevLidAngle + (chestEntity.adjacentChestZNeg.lidAngle - chestEntity.adjacentChestZNeg.prevLidAngle) * f;
	            if (f2 > f1)  f1 = f2;
	        }
	        if (chestEntity.adjacentChestXNeg != null) {
	            float f3 = chestEntity.adjacentChestXNeg.prevLidAngle + (chestEntity.adjacentChestXNeg.lidAngle - chestEntity.adjacentChestXNeg.prevLidAngle) * f;
	            if (f3 > f1) f1 = f3;
	        }
	        f1 = 1.0F - f1;
	        f1 = 1.0F - f1 * f1 * f1;
	        modelchest.chestLid.rotateAngleX = -((f1 * 3.141593F) / 2.0F);
	        modelchest.renderAll();
	        
	        // finish render
	        GL11.glDisable(32826 /*GL_RESCALE_NORMAL_EXT*/);
	        GL11.glPopMatrix();
	        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	    }
}
