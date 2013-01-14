package mod.xie.xm.util;

import java.util.HashMap;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.IFuelHandler;

public class XieFuelManager implements IFuelHandler {

	private HashMap<ItemWrapper,Integer> map = new HashMap<ItemWrapper,Integer>();
	private static XieFuelManager instance = new XieFuelManager();
	
	private XieFuelManager() {}
	
	public void addFuel(ItemStack item, int i) {
		map.put(new ItemWrapper(item), i);
	}
	
	public void addFuel(ItemWrapper item, int i) {
		map.put(item,i);
	}
	
	@Override
	public int getBurnTime(ItemStack fuel) {
		if (map.isEmpty()) return 0;
		
		for (ItemWrapper i : map.keySet()) {
			if (i.equals(fuel)) return map.get(i);
		}
		
		return 0;
	}

	// all the static stuff
	
	public static XieFuelManager get() {
		return instance;
	}
	
	public static void add(ItemStack item, int i) {
		instance.addFuel(item, i);
	}
	
	public static void add(ItemWrapper item, int i) {
		instance.addFuel(item,i);
	}
	
	public static int getItemBurnTime(ItemStack fuel) {
		return instance.getBurnTime(fuel);
	}
	
}
