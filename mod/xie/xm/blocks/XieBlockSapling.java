package mod.xie.xm.blocks;

import java.util.List;

import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.StepSound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Icon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.creativetab.CreativeTabs;
import java.util.Random;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;


public class XieBlockSapling extends XieBlockPlant implements IXieBlock {

	public XieBlockSapling(String plantName, int plantID) {
		super(plantName, plantID);
		growsOn = new int[] {Block.dirt.blockID, Block.grass.blockID};
		growsInto = Block.leaves.blockID;
		setRenderType(1);
		setBlockBounds(0.1F,0.1F,0.1F,0.9F,0.9F,0.9F);
		growthRate=14;
		this.setCreativeTab(CreativeTabs.tabDecorations);
	}

	// update tick override
	// since my saplings can support multiple tree types (up to 8 each)
	// we only have 1 bit with which to handle growth amount, which
	// just so happens to be the same bit that leaves use to decay
	public void updateTick(World world, int i, int j, int k, Random random) {
        // super.updateTick(world, i, j, k, random);
	    testThatBlockCanStillGrowHere(world, i, j, k);
    	
	    int netGrowthRate = (int)(100F / growthRate);
	    if (netGrowthRate<=0) netGrowthRate=1;
	    
	    if(random.nextInt(netGrowthRate) == 0)
        {
            int meta = world.getBlockMetadata(i,j,k);
            if ((meta & 8) == 0) {
            	Xie.setBlockMeta(world, i, j, k, meta | 8);
            } else {
            	growIntoSomethingElse(world, i, j, k);
            }
       }
	}
	
	// need to filter for decay flag
	@Override
	public Icon getIcon(int i, int j) {
		return tex.getTexFromSideAndMeta(i, j&-9);
	}

	public void dropBlockAsItemWithChance(World world, int i, int j, int k, int l, float f, int multiplier) {
       if(world.isRemote)
       {
           return;
       }
       if (drops==null || drops.isEmpty()) {
    	  	// drop self
    	   ItemStack drop;
    	   if (inheritMeta) drop = new ItemStack(blockID, 1, l&-9);
    	   else drop = new ItemStack(blockID, 1, 0);
    	   Xie.spawnItem(drop, world, i, j, k);
       } else {
    	   // get drops
    	   Xie.spawnItems(drops.getDropsForMetaAndMultiplier(l, multiplier), world, i, j, k);
       }
	}

	
    @SideOnly(Side.CLIENT)
    @Override
    public void getSubBlocks(int id, CreativeTabs tab, List list) {
    	list.add(new ItemStack(id,1,0));
    	if (names==null) return;
    	
    	int i=0;
    	while (++i<names.length) list.add(new ItemStack(id, 1, i));
    }
	
}
