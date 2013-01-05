package xie.mods.xm.parser;

import net.minecraft.item.ItemStack;

public class DungeonLootHelper {

	public ItemStack itemstack;
	public int rarity;
	public int min;
	public int max;
	
	public DungeonLootHelper(ItemStack i, int r, int m, int M) {
		itemstack=i;
		rarity=r;
		min=m;
		max=M;
	}
	
	public DungeonLootHelper(ItemStack i, float r, int m, int M) {
		rarity = 1;
		if (r>=1.5) rarity = Math.round(r);
		if (r==0) rarity=0;
		
		itemstack=i;
		min=m;
		max=M;
	}
}
