package mod.xie.xm.items;

import java.util.List;

import mod.xie.xm.Work;
import mod.xie.xm.file.XieLog;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumMovingObjectType;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;


public class XieItemBucket extends XieItem {

	public static enum Contents {
		empty, water, lava, milk
	}
	
	public boolean canHold[];
	
	public XieItemBucket(String name, int i, int j) {
		super(name, i, j);
		names = new String[] {"Bucket", "Bucket of Water", "Bucket of Lava", "Bucket of Milk"};
		canHold = new boolean[Contents.values().length];
		for (int n=0; n<canHold.length; n++) canHold[n]=true;
		setHasSubtypes(true);
	}

	public void setName(String contents, String name) {
		setName(parseContents(contents),name);
	}
	
	public void setName(Contents contents, String name) {
		names[contents.ordinal()]=name;
	}
	
	// TODO make milk drinkable
	public ItemStack onItemRightClick(ItemStack itemstack, World world, EntityPlayer entityplayer) {
		int dmg = itemstack.getItemDamage();
		if (dmg>=Contents.values().length) dmg=0; 
		Contents contents = Contents.values()[dmg];
		boolean isFillable = dmg==0;
		
		float f = 1.0F;
        double x = entityplayer.prevPosX + (entityplayer.posX - entityplayer.prevPosX) * (double)f;
        double y = (entityplayer.prevPosY + (entityplayer.posY - entityplayer.prevPosY) * (double)f + 1.6200000000000001D) - (double)entityplayer.yOffset;
        double z = entityplayer.prevPosZ + (entityplayer.posZ - entityplayer.prevPosZ) * (double)f;
        
        MovingObjectPosition movingobjectposition = getMovingObjectPositionFromPlayer(world, entityplayer, isFillable);
        if (movingobjectposition == null) return itemstack;

        if (isFillable && cowProximityTest(world,x,y,z)) {
        	if (canHold[Contents.milk.ordinal()])
        		return getItemStack(Contents.milk);
        	else return itemstack;
        }
        
        if (movingobjectposition.typeOfHit == EnumMovingObjectType.TILE) {
        	return doTileHit(world, itemstack, entityplayer, movingobjectposition, isFillable, x, y, z);
        }
        
        return itemstack;
    }
	
	protected boolean cowProximityTest(World world, double x, double y, double z) {
		// TODO make proximity test in FRONT of player only (instead of 1m in each direction :P)
		AxisAlignedBB proximity = AxisAlignedBB.getBoundingBox(x-1,y-1,z-1,x+1,y+1,z+1);
		List cowz = world.getEntitiesWithinAABB(EntityCow.class, proximity);
		return !cowz.isEmpty();
	}
	
	protected ItemStack doTileHit(World world, ItemStack itemstack, EntityPlayer entityplayer, MovingObjectPosition mop, boolean isFillable, double x, double y, double z) {
	    int i = mop.blockX;
        int j = mop.blockY;
        int k = mop.blockZ;
        if (!world.canMineBlock(entityplayer, i, j, k)) return itemstack;
        
        if (isFillable) {
            if (!entityplayer.canPlayerEdit(i, j, k, k, itemstack)) return itemstack;
            
            if (world.getBlockMaterial(i, j, k) == Material.water && world.getBlockMetadata(i, j, k) == 0) {
            	if (!canHold[Contents.water.ordinal()]) return itemstack;
                world.setBlockWithNotify(i, j, k, 0);
                if (entityplayer.capabilities.isCreativeMode) return itemstack;
                else return getItemStack(Contents.water);
            }
            
            if (world.getBlockMaterial(i, j, k) == Material.lava && world.getBlockMetadata(i, j, k) == 0) {
            	if (!canHold[Contents.lava.ordinal()]) return itemstack;
            	world.setBlockWithNotify(i, j, k, 0);
                if (entityplayer.capabilities.isCreativeMode) return itemstack;
                else return getItemStack(Contents.lava);
            }
        } else {
            if (!isEmptyable(itemstack)) return itemstack;
            if (!entityplayer.canPlayerEdit(i, j, k, k, itemstack)) return itemstack;
            
            if (mop.sideHit == 0) j--;
            if (mop.sideHit == 1) j++;
            if (mop.sideHit == 2) k--;
            if (mop.sideHit == 3) k++;
            if (mop.sideHit == 4) i--;
            if (mop.sideHit == 5) i++;
                        
            if (world.isAirBlock(i, j, k) || !world.getBlockMaterial(i, j, k).isSolid()) {            
                if (world.provider.isHellWorld && isWater(itemstack))
                	doFizzle(world, x, y, z, i, j, k);
                else world.setBlockAndMetadataWithNotify(i, j, k, getContentsBlock(itemstack).blockID, 0);
                
                if (entityplayer.capabilities.isCreativeMode) return itemstack;
                else return getItemStack(Contents.empty);
            }
        }
        
        return itemstack;
	}
	
	public ItemStack getItemStack(Contents contents) {
		return new ItemStack(this,1,contents.ordinal());
	}
	
	public boolean isEmptyable(ItemStack i) {
		return getContentsBlock(i)!=null;
	}
	
	protected Block getContentsBlock(ItemStack itemstack) {
		int dmg = itemstack.getItemDamage();
		if (dmg>=Contents.values().length) dmg=0;
		Contents contents = Contents.values()[dmg];
		switch (contents) {
		case water: return Block.waterMoving;
		case lava: return Block.lavaMoving;
		default:
			return null;
		}
	}
	
	protected boolean isWater(ItemStack itemstack) {
		return itemstack.getItemDamage()==Contents.water.ordinal();
	}
	
	protected void doFizzle(World world, double x, double y, double z, int i, int j, int k) {
		world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.fizz", 0.5F, 2.6F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.8F);
        for (int l = 0; l < 8; l++) 
        	world.spawnParticle("largesmoke", i + Math.random(), j + Math.random(), k + Math.random(), 0.0D, 0.0D, 0.0D);
	}

	public void setCanHold(Contents contents, Boolean can) {
		canHold[contents.ordinal()]=can;
	}
	
	public static boolean isValidContents(String s) {
		s = s.toLowerCase().trim();
		for (Contents c : Contents.values()) if (c.name().equals(s)) return true;
		return false;
	}
	
	public static Contents parseContents(String s) {
		s = s.toLowerCase().trim();
		for (Contents c : Contents.values()) if (c.name().equals(s)) return c;
		
		String errorString = "Could not identify bucket contents "+s+". Try one of these: ";
		for (Contents content : Contents.values()) errorString=errorString+content.name()+",";
		errorString = errorString.substring(0,errorString.length()-2)+".";
		XieLog.log().logIncomplete(errorString);
		return Contents.empty;
	}
}
