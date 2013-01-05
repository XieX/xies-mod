package xie.mods.xm.items;

import java.util.LinkedList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;
import java.util.List;

import xie.mods.xm.util.BlockID;
import xie.mods.xm.util.Droppables;
import xie.mods.xm.util.Xie;

public class XieItemStick extends XieItem implements XieItemInterface {
	
	protected List<BlockID> blocksAffected;
	protected List<Droppables> drops;
	protected int destroyChance = 66;
	
	public XieItemStick(String name, int id, int icon) {
		super(name, id, icon);
        setMaxStackSize(1);
        setMaxDamage(16);
        blocksAffected = new LinkedList<BlockID>();
        drops = new LinkedList<Droppables>();
	}

	@Override
	public boolean onItemUse(ItemStack itemstack, EntityPlayer entityplayer, World world, int i, int j, int k, int l, float a, float b, float c) {		
		if (blocksAffected==null || drops==null) return false;
		
		for (int index = 0; index<blocksAffected.size(); index++) {
			BlockID block = blocksAffected.get(index);
			if (block.matches(world, i, j, k)) {
				int bid = block.blockID;
				Xie.playBlockCrunch(world, i, j, k, bid);
				Xie.addBlockDestroyEffects(i, j, k, bid, bid >> 8);
	
				if (!world.isRemote) {
					Xie.spawnItems(drops.get(index).getDrops(),world,i,j,k);
					if (world.rand.nextInt(100)<destroyChance) {
						//Block.blocksList[bid].harvestBlock(world, entityplayer, i, j, k, l);
						world.setBlockWithNotify(i, j, k, 0);
					}
					itemstack.damageItem(1, entityplayer);
				}
				return true;
			}
		}
		
		return false;
	}
	
	public void addDrops(int blockID, Droppables drop) {
		blocksAffected.add(new BlockID(blockID));
		drops.add(drop);
	}
	
	public void addDrops(BlockID blockID, Droppables drop) {
		blocksAffected.add(blockID);
		drops.add(drop);
	}
	
	public void setDestroyChance(int c) {
		if (c<0) c=0;
		else if (c>100) c=100;
		
		destroyChance=c;
	}
	
	public Item setMaxDamage(int d) {
		return super.setMaxDamage(d);
	}
}
