package mod.xie.xm.items;

import java.util.LinkedList;

import mod.xie.xm.blocks.XieBlockPlant;
import mod.xie.xm.util.Droppables;
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
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;
import java.util.List;

import net.minecraft.block.Block;


public class XieItemFertiliser extends XieItem implements XieItemInterface {

	protected Droppables drops;
	boolean worksOnEverything;
	List<Block> worksOn;
	
	public XieItemFertiliser(String name, int id) {
		super(name, id);
		worksOnEverything=true;
	}
	
	public XieItemFertiliser(String name, int id, List<Block> worksOn) {
		this(name, id);
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
	        return vanillaFertilisation(itemstack, world, i, j, k, entityplayer);
	        
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
    
    protected boolean vanillaFertilisation(ItemStack itemstack, World world, int i, int j, int k, EntityPlayer player) {
    	return ItemDye.applyBonemeal(itemstack, world, i, j, k, player);
    }
}
