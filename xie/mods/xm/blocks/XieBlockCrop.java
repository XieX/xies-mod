package xie.mods.xm.blocks;

import java.util.Random;

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


public class XieBlockCrop extends XieBlockPlant {
	
	// constructor
	public XieBlockCrop(String plantName, int plantID) {
		super(plantName, plantID);
		setRenderType(6);
	}
	
	public void updateTick(World world, int i, int j, int k, Random random)
	{
        // super.updateTick(world, i, j, k, random);
	    testThatBlockCanStillGrowHere(world, i, j, k);
    	
	    int netGrowthRate = (int) (getCropGrowthRate(world,i,j,k) * (growthRate/100F));
	    if (netGrowthRate<=0) netGrowthRate=1;
	    
	    if(random.nextInt(netGrowthRate) == 0)
        {
            int growth = getPlantGrowth(world, i, j, k) + 1;
            if(growth < maxGrowth)
            {
            	Xie.setBlockMeta(world, i, j, k, growth);
            } else
            {
            	Xie.setBlockMeta(world, i, j, k, maxGrowth);
        		tryToGrowMoah(world, i, j, k);
            }
       }
	}
	   
   // copypasta from BlockCrops
   private float getCropGrowthRate(World world, int i, int j, int k) {
       float f = 1.0F;
       int n1 = world.getBlockId(i, j, k - 1);
       int n2 = world.getBlockId(i, j, k + 1);
       int n3 = world.getBlockId(i - 1, j, k);
       int n4 = world.getBlockId(i + 1, j, k);
       int n5 = world.getBlockId(i - 1, j, k - 1);
       int n6 = world.getBlockId(i + 1, j, k - 1);
       int n7 = world.getBlockId(i + 1, j, k + 1);
       int n8 = world.getBlockId(i - 1, j, k + 1);
       boolean flag = n3 == blockID || n4 == blockID;
       boolean flag1 = n1 == blockID || n2 == blockID;
       boolean flag2 = n5 == blockID || n6 == blockID || n7 == blockID || n8 == blockID;
       for(int l2 = i - 1; l2 <= i + 1; l2++)
       {
           for(int i3 = k - 1; i3 <= k + 1; i3++)
           {
               int j3 = world.getBlockId(l2, j - 1, i3);
               float f1 = 0.0F;
               if(j3 == Block.tilledField.blockID)
               {
                   f1 = 1.0F;
                   if(world.getBlockMetadata(l2, j - 1, i3) > 0)
                   {
                       f1 = 3F;
                   }
               }
               if(l2 != i || i3 != k)
               {
                   f1 /= 4F;
               }
               f += f1;
           }

       }

       if(flag2 || flag && flag1)
       {
           f /= 2.0F;
       }
       return f;
   }
}
