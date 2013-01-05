package xie.mods.xm.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.ICraftingHandler;

public class XieCraftingManager implements ICraftingHandler {

	private static XieCraftingManager instance = new XieCraftingManager();
	
	private XieCraftingManager() {
		
	}
	
	public static XieCraftingManager get() {
		return instance;
	}

	@Override
	public void onCrafting(EntityPlayer player, ItemStack item,	IInventory craftMatrix) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSmelting(EntityPlayer player, ItemStack item) {
		// TODO Auto-generated method stub
		
	}
	
	
}
