package mod.xie.xm.blocks;


import mod.xie.xm.parser.Location;
import mod.xie.xm.util.Xie;
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

public class XieBlockFacing extends XieBlockMounted {
	
	public XieBlockFacing(String n, int id, Material mat) {
		super(n, id, mat);
	}
	
	public boolean canPlaceBlockOnSide(World world, int i, int j, int k, int l) {
		return true;
	}
	
	public void onNeighborBlockChange(World world, int i, int j, int k, int l) {
		return;
    }
	
	public void onBlockPlacedBy(World world, int i, int j, int k, EntityLiving entityliving) {
        int playerFacing = MathHelper.floor_double((double)((entityliving.rotationYaw * 4F) / 360F) + 0.5D) & 3;
        int meta = 0;
        if (playerFacing == 0) meta = Location.EAST.ordinal();
        if (playerFacing == 1) meta = Location.SOUTH.ordinal();
        if (playerFacing == 2) meta = Location.WEST.ordinal();
        if (playerFacing == 3) meta = Location.NORTH.ordinal();
        world.setBlockMetadataWithNotify(i, j, k, meta);
    }
	
	public boolean blockActivated(World world, int i, int j, int k, EntityPlayer entityplayer) {
    	if (!isMovable) return false;
    	if (entityplayer.getCurrentEquippedItem()!=movableTool) return false;
    	
    	int meta = world.getBlockMetadata(i, j, k);
    	Location facing = Location.values()[meta];
    	switch (facing) {
    		case NORTH: meta = Location.EAST.ordinal(); break;
    		case EAST: meta = Location.SOUTH.ordinal(); break;
    		case SOUTH: meta = Location.WEST.ordinal(); break;
    		case WEST: meta = Location.NORTH.ordinal(); break;
    		default: meta=Location.NORTH.ordinal();
    	}
    	
    	Xie.setBlockMeta(world, i, j, k, meta);
    	return true;
    }
}
