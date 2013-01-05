package xie.mods.xm.blocks;

import xie.mods.xm.parser.Location;

import net.minecraft.block.Block;
import net.minecraft.block.BlockPane;
import net.minecraft.block.StepSound;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.creativetab.CreativeTabs;
import xie.mods.xm.util.Xie;
import xie.mods.xm.util.XieTex;

public class XieBlockMounted extends XieBlock{

	public boolean isMovable = true;
	public ItemStack movableTool = null;
	
	protected boolean canFace[];
	protected float blockBoundsBase [];
	
	public XieBlockMounted(String n, int id, Material mat) {
		super(id, mat, n);
	}
	
	public void setCanFace(int i, boolean b) {
		if (canFace==null) canFace = new boolean[6];
		if (i<0 || i>=6) return;
		canFace[i]=b;
	}
	
	public void setCanFace(String face, boolean b) {
		int i = -1;
		for (Location l : Location.values()) {
			if (l.name().toLowerCase().equals(face.toLowerCase())) i=l.ordinal();
		}
		setCanFace(i,b);
	}
	
	public void setCanFace(String facing) {
		String faces[] = facing.split(",");
		for (String f : faces) setCanFace(f,true);
	}

	public boolean canPlaceBlockAt(World world, int i, int j, int k) {
		return super.canPlaceBlockAt(world, i, j, k);
    }
	
	public boolean canPlaceBlockOnSide(World world, int i, int j, int k, int l) {
		if (canFace==null) return canPlaceBlockAt(world, i, j, k);
		if (canFace[l]) return canPlaceBlockAt(world, i, j, k);
		else return false;
	}
		
	@Override
	public void onBlockPlacedBy(World world, int i, int j, int k, EntityLiving e) {
		int facing = Xie.getFacing(e);
        world.setBlockMetadataWithNotify(i, j, k, facing);
        super.onBlockPlacedBy(world, i, j, k, e);
    }
	
	public void onNeighborBlockChange(World world, int i, int j, int k, int l) {
		// TODO
    }
	
	public void dropBlock(World world, int i, int j, int k) {
		dropBlockAsItem(world, i, j, k, world.getBlockMetadata(i, j, k), 0);
        world.setBlockWithNotify(i, j, k, 0);
	}
	
    @Override
	public boolean isOpaqueCube() { return false; }

    @Override
	public boolean renderAsNormalBlock() { return false; }
	
	protected boolean checkCeiling(World world, int i, int j, int k) { return world.getBlockId(i, j+1, k)!=0; }
	protected boolean checkFloor(World world, int i, int j, int k) { return world.getBlockId(i, j-1, k)!=0;	}
	protected boolean checkWest(World world, int i, int j, int k) {	return world.getBlockId(i, j, k-1)!=0;	}
	protected boolean checkEast(World world, int i, int j, int k) {	return world.getBlockId(i, j, k+1)!=0; }
	protected boolean checkNorth(World world, int i, int j, int k) { return world.getBlockId(i-1, j, k)!=0;	}
	protected boolean checkSouth(World world, int i, int j, int k) { return world.getBlockId(i+1, j, k)!=0;	}

	public boolean blockActivated(World world, int i, int j, int k, EntityPlayer entityplayer) {
    	if (!isMovable) return false;
    	if (entityplayer.getCurrentEquippedItem()!=movableTool) return false;
    	
    	int meta = world.getBlockMetadata(i, j, k);
    	
    	for (int n=1; n<6; n++) {
    		int newMeta = (meta + n) % 6;
    		if (canFace==null || canFace[newMeta]) {
    			meta = newMeta;
    			break;
    		}
    	}
    	
    	Xie.setBlockMeta(world, i, j, k, meta);
    	return true;
    }
	
	// when texture object is set, rotate and fill in metadata values for rotated versions of the block 
    @Override
	public void setTex(XieTex tex) {
    	XieTex newTex = new XieTex();
    	int topTex = tex.getTexFromSide(XieTex.TOP);
    	int botTex = tex.getTexFromSide(XieTex.BOT);
    	int frontTex = tex.getTexFromSide(XieTex.NORTH);
    	int leftTex = tex.getTexFromSide(XieTex.EAST);
    	int rightTex = tex.getTexFromSide(XieTex.WEST);
    	int backTex = tex.getTexFromSide(XieTex.SOUTH);
    	
    	for (Location facing: Location.values()) {
    		switch(facing) {
	    		case CEILING:
	    			newTex.setTexture(topTex, XieTex.EAST, facing.ordinal());
	    			newTex.setTexture(botTex, XieTex.WEST, facing.ordinal());
	    			newTex.setTexture(leftTex, XieTex.NORTH, facing.ordinal());
	    			newTex.setTexture(rightTex, XieTex.SOUTH, facing.ordinal());
	    			newTex.setTexture(frontTex, XieTex.BOT, facing.ordinal());
	    			newTex.setTexture(backTex, XieTex.TOP, facing.ordinal());
	    			break;
	    		case FLOOR:
	    			newTex.setTexture(topTex, XieTex.EAST, facing.ordinal());
	    			newTex.setTexture(botTex, XieTex.WEST, facing.ordinal());
	    			newTex.setTexture(leftTex, XieTex.NORTH, facing.ordinal());
	    			newTex.setTexture(rightTex, XieTex.SOUTH, facing.ordinal());
	    			newTex.setTexture(frontTex, XieTex.TOP, facing.ordinal());
	    			newTex.setTexture(backTex, XieTex.BOT, facing.ordinal());
	    			break;
	    		case NORTH:
	    			newTex.setTexture(topTex, XieTex.TOP, facing.ordinal());
	    			newTex.setTexture(botTex, XieTex.BOT, facing.ordinal());
	    			newTex.setTexture(frontTex, XieTex.NORTH, facing.ordinal());
	    			newTex.setTexture(leftTex, XieTex.EAST, facing.ordinal());
	    			newTex.setTexture(rightTex, XieTex.WEST, facing.ordinal());
	    			newTex.setTexture(backTex, XieTex.SOUTH, facing.ordinal());
	    			break;
	    		case SOUTH:
	    			newTex.setTexture(topTex, XieTex.TOP, facing.ordinal());
	    			newTex.setTexture(botTex, XieTex.BOT, facing.ordinal());
	    			newTex.setTexture(frontTex, XieTex.SOUTH, facing.ordinal());
	    			newTex.setTexture(leftTex, XieTex.WEST, facing.ordinal());
	    			newTex.setTexture(rightTex, XieTex.EAST, facing.ordinal());
	    			newTex.setTexture(backTex, XieTex.NORTH, facing.ordinal());
	    			break;
	    		case EAST:
	    			newTex.setTexture(topTex, XieTex.TOP, facing.ordinal());
	    			newTex.setTexture(botTex, XieTex.BOT, facing.ordinal());
	    			newTex.setTexture(frontTex, XieTex.EAST, facing.ordinal());
	    			newTex.setTexture(leftTex, XieTex.SOUTH, facing.ordinal());
	    			newTex.setTexture(rightTex, XieTex.NORTH, facing.ordinal());
	    			newTex.setTexture(backTex, XieTex.WEST, facing.ordinal());
	    			break;
	    		case WEST:
	    			newTex.setTexture(topTex, XieTex.TOP, facing.ordinal());
	    			newTex.setTexture(botTex, XieTex.BOT, facing.ordinal());
	    			newTex.setTexture(frontTex, XieTex.WEST, facing.ordinal());
	    			newTex.setTexture(leftTex, XieTex.NORTH, facing.ordinal());
	    			newTex.setTexture(rightTex, XieTex.SOUTH, facing.ordinal());
	    			newTex.setTexture(backTex, XieTex.EAST, facing.ordinal());
	    			break;
    		}
    	}
    	
    	if (tex.getTexFile()!=null)
    		newTex.setTexFile(tex.getTexFile());
    	
    	super.setTex(newTex);
    }
}
