package xie.mods.xm.items;

import java.util.LinkedList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockMushroom;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockStem;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;
import java.util.List;

import net.minecraft.block.Block;

import xie.mods.xm.blocks.XieBlockPlant;
import xie.mods.xm.util.Droppables;

public class XieItemFertiliser extends XieItem implements XieItemInterface {

	protected Droppables drops;
	boolean worksOnEverything;
	List<Block> worksOn;
	
	public XieItemFertiliser(String name, int id, int icon) {
		super(name, id, icon);
		worksOnEverything=true;
	}
	
	public XieItemFertiliser(String name, int id, int icon, List<Block> worksOn) {
		this(name, id, icon);
		setWorksOn(worksOn);
	}
	
	@Override
    public boolean onItemUse(ItemStack itemstack, EntityPlayer entityplayer, World world, 
    									int i, int j, int k, int l, float a, float b, float c) {
    	int bid = world.getBlockId(i, j, k);
    	Block block = Block.blocksList[bid];
    	
    	if (worksOnEverything || worksOn.contains(block)) {
	    	// check for custom plants
	        if(block instanceof XieBlockPlant) {
	        	XieBlockPlant x = (XieBlockPlant)block;
	        	if (x.fertilisable) {
	        		if (!world.isRemote) {
	        			x.fertilize(world, i, j, k);
	        			itemstack.stackSize--;
	        		}
	        		return true;
	        	} else return false;
	        }
	        
	        // fertilise vanilla plants
	        return vanillaFertilisation(bid, itemstack, world, i, j, k, l);
	        
    	}
    	
        return false;
    }
    
    public void setWorksOn(List<Block> b) {
    	worksOnEverything=false;
    	worksOn=b;
    }
    
    public void addWorksOn(Block b) {
    	if (worksOn==null) worksOn=new LinkedList<Block>();
    	worksOn.add(b);
    }
    
    protected boolean vanillaFertilisation(int bid, ItemStack itemstack, World world, int i, int j, int k, int l) {
    	if(bid == Block.sapling.blockID)
        {
            if(!world.isRemote)
            {
                ((BlockSapling)Block.sapling).growTree(world, i, j, k, world.rand);
                itemstack.stackSize--;
            }
            return true;
        }
        if(bid == Block.mushroomBrown.blockID || bid == Block.mushroomRed.blockID)
        {
            if(!world.isRemote && ((BlockMushroom)Block.blocksList[bid]).fertilizeMushroom(world, i, j, k, world.rand))
            {
                itemstack.stackSize--;
            }
            return true;
        }
        if(bid == Block.pumpkinStem.blockID || bid == Block.melonStem.blockID)
        {
            if(!world.isRemote)
            {
                ((BlockStem)Block.blocksList[bid]).fertilizeStem(world, i, j, k);
                itemstack.stackSize--;
            }
            return true;
        }
        if(bid == Block.crops.blockID)
        {
            if(!world.isRemote)
            {
                ((BlockCrops)Block.crops).fertilize(world, i, j, k);
                itemstack.stackSize--;
            }
            return true;
        }
        if(bid == Block.grass.blockID)
        {
            if(!world.isRemote)
            {
                itemstack.stackSize--;
label0:
                for(int j1 = 0; j1 < 128; j1++)
                {
                    int k1 = i;
                    int l1 = j + 1;
                    int i2 = k;
                    for(int j2 = 0; j2 < j1 / 16; j2++)
                    {
                        k1 += itemRand.nextInt(3) - 1;
                        l1 += ((itemRand.nextInt(3) - 1) * itemRand.nextInt(3)) / 2;
                        i2 += itemRand.nextInt(3) - 1;
                        if(world.getBlockId(k1, l1 - 1, i2) != Block.grass.blockID || world.isBlockNormalCube(k1, l1, i2))
                        {
                            continue label0;
                        }
                    }

                    if(world.getBlockId(k1, l1, i2) != 0)
                    {
                        continue;
                    }
                    if(itemRand.nextInt(10) != 0)
                    {
                        world.setBlockAndMetadataWithNotify(k1, l1, i2, Block.tallGrass.blockID, 1);
                        continue;
                    }
                    if(itemRand.nextInt(3) != 0)
                    {
                        world.setBlockWithNotify(k1, l1, i2, Block.plantYellow.blockID);
                    } else
                    {
                        world.setBlockWithNotify(k1, l1, i2, Block.plantRed.blockID);
                    }
                }

            }
            return true;
        }
		return false;
    }
}
