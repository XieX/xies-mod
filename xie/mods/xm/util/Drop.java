// Object to contain the information and conditions regarding an item/block drop
// Xie 14-10-11

package xie.mods.xm.util;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class Drop {
	public ItemStack drop;
	
	protected int metaChance[]; 	// chance of spawning per meta (Default 100% each meta)
	protected int quantity;
	protected boolean simple;
	public boolean inheritMeta; // whether or not to "damage" this drop by the meta value of the block dropping it
	
	// basic constructor
	public Drop(int id) {
		this(id, 100);
	}	

	// additional constructors
	public Drop(Block block) {
		this(block.blockID);
	}
	
	public Drop(Item item) {
		this(item.shiftedIndex);
	}

	public Drop(int id, int chance) {
		this(new ItemStack(id, 1, 0), chance, 1);
	}
	
	public Drop(int id, int chance, int quant) {
		this(new ItemStack(id,1,0), chance, quant);
	}
	
	public Drop(ItemStack is, int chance, int quant) {
		drop = is;
		metaChance = new int[16];
		setChance(chance);
		simple=true;
		setQuantity(quant);
		inheritMeta = false;
	}
	
	public Drop(ItemStack is) {
		this(is, 100, 1);
	}
	
	public Drop(ItemStack is, int chance) {
		this(is, chance, 1);
	}
	
	// setters and getters
	public void setChance(int chance) { metaChance[0]=validateChance(chance); }
	public int getChance() { return metaChance[0]; }
	
	public void setQuantity(int q) {quantity=q;}
	public int getQuantity() {return quantity;}
	
	public boolean setMetaChance(int meta, int chance) {
		simple=false;
		if (meta>15 || meta<0) return false;
		metaChance[meta]=validateChance(chance);
		return true;
	}
	
	public boolean setMetaChanceRange(int min, int max, int chance) {
		simple=false;
		if (min>15 || min<0 || max>15 || max<0 || min>max) return false;
		for (int i=min; i<=max; i++) metaChance[i]=chance;
		return true;
	}
	

	public void setChance(String range, int chance) {
		int metaRange[] = Xie.parseMetaRange(range);
		if (metaRange!=null)
			setMetaChanceRange(metaRange[0], metaRange[1], chance);
	}
	
	// public methods
	public ItemStack getDrop(Random rand) {
		if (rand.nextInt(100)<metaChance[0]) return drop;
		else return null;
	}
	
	public ItemStack getDropForMeta(Random rand, int meta) {
		if (inheritMeta) {
			if (meta<0) meta=0;
			if (meta>15) meta=15;
			
			if (rand.nextInt(100)<(simple?metaChance[0]:metaChance[meta])) {
				return new ItemStack(drop.itemID,1,meta);
			} else return null;
		}
		
		if (simple || meta<0 || meta>15) return getDrop(rand);
		if (rand.nextInt(100)<metaChance[meta]) return drop;
		else return null;
	}

	@Override
	public String toString() {
		if (drop==null) return "[XieDrop] null";
		
		String r = "[XieDrop] "+drop.getItem().getItemName()+" chance=";
		if (simple) r+=metaChance[0]+" ";
		else for (int c : metaChance) r+=c+" ";
		
		r+="#="+quantity;
			
		return r;
	}
	
	// private methods
	protected int validateChance(int chance) {
		if (chance<0) chance=0;
		if (chance>100) chance=100;
		return chance;
	}

}
